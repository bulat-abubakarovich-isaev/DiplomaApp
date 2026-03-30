package com.example.anonymousmeetup.di

import android.util.Log
import com.example.anonymousmeetup.data.remote.FirebaseService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            firestore.firestoreSettings = settings
            Log.d("FirebaseModule", "Firestore СѓСЃРїРµС€РЅРѕ РёРЅРёС†РёР°Р»РёР·РёСЂРѕРІР°РЅ")
            firestore
        } catch (e: Exception) {
            Log.e("FirebaseModule", "РћС€РёР±РєР° РёРЅРёС†РёР°Р»РёР·Р°С†РёРё Firestore", e)
            throw e
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseService(firestore: FirebaseFirestore): FirebaseService {
        return FirebaseService(firestore)
    }
}


