const { describe, it, beforeEach, mock } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { notifyRequestCreated } = require("../lib/requestNotification");

if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.GCLOUD_PROJECT?.startsWith("demo-")) {
  throw new Error("Run through the emulators under a demo- project, never a real project.");
}

// A separate emulator project prevents the live Functions emulator from claiming these batches.
const project = `${process.env.GCLOUD_PROJECT}-request-payloads`;
const db = getFirestore(initializeApp({ projectId: project }));
const marker = db.doc("households/home/requestPushes/sender_batch-1");
const recipient = db.doc("users/recipient");
let send;

function request(fields = {}) {
  return {
    batchId: "batch-1", createdByUserId: "sender", toUserId: "recipient",
    choreId: "dishes", state: "OPEN", ...fields,
  };
}

async function writeRequest(id, fields = {}) {
  const created = request(fields);
  await db.doc(`households/home/requests/${id}`).set(created);
  return created;
}

function notify(id, created) {
  return notifyRequestCreated(db, { send }, "home", id, created);
}

beforeEach(async () => {
  const response = await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${project}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(response.ok, true);
  await recipient.set({ fcmToken: "recipient-token", pushTypes: ["request"] });
  await db.doc("households/home/members/sender").set({ displayName: "Šárka" });
  await db.doc("households/home/chores/dishes").set({ name: "Nádobí" });
  send = mock.fn(async () => "test-message");
});

describe("request notification dispatch", () => {
  it("sends the current sender, open chore names and optional note to the recipient's token", async () => {
    await db.doc("households/home/chores/trash").set({ name: "Koš" });
    const created = await writeRequest("a", { note: "Prosím dnes 🙏" });
    await writeRequest("b", { choreId: "trash" });
    await writeRequest("c", { choreId: "cancelled", state: "CANCELLED" });
    await notify("a", created);

    assert.equal(send.mock.callCount(), 1);
    const message = send.mock.calls[0].arguments[0];
    assert.equal(message.token, "recipient-token");
    assert.deepEqual(message.data, { type: "request", householdId: "home" });
    assert.deepEqual(message.android.notification, {
      channelId: "chore_requests",
      titleLocKey: "chore_request_notification_title", titleLocArgs: ["Šárka"],
      bodyLocKey: "chore_request_notification_body_note", bodyLocArgs: ["Nádobí, Koš", "Prosím dnes 🙏"],
    });
  });

  it("keeps batches from different senders separate even when the other sender has a lower request id", async () => {
    await writeRequest("a-intruder", { createdByUserId: "someone-else", choreId: "other-chore" });
    const created = await writeRequest("b-sender");
    await notify("b-sender", created);
    assert.equal(send.mock.callCount(), 1);
    assert.deepEqual(send.mock.calls[0].arguments[0].android.notification.bodyLocArgs, ["Nádobí"]);
  });

  it("sends once across concurrent duplicate deliveries and other requests in the same batch", async () => {
    const first = await writeRequest("a");
    const second = await writeRequest("b");
    await Promise.all([notify("a", first), notify("a", first), notify("b", second)]);
    await notify("a", first);
    assert.equal(send.mock.callCount(), 1);
  });

  it("does not send or claim a batch without a token or request opt-in, including older builds", async () => {
    const created = await writeRequest("a");
    for (const user of [
      { pushTypes: ["request"] },
      { fcmToken: "token", pushTypes: [] },
      { fcmToken: "token", pushTypes: ["invite"] },
      { fcmToken: "token" },
    ]) {
      await recipient.set(user);
      await notify("a", created);
      assert.equal(send.mock.callCount(), 0);
      assert.equal((await marker.get()).exists, false);
    }
  });

  it("does not send a batch with no open requests", async () => {
    const created = await writeRequest("a", { state: "CANCELLED" });
    await notify("a", created);
    assert.equal(send.mock.callCount(), 0);
    assert.equal((await marker.get()).exists, false);
  });

  it("uses empty localization arguments when sender or chore records are missing", async () => {
    const created = await writeRequest("a", { choreId: "missing" });
    await db.doc("households/home/members/sender").delete();
    await notify("a", created);
    assert.equal(send.mock.callCount(), 1);
    const notification = send.mock.calls[0].arguments[0].android.notification;
    assert.deepEqual(notification.titleLocArgs, [""]);
    assert.deepEqual(notification.bodyLocArgs, [""]);
  });

  for (const code of ["messaging/invalid-registration-token", "messaging/registration-token-not-registered"]) {
    it(`clears a stale token after ${code}`, async () => {
      const created = await writeRequest("a");
      send = mock.fn(async () => { throw Object.assign(new Error("stale token"), { code }); });
      await notify("a", created);
      assert.equal(send.mock.callCount(), 1);
      assert.equal((await recipient.get()).get("fcmToken"), undefined);
      assert.deepEqual((await recipient.get()).get("pushTypes"), ["request"]);
    });
  }

  it("keeps the token after a transient messaging failure", async () => {
    const created = await writeRequest("a");
    send = mock.fn(async () => { throw Object.assign(new Error("unavailable"), { code: "messaging/server-unavailable" }); });
    await notify("a", created);
    assert.equal(send.mock.callCount(), 1);
    assert.equal((await recipient.get()).get("fcmToken"), "recipient-token");
  });
});
