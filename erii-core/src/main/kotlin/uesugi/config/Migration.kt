package uesugi.config

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import uesugi.common.HistoryTable
import uesugi.core.message.resource.ResourceTable
import uesugi.core.state.emotion.EmotionTable
import uesugi.core.state.evolution.LearnedVocabTable
import uesugi.core.state.flow.FlowStateTable
import uesugi.core.state.meme.MemeData.MemeScanStateTable
import uesugi.core.state.meme.MemeData.MemeTable
import uesugi.core.state.memory.FactsTable
import uesugi.core.state.memory.MemoryStateTable
import uesugi.core.state.memory.SummaryTable
import uesugi.core.state.memory.UserProfileTable
import uesugi.core.state.volition.VolitionStateTable

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
            MemeTable,
            MemeScanStateTable,
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