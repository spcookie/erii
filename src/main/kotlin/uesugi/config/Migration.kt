package uesugi.config

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import uesugi.core.message.history.HistoryTable
import uesugi.core.message.resource.ResourceTable

fun migration(database: Database) {
    transaction(database) {
        val migration = MigrationUtils.statementsRequiredForDatabaseMigration(
            HistoryTable,
            ResourceTable,
            _root_ide_package_.uesugi.core.state.emotion.EmotionTable,
            _root_ide_package_.uesugi.core.state.memory.FactsTable,
            _root_ide_package_.uesugi.core.state.memory.UserProfileTable,
            _root_ide_package_.uesugi.core.state.memory.SummaryTable,
            _root_ide_package_.uesugi.core.state.memory.MemoryStateTable,
            _root_ide_package_.uesugi.core.state.evolution.LearnedVocabTable,
            _root_ide_package_.uesugi.core.state.flow.FlowStateTable,
            _root_ide_package_.uesugi.core.state.volition.VolitionStateTable
        )
        execInBatch(migration)
    }
}

private fun init(database: Database) {
    transaction(database) {
        SchemaUtils.create(
            HistoryTable,
            ResourceTable,
            _root_ide_package_.uesugi.core.state.emotion.EmotionTable,
            _root_ide_package_.uesugi.core.state.memory.FactsTable,
            _root_ide_package_.uesugi.core.state.memory.UserProfileTable,
            _root_ide_package_.uesugi.core.state.memory.SummaryTable,
            _root_ide_package_.uesugi.core.state.memory.MemoryStateTable,
            _root_ide_package_.uesugi.core.state.evolution.LearnedVocabTable,
            _root_ide_package_.uesugi.core.state.flow.FlowStateTable,
            _root_ide_package_.uesugi.core.state.volition.VolitionStateTable,
            inBatch = true
        )
    }
}

fun migrationIf(condition: Boolean, database: Database) {
    _root_ide_package_.uesugi.config.init(database)
    if (condition) {
        _root_ide_package_.uesugi.config.migration(database)
    }
}