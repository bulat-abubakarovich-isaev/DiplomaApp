package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.model.GroupMessage
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.remote.FirebaseService
import com.example.anonymousmeetup.data.security.EncryptedPayload
import com.example.anonymousmeetup.data.security.EncryptionService
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupChatRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val userPreferences: UserPreferences,
    private val encryptionService: EncryptionService
) {
    fun observeGroupMessages(groupHash: String): Flow<List<GroupMessage>> {
        return firebaseService.listenGroupMessages(groupHash)
    }

    suspend fun sendGroupMessage(groupHash: String, text: String, senderAlias: String? = null) {
        val alias = senderAlias ?: userPreferences.getNickname().orEmpty().ifBlank { "Аноним" }
        val key = deriveGroupKey(groupHash)
        val encrypted = encryptionService.encryptAead(key, text.toByteArray(Charsets.UTF_8))

        firebaseService.sendGroupMessage(
            GroupMessage(
                id = "",
                groupHash = groupHash,
                senderAlias = alias,
                ciphertext = encrypted.ciphertext,
                nonce = encrypted.nonce,
                timestamp = System.currentTimeMillis(),
                ttlSeconds = 604800L,
                version = 1
            )
        )
    }

    fun decryptGroupMessage(groupHash: String, message: GroupMessage): String? {
        return try {
            val key = deriveGroupKey(groupHash)
            val payload = EncryptedPayload(
                ciphertext = message.ciphertext,
                nonce = message.nonce
            )
            val bytes = encryptionService.decryptAead(key, payload)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveGroupKey(groupHash: String): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(groupHash.toByteArray(Charsets.UTF_8))
        return hash.copyOf(32)
    }
}
