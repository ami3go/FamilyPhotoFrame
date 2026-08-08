package com.example.familyphotoframe.data.secret

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familyphotoframe.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Keystore-backed [KeystoreSecretStore] (spec §14): a stored secret
 * round-trips, an unknown ref reveals null, and forgetting removes it. Runs
 * instrumented because it exercises the real AndroidKeyStore.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {

    private lateinit var db: AppDatabase
    private lateinit var store: KeystoreSecretStore

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        store = KeystoreSecretStore(ctx, db.secretDao(), Dispatchers.IO)
    }

    @After fun tearDown() = db.close()

    @Test
    fun storeThenReveal_roundTrips() = runBlocking {
        store.store("cred_smb_nas1", "smb_password", "hunter2!\u00e9\u00fc")
        assertEquals("hunter2!\u00e9\u00fc", store.reveal("cred_smb_nas1"))
    }

    @Test
    fun unknownRef_revealsNull() = runBlocking {
        assertNull(store.reveal("nope"))
    }

    @Test
    fun forget_removesSecret() = runBlocking {
        store.store("cred_x", "smb_password", "secret")
        store.forget("cred_x")
        assertNull(store.reveal("cred_x"))
    }

    /**
     * On API 21-22 the AES key must be wrapped and persisted; on API 23+ it lives in
     * AndroidKeyStore and no wrapped key may be written. Either way the ciphertext
     * must never equal the plaintext.
     */
    @Test
    fun storageShapeMatchesApiLevel() = runBlocking {
        store.store("cred_shape", "smb_password", "s3cret")
        val row = db.secretDao().get("cred_shape")!!
        assertEquals(
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M,
            row.wrappedKey != null,
        )
        assertNotEquals("s3cret", String(row.encryptedSecretBlob, Charsets.UTF_8))
    }

    @Test
    fun overwrite_keepsLatestValue() = runBlocking {
        store.store("cred_y", "smb_password", "old")
        store.store("cred_y", "smb_password", "new")
        assertEquals("new", store.reveal("cred_y"))
    }
}
