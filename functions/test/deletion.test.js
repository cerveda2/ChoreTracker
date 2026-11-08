// Runs against the Auth/Firestore/Functions emulators under a demo- project: `npm run test:emulators`.
const { describe, it, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  throw new Error("Run these through `npm run test:emulators`, never against a real project.");
}

const PROJECT = process.env.GCLOUD_PROJECT;
initializeApp({ projectId: PROJECT });
const db = getFirestore();

const AUTH = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`;
const FIRESTORE = `http://${process.env.FIRESTORE_EMULATOR_HOST}`;
const DOCUMENTS = `${FIRESTORE}/v1/projects/${PROJECT}/databases/(default)/documents`;
// firebase.json's functions emulator port - emulators:exec doesn't export a host for it.
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/europe-west1`;

async function signUp(name) {
  const res = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: `${name}@example.com`, password: "password123", returnSecureToken: true }),
  });
  const body = await res.json();
  return { uid: body.localId, token: body.idToken };
}

async function call(fn, user, data) {
  const headers = { "content-type": "application/json" };
  if (user) headers.authorization = `Bearer ${user.token}`;
  const res = await fetch(`${FUNCTIONS}/${fn}`, { method: "POST", headers, body: JSON.stringify({ data }) });
  return res.json();
}

// What a signed-in app user's own create would get from firestore.rules (status code).
async function createHouseholdAs(user, householdId, ownerUserId) {
  const res = await fetch(`${DOCUMENTS}/households?documentId=${householdId}`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${user.token}` },
    body: JSON.stringify({ fields: { ownerUserId: { stringValue: ownerUserId }, name: { stringValue: "Home" } } }),
  });
  return res.status;
}

async function readAs(user, path) {
  const res = await fetch(`${DOCUMENTS}/${path}`, { headers: { authorization: `Bearer ${user.token}` } });
  return res.status;
}

async function seedHousehold(householdId, owner, members = [], inviteCodes = 1) {
  const household = db.doc(`households/${householdId}`);
  await household.set({ ownerUserId: owner.uid, name: householdId, inviteCode: `${householdId}-0` });
  for (const user of [owner, ...members]) {
    await household.collection("members").doc(user.uid).set({
      id: user.uid,
      userId: user.uid,
      householdId,
      displayName: user.uid,
      role: user === owner ? "OWNER" : "MEMBER",
      active: true,
    });
    await db.doc(`users/${user.uid}`).set({ userId: user.uid, householdId });
  }
  await household.collection("chores").doc("chore-1").set({ name: "Kitchen" });
  await household.collection("completions").doc("completion-1").set({ createdByUserId: owner.uid });
  await household.collection("invites").doc("invite-1").set({ code: `${householdId}-0` });
  const writer = db.bulkWriter();
  for (let i = 0; i < inviteCodes; i++) {
    writer.set(db.doc(`inviteCodes/${householdId}-${i}`), { householdId, inviteId: "invite-1" });
  }
  await writer.close();
}

async function inviteCodeCount(householdId) {
  return (await db.collection("inviteCodes").where("householdId", "==", householdId).get()).size;
}

async function subcollectionDocCount(householdId) {
  const household = db.doc(`households/${householdId}`);
  const counts = await Promise.all(
    ["members", "chores", "completions", "invites"].map(async (name) => (await household.collection(name).get()).size),
  );
  return counts.reduce((a, b) => a + b, 0);
}

async function exists(path) {
  return (await db.doc(path).get()).exists;
}

beforeEach(async () => {
  await fetch(`${FIRESTORE}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: "DELETE" });
  await fetch(`${AUTH}/emulator/v1/projects/${PROJECT}/accounts`, { method: "DELETE" });
});

describe("deleteHousehold", () => {
  it("refuses an unauthenticated call", async () => {
    const result = await call("deleteHousehold", null, { householdId: "household-1" });
    assert.equal(result.error?.status, "UNAUTHENTICATED");
  });

  it("refuses a call without a householdId", async () => {
    const owner = await signUp("owner");
    const result = await call("deleteHousehold", owner, {});
    assert.equal(result.error?.status, "INVALID_ARGUMENT");
  });

  it("refuses anyone but the owner and leaves the household intact", async () => {
    const owner = await signUp("owner");
    const member = await signUp("member");
    const stranger = await signUp("stranger");
    await seedHousehold("household-1", owner, [member]);

    for (const caller of [member, stranger]) {
      const result = await call("deleteHousehold", caller, { householdId: "household-1" });
      assert.equal(result.error?.status, "PERMISSION_DENIED");
    }
    assert.ok(await exists("households/household-1"));
    assert.equal(await subcollectionDocCount("household-1"), 5);
    assert.equal(await inviteCodeCount("household-1"), 1);
    assert.ok(!(await exists("deletedHouseholds/household-1")));
  });

  it("deletes the whole household and its invite codes, tombstones it and keeps every pointer", async () => {
    const owner = await signUp("owner");
    const member = await signUp("member");
    await seedHousehold("household-1", owner, [member], 3);

    const result = await call("deleteHousehold", owner, { householdId: "household-1" });

    assert.equal(result.error, undefined);
    assert.ok(!(await exists("households/household-1")));
    assert.equal(await subcollectionDocCount("household-1"), 0);
    assert.equal(await inviteCodeCount("household-1"), 0);
    assert.ok(await exists("deletedHouseholds/household-1"));
    // Kept on purpose: the next household read is refused, which wipes that device's local data.
    for (const user of [owner, member]) {
      assert.equal((await db.doc(`users/${user.uid}`).get()).get("householdId"), "household-1");
    }
  });

  it("leaves other households alone", async () => {
    const owner = await signUp("owner");
    const otherOwner = await signUp("other");
    await seedHousehold("household-1", owner);
    await seedHousehold("household-2", otherOwner);

    await call("deleteHousehold", owner, { householdId: "household-1" });

    assert.ok(await exists("households/household-2"));
    assert.equal(await subcollectionDocCount("household-2"), 4);
    assert.equal(await inviteCodeCount("household-2"), 1);
  });

  it("succeeds again on a retry after the household is gone", async () => {
    const owner = await signUp("owner");
    await seedHousehold("household-1", owner);
    await call("deleteHousehold", owner, { householdId: "household-1" });

    const result = await call("deleteHousehold", owner, { householdId: "household-1" });

    assert.equal(result.error, undefined);
  });

});

describe("households rules after a delete", () => {
  it("refuses recreating the deleted household and reading it", async () => {
    const owner = await signUp("owner");
    const member = await signUp("member");
    await seedHousehold("household-1", owner, [member]);
    await call("deleteHousehold", owner, { householdId: "household-1" });

    assert.equal(await createHouseholdAs(owner, "household-1", owner.uid), 403);
    assert.equal(await readAs(owner, "households/household-1"), 403);
    assert.equal(await readAs(member, "households/household-1"), 403);
  });

  it("still lets a user create a new household for themselves, and only for themselves", async () => {
    const owner = await signUp("owner");
    const member = await signUp("member");
    await seedHousehold("household-1", owner, [member]);
    await call("deleteHousehold", owner, { householdId: "household-1" });

    assert.equal(await createHouseholdAs(owner, "household-2", owner.uid), 200);
    assert.equal(await createHouseholdAs(owner, "household-3", member.uid), 403);
  });
});

describe("deleteAccount", () => {
  it("tears down and tombstones a sole owner's household", async () => {
    const owner = await signUp("owner");
    await seedHousehold("household-1", owner, [], 2);

    const result = await call("deleteAccount", owner, {});

    assert.equal(result.error, undefined);
    assert.ok(!(await exists("households/household-1")));
    assert.equal(await inviteCodeCount("household-1"), 0);
    assert.ok(await exists("deletedHouseholds/household-1"));
    assert.ok(!(await exists(`users/${owner.uid}`)));
    await assert.rejects(getAuth().getUser(owner.uid), { code: "auth/user-not-found" });
  });

  it("hands ownership to a co-member before removing the owner", async () => {
    const owner = await signUp("owner");
    const member = await signUp("member");
    await seedHousehold("household-1", owner, [member]);

    const result = await call("deleteAccount", owner, {});

    assert.equal(result.error, undefined);
    assert.equal((await db.doc("households/household-1").get()).get("ownerUserId"), member.uid);
    const members = db.collection("households/household-1/members");
    assert.equal((await members.doc(member.uid).get()).get("role"), "OWNER");
    const departed = await members.doc(owner.uid).get();
    assert.equal(departed.get("role"), "MEMBER");
    assert.equal(departed.get("active"), false);
    assert.ok(departed.get("removedAt"));
    assert.ok(!(await exists("deletedHouseholds/household-1")));
  });

  it("prefers an active admin when the owner deletes their account", async () => {
    const owner = await signUp("owner");
    const plain = await signUp("plain");
    const admin = await signUp("admin");
    await seedHousehold("household-1", owner, [plain, admin]);
    await db.doc(`households/household-1/members/${admin.uid}`).update({ role: "ADMIN" });

    const result = await call("deleteAccount", owner, {});

    assert.ok(!result.error);
    assert.equal((await db.doc("households/household-1").get()).get("ownerUserId"), admin.uid);
    assert.equal((await db.doc(`households/household-1/members/${admin.uid}`).get()).get("role"), "OWNER");
  });

  it("soft-removes a plain member and leaves the household to its owner", async () => {
    const owner = await signUp("owner");
    const member = await signUp("member");
    await seedHousehold("household-1", owner, [member]);

    const result = await call("deleteAccount", member, {});

    assert.equal(result.error, undefined);
    assert.equal((await db.doc("households/household-1").get()).get("ownerUserId"), owner.uid);
    const departed = await db.doc(`households/household-1/members/${member.uid}`).get();
    assert.equal(departed.get("active"), false);
    assert.ok(departed.get("removedAt"));
    assert.ok(!(await exists(`users/${member.uid}`)));
  });
});
