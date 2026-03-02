package uesugi.toolkit

import okio.FileSystem
import okio.Path
import okio.Source
import okio.buffer

interface ObjectStorage {

    /** 写入（覆盖） */
    fun put(path: Path, source: Source)

    /** 读取 */
    fun get(path: Path): Source

    /** 是否存在 */
    fun exists(path: Path): Boolean

    /** 删除 */
    fun delete(path: Path)

    /** 列表（可选） */
    fun list(dir: Path): List<Path>
}


class LocalObjectStorage(
    private val baseDir: Path,
    private val fs: FileSystem = FileSystem.SYSTEM
) : ObjectStorage {

    init {
        fs.createDirectories(baseDir)
    }

    private fun resolve(path: Path): Path {
        require(!path.isAbsolute) { "path must be relative: $path" }
        return baseDir / path
    }

    override fun put(path: Path, source: Source) {
        val target = resolve(path)
        fs.createDirectories(target.parent!!)

        fs.sink(target).use { sink ->
            source.buffer().use { buffered ->
                buffered.readAll(sink)
            }
        }
    }

    override fun get(path: Path): Source {
        val target = resolve(path)
        check(fs.exists(target)) { "file not exists: $path" }
        return fs.source(target)
    }

    override fun exists(path: Path): Boolean =
        fs.exists(resolve(path))

    override fun delete(path: Path) {
        val target = resolve(path)
        if (fs.exists(target)) {
            fs.delete(target)
        }
    }

    override fun list(dir: Path): List<Path> {
        val target = resolve(dir)
        if (!fs.exists(target)) return emptyList()

        return fs.list(target)
            .map { it.relativeTo(baseDir) }
    }
}
