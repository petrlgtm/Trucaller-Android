package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.trucaller.backend.auth.userId
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.service.FcmService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

// ── Request / Response DTOs ─────────────────────────────────────────────

@Serializable
data class CreateFamilyGroupRequest(
    val name: String,
    val description: String? = null
)

@Serializable
data class InviteMemberRequest(
    val phoneNumber: String,
    val role: String = "MEMBER"
)

@Serializable
data class JoinGroupRequest(
    val inviteCode: String
)

@Serializable
data class UpdateMemberRoleRequest(
    val role: String
)

@Serializable
data class FamilyAlertRequest(
    val type: String,
    val message: String? = null,
    val targetUserId: String? = null
)

@Serializable
data class FamilyGroupResponse(
    val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val inviteCode: String,
    val memberCount: Int,
    val createdAt: String
)

@Serializable
data class FamilyGroupDetailResponse(
    val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val inviteCode: String,
    val memberCount: Int,
    val createdAt: String,
    val members: List<FamilyMemberResponse>
)

@Serializable
data class FamilyMemberResponse(
    val id: String,
    val groupId: String,
    val userId: String,
    val displayName: String,
    val phoneNumber: String,
    val role: String,
    val joinedAt: String
)

@Serializable
data class FamilyDeviceResponse(
    val userId: String,
    val displayName: String,
    val deviceId: String,
    val model: String,
    val manufacturer: String,
    val status: String,
    val lastSeen: String
)

// ── Routes ──────────────────────────────────────────────────────────────

/**
 * Registers all family-group management routes under `/api/family` on the Ktor [Route] receiver.
 * All endpoints require JWT authentication.
 */
fun Route.familyGroupRoutes() {
    authenticate("auth-jwt") {
        route("/api/family") {
            createGroup()
            getMyGroups()
            getGroupDetail()
            inviteMember()
            joinGroup()
            updateMemberRole()
            removeMember()
            deleteGroup()
            getGroupDevices()
            sendGroupAlert()
            regenerateInviteCode()
        }
    }
}

// ── POST /api/family/create ─────────────────────────────────────────────

private fun Route.createGroup() {
    post("/create") {
        val userId = call.userId()

        val request = try {
            call.receive<CreateFamilyGroupRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@post
        }

        if (request.name.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Group name is required")
            )
            return@post
        }

        // Look up the creating user's info
        val userDoc = Collections.users
            .find(Filters.eq("_id", userId))
            .firstOrNull()
        val userName = userDoc?.getString("fullName") ?: "Unknown"
        val userPhone = userDoc?.getString("phoneNumber") ?: ""

        val groupId = ObjectId().toString()
        val inviteCode = generateInviteCode()
        val now = Instant.now().toString()

        val groupDoc = Document()
            .append("_id", groupId)
            .append("name", request.name)
            .append("description", request.description ?: "")
            .append("ownerId", userId)
            .append("inviteCode", inviteCode)
            .append("createdAt", now)

        Collections.familyGroups.insertOne(groupDoc)

        // Add the creator as an OWNER member
        val memberId = ObjectId().toString()
        val memberDoc = Document()
            .append("_id", memberId)
            .append("groupId", groupId)
            .append("userId", userId)
            .append("displayName", userName)
            .append("phoneNumber", userPhone)
            .append("role", "OWNER")
            .append("joinedAt", now)

        Collections.familyMembers.insertOne(memberDoc)

        val memberCount = Collections.familyMembers
            .find(Filters.eq("groupId", groupId))
            .toList()
            .size

        val response = FamilyGroupResponse(
            id = groupId,
            name = request.name,
            description = request.description ?: "",
            ownerId = userId,
            inviteCode = inviteCode,
            memberCount = memberCount,
            createdAt = now
        )

        call.respond(
            HttpStatusCode.Created,
            ApiResponse(
                success = true,
                data = response,
                message = "Family group created successfully"
            )
        )
    }
}

// ── GET /api/family/my-groups ───────────────────────────────────────────

private fun Route.getMyGroups() {
    get("/my-groups") {
        val userId = call.userId()

        // Find all groups where the user is a member
        val memberDocs = Collections.familyMembers
            .find(Filters.eq("userId", userId))
            .toList()

        val groupIds = memberDocs.map { it.getString("groupId") }

        if (groupIds.isEmpty()) {
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = emptyList<FamilyGroupResponse>(),
                    message = "No family groups found"
                )
            )
            return@get
        }

        val groupDocs = Collections.familyGroups
            .find(Filters.`in`("_id", groupIds))
            .toList()

        val groups = groupDocs.map { doc ->
            val memberCount = Collections.familyMembers
                .find(Filters.eq("groupId", doc.getString("_id")))
                .toList()
                .size

            FamilyGroupResponse(
                id = doc.getString("_id"),
                name = doc.getString("name"),
                description = doc.getString("description") ?: "",
                ownerId = doc.getString("ownerId"),
                inviteCode = doc.getString("inviteCode"),
                memberCount = memberCount,
                createdAt = doc.getString("createdAt")
            )
        }

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = groups,
                message = "Retrieved ${groups.size} group(s)"
            )
        )
    }
}

// ── GET /api/family/{groupId} ───────────────────────────────────────────

private fun Route.getGroupDetail() {
    get("/{groupId}") {
        val userId = call.userId()
        val groupId = call.parameters["groupId"]

        if (groupId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing groupId")
            )
            return@get
        }

        // Verify the user is a member of this group
        val memberDoc = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", userId)
            ))
            .firstOrNull()

        if (memberDoc == null) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "You are not a member of this group")
            )
            return@get
        }

        val groupDoc = Collections.familyGroups
            .find(Filters.eq("_id", groupId))
            .firstOrNull()

        if (groupDoc == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(success = false, error = "Group not found")
            )
            return@get
        }

        // Get all members for this group
        val memberDocs = Collections.familyMembers
            .find(Filters.eq("groupId", groupId))
            .toList()

        val members = memberDocs.map { doc ->
            FamilyMemberResponse(
                id = doc.getString("_id"),
                groupId = doc.getString("groupId"),
                userId = doc.getString("userId"),
                displayName = doc.getString("displayName") ?: "Unknown",
                phoneNumber = doc.getString("phoneNumber") ?: "",
                role = doc.getString("role") ?: "MEMBER",
                joinedAt = doc.getString("joinedAt") ?: ""
            )
        }

        // A heterogeneous Map<String, Any> cannot be serialized by kotlinx —
        // this must stay a typed DTO or the route 500s at response time.
        val groupResponse = FamilyGroupDetailResponse(
            id = groupDoc.getString("_id"),
            name = groupDoc.getString("name"),
            description = groupDoc.getString("description") ?: "",
            ownerId = groupDoc.getString("ownerId"),
            inviteCode = groupDoc.getString("inviteCode"),
            memberCount = members.size,
            createdAt = groupDoc.getString("createdAt"),
            members = members
        )

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = groupResponse,
                message = "Group detail retrieved"
            )
        )
    }
}

// ── POST /api/family/{groupId}/invite ───────────────────────────────────

private fun Route.inviteMember() {
    post("/{groupId}/invite") {
        val userId = call.userId()
        val groupId = call.parameters["groupId"]

        if (groupId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing groupId")
            )
            return@post
        }

        val request = try {
            call.receive<InviteMemberRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@post
        }

        // Verify the requester is an OWNER or ADMIN of this group
        val callerMember = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", userId)
            ))
            .firstOrNull()

        if (callerMember == null || callerMember.getString("role") !in listOf("OWNER", "ADMIN")) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "Only OWNER or ADMIN can invite members")
            )
            return@post
        }

        val validRoles = listOf("MEMBER", "ADMIN")
        if (request.role !in validRoles) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = "Invalid role. Must be one of: ${validRoles.joinToString()}"
                )
            )
            return@post
        }

        // Look up the invited user by phone number
        val invitedUser = Collections.users
            .find(Filters.eq("phoneNumber", request.phoneNumber))
            .firstOrNull()

        if (invitedUser == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(success = false, error = "User with this phone number not found")
            )
            return@post
        }

        val invitedUserId = invitedUser.getString("_id")
            ?: invitedUser.getObjectId("_id").toString()

        // Check if already a member
        val existing = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", invitedUserId)
            ))
            .firstOrNull()

        if (existing != null) {
            call.respond(
                HttpStatusCode.Conflict,
                ApiResponse<Unit>(success = false, error = "User is already a member of this group")
            )
            return@post
        }

        val now = Instant.now().toString()
        val memberId = ObjectId().toString()
        val memberDoc = Document()
            .append("_id", memberId)
            .append("groupId", groupId)
            .append("userId", invitedUserId)
            .append("displayName", invitedUser.getString("fullName") ?: "Unknown")
            .append("phoneNumber", request.phoneNumber)
            .append("role", request.role)
            .append("joinedAt", now)

        Collections.familyMembers.insertOne(memberDoc)

        // Send FCM push to invited user's devices
        val invitedDevices = Collections.devices
            .find(Filters.eq("userId", invitedUserId))
            .toList()

        val groupDoc = Collections.familyGroups
            .find(Filters.eq("_id", groupId))
            .firstOrNull()
        val groupName = groupDoc?.getString("name") ?: "a family group"

        for (device in invitedDevices) {
            val fcmToken = device.getString("fcmToken")
            if (!fcmToken.isNullOrBlank()) {
                FcmService.sendPush(
                    fcmToken,
                    mapOf(
                        "action" to "FAMILY_INVITE",
                        "groupId" to groupId,
                        "groupName" to groupName
                    )
                )
            }
        }

        val response = FamilyMemberResponse(
            id = memberId,
            groupId = groupId,
            userId = invitedUserId,
            displayName = invitedUser.getString("fullName") ?: "Unknown",
            phoneNumber = request.phoneNumber,
            role = request.role,
            joinedAt = now
        )

        call.respond(
            HttpStatusCode.Created,
            ApiResponse(
                success = true,
                data = response,
                message = "Member invited successfully"
            )
        )
    }
}

// ── POST /api/family/join ───────────────────────────────────────────────

private fun Route.joinGroup() {
    post("/join") {
        val userId = call.userId()

        val request = try {
            call.receive<JoinGroupRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@post
        }

        if (request.inviteCode.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invite code is required")
            )
            return@post
        }

        // Find the group by invite code
        val groupDoc = Collections.familyGroups
            .find(Filters.eq("inviteCode", request.inviteCode))
            .firstOrNull()

        if (groupDoc == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(success = false, error = "Invalid invite code")
            )
            return@post
        }

        val groupId = groupDoc.getString("_id")

        // Check if already a member
        val existing = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", userId)
            ))
            .firstOrNull()

        if (existing != null) {
            call.respond(
                HttpStatusCode.Conflict,
                ApiResponse<Unit>(success = false, error = "You are already a member of this group")
            )
            return@post
        }

        // Look up user info
        val userDoc = Collections.users
            .find(Filters.eq("_id", userId))
            .firstOrNull()
        val userName = userDoc?.getString("fullName") ?: "Unknown"
        val userPhone = userDoc?.getString("phoneNumber") ?: ""

        val now = Instant.now().toString()
        val memberId = ObjectId().toString()
        val memberDoc = Document()
            .append("_id", memberId)
            .append("groupId", groupId)
            .append("userId", userId)
            .append("displayName", userName)
            .append("phoneNumber", userPhone)
            .append("role", "MEMBER")
            .append("joinedAt", now)

        Collections.familyMembers.insertOne(memberDoc)

        val memberCount = Collections.familyMembers
            .find(Filters.eq("groupId", groupId))
            .toList()
            .size

        val response = FamilyGroupResponse(
            id = groupId,
            name = groupDoc.getString("name"),
            description = groupDoc.getString("description") ?: "",
            ownerId = groupDoc.getString("ownerId"),
            inviteCode = groupDoc.getString("inviteCode"),
            memberCount = memberCount,
            createdAt = groupDoc.getString("createdAt")
        )

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = response,
                message = "Successfully joined the group"
            )
        )
    }
}

// ── PUT /api/family/{groupId}/members/{userId}/role ─────────────────────

private fun Route.updateMemberRole() {
    put("/{groupId}/members/{memberId}/role") {
        val currentUserId = call.userId()
        val groupId = call.parameters["groupId"]
        val targetUserId = call.parameters["memberId"]

        if (groupId.isNullOrBlank() || targetUserId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing groupId or userId")
            )
            return@put
        }

        val request = try {
            call.receive<UpdateMemberRoleRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@put
        }

        val validRoles = listOf("MEMBER", "ADMIN")
        if (request.role !in validRoles) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = "Invalid role. Must be one of: ${validRoles.joinToString()}"
                )
            )
            return@put
        }

        // Verify the requester is OWNER of this group
        val callerMember = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", currentUserId)
            ))
            .firstOrNull()

        if (callerMember == null || callerMember.getString("role") != "OWNER") {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "Only the group OWNER can change member roles")
            )
            return@put
        }

        // Cannot change owner's own role
        if (targetUserId == currentUserId) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Cannot change your own role as OWNER")
            )
            return@put
        }

        // Find the target member
        val targetMember = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", targetUserId)
            ))
            .firstOrNull()

        if (targetMember == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(success = false, error = "Member not found in this group")
            )
            return@put
        }

        Collections.familyMembers.updateOne(
            Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", targetUserId)
            ),
            Document("\$set", Document("role", request.role))
        )

        call.respond(
            HttpStatusCode.OK,
            ApiResponse<Nothing>(
                success = true,
                message = "Member role updated to ${request.role}"
            )
        )
    }
}

// ── DELETE /api/family/{groupId}/members/{userId} ───────────────────────

private fun Route.removeMember() {
    delete("/{groupId}/members/{memberId}") {
        val currentUserId = call.userId()
        val groupId = call.parameters["groupId"]
        val targetUserId = call.parameters["memberId"]

        if (groupId.isNullOrBlank() || targetUserId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing groupId or userId")
            )
            return@delete
        }

        // Verify the requester is OWNER/ADMIN, or is removing themselves
        val callerMember = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", currentUserId)
            ))
            .firstOrNull()

        if (callerMember == null) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "You are not a member of this group")
            )
            return@delete
        }

        val callerRole = callerMember.getString("role") ?: "MEMBER"
        val isSelfRemoval = currentUserId == targetUserId

        if (!isSelfRemoval && callerRole !in listOf("OWNER", "ADMIN")) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "Only OWNER or ADMIN can remove other members")
            )
            return@delete
        }

        // Cannot remove the OWNER (they must transfer ownership first or delete the group)
        val targetMember = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", targetUserId)
            ))
            .firstOrNull()

        if (targetMember == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(success = false, error = "Member not found in this group")
            )
            return@delete
        }

        if (targetMember.getString("role") == "OWNER" && !isSelfRemoval) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "Cannot remove the group OWNER")
            )
            return@delete
        }

        // If OWNER is leaving, delete the entire group
        if (isSelfRemoval && callerRole == "OWNER") {
            Collections.familyMembers.deleteMany(Filters.eq("groupId", groupId))
            Collections.familyGroups.deleteOne(Filters.eq("_id", groupId))

            call.respond(
                HttpStatusCode.OK,
                ApiResponse<Nothing>(
                    success = true,
                    message = "Group deleted because the owner left"
                )
            )
            return@delete
        }

        Collections.familyMembers.deleteOne(
            Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", targetUserId)
            )
        )

        call.respond(
            HttpStatusCode.OK,
            ApiResponse<Nothing>(
                success = true,
                message = "Member removed from group"
            )
        )
    }
}

// ── DELETE /api/family/{groupId} ────────────────────────────────────────

private fun Route.deleteGroup() {
    delete("/{groupId}") {
        val currentUserId = call.userId()
        val groupId = call.parameters["groupId"]

        if (groupId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing groupId")
            )
            return@delete
        }

        val groupDoc = Collections.familyGroups.find(Filters.eq("_id", groupId)).firstOrNull()
        if (groupDoc == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(success = false, error = "Group not found")
            )
            return@delete
        }

        if (groupDoc.getString("ownerId") != currentUserId) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "Only the group OWNER can delete the group")
            )
            return@delete
        }

        Collections.familyMembers.deleteMany(Filters.eq("groupId", groupId))
        Collections.familyGroups.deleteOne(Filters.eq("_id", groupId))

        call.respond(
            HttpStatusCode.OK,
            ApiResponse<Nothing>(success = true, message = "Group deleted successfully")
        )
    }
}

// ── GET /api/family/{groupId}/devices ───────────────────────────────────

private fun Route.getGroupDevices() {
    get("/{groupId}/devices") {
        val userId = call.userId()
        val groupId = call.parameters["groupId"]

        if (groupId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing groupId")
            )
            return@get
        }

        // Verify the requester is a member of this group
        val callerMember = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", userId)
            ))
            .firstOrNull()

        if (callerMember == null) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "You are not a member of this group")
            )
            return@get
        }

        // Get all members of this group
        val memberDocs = Collections.familyMembers
            .find(Filters.eq("groupId", groupId))
            .toList()

        val memberUserIds = memberDocs.map { it.getString("userId") }
        val memberNameMap = memberDocs.associate {
            it.getString("userId") to (it.getString("displayName") ?: "Unknown")
        }

        // Get all devices belonging to group members
        val devices = mutableListOf<FamilyDeviceResponse>()
        for (memberId in memberUserIds) {
            val memberDevices = Collections.devices
                .find(Filters.eq("userId", memberId))
                .toList()

            for (deviceDoc in memberDevices) {
                devices.add(
                    FamilyDeviceResponse(
                        userId = memberId,
                        displayName = memberNameMap[memberId] ?: "Unknown",
                        deviceId = deviceDoc.getString("deviceId"),
                        model = deviceDoc.getString("model") ?: "",
                        manufacturer = deviceDoc.getString("manufacturer") ?: "",
                        status = deviceDoc.getString("status") ?: "ACTIVE",
                        lastSeen = deviceDoc.getString("lastSeen") ?: ""
                    )
                )
            }
        }

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = devices,
                message = "Retrieved ${devices.size} device(s) across ${memberUserIds.size} member(s)"
            )
        )
    }
}

// ── POST /api/family/{groupId}/alert ────────────────────────────────────

private fun Route.sendGroupAlert() {
    post("/{groupId}/alert") {
        val userId = call.userId()
        val groupId = call.parameters["groupId"]

        if (groupId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing groupId")
            )
            return@post
        }

        val request = try {
            call.receive<FamilyAlertRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@post
        }

        val validTypes = listOf("EMERGENCY", "CHECK_IN", "LOCATION_REQUEST", "CUSTOM")
        if (request.type !in validTypes) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = "Invalid alert type. Must be one of: ${validTypes.joinToString()}"
                )
            )
            return@post
        }

        // Verify the requester is a member of this group
        val callerMember = Collections.familyMembers
            .find(Filters.and(
                Filters.eq("groupId", groupId),
                Filters.eq("userId", userId)
            ))
            .firstOrNull()

        if (callerMember == null) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "You are not a member of this group")
            )
            return@post
        }

        // Determine target members: specific user or all group members (excluding sender)
        val targetUserIds = if (request.targetUserId != null) {
            // Verify target is in the group
            val targetMember = Collections.familyMembers
                .find(Filters.and(
                    Filters.eq("groupId", groupId),
                    Filters.eq("userId", request.targetUserId)
                ))
                .firstOrNull()

            if (targetMember == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, error = "Target user is not a member of this group")
                )
                return@post
            }
            listOf(request.targetUserId)
        } else {
            // All members except the sender
            Collections.familyMembers
                .find(Filters.and(
                    Filters.eq("groupId", groupId),
                    Filters.ne("userId", userId)
                ))
                .toList()
                .map { it.getString("userId") }
        }

        // Look up sender info
        val senderDoc = Collections.users
            .find(Filters.eq("_id", userId))
            .firstOrNull()
        val senderName = senderDoc?.getString("fullName") ?: "A family member"

        val groupDoc = Collections.familyGroups
            .find(Filters.eq("_id", groupId))
            .firstOrNull()
        val groupName = groupDoc?.getString("name") ?: "Family group"

        // Send FCM push to all target users' devices
        var sentCount = 0
        for (targetId in targetUserIds) {
            val targetDevices = Collections.devices
                .find(Filters.eq("userId", targetId))
                .toList()

            for (device in targetDevices) {
                val fcmToken = device.getString("fcmToken")
                if (!fcmToken.isNullOrBlank()) {
                    val pushSent = FcmService.sendPush(
                        fcmToken,
                        mapOf(
                            "action" to "FAMILY_ALERT",
                            "alertType" to request.type,
                            "groupId" to groupId,
                            "groupName" to groupName,
                            "senderName" to senderName,
                            "message" to (request.message ?: "")
                        )
                    )
                    if (pushSent) sentCount++
                }
            }
        }

        call.respond(
            HttpStatusCode.OK,
            ApiResponse<Nothing>(
                success = true,
                message = "Alert sent to ${targetUserIds.size} member(s), $sentCount device(s) notified"
            )
        )
    }
}

// ── POST /api/family/{groupId}/regenerate-invite ─────────────────────────

private fun Route.regenerateInviteCode() {
    post("/{groupId}/regenerate-invite") {
        val currentUserId = call.userId()
        val groupId = call.parameters["groupId"]

        if (groupId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing groupId")
            )
            return@post
        }

        val groupDoc = Collections.familyGroups.find(Filters.eq("_id", groupId)).firstOrNull()
        if (groupDoc == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(success = false, error = "Group not found")
            )
            return@post
        }

        if (groupDoc.getString("ownerId") != currentUserId) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "Only the group OWNER can regenerate the invite code")
            )
            return@post
        }

        val newCode = generateInviteCode()
        Collections.familyGroups.updateOne(
            Filters.eq("_id", groupId),
            Document("\$set", Document("inviteCode", newCode))
        )

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = mapOf("inviteCode" to newCode),
                message = "Invite code regenerated"
            )
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────

/**
 * Generates a short, URL-safe invite code (8 alphanumeric characters).
 */
private fun generateInviteCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    return (1..8).map { chars.random() }.joinToString("")
}
