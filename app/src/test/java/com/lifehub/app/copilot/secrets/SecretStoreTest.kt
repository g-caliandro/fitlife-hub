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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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
