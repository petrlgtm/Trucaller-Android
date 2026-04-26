package com.byron.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.repository.ContactRepository
import com.byron.trucaller.util.readPhoneContacts
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DialPadViewModel(
    application: Application,
    private val contactRepository: ContactRepository
) : AndroidViewModel(application) {

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _matchingContacts = MutableStateFlow<List<Contact>>(emptyList())
    val matchingContacts: StateFlow<List<Contact>> = _matchingContacts.asStateFlow()

    private var dbContacts: List<Contact> = emptyList()
    private var filterJob: Job? = null

    init {
        viewModelScope.launch {
            contactRepository.getAllContacts().collectLatest {
                dbContacts = it
                filterContacts(_phoneNumber.value)
            }
        }
    }

    fun onDigitPressed(digit: String) {
        _phoneNumber.value += digit
        filterContacts(_phoneNumber.value)
    }

    fun onBackspace() {
        if (_phoneNumber.value.isNotEmpty()) {
            _phoneNumber.value = _phoneNumber.value.dropLast(1)
            filterContacts(_phoneNumber.value)
        }
    }

    fun onBackspaceLongClick() {
        _phoneNumber.value = ""
        filterContacts("")
    }

    fun onLongPressZero() {
        _phoneNumber.value += "+"
        filterContacts(_phoneNumber.value)
    }

    private fun filterContacts(query: String) {
        if (query.isEmpty()) {
            _matchingContacts.value = emptyList()
            return
        }

        // T9 Search logic:
        // 2 -> ABC, 3 -> DEF, 4 -> GHI, 5 -> JKL, 6 -> MNO, 7 -> PQRS, 8 -> TUV, 9 -> WXYZ
        val t9Map = mapOf(
            '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL",
            '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ"
        )

        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            // 1. Get system contacts
            val systemContacts = try {
                readPhoneContacts(getApplication()).map { pc ->
                    Contact(
                        id = "sys-${pc.phoneNumber}",
                        userId = "system",
                        name = pc.name,
                        phoneNumber = pc.phoneNumber,
                        email = pc.email,
                        syncedAt = "",
                        isBackedUp = false
                    )
                }
            } catch (e: Exception) {
                emptyList<Contact>()
            }

            // 2. Merge with DB contacts (deduplicate by phone)
            val allMerged = (dbContacts + systemContacts).distinctBy { it.phoneNumber }

            // 3. Filter
            val filtered = allMerged.filter { contact ->
                // Match phone number (strip formatting)
                val cleanContactPhone = contact.phoneNumber.replace(Regex("[^\\d+]"), "")
                val cleanQuery = query.replace(Regex("[^\\d+]"), "")
                val phoneMatch = cleanContactPhone.contains(cleanQuery)
                
                // Match T9 name
                val nameMatch = if (query.all { it.isDigit() || it == '+' }) {
                    matchT9(contact.name, query.filter { it.isDigit() }, t9Map)
                } else {
                    contact.name.contains(query, ignoreCase = true)
                }

                phoneMatch || nameMatch
            }.take(15)
            
            _matchingContacts.value = filtered
        }
    }

    private fun matchT9(name: String, digits: String, t9Map: Map<Char, String>): Boolean {
        if (digits.isEmpty()) return false
        val nameWords = name.uppercase().split(" ")
        
        return nameWords.any { word ->
            val wordDigits = word.map { char ->
                t9Map.entries.find { it.value.contains(char) }?.key ?: '?'
            }.joinToString("")
            wordDigits.startsWith(digits) || wordDigits.contains(digits)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                DialPadViewModel(app, app.container.contactRepository)
            }
        }
    }
}
