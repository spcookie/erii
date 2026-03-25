package uesugi.config

import io.ktor.server.application.*
import okio.Path.Companion.toPath
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule
import uesugi.core.component.EmbeddedVectorStore
import uesugi.core.component.LocalObjectStorage
import uesugi.core.component.ObjectStorage
import uesugi.core.component.VectorStore
import uesugi.core.message.messageModule
import uesugi.core.state.emotion.EmotionJob
import uesugi.core.state.emotion.emotionModule
import uesugi.core.state.evolution.EvolutionJob
import uesugi.core.state.evolution.evolutionModule
import uesugi.core.state.flow.FlowJob
import uesugi.core.state.flow.flowModule
import uesugi.core.state.meme.MemeJob
import uesugi.core.state.meme.memeModule
import uesugi.core.state.memory.MemoryJob
import uesugi.core.state.memory.memoryModule
import uesugi.core.state.volition.VolitionJob
import uesugi.core.state.volition.volitionModule
import uesugi.toolkit.WebPageMarkdownScraper
import uesugi.toolkit.WebScreenshotTaker
import javax.sql.DataSource

fun Application.warmUp() {
    monitor.subscribe(ApplicationStarted) {
        it.get<EmotionJob>().apply { openTimingTriggerSignal() }
        it.get<MemoryJob>().apply { openTimingTriggerSignal() }
        it.get<VolitionJob>().apply { openTimingTriggerSignal() }
        it.get<EvolutionJob>().apply { openTimingTriggerSignal() }
        it.get<FlowJob>().apply { openTimingTriggerSignal() }
        it.get<MemeJob>().apply { openTimingTriggerSignal() }
    }
}

fun Application.configBaseModule() = koinModule {
    single<ObjectStorage> {
        LocalObjectStorage(
            baseDir = "./store/object".toPath()
        )
    }
    factory<VectorStore> {
        EmbeddedVectorStore(it.get(), it.get())
    }
    single<DataSource> {
        ConnectionFactoryConfig().getDataSource()
    }
    single(createdAtStart = true) {
        val database = Database.connect(
            get(),
            {},
            DatabaseConfig {
                useNestedTransactions = true
            }
        )
        TransactionManager.defaultDatabase = database
        database
    }
    single(createdAtStart = true) { JobRunrConfig(get()).run { start() } }

    single(named("port")) {
        environment.config
            .property("ktor.deployment.port")
            .getString()
            .toInt()
    }

}

val gatewayModule = module {
    val noProxyClient = HttpClientFactory().createClient()
    val proxyClient = HttpClientFactory().createProxyClient()
    single { noProxyClient }
    single(named(HttpClientFactory.Type.NO_PROXY)) { noProxyClient }
    single(named(HttpClientFactory.Type.PROXY)) { proxyClient }
}


val infrastructureModule = module {
    single { LLMFactory().promptExecutor() }
    single { WebPageMarkdownScraper() }
    single { WebScreenshotTaker() }
}

val appModule = module {
    includes(gatewayModule)
    includes(infrastructureModule)
    includes(messageModule)
    includes(emotionModule)
    includes(evolutionModule)
    includes(flowModule)
    includes(memeModule)
    includes(memoryModule)
    includes(volitionModule)
}