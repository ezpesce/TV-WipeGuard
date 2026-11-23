package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PinVerifyResult
import com.example.data.SecurityPreferencesRepository
import com.example.domain.FactoryResetOutcome
import com.example.domain.SanitizationPassMode
import com.example.domain.SystemSanitizerEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TV WipeGuard", appName)
    }

    @Test
    fun `pin hash and verification lock out after max attempts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SecurityPreferencesRepository(context)
        repo.saveNewPin("4829")

        assertEquals(PinVerifyResult.Success, repo.verifyPin("4829"))

        for (attempt in 1 until SecurityPreferencesRepository.MAX_FAILED_ATTEMPTS) {
            val res = repo.verifyPin("0000", nowEpochMs = 1_000_000L)
            assertTrue(res is PinVerifyResult.Invalid)
        }
        val locked = repo.verifyPin("0000", nowEpochMs = 1_000_000L)
        assertTrue(locked is PinVerifyResult.LockedOut)
    }

    @Test
    fun `sanitization engine produces verified sha256 report`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SystemSanitizerEngine(context)

        val report = engine.executeDeepSanitization(
            passMode = SanitizationPassMode.FAST_ZERO_PASS,
            authorizedFolders = emptyList(),
            safeVerificationMode = true,
            onProgress = {},
            onLog = {},
        )

        assertTrue(report.bytesOverwritten > 0L)
        assertEquals(64, report.integritySha256.length)
        assertEquals(FactoryResetOutcome.SKIPPED_SAFE_MODE, report.factoryResetOutcome)
        assertNotEquals("", report.reportId)
    }
}
