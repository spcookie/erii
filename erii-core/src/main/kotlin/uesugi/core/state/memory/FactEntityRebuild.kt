package uesugi.core.state.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import uesugi.config.ConnectionFactoryConfig
import uesugi.config.migrationIf

data class FactEntityRebuildOptions(
    val dryRun: Boolean = false,
    val onlyEmpty: Boolean = true,
    val includeInvalid: Boolean = false,
    val botMark: String? = null,
    val groupId: String? = null,
    val limit: Int? = null
)

data class FactEntityRebuildSummary(
    val scanned: Int,
    val updated: Int,
    val unchanged: Int,
    val failed: Int
)

data class FactEntityRebuildItem(
    val factId: Int,
    val keyword: String,
    val before: List<String>,
    val after: List<String>,
    val updated: Boolean,
    val error: String? = null
)

data class FactEntityRebuildReport(
    val summary: FactEntityRebuildSummary,
    val items: List<FactEntityRebuildItem>
)

class FactEntityRebuildRunner(
    private val repository: MemoryRepository,
    private val analyzer: suspend (FactsRecord) -> List<String>
) {
    suspend fun run(options: FactEntityRebuildOptions = FactEntityRebuildOptions()): FactEntityRebuildReport {
        val facts = withContext(Dispatchers.IO) {
            repository.getFactsForEntityRebuild(
                botMark = options.botMark,
                groupId = options.groupId,
                onlyEmptyEntities = options.onlyEmpty,
                includeInvalid = options.includeInvalid,
                limit = options.limit
            )
        }

        val items = facts.map { fact ->
            try {
                val normalized = normalizeEntities(analyzer(fact))
                val changed = normalized != fact.entities
                if (changed && !options.dryRun) {
                    withContext(Dispatchers.IO) {
                        repository.updateFactEntities(fact.id, normalized)
                    }
                }
                FactEntityRebuildItem(
                    factId = fact.id,
                    keyword = fact.keyword,
                    before = fact.entities,
                    after = normalized,
                    updated = changed && !options.dryRun
                )
            } catch (e: Exception) {
                FactEntityRebuildItem(
                    factId = fact.id,
                    keyword = fact.keyword,
                    before = fact.entities,
                    after = fact.entities,
                    updated = false,
                    error = e.message ?: e::class.simpleName
                )
            }
        }

        val summary = FactEntityRebuildSummary(
            scanned = items.size,
            updated = items.count { it.updated },
            unchanged = items.count { it.error == null && !it.updated },
            failed = items.count { it.error != null }
        )
        return FactEntityRebuildReport(summary, items)
    }

    private fun normalizeEntities(values: List<String>): List<String> =
        values.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

fun parseFactEntityRebuildOptions(args: Array<String>): FactEntityRebuildOptions {
    var dryRun = false
    var onlyEmpty = true
    var includeInvalid = false
    var botMark: String? = null
    var groupId: String? = null
    var limit: Int? = null

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--dry-run" -> dryRun = true
            "--all" -> onlyEmpty = false
            "--include-invalid" -> includeInvalid = true
            "--bot" -> botMark = args.valueAfter(arg, ++index)
            "--group" -> groupId = args.valueAfter(arg, ++index)
            "--limit" -> limit = args.valueAfter(arg, ++index).toInt()
            else -> throw IllegalArgumentException("Unknown option: $arg")
        }
        index++
    }

    return FactEntityRebuildOptions(
        dryRun = dryRun,
        onlyEmpty = onlyEmpty,
        includeInvalid = includeInvalid,
        botMark = botMark,
        groupId = groupId,
        limit = limit
    )
}

fun main(args: Array<String>) = runBlocking {
    val options = parseFactEntityRebuildOptions(args)
    val dataSource = ConnectionFactoryConfig().getDataSource()
    val database = Database.connect(
        datasource = dataSource,
        databaseConfig = DatabaseConfig {
            useNestedTransactions = true
        }
    )
    TransactionManager.defaultDatabase = database
    migrationIf(true, database)

    val runner = FactEntityRebuildRunner(MemoryRepository()) { fact ->
        extractEntityCandidates(fact.description)
    }
    val report = runner.run(options)

    println("Fact entity rebuild: ${report.summary}")
    report.items.forEach { item ->
        val status = when {
            item.error != null -> "FAILED ${item.error}"
            item.updated -> "UPDATED"
            else -> "UNCHANGED"
        }
        println("#${item.factId} $status ${item.keyword}: ${item.before} -> ${item.after}")
    }
}

private fun Array<String>.valueAfter(option: String, index: Int): String =
    getOrNull(index) ?: throw IllegalArgumentException("$option requires a value")

private fun extractEntityCandidates(text: String): List<String> {
    val words = Regex("""[\p{IsHan}A-Za-z0-9_/\-.]{2,}""")
        .findAll(text)
        .map { it.value.trim() }
        .filterNot { it in entityStopWords }
        .toList()
    return words.distinct().take(16)
}

private val entityStopWords = setOf(
    "user",
    "from",
    "to",
    "and",
    "the",
    "已经",
    "开始",
    "用户",
    "事实"
)
