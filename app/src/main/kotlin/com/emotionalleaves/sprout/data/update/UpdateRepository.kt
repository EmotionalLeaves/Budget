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
    /** The APK download broke off (or didn't match its checksum): offer "Try again". */
    data class DownloadFailed(val release: ReleaseInfo) : UpdateState
    /** Android didn't finish installing (cancelled, or not enough space): offer "Install" again. */
    data class InstallFailed(val release: ReleaseInfo, val file: File) : UpdateState
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
    /** Built in at build time (UPDATE_TOKEN secret); empty when there isn't one. */
    private val builtInKey: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val apiBase: String = "https://api.github.com",
    /** A key you pasted in Settings → Updates (stored encrypted); used when nothing is built in. */
    private val storedKey: suspend () -> String = { "" },
    /** This build's version code, for comparing against test builds. 0 = unknown. */
    private val currentCode: Int = 0,
    /** Beta channel on: test builds (prereleases) count as updates too. */
    private val beta: suspend () -> Boolean = { false },
    private val saveStoredKey: suspend (String) -> Unit = {},
) {
    private suspend fun key(): String = builtInKey.ifEmpty { runCatching { storedKey() }.getOrDefault("") }
    private val _hasKey = MutableStateFlow(builtInKey.isNotEmpty())
    /** Whether in-app downloads can work (a built-in or saved key). */
    val hasKeyFlow: StateFlow<Boolean> = _hasKey.asStateFlow()
    val keyIsBuiltIn: Boolean get() = builtInKey.isNotEmpty()

    /** Saves a pasted read-only key, then checks it right away. Returns true if GitHub accepted it. */
    suspend fun saveKey(raw: String): Boolean {
        val k = raw.trim().filter { it.isLetterOrDigit() || it == '_' }
        if (k.length < 20) return false
        saveStoredKey(k)
        _hasKey.value = true
        check()
        val ok = _state.value !is UpdateState.OpenReleasesPage
        if (!ok) { saveStoredKey(""); _hasKey.value = false }
        return ok
    }

    suspend fun refreshHasKey() { _hasKey.value = key().isNotEmpty() }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val releasesPage: String get() = "https://github.com/$repo/releases/latest"
    val autoCheck = context.updateStore.data.map { it[AUTO] ?: true }
    val dismissedVersion = context.updateStore.data.map { it[DISMISSED] }
    /** Download new versions in the background on Wi-Fi (or any unmetered network), so they're ready to install. */
    val autoDownload = context.updateStore.data.map { it[AUTO_DOWNLOAD] ?: true }
    suspend fun setAutoDownload(on: Boolean) { context.updateStore.edit { it[AUTO_DOWNLOAD] = on } }
    /** Android 12+: install a background-downloaded update by itself (no taps once Sprout installed an update before). */
    val autoInstall = context.updateStore.data.map { it[AUTO_INSTALL] ?: true }
    suspend fun setAutoInstall(on: Boolean) { context.updateStore.edit { it[AUTO_INSTALL] = on } }
    val hasKey: Boolean get() = _hasKey.value

    suspend fun setAutoCheck(on: Boolean) { context.updateStore.edit { it[AUTO] = on } }

    /** Background check (daily worker): returns the newer version once, so it's announced only one time. */
    suspend fun checkForNotification(): String? {
        val prefs = context.updateStore.data.first()
        if (prefs[AUTO] == false) return null
        check(quiet = true)
        val v = (state.value as? UpdateState.Available)?.release?.version ?: return null
        if (prefs[NOTIFIED] == v || prefs[DISMISSED] == v) return null
        context.updateStore.edit { it[NOTIFIED] = v }
        return v
    }

    /**
     * On launch: the version you were on before, if Sprout was just updated (null on first run or no change).
     * Also deletes the downloaded APK, which isn't needed once installed.
     */
    suspend fun consumeJustUpdatedFrom(): String? {
        val prefs = context.updateStore.data.first()
        // Keep a background-downloaded update that's still newer than this version; delete anything else.
        val readyVersion = prefs[READY_VERSION]
        val file = File(context.cacheDir, "updates/sprout-update.apk")
        if (readyVersion != null && UpdateCheck.isNewer(readyVersion, currentVersion) && file.exists()) {
            if (_state.value is UpdateState.Idle) {
                _state.value = UpdateState.ReadyToInstall(
                    ReleaseInfo(readyVersion, prefs[READY_NOTES].orEmpty(), file.name, "", "", file.length(), null, releasesPage), file,
                )
            }
        } else {
            File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
            context.updateStore.edit { it.remove(READY_VERSION); it.remove(READY_NOTES) }
        }
        val last = prefs[LAST_SEEN]
        context.updateStore.edit { it[LAST_SEEN] = currentVersion }
        return last?.takeIf { it != currentVersion }
    }
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
        val key = key()
        _hasKey.value = key.isNotEmpty()
        val result = runCatching {
            val betaOn = runCatching { beta() }.getOrDefault(false)
            val req = Request.Builder().url(if (betaOn) "$apiBase/repos/$repo/releases?per_page=15" else "$apiBase/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .apply { if (key.isNotEmpty()) header("Authorization", "Bearer $key") }
                .build()
            http.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> r.body?.string().orEmpty().let { body ->
                        if (betaOn) UpdateCheck.parseReleases(body).firstOrNull() else UpdateCheck.parseRelease(body)
                    }
                    r.code == 401 || r.code == 403 || r.code == 404 -> return@runCatching UpdateState.OpenReleasesPage(releasesPage, null)
                    else -> error("HTTP ${r.code}")
                }
            }
        }
        _state.value = result.fold(
            onSuccess = { v ->
                when (v) {
                    is UpdateState -> if (quiet) UpdateState.Idle else v
                    is ReleaseInfo -> if (UpdateCheck.isNewerBuild(v, currentVersion, currentCode.takeIf { it > 0 })) UpdateState.Available(v) else UpdateState.UpToDate
                    else -> UpdateState.UpToDate
                }
            },
            onFailure = { if (quiet) UpdateState.Idle else UpdateState.Failed("Couldn't check for updates. Try again later.") },
        )
    }

    /** Downloads the APK into the app's cache, checks its SHA-256, then hands it to Android's installer. */
    suspend fun download(release: ReleaseInfo) = withContext(Dispatchers.IO) {
        val key = key()
        if (key.isEmpty()) {
            _state.value = UpdateState.OpenReleasesPage(release.pageUrl.ifEmpty { releasesPage }, release)
            return@withContext
        }
        _state.value = UpdateState.Downloading(release, 0)
        // Resume a download that broke off (same version only); anything else in the folder is stale.
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val part = File(dir, "sprout-${release.version}.part")
        dir.listFiles()?.filter { it.name != part.name }?.forEach { it.delete() }
        val out = File(dir, "sprout-update.apk")
        val ok = runCatching {
            var url = UpdateCheck.downloadUrl(release, hasKey = true)
            var hops = 0
            while (true) {
                val b = Request.Builder().url(url)
                val resumeFrom = part.length()
                if (resumeFrom > 0) b.header("Range", "bytes=$resumeFrom-")
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
                if (resp.code == 416) { resp.close(); part.delete(); continue } // our partial file is bad: start over
                if (!resp.isSuccessful) { resp.close(); error("HTTP ${resp.code}") }
                val resumed = resp.code == 206 && resumeFrom > 0
                val total = (resp.body?.contentLength()?.takeIf { it > 0 }?.let { if (resumed) it + resumeFrom else it }) ?: release.sizeBytes
                resp.body!!.byteStream().use { input ->
                    java.io.FileOutputStream(part, resumed).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = if (resumed) resumeFrom else 0L
                        var last = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                            if (pct != last) { last = pct; _state.value = UpdateState.Downloading(release, pct) }
                        }
                    }
                }
                // Check the whole file (including any resumed part) before it's offered for install.
                val digest = MessageDigest.getInstance("SHA-256")
                part.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && hex != release.sha256) { part.delete(); error("checksum mismatch") }
                out.delete()
                if (!part.renameTo(out)) { part.copyTo(out, overwrite = true); part.delete() }
                break
            }
            true
        }.getOrElse { false }
        if (ok) context.updateStore.edit { it[READY_VERSION] = release.version; it[READY_NOTES] = release.notes }
        _state.value = if (ok) UpdateState.ReadyToInstall(release, out) else UpdateState.DownloadFailed(release)
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

    /** Android refused or cancelled the install: offer it again (the file is still there). */
    fun installFailed() {
        val s = _state.value
        if (s is UpdateState.ReadyToInstall) _state.value = UpdateState.InstallFailed(s.release, s.file)
    }

    companion object {
        const val CHECK_INTERVAL_SECONDS = 6 * 3600L
        private val AUTO = booleanPreferencesKey("auto_check")
        private val LAST = longPreferencesKey("last_check")
        private val DISMISSED = stringPreferencesKey("dismissed_version")
        private val NOTIFIED = stringPreferencesKey("notified_version")
        private val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
        private val AUTO_INSTALL = booleanPreferencesKey("auto_install")
        private val READY_VERSION = stringPreferencesKey("ready_version")
        private val READY_NOTES = stringPreferencesKey("ready_notes")
        private val LAST_SEEN = stringPreferencesKey("last_seen_version")
    }
}
