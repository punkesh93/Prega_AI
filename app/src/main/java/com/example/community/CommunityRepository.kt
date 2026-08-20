package com.example.community

import com.example.stats.AppStats
import com.example.stats.StatEvent
import com.example.ui.coach.containsRedFlag
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Prega AI — Birth Club community, Firebase-backed (Phase 1, text-only).
 *
 * Shape of the world:
 *   profiles/{uid}                       handle, dueMonth, createdAt
 *   profiles/{uid}/blocks/{blockedUid}   private block list
 *   clubs/{yyyy-MM}                      name, createdAt   (doc id IS the due month)
 *   clubs/{c}/members/{uid}              handle, joinedAt
 *   clubs/{c}/messages/{id}              authorId, handle, body, hidden, createdAt
 *   clubs/{c}/posts/{id}                 authorId, handle, week, body, hidden, createdAt
 *   clubs/{c}/posts/{id}/hearts/{uid}    one doc per heart
 *   clubs/{c}/circles/{id}               hostId, handle, title, startsAt, jitsiRoom
 *   reports/{id}                         write-only from clients; reviewed in console
 *
 * Design notes, hard-won elsewhere in this codebase and applied here:
 * - The club doc id being the due month ("2027-03") makes "create the club if
 *   she's the first mother" a single idempotent set(merge) — no server code.
 * - Identity is Firebase ANONYMOUS auth + a chosen handle. No email, no phone,
 *   no real name. Her local-first data never touches any of this.
 * - Every function is fire-safe: failures return false/empty rather than
 *   throwing into the UI. Community being down must feel like weather, not
 *   like a crash.
 * - Blocking filters client-side (Firestore rules cannot filter list queries
 *   by a per-user subcollection); the rules' job is keeping the block list
 *   private and writes honest, the client's job is never rendering blocked
 *   authors.
 * - Red-flag medical content is flagged AT SEND TIME by the author's own
 *   client (field 'flagged') and re-checked on render, so dangerous claims
 *   always carry the "please talk to your midwife" overlay even if one side
 *   is on an old build.
 */
object CommunityRepository {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    // ─── Session / profile ─────────────────────────────────────────────

    suspend fun ensureSignedIn(): Boolean = runCatching {
        if (auth.currentUser == null) auth.signInAnonymously().await()
        auth.currentUser != null
    }.getOrDefault(false)

    /** Null when not joined yet (or offline). */
    suspend fun myProfile(): CommunityProfile? = runCatching {
        val id = uid ?: return null
        val snap = db.collection("profiles").document(id).get().await()
        if (!snap.exists()) null else CommunityProfile(
            uid = id,
            handle = snap.getString("handle").orEmpty(),
            dueMonth = snap.getString("dueMonth").orEmpty(),
        )
    }.getOrNull()

    /**
     * One-shot join: creates her profile, the club (idempotently), and her
     * membership. [dueDate] is the app's stored yyyy-MM-dd due date.
     */
    suspend fun joinBirthClub(handle: String, dueDate: String): CommunityProfile? = runCatching {
        if (!ensureSignedIn()) return null
        val id = uid ?: return null
        val clean = handle.trim().take(24)
        if (clean.length < 3) return null
        val dueMonth = dueDate.take(7).ifBlank { return null }   // yyyy-MM
        val clubName = "Due " + monthName(dueMonth)

        db.collection("profiles").document(id)
            .set(mapOf("handle" to clean, "dueMonth" to dueMonth,
                       "createdAt" to FieldValue.serverTimestamp())).await()
        db.collection("clubs").document(dueMonth)
            .set(mapOf("name" to clubName), com.google.firebase.firestore.SetOptions.merge()).await()
        db.collection("clubs").document(dueMonth).collection("members").document(id)
            .set(mapOf("handle" to clean, "joinedAt" to FieldValue.serverTimestamp())).await()
        AppStats.log(StatEvent.CommunityJoined)
        CommunityProfile(uid = id, handle = clean, dueMonth = dueMonth)
    }.getOrNull()

    /** GDPR-spirit exit: removes profile, membership, and the sign-in itself.
     *  Authored messages remain, shown as from "a departed member". */
    suspend fun leaveCommunity(profile: CommunityProfile): Boolean = runCatching {
        db.collection("clubs").document(profile.dueMonth)
            .collection("members").document(profile.uid).delete().await()
        db.collection("profiles").document(profile.uid).delete().await()
        auth.currentUser?.delete()?.await()
        true
    }.getOrDefault(false)

    // ─── Live streams ──────────────────────────────────────────────────

    fun messages(club: String): Flow<List<ClubMessage>> = watch(
        db.collection("clubs").document(club).collection("messages")
            .whereEqualTo("hidden", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(120)
    ) { d ->
        ClubMessage(
            id = d.id,
            authorId = d.getString("authorId").orEmpty(),
            handle = d.getString("handle").ifNullOrBlank("a departed member"),
            body = d.getString("body").orEmpty(),
            flagged = d.getBoolean("flagged") ?: false,
            createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        )
    }

    fun posts(club: String): Flow<List<ClubPost>> = watch(
        db.collection("clubs").document(club).collection("posts")
            .whereEqualTo("hidden", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(60)
    ) { d ->
        ClubPost(
            id = d.id,
            authorId = d.getString("authorId").orEmpty(),
            handle = d.getString("handle").ifNullOrBlank("a departed member"),
            week = (d.getLong("week") ?: 0L).toInt(),
            body = d.getString("body").orEmpty(),
            createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        )
    }

    fun circles(club: String): Flow<List<Circle>> = watch(
        db.collection("clubs").document(club).collection("circles")
            .orderBy("startsAt", Query.Direction.ASCENDING)
            .limit(20)
    ) { d ->
        Circle(
            id = d.id,
            hostId = d.getString("hostId").orEmpty(),
            handle = d.getString("handle").ifNullOrBlank("a departed member"),
            title = d.getString("title").orEmpty(),
            startsAt = d.getTimestamp("startsAt")?.toDate()?.time ?: 0L,
            jitsiRoom = d.getString("jitsiRoom").orEmpty(),
        )
    }

    fun myBlockedIds(): Flow<Set<String>> {
        val id = uid ?: return flowOf(emptySet())
        return watch(db.collection("profiles").document(id).collection("blocks")) { it.id }
            .map { it.toSet() }
    }

    // ─── Writes ────────────────────────────────────────────────────────

    suspend fun sendMessage(club: String, profile: CommunityProfile, body: String): Boolean =
        runCatching {
            val text = body.trim().take(2000)
            if (text.isEmpty()) return false
            db.collection("clubs").document(club).collection("messages").add(
                mapOf(
                    "authorId" to profile.uid,
                    "handle" to profile.handle,
                    "body" to text,
                    "flagged" to containsRedFlag(text),
                    "hidden" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            AppStats.log(StatEvent.CommunityMessageSent); true
        }.getOrDefault(false)

    suspend fun sharePost(club: String, profile: CommunityProfile, week: Int, body: String): Boolean =
        runCatching {
            val text = body.trim().take(1000)
            if (text.isEmpty()) return false
            db.collection("clubs").document(club).collection("posts").add(
                mapOf(
                    "authorId" to profile.uid,
                    "handle" to profile.handle,
                    "week" to week,
                    "body" to text,
                    "flagged" to containsRedFlag(text),
                    "hidden" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            AppStats.log(StatEvent.CommunityPostShared); true
        }.getOrDefault(false)

    suspend fun hostCircle(club: String, profile: CommunityProfile, title: String, startsAtMillis: Long): Boolean =
        runCatching {
            val clean = title.trim().take(80)
            if (clean.length < 3) return false
            val room = "prega-${club.replace("-", "")}-${UUID.randomUUID().toString().take(8)}"
            db.collection("clubs").document(club).collection("circles").add(
                mapOf(
                    "hostId" to profile.uid,
                    "handle" to profile.handle,
                    "title" to clean,
                    "startsAt" to com.google.firebase.Timestamp(java.util.Date(startsAtMillis)),
                    "jitsiRoom" to room,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            AppStats.log(StatEvent.CircleHosted); true
        }.getOrDefault(false)

    suspend fun block(otherUid: String): Boolean = runCatching {
        val id = uid ?: return false
        db.collection("profiles").document(id).collection("blocks").document(otherUid)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp())).await(); true
    }.getOrDefault(false)

    suspend fun report(club: String, targetType: String, targetId: String, reason: String): Boolean =
        runCatching {
            val id = uid ?: return false
            db.collection("reports").add(
                mapOf(
                    "reporterId" to id,
                    "clubId" to club,
                    "targetType" to targetType,   // "message" | "post"
                    "targetId" to targetId,
                    "reason" to reason.trim().take(300).ifBlank { "reported" },
                    "resolved" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await(); true
        }.getOrDefault(false)

    // ─── Plumbing ──────────────────────────────────────────────────────

    private fun <T> watch(
        query: Query,
        map: (com.google.firebase.firestore.DocumentSnapshot) -> T,
    ): Flow<List<T>> = callbackFlow {
        var reg: ListenerRegistration? = null
        reg = query.addSnapshotListener { snap, _ ->
            // Errors (offline, rules, missing index) surface as an empty list:
            // the UI shows its calm empty/awake state instead of crashing.
            trySend(snap?.documents?.mapNotNull { runCatching { map(it) }.getOrNull() } ?: emptyList())
        }
        awaitClose { reg?.remove() }
    }

    private fun String?.ifNullOrBlank(fallback: String) =
        if (this.isNullOrBlank()) fallback else this

    private fun monthName(dueMonth: String): String = runCatching {
        val (y, m) = dueMonth.split("-")
        val names = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
        "${names[m.toInt() - 1]} $y"
    }.getOrDefault(dueMonth)
}

data class CommunityProfile(val uid: String, val handle: String, val dueMonth: String)

data class ClubMessage(
    val id: String,
    val authorId: String,
    val handle: String,
    val body: String,
    val flagged: Boolean,
    val createdAt: Long,
)

data class ClubPost(
    val id: String,
    val authorId: String,
    val handle: String,
    val week: Int,
    val body: String,
    val createdAt: Long,
)

data class Circle(
    val id: String,
    val hostId: String,
    val handle: String,
    val title: String,
    val startsAt: Long,
    val jitsiRoom: String,
)
