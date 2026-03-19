package uesugi.core.component

import org.mapdb.DBMaker
import java.nio.file.Files
import java.nio.file.Paths


object MapDB {

    val Cache by lazy {
        val dbFile = Paths.get("./store/cache/data.db")
        Files.createDirectories(dbFile.parent)
        val db = DBMaker
            .fileDB(dbFile.toFile())
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
