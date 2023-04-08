package org.notenoughupdates.priceserver.utils

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    val port: Int, // Port to start the webserver on
    val databaseIp: String, // Ip of the database
    val databaseName: String, // Name of the database
    val databaseUser: String, // Database user to log in with
    val databasePassword: String, // Password of the database user
    val fetchTime: Int, // How often (in minutes) to fetch new data
    val deleteTime: Int, // How often (in minutes) to delete old data
    val retentionTime: Int, // How long (in days) to keep data
    val clusterTime: String // The time (in UTC) when the database should be clustered to improve performance
)