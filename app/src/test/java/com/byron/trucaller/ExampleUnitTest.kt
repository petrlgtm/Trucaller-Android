package com.byron.trucaller

import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.data.model.TrustLevel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Smoke tests that verify the testing infrastructure is wired correctly:
 * MainDispatcherRule, factory functions, Truth assertions, and coroutine tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestInfrastructureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `MainDispatcherRule allows coroutine test to run`() = runTest {
        val user = createTestUser()
        assertThat(user.fullName).isEqualTo("Test User")
    }

    @Test
    fun `createTestCallerIdEntry returns correct defaults`() {
        val entry = createTestCallerIdEntry()
        assertThat(entry.id).isEqualTo("cid-001")
        assertThat(entry.phoneNumber).isEqualTo("+254700000001")
        assertThat(entry.spamScore).isEqualTo(0)
        assertThat(entry.category).isEqualTo(SpamCategory.SAFE)
    }

    @Test
    fun `createTestCallerIdEntry supports overrides`() {
        val entry = createTestCallerIdEntry(
            id = "cid-spam",
            spamScore = 90,
            category = SpamCategory.FRAUD
        )
        assertThat(entry.id).isEqualTo("cid-spam")
        assertThat(entry.spamScore).isEqualTo(90)
        assertThat(entry.category).isEqualTo(SpamCategory.FRAUD)
    }

    @Test
    fun `createTestUser returns correct defaults`() {
        val user = createTestUser()
        assertThat(user.id).isEqualTo("user-001")
        assertThat(user.isActive).isTrue()
        assertThat(user.trustLevel).isEqualTo(TrustLevel.TRUSTED)
        assertThat(user.trustScore).isEqualTo(50)
    }

    @Test
    fun `createTestUser supports overrides`() {
        val user = createTestUser(
            id = "user-inactive",
            isActive = false,
            trustLevel = TrustLevel.NEW,
            trustScore = 5
        )
        assertThat(user.isActive).isFalse()
        assertThat(user.trustLevel).isEqualTo(TrustLevel.NEW)
        assertThat(user.trustScore).isEqualTo(5)
    }

    @Test
    fun `createTestContact returns correct defaults`() {
        val contact = createTestContact()
        assertThat(contact.id).isEqualTo("contact-001")
        assertThat(contact.userId).isEqualTo("user-001")
        assertThat(contact.isBackedUp).isFalse()
        assertThat(contact.isFavourite).isFalse()
    }

    @Test
    fun `createTestContact supports overrides`() {
        val contact = createTestContact(
            id = "contact-fav",
            isFavourite = true,
            favouriteSegment = "Family"
        )
        assertThat(contact.isFavourite).isTrue()
        assertThat(contact.favouriteSegment).isEqualTo("Family")
    }
}
