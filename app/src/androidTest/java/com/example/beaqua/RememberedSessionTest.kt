package com.example.beaqua

import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.checkbox.MaterialCheckBox
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RememberedSessionTest {
    private lateinit var context: Context

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "session-test-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir(): File = directory
        }
    }

    @After fun cleanup() { context.noBackupFilesDir.deleteRecursively() }

    @Test fun checkedSessionSurvivesRecreationWithoutSavingPlaintext() {
        RememberedSession.save(context, "sample_customer", "Customer", "test-only-password", true)
        val reopened = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = context.noBackupFilesDir
        }
        val account = RememberedSession.read(reopened)!!
        assertEquals("sample_customer", account.username)
        assertEquals("Customer", account.role)
        assertEquals(RememberedSession.fingerprint("test-only-password"), account.credentialFingerprint)
        assertNotEquals(RememberedSession.fingerprint("new-password"), account.credentialFingerprint)
        val disk = File(context.noBackupFilesDir, "remembered_session").readText()
        assertFalse(disk.contains("sample_customer"))
        assertFalse(disk.contains("test-only-password"))
    }

    @Test fun uncheckedSignInAndLogoutClearExistingSession() {
        RememberedSession.save(context, "sample", "Customer", "test-password", true)
        RememberedSession.save(context, "sample", "Customer", "test-password", false)
        assertFalse(RememberedSession.exists(context))
        assertNull(RememberedSession.read(context))
        RememberedSession.save(context, "sample", "Customer", "test-password", true)
        RememberedSession.clear(context)
        assertNull(RememberedSession.read(context))
    }

    @Test fun damagedSessionFallsBackToManualSignIn() {
        File(context.noBackupFilesDir, "remembered_session").writeText("invalid")
        assertNull(RememberedSession.read(context))
        assertFalse(RememberedSession.exists(context))
    }

    @Test fun routesEachAccountToItsOwnScreen() {
        assertEquals(UserHomeActivity::class.java.name,
            RememberedSession.destination(context, "sample", "Customer").component!!.className)
        assertEquals(StationOwnerActivity::class.java.name,
            RememberedSession.destination(context, "sample", "Station Owner").component!!.className)
        assertEquals(AdminActivity::class.java.name,
            RememberedSession.destination(context, "admin", "Admin").component!!.className)
    }

    @Test fun checkboxStartsUncheckedAndCanBeSelected() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val themed = androidx.appcompat.view.ContextThemeWrapper(context, R.style.Theme_BeAqua)
            val layout = LayoutInflater.from(themed).inflate(R.layout.activity_login, null)
            val checkbox = layout.findViewById<MaterialCheckBox>(R.id.cbKeepSignedIn)
            assertFalse(checkbox.isChecked)
            checkbox.performClick()
            assertTrue(checkbox.isChecked)
        }
    }
}
