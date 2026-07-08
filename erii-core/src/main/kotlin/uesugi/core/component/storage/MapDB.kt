package uesugi.core.component.storage

import org.mapdb.DB
import org.mapdb.DBMaker
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

object MapDB {

    private val modules = ConcurrentHashMap<String, DB>()
    private val plugins = ConcurrentHashMap<String, DB>()

    fun module(name: String): DB = modules.getOrPut(name) {
        val dbFile = Paths.get("./store/cache/$name.cache")
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
        db
    }

    fun plugin(name: String): DB = plugins.getOrPut(name) {
        val dbFile = Paths.get("./store/cache/plugin/$name.cache")
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
        db
    }
}
