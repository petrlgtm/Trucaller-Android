package com.trucaller.backend.service

import com.trucaller.backend.data.Collections
import org.bson.Document
import java.time.Instant

/**
 * Append-only audit trail for privileged admin mutations.
 *
 * Every state-changing admin action that affects another account or shared data
 * (trust-score / spam-score edits, role changes, manual flag/clear) should call
 * [record] so the change is attributable and reviewable. Writes are best-effort:
 * an audit failure must never block the underlying admin operation, but is logged.
 */
object AdminAudit {

    /**
     * Records a single admin mutation.
     *
     * @param adminId    the acting admin's user id (from the JWT subject)
     * @param action     short machine-readable action key, e.g. "UPDATE_TRUST_SCORE"
     * @param targetType the kind of entity affected, e.g. "user" / "callerId"
     * @param targetId   the affected entity's id
     * @param details    optional before/after values and context
     */
    suspend fun record(
        adminId: String,
        action: String,
        targetType: String,
        targetId: String,
        details: Map<String, Any?> = emptyMap()
    ) {
        runCatching {
            val doc = Document()
                .append("adminId", adminId)
                .append("action", action)
                .append("targetType", targetType)
                .append("targetId", targetId)
                .append("timestamp", Instant.now().toString())
            details.forEach { (k, v) -> if (v != null) doc.append(k, v) }
            Collections.adminAuditLog.insertOne(doc)
        }
    }
}
