package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.CallerIdDao
import com.byron.trucaller.data.dao.ContactAliasDao
import com.byron.trucaller.data.dao.ContactDao
import com.byron.trucaller.data.dao.UserDao
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.model.ContactAlias
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.data.model.TrustLevel
import com.byron.trucaller.data.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CallerIdRepository].
 *
 * Tests the multi-tier lookupNumber pipeline, phone normalization via
 * the repository's delegation to PhoneUtils, and CRUD pass-through to the DAO.
 */
class CallerIdRepositoryTest {

    private lateinit var callerIdDao: CallerIdDao
    private lateinit var contactDao: ContactDao
    private lateinit var userDao: UserDao
    private lateinit var contactAliasDao: ContactAliasDao
    private lateinit var repository: CallerIdRepository

    private val callerIdEntry = CallerIdEntry(
        id = "cid-001",
        phoneNumber = "+256712345678",
        name = "Known Caller",
        spamScore = 10,
        reportCount = 1,
        category = SpamCategory.SAFE,
        lastUpdated = "2026-01-01T00:00:00Z"
    )

    private val registeredUser = User(
        id = "user-100",
        fullName = "Registered User",
        phoneNumber = "+256700000001",
        passwordHash = "hash",
        createdAt = "2026-01-01T00:00:00Z",
        lastLogin = "2026-03-01T00:00:00Z",
        isActive = true,
        trustScore = 50,
        trustLevel = TrustLevel.TRUSTED
    )

    private val contact = Contact(
        id = "cnt-001",
        userId = "user-001",
        name = "Contact Person",
        phoneNumber = "+256700000002",
        syncedAt = "2026-01-15T00:00:00Z",
        isBackedUp = true
    )

    private val alias = ContactAlias(
        id = "alias-001",
        phoneNumber = "+256700000003",
        name = "Alias Name",
        source = "user",
        addedAt = "2026-02-01T00:00:00Z"
    )

    @Before
    fun setup() {
        callerIdDao = mockk(relaxed = true)
        contactDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        contactAliasDao = mockk(relaxed = true)
        repository = CallerIdRepository(callerIdDao, contactDao, userDao, contactAliasDao)
    }

    // ── lookupNumber: Tier 1 — Caller ID DB ─────────────────────────────

    @Test
    fun `lookupNumber returns caller_id_db source when entry exists in CallerIdDao`() = runTest {
        coEvery { callerIdDao.getByPhone("+256712345678") } returns callerIdEntry

        val result = repository.lookupNumber("+256712345678")

        assertEquals("caller_id_db", result.source)
        assertNotNull(result.callerIdEntry)
        assertEquals("Known Caller", result.callerIdEntry!!.name)
        assertNull(result.contactMatch)
    }

    @Test
    fun `lookupNumber Tier 1 does not query lower tiers when caller ID entry found`() = runTest {
        coEvery { callerIdDao.getByPhone("+256712345678") } returns callerIdEntry

        repository.lookupNumber("+256712345678")

        coVerify(exactly = 0) { userDao.getByPhone(any()) }
        coVerify(exactly = 0) { contactDao.getByPhone(any()) }
        coVerify(exactly = 0) { contactAliasDao.getAliasesByPhoneOnce(any()) }
    }

    // ── lookupNumber: Tier 2 — Registered Users ─────────────────────────

    @Test
    fun `lookupNumber returns registered_user source when user exists in UserDao`() = runTest {
        coEvery { callerIdDao.getByPhone("+256700000001") } returns null
        coEvery { userDao.getByPhone("+256700000001") } returns registeredUser

        val result = repository.lookupNumber("+256700000001")

        assertEquals("registered_user", result.source)
        assertNotNull(result.callerIdEntry)
        assertEquals("Registered User", result.callerIdEntry!!.name)
        assertEquals(0, result.callerIdEntry!!.spamScore)
        assertEquals(SpamCategory.SAFE, result.callerIdEntry!!.category)
    }

    @Test
    fun `lookupNumber Tier 2 synthesized entry uses user id and phone`() = runTest {
        coEvery { callerIdDao.getByPhone("+256700000001") } returns null
        coEvery { userDao.getByPhone("+256700000001") } returns registeredUser

        val result = repository.lookupNumber("+256700000001")

        assertEquals("user-${registeredUser.id}", result.callerIdEntry!!.id)
        assertEquals(registeredUser.phoneNumber, result.callerIdEntry!!.phoneNumber)
    }

    // ── lookupNumber: Tier 3 — Central Contacts ─────────────────────────

    @Test
    fun `lookupNumber returns central_drive source when contact exists in ContactDao`() = runTest {
        coEvery { callerIdDao.getByPhone("+256700000002") } returns null
        coEvery { userDao.getByPhone("+256700000002") } returns null
        coEvery { contactDao.getByPhone("+256700000002") } returns contact

        val result = repository.lookupNumber("+256700000002")

        assertEquals("central_drive", result.source)
        assertNotNull(result.callerIdEntry)
        assertEquals("Contact Person", result.callerIdEntry!!.name)
        assertNotNull(result.contactMatch)
        assertEquals("cnt-001", result.contactMatch!!.id)
    }

    // ── lookupNumber: Tier 4 — Contact Aliases ──────────────────────────

    @Test
    fun `lookupNumber returns alias source when aliases exist`() = runTest {
        coEvery { callerIdDao.getByPhone("+256700000003") } returns null
        coEvery { userDao.getByPhone("+256700000003") } returns null
        coEvery { contactDao.getByPhone("+256700000003") } returns null
        coEvery { contactAliasDao.getAliasesByPhoneOnce("+256700000003") } returns listOf(alias)

        val result = repository.lookupNumber("+256700000003")

        assertEquals("alias", result.source)
        assertNotNull(result.callerIdEntry)
        assertEquals("Alias Name", result.callerIdEntry!!.name)
    }

    @Test
    fun `lookupNumber alias tier selects best alias by source priority`() = runTest {
        val aliasUser = alias.copy(id = "alias-u", source = "user", name = "User Alias")
        val aliasWa = alias.copy(id = "alias-w", source = "whatsapp", name = "WhatsApp Alias")
        val aliasCb = alias.copy(id = "alias-c", source = "contact_book", name = "Contact Book Alias")

        coEvery { callerIdDao.getByPhone("+256700000003") } returns null
        coEvery { userDao.getByPhone("+256700000003") } returns null
        coEvery { contactDao.getByPhone("+256700000003") } returns null
        coEvery { contactAliasDao.getAliasesByPhoneOnce("+256700000003") } returns
                listOf(aliasWa, aliasCb, aliasUser)

        val result = repository.lookupNumber("+256700000003")

        // "user" source has highest priority
        assertEquals("User Alias", result.callerIdEntry!!.name)
    }

    // ── lookupNumber: Tier 5 — Not Found ────────────────────────────────

    @Test
    fun `lookupNumber returns not_found when no tier matches`() = runTest {
        coEvery { callerIdDao.getByPhone("+256799999999") } returns null
        coEvery { userDao.getByPhone("+256799999999") } returns null
        coEvery { contactDao.getByPhone("+256799999999") } returns null
        coEvery { contactAliasDao.getAliasesByPhoneOnce("+256799999999") } returns emptyList()

        val result = repository.lookupNumber("+256799999999")

        assertEquals("not_found", result.source)
        assertNull(result.callerIdEntry)
        assertNull(result.contactMatch)
    }

    // ── Phone normalization ─────────────────────────────────────────────

    @Test
    fun `lookupNumber normalizes local phone number before querying`() = runTest {
        // "0712345678" should be normalized to "+256712345678" by PhoneUtils
        coEvery { callerIdDao.getByPhone("+256712345678") } returns callerIdEntry

        val result = repository.lookupNumber("0712345678")

        assertEquals("caller_id_db", result.source)
        coVerify { callerIdDao.getByPhone("+256712345678") }
    }

    @Test
    fun `lookupNumber normalizes bare subscriber number`() = runTest {
        // "712345678" (9-digit starting with 7) should become "+256712345678"
        coEvery { callerIdDao.getByPhone("+256712345678") } returns callerIdEntry

        val result = repository.lookupNumber("712345678")

        assertEquals("caller_id_db", result.source)
        coVerify { callerIdDao.getByPhone("+256712345678") }
    }

    @Test
    fun `lookupNumber normalizes number with country code without plus`() = runTest {
        // "256712345678" should become "+256712345678"
        coEvery { callerIdDao.getByPhone("+256712345678") } returns callerIdEntry

        val result = repository.lookupNumber("256712345678")

        assertEquals("caller_id_db", result.source)
        coVerify { callerIdDao.getByPhone("+256712345678") }
    }

    @Test
    fun `lookupNumber strips formatting characters`() = runTest {
        // "+256 712-345 678" should become "+256712345678"
        coEvery { callerIdDao.getByPhone("+256712345678") } returns callerIdEntry

        val result = repository.lookupNumber("+256 712-345 678")

        assertEquals("caller_id_db", result.source)
    }

    // ── CRUD pass-through ───────────────────────────────────────────────

    @Test
    fun `getAllEntries delegates to callerIdDao getAll`() = runTest {
        every { callerIdDao.getAll() } returns flowOf(listOf(callerIdEntry))

        val result = repository.getAllEntries().first()
        assertEquals(1, result.size)
    }

    @Test
    fun `searchEntries delegates to callerIdDao search`() = runTest {
        every { callerIdDao.search("Known") } returns flowOf(listOf(callerIdEntry))

        val result = repository.searchEntries("Known").first()
        assertEquals(1, result.size)
    }

    @Test
    fun `getEntryCount delegates to callerIdDao countFlow`() = runTest {
        every { callerIdDao.countFlow() } returns flowOf(5)

        val count = repository.getEntryCount().first()
        assertEquals(5, count)
    }

    @Test
    fun `insertEntry delegates to callerIdDao insert`() = runTest {
        coEvery { callerIdDao.insert(callerIdEntry) } returns Unit

        repository.insertEntry(callerIdEntry)

        coVerify { callerIdDao.insert(callerIdEntry) }
    }

    @Test
    fun `updateEntry delegates to callerIdDao update`() = runTest {
        coEvery { callerIdDao.update(callerIdEntry) } returns Unit

        repository.updateEntry(callerIdEntry)

        coVerify { callerIdDao.update(callerIdEntry) }
    }

    @Test
    fun `deleteEntry delegates to callerIdDao delete`() = runTest {
        coEvery { callerIdDao.delete(callerIdEntry) } returns Unit

        repository.deleteEntry(callerIdEntry)

        coVerify { callerIdDao.delete(callerIdEntry) }
    }

    @Test
    fun `deleteEntriesByIds delegates to callerIdDao deleteByIds`() = runTest {
        val ids = listOf("cid-001", "cid-002")
        coEvery { callerIdDao.deleteByIds(ids) } returns Unit

        repository.deleteEntriesByIds(ids)

        coVerify { callerIdDao.deleteByIds(ids) }
    }

    @Test
    fun `updateCategoryByIds delegates to callerIdDao`() = runTest {
        val ids = listOf("cid-001")
        coEvery { callerIdDao.updateCategoryByIds(ids, SpamCategory.FRAUD, "2026-03-20") } returns Unit

        repository.updateCategoryByIds(ids, SpamCategory.FRAUD, "2026-03-20")

        coVerify { callerIdDao.updateCategoryByIds(ids, SpamCategory.FRAUD, "2026-03-20") }
    }

    @Test
    fun `getByPhone delegates to callerIdDao`() = runTest {
        coEvery { callerIdDao.getByPhone("+256712345678") } returns callerIdEntry

        val result = repository.getByPhone("+256712345678")
        assertEquals("cid-001", result!!.id)
    }

    @Test
    fun `getById delegates to callerIdDao`() = runTest {
        coEvery { callerIdDao.getById("cid-001") } returns callerIdEntry

        val result = repository.getById("cid-001")
        assertEquals("+256712345678", result!!.phoneNumber)
    }
}
