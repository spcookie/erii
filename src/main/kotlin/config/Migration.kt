package uesugi.config

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import uesugi.core.emotion.EmotionTable
import uesugi.core.evolution.LearnedVocabTable
import uesugi.core.flow.FlowStateTable
import uesugi.core.history.HistoryTable
import uesugi.core.memory.*
import uesugi.core.resource.ResourceTable
import uesugi.core.volition.VolitionStateTable

fun migration(database: Database) {
    transaction(database) {
        val migration = MigrationUtils.statementsRequiredForDatabaseMigration(
            HistoryTable,
            ResourceTable,
            EmotionTable,
            FactsTable,
            TodoTable,
            UserProfileTable,
            SummaryTable,
            MemoryStateTable,
            LearnedVocabTable,
            FlowStateTable,
            VolitionStateTable
        )
//        execInBatch(migration)
    }
}