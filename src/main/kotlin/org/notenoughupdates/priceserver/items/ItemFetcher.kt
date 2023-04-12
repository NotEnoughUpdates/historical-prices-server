package org.notenoughupdates.priceserver.items

import org.notenoughupdates.priceserver.config
import org.notenoughupdates.priceserver.database.ItemsTable
import org.notenoughupdates.priceserver.utils.NetworkUtils
import org.notenoughupdates.priceserver.utils.roundToDecimals
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

object ItemFetcher {
    private val BAZAAR_ENCHANTMENT_PATTERN = Regex("ENCHANTMENT_(\\D*)_(\\d+)")

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        prettyPrint = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun initialize() {
        val timer = Timer()
        timer.scheduleAtFixedRate(0, config.fetchTime.minutes.inWholeMilliseconds) {
            try {
                NetworkUtils.getGzipInputStream("https://moulberry.codes/lowestbin.json.gz").use { stream ->
                    val now = Clock.System.now()
                    val response: LowestBinResponse = json.decodeFromStream(stream)
                    transaction {
                        ItemsTable.batchInsert(response.items) {
                            this[ItemsTable.itemId] = it.first
                            this[ItemsTable.time] = now
                            this[ItemsTable.buyPrice] = it.second.roundToDecimals(1)
                        }
                    }
                    println("Added ${response.items.size} items from the auction house.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                NetworkUtils.getInputStream("https://api.hypixel.net/skyblock/bazaar").use { stream ->
                    val now = Clock.System.now()
                    val items = json.decodeFromStream<JsonObject>(stream)["products"]?.jsonObject?.values?.map {
                        json.decodeFromJsonElement<QuickStatus>(it.jsonObject["quick_status"]!!)
                    }?.filter { it.hasData() } ?: return@use
                    transaction {
                        ItemsTable.batchInsert(items) {
                            this[ItemsTable.itemId] = transformHypixelBazaarToNEUItemId(it.productId)
                            this[ItemsTable.time] = now
                            this[ItemsTable.buyPrice] = it.buyPrice.roundToDecimals(1)
                            this[ItemsTable.sellPrice] = it.sellPrice.roundToDecimals(1)
                        }
                    }
                    println("Added ${items.size} items from the bazaar.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        timer.scheduleAtFixedRate(10000, config.deleteTime.minutes.inWholeMilliseconds) {
            val amountDeleted = transaction {
                ItemsTable.deleteWhere { time greaterEq Clock.System.now().plus(config.retentionTime.days) }
            }
            println("Deleted $amountDeleted rows of old data.")
        }
    }

    fun getItem(item: String): ItemResponse? {
        return transaction {
            val query = ItemsTable.slice(ItemsTable.time, ItemsTable.buyPrice, ItemsTable.sellPrice)
                .select { ItemsTable.itemId eq item }.toList()
            if (query.isEmpty()) return@transaction null
            val response = ItemResponse(mutableListOf())
            for (row in query) {
                response.data.add(
                    row[ItemsTable.time] to ItemData(
                        row[ItemsTable.buyPrice],
                        row[ItemsTable.sellPrice]
                    )
                )
            }
            response
        }
    }

    private fun transformHypixelBazaarToNEUItemId(itemId: String): String {
        val match = BAZAAR_ENCHANTMENT_PATTERN.matchEntire(itemId) ?: return itemId.replace(":", "-")
        return match.groupValues[1] + ";" + match.groupValues[2]
    }
}

@Serializable(with = ItemResponseSerializer::class)
data class ItemResponse(val data: MutableList<Pair<Instant, ItemData>>)

internal object ItemResponseSerializer : KSerializer<ItemResponse> {
    private val stringToJsonElementSerializer = MapSerializer(String.serializer(), JsonElement.serializer())

    override val descriptor: SerialDescriptor = stringToJsonElementSerializer.descriptor

    override fun deserialize(decoder: Decoder): ItemResponse {
        error("Should never be used")
    }

    override fun serialize(encoder: Encoder, value: ItemResponse) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        val map: MutableMap<String, JsonElement> = mutableMapOf()

        for (element in value.data) {
            map[element.first.toString()] = json.encodeToJsonElement(element.second)
        }

        stringToJsonElementSerializer.serialize(encoder, map)
    }
}

@Serializable
data class ItemData(val b: Double, val s: Double? = null)

@Serializable(with = LowestBinResponseSerializer::class)
data class LowestBinResponse(val items: List<Pair<String, Double>>)

internal object LowestBinResponseSerializer : KSerializer<LowestBinResponse> {
    private val stringToJsonElementSerializer = MapSerializer(String.serializer(), JsonElement.serializer())

    override val descriptor: SerialDescriptor = stringToJsonElementSerializer.descriptor

    override fun deserialize(decoder: Decoder): LowestBinResponse {
        require(decoder is JsonDecoder)
        val json = decoder.json
        val filtersMap = stringToJsonElementSerializer.deserialize(decoder)
        val response = mutableListOf<Pair<String, Double>>()
        for (element in filtersMap) {
            response.add(element.key to json.decodeFromJsonElement(Double.serializer(), element.value))
        }
        return LowestBinResponse(response)
    }

    override fun serialize(encoder: Encoder, value: LowestBinResponse) {
        error("Should never be used")
    }
}