package dev.echonine.kite.util

import com.google.gson.Gson
import com.google.gson.JsonArray
import dev.echonine.kite.Kite
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun HttpClient.get(uri: String): HttpResponse<String> {
    return send(HttpRequest.newBuilder().GET().uri(URI.create(uri)).build(), HttpResponse.BodyHandlers.ofString())
}

fun checkForUpdates() {
    val currentPluginVersion = Kite.INSTANCE!!.pluginMeta.version
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    // Wrapping with a try-catch block to silently ignore any exceptions.
    try {
        // Returning in case current plugin version does not exist in the Modrinth version history.
        if (client.get("https://api.modrinth.com/v2/project/1KWI0TqA/version/$currentPluginVersion").statusCode() != 200)
            return
        // Requesting version history from Modrinth API.
        val response = client.get("https://api.modrinth.com/v2/project/1KWI0TqA/version")
        val latestVersion = Gson().fromJson(response.body(), JsonArray::class.java).asJsonArray[0].asJsonObject
        val latestVersionNumber = latestVersion["version_number"].asString
        if (currentPluginVersion != latestVersionNumber) {
            Kite.INSTANCE!!.logger.info("A new version of Kite is available: $latestVersionNumber (${latestVersion["version_type"].asString}), you're on: $currentPluginVersion")
            Kite.INSTANCE!!.logger.info("https://modrinth.com/plugin/1KWI0TqA/version/${latestVersion["id"].asString}")
        }
    } catch (_: Throwable) {
        // IGNORE
    } finally {
        client.close()
    }

}