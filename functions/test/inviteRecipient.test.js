// Who onInviteAccepted notifies, against the Firestore emulator: `npm run test:emulators`.
const { describe, it, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { inviteRecipient } = require("../lib/inviteRecipient");

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("Run through the emulators, never against a real project.");
}

const project = process.env.GCLOUD_PROJECT;
initializeApp({ projectId: project });
const db = getFirestore();

function member(uid, fields) {
  return db.doc(`households/home/members/${uid}`).set({ id: uid, userId: uid, householdId: "home", active: true, ...fields });
}

beforeEach(async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  await fetch(`http://${host}/emulator/v1/projects/${project}/databases/(default)/documents`, { method: "DELETE" });
  await member("owner", { role: "OWNER" });
  await member("admin", { role: "ADMIN" });
});

describe("inviteRecipient", () => {
  it("notifies an admin who created the invite", async () => {
    assert.equal(await inviteRecipient(db, "home", "admin", "owner"), "admin");
  });

  it("falls back to the owner for an invite without a creator (owner uploads, older app versions)", async () => {
    assert.equal(await inviteRecipient(db, "home", undefined, "owner"), "owner");
    assert.equal(await inviteRecipient(db, "home", "", "owner"), "owner");
  });

  it("falls back to the owner once the creator has left or been removed", async () => {
    await member("admin", { role: "ADMIN", active: false });
    assert.equal(await inviteRecipient(db, "home", "admin", "owner"), "owner");
  });

  it("falls back to the owner once the creator is no longer an admin", async () => {
    await member("admin", { role: "MEMBER" });
    assert.equal(await inviteRecipient(db, "home", "admin", "owner"), "owner");
  });

  it("falls back to the owner when the creator has no member doc", async () => {
    assert.equal(await inviteRecipient(db, "home", "someone-else", "owner"), "owner");
  });
});
