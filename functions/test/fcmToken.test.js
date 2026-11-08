// Runs against the Firestore/Functions emulators under a demo- project: `npm run test:emulators`.
const { describe, it, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("Run these through `npm run test:emulators`, never against a real project.");
}

const PROJECT = process.env.GCLOUD_PROJECT;
initializeApp({ projectId: PROJECT });
const db = getFirestore();

// onFcmTokenWritten runs asynchronously after the write, so wait for its effect.
async function eventually(check, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    if (await check()) return;
    if (Date.now() > deadline) throw new Error("condition not met in time");
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
}

// What FirebaseFcmTokenWriter.writeToken sends.
function register(userId, token) {
  return db.doc(`users/${userId}`).set(
    { userId, fcmToken: token, fcmTokenUpdatedAt: FieldValue.serverTimestamp() },
    { merge: true },
  );
}

async function tokenOf(userId) {
  return (await db.doc(`users/${userId}`).get()).get("fcmToken");
}

beforeEach(async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  await fetch(`http://${host}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: "DELETE" });
});

describe("onFcmTokenWritten", () => {
  it("moves a token to the account that registered it last, leaving other tokens alone", async () => {
    await register("user-a", "device-1");
    await register("user-c", "device-2");

    await register("user-b", "device-1");

    await eventually(async () => (await tokenOf("user-a")) === undefined);
    // Give user-a's own (earlier) trigger time to run too - it mustn't take the token back.
    await new Promise((resolve) => setTimeout(resolve, 500));
    assert.equal(await tokenOf("user-b"), "device-1");
    assert.equal(await tokenOf("user-c"), "device-2");
  });

  it("hands the token back when the first account registers it again", async () => {
    await register("user-a", "device-1");
    await register("user-b", "device-1");
    await eventually(async () => (await tokenOf("user-a")) === undefined);

    await register("user-a", "device-1");

    await eventually(async () => (await tokenOf("user-b")) === undefined);
    assert.equal(await tokenOf("user-a"), "device-1");
  });

  it("leaves a token written without a claim time alone (older app versions)", async () => {
    await register("user-a", "device-1");
    await db.doc("users/user-b").set({ userId: "user-b", fcmToken: "device-1" });
    await new Promise((resolve) => setTimeout(resolve, 500));

    assert.equal(await tokenOf("user-a"), "device-1");
    assert.equal(await tokenOf("user-b"), "device-1");
  });
});
