package uesugi.cli

import io.ktor.server.application.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uesugi.common.toolkit.logger
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path

private val LOG = logger("cli")

@Serializable
data class ServerConfig(
    val type: String,
    val port: Int,
    val username: String,
    val password: String
)

class IpcRingBuffer(filePath: Path) {
    companion object {
        private const val SIZE = 64 * 1024L // 64KB
        private const val DEFAULT_FILENAME = ".conf/erii.sock"
    }

    private val file: RandomAccessFile
    private val buffer: MappedByteBuffer
    private val fullPath: Path = filePath.resolve(DEFAULT_FILENAME)

    init {
        LOG.info("Opening mmap file: {}", fullPath)
        fullPath.parent?.toFile()?.mkdirs()
        file = RandomAccessFile(fullPath.toFile(), "rw")
        if (file.length() < SIZE) {
            file.setLength(SIZE)
            LOG.info("Created new mmap file with size {}", SIZE)
        }
        buffer = file.channel.map(FileChannel.MapMode.READ_WRITE, 0, SIZE)
        LOG.info("Mmap initialized successfully")
    }

    fun writeConfig(port: Int, username: String, password: String) {
        val config = ServerConfig(
            type = "config",
            port = port,
            username = username,
            password = password
        )
        val json = Json.encodeToString(config)
        val bytes = json.toByteArray(Charsets.UTF_8)

        // 写入长度 (4 bytes)
        buffer.putInt(0, bytes.size)
        // 写入JSON数据
        buffer.position(4)
        buffer.put(bytes)
        // 刷盘确保写入
        buffer.force()

        LOG.info("Config written successfully ({} bytes)", bytes.size)
    }

    fun close() {
        LOG.info("Closing mmap file")
        file.close()
    }
}

object IpcWriter {
    private val dirPath: Path = Path.of(System.getenv("ERII_IPC_PATH") ?: ".")
    private var ringBuffer: IpcRingBuffer? = null

    fun init() {
        if (ringBuffer == null) {
            LOG.info("IpcWriter initializing with dir: {}", dirPath)
            ringBuffer = IpcRingBuffer(dirPath)
        }
    }

    fun writeConfig(port: Int, username: String, password: String) {
        if (ringBuffer == null) {
            init()
        }
        LOG.info("Writing config - port={}, username={}", port, username)
        ringBuffer?.writeConfig(port, username, password)
    }

    fun close() {
        ringBuffer?.close()
        ringBuffer = null
    }
}

fun Application.configureIpc() {
    val port = environment.config.property("ktor.deployment.port").getString().toInt()
    val username = environment.config.property("security.username").getString()
    val password = environment.config.property("security.password").getString()
    IpcWriter.init()
    IpcWriter.writeConfig(port, username, password)
}