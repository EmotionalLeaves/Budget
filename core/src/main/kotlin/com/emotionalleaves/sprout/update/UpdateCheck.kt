package com.emotionalleaves.sprout.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URI

/** The newest release and its APK. Nothing here ever carries financial data. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkName: String,
    /** api.github.com asset URL: works for private repos with a key. */
    val apiUrl: String,
    /** github.com download link: works for public repos (and in a signed-in browser). */
    val browserUrl: String,
    val sizeBytes: Long,
    /** Lowercase hex SHA-256 from GitHub's asset digest, when present. */
    val sha256: String?,
    val pageUrl: String,
)

/** Pure update logic: parse GitHub's release JSON, compare versions, choose where to download from. */
object UpdateCheck {
    const val API_HOST = "api.github.com"

    fun parseRelease(json: String): ReleaseInfo? = runCatching {
        val o = Json.parseToJsonElement(json).jsonObject
        if (o.bool("draft") == true || o.bool("prerelease") == true) return null
        val tag = o.str("tag_name") ?: return null
        val asset = (o["assets"] as? JsonArray)?.map { it.jsonObject }
            ?.firstOrNull { it.str("name")?.endsWith(".apk", ignoreCase = true) == true } ?: return null
        ReleaseInfo(
            version = tag.removePrefix("v").trim(),
            notes = summarizeNotes(o.str("body").orEmpty()),
            apkName = asset.str("name")!!,
            apiUrl = asset.str("url") ?: return null,
            browserUrl = asset.str("browser_download_url") ?: return null,
            sizeBytes = (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0,
            sha256 = asset.str("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
                ?.takeIf { Regex("[0-9a-f]{64}").matches(it) },
            pageUrl = o.str("html_url").orEmpty(),
        )
    }.getOrNull()

    /** "1.10.0" > "1.9.3". Non-numeric parts count as 0; a missing part counts as 0. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    /** With a key, the API asset URL (the only one that works for a private repo); without, the public link. */
    fun downloadUrl(release: ReleaseInfo, hasKey: Boolean): String = if (hasKey) release.apiUrl else release.browserUrl

    /** The key may only ever be sent to api.github.com over HTTPS. */
    fun mayCarryKey(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.host.equals(API_HOST, ignoreCase = true)
    }.getOrDefault(false)

    /** The first few bullet lines of a release's notes, for a one-glance banner. Markdown markers removed. */
    fun summarizeNotes(body: String, maxLines: Int = 3): String =
        body.lines().map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .take(maxLines)
            .joinToString("\n") { "• " + it.drop(2).replace("**", "").replace("`", "").take(160) }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
