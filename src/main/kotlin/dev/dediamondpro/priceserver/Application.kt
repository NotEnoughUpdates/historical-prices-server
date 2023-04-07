package dev.dediamondpro.priceserver

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import dev.dediamondpro.priceserver.database.DatabaseHandler
import dev.dediamondpro.priceserver.items.ItemFetcher
import dev.dediamondpro.priceserver.items.ItemResponse
import dev.dediamondpro.priceserver.utils.ConfigData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

fun main() {
    DatabaseHandler.initialize()
    ItemFetcher.initialize()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

@OptIn(ExperimentalSerializationApi::class)
val config: ConfigData by lazy {
    File("config.json").inputStream().use {
        ItemFetcher.json.decodeFromStream(it)
    }
}

private val dataCache: LoadingCache<String, Optional<ItemResponse>> = Caffeine.newBuilder()
    .expireAfterWrite(2.minutes.toJavaDuration())
    .maximumSize(250)
    .build { Optional.ofNullable(ItemFetcher.getItem(it)) }

fun Application.module() {
    install(ContentNegotiation) { json(ItemFetcher.json) }
    install(Compression) {
        gzip()
    }
    routing {
        get("/") {
            val item = call.request.queryParameters["item"]
            println("Request for $item")
            if (item == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val data = dataCache.get(item)
            if (data.isEmpty) {
                call.respond(HttpStatusCode.NotFound, "Could not find the item $item")
                return@get
            }
            call.respond(data.get())
        }
    }
}
