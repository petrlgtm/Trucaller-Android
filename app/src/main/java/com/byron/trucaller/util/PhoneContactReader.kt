package com.byron.trucaller.util

import android.content.Context
import android.provider.ContactsContract

data class PhoneContact(
    val name: String,
    val phoneNumber: String,
    val email: String? = null
)

/**
 * Reads all contacts with phone numbers from the device's contact book.
 * Also fetches primary email addresses when available.
 * Requires READ_CONTACTS permission.
 */
fun readPhoneContacts(context: Context): List<PhoneContact> {
    val contacts = mutableMapOf<String, PhoneContact>() // phone -> contact (dedup by phone)

    // Step 1: Read all phone numbers + names
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    ) ?: return emptyList()

    // Collect contact IDs for email lookup
    val contactIdToPhone = mutableMapOf<String, String>()

    cursor.use {
        val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (it.moveToNext()) {
            val contactId = it.getString(idIndex) ?: continue
            val name = it.getString(nameIndex)?.trim() ?: continue
            val rawNumber = it.getString(numberIndex)?.trim() ?: continue

            // Normalize: remove spaces, dashes, parentheses
            val normalized = rawNumber.replace(Regex("[\\s\\-().]"), "")
            if (normalized.length < 7 || name.isBlank()) continue

            // Convert local Ugandan numbers to +256 format
            val fullNumber = when {
                normalized.startsWith("+") -> normalized
                normalized.startsWith("0") && normalized.length == 10 ->
                    "+256${normalized.substring(1)}"
                normalized.length == 9 && (normalized.startsWith("7") || normalized.startsWith("3")) ->
                    "+256$normalized"
                else -> normalized
            }

            // Deduplicate by normalized phone number
            if (!contacts.containsKey(fullNumber)) {
                contacts[fullNumber] = PhoneContact(name = name, phoneNumber = fullNumber)
                contactIdToPhone[contactId] = fullNumber
            }
        }
    }

    // Step 2: Fetch emails for the contacts we found
    if (contactIdToPhone.isNotEmpty()) {
        try {
            val emailCursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS
                ),
                null,
                null,
                null
            )

            emailCursor?.use { ec ->
                val emailIdIndex = ec.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val emailAddrIndex = ec.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)

                while (ec.moveToNext()) {
                    val contactId = ec.getString(emailIdIndex) ?: continue
                    val email = ec.getString(emailAddrIndex)?.trim() ?: continue

                    val phoneKey = contactIdToPhone[contactId] ?: continue
                    val existing = contacts[phoneKey] ?: continue

                    // Only set email if not already set (keep first email found)
                    if (existing.email == null && email.contains("@")) {
                        contacts[phoneKey] = existing.copy(email = email)
                    }
                }
            }
        } catch (_: Exception) {
            // Email fetch is best-effort
        }
    }

    return contacts.values.toList()
}
