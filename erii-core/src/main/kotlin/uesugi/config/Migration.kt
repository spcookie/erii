package uesugi.config

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import uesugi.common.data.HistoryTable
import uesugi.common.data.ResourceTable
import uesugi.core.component.usage.TokenUsageTable
import uesugi.core.state.emotion.EmotionTable
import uesugi.core.state.evolution.LearnedVocabTable
import uesugi.core.state.flow.FlowStateTable
import uesugi.core.state.meme.MemeData.MemeScanStateTable
import uesugi.core.state.meme.MemeData.MemeTable
import uesugi.core.state.memory.FactsTable
import uesugi.core.state.memory.MemoryStateTable
import uesugi.core.state.memory.UserProfileTable
import uesugi.core.state.summary.SummaryStateTable
import uesugi.core.state.summary.SummaryTable
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
            SummaryStateTable,
            MemoryStateTable,
            LearnedVocabTable,
            FlowStateTable,
            VolitionStateTable,
            TokenUsageTable
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
            SummaryStateTable,
            MemoryStateTable,
            LearnedVocabTable,
            FlowStateTable,
            VolitionStateTable,
            MemeTable,
            MemeScanStateTable,
            TokenUsageTable,
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
