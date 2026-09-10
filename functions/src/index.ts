import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { initializeApp } from "firebase-admin/app";
import {
  getFirestore,
  FieldValue,
  Firestore,
  DocumentReference,
  DocumentSnapshot,
} from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { getMessaging } from "firebase-admin/messaging";

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
    const memberQuery = await db
      .collection(`households/${householdId}/members`)
      .where("id", "==", consumedByMemberId)
      .limit(1)
      .get();
    const memberSnap = memberQuery.docs[0];
    const joiningMemberUserId: string | undefined = memberSnap?.get("userId");

    // Self-join edge case: the owner consuming their own invite (e.g. reclaiming a placeholder
    // member). Don't notify yourself.
    if (joiningMemberUserId && joiningMemberUserId === ownerUserId) {
      logger.info("onInviteAccepted: owner consumed their own invite - skipping", {
        householdId,
        ownerUserId,
      });
      return;
    }

    const ownerUserSnap = await db.doc(`users/${ownerUserId}`).get();
    const fcmToken: string | undefined = ownerUserSnap.get("fcmToken");
    if (!fcmToken) {
      logger.info("onInviteAccepted: owner has no fcmToken registered - skipping", {
        householdId,
        ownerUserId,
      });
      return;
    }

    const joiningDisplayName: string = memberSnap?.get("displayName") || "Someone";

    try {
      await getMessaging().send({
        token: fcmToken,
        notification: {
          title: "New household member",
          body: `${joiningDisplayName} joined your household`,
        },
      });
      logger.info("onInviteAccepted: notified household owner", {
        householdId,
        ownerUserId,
        consumedByMemberId,
        joiningDisplayName,
      });
    } catch (error) {
      const code = (error as { code?: string }).code;
      const isStaleToken =
        code === "messaging/invalid-registration-token" ||
        code === "messaging/registration-token-not-registered";
      if (isStaleToken) {
        logger.warn("onInviteAccepted: stale fcmToken - clearing", { ownerUserId });
        await db.doc(`users/${ownerUserId}`).update({
          fcmToken: FieldValue.delete(),
        });
      } else {
        logger.error("onInviteAccepted: messaging().send failed", { ownerUserId, error });
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

  // Member docs carry no join timestamp, so "oldest co-member" is not computable - fall back to a
  // deterministic pick: the lowest document id (which is the uid for a linked member).
  const coLinked = membersSnap.docs
    .filter((doc) => {
      const memberUserId = doc.get("userId");
      return typeof memberUserId === "string" && memberUserId !== uid && doc.get("active") === true;
    })
    .sort((a, b) => a.id.localeCompare(b.id));

  if (isOwner && coLinked.length === 0) {
    // Sole owner: tear the whole household down (the "delete household" case deferred from 9b).
    const codes = await db
      .collection("inviteCodes")
      .where("householdId", "==", householdId)
      .get();
    if (!codes.empty) {
      const batch = db.batch();
      codes.docs.forEach((doc) => batch.delete(doc.ref));
      await batch.commit();
    }
    await db.recursiveDelete(householdRef);
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
  // and invites are untouched by construction, so history keeps resolving the departed name.
  await membersRef
    .doc(uid)
    .set({ active: false, removedAt: FieldValue.serverTimestamp() }, { merge: true });
  logger.info("deleteAccount: member soft-removed", { uid, householdId });
}
