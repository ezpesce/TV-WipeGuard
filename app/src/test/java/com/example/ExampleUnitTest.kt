package com.example

import com.example.data.SecurityPreferencesRepository
import com.example.domain.SystemSanitizerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `pin salted sha256 hashing is deterministic and salt-sensitive`() {
        val hashA1 = SecurityPreferencesRepository.hashPinWithSalt("4829", "aabbccdd")
        val hashA2 = SecurityPreferencesRepository.hashPinWithSalt("4829", "aabbccdd")
        val hashB = SecurityPreferencesRepository.hashPinWithSalt("4829", "11223344")
        val hashWrongPin = SecurityPreferencesRepository.hashPinWithSalt("9999", "aabbccdd")

        assertEquals(64, hashA1.length)
        assertEquals(hashA1, hashA2)
        assertNotEquals(hashA1, hashB)
        assertNotEquals(hashA1, hashWrongPin)
    }

    @Test
    fun `sha256 integrity signature and byte formatting work as expected`() {
        val sig = SystemSanitizerEngine.sha256Hex("WG-12345678|VERIFIED")
        assertEquals(64, sig.length)
        assertEquals("512 B", SystemSanitizerEngine.formatBytes(512L))
        assertEquals("1.50 MB", SystemSanitizerEngine.formatBytes(1_572_864L))
    }
}
