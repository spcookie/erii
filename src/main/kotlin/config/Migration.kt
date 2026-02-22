package uesugi.config

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import uesugi.core.emotion.EmotionTable
import uesugi.core.evolution.LearnedVocabTable
import uesugi.core.flow.FlowStateTable
import uesugi.core.history.HistoryTable
import uesugi.core.memory.FactsTable
import uesugi.core.memory.MemoryStateTable
import uesugi.core.memory.SummaryTable
import uesugi.core.memory.UserProfileTable
import uesugi.core.resource.ResourceTable
import uesugi.core.volition.VolitionStateTable

fun migration(database: Database) {
    transaction(database) {
        val migration = MigrationUtils.statementsRequiredForDatabaseMigration(
            HistoryTable,
            ResourceTable,
            EmotionTable,
            FactsTable,
            UserProfileTable,
            SummaryTable,
            MemoryStateTable,
            LearnedVocabTable,
            FlowStateTable,
            VolitionStateTable
        )
        execInBatch(migration)
    }
}

private fun init(database: Database) {
    transaction(database) {
        SchemaUtils.create(
            HistoryTable,
            ResourceTable,
            EmotionTable,
            FactsTable,
            UserProfileTable,
            SummaryTable,
            MemoryStateTable,
            LearnedVocabTable,
            FlowStateTable,
            VolitionStateTable,
            inBatch = true
        )
    }
}

fun migrationIf(condition: Boolean, database: Database) {
    init(database)
    if (condition) {
        migration(database)
    }
}