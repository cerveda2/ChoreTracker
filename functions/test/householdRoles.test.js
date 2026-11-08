// Authorization checks against the Auth and Firestore emulators: `npm run test:emulators`.
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
  if (typeof input === "boolean") return { booleanValue: input };
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

async function deleteAs(user, path) {
  return (await fetch(`${documents}/${path}`, {
    method: "DELETE",
    headers: { authorization: `Bearer ${user.token}` },
  })).status;
}

async function seed() {
  const owner = await signUp("owner");
  const admin = await signUp("admin");
  const member = await signUp("member");
  const outsider = await signUp("outsider");
  const household = db.doc("households/home");
  await household.set({ id: "home", name: "Home", ownerUserId: owner.uid, inviteCode: "ABC123" });
  for (const [user, role] of [[owner, "OWNER"], [admin, "ADMIN"], [member, "MEMBER"]]) {
    await household.collection("members").doc(user.uid).set({
      id: user.uid, householdId: "home", userId: user.uid, displayName: role, role, active: true,
    });
  }
  await household.collection("invites").doc("invite-1").set({
    id: "invite-1", householdId: "home", code: "ABC123", consumedAt: null,
  });
  return { owner, admin, member, outsider };
}

beforeEach(async () => {
  await fetch(`${firestore}/emulator/v1/projects/${project}/databases/(default)/documents`, { method: "DELETE" });
  await fetch(`${auth}/emulator/v1/projects/${project}/accounts`, { method: "DELETE" });
});

describe("household roles", () => {
  it("lets a member manage only their own chore and keeps its creator immutable", async () => {
    const { admin, member, outsider } = await seed();
    const chore = `households/home/chores/chore-1`;
    assert.equal(await writeAs(member, chore, {
      id: "chore-1", householdId: "home", name: "Dishes", createdByUserId: member.uid,
    }), 200);
    assert.equal(await writeAs(member, chore, { name: "Wash dishes" }, ["name"]), 200);
    assert.equal(await writeAs(outsider, chore, { name: "No" }, ["name"]), 403);
    assert.equal(await writeAs(member, chore, { createdByUserId: outsider.uid }, ["createdByUserId"]), 403);
    assert.equal(await writeAs(member, chore, { deletedAt: "2026-01-01" }, ["deletedAt"]), 200);
    assert.equal(await writeAs(admin, chore, { name: "Admin edit" }, ["name"]), 200);
    assert.equal(await deleteAs(member, chore), 403);
  });

  it("grants admin management without ownership or role changes", async () => {
    const { owner, admin, member } = await seed();
    assert.equal(await writeAs(admin, "households/home", { name: "New home" }, ["name"]), 200);
    assert.equal(await writeAs(admin, "households/home", { ownerUserId: admin.uid }, ["ownerUserId"]), 403);
    assert.equal(await deleteAs(admin, "households/home"), 403);
    assert.equal(await writeAs(admin, `households/home/members/${member.uid}`, {
      active: false, removedAt: "2026-01-01",
    }, ["active", "removedAt"]), 200);
    assert.equal(await writeAs(admin, `households/home/members/${owner.uid}`, {
      active: false, removedAt: "2026-01-01",
    }, ["active", "removedAt"]), 403);
    assert.equal(await writeAs(admin, `households/home/members/${member.uid}`, { role: "ADMIN" }, ["role"]), 403);
  });

  it("allows admin invites and placeholders but not removal of another admin", async () => {
    const { owner, admin, member } = await seed();
    assert.equal(await writeAs(admin, "households/home/invites/invite-2", {
      id: "invite-2", householdId: "home", code: "NEWCODE",
    }), 200);
    assert.equal(await writeAs(admin, "inviteCodes/NEWCODE", {
      householdId: "home", inviteId: "invite-2",
    }), 200);
    assert.equal(await writeAs(admin, "households/home", { inviteCode: "NEWCODE" }, ["inviteCode"]), 200);
    assert.equal(await writeAs(admin, "households/home/members/placeholder-1", {
      id: "placeholder-1", householdId: "home", userId: null,
      displayName: "Guest", role: "MEMBER", active: true,
    }), 200);
    assert.equal(await writeAs(admin, "households/home/members/placeholder-2", {
      id: "placeholder-2", householdId: "home", userId: null,
      displayName: "Guest", role: "ADMIN", active: true,
    }), 403);
    await db.doc(`households/home/members/${member.uid}`).update({ role: "ADMIN" });
    assert.equal(await writeAs(admin, `households/home/members/${member.uid}`, {
      active: false, removedAt: "2026-01-01",
    }, ["active", "removedAt"]), 403);
    assert.equal(await writeAs(owner, `households/home/members/${member.uid}`, { role: "MEMBER" }, ["role"]), 200);
  });

  it("lets an invite name only its own creator, and never change it", async () => {
    const { owner, admin } = await seed();
    const invite = "households/home/invites/invite-3";
    const fields = { id: "invite-3", householdId: "home", code: "CODE3" };
    assert.equal(await writeAs(admin, invite, { ...fields, createdByUserId: owner.uid }), 403);
    assert.equal(await writeAs(admin, invite, { ...fields, createdByUserId: admin.uid }), 200);
    // A retried upload resends the same value; changing it is denied, even to the owner.
    assert.equal(await writeAs(admin, invite, { createdByUserId: admin.uid }, ["createdByUserId"]), 200);
    assert.equal(await writeAs(owner, invite, { createdByUserId: owner.uid }, ["createdByUserId"]), 403);
    // The owner's snapshot upload leaves it out, which keeps it.
    assert.equal(await writeAs(owner, invite, { code: "CODE3" }, ["code"]), 200);
    assert.equal((await db.doc(invite).get()).get("createdByUserId"), admin.uid);
  });

  it("rejects an elevated self-join and permits only an admin self-demotion", async () => {
    const { admin, member, outsider } = await seed();
    const ownPath = `households/home/members/${outsider.uid}`;
    const joined = {
      id: outsider.uid, householdId: "home", userId: outsider.uid, displayName: "Outsider",
      active: true, joinedViaInviteId: "invite-1",
    };
    assert.equal(await writeAs(outsider, ownPath, { ...joined, role: "ADMIN" }), 403);
    assert.equal(await writeAs(outsider, ownPath, { ...joined, role: "MEMBER" }), 200);
    assert.equal(await writeAs(member, `households/home/members/${member.uid}`, { role: "ADMIN" }, ["role"]), 403);
    assert.equal(await writeAs(admin, `households/home/members/${admin.uid}`, { role: "MEMBER" }, ["role"]), 200);
  });

  it("resets an admin on leave and requires a member role on rejoin", async () => {
    const { admin } = await seed();
    const path = `households/home/members/${admin.uid}`;
    assert.equal(await writeAs(admin, path, {
      active: false, removedAt: "2026-01-01", role: "MEMBER",
    }, ["active", "removedAt", "role"]), 200);
    assert.equal(await writeAs(admin, path, {
      active: true, removedAt: null, joinedViaInviteId: "invite-1", role: "ADMIN",
    }, ["active", "removedAt", "joinedViaInviteId", "role"]), 403);
    assert.equal(await writeAs(admin, path, {
      active: true, removedAt: null, joinedViaInviteId: "invite-1", role: "MEMBER",
    }, ["active", "removedAt", "joinedViaInviteId", "role"]), 200);
  });

  it("lets an admin resend a placeholder that already landed, but not change it", async () => {
    const { admin } = await seed();
    const path = "households/home/members/placeholder-1";
    const placeholder = {
      id: "placeholder-1", householdId: "home", userId: null, displayName: "Guest", role: "MEMBER", active: true,
    };
    const fields = Object.keys(placeholder);
    assert.equal(await writeAs(admin, path, placeholder, fields), 200);
    assert.equal(await writeAs(admin, path, placeholder, fields), 200);
    assert.equal(await writeAs(admin, path, { ...placeholder, displayName: "Renamed" }, fields), 403);
  });
});

// What the 0.1.4 app sends, which still runs next to the release that adds roles.
describe("0.1.4 uploads", () => {
  it("demote an admin whose app re-uploads their member doc", async () => {
    const { admin } = await seed();
    const path = `households/home/members/${admin.uid}`;
    await db.doc(path).update({ removedAt: null, email: "admin@example.com" });
    const upload = {
      id: admin.uid, householdId: "home", userId: admin.uid, displayName: "ADMIN",
      role: "MEMBER", active: true, removedAt: null, email: "admin@example.com",
    };
    assert.equal(await writeAs(admin, path, upload, Object.keys(upload)), 200);
    assert.equal((await db.doc(path).get()).get("role"), "MEMBER");
  });

  it("let an admin leave without touching role, but never raise it", async () => {
    const { admin, member } = await seed();
    const leave = { active: false, removedAt: "2026-01-01" };
    const mask = ["active", "removedAt", "email"];
    assert.equal(await writeAs(member, `households/home/members/${member.uid}`, { ...leave, role: "ADMIN" }, [
      ...mask, "role",
    ]), 403);
    assert.equal(await writeAs(admin, `households/home/members/${admin.uid}`, leave, mask), 200);
  });

  it("keep a member's chore creator when the owner re-uploads every chore", async () => {
    const { owner, member } = await seed();
    const path = "households/home/chores/chore-1";
    const chore = {
      id: "chore-1", householdId: "home", name: "Dishes", isActive: true,
      createdAt: "2026-01-01", deletedAt: null, frequencyDays: null, category: "OTHER",
    };
    assert.equal(await writeAs(member, path, { ...chore, createdByUserId: member.uid }), 200);
    assert.equal(await writeAs(owner, path, { ...chore, name: "Kitchen dishes" }, Object.keys(chore)), 200);
    assert.equal((await db.doc(path).get()).get("createdByUserId"), member.uid);
  });
});
