import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
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

    const memberSnap = await db
      .doc(`households/${householdId}/members/${consumedByMemberId}`)
      .get();
    const joiningMemberUserId: string | undefined = memberSnap.get("userId");

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

    const joiningDisplayName: string = memberSnap.get("displayName") || "Someone";

    try {
      await getMessaging().send({
        token: fcmToken,
        notification: {
          title: "New household member",
          body: `${joiningDisplayName} joined your household`,
        },
      });
      logger.info("onInviteAccepted: notified household owner", { householdId, ownerUserId });
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
