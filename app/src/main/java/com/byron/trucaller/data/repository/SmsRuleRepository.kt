package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.SmsRuleDao
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsRule
import com.byron.trucaller.data.model.SmsRuleType
import kotlinx.coroutines.flow.Flow

class SmsRuleRepository(private val smsRuleDao: SmsRuleDao) {

    fun getRulesByUser(userId: String): Flow<List<SmsRule>> =
        smsRuleDao.getByUserId(userId)

    suspend fun getActiveRulesByUser(userId: String): List<SmsRule> =
        smsRuleDao.getActiveByUserId(userId)

    suspend fun getRuleById(id: String): SmsRule? =
        smsRuleDao.getById(id)

    suspend fun insertRule(rule: SmsRule) =
        smsRuleDao.insert(rule)

    suspend fun updateRule(rule: SmsRule) =
        smsRuleDao.update(rule)

    suspend fun toggleRuleActive(id: String, isActive: Boolean) =
        smsRuleDao.updateActive(id, isActive)

    suspend fun deleteRule(rule: SmsRule) =
        smsRuleDao.delete(rule)

    suspend fun deleteRuleById(id: String) =
        smsRuleDao.deleteById(id)

    fun getRuleCount(userId: String): Flow<Int> =
        smsRuleDao.getCountByUser(userId)

    /**
     * Evaluate user rules against a message.
     *
     * Rules are already sorted by descending priority from the DAO.
     * Returns the target category of the first matching rule, or null if none match.
     */
    suspend fun evaluate(userId: String, sender: String, body: String): SmsCategory? {
        val rules = smsRuleDao.getActiveByUserId(userId)
        for (rule in rules) {
            if (matches(rule, sender, body)) {
                return rule.targetCategory
            }
        }
        return null
    }

    private fun matches(rule: SmsRule, sender: String, body: String): Boolean {
        return when (rule.ruleType) {
            SmsRuleType.SENDER_MATCH -> {
                sender.contains(rule.pattern, ignoreCase = true)
            }
            SmsRuleType.KEYWORD_MATCH -> {
                body.contains(rule.pattern, ignoreCase = true)
            }
            SmsRuleType.REGEX_MATCH -> {
                try {
                    val regex = Regex(rule.pattern, RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(body) || regex.containsMatchIn(sender)
                } catch (_: Exception) {
                    false
                }
            }
        }
    }
}
