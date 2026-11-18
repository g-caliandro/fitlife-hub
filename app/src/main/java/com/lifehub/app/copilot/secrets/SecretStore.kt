package com.lifehub.app.copilot.secrets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Names of the secrets the copilot stores. */
object SecretNames {
    const val LLM_API_KEY = "llm_api_key"
    const val STT_API_KEY = "stt_api_key"
}

/**
 * Small encrypted key/value store for API keys. Values never touch SharedPreferences, `Settings`, the JSON export or logs.
 * An unreadable value (e.g. after a restore onto another phone, where the Keystore key is gone) reads as absent.
 */
interface SecretStore {
    fun get(name: String): String?
    fun put(name: String, value: String)
    fun remove(name: String)
    fun has(name: String): Boolean = get(name) != null
}

/**
 * AES-256-GCM. Each secret is one file `<dir>/<name>.bin` = 12-byte IV + ciphertext; the secret's name is the GCM
 * associated data, so a file cannot be swapped under another name. [dir] must live in `noBackupFilesDir`.
 * [key] is a non-extractable Android Keystore key in production; tests pass a software key (and may pass a [newCipher]
 * that enforces the Keystore rules).
 */
class EncryptedFileSecretStore(
    private val dir: File,
    private val key: () -> SecretKey,
    private val newCipher: () -> Cipher = { Cipher.getInstance(TRANSFORMATION) },
) : SecretStore {
    private fun file(name: String): File {
        require(name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '_' }) { "bad secret name" }
        return File(dir, "$name.bin")
    }

    @Synchronized
    override fun get(name: String): String? {
        val f = file(name)
        if (!f.isFile) return null
        return try {
            val bytes = f.readBytes()
            if (bytes.size <= IV_BYTES) return null
            val cipher = newCipher()
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    @Synchronized
    override fun put(name: String, value: String) {
        val f = file(name)
        if (value.isEmpty()) { remove(name); return }
        dir.mkdirs()
        // The cipher picks the IV: an Android Keystore key (randomized encryption required) rejects a caller-provided one.
        val cipher = newCipher()
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV length ${iv.size}" }
        val out = iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(out)
        if (!tmp.renameTo(f)) { f.delete(); check(tmp.renameTo(f)) { "Could not save secret" } }
    }

    @Synchronized
    override fun remove(name: String) { file(name).delete() }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        const val DIR_NAME = "copilot_secrets"
        private const val KEY_ALIAS = "lifehub_copilot_secrets_v1"

        /** Production store: ciphertext under `noBackupFilesDir` (never backed up), key in the Android Keystore. */
        fun create(context: Context): EncryptedFileSecretStore =
            EncryptedFileSecretStore(File(context.applicationContext.noBackupFilesDir, DIR_NAME), key = { keystoreKey() })

        @Synchronized
        private fun keystoreKey(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return gen.generateKey()
        }
    }
}

/** Test double. */
class InMemorySecretStore : SecretStore {
    private val m = HashMap<String, String>()
    override fun get(name: String) = m[name]
    override fun put(name: String, value: String) { if (value.isEmpty()) m.remove(name) else m[name] = value }
    override fun remove(name: String) { m.remove(name) }
}

/** "••••abcd" for UI display (last 4 only for keys long enough that this reveals little); null when no key. */
fun maskKey(key: String?): String? {
    if (key.isNullOrEmpty()) return null
    return if (key.length >= 16) "••••" + key.takeLast(4) else "••••"
}
