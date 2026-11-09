import { Firestore } from "firebase-admin/firestore";

const MANAGER_ROLES = ["OWNER", "ADMIN"];

// Who onInviteAccepted notifies: the invite's creator (admins stamp createdByUserId when they
// upload one) while they're still an active owner or admin, otherwise the household owner. That
// covers invites without the stamp - the owner's own uploads and older app versions - and a creator
// who has since left, been removed or been demoted, and so can't turn these pushes off any more.
export async function inviteRecipient(
  db: Firestore,
  householdId: string,
  createdByUserId: unknown,
  ownerUserId: string,
): Promise<string> {
  if (typeof createdByUserId !== "string" || createdByUserId.length === 0) {
    return ownerUserId;
  }
  const creator = await db.doc(`households/${householdId}/members/${createdByUserId}`).get();
  const isActiveManager =
    creator.exists && creator.get("active") !== false && MANAGER_ROLES.includes(creator.get("role"));
  return isActiveManager ? createdByUserId : ownerUserId;
}
