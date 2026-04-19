package cz.dcervenka.choretracker.core.remote.firebase.datasource

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
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
import kotlin.time.Instant

private const val USERS_COLLECTION = "users"
private const val HOUSEHOLDS_COLLECTION = "households"
private const val MEMBERS_COLLECTION = "members"
private const val CHORES_COLLECTION = "chores"
private const val COMPLETIONS_COLLECTION = "completions"
private const val INVITES_COLLECTION = "invites"

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
        householdRef: com.google.firebase.firestore.DocumentReference,
        snapshot: HouseholdSnapshot,
    ) {
        if (snapshot.members.isEmpty()) return

        val membershipBatch = db.batch()
        snapshot.members.forEach { member ->
            val memberDocumentId = member.userId ?: member.id
            membershipBatch.set(
                householdRef.collection(MEMBERS_COLLECTION).document(memberDocumentId),
                mapOf(
                    "id" to member.id,
                    "householdId" to member.householdId,
                    "userId" to member.userId,
                    "displayName" to member.displayName,
                    "role" to member.role.name,
                    "active" to true,
                ),
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
        householdRef: com.google.firebase.firestore.DocumentReference,
        snapshot: HouseholdSnapshot,
    ) {
        val contentBatch = db.batch()
        var hasWrites = false

        snapshot.chores.forEach { chore ->
            hasWrites = true
            contentBatch.set(
                householdRef.collection(CHORES_COLLECTION).document(chore.id),
                mapOf(
                    "id" to chore.id,
                    "householdId" to chore.householdId,
                    "name" to chore.name,
                    "isActive" to chore.isActive,
                    "createdAt" to chore.createdAt.asTimestamp(),
                    "deletedAt" to chore.deletedAt?.asTimestamp(),
                    "frequencyDays" to chore.frequencyDays,
                    "category" to chore.category.name,
                ),
                SetOptions.merge(),
            )
        }

        snapshot.completions.forEach { completion ->
            hasWrites = true
            contentBatch.set(
                householdRef.collection(COMPLETIONS_COLLECTION).document(completion.id),
                mapOf(
                    "id" to completion.id,
                    "householdId" to completion.householdId,
                    "choreId" to completion.choreId,
                    "createdAt" to completion.createdAt.asTimestamp(),
                    "createdByUserId" to completion.createdByUserId,
                    "note" to completion.note,
                    "participantMemberIds" to completion.participantMemberIds,
                ),
                SetOptions.merge(),
            )
        }

        snapshot.invites.forEach { invite ->
            hasWrites = true
            contentBatch.set(
                householdRef.collection(INVITES_COLLECTION).document(invite.id),
                mapOf(
                    "id" to invite.id,
                    "householdId" to invite.householdId,
                    "code" to invite.code,
                    "createdAt" to invite.createdAt.asTimestamp(),
                    "consumedAt" to invite.consumedAt?.asTimestamp(),
                ),
                SetOptions.merge(),
            )
        }

        if (hasWrites) {
            awaitTask(contentBatch.commit())
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
        val directHouseholdId = awaitTask(db.collection(USERS_COLLECTION).document(userId).get())
            .getString("householdId")
        if (directHouseholdId != null) {
            return directHouseholdId
        }

        return awaitTask(
            db.collectionGroup(MEMBERS_COLLECTION)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get(),
        ).documents.firstOrNull()?.getString("householdId")
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
    )

    private fun Timestamp?.asInstant(): Instant = this?.let { firebaseTimestamp ->
        Instant.fromEpochMilliseconds(firebaseTimestamp.toDate().time)
    } ?: Instant.fromEpochMilliseconds(0)

    private fun Instant.asTimestamp(): Timestamp = Timestamp(java.util.Date(toEpochMilliseconds()))

    private suspend fun <T> awaitTask(task: Task<T>): T =
        suspendCancellableCoroutineCompat(task)
}

private suspend fun <T> suspendCancellableCoroutineCompat(task: Task<T>): T =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { continuation.resume(it) }
        task.addOnFailureListener { continuation.resumeWithException(it) }
    }
