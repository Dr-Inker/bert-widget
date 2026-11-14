package global.bert.widget.data

import android.content.Context
import android.os.Build
import global.bert.widget.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A BERT release on the website: its name (a label only) and its build number, which decides what is newer. */
data class BERTRelease(val version: String, val versionCode: Int) {
    companion object {
        /** "0.14.3": two to four short numbers, so nothing but a version is ever shown from the file. */
        private val VERSION = Regex("^\\d{1,3}(\\.\\d{1,3}){1,3}$")

        /** The website's release.json (the same file the download page reads); null for anything malformed. */
        fun parse(raw: String): BERTRelease? = runCatching {
            val json = JSONObject(raw)
            val version = json.get("version") as? String ?: return null
            val code = json.get("versionCode") as? Int ?: return null
            BERTRelease(version, code).takeIf { VERSION.matches(version) && code > 0 }
        }.getOrNull()
    }
}

/**
 * Critique 2026-11-12 (Shipping): an APK installed from the website is never told a newer BERT exists. While the app is
 * open, at most once a day, this reads the website's release.json and keeps the answer, so Home can say "BERT 0.14.3 is
 * out" and About can mention it. It never runs for an install from Google Play, which updates the app itself and whose
 * policy forbids self-update prompts; then no request is made at all. Nothing is downloaded or installed here: the
 * notice only opens the website in the browser. A failure is silent and counts as the day's check.
 */
class BERTUpdateCheck(
    private val context: Context,
    private val http: BERTHttp = BERTActivityRepository.NO_REDIRECTS,
    private val url: String = BuildConfig.BERT_RELEASE_URL,
    private val installedCode: Int = BuildConfig.VERSION_CODE,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences("bert_update", Context.MODE_PRIVATE)

    /** True for a Play install, and (failing closed) when the installer cannot be read. */
    private val fromPlay: Boolean by lazy { runCatching { installedFromPlay(context) }.getOrDefault(true) }

    /** Asks the website if the last check was a day or more ago; returns [newer] either way. */
    suspend fun checkIfDue(): BERTRelease? = withContext(Dispatchers.IO) {
        if (fromPlay || url.isBlank()) return@withContext null
        val last = preferences.getLong(CHECKED_AT, 0L)
        val now = clock()
        // A clock set back makes the gap negative: that is due, rather than never again.
        if (last != 0L && now - last in 0 until DAY) return@withContext newer()
        preferences.edit().putLong(CHECKED_AT, now).commit()
        try {
            val release = BERTRelease.parse(http.get(url, MAX_BYTES, 8_000))
            ensureActive()
            if (release != null) preferences.edit().putString(VERSION, release.version).putInt(CODE, release.versionCode).apply()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Offline, a refused redirect, a page that is not the file: say nothing, and try again tomorrow.
        }
        newer()
    }

    /** The saved release when it is newer than this install (never on a Play install); for About. */
    fun newer(): BERTRelease? {
        if (fromPlay) return null
        val version = preferences.getString(VERSION, null) ?: return null
        val code = preferences.getInt(CODE, 0)
        return BERTRelease(version, code).takeIf { code > installedCode }
    }

    /** [newer], unless the person has hidden Home's row for that version or a later one. */
    fun homeNotice(): BERTRelease? = newer()?.takeIf { it.versionCode > preferences.getInt(HIDDEN, 0) }

    /** Hides Home's row for [release] for good; a later release shows again. */
    fun dismiss(release: BERTRelease) {
        preferences.edit().putInt(HIDDEN, maxOf(release.versionCode, preferences.getInt(HIDDEN, 0))).apply()
    }

    companion object {
        private const val DAY = 24 * 60 * 60_000L
        /** release.json is about 1 KB. */
        private const val MAX_BYTES = 8 * 1024
        private const val CHECKED_AT = "checked_at"
        private const val VERSION = "version"
        private const val CODE = "version_code"
        private const val HIDDEN = "hidden_code"
        internal const val PLAY_STORE = "com.android.vending"

        /** Whether Google Play installed (or now owns updates of) this app. Throws if Android cannot say. */
        internal fun installedFromPlay(context: Context): Boolean {
            val pm = context.packageManager
            val installers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(context.packageName)
                buildList {
                    add(info.installingPackageName); add(info.initiatingPackageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(info.updateOwnerPackageName)
                }
            } else {
                @Suppress("DEPRECATION")
                listOf(pm.getInstallerPackageName(context.packageName))
            }
            return PLAY_STORE in installers
        }
    }
}
