package cz.dcervenka.choretracker.core.remote.firebase.datasource

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.core.model.sync.HouseholdSnapshot
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import cz.dcervenka.choretracker.core.remote.firebase.runtime.FIREBASE_FUNCTIONS_REGION
import cz.dcervenka.choretracker.core.remote.firebase.runtime.FirebaseRuntimeConfigurator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val USERS_COLLECTION = "users"
private const val HOUSEHOLDS_COLLECTION = "households"
private const val MEMBERS_COLLECTION = "members"
private const val CHORES_COLLECTION = "chores"
private const val COMPLETIONS_COLLECTION = "completions"
private const val INVITES_COLLECTION = "invites"
private const val INVITE_CODES_COLLECTION = "inviteCodes"
private const val FIRESTORE_BATCH_LIMIT = 500
private val FIRESTORE_TASK_TIMEOUT = 20.seconds

@Suppress("TooManyFunctions", "LargeClass")
@Singleton
class FirebaseHouseholdDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RemoteHouseholdDataSource {

    init {
        FirebaseRuntimeConfigurator.configure(context)
    }

    private val firestore: FirebaseFirestore?
        get() = if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseFirestore.getInstance()

    private val firebaseFunctions: FirebaseFunctions?
        get() = if (FirebaseApp.getApps(context).isEmpty()) {
            null
        } else {
            FirebaseFunctions.getInstance(FIREBASE_FUNCTIONS_REGION)
        }

    override fun observeMembers(householdId: String, currentUserId: String): Flow<List<HouseholdMember>> = callbackFlow {
        val db = firestore
        if (db == null) {
            close()
            return@callbackFlow
        }
        val registration = db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            .collection(MEMBERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeMembers: listen failed householdId=$householdId")
                    if (error.isPermissionDenied()) {
                        // Rules deny this once currentUserId is no longer a member - an empty
                        // list makes RealtimeSyncApplier.applyMembers's existing "not a member
                        // anymore" check fire and clear local data, instead of this listener
                        // just going silent forever.
                        trySend(emptyList())
                    }
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { it.asMember(currentUserId, householdId) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override fun observeCompletions(householdId: String): Flow<List<ChoreCompletion>> = callbackFlow {
        val db = firestore
        if (db == null) {
            close()
            return@callbackFlow
        }
        val registration = db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            .collection(COMPLETIONS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeCompletions: listen failed householdId=$householdId")
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { it.asCompletion(householdId) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override fun observeInvites(householdId: String): Flow<List<Invite>> = callbackFlow {
        val db = firestore
        if (db == null) {
            close()
            return@callbackFlow
        }
        val registration = db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            .collection(INVITES_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeInvites: listen failed householdId=$householdId")
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { it.asInvite(householdId) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override fun observeChores(householdId: String): Flow<List<Chore>> = callbackFlow {
        val db = firestore
        if (db == null) {
            close()
            return@callbackFlow
        }
        val registration = db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            .collection(CHORES_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeChores: listen failed householdId=$householdId")
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { it.asChore(householdId) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun upsertHouseholdSnapshot(snapshot: HouseholdSnapshot, userId: String): EmptyResult {
        Timber.d(
            "upsertHouseholdSnapshot: householdId=${snapshot.household.id} " +
                "completions=${snapshot.completions.size}",
        )
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching {
            val householdRef = db.collection(HOUSEHOLDS_COLLECTION).document(snapshot.household.id)
            if (snapshot.householdWriteFields.isNotEmpty()) {
                val fields = mapOf(
                    "id" to snapshot.household.id,
                    "name" to snapshot.household.name,
                    "ownerUserId" to snapshot.household.ownerUserId,
                    "inviteCode" to snapshot.household.inviteCode,
                    "createdAt" to snapshot.household.createdAt.asTimestamp(),
                ).filterKeys { it in snapshot.householdWriteFields }
                awaitTask(householdRef.set(fields, SetOptions.merge()))
            }

            upsertMembershipData(
                db = db,
                householdRef = householdRef,
                snapshot = snapshot,
                currentUserId = userId,
            )
            upsertHouseholdContent(
                db = db,
                householdRef = householdRef,
                snapshot = snapshot,
            )
            batchUserFallback(db, userId = userId, householdId = snapshot.household.id)
            Timber.d("upsertHouseholdSnapshot: success")
            AppResult.Success(Unit)
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "upsertHouseholdSnapshot: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    private suspend fun upsertMembershipData(
        db: FirebaseFirestore,
        householdRef: DocumentReference,
        snapshot: HouseholdSnapshot,
        currentUserId: String,
    ) {
        if (snapshot.members.isEmpty()) return

        val membersRef = householdRef.collection(MEMBERS_COLLECTION)
        // A transaction because this device's copy of a member can predate their departure or return, and
        // writing it would undo that. filterNot finishes first, as Firestore needs reads before writes.
        awaitTask(
            db.runTransaction { transaction ->
                snapshot.members
                    .filterNot { member ->
                        // A placeholder has no account to leave with. Rules also deny member reads until the
                        // current user's own doc exists, so checking only other accounts keeps a first push working.
                        val otherUserId = member.userId?.takeIf { it != currentUserId } ?: return@filterNot false
                        val remote = transaction.get(membersRef.document(otherUserId))
                        member.isOutdatedBy(remote.getBoolean("active"), remote.getString("joinedViaInviteId"))
                    }
                    .forEach { member ->
                        val memberDocumentId = member.userId ?: member.id
                        transaction.set(
                            membersRef.document(memberDocumentId),
                            member.toFirestoreFields(
                                writeRole = member.userId == currentUserId || member.removedAt != null,
                            ),
                            SetOptions.merge(),
                        )
                        if (member.userId != null) {
                            transaction.delete(membersRef.document(member.id))
                        }
                        // Not for departed members: this would restore the household pointer of someone
                        // who left and recreate the user record of someone who deleted their account.
                        member.userId?.takeIf { member.removedAt == null }?.let { memberUserId ->
                            transaction.set(
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
            },
        )
    }

    private suspend fun upsertHouseholdContent(
        db: FirebaseFirestore,
        householdRef: DocumentReference,
        snapshot: HouseholdSnapshot,
    ) {
        val writes = buildList<Pair<DocumentReference, Map<String, Any?>>> {
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
                // Lets fetchInviteByCode resolve a code to (householdId, id) with a single get()
                // instead of a listable collectionGroup query - see this doc's own rule.
                add(
                    db.collection(INVITE_CODES_COLLECTION).document(invite.code) to mapOf(
                        "householdId" to invite.householdId,
                        "inviteId" to invite.id,
                    ),
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
        writeRole: Boolean,
    ): EmptyResult {
        Timber.d("upsertMemberSnapshot: householdId=$householdId completions=${completions.size}")
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching {
            val householdRef = db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            val memberDocumentId = member.userId ?: member.id
            val memberBatch = db.batch()
            memberBatch.set(
                householdRef.collection(MEMBERS_COLLECTION).document(memberDocumentId),
                member.toFirestoreFields(writeRole = writeRole),
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
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "upsertMemberSnapshot: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    override suspend fun upsertChore(chore: Chore): EmptyResult = remoteWrite("upsertChore") { db ->
        val fields = buildMap<String, Any?> {
            put("id", chore.id)
            put("householdId", chore.householdId)
            put("name", chore.name)
            put("isActive", chore.isActive)
            put("createdAt", chore.createdAt.asTimestamp())
            put("deletedAt", chore.deletedAt?.asTimestamp())
            put("frequencyDays", chore.frequencyDays)
            put("category", chore.category.name)
            chore.createdByUserId?.let { put("createdByUserId", it) }
            put("rotationMemberIds", chore.rotationMemberIds)
            put("rotationStartedAt", chore.rotationStartedAt?.asTimestamp())
        }
        awaitTask(
            db.collection(HOUSEHOLDS_COLLECTION).document(chore.householdId)
                .collection(CHORES_COLLECTION).document(chore.id).set(fields, SetOptions.merge()),
        )
    }

    override suspend fun upsertCompletion(completion: ChoreCompletion): EmptyResult = remoteWrite(
        "upsertCompletion",
    ) { db ->
        awaitTask(
            db.collection(HOUSEHOLDS_COLLECTION).document(completion.householdId)
                .collection(COMPLETIONS_COLLECTION).document(completion.id)
                .set(
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
                ),
        )
    }

    override suspend fun updateHouseholdName(householdId: String, name: String): EmptyResult =
        remoteWrite("updateHouseholdName") { db ->
            awaitTask(db.collection(HOUSEHOLDS_COLLECTION).document(householdId).update("name", name))
        }

    override suspend fun upsertInvite(invite: Invite): EmptyResult = remoteWrite("upsertInvite") { db ->
        val householdRef = db.collection(HOUSEHOLDS_COLLECTION).document(invite.householdId)
        val batch = db.batch()
        batch.set(
            householdRef.collection(INVITES_COLLECTION).document(invite.id),
            buildMap<String, Any?> {
                put("id", invite.id)
                put("householdId", invite.householdId)
                put("code", invite.code)
                put("createdAt", invite.createdAt.asTimestamp())
                invite.targetMemberId?.let { put("targetMemberId", it) }
            },
            SetOptions.merge(),
        )
        batch.set(
            db.collection(INVITE_CODES_COLLECTION).document(invite.code),
            mapOf("householdId" to invite.householdId, "inviteId" to invite.id),
            SetOptions.merge(),
        )
        if (invite.targetMemberId == null) batch.update(householdRef, "inviteCode", invite.code)
        awaitTask(batch.commit())
    }

    override suspend fun upsertPlaceholder(member: HouseholdMember): EmptyResult = remoteWrite(
        "upsertPlaceholder",
    ) { db ->
        awaitTask(
            db.collection(HOUSEHOLDS_COLLECTION).document(member.householdId)
                .collection(MEMBERS_COLLECTION).document(member.id)
                .set(member.toFirestoreFields(writeRole = true), SetOptions.merge()),
        )
    }

    override suspend fun removeMember(householdId: String, memberDocId: String): EmptyResult =
        remoteWrite("removeMember") { db ->
            awaitTask(
                db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
                    .collection(MEMBERS_COLLECTION).document(memberDocId)
                    .set(
                        mapOf(
                            "active" to false,
                            "removedAt" to Timestamp.now(),
                            "email" to FieldValue.delete(),
                            "role" to HouseholdRole.MEMBER.name,
                        ),
                        SetOptions.merge(),
                    ),
            )
        }

    override suspend fun setMemberRole(householdId: String, memberDocId: String, role: String): EmptyResult =
        remoteWrite("setMemberRole") { db ->
            awaitTask(
                db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
                    .collection(MEMBERS_COLLECTION).document(memberDocId).update("role", role),
            )
        }

    private suspend fun remoteWrite(label: String, action: suspend (FirebaseFirestore) -> Unit): EmptyResult {
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching { action(db) }.rethrowCancellation().fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { error ->
                Timber.e(error, "$label: failed")
                AppResult.Error(error.toAppError(), error)
            },
        )
    }

    override suspend fun fetchInviteByCode(code: String): AppResult<Invite?> {
        Timber.d("fetchInviteByCode: code=$code")
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching {
            // Two gets instead of the old collectionGroup(INVITES_COLLECTION).whereEqualTo("code",
            // ...) query: a joining user isn't a household member yet, so they can't be granted a
            // listable query over every household's invites without letting anyone enumerate
            // every code. inviteCodes/{code} is a "get"-only pointer - see its rule.
            val pointer = awaitTask(db.collection(INVITE_CODES_COLLECTION).document(code).get())
            val householdId = pointer.getString("householdId")
            val inviteId = pointer.getString("inviteId")
            if (householdId == null || inviteId == null) {
                AppResult.Success(null)
            } else {
                val inviteDoc = awaitTask(
                    db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
                        .collection(INVITES_COLLECTION).document(inviteId)
                        .get(),
                )
                AppResult.Success(inviteDoc.takeIf { it.exists() }?.asInvite(householdId))
            }
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "fetchInviteByCode: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    // By userId rather than members/{uid}: rules deny a non-member's get of a member doc that doesn't exist.
    override suspend fun fetchOwnMemberId(householdId: String, userId: String): AppResult<String?> {
        Timber.d("fetchOwnMemberId: householdId=$householdId")
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching {
            val ownDoc = awaitTask(
                db.collectionGroup(MEMBERS_COLLECTION).whereEqualTo("userId", userId).get(Source.SERVER),
            ).documents.firstOrNull { it.getString("householdId") == householdId }
            AppResult.Success(ownDoc?.let { it.getString("id") ?: it.id })
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "fetchOwnMemberId: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    // Server only: offline, a default get can answer from an empty cache, which reads as everyone having left.
    override suspend fun fetchMembers(householdId: String, currentUserId: String): AppResult<List<HouseholdMember>> {
        Timber.d("fetchMembers: householdId=$householdId")
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching {
            val members = awaitTask(
                db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
                    .collection(MEMBERS_COLLECTION)
                    .get(Source.SERVER),
            ).documents.map { it.asMember(currentUserId, householdId) }
            AppResult.Success(members)
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "fetchMembers: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    override suspend fun markInviteConsumed(
        householdId: String,
        inviteId: String,
        consumedAt: Instant,
        consumedByMemberId: String,
    ): EmptyResult {
        Timber.d("markInviteConsumed: householdId=$householdId inviteId=$inviteId")
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching {
            awaitTask(
                db.collection(HOUSEHOLDS_COLLECTION)
                    .document(householdId)
                    .collection(INVITES_COLLECTION)
                    .document(inviteId)
                    .update(
                        mapOf(
                            "consumedAt" to consumedAt.asTimestamp(),
                            "consumedByMemberId" to consumedByMemberId,
                        ),
                    ),
            )
            Timber.d("markInviteConsumed: success")
            AppResult.Success(Unit)
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "markInviteConsumed: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    override suspend fun deleteCompletion(householdId: String, completionId: String): EmptyResult {
        Timber.d("deleteCompletion: householdId=$householdId completionId=$completionId")
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
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
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "deleteCompletion: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    override suspend fun transferOwnership(
        householdId: String,
        newOwnerUserId: String,
        newOwnerMemberDocId: String,
        previousOwnerMemberDocId: String,
    ): EmptyResult {
        Timber.d("transferOwnership: householdId=$householdId newOwnerUserId=$newOwnerUserId")
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching {
            val householdRef = db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            val members = householdRef.collection(MEMBERS_COLLECTION)
            // One batch: every write is evaluated against the pre-batch state where the caller is
            // still isHouseholdOwner, so all three pass the rules atomically. Rules and UI both
            // read role for the new owner's and previous owner's subsequent actions.
            val batch = db.batch()
            batch.update(householdRef, "ownerUserId", newOwnerUserId)
            batch.set(
                members.document(newOwnerMemberDocId),
                mapOf("role" to HouseholdRole.OWNER.name),
                SetOptions.merge(),
            )
            batch.set(
                members.document(previousOwnerMemberDocId),
                mapOf("role" to HouseholdRole.ADMIN.name),
                SetOptions.merge(),
            )
            awaitTask(batch.commit())
            Timber.d("transferOwnership: success")
            AppResult.Success(Unit)
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "transferOwnership: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    override suspend fun leaveHousehold(householdId: String, selfMemberDocId: String): EmptyResult {
        Timber.d("leaveHousehold: householdId=$householdId selfMemberDocId=$selfMemberDocId")
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching {
            val householdRef = db.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            val batch = db.batch()
            // Only active/removedAt/email on the caller's own member doc - the self-leave rules branch
            // rejects anything else. NOT the full member map (upsertMemberSnapshot), which would
            // also try to re-push completions after active flips to false and get denied.
            batch.set(
                householdRef.collection(MEMBERS_COLLECTION).document(selfMemberDocId),
                mapOf(
                    "active" to false,
                    "removedAt" to Timestamp.now(),
                    "email" to FieldValue.delete(),
                    "role" to HouseholdRole.MEMBER.name,
                ),
                SetOptions.merge(),
            )
            // Drop the household pointer so a later restoreHouseholdForUser resolves to no
            // household instead of hitting PERMISSION_DENIED and writing a blank snapshot.
            batch.set(
                db.collection(USERS_COLLECTION).document(selfMemberDocId),
                mapOf("householdId" to FieldValue.delete()),
                SetOptions.merge(),
            )
            awaitTask(batch.commit())
            Timber.d("leaveHousehold: success")
            AppResult.Success(Unit)
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "leaveHousehold: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    override suspend fun deleteHousehold(householdId: String): EmptyResult {
        Timber.d("deleteHousehold: householdId=$householdId")
        val functions = firebaseFunctions ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        return runCatching {
            awaitTask(functions.getHttpsCallable("deleteHousehold").call(mapOf("householdId" to householdId)))
            Timber.d("deleteHousehold: success")
            AppResult.Success(Unit)
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "deleteHousehold: failed")
            AppResult.Error(error.toAppError(), error)
        }
    }

    override suspend fun fetchHouseholdSnapshot(userId: String): AppResult<HouseholdSnapshot?> {
        Timber.d("fetchHouseholdSnapshot: userId=$userId")
        val db = firestore ?: return AppResult.Error(AppError.FIREBASE_NOT_CONFIGURED)
        var resolvedHouseholdId: String? = null
        return runCatching {
            val householdId = resolveHouseholdId(db, userId)
            resolvedHouseholdId = householdId
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
        }.rethrowCancellation().getOrElse { error ->
            Timber.e(error, "fetchHouseholdSnapshot: failed")
            val householdId = resolvedHouseholdId
            if (householdId != null && error.isPermissionDenied()) {
                // resolveHouseholdId already succeeded, so the household id itself is real - this
                // failure is rules denying the household/subcollection reads, which only happens
                // once userId is no longer a member. An empty-members snapshot makes
                // restoreHouseholdForUser's existing "not a member anymore" check fire and clear
                // local data, instead of surfacing as a generic sync error forever.
                Timber.w("fetchHouseholdSnapshot: permission denied for householdId=$householdId, userId=$userId")
                AppResult.Success(emptyMembersSnapshot(householdId))
            } else {
                AppResult.Error(error.toAppError(), error)
            }
        }
    }

    private fun emptyMembersSnapshot(householdId: String) = HouseholdSnapshot(
        household = Household(
            id = householdId,
            name = "",
            ownerUserId = "",
            inviteCode = "",
            createdAt = Instant.fromEpochMilliseconds(0),
        ),
        members = emptyList(),
        chores = emptyList(),
        completions = emptyList(),
        invites = emptyList(),
    )

    private suspend fun resolveHouseholdId(db: FirebaseFirestore, userId: String): String? {
        val directHouseholdId = runCatching {
            awaitTask(db.collection(USERS_COLLECTION).document(userId).get())
                .getString("householdId")
        }.rethrowCancellation().getOrNull()
        if (directHouseholdId != null) return directHouseholdId

        return runCatching {
            awaitTask(
                db.collectionGroup(MEMBERS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .limit(1)
                    .get(),
            ).documents.firstOrNull()?.getString("householdId")
        }.rethrowCancellation().getOrElse { e ->
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
            ?.let { runCatching { HouseholdRole.valueOf(it) }.getOrNull() }
            ?: HouseholdRole.MEMBER,
        isCurrentUser = getString("userId") == currentUserId,
        email = getString("email"),
        joinedViaInviteId = getString("joinedViaInviteId"),
        removedAt = getTimestamp("removedAt")?.asInstant(),
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
        createdByUserId = getString("createdByUserId"),
        rotationMemberIds = get("rotationMemberIds")
            .let { value -> (value as? List<*>)?.mapNotNull { it as? String } }
            .orEmpty(),
        rotationStartedAt = getTimestamp("rotationStartedAt")?.asInstant(),
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

// Firestore's offline persistence queues a write locally and its Task simply never completes
// until connectivity returns - without a timeout, every await here (and therefore every
// repository write, which all funnel through syncPendingOperations) would hang indefinitely
// while offline. TimeoutCancellationException is itself a CancellationException, so it's
// converted to a plain TimeoutException here rather than left to propagate -
// rethrowCancellation() (below) would otherwise treat a timeout as structured-concurrency
// cancellation instead of a business error, and it would never reach the AppResult.Error the
// caller is waiting for.
private suspend fun <T> awaitTask(task: Task<T>): T =
    try {
        withTimeout(FIRESTORE_TASK_TIMEOUT) { suspendCancellableCoroutineCompat(task) }
    } catch (e: TimeoutCancellationException) {
        throw IOException("Sync timed out - check your connection and try again.", e)
    }

// Result.getOrElse/getOrNull don't special-case CancellationException - without this, a coroutine
// cancelled mid-Firestore-call (e.g. its scope torn down) gets misreported as a business-logic
// AppResult.Error instead of letting the cancellation propagate as structured concurrency expects.
internal fun <T> Result<T>.rethrowCancellation(): Result<T> =
    onFailure { if (it is CancellationException) throw it }

internal fun Throwable.isPermissionDenied(): Boolean =
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

// Whether this copy of another account predates their own change: leaving, or rejoining through a new
// invite after this copy recorded their departure.
internal fun HouseholdMember.isOutdatedBy(remoteActive: Boolean?, remoteJoinedViaInviteId: String?): Boolean =
    if (removedAt == null) {
        remoteActive == false
    } else {
        remoteActive == true && remoteJoinedViaInviteId != joinedViaInviteId
    }

// A departed member's name stays so history keeps resolving, but their email has no further use.
internal fun HouseholdMember.toFirestoreFields(writeRole: Boolean = false): Map<String, Any?> = buildMap {
    put("id", id)
    put("householdId", householdId)
    put("userId", userId)
    put("displayName", displayName)
    if (writeRole) put("role", role.name)
    put("active", removedAt == null)
    put("removedAt", removedAt?.asTimestamp())
    if (removedAt != null) {
        put("email", FieldValue.delete())
    } else {
        email?.let { put("email", it) }
    }
    joinedViaInviteId?.let { put("joinedViaInviteId", it) }
}

private fun Timestamp?.asInstant(): Instant = this?.let { firebaseTimestamp ->
    Instant.fromEpochMilliseconds(firebaseTimestamp.toDate().time)
} ?: Clock.System.now()

private fun Instant.asTimestamp(): Timestamp = Timestamp(java.util.Date(toEpochMilliseconds()))

private suspend fun <T> suspendCancellableCoroutineCompat(task: Task<T>): T =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { continuation.resume(it) }
        task.addOnFailureListener { continuation.resumeWithException(it) }
    }
