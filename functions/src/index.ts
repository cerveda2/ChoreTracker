import { onDocumentCreated, onDocumentUpdated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { initializeApp } from "firebase-admin/app";
import {
  getFirestore,
  FieldValue,
  Firestore,
  DocumentReference,
  DocumentSnapshot,
  Timestamp,
} from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { getMessaging } from "firebase-admin/messaging";
import { inviteRecipient } from "./inviteRecipient";

initializeApp();

export const onInviteAccepted = onDocumentUpdated(
  "households/{householdId}/invites/{inviteId}",
  async (event) => {
    const db = getFirestore();
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) {
      return;
    }

    // Only fire on the consumedAt null -> set transition.
    const wasConsumed = before.consumedAt != null;
    const isNowConsumed = after.consumedAt != null;
    if (wasConsumed || !isNowConsumed) {
      return;
    }

    const { householdId } = event.params;
    const consumedByMemberId: string | undefined = after.consumedByMemberId;
    if (!consumedByMemberId) {
      logger.warn("onInviteAccepted: consumed without consumedByMemberId", { householdId });
      return;
    }

    const householdSnap = await db.doc(`households/${householdId}`).get();
    const ownerUserId: string | undefined = householdSnap.get("ownerUserId");
    if (!ownerUserId) {
      logger.warn("onInviteAccepted: household or ownerUserId missing", { householdId });
      return;
    }

    // consumedByMemberId is the member's logical id (HouseholdMember.id), not the Firestore
    // document id - member docs are keyed by uid (see upsertMemberSnapshot/memberDocumentId on
    // the client), which isn't known here without the join actually completing first. Query by
    // the "id" field instead of doc(...) by path, which would silently miss every join.
    //
    // A targeted invite's claim briefly leaves TWO docs sharing this "id" field: the original
    // placeholder (still keyed by its own id, userId null) and the one the joining client just
    // wrote (keyed by uid) - see the cleanup below. Without an orderBy, which doc comes back
    // first here is unspecified, so prefer whichever result is actually linked (has a userId)
    // instead of trusting docs[0].
    const memberQuery = await db
      .collection(`households/${householdId}/members`)
      .where("id", "==", consumedByMemberId)
      .get();
    const memberSnap = memberQuery.docs.find((doc) => doc.get("userId") != null) ?? memberQuery.docs[0];
    const joiningMemberUserId: string | undefined = memberSnap?.get("userId");

    // A targeted (per-member) invite's targetMemberId is that placeholder's own doc id (see
    // MembersSettingsScreen's GenerateMemberInvite / member.id on the client). Once claimed, the
    // real member doc always lives at members/{uid} instead (memberDocumentId = userId ?: id in
    // FirebaseHouseholdDataSource) - the client can never delete the leftover placeholder itself,
    // since firestore.rules restricts member-doc delete to the household owner. Admin SDK bypasses
    // that, so clean it up here, right as the claim completes.
    const targetMemberId: string | undefined = after.targetMemberId;
    if (targetMemberId && targetMemberId !== joiningMemberUserId) {
      const placeholderRef = db.doc(`households/${householdId}/members/${targetMemberId}`);
      const placeholderSnap = await placeholderRef.get();
      if (placeholderSnap.exists && placeholderSnap.get("userId") == null) {
        await placeholderRef.delete();
        logger.info("onInviteAccepted: deleted stale placeholder member doc", {
          householdId,
          targetMemberId,
        });
      }
    }

    const recipientUserId = await inviteRecipient(db, householdId, after.createdByUserId, ownerUserId);

    // Self-join edge case: someone consuming their own invite (e.g. reclaiming a placeholder
    // member). Don't notify yourself.
    if (joiningMemberUserId && joiningMemberUserId === recipientUserId) {
      logger.info("onInviteAccepted: creator consumed their own invite - skipping", {
        householdId,
        recipientUserId,
      });
      return;
    }

    const recipientUserSnap = await db.doc(`users/${recipientUserId}`).get();
    const fcmToken: string | undefined = recipientUserSnap.get("fcmToken");
    if (!fcmToken) {
      logger.info("onInviteAccepted: recipient has no fcmToken registered - skipping", {
        householdId,
        recipientUserId,
      });
      return;
    }

    const joiningDisplayName: string | undefined = memberSnap?.get("displayName") || undefined;

    try {
      // Localization keys, not literal text: the client resolves them against its own string
      // resources (core/notifications/src/main/res), so the push follows the app's language.
      // Deliberately no title/body alongside them - the Android FCM SDK prefers a literal title
      // over titleLocKey whenever both are present.
      const body = joiningDisplayName
        ? { bodyLocKey: "invite_accepted_notification_body", bodyLocArgs: [joiningDisplayName] }
        : { bodyLocKey: "invite_accepted_notification_body_anonymous" };
      await getMessaging().send({
        token: fcmToken,
        android: {
          notification: { titleLocKey: "invite_accepted_notification_title", ...body },
        },
      });
      logger.info("onInviteAccepted: notified invite creator", {
        householdId,
        recipientUserId,
        consumedByMemberId,
        joiningDisplayName,
      });
    } catch (error) {
      const code = (error as { code?: string }).code;
      const isStaleToken =
        code === "messaging/invalid-registration-token" ||
        code === "messaging/registration-token-not-registered";
      if (isStaleToken) {
        logger.warn("onInviteAccepted: stale fcmToken - clearing", { recipientUserId });
        await db.doc(`users/${recipientUserId}`).update({
          fcmToken: FieldValue.delete(),
        });
      } else {
        logger.error("onInviteAccepted: messaging().send failed", { recipientUserId, error });
      }
    }
  },
);

// A device's token belongs to whoever registered it last. The app clears its token before
// signing out, but that can't happen offline, when Firebase signs the user out itself, or after
// a reinstall - so when another account registers the same token, take it off the old one, or
// that account's pushes keep reaching a device someone else is now using.
export const onFcmTokenWritten = onDocumentWritten("users/{userId}", async (event) => {
  const token = event.data?.after?.get("fcmToken");
  // Stamped (server time) only by the app's token registration - membership syncs write this doc
  // too. Older app versions don't stamp it; their writes and tokens are left alone.
  const claimedAt: Timestamp | undefined = event.data?.after?.get("fcmTokenUpdatedAt");
  const previousClaim: Timestamp | undefined = event.data?.before?.get("fcmTokenUpdatedAt");
  if (typeof token !== "string" || !claimedAt || (previousClaim && claimedAt.isEqual(previousClaim))) {
    return;
  }
  const db = getFirestore();
  const holders = await db.collection("users").where("fcmToken", "==", token).get();
  // Triggers can run late and out of order, so the claim times decide, not which runs first.
  const previous = holders.docs.filter((doc) => {
    const theirClaim: Timestamp | undefined = doc.get("fcmTokenUpdatedAt");
    return doc.id !== event.params.userId && theirClaim !== undefined && theirClaim.toMillis() < claimedAt.toMillis();
  });
  if (previous.length === 0) {
    return;
  }
  const batch = db.batch();
  previous.forEach((doc) => batch.update(doc.ref, { fcmToken: FieldValue.delete() }));
  await batch.commit();
  logger.info("onFcmTokenWritten: moved token to its new account", {
    userId: event.params.userId,
    clearedFrom: previous.map((doc) => doc.id),
  });
});

// One member asked another to do one or more chores. A send writes all of its requests in one
// batch and each of them fires this, so only the batch's lowest id sends, naming every chore that's
// still open (the sender may have cancelled some before the upload).
export const onRequestCreated = onDocumentCreated(
  "households/{householdId}/requests/{requestId}",
  async (event) => {
    const created = event.data?.data();
    if (!created) {
      return;
    }
    const { householdId, requestId } = event.params;
    const db = getFirestore();
    const batch = await db
      .collection(`households/${householdId}/requests`)
      .where("batchId", "==", created.batchId)
      .get();
    if (batch.docs.map((doc) => doc.id).sort()[0] !== requestId) {
      return;
    }
    const open = batch.docs.filter((doc) => doc.get("state") === "OPEN");
    if (open.length === 0) {
      return;
    }

    const toUserId: string = created.toUserId;
    const recipientSnap = await db.doc(`users/${toUserId}`).get();
    const fcmToken: string | undefined = recipientSnap.get("fcmToken");
    const pushTypes: unknown = recipientSnap.get("pushTypes");
    // A build lists "request" only once it can show this push, so older ones get nothing rather
    // than an empty notification (see #106).
    if (!fcmToken || !Array.isArray(pushTypes) || !pushTypes.includes("request")) {
      logger.info("onRequestCreated: recipient can't receive request pushes - skipping", {
        householdId,
        toUserId,
      });
      return;
    }

    const [senderSnap, ...choreSnaps] = await db.getAll(
      db.doc(`households/${householdId}/members/${created.createdByUserId}`),
      ...open.map((doc) => db.doc(`households/${householdId}/chores/${doc.get("choreId")}`)),
    );
    const senderName: string = senderSnap.get("displayName") || "";
    const choreNames = choreSnaps
      .map((snap) => snap.get("name"))
      .filter((name): name is string => typeof name === "string" && name.length > 0)
      .join(", ");
    const note: string | undefined = created.note || undefined;

    try {
      // Localization keys only, as in onInviteAccepted.
      const body = note
        ? { bodyLocKey: "chore_request_notification_body_note", bodyLocArgs: [choreNames, note] }
        : { bodyLocKey: "chore_request_notification_body", bodyLocArgs: [choreNames] };
      await getMessaging().send({
        token: fcmToken,
        data: { type: "request", householdId },
        android: {
          notification: {
            channelId: "chore_requests",
            titleLocKey: "chore_request_notification_title",
            titleLocArgs: [senderName],
            ...body,
          },
        },
      });
      logger.info("onRequestCreated: notified recipient", { householdId, toUserId, requests: open.length });
    } catch (error) {
      const code = (error as { code?: string }).code;
      if (code === "messaging/invalid-registration-token" || code === "messaging/registration-token-not-registered") {
        logger.warn("onRequestCreated: stale fcmToken - clearing", { toUserId });
        await db.doc(`users/${toUserId}`).update({ fcmToken: FieldValue.delete() });
      } else {
        logger.error("onRequestCreated: messaging().send failed", { toUserId, error });
      }
    }
  },
);

// Google Play requires an in-app account deletion path. Routed through the Admin SDK rather than
// the client because admin.auth().deleteUser() has no recent-login requirement - the client SDK's
// FirebaseUser.delete() throws on a stale session and would force a re-auth prompt.
//
// Pinned to europe-west1 to sit next to the Firestore database (onInviteAccepted co-locates there
// automatically as a Firestore trigger; a callable does not, so it must be explicit). The Android
// client requests the same region.
export const deleteAccount = onCall({ region: "europe-west1" }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "You must be signed in to delete your account.");
  }

  const db = getFirestore();
  const userRef = db.doc(`users/${uid}`);
  const directHouseholdId: string | undefined = (await userRef.get()).get("householdId");
  const householdId = directHouseholdId ?? (await resolveActiveHouseholdId(db, uid));

  if (householdId) {
    const householdRef = db.doc(`households/${householdId}`);
    const householdSnap = await householdRef.get();
    if (householdSnap.exists) {
      await resolveHouseholdMembership(db, uid, householdId, householdRef, householdSnap);
    } else {
      logger.warn("deleteAccount: stale householdId, household missing", { uid, householdId });
    }
  }

  // Admin SDK bypasses security rules, so no users/{uid} rule change is needed. Auth user goes
  // LAST: a Firestore failure above leaves the account intact and the whole call retryable.
  await userRef.delete();
  await getAuth().deleteUser(uid);
  logger.info("deleteAccount: account removed", { uid, hadHousehold: householdId != null });
});

// Owner-only, and deletes the household for every member. Every users/{uid}.householdId, the
// owner's included, is left pointing here on purpose: the next household read is refused, which
// wipes that device's local data the same way a removed member's is - the owner's other devices
// too, which a dropped pointer would leave holding the household.
export const deleteHousehold = onCall({ region: "europe-west1" }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "You must be signed in to delete a household.");
  }
  const householdId = request.data?.householdId;
  if (typeof householdId !== "string" || householdId.length === 0) {
    throw new HttpsError("invalid-argument", "householdId is required.");
  }

  const db = getFirestore();
  const householdRef = db.doc(`households/${householdId}`);
  const householdSnap = await householdRef.get();
  // Missing means a retry after a lost response - succeed so the caller still clears local data.
  if (householdSnap.exists) {
    if (householdSnap.get("ownerUserId") !== uid) {
      throw new HttpsError("permission-denied", "Only the household owner can delete it.");
    }
    await deleteHouseholdTree(db, householdId, householdRef);
    logger.info("deleteHousehold: household deleted", { uid, householdId });
  }
});

// The tombstone goes first: the households create rule refuses a deleted id, so no client - the
// owner's other device, or a push already in flight - can recreate the household afterwards.
// Members go before the rest of the tree: once their docs are gone the rules refuse their writes,
// so nothing they send mid-delete lands after recursiveDelete has listed what to remove. The
// household doc goes last, so a partial failure keeps ownerUserId in place and the caller can
// retry.
async function deleteHouseholdTree(
  db: Firestore,
  householdId: string,
  householdRef: DocumentReference,
): Promise<void> {
  await db.doc(`deletedHouseholds/${householdId}`).set({ deletedAt: FieldValue.serverTimestamp() });
  const codes = await db.collection("inviteCodes").where("householdId", "==", householdId).get();
  if (!codes.empty) {
    const batch = db.batch();
    codes.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
  }
  await db.recursiveDelete(householdRef.collection("members"));
  await db.recursiveDelete(householdRef);
}

// users/{uid}.householdId can be stale or missing (e.g. lost to a prior partial sync) while the
// caller is still an active member somewhere - without this fallback, resolveHouseholdMembership
// is skipped entirely and the household (and, for a sole owner, its data) is silently orphaned.
// Mirrors the Android client's own fallback (FirebaseHouseholdDataSource.resolveHouseholdId):
// members.userId already has a collection-group index (firestore.indexes.json) for this exact
// query, so this needs no new index.
async function resolveActiveHouseholdId(db: Firestore, uid: string): Promise<string | undefined> {
  const snap = await db.collectionGroup("members").where("userId", "==", uid).limit(1).get();
  const doc = snap.docs[0];
  if (!doc || doc.get("active") !== true) {
    return undefined;
  }
  return doc.get("householdId");
}

async function resolveHouseholdMembership(
  db: Firestore,
  uid: string,
  householdId: string,
  householdRef: DocumentReference,
  householdSnap: DocumentSnapshot,
): Promise<void> {
  const membersRef = householdRef.collection("members");
  const membersSnap = await membersRef.get();
  const isOwner = householdSnap.get("ownerUserId") === uid;

  // Member docs carry no join timestamp. Prefer an admin, then pick the lowest document id
  // (the uid for a linked member) for a deterministic fallback.
  const coLinked = membersSnap.docs
    .filter((doc) => {
      const memberUserId = doc.get("userId");
      return typeof memberUserId === "string" && memberUserId !== uid && doc.get("active") === true;
    })
    .sort((a, b) => {
      const aAdmin = a.get("role") === "ADMIN" ? 0 : 1;
      const bAdmin = b.get("role") === "ADMIN" ? 0 : 1;
      return aAdmin - bAdmin || a.id.localeCompare(b.id);
    });

  if (isOwner && coLinked.length === 0) {
    await deleteHouseholdTree(db, householdId, householdRef);
    logger.info("deleteAccount: sole-owner household torn down", { uid, householdId });
    return;
  }

  if (isOwner) {
    const newOwner = coLinked[0];
    const newOwnerUserId: string = newOwner.get("userId");
    const batch = db.batch();
    batch.update(householdRef, { ownerUserId: newOwnerUserId });
    batch.set(membersRef.doc(newOwner.id), { role: "OWNER" }, { merge: true });
    batch.set(membersRef.doc(uid), { role: "MEMBER" }, { merge: true });
    await batch.commit();
    logger.info("deleteAccount: ownership transferred before leaving", {
      uid,
      householdId,
      newOwnerUserId,
    });
  }

  // Soft-remove the caller's own row - same shape as the client's leaveHousehold write. Completions
  // and invites are untouched by construction, so history keeps resolving the departed name; the
  // email has no further use.
  await membersRef
    .doc(uid)
    .set(
      { active: false, removedAt: FieldValue.serverTimestamp(), email: FieldValue.delete(), role: "MEMBER" },
      { merge: true },
    );
  logger.info("deleteAccount: member soft-removed", { uid, householdId });
}
