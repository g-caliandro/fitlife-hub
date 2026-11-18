package com.lifehub.app.copilot.secrets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.data.prefs.CopilotSettingsRepository
import com.lifehub.app.data.prefs.SettingsRepository
import com.lifehub.app.export.JsonCodec
import com.lifehub.app.mock.MockDataGenerator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.AlgorithmParameters
import java.security.InvalidAlgorithmParameterException
import java.security.Key
import java.security.SecureRandom
import java.security.Security
import java.security.spec.AlgorithmParameterSpec
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Uses a software AES key: Robolectric has no AndroidKeyStore. The Keystore key provider is checked on a device. */
@RunWith(RobolectricTestRunner::class)
class SecretStoreTest {
    private lateinit var ctx: Context
    private lateinit var dir: File
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val secret = "sk-or-v1-abcdefghijklmnopqrstuvwxyz0123456789"

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        dir = File(ctx.noBackupFilesDir, "copilot_secrets_test").also { it.deleteRecursively() }
    }
    private fun store(k: SecretKey = key) = EncryptedFileSecretStore(dir, { k })

    @Test fun roundTrip() {
        val s = store()
        s.put(SecretNames.LLM_API_KEY, secret)
        assertEquals(secret, s.get(SecretNames.LLM_API_KEY))
        assertEquals(secret, store().get(SecretNames.LLM_API_KEY)) // survives a new instance
        s.put(SecretNames.LLM_API_KEY, "other"); assertEquals("other", s.get(SecretNames.LLM_API_KEY))
        assertTrue(s.has(SecretNames.LLM_API_KEY))
    }

    /** Regression: on the phone "Save key" failed because the store passed its own IV, which a Keystore key rejects. */
    @Test fun encryptionLetsTheCipherChooseTheIvLikeAndroidKeystoreRequires() {
        val spis = mutableListOf<KeystoreLikeSpi>()
        val s = EncryptedFileSecretStore(dir, { key }) { KeystoreLikeSpi().also { spis += it }.cipher() }
        val groqKey = "gsk_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOP"
        s.put(SecretNames.STT_API_KEY, groqKey)
        assertEquals(groqKey, s.get(SecretNames.STT_API_KEY))
        assertTrue(spis.any { it.encrypted }) // the strict cipher really handled the encryption
    }

    @Test fun absentKeyIsNull() {
        assertNull(store().get(SecretNames.LLM_API_KEY)); assertFalse(store().has(SecretNames.STT_API_KEY))
    }

    @Test fun removeAndEmptyDelete() {
        val s = store(); s.put(SecretNames.STT_API_KEY, secret)
        s.remove(SecretNames.STT_API_KEY); assertNull(s.get(SecretNames.STT_API_KEY))
        s.put(SecretNames.STT_API_KEY, secret); s.put(SecretNames.STT_API_KEY, ""); assertNull(s.get(SecretNames.STT_API_KEY))
    }

    @Test fun ciphertextOnDiskIsNotPlaintextAndUnderTheGivenDir() {
        store().put(SecretNames.LLM_API_KEY, secret)
        val f = File(dir, "llm_api_key.bin")
        assertTrue(f.isFile)
        assertFalse(String(f.readBytes(), Charsets.ISO_8859_1).contains("abcdefghij"))
    }

    @Test fun unreadableCiphertextReadsAsAbsent() {
        val s = store(); s.put(SecretNames.LLM_API_KEY, secret)
        val f = File(dir, "llm_api_key.bin"); val b = f.readBytes(); b[b.size - 1] = (b[b.size - 1].toInt() xor 1).toByte(); f.writeBytes(b)
        assertNull(s.get(SecretNames.LLM_API_KEY))
        s.put(SecretNames.STT_API_KEY, secret)
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertNull(store(other).get(SecretNames.STT_API_KEY)) // e.g. Keystore key lost after a restore
    }

    @Test fun fileCannotBeSwappedUnderAnotherName() {
        val s = store(); s.put(SecretNames.LLM_API_KEY, secret)
        File(dir, "llm_api_key.bin").copyTo(File(dir, "stt_api_key.bin"))
        assertNull(s.get(SecretNames.STT_API_KEY))
    }

    @Test fun keyIsNotInSharedPreferencesNorInTheExport() {
        store().put(SecretNames.LLM_API_KEY, secret)
        // Exercise every prefs-backed settings object, then scan all prefs files on disk.
        val settings = SettingsRepository(ctx); settings.setProteinTarget(140)
        val cs = CopilotSettingsRepository(ctx); cs.setLlmModel("m"); cs.setSttLanguage("it")
        val prefsDir = File(ctx.applicationInfo.dataDir, "shared_prefs")
        val prefsText = prefsDir.listFiles().orEmpty().joinToString("\n") { it.readText() }
        assertTrue(prefsText.isNotEmpty()); assertFalse(prefsText.contains(secret))
        assertFalse(settings.current().toString().contains(secret)); assertFalse(cs.current().toString().contains(secret))
        val snap = MockDataGenerator.generate(LocalDate.of(2026, 11, 12), ZoneId.of("UTC")).copy(settings = settings.current())
        val json = JsonCodec.encode(snap, ZonedDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneId.of("UTC")), "0.1.0")
        assertFalse(json.contains(secret)); assertFalse(json.contains("api_key", ignoreCase = true)); assertFalse(json.contains("llmBaseUrl"))
    }

    @Test fun productionStoreLivesInNoBackupDir() {
        val s = EncryptedFileSecretStore.create(ctx)
        val dirField = EncryptedFileSecretStore::class.java.getDeclaredField("dir").apply { isAccessible = true }.get(s) as File
        assertEquals(File(ctx.noBackupFilesDir, "copilot_secrets"), dirField)
    }

    @Test fun maskShowsAtMostLastFour() {
        assertNull(maskKey(null)); assertNull(maskKey(""))
        assertEquals("••••", maskKey("short"))
        assertEquals("••••6789", maskKey(secret))
    }
}

/** AES/GCM that behaves like an AndroidKeyStore key with randomized encryption required: a caller IV on encrypt is refused. */
class KeystoreLikeSpi : CipherSpi() {
    private val d: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
    var encrypted = false

    fun cipher(): Cipher = object : Cipher(this, Security.getProvider("SunJCE"), "AES/GCM/NoPadding") {}

    private fun check(opmode: Int, callerIv: Boolean) {
        if (opmode == Cipher.ENCRYPT_MODE && callerIv) throw InvalidAlgorithmParameterException("Caller-provided IV not permitted")
        if (opmode == Cipher.ENCRYPT_MODE) encrypted = true
    }
    override fun engineSetMode(mode: String?) {}
    override fun engineSetPadding(padding: String?) {}
    override fun engineGetBlockSize() = d.blockSize
    override fun engineGetOutputSize(inputLen: Int) = d.getOutputSize(inputLen)
    override fun engineGetIV(): ByteArray? = d.iv
    override fun engineGetParameters(): AlgorithmParameters? = d.parameters
    override fun engineInit(opmode: Int, key: Key, random: SecureRandom?) { check(opmode, false); d.init(opmode, key) }
    override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameterSpec?, random: SecureRandom?) { check(opmode, params != null); d.init(opmode, key, params) }
    override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameters?, random: SecureRandom?) { check(opmode, params != null); d.init(opmode, key, params) }
    override fun engineUpdate(input: ByteArray, offset: Int, len: Int): ByteArray? = d.update(input, offset, len)
    override fun engineUpdate(input: ByteArray, offset: Int, len: Int, output: ByteArray, outOffset: Int) = d.update(input, offset, len, output, outOffset)
    override fun engineUpdateAAD(src: ByteArray, offset: Int, len: Int) = d.updateAAD(src, offset, len)
    override fun engineDoFinal(input: ByteArray, offset: Int, len: Int): ByteArray = d.doFinal(input, offset, len)
    override fun engineDoFinal(input: ByteArray, offset: Int, len: Int, output: ByteArray, outOffset: Int) = d.doFinal(input, offset, len, output, outOffset)
}
