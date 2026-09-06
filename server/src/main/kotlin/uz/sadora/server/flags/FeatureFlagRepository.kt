package uz.sadora.server.flags

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.FeatureFlagRules
import uz.sadora.server.db.FeatureFlagsTable
import uz.sadora.server.db.dbQuery

data class FlagDefinition(
    val key: String,
    val description: String,
    val enabled: Boolean,
    val defaultValue: Boolean,
)

/**
 * A targeting rule. Every nullable field means "any"; [rolloutPercentage] narrows a
 * match further to a stable slice of the users it matched.
 */
data class FlagRule(
    val id: Uuid,
    val flagKey: String,
    val environment: String?,
    val country: String?,
    val language: String?,
    val lifeStage: String?,
    val platform: String?,
    val cohort: String?,
    val rolloutPercentage: Int,
    val value: Boolean,
    val priority: Int,
)

class FeatureFlagRepository {

    suspend fun all(): List<FlagDefinition> = dbQuery {
        // Same reason as the feature definitions: a toggled flag must not jump rows.
        FeatureFlagsTable.selectAll()
            .orderBy(FeatureFlagsTable.key to SortOrder.ASC)
            .map { it.toDefinition() }
    }

    suspend fun rules(): List<FlagRule> = dbQuery {
        FeatureFlagRules.selectAll()
            .orderBy(FeatureFlagRules.priority to SortOrder.ASC)
            .map { it.toRule() }
    }

    suspend fun setEnabled(key: String, enabled: Boolean, defaultValue: Boolean, updatedBy: Uuid?): Boolean =
        dbQuery {
            FeatureFlagsTable.update({ FeatureFlagsTable.key eq key }) {
                it[FeatureFlagsTable.enabled] = enabled
                it[FeatureFlagsTable.defaultValue] = defaultValue
                it[updatedAt] = now().toOffsetDateTime()
                it[FeatureFlagsTable.updatedBy] = updatedBy
            } > 0
        }

    suspend fun addRule(rule: FlagRule): Uuid = dbQuery {
        val id = Uuid.random()
        FeatureFlagRules.insert {
            it[FeatureFlagRules.id] = id
            it[flagKey] = rule.flagKey
            it[environment] = rule.environment
            it[country] = rule.country
            it[language] = rule.language
            it[lifeStage] = rule.lifeStage
            it[platform] = rule.platform
            it[cohort] = rule.cohort
            it[rolloutPercentage] = rule.rolloutPercentage
            it[value] = rule.value
            it[priority] = rule.priority
            it[createdAt] = now().toOffsetDateTime()
        }
        id
    }

    suspend fun removeRule(id: Uuid): Boolean = dbQuery {
        FeatureFlagRules.deleteWhere { FeatureFlagRules.id eq id } > 0
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toDefinition() = FlagDefinition(
        key = this[FeatureFlagsTable.key],
        description = this[FeatureFlagsTable.description],
        enabled = this[FeatureFlagsTable.enabled],
        defaultValue = this[FeatureFlagsTable.defaultValue],
    )

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRule() = FlagRule(
        id = this[FeatureFlagRules.id],
        flagKey = this[FeatureFlagRules.flagKey],
        environment = this[FeatureFlagRules.environment],
        country = this[FeatureFlagRules.country],
        language = this[FeatureFlagRules.language],
        lifeStage = this[FeatureFlagRules.lifeStage],
        platform = this[FeatureFlagRules.platform],
        cohort = this[FeatureFlagRules.cohort],
        rolloutPercentage = this[FeatureFlagRules.rolloutPercentage],
        value = this[FeatureFlagRules.value],
        priority = this[FeatureFlagRules.priority],
    )
}
