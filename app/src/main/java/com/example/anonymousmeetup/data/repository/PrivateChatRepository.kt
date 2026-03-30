package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.local.ChatSessionStore
import com.example.anonymousmeetup.data.model.ChatSession
import com.example.anonymousmeetup.data.model.LocationPayload
import com.example.anonymousmeetup.data.model.PrivateMessage
import com.example.anonymousmeetup.data.model.PrivateMessageUiModel
import com.example.anonymousmeetup.data.model.RotatePayload
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.remote.FirebaseService
import com.example.anonymousmeetup.data.security.EncryptedPayload
import com.example.anonymousmeetup.data.security.EncryptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivateChatRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val userPreferences: UserPreferences,
    private val userRepository: UserRepository,
    private val encryptionService: EncryptionService,
    private val chatSessionStore: ChatSessionStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var handshakesStarted = false

    suspend fun allocateChatHash(): String = firebaseService.allocateChatHash()

    suspend fun initiateHandshake(targetHint: String): Result<String> {
        return runCatching {
            val target = parseTargetHint(targetHint)
            require(target.publicKey.isNotBlank()) { "Публичный ключ получателя пустой" }

            val keyInfo = userRepository.ensureDailyKeys()
            val myLogin = userPreferences.getNickname() ?: "Аноним"
            val chatHash = allocateChatHash()

            val myPrivate = encryptionService.importPrivateKey(keyInfo.privateKey)
            val targetPublic = encryptionService.importPublicKey(target.publicKey)
            val sharedSecret = encryptionService.deriveSharedSecret(myPrivate, targetPublic)

            val sessionId = UUID.randomUUID().toString()
            val secretRef = "secret_$sessionId"
            chatSessionStore.saveSharedSecret(secretRef, sharedSecret)
            chatSessionStore.upsertSession(
                ChatSession(
                    sessionId = sessionId,
                    peerDisplayName = target.displayName,
                    peerPublicKey = target.publicKey,
                    currentChatHash = chatHash,
                    previousChatHashes = emptyList(),
                    sharedSecretRef = secretRef,
                    createdAt = System.currentTimeMillis(),
                    rotateAt = null,
                    isActive = true
                )
            )

            val payload = JSONObject()
                .put("chatHash", chatHash)
                .put("initiatorPublicKey", keyInfo.publicKey)
                .put("initiatorDisplayName", myLogin)
                .put("createdAt", System.currentTimeMillis())
                .toString()

            val encryptedPayload = encryptionService.encryptMessage(payload, target.publicKey)
            val targetHash = encryptionService.hashPublicKey(target.publicKey)
            firebaseService.sendHandshake(targetHash, encryptedPayload)

            ensureHandshakeListenerStarted()
            sessionId
        }
    }

    suspend fun createOrAcceptSession(handshakePayload: String): Result<ChatSession> {
        return runCatching {
            val json = JSONObject(handshakePayload)
            val chatHash = json.getString("chatHash")
            val initiatorPublicKey = json.getString("initiatorPublicKey")
            val initiatorDisplayName = json.optString("initiatorDisplayName", "Собеседник")

            val existing = chatSessionStore.getSessionByChatHash(chatHash)
            if (existing != null) {
                return@runCatching existing
            }

            val keyInfo = userRepository.ensureDailyKeys()
            val myPrivate = encryptionService.importPrivateKey(keyInfo.privateKey)
            val peerPublic = encryptionService.importPublicKey(initiatorPublicKey)
            val sharedSecret = encryptionService.deriveSharedSecret(myPrivate, peerPublic)

            val sessionId = UUID.randomUUID().toString()
            val secretRef = "secret_$sessionId"
            chatSessionStore.saveSharedSecret(secretRef, sharedSecret)

            val session = ChatSession(
                sessionId = sessionId,
                peerDisplayName = initiatorDisplayName,
                peerPublicKey = initiatorPublicKey,
                currentChatHash = chatHash,
                previousChatHashes = emptyList(),
                sharedSecretRef = secretRef,
                createdAt = System.currentTimeMillis(),
                rotateAt = null,
                isActive = true
            )
            chatSessionStore.upsertSession(session)
            session
        }
    }

    fun observePrivateMessages(chatHash: String): Flow<List<PrivateMessageUiModel>> = callbackFlow {
        ensureHandshakeListenerStarted()
        val job = launch {
            firebaseService.listenPrivateMessages(chatHash).collectLatest { messages ->
                val session = chatSessionStore.getSessionByChatHash(chatHash)
                if (session == null) {
                    trySend(emptyList())
                    return@collectLatest
                }

                val sharedSecret = chatSessionStore.getSharedSecret(session.sharedSecretRef)
                if (sharedSecret == null) {
                    trySend(emptyList())
                    return@collectLatest
                }

                val myLogin = userPreferences.getNickname().orEmpty()
                val uiItems = mutableListOf<PrivateMessageUiModel>()

                messages.sortedBy { it.timestamp }.forEach { message ->
                    val decoded = decodeMessage(
                        message = message,
                        sharedSecret = sharedSecret,
                        myLogin = myLogin,
                        session = session
                    )
                    if (decoded != null) {
                        uiItems.add(decoded)
                    }
                }

                trySend(
                    uiItems
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                )
            }
        }

        awaitClose { job.cancel() }
    }

    suspend fun sendPrivateText(chatHash: String, text: String) {
        val payload = JSONObject()
            .put("type", TYPE_TEXT)
            .put("text", text)
            .put("sender", userPreferences.getNickname().orEmpty())
            .put("sentAt", System.currentTimeMillis())
            .toString()
        sendPrivatePayload(chatHash, TYPE_TEXT, payload)
    }

    suspend fun sendLocation(chatHash: String, locationPayload: LocationPayload) {
        val payload = JSONObject()
            .put("type", TYPE_LOCATION)
            .put("displayName", locationPayload.displayName)
            .put("latitude", locationPayload.latitude)
            .put("longitude", locationPayload.longitude)
            .put("sentAt", locationPayload.sentAt)
            .toString()
        sendPrivatePayload(chatHash, TYPE_LOCATION, payload)
    }

    suspend fun rotateChatHash(sessionId: String): Result<Unit> {
        return runCatching {
            val session = chatSessionStore.getSession(sessionId)
                ?: throw IllegalStateException("Сессия не найдена")
            val newHash = allocateChatHash()
            val rotatePayload = RotatePayload(
                chatHashNew = newHash,
                rotateId = UUID.randomUUID().toString(),
                validFrom = System.currentTimeMillis() + 5_000,
                gracePeriodSeconds = 86_400
            )

            val payload = JSONObject()
                .put("type", TYPE_ROTATE)
                .put("chatHashNew", rotatePayload.chatHashNew)
                .put("rotateId", rotatePayload.rotateId)
                .put("validFrom", rotatePayload.validFrom)
                .put("gracePeriodSeconds", rotatePayload.gracePeriodSeconds)
                .put("sender", userPreferences.getNickname().orEmpty())
                .toString()

            sendPrivatePayload(session.currentChatHash, TYPE_ROTATE, payload)

            chatSessionStore.upsertSession(
                session.copy(
                    currentChatHash = newHash,
                    previousChatHashes = (session.previousChatHashes + session.currentChatHash).distinct(),
                    rotateAt = rotatePayload.validFrom
                )
            )
        }
    }

    fun observeSessions(): Flow<List<ChatSession>> {
        ensureHandshakeListenerStarted()
        return chatSessionStore.observeSessions()
    }

    fun observeSession(sessionId: String): Flow<ChatSession?> {
        ensureHandshakeListenerStarted()
        return chatSessionStore.observeSessionById(sessionId)
    }

    private suspend fun sendPrivatePayload(chatHash: String, type: String, payload: String) {
        val session = chatSessionStore.getSessionByChatHash(chatHash)
            ?: throw IllegalStateException("Сессия для канала не найдена")
        val sharedSecret = chatSessionStore.getSharedSecret(session.sharedSecretRef)
            ?: throw IllegalStateException("Секрет сессии не найден")

        val nonce = encryptionService.randomNonce()
        val messageKey = encryptionService.deriveMessageKey(sharedSecret, nonce)
        val encryptedPayload = encryptionService.encryptAead(
            key = messageKey,
            plaintext = payload.toByteArray(Charsets.UTF_8),
            nonce = nonce
        )

        firebaseService.sendPrivateMessage(
            PrivateMessage(
                id = "",
                chatHash = chatHash,
                ciphertext = encryptedPayload.ciphertext,
                nonce = encryptedPayload.nonce,
                version = 1,
                ttlSeconds = 2_592_000L,
                timestamp = System.currentTimeMillis(),
                type = type
            )
        )
    }

    private suspend fun decodeMessage(
        message: PrivateMessage,
        sharedSecret: ByteArray,
        myLogin: String,
        session: ChatSession
    ): PrivateMessageUiModel? {
        return try {
            val nonceBytes = Base64.getDecoder().decode(message.nonce)
            val messageKey = encryptionService.deriveMessageKey(sharedSecret, nonceBytes)
            val decryptedBytes = encryptionService.decryptAead(
                key = messageKey,
                payload = EncryptedPayload(
                    ciphertext = message.ciphertext,
                    nonce = message.nonce
                )
            )
            val json = JSONObject(String(decryptedBytes, Charsets.UTF_8))
            val type = json.optString("type", message.type.ifBlank { TYPE_TEXT })
            val sender = json.optString("sender")
            val isMine = sender.isNotBlank() && sender == myLogin

            when (type) {
                TYPE_TEXT -> PrivateMessageUiModel(
                    id = message.id,
                    text = json.optString("text", ""),
                    timestamp = json.optLong("sentAt", message.timestamp),
                    type = TYPE_TEXT,
                    isMine = isMine
                )

                TYPE_LOCATION -> {
                    val location = LocationPayload(
                        displayName = json.optString("displayName", session.peerDisplayName),
                        latitude = json.optDouble("latitude"),
                        longitude = json.optDouble("longitude"),
                        sentAt = json.optLong("sentAt", message.timestamp)
                    )
                    PrivateMessageUiModel(
                        id = message.id,
                        text = "Геопозиция: %.5f, %.5f".format(location.latitude, location.longitude),
                        timestamp = location.sentAt,
                        type = TYPE_LOCATION,
                        isMine = isMine,
                        location = location
                    )
                }

                TYPE_ROTATE -> {
                    applyRotationFromMessage(json, session)
                    PrivateMessageUiModel(
                        id = message.id,
                        text = "Канал приватного чата обновлён",
                        timestamp = json.optLong("validFrom", message.timestamp),
                        type = TYPE_ROTATE,
                        isMine = isMine
                    )
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun applyRotationFromMessage(json: JSONObject, session: ChatSession) {
        val newHash = json.optString("chatHashNew")
        if (newHash.isBlank() || newHash == session.currentChatHash) return

        chatSessionStore.upsertSession(
            session.copy(
                currentChatHash = newHash,
                previousChatHashes = (session.previousChatHashes + session.currentChatHash).distinct(),
                rotateAt = json.optLong("validFrom", System.currentTimeMillis())
            )
        )
    }

    private fun ensureHandshakeListenerStarted() {
        if (handshakesStarted) return
        handshakesStarted = true
        scope.launch {
            runCatching {
                val publicKey = userRepository.getPublicKey()
                val targetHint = encryptionService.hashPublicKey(publicKey)

                firebaseService.listenHandshakes(targetHint).collectLatest { handshakes ->
                    val privateKey = userPreferences.getPrivateKey().orEmpty()
                    if (privateKey.isBlank()) return@collectLatest

                    handshakes.forEach { handshake ->
                        runCatching {
                            val decrypted = encryptionService.decryptMessage(handshake.payload, privateKey)
                            createOrAcceptSession(decrypted)
                            firebaseService.deleteHandshake(handshake.id)
                        }
                    }
                }
            }.onFailure {
                handshakesStarted = false
            }
        }
    }

    private fun parseTargetHint(targetHint: String): HandshakeTarget {
        val parts = targetHint.split("::", limit = 2)
        return if (parts.size == 2) {
            HandshakeTarget(displayName = parts[0].ifBlank { "Собеседник" }, publicKey = parts[1])
        } else {
            HandshakeTarget(displayName = "Собеседник", publicKey = targetHint)
        }
    }

    private data class HandshakeTarget(
        val displayName: String,
        val publicKey: String
    )

    companion object {
        private const val TYPE_TEXT = "TEXT"
        private const val TYPE_LOCATION = "LOCATION"
        private const val TYPE_ROTATE = "ROTATE"
    }
}
