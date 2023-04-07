package dev.dediamondpro.priceserver.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.dediamondpro.priceserver.config
import io.ktor.server.util.*
import io.ktor.util.date.*
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit

object DatabaseHandler {
    private val source = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://${config.databaseIp}/${config.databaseName}"
        driverClassName = "org.postgresql.Driver"
        username = config.databaseUser
        password = config.databasePassword
        minimumIdle = 3
        maximumPoolSize = 10
        isAutoCommit = false
        addDataSourceProperty("reWriteBatchedInserts", true)
    })
    val database: Database = Database.connect(source, databaseConfig = DatabaseConfig.invoke {
        //warnLongQueriesDuration = 250
    })

    fun initialize() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(ItemsTable)
        }
        val now = Clock.System.now()
        var date = LocalTime.parse(config.clusterTime)
            .atDate(now.toLocalDateTime(TimeZone.UTC).date)
            .toInstant(TimeZone.UTC)
        if (date < now) date = date.plus(1.days)
        Timer().scheduleAtFixedRate(date.toJavaInstant().toGMTDate().toJvmDate(), 1.days.inWholeMilliseconds) {
            println("Starting cluster on database.")
            val start = Clock.System.now()
            transaction { "CLUSTER items USING item_id_index".exec() }
            println("Finished cluster, took ${Clock.System.now().minus(start).toDouble(DurationUnit.SECONDS)}s.")
        }
    }
}