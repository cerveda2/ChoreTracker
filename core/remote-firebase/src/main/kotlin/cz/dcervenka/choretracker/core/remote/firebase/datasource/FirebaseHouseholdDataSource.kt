package cz.dcervenka.choretracker.core.remote.firebase.datasource

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.core.model.sync.HouseholdSnapshot
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import cz.dcervenka.choretracker.core.remote.firebase.runtime.FirebaseRuntimeConfigurator
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Instant

private const val USERS_COLLECTION = "users"
private const val HOUSEHOLDS_COLLECTION = "households"
private const val MEMBERS_COLLECTION = "members"
private const val CHORES_COLLECTION = "chores"
private const val COMPLETIONS_COLLECTION = "completions"
private const val INVITES_COLLECTION = "invites"
private const val FIRESTORE_BATCH_LIMIT = 500

@Suppress("TooManyFunctions")
@Singleton
class FirebaseHouseholdDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RemoteHouseholdDataSource {

    init {
        FirebaseRuntimeConfigurator.configure(context)
    }

    private val firestore: FirebaseFirestore?
        get() = if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseFirestore.getInstance()

    override suspend fun upsertHouseholdSnapshot(snapshot: HouseholdSnapshot, userId: String): EmptyResult {
        Timber.d(
            "upsertHouseholdSnapshot: householdId=${snapshot.household.id} " +
                "chores=${snapshot.chores.size} completions=${snapshot.completions.size}",
        )
        val db = firestore ?: return AppResult.Error("Firebase isn't configured yet.")
        return runCatching {
            val householdRef = db.collection(HOUSEHOLDS_COLLECTION).document(snapshot.household.id)
            awaitTask(
                householdRef.set(
                    mapOf(
                        "id" to snapshot.household.id,
                        "name" to snapshot.household.name,
                        "ownerUserId" to snapshot.household.ownerUserId,
                        "inviteCode" to snapshot.household.inviteCode,
                        "createdAt" to snapshot.household.createdAt.asTimestamp(),
                    ),
                    SetOptions.merge(),
                ),
            )

            upsertMembershipData(
                db = db,
                householdRef = householdRef,
                snapshot = snapshot,
            )
            upsertHouseholdContent(
                db = db,
                householdRef = householdRef,
                snapshot = snapshot,
            )
            batchUserFallback(db, userId = userId, householdId = snapshot.household.id)
            Timber.d("upsertHouseholdSnapshot: success")
            AppResult.Success(Unit)
        }.getOrElse { error ->
            Timber.e(error, "upsertHouseholdSnapshot: failed")
            AppResult.Error(
                error.message ?: "Unable to sync household data.",
                error,
            )
        }
    }

    private suspend fun upsertMembershipData(
        db: FirebaseFirestore,
        householdRef: DocumentReference,
        snapshot: HouseholdSnapshot,
    ) {
        if (snapshot.members.isEmpty()) return

        val membershipBatch = db.batch()
        snapshot.members.forEach { member ->
            val memberDocumentId = member.userId ?: member.id
            membershipBatch.set(
                householdRef.collection(MEMBERS_COLLECTION).document(memberDocumentId),
                buildMap {
                    put("id", member.id)
                    put("householdId", member.householdId)
                    put("userId", member.userId)
                    put("displayName", member.displayName)
                    put("role", member.role.name)
                    put("active", true)
                    member.email?.let { put("email", it) }
                },
                SetOptions.merge(),
            )
            member.userId?.let { memberUserId ->
                membershipBatch.set(
                    db.collection(USERS_COLLECTION).document(memberUserId),
                    mapOf(
                        "userId" to memberUserId,
                        "householdId" to snapshot.household.id,
                        "displayName" to member.displayName,
                        "updatedAt" to Timestamp.now(),
                    ),
                    SetOptions.merge(),
                )
            }
        }
        awaitTask(membershipBatch.commit())
    }

    private suspend fun upsertHouseholdContent(
        db: FirebaseFirestore,
        householdRef: DocumentReference,
        snapshot: HouseholdSnapshot,
    ) {
        val writes = buildList<Pair<DocumentReference, Map<String, Any?>>> {
            snapshot.chores.forEach { chore ->
                add(
                    householdRef.collection(CHORES_COLLECTION).document(chore.id) to mapOf(
                        "id" to chore.id,
                        "householdId" to chore.householdId,
                        "name" to chore.name,
                        "isActive" to chore.isActive,
                        "createdAt" to chore.createdAt.asTimestamp(),
                        "deletedAt" to chore.deletedAt?.asTimestamp(),
                        "frequencyDays" to chore.frequencyDays,
                        "category" to chore.category.name,
                    ),
                )
            }
            snapshot.completions.forEach { completion ->
                add(
                    householdRef.collection(COMPLETIONS_COLLECTION).document(completion.id) to mapOf(
                        "id" to completion.id,
                        "householdId" to completion.householdId,
                        "choreId" to completion.choreId,
                        "createdAt" to completion.createdAt.asTimestamp(),
                        "createdByUserId" to completion.createdByUserId,
                        "note" to completion.note,
                        "participantMemberIds" to completion.participantMemberIds,
                    ),
                )
            }
            snapshot.invites.forEach { invite ->
                add(
                    householdRef.collection(INVITES_COLLECTION).document(invite.id) to buildMap {
                        put("id", invite.id)
                        put("householdId", invite.householdId)
                        put("code", invite.code)
                        put("createdAt", invite.createdAt.asTimestamp())
                        invite.consumedAt?.let { put("consumedAt", it.asTimestamp()) }
                        invite.targetMemberId?.let { put("targetMemberId", it) }
                        invite.consumedByMemberId?.let { put("consumedByMemberId", it) }
                    },
                )
            }
        }

        writes.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data, SetOptions.merge()) }
            awaitTask(batch.commit())
        }
    }

    override suspend fun upsertMemberSnapshot(
        householdId: String,
        member: HouseholdMember,
        completions: List<ChoreCompletion>,
        userId: String,
    ): EmptyResult {
        Timber.d("upsertMemberSnapshot: householdId=$householdId completions=${completions.size}")
        val db = firestore ?: return AppResult.Error("Firebase isn't configured yet.")
        return runCatching {
            val householdRef = db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            val memberDocumentId = member.userId ?: member.id
            val memberBatch = db.batch()
            memberBatch.set(
                householdRef.collection(MEMBERS_COLLECTION).document(memberDocumentId),
                buildMap {
                    put("id", member.id)
                    put("householdId", member.householdId)
                    put("userId", member.userId)
                    put("displayName", member.displayName)
                    put("role", member.role.name)
                    put("active", true)
                    member.email?.let { put("email", it) }
                },
                SetOptions.merge(),
            )
            memberBatch.set(
                db.collection(USERS_COLLECTION).document(userId),
                mapOf(
                    "userId" to userId,
                    "householdId" to householdId,
                    "displayName" to member.displayName,
                    "updatedAt" to Timestamp.now(),
                ),
                SetOptions.merge(),
            )
            awaitTask(memberBatch.commit())

            completions.map { completion ->
                householdRef.collection(COMPLETIONS_COLLECTION).document(completion.id) to mapOf(
                    "id" to completion.id,
                    "householdId" to completion.householdId,
                    "choreId" to completion.choreId,
                    "createdAt" to completion.createdAt.asTimestamp(),
                    "createdByUserId" to completion.createdByUserId,
                    "note" to completion.note,
                    "participantMemberIds" to completion.participantMemberIds,
                )
            }.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (ref, data) -> batch.set(ref, data, SetOptions.merge()) }
                awaitTask(batch.commit())
            }

            Timber.d("upsertMemberSnapshot: success")
            AppResult.Success(Unit)
        }.getOrElse { error ->
            Timber.e(error, "upsertMemberSnapshot: failed")
            AppResult.Error(error.message ?: "Unable to sync member data.", error)
        }
    }

    override suspend fun deleteMember(householdId: String, firestoreDocId: String): EmptyResult {
        Timber.d("deleteMember: householdId=$householdId firestoreDocId=$firestoreDocId")
        val db = firestore ?: return AppResult.Error("Firebase isn't configured yet.")
        return runCatching {
            awaitTask(
                db.collection(HOUSEHOLDS_COLLECTION)
                    .document(householdId)
                    .collection(MEMBERS_COLLECTION)
                    .document(firestoreDocId)
                    .delete(),
            )
            Timber.d("deleteMember: success")
            AppResult.Success(Unit)
        }.getOrElse { error ->
            Timber.e(error, "deleteMember: failed")
            AppResult.Error(error.message ?: "Unable to delete member.", error)
        }
    }

    override suspend fun fetchInviteByCode(code: String): AppResult<Invite?> {
        Timber.d("fetchInviteByCode: code=$code")
        val db = firestore ?: return AppResult.Error("Firebase isn't configured yet.")
        return runCatching {
            val doc = awaitTask(
                db.collectionGroup(INVITES_COLLECTION)
                    .whereEqualTo("code", code)
                    .limit(1)
                    .get(),
            ).documents.firstOrNull()
            AppResult.Success(doc?.asInvite(doc.getString("householdId").orEmpty()))
        }.getOrElse { error ->
            Timber.e(error, "fetchInviteByCode: failed")
            AppResult.Error(error.message ?: "Unable to fetch invite.", error)
        }
    }

    override suspend fun markInviteConsumed(householdId: String, inviteId: String, consumedAt: Instant, consumedByMemberId: String): EmptyResult {
        Timber.d("markInviteConsumed: householdId=$householdId inviteId=$inviteId consumedByMemberId=$consumedByMemberId")
        val db = firestore ?: return AppResult.Error("Firebase isn't configured yet.")
        return runCatching {
            awaitTask(
                db.collection(HOUSEHOLDS_COLLECTION)
                    .document(householdId)
                    .collection(INVITES_COLLECTION)
                    .document(inviteId)
                    .update(
                        "consumedAt", consumedAt.asTimestamp(),
                        "consumedByMemberId", consumedByMemberId,
                    ),
            )
            Timber.d("markInviteConsumed: success")
            AppResult.Success(Unit)
        }.getOrElse { error ->
            Timber.e(error, "markInviteConsumed: failed")
            AppResult.Error(error.message ?: "Unable to mark invite as consumed.", error)
        }
    }

    override suspend fun deleteCompletion(householdId: String, completionId: String): EmptyResult {
        Timber.d("deleteCompletion: householdId=$householdId completionId=$completionId")
        val db = firestore ?: return AppResult.Error("Firebase isn't configured yet.")
        return runCatching {
            awaitTask(
                db.collection(HOUSEHOLDS_COLLECTION)
                    .document(householdId)
                    .collection(COMPLETIONS_COLLECTION)
                    .document(completionId)
                    .delete(),
            )
            Timber.d("deleteCompletion: success")
            AppResult.Success(Unit)
        }.getOrElse { error ->
            Timber.e(error, "deleteCompletion: failed")
            AppResult.Error(error.message ?: "Unable to delete completion.", error)
        }
    }

    override suspend fun fetchHouseholdSnapshot(userId: String): AppResult<HouseholdSnapshot?> {
        Timber.d("fetchHouseholdSnapshot: userId=$userId")
        val db = firestore ?: return AppResult.Error("Firebase isn't configured yet.")
        return runCatching {
            val householdId = resolveHouseholdId(db, userId)
            if (householdId == null) {
                Timber.d("fetchHouseholdSnapshot: no household found for userId=$userId")
                AppResult.Success(null)
            } else {
                val householdRef = db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
                val householdDoc = awaitTask(householdRef.get())
                if (!householdDoc.exists()) {
                    AppResult.Success(null)
                } else {
                    val members = awaitTask(householdRef.collection(MEMBERS_COLLECTION).get()).documents
                        .map { it.asMember(currentUserId = userId, householdId = householdId) }
                    val chores = awaitTask(householdRef.collection(CHORES_COLLECTION).get()).documents
                        .map { it.asChore(householdId) }
                    val completions = awaitTask(householdRef.collection(COMPLETIONS_COLLECTION).get()).documents
                        .map { it.asCompletion(householdId) }
                    val invites = awaitTask(householdRef.collection(INVITES_COLLECTION).get()).documents
                        .map { it.asInvite(householdId) }

                    Timber.d(
                        "fetchHouseholdSnapshot: fetched householdId=$householdId " +
                            "members=${members.size} chores=${chores.size} completions=${completions.size}",
                    )
                    AppResult.Success(
                        HouseholdSnapshot(
                            household = householdDoc.asHousehold(householdId),
                            members = members,
                            chores = chores,
                            completions = completions,
                            invites = invites,
                        ),
                    )
                }
            }
        }.getOrElse { error ->
            Timber.e(error, "fetchHouseholdSnapshot: failed")
            AppResult.Error(
                error.message ?: "Unable to load household data.",
                error,
            )
        }
    }

    private suspend fun resolveHouseholdId(db: FirebaseFirestore, userId: String): String? {
        val directHouseholdId = runCatching {
            awaitTask(db.collection(USERS_COLLECTION).document(userId).get())
                .getString("householdId")
        }.getOrNull()
        if (directHouseholdId != null) return directHouseholdId

        return runCatching {
            awaitTask(
                db.collectionGroup(MEMBERS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .limit(1)
                    .get(),
            ).documents.firstOrNull()?.getString("householdId")
        }.getOrElse { e ->
            Timber.w(e, "resolveHouseholdId: collectionGroup query failed for userId=$userId")
            null
        }
    }

    private suspend fun batchUserFallback(
        db: FirebaseFirestore,
        userId: String,
        householdId: String,
    ) {
        awaitTask(
            db.collection(USERS_COLLECTION).document(userId).set(
                mapOf(
                    "userId" to userId,
                    "householdId" to householdId,
                    "updatedAt" to Timestamp.now(),
                ),
                SetOptions.merge(),
            ),
        )
    }

    private fun DocumentSnapshot.asHousehold(householdId: String): Household = Household(
        id = getString("id") ?: householdId,
        name = getString("name").orEmpty(),
        ownerUserId = getString("ownerUserId").orEmpty(),
        inviteCode = getString("inviteCode").orEmpty(),
        createdAt = getTimestamp("createdAt").asInstant(),
    )

    private fun DocumentSnapshot.asMember(currentUserId: String, householdId: String): HouseholdMember = HouseholdMember(
        id = getString("id") ?: id,
        householdId = getString("householdId") ?: householdId,
        userId = getString("userId"),
        displayName = getString("displayName").orEmpty(),
        role = getString("role")
            ?.let(HouseholdRole::valueOf)
            ?: HouseholdRole.MEMBER,
        isCurrentUser = getString("userId") == currentUserId,
        email = getString("email"),
    )

    private fun DocumentSnapshot.asChore(householdId: String): Chore = Chore(
        id = getString("id") ?: id,
        householdId = getString("householdId") ?: householdId,
        name = getString("name").orEmpty(),
        isActive = getBoolean("isActive") ?: true,
        createdAt = getTimestamp("createdAt").asInstant(),
        deletedAt = getTimestamp("deletedAt")?.asInstant(),
        frequencyDays = getLong("frequencyDays")?.toInt(),
        category = getString("category")
            ?.let { runCatching { ChoreCategory.valueOf(it) }.getOrNull() }
            ?: ChoreCategory.OTHER,
    )

    private fun DocumentSnapshot.asCompletion(householdId: String): ChoreCompletion = ChoreCompletion(
        id = getString("id") ?: id,
        householdId = getString("householdId") ?: householdId,
        choreId = getString("choreId").orEmpty(),
        createdAt = getTimestamp("createdAt").asInstant(),
        createdByUserId = getString("createdByUserId").orEmpty(),
        note = getString("note"),
        participantMemberIds = get("participantMemberIds")
            .let { value -> (value as? List<*>)?.mapNotNull { it as? String } }
            .orEmpty(),
    )

    private fun DocumentSnapshot.asInvite(householdId: String): Invite = Invite(
        id = getString("id") ?: id,
        householdId = getString("householdId") ?: householdId,
        code = getString("code").orEmpty(),
        createdAt = getTimestamp("createdAt").asInstant(),
        consumedAt = getTimestamp("consumedAt")?.asInstant(),
        targetMemberId = getString("targetMemberId"),
        consumedByMemberId = getString("consumedByMemberId"),
    )
}

private suspend fun <T> awaitTask(task: Task<T>): T =
    suspendCancellableCoroutineCompat(task)

private fun Timestamp?.asInstant(): Instant = this?.let { firebaseTimestamp ->
    Instant.fromEpochMilliseconds(firebaseTimestamp.toDate().time)
} ?: Clock.System.now()

private fun Instant.asTimestamp(): Timestamp = Timestamp(java.util.Date(toEpochMilliseconds()))

private suspend fun <T> suspendCancellableCoroutineCompat(task: Task<T>): T =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { continuation.resume(it) }
        task.addOnFailureListener { continuation.resumeWithException(it) }
    }
