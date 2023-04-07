package dev.dediamondpro.priceserver.database

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ItemsTable : LongIdTable("items") {
    val itemId = text("item_id").index("item_id_index")
    val time = timestamp("time")
    val buyPrice = double("buy_price")
    val sellPrice = double("sell_price").nullable()
}