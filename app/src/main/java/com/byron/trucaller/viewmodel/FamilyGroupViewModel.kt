package com.byron.trucaller.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.data.model.FamilyGroup
import com.byron.trucaller.data.model.FamilyGroupMember
import com.byron.trucaller.data.model.FamilyGroupRole
import com.byron.trucaller.service.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents a device within a family group, with resolved location data
 * for rendering on the family device map.
 */
data class FamilyDeviceMapEntry(
    val deviceId: String,
    val deviceModel: String,
    val memberName: String,
    val memberId: String,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val lastSeen: String
)

data class FamilyGroupUiState(
    val groups: List<FamilyGroup> = emptyList(),
    val selectedGroup: FamilyGroup? = null,
    val memberDevices: List<FamilyDeviceMapEntry> = emptyList(),
    val isLoadingDevices: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class FamilyGroupViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FamilyGroupUiState())
    val uiState: StateFlow<FamilyGroupUiState> = _uiState.asStateFlow()

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun loadGroups(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = ApiClient.getFamilyGroups(userId)
                if (result.success && result.data != null) {
                    val groups = result.data.map { it.toFamilyGroup() }
                    _uiState.value = _uiState.value.copy(
                        groups = groups,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error ?: "Failed to load groups"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load family groups", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    fun loadGroupDetail(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = ApiClient.getFamilyGroup(groupId)
                if (result.success && result.data != null) {
                    val group = result.data.toFamilyGroup()
                    _uiState.value = _uiState.value.copy(
                        selectedGroup = group,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error ?: "Failed to load group"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load group detail", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    fun createGroup(name: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = ApiClient.createFamilyGroup(name, userId)
                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Group \"$name\" created"
                    )
                    loadGroups(userId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error ?: "Failed to create group"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create family group", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    fun renameGroup(groupId: String, newName: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = ApiClient.updateFamilyGroup(groupId, newName)
                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Group renamed to \"$newName\""
                    )
                    loadGroupDetail(groupId)
                    loadGroups(userId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error ?: "Failed to rename group"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename group", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    fun deleteGroup(groupId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = ApiClient.deleteFamilyGroup(groupId)
                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        selectedGroup = null,
                        successMessage = "Group deleted"
                    )
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error ?: "Failed to delete group"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete group", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    fun joinGroup(inviteCode: String, userId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = ApiClient.joinFamilyGroup(inviteCode, userId)
                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Joined group successfully"
                    )
                    loadGroups(userId)
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error ?: "Invalid invite code"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join group", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    fun leaveGroup(groupId: String, userId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = ApiClient.leaveFamilyGroup(groupId, userId)
                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        selectedGroup = null,
                        successMessage = "Left group"
                    )
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error ?: "Failed to leave group"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to leave group", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    fun removeMember(groupId: String, memberId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                val result = ApiClient.removeFamilyGroupMember(groupId, memberId)
                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Member removed"
                    )
                    loadGroupDetail(groupId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.error ?: "Failed to remove member"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove member", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    fun promoteMember(groupId: String, memberId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                val result = ApiClient.promoteFamilyGroupMember(groupId, memberId)
                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Member promoted to admin"
                    )
                    loadGroupDetail(groupId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.error ?: "Failed to promote member"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to promote member", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    fun regenerateInviteCode(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                val result = ApiClient.regenerateInviteCode(groupId)
                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Invite code regenerated"
                    )
                    loadGroupDetail(groupId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.error ?: "Failed to regenerate invite code"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to regenerate invite code", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    // ── Device Map Methods ─────────────────────────────────────────────

    /**
     * Fetches all devices belonging to members of the currently selected
     * group, resolving each device's last known location from the backend.
     * Populates [FamilyGroupUiState.memberDevices] for the device map.
     */
    fun fetchGroupMemberDevices(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDevices = true)
            try {
                val group = _uiState.value.selectedGroup
                    ?: _uiState.value.groups.find { it.id == groupId }

                if (group == null) {
                    // Load the group detail and then retry — loadGroupDetail is async,
                    // so we wait for selectedGroup to be populated before continuing.
                    loadGroupDetail(groupId)
                    // Give the detail load a moment, then retry with updated state
                    kotlinx.coroutines.delay(500)
                    val retryGroup = _uiState.value.selectedGroup
                        ?: _uiState.value.groups.find { it.id == groupId }
                    if (retryGroup == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingDevices = false,
                            error = "Group not found"
                        )
                        return@launch
                    }
                    // Fall through with the retried group — reassign val via a local
                    fetchGroupMemberDevicesInternal(retryGroup)
                    return@launch
                }

                fetchGroupMemberDevicesInternal(group)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch group member devices", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingDevices = false,
                    error = "Failed to load device locations: ${e.message}"
                )
            }
        }
    }

    private suspend fun fetchGroupMemberDevicesInternal(group: com.byron.trucaller.data.model.FamilyGroup) {
                val entries = mutableListOf<FamilyDeviceMapEntry>()

                for (member in group.members) {
                    try {
                        val devicesResult = ApiClient.getDevices(member.userId)
                        if (devicesResult.success && devicesResult.data != null) {
                            for (deviceMap in devicesResult.data) {
                                val devId = deviceMap["id"]?.toString() ?: continue
                                val model = deviceMap["model"]?.toString() ?: "Unknown"
                                val status = deviceMap["status"]?.toString() ?: "UNKNOWN"
                                val lastSeen = deviceMap["lastSeen"]?.toString() ?: ""

                                // Attempt to get location from IP logs
                                var lat: Double? = null
                                var lon: Double? = null
                                try {
                                    val logsResult = ApiClient.getDeviceIpLogs(devId)
                                    if (logsResult.success && logsResult.data != null) {
                                        val latest = logsResult.data.maxByOrNull {
                                            it["timestamp"]?.toString() ?: ""
                                        }
                                        lat = (latest?.get("latitude") as? Number)?.toDouble()
                                        lon = (latest?.get("longitude") as? Number)?.toDouble()
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to get IP logs for device $devId", e)
                                }

                                entries.add(
                                    FamilyDeviceMapEntry(
                                        deviceId = devId,
                                        deviceModel = model,
                                        memberName = member.fullName,
                                        memberId = member.userId,
                                        status = status,
                                        latitude = lat,
                                        longitude = lon,
                                        lastSeen = lastSeen
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch devices for member ${member.userId}", e)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    memberDevices = entries,
                    isLoadingDevices = false
                )
    }

    /**
     * Sends a ring command to a family member's device via FCM.
     */
    fun ringFamilyDevice(deviceId: String, triggeredBy: String, triggeredByName: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.triggerAlarm(mapOf(
                    "deviceId" to deviceId,
                    "action" to "REMOTE_ALARM",
                    "triggeredBy" to triggeredBy,
                    "triggeredByName" to triggeredByName,
                    "triggeredByRole" to "family_member",
                    "notes" to "Family ring via device map"
                ))
                _uiState.value = _uiState.value.copy(
                    successMessage = if (result.success) "Ring command sent" else "Ring failed: ${result.error}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Ring family device failed", e)
                _uiState.value = _uiState.value.copy(
                    error = "Ring failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Sends a lock command to a family member's device via FCM.
     */
    fun lockFamilyDevice(deviceId: String, triggeredBy: String, triggeredByName: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.triggerAlarm(mapOf(
                    "deviceId" to deviceId,
                    "action" to "LOCK_DEVICE",
                    "triggeredBy" to triggeredBy,
                    "triggeredByName" to triggeredByName,
                    "triggeredByRole" to "family_member",
                    "notes" to "Family lock via device map"
                ))
                _uiState.value = _uiState.value.copy(
                    successMessage = if (result.success) "Lock command sent" else "Lock failed: ${result.error}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Lock family device failed", e)
                _uiState.value = _uiState.value.copy(
                    error = "Lock failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Sends a location request to a family member's device via FCM.
     */
    fun requestFamilyDeviceLocation(deviceId: String, triggeredBy: String, triggeredByName: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.triggerAlarm(mapOf(
                    "deviceId" to deviceId,
                    "action" to "LOCATION_REQUEST",
                    "triggeredBy" to triggeredBy,
                    "triggeredByName" to triggeredByName,
                    "triggeredByRole" to "family_member",
                    "notes" to "Family location request via device map"
                ))
                _uiState.value = _uiState.value.copy(
                    successMessage = if (result.success) "Location request sent" else "Location request failed: ${result.error}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Location request failed", e)
                _uiState.value = _uiState.value.copy(
                    error = "Location request failed: ${e.message}"
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.toFamilyGroup(): FamilyGroup {
        val membersRaw = (this["members"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
        val members = membersRaw.map { m ->
            FamilyGroupMember(
                userId = m["userId"]?.toString() ?: "",
                fullName = m["fullName"]?.toString() ?: "",
                phoneNumber = m["phoneNumber"]?.toString() ?: "",
                role = try {
                    FamilyGroupRole.valueOf(m["role"]?.toString()?.uppercase() ?: "MEMBER")
                } catch (_: Exception) {
                    FamilyGroupRole.MEMBER
                },
                joinedAt = m["joinedAt"]?.toString() ?: "",
                avatarUrl = m["avatarUrl"]?.toString()
            )
        }
        return FamilyGroup.fromApi(
            id = this["id"]?.toString() ?: "",
            name = this["name"]?.toString() ?: "",
            ownerId = this["ownerId"]?.toString() ?: this["createdBy"]?.toString() ?: "",
            createdAt = this["createdAt"]?.toString() ?: "",
            inviteCode = this["inviteCode"]?.toString() ?: "",
            members = members,
            maxMembers = (this["maxMembers"] as? Number)?.toInt() ?: 10,
            description = this["description"]?.toString() ?: ""
        )
    }

    companion object {
        private const val TAG = "FamilyGroupVM"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FamilyGroupViewModel()
            }
        }
    }
}
