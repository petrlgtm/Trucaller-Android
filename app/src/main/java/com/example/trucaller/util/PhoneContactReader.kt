package com.example.trucaller.util

import android.content.Context
import android.provider.ContactsContract

data class PhoneContact(
    val name: String,
    val phoneNumber: String
)

/**
 * Reads all contacts with phone numbers from the device's contact book.
 * Requires READ_CONTACTS permission.
 */
fun readPhoneContacts(context: Context): List<PhoneContact> {
    val contacts = mutableMapOf<String, PhoneContact>() // phone -> contact (dedup by phone)

    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    ) ?: return emptyList()

    cursor.use {
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (it.moveToNext()) {
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

            // Deduplicate by normalized phone number, keep first occurrence (has display name)
            if (!contacts.containsKey(fullNumber)) {
                contacts[fullNumber] = PhoneContact(name = name, phoneNumber = fullNumber)
            }
        }
    }

    return contacts.values.toList()
}
