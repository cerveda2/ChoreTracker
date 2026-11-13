import { DocumentData, FieldValue, Firestore } from "firebase-admin/firestore";
import { Message, Messaging } from "firebase-admin/messaging";
import * as logger from "firebase-functions/logger";
import { acceptsPush } from "./pushTypes";

const ALREADY_EXISTS = 6;

export function buildRequestMessage(input: {
  token: string;
  householdId: string;
  senderName: string;
  choreNames: string;
  note?: string;
}): Message {
  const body = input.note
    ? { bodyLocKey: "chore_request_notification_body_note", bodyLocArgs: [input.choreNames, input.note] }
    : { bodyLocKey: "chore_request_notification_body", bodyLocArgs: [input.choreNames] };
  return {
    token: input.token,
    data: { type: "request", householdId: input.householdId },
    android: {
      notification: {
        channelId: "chore_requests",
        titleLocKey: "chore_request_notification_title",
        titleLocArgs: [input.senderName],
        ...body,
      },
    },
  };
}

export async function notifyRequestCreated(
  db: Firestore,
  messaging: Pick<Messaging, "send">,
  householdId: string,
  requestId: string,
  created: DocumentData,
): Promise<void> {
  const batch = await db
    .collection(`households/${householdId}/requests`)
    .where("batchId", "==", created.batchId)
    .where("createdByUserId", "==", created.createdByUserId)
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
  if (!fcmToken || !acceptsPush(recipientSnap.get("pushTypes"), "request")) {
    logger.info("onRequestCreated: recipient can't receive request pushes - skipping", {
      householdId,
      toUserId,
    });
    return;
  }

  try {
    await db
      .doc(`households/${householdId}/requestPushes/${created.createdByUserId}_${created.batchId}`)
      .create({ sentAt: FieldValue.serverTimestamp() });
  } catch (error) {
    if ((error as { code?: number }).code === ALREADY_EXISTS) {
      logger.info("onRequestCreated: batch already notified - skipping", { householdId, requestId });
      return;
    }
    throw error;
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

  try {
    await messaging.send(buildRequestMessage({
      token: fcmToken,
      householdId,
      senderName,
      choreNames,
      note: created.note || undefined,
    }));
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
}
