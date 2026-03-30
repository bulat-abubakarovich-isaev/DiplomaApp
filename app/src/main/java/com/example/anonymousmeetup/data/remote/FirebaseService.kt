package com.example.anonymousmeetup.data.remote

import android.util.Log
import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.model.GroupMessage
import com.example.anonymousmeetup.data.model.HandshakeEnvelope
import com.example.anonymousmeetup.data.model.HashPoolStat
import com.example.anonymousmeetup.data.model.PrivateMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val groupsCollection = firestore.collection("groups")
    private val groupMessagesCollection = firestore.collection("group_messages")
    private val privateMessagesCollection = firestore.collection("private_messages")
    private val privateHandshakesCollection = firestore.collection("private_handshakes")
    private val hashPoolStatsCollection = firestore.collection("hash_pool_stats")

    suspend fun getAllGroups(): List<Group> {
        return try {
            groupsCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Group::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error loading groups", e)
            emptyList()
        }
    }

    suspend fun searchGroups(query: String): List<Group> {
        return try {
            val normalized = query.trim().lowercase()
            val all = groupsCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(250)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Group::class.java)?.copy(id = it.id) }

            if (normalized.isBlank()) {
                all.take(50)
            } else {
                all.filter { group ->
                    group.name.lowercase().contains(normalized) ||
                        group.category.lowercase().contains(normalized) ||
                        group.description.lowercase().contains(normalized)
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error searching groups", e)
            emptyList()
        }
    }

    suspend fun createGroup(
        name: String,
        description: String,
        category: String,
        isPrivate: Boolean
    ): String {
        return try {
            val docRef = groupsCollection.document()
            val group = Group(
                id = docRef.id,
                name = name,
                description = description,
                category = category,
                isPrivate = isPrivate,
                groupHash = "grp_${UUID.randomUUID().toString().replace("-", "").take(24)}",
                createdAt = System.currentTimeMillis()
            )
            docRef.set(group).await()
            docRef.id
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error creating group", e)
            throw e
        }
    }

    suspend fun updateGroup(group: Group) {
        groupsCollection.document(group.id).set(group).await()
    }

    suspend fun deleteGroup(groupId: String) {
        groupsCollection.document(groupId).delete().await()
    }

    suspend fun getGroup(groupId: String): Group? {
        return try {
            val byId = groupsCollection.document(groupId).get().await()
            if (byId.exists()) {
                return byId.toObject(Group::class.java)?.copy(id = byId.id)
            }

            val byHash = groupsCollection
                .whereEqualTo("groupHash", groupId)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
            byHash?.toObject(Group::class.java)?.copy(id = byHash.id)
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error getting group", e)
            null
        }
    }

    fun listenGroupMessages(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
        var registration: ListenerRegistration? = null
        val job = CoroutineScope(Dispatchers.IO).launch {
            val group = getGroup(groupId)
            val groupHash = group?.groupHash ?: groupId

            registration = groupMessagesCollection
                .whereEqualTo("groupHash", groupHash)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("FirebaseService", "Group messages listener error: ${error.message}")
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.toObjects(GroupMessage::class.java) ?: emptyList())
                }
        }

        awaitClose {
            job.cancel()
            registration?.remove()
        }
    }

    suspend fun sendGroupMessage(message: GroupMessage) {
        groupMessagesCollection.add(message).await()
    }

    fun listenPrivateMessages(chatHash: String): Flow<List<PrivateMessage>> = callbackFlow {
        val registration = privateMessagesCollection
            .whereEqualTo("chatHash", chatHash)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseService", "Private messages listener error: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snapshot?.toObjects(PrivateMessage::class.java) ?: emptyList()
                trySend(items)
            }

        awaitClose { registration.remove() }
    }

    suspend fun sendPrivateMessage(message: PrivateMessage) {
        privateMessagesCollection.add(message).await()
        incrementRangeMessages(extractRangeId(message.chatHash))
    }

    suspend fun sendHandshake(targetHint: String, encryptedPayload: String) {
        privateHandshakesCollection.add(
            mapOf(
                "targetHint" to targetHint,
                "payload" to encryptedPayload,
                "timestamp" to System.currentTimeMillis()
            )
        ).await()
    }

    fun listenHandshakes(targetHint: String): Flow<List<HandshakeEnvelope>> = callbackFlow {
        val registration = privateHandshakesCollection
            .whereEqualTo("targetHint", targetHint)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseService", "Handshake listener error: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snapshot?.toObjects(HandshakeEnvelope::class.java) ?: emptyList()
                trySend(items)
            }

        awaitClose { registration.remove() }
    }

    suspend fun deleteHandshake(handshakeId: String) {
        privateHandshakesCollection.document(handshakeId).delete().await()
    }

    suspend fun allocateChatHash(): String {
        val snapshot = hashPoolStatsCollection.get().await()
        val stats = snapshot.documents.mapNotNull { doc ->
            doc.toObject(HashPoolStat::class.java)?.copy(rangeId = doc.id)
        }

        val targetRange = if (stats.isNotEmpty()) {
            stats.minByOrNull { it.messagesCount + it.allocatedCount }?.rangeId ?: DEFAULT_RANGES.first()
        } else {
            DEFAULT_RANGES.random()
        }

        val chatHash = "h_${targetRange}_${UUID.randomUUID().toString().replace("-", "").take(24)}"
        val docRef = hashPoolStatsCollection.document(targetRange)

        firestore.runTransaction { transaction ->
            val current = transaction.get(docRef)
            val allocated = current.getLong("allocatedCount") ?: 0L
            val messages = current.getLong("messagesCount") ?: 0L
            transaction.set(
                docRef,
                mapOf(
                    "rangeId" to targetRange,
                    "allocatedCount" to allocated + 1,
                    "messagesCount" to messages,
                    "lastUpdated" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        }.await()

        return chatHash
    }

    private suspend fun incrementRangeMessages(rangeId: String) {
        if (rangeId.isBlank()) return

        val docRef = hashPoolStatsCollection.document(rangeId)
        firestore.runTransaction { transaction ->
            val current = transaction.get(docRef)
            val allocated = current.getLong("allocatedCount") ?: 0L
            val messages = current.getLong("messagesCount") ?: 0L
            transaction.set(
                docRef,
                mapOf(
                    "rangeId" to rangeId,
                    "allocatedCount" to allocated,
                    "messagesCount" to messages + 1,
                    "lastUpdated" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        }.await()
    }

    private fun extractRangeId(chatHash: String): String {
        val withoutPrefix = chatHash.removePrefix("h_")
        return withoutPrefix.substringBefore("_", "")
    }

    companion object {
        private val DEFAULT_RANGES = listOf("00AF", "11BE", "22CD", "33DC", "44EB", "55FA")
    }
}
