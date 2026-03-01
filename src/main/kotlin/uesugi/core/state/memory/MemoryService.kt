package uesugi.core.state.memory

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class MemoryService {

    fun getFacts(
        botMark: String,
        groupId: String,
        subjects: List<String>,
        limit: Int = 25
    ): List<FactsEntity> {
        return transaction {
            val userFacts = FactsEntity.find {
                (FactsTable.botMark eq botMark) and
                        (FactsTable.groupId eq groupId) and
                        (FactsTable.scopeType eq Scopes.USER) and
                        (FactsTable.validFrom lessEq CurrentDateTime) and
                        (FactsTable.validTo.isNull() or (FactsTable.validTo greater CurrentDateTime))
            }.orderBy(FactsTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .reversed()
                .filter { fact ->
                    fact.subjects.split(",")
                        .any { subjects.contains(it) }
                }
            val groupFacts = FactsEntity.find {
                (FactsTable.botMark eq botMark) and
                        (FactsTable.groupId eq groupId) and
                        (FactsTable.scopeType eq Scopes.GROUP) and
                        (FactsTable.validFrom lessEq CurrentDateTime) and
                        (FactsTable.validTo.isNull() or (FactsTable.validTo greater CurrentDateTime))
            }.orderBy(FactsTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .reversed()
            userFacts + groupFacts
        }
    }

    fun getAllFactsByGroup(
        botMark: String,
        groupId: String
    ): List<FactsEntity> {
        return transaction {
            FactsEntity.find {
                (FactsTable.botMark eq botMark) and
                        (FactsTable.groupId eq groupId) and
                        (FactsTable.validFrom lessEq CurrentDateTime) and
                        (FactsTable.validTo.isNull() or (FactsTable.validTo greater CurrentDateTime))
            }.orderBy(FactsTable.createdAt to SortOrder.DESC)
                .reversed()
                .toList()
        }
    }

    fun getFactSize(
        botMark: String,
        groupId: String
    ): Long {
        return transaction {
            FactsEntity.find {
                (FactsTable.botMark eq botMark) and
                        (FactsTable.groupId eq groupId)
            }.count()
        }
    }

    fun getUserProfiles(
        botMark: String,
        groupId: String,
        userId: List<String>
    ): List<UserProfileEntity> {
        return transaction {
            UserProfileEntity.find {
                (UserProfileTable.botMark eq botMark) and
                        (UserProfileTable.groupId eq groupId) and
                        (UserProfileTable.userId inList userId)
            }.toList()
        }
    }

    fun getAllUserProfilesByGroup(
        botMark: String,
        groupId: String
    ): List<UserProfileEntity> {
        return transaction {
            UserProfileEntity.find {
                (UserProfileTable.botMark eq botMark) and
                        (UserProfileTable.groupId eq groupId)
            }.orderBy(UserProfileTable.createdAt to SortOrder.DESC)
                .reversed()
                .toList()
        }
    }

    fun getUserProfileSize(
        botMark: String,
        groupId: String
    ): Long {
        return transaction {
            UserProfileEntity.find {
                (UserProfileTable.botMark eq botMark) and
                        (UserProfileTable.groupId eq groupId)
            }.count()
        }
    }

    fun getSummary(
        botMark: String,
        groupId: String
    ): SummaryEntity? {
        return transaction {
            SummaryEntity.find {
                (SummaryTable.botMark eq botMark) and
                        (SummaryTable.groupId eq groupId)
            }.orderBy(SummaryTable.createdAt to SortOrder.DESC)
                .firstOrNull()
        }
    }

}