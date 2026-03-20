package com.byron.trucaller

import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.data.model.TrustLevel
import com.byron.trucaller.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that sets [Dispatchers.Main] to an [UnconfinedTestDispatcher]
 * for the duration of each test, then resets it afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

// ---------------------------------------------------------------------------
// Factory functions for test data
// ---------------------------------------------------------------------------

fun createTestCallerIdEntry(
    id: String = "cid-001",
    phoneNumber: String = "+254700000001",
    name: String = "Test Caller",
    spamScore: Int = 0,
    reportCount: Int = 0,
    category: SpamCategory = SpamCategory.SAFE,
    lastUpdated: String = "2026-01-01T00:00:00Z"
): CallerIdEntry = CallerIdEntry(
    id = id,
    phoneNumber = phoneNumber,
    name = name,
    spamScore = spamScore,
    reportCount = reportCount,
    category = category,
    lastUpdated = lastUpdated
)

fun createTestUser(
    id: String = "user-001",
    fullName: String = "Test User",
    phoneNumber: String = "+254700000001",
    email: String? = "test@example.com",
    passwordHash: String = "hashed_password",
    createdAt: String = "2026-01-01T00:00:00Z",
    lastLogin: String? = null,
    isActive: Boolean = true,
    avatarUrl: String? = null,
    securityPin: String? = null,
    trustScore: Int = 50,
    trustLevel: TrustLevel = TrustLevel.TRUSTED
): User = User(
    id = id,
    fullName = fullName,
    phoneNumber = phoneNumber,
    email = email,
    passwordHash = passwordHash,
    createdAt = createdAt,
    lastLogin = lastLogin,
    isActive = isActive,
    avatarUrl = avatarUrl,
    securityPin = securityPin,
    trustScore = trustScore,
    trustLevel = trustLevel
)

fun createTestContact(
    id: String = "contact-001",
    userId: String = "user-001",
    name: String = "Test Contact",
    phoneNumber: String = "+254700000002",
    email: String? = "contact@example.com",
    syncedAt: String = "2026-01-01T00:00:00Z",
    isBackedUp: Boolean = false,
    isFavourite: Boolean = false,
    favouriteSegment: String? = null,
    note: String? = null,
    photoUri: String? = null
): Contact = Contact(
    id = id,
    userId = userId,
    name = name,
    phoneNumber = phoneNumber,
    email = email,
    syncedAt = syncedAt,
    isBackedUp = isBackedUp,
    isFavourite = isFavourite,
    favouriteSegment = favouriteSegment,
    note = note,
    photoUri = photoUri
)
