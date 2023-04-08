package org.notenoughupdates.priceserver.items

import kotlinx.serialization.Serializable

@Serializable
data class QuickStatus(
    val productId: String,
    val sellPrice: Double,
    val sellVolume: Long,
    val sellMovingWeek: Long,
    val sellOrders: Long,
    val buyPrice: Double,
    val buyVolume: Long,
    val buyMovingWeek: Long,
    val buyOrders: Long
) {
    fun hasData(): Boolean {
        return sellPrice != 0.0 || sellVolume != 0L || sellMovingWeek != 0L || sellOrders != 0L
                || buyPrice != 0.0 || buyVolume != 0L || buyMovingWeek != 0L || buyOrders != 0L
    }
}
