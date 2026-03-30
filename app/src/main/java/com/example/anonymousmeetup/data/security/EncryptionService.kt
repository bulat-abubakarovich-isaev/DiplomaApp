package com.example.anonymousmeetup.data.security

import android.content.Context
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EncryptionService"
        private const val KEY_SIZE = 256
        private const val PROVIDER = BouncyCastleProvider.PROVIDER_NAME
        private const val ALGORITHM = "EC"
        private const val TRANSFORMATION = "ECIES"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val GCM_TAG_LENGTH = 128
    }

    init {
        try {
            Security.removeProvider(PROVIDER)
        } catch (_: Exception) {
            // Ignore when provider was not installed yet.
        }
        Security.addProvider(BouncyCastleProvider())
        Security.setProperty("crypto.policy", "unlimited")
        Log.d(TAG, "EncryptionService initialized with BouncyCastle provider")
    }

    fun generateKeyPair(): KeyPair {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER)
            keyPairGenerator.initialize(KEY_SIZE)
            keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating key pair", e)
            throw EncryptionException("Key generation failed: ${e.message}")
        }
    }

    fun exportPublicKey(publicKey: PublicKey): String {
        return try {
            Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting public key", e)
            throw EncryptionException("Public key export failed: ${e.message}")
        }
    }

    fun exportPrivateKey(privateKey: PrivateKey): String {
        return try {
            Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting private key", e)
            throw EncryptionException("Private key export failed: ${e.message}")
        }
    }

    fun importPublicKey(encodedKey: String): PublicKey {
        return try {
            require(encodedKey.isNotBlank()) { "Empty key" }
            val keyBytes = Base64.decode(encodedKey, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER)
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing public key", e)
            throw EncryptionException("Public key import failed: ${e.message}")
        }
    }

    fun importPrivateKey(encodedKey: String): PrivateKey {
        return try {
            require(encodedKey.isNotBlank()) { "Empty key" }
            val keyBytes = Base64.decode(encodedKey, Base64.NO_WRAP)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER)
            keyFactory.generatePrivate(keySpec)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing private key", e)
            throw EncryptionException("Private key import failed: ${e.message}")
        }
    }

    fun encryptMessage(message: String, recipientPublicKey: String): String {
        return try {
            require(message.isNotBlank()) { "Empty message" }
            val publicKey = importPublicKey(recipientPublicKey)
            val cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting message", e)
            throw EncryptionException("Message encryption failed: ${e.message}")
        }
    }

    fun decryptMessage(encryptedMessage: String, privateKey: String): String {
        return try {
            require(encryptedMessage.isNotBlank()) { "Empty encrypted message" }
            val key = importPrivateKey(privateKey)
            val cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER)
            cipher.init(Cipher.DECRYPT_MODE, key)
            val encryptedBytes = Base64.decode(encryptedMessage, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting message", e)
            throw EncryptionException("Message decryption failed: ${e.message}")
        }
    }

    fun deriveSharedSecret(myPrivateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        return try {
            val keyAgreement = KeyAgreement.getInstance("ECDH", PROVIDER)
            keyAgreement.init(myPrivateKey)
            keyAgreement.doPhase(peerPublicKey, true)
            keyAgreement.generateSecret()
        } catch (e: Exception) {
            Log.e(TAG, "Error deriving shared secret", e)
            throw EncryptionException("Shared secret derivation failed: ${e.message}")
        }
    }

    fun deriveMessageKey(sharedSecret: ByteArray, nonce: ByteArray): ByteArray {
        return try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(sharedSecret, HMAC_ALGORITHM))
            mac.doFinal(nonce).copyOf(32)
        } catch (e: Exception) {
            Log.e(TAG, "Error deriving message key", e)
            throw EncryptionException("Message key derivation failed: ${e.message}")
        }
    }

    fun encryptAead(key: ByteArray, plaintext: ByteArray): EncryptedPayload {
        val nonce = randomNonce()
        return encryptAead(key, plaintext, nonce)
    }

    fun encryptAead(key: ByteArray, plaintext: ByteArray, nonce: ByteArray): EncryptedPayload {
        return try {
            val cipher = Cipher.getInstance(AES_GCM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOf(32), "AES"), spec)
            val encrypted = cipher.doFinal(plaintext)
            EncryptedPayload(
                ciphertext = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                nonce = Base64.encodeToString(nonce, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting AEAD payload", e)
            throw EncryptionException("AEAD encryption failed: ${e.message}")
        }
    }

    fun decryptAead(key: ByteArray, payload: EncryptedPayload): ByteArray {
        return try {
            val nonce = Base64.decode(payload.nonce, Base64.NO_WRAP)
            val ciphertext = Base64.decode(payload.ciphertext, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_GCM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOf(32), "AES"), spec)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting AEAD payload", e)
            throw EncryptionException("AEAD decryption failed: ${e.message}")
        }
    }

    fun randomNonce(size: Int = NONCE_BYTES): ByteArray {
        return ByteArray(size).also { SecureRandom().nextBytes(it) }
    }

    fun hashPublicKey(publicKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(publicKey.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }
}

data class EncryptedPayload(
    val ciphertext: String,
    val nonce: String
)

class EncryptionException(message: String) : Exception(message)

