package uesugi.core.component.storage

import org.mapdb.DB
import org.mapdb.DBMaker
import uesugi.config.StorePathConfig
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

object MapDB {

    private val modules = ConcurrentHashMap<String, DB>()
    private val plugins = ConcurrentHashMap<String, DB>()

    fun module(name: String): DB = modules.compute(name) { _, existing ->
        if (existing != null && !existing.isClosed()) return@compute existing
        val dbFile = StorePathConfig.resolve("cache", "$name.cache")
        openDb(dbFile)
    }!!

    fun plugin(name: String): DB = plugins.compute(name) { _, existing ->
        if (existing != null && !existing.isClosed()) return@compute existing
        val dbFile = StorePathConfig.resolve("cache", "plugin", "$name.cache")
        openDb(dbFile)
    }!!

    private fun openDb(dbFile: java.nio.file.Path): DB {
        Files.createDirectories(dbFile.parent)
        val db = DBMaker
            .fileDB(dbFile.toFile())
            .checksumHeaderBypass()
            .make()
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                db.close()
            } catch (_: Exception) {
            }
        })
        return db
    }
}
