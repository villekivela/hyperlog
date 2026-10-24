package com.hyperlog.app.coach

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Seals the key before it is written, and opens it when read. */
interface KeySeal {
    fun seal(plain: ByteArray): ByteArray
    fun open(sealed: ByteArray): ByteArray
}

/**
 * The Claude key (SPEC, Coaching → With a Claude key): one file in `no_backup`, which Android never
 * puts in Auto Backup or a device transfer, sealed by [seal]. It isn't in the database, so a
 * backup leaves it out and a restore keeps the phone's key. Nothing shows or logs it but its
 * [ending].
 */
class ClaudeKeys(private val file: File, private val seal: KeySeal) {
    private val _ending = MutableStateFlow(read()?.let(::ending))

    /** The key's last 4 characters, as Settings shows it; null with no key. */
    val ending: StateFlow<String?> = _ending.asStateFlow()

    /** The key, for the client; null with none, or when it can't be opened (a new phone's Keystore). */
    fun key(): String? = read()

    fun save(key: String) {
        val partial = File(file.parentFile, file.name + ".part")
        partial.writeBytes(seal.seal(key.toByteArray()))
        if (!partial.renameTo(file)) {
            partial.delete()
            error("Couldn't save the key")
        }
        _ending.value = ending(key)
    }

    fun remove() {
        file.delete()
        _ending.value = null
    }

    private fun read(): String? =
        if (!file.exists()) null else runCatching { String(seal.open(file.readBytes())) }.getOrNull()

    companion object {
        fun file(context: Context) = File(context.noBackupFilesDir, "claude_key")

        private fun ending(key: String) = key.takeLast(4)

        @Volatile
        private var instance: ClaudeKeys? = null

        /** The app's one store, sealed with Android Keystore. */
        fun get(context: Context): ClaudeKeys = instance ?: synchronized(this) {
            instance ?: ClaudeKeys(file(context.applicationContext), KeystoreSeal).also { instance = it }
        }
    }
}

/** AES-GCM with a key that Android Keystore makes and keeps, and never lets out. */
object KeystoreSeal : KeySeal {
    private const val ALIAS = "claude_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    /** The IV, then the ciphertext. */
    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secret()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }
}
