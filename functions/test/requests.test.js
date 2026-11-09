// Who may ask, cancel and decline chore requests, against the Auth and Firestore emulators: `npm run test:emulators`.
const { describe, it, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  throw new Error("Run through the emulators, never against a real project.");
}

const project = process.env.GCLOUD_PROJECT;
if (!getApps().length) initializeApp({ projectId: project });
const db = getFirestore();
const auth = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`;
const firestore = `http://${process.env.FIRESTORE_EMULATOR_HOST}`;
const documents = `${firestore}/v1/projects/${project}/databases/(default)/documents`;

async function signUp(name) {
  const response = await fetch(`${auth}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: `${name}@example.com`, password: "password123", returnSecureToken: true }),
  });
  const body = await response.json();
  return { uid: body.localId, token: body.idToken };
}

function value(input) {
  if (input === null) return { nullValue: null };
  return { stringValue: String(input) };
}

async function writeAs(user, path, fields, mask) {
  const query = mask ? `?${mask.map((field) => `updateMask.fieldPaths=${field}`).join("&")}` : "";
  const response = await fetch(`${documents}/${path}${query}`, {
    method: "PATCH",
    headers: { "content-type": "application/json", authorization: `Bearer ${user.token}` },
    body: JSON.stringify({ fields: Object.fromEntries(Object.entries(fields).map(([key, field]) => [key, value(field)])) }),
  });
  return response.status;
}

async function seed() {
  const owner = await signUp("owner");
  const sam = await signUp("sam");
  const outsider = await signUp("outsider");
  const household = db.doc("households/home");
  await household.set({ id: "home", name: "Home", ownerUserId: owner.uid, inviteCode: "ABC123" });
  // Sam claimed a placeholder, so his member id isn't his uid.
  await household.collection("members").doc(owner.uid).set({
    id: owner.uid, householdId: "home", userId: owner.uid, displayName: "Owner", role: "OWNER", active: true,
  });
  await household.collection("members").doc(sam.uid).set({
    id: "member-sam", householdId: "home", userId: sam.uid, displayName: "Sam", role: "MEMBER", active: true,
  });
  await household.collection("members").doc("placeholder").set({
    id: "placeholder", householdId: "home", userId: null, displayName: "Grandma", role: "MEMBER", active: true,
  });
  await household.collection("chores").doc("dishes").set({ id: "dishes", householdId: "home", name: "Dishes" });
  return { owner, sam, outsider };
}

function ask(from, fromMemberId, to, toMemberId, overrides = {}) {
  return {
    id: "request-1", householdId: "home", batchId: "batch-1", choreId: "dishes",
    fromMemberId, toMemberId, toUserId: to, createdByUserId: from.uid, note: null,
    createdAt: "2026-01-01", state: "OPEN", resolvedAt: null, ...overrides,
  };
}

const path = "households/home/requests/request-1";

beforeEach(async () => {
  await fetch(`${firestore}/emulator/v1/projects/${project}/databases/(default)/documents`, { method: "DELETE" });
  await fetch(`${auth}/emulator/v1/projects/${project}/accounts`, { method: "DELETE" });
});

describe("chore requests", () => {
  it("can only be sent by a member, as themselves, to another linked member", async () => {
    const { owner, sam, outsider } = await seed();
    assert.equal(await writeAs(outsider, path, ask(outsider, outsider.uid, sam.uid, "member-sam")), 403);
    assert.equal(await writeAs(owner, path, ask(owner, "member-sam", sam.uid, "member-sam")), 403);
    assert.equal(await writeAs(owner, path, { ...ask(owner, owner.uid, sam.uid, "member-sam"), createdByUserId: sam.uid }), 403);
    assert.equal(await writeAs(owner, path, ask(owner, owner.uid, owner.uid, owner.uid)), 403);
    assert.equal(await writeAs(owner, path, ask(owner, owner.uid, "placeholder", "placeholder")), 403);
    assert.equal(await writeAs(owner, path, ask(owner, owner.uid, sam.uid, sam.uid)), 403);
    assert.equal(await writeAs(owner, path, ask(owner, owner.uid, sam.uid, "member-sam", { choreId: "nope" })), 403);
    assert.equal(await writeAs(owner, path, ask(owner, owner.uid, sam.uid, "member-sam", { state: "DECLINED" })), 403);
    assert.equal(await writeAs(owner, path, ask(owner, owner.uid, sam.uid, "member-sam")), 200);
  });

  it("lets only the recipient decline and only the sender cancel, once", async () => {
    const { owner, sam } = await seed();
    const sent = ask(owner, owner.uid, sam.uid, "member-sam");
    assert.equal(await writeAs(owner, path, sent), 200);
    assert.equal(await writeAs(owner, path, sent), 200);
    const mask = ["state", "resolvedAt"];
    assert.equal(await writeAs(owner, path, { state: "DECLINED", resolvedAt: "2026-01-02" }, mask), 403);
    assert.equal(await writeAs(sam, path, { state: "CANCELLED", resolvedAt: "2026-01-02" }, mask), 403);
    assert.equal(await writeAs(sam, path, { choreId: "dishes", note: "No" }, ["note"]), 403);
    assert.equal(await writeAs(sam, path, { state: "DECLINED", resolvedAt: "2026-01-02" }, mask), 200);
    assert.equal(await writeAs(owner, path, { state: "CANCELLED", resolvedAt: "2026-01-03" }, mask), 403);
  });

  it("accepts a request the sender cancelled before it was uploaded", async () => {
    const { owner, sam } = await seed();
    assert.equal(await writeAs(owner, path, ask(owner, owner.uid, sam.uid, "member-sam", {
      state: "CANCELLED", resolvedAt: "2026-01-02",
    })), 200);
  });
});
