package com.emotionalleaves.sprout.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.emotionalleaves.sprout.update.ReleaseInfo
import com.emotionalleaves.sprout.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private val Context.updateStore: DataStore<Preferences> by preferencesDataStore(name = "updates")

/** Where the in-app updater is. One state for the banner and the Settings row. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val percent: Int) : UpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val file: File) : UpdateState
    /** Couldn't download in the app (no key, or the key stopped working): open the Releases page instead. */
    data class OpenReleasesPage(val url: String, val release: ReleaseInfo?) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update from GitHub Releases. Only release metadata and the APK are fetched; no financial data is ever
 * sent. The read-only key (if built in) goes only to api.github.com. Downloads are checked against GitHub's
 * SHA-256 digest, and Android itself refuses an APK signed with a different key.
 */
class UpdateRepository(
    private val context: Context,
    private val currentVersion: String,
    private val repo: String,
    private val key: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val apiBase: String = "https://api.github.com",
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val releasesPage: String get() = "https://github.com/$repo/releases/latest"
    val autoCheck = context.updateStore.data.map { it[AUTO] ?: true }
    val dismissedVersion = context.updateStore.data.map { it[DISMISSED] }

    suspend fun setAutoCheck(on: Boolean) { context.updateStore.edit { it[AUTO] = on } }
    suspend fun dismiss(version: String) { context.updateStore.edit { it[DISMISSED] = version } }

    /** On app start: at most every 6 hours, and only if automatic checks are on. Quiet on any failure. */
    suspend fun checkIfDue(nowSeconds: Long) {
        val prefs = context.updateStore.data.first()
        if (prefs[AUTO] == false) return
        if (nowSeconds - (prefs[LAST] ?: 0L) < CHECK_INTERVAL_SECONDS) return
        check(quiet = true)
        context.updateStore.edit { it[LAST] = nowSeconds }
    }

    suspend fun check(quiet: Boolean = false) = withContext(Dispatchers.IO) {
        if (_state.value is UpdateState.Downloading) return@withContext
        _state.value = UpdateState.Checking
        val result = runCatching {
            val req = Request.Builder().url("$apiBase/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .apply { if (key.isNotEmpty()) header("Authorization", "Bearer $key") }
                .build()
            http.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> UpdateCheck.parseRelease(r.body?.string().orEmpty())
                    r.code == 401 || r.code == 403 || r.code == 404 -> return@runCatching UpdateState.OpenReleasesPage(releasesPage, null)
                    else -> error("HTTP ${r.code}")
                }
            }
        }
        _state.value = result.fold(
            onSuccess = { v ->
                when (v) {
                    is UpdateState -> if (quiet) UpdateState.Idle else v
                    is ReleaseInfo -> if (UpdateCheck.isNewer(v.version, currentVersion)) UpdateState.Available(v) else UpdateState.UpToDate
                    else -> UpdateState.UpToDate
                }
            },
            onFailure = { if (quiet) UpdateState.Idle else UpdateState.Failed("Couldn't check for updates. Try again later.") },
        )
    }

    /** Downloads the APK into the app's cache, checks its SHA-256, then hands it to Android's installer. */
    suspend fun download(release: ReleaseInfo) = withContext(Dispatchers.IO) {
        if (key.isEmpty()) {
            _state.value = UpdateState.OpenReleasesPage(release.pageUrl.ifEmpty { releasesPage }, release)
            return@withContext
        }
        _state.value = UpdateState.Downloading(release, 0)
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "sprout-update.apk")
        val ok = runCatching {
            var url = UpdateCheck.downloadUrl(release, hasKey = true)
            var hops = 0
            while (true) {
                val b = Request.Builder().url(url)
                if (UpdateCheck.mayCarryKey(url)) {
                    b.header("Authorization", "Bearer $key").header("Accept", "application/octet-stream")
                }
                val resp = http.newCall(b.build()).execute()
                if (resp.isRedirect) {
                    val next = resp.header("Location") ?: error("redirect without location")
                    resp.close()
                    if (++hops > 5) error("too many redirects")
                    url = next
                    continue
                }
                if (resp.code == 401 || resp.code == 404) {
                    resp.close()
                    _state.value = UpdateState.OpenReleasesPage(release.pageUrl.ifEmpty { releasesPage }, release)
                    return@withContext
                }
                if (!resp.isSuccessful) { resp.close(); error("HTTP ${resp.code}") }
                val digest = MessageDigest.getInstance("SHA-256")
                val total = resp.body?.contentLength()?.takeIf { it > 0 } ?: release.sizeBytes
                resp.body!!.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                            if (pct != last) { last = pct; _state.value = UpdateState.Downloading(release, pct) }
                        }
                    }
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && hex != release.sha256) { out.delete(); error("checksum mismatch") }
                break
            }
            true
        }.getOrElse { false }
        _state.value = if (ok) UpdateState.ReadyToInstall(release, out) else UpdateState.Failed("The download didn't finish. Try again.")
    }

    /** True when Android lets Sprout open the installer; otherwise send the user to the one-time setting. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun reset() { _state.value = UpdateState.Idle }

    companion object {
        const val CHECK_INTERVAL_SECONDS = 6 * 3600L
        private val AUTO = booleanPreferencesKey("auto_check")
        private val LAST = longPreferencesKey("last_check")
        private val DISMISSED = stringPreferencesKey("dismissed_version")
    }
}
