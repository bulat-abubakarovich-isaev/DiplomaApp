package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.local.ChatSessionStore
import com.example.anonymousmeetup.data.local.EncounterStore
import com.example.anonymousmeetup.data.model.EncounterLocal
import com.example.anonymousmeetup.data.model.LocalContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val chatSessionStore: ChatSessionStore,
    private val encounterStore: EncounterStore
) {
    fun getEncounters(): Flow<List<EncounterLocal>> = encounterStore.observeEncounters()

    fun getFriends(): Flow<List<LocalContact>> {
        return chatSessionStore.observeSessions().map { sessions ->
            sessions.filter { it.isActive }.map { session ->
                LocalContact(
                    sessionId = session.sessionId,
                    alias = session.peerDisplayName,
                    peerPublicKey = session.peerPublicKey,
                    currentChatHash = session.currentChatHash,
                    addedAt = session.createdAt
                )
            }
        }
    }
}
