package dev.dediamondpro.priceserver.utils

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier
import kotlin.math.pow
import kotlin.math.round

fun Double.roundToDecimals(decimals: Int): Double {
    val multiplier = 10.0.pow(decimals)
    return round(this * multiplier) / multiplier
}

/*fun <U> CompletableFuture.supplyAsync(executor: Executor, supplier: Supplier<U>): CompletableFuture<U> {

}*/