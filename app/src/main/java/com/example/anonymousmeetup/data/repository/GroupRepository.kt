package com.example.anonymousmeetup.data.repository

import android.util.Log
import com.example.anonymousmeetup.data.local.JoinedGroupsStore
import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.remote.FirebaseService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val joinedGroupsStore: JoinedGroupsStore
) {
    fun getGroups(): Flow<List<Group>> {
        // Legacy name kept: now returns local joined groups only.
        return joinedGroupsStore.observeJoinedGroups()
    }

    fun getLocalJoinedGroups(): Flow<List<Group>> = joinedGroupsStore.observeJoinedGroups()

    fun searchGroups(query: String): Flow<List<Group>> = flow {
        emit(firebaseService.searchGroups(query))
    }

    suspend fun createGroup(name: String, description: String, category: String, isPrivate: Boolean): String {
        Log.d("GroupRepository", "Создание новой группы: $name")
        val groupId = firebaseService.createGroup(name, description, category, isPrivate)
        val created = firebaseService.getGroup(groupId)
        if (created != null) {
            joinedGroupsStore.join(created)
        }
        return groupId
    }

    suspend fun joinGroup(groupId: String) {
        val group = firebaseService.getGroup(groupId) ?: throw IllegalStateException("Группа не найдена")
        joinedGroupsStore.join(group)
    }

    suspend fun joinGroupLocally(group: Group) {
        joinedGroupsStore.join(group)
    }

    suspend fun leaveGroup(groupId: String) {
        joinedGroupsStore.leave(groupId)
    }

    suspend fun leaveGroupLocally(groupId: String) {
        joinedGroupsStore.leave(groupId)
    }

    suspend fun getGroup(groupId: String): Group {
        return firebaseService.getGroup(groupId) ?: throw IllegalStateException("Группа не найдена")
    }

    suspend fun isGroupJoined(groupId: String): Boolean {
        return joinedGroupsStore.isJoined(groupId)
    }

    suspend fun updateGroup(group: Group) {
        firebaseService.updateGroup(group)
    }

    suspend fun deleteGroup(groupId: String) {
        firebaseService.deleteGroup(groupId)
        joinedGroupsStore.leave(groupId)
    }
}
