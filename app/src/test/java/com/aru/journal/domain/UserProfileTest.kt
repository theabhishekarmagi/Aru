package com.aru.journal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UserProfileTest {
    @Test fun `all optional profile fields may be skipped`() {
        val profile = UserProfile(accountId = "account-a", profileSetupCompleted = true)

        assertNull(profile.displayName)
        assertNull(profile.dateOfBirth)
        assertNull(profile.heightCm)
        assertNull(profile.weightKg)
        assertEquals(true, profile.profileSetupCompleted)
    }

    @Test fun `profile rejects malformed and unsafe values`() {
        assertThrows(IllegalArgumentException::class.java) { UserProfile("") }
        assertThrows(IllegalArgumentException::class.java) { UserProfile("account-a", displayName = " ") }
        assertThrows(IllegalArgumentException::class.java) { UserProfile("account-a", dateOfBirth = "not-a-date") }
        assertThrows(IllegalArgumentException::class.java) { UserProfile("account-a", heightCm = 49.9) }
        assertThrows(IllegalArgumentException::class.java) { UserProfile("account-a", weightKg = 500.1) }
    }
}
