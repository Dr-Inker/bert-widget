package global.bert.widget.theme

import android.app.WallpaperManager
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import global.bert.widget.R
import global.bert.widget.data.MyBertStore

enum class BERTThemePack(
    val id: String,
    val displayName: String,
    val subtitle: String,
    @RawRes val homeWallpaperRes: Int,
    @RawRes val lockWallpaperRes: Int,
    @DrawableRes val previewRes: Int,
    val backgroundArgb: Long,
    val panelArgb: Long,
    val textArgb: Long,
    val mutedArgb: Long,
    val accentArgb: Long,
    /** The Bert widget's mood pill, which sits over the picture: orange on dark ink unless a pack says otherwise. */
    val highlightArgb: Long = 0xFFFF9433,
    val onHighlightArgb: Long = 0xFF1F0E02,
) {
    /**
     * The BERT brand kit's product mode: near-black purple, the kit's violet glow and its gradient wordmark (the stacked
     * "Mayor of Solana" lockup on the Lock Screen). The widget default for new installs; the app's BERT Violet scheme.
     */
    BERT_VIOLET(
        id = "bert-violet",
        displayName = "BERT Violet",
        subtitle = "THE BRAND KIT",
        homeWallpaperRes = R.raw.wallpaper_bert_violet_home,
        lockWallpaperRes = R.raw.wallpaper_bert_violet_lock,
        previewRes = R.drawable.preview_bert_violet,
        backgroundArgb = 0xFF100B16,
        panelArgb = 0xFF1A1125,
        textArgb = 0xFFFFFFFF,
        mutedArgb = 0xFFCECECE,
        // The widget's thin accent stripe: the kit's violet is a border colour.
        accentArgb = 0xFF8531D5,
        // The kit's lilac on its ink (#150C1F): its "active" colour, never orange.
        highlightArgb = 0xFFEABFFF,
        onHighlightArgb = 0xFF150C1F,
    ),
    /** The app's original navy and orange (the Berthalla Navy scheme in BERTComponents.kt): the widget default before 0.11. */
    BERT_NAVY(
        id = "bert-navy",
        displayName = "BERT Navy",
        subtitle = "THE APP\u2019S OWN COLOURS",
        homeWallpaperRes = R.raw.wallpaper_bert_navy_home,
        lockWallpaperRes = R.raw.wallpaper_bert_navy_lock,
        previewRes = R.drawable.preview_bert_navy,
        backgroundArgb = 0xFF071A2F,
        panelArgb = 0xFF0D2942,
        textArgb = 0xFFFFF5DF,
        mutedArgb = 0xFF9CB0C5,
        accentArgb = 0xFFFF9433,
    ),
    MAYOR_PURPLE(
        id = "mayor-purple",
        displayName = "Mayor Purple",
        subtitle = "BERT.GLOBAL · BIG ENERGY",
        homeWallpaperRes = R.raw.wallpaper_mayor_purple_home,
        lockWallpaperRes = R.raw.wallpaper_mayor_purple_lock,
        previewRes = R.drawable.preview_mayor_purple,
        backgroundArgb = 0xFF4D0B8C,
        panelArgb = 0xFF6E23B7,
        textArgb = 0xFFFFFFFF,
        mutedArgb = 0xFFEABFFF,
        accentArgb = 0xFFF25836,
    ),
    WOOFHUB_NIGHT(
        id = "woofhub-night",
        displayName = "Woofhub Night",
        subtitle = "POWERED BY BERT",
        homeWallpaperRes = R.raw.wallpaper_woofhub_night_home,
        lockWallpaperRes = R.raw.wallpaper_woofhub_night_lock,
        previewRes = R.drawable.preview_woofhub_night,
        backgroundArgb = 0xFF0C0712,
        panelArgb = 0xFF2A143A,
        textArgb = 0xFFFFFFFF,
        mutedArgb = 0xFFCAB5D6,
        accentArgb = 0xFF8B37F7,
    ),
    BERTHALLA_NIGHTS(
        id = "berthalla-nights",
        displayName = "Berthalla Nights",
        subtitle = "THE BERT ECOSYSTEM",
        homeWallpaperRes = R.raw.wallpaper_berthalla_nights_home,
        lockWallpaperRes = R.raw.wallpaper_berthalla_nights_lock,
        previewRes = R.drawable.preview_berthalla_nights,
        backgroundArgb = 0xFF061126,
        panelArgb = 0xFF15314F,
        textArgb = 0xFFFFFFFF,
        mutedArgb = 0xFFAFC1D8,
        accentArgb = 0xFFFF5435,
    );

    val palette: BERTPalette get() = BERTPalette(backgroundArgb, panelArgb, textArgb, mutedArgb, accentArgb, highlightArgb, onHighlightArgb)

    companion object {
        /** Nobody has chosen yet (or a saved id is from a newer build): the brand kit's BERT Violet. */
        val DEFAULT = BERT_VIOLET

        fun fromId(id: String?): BERTThemePack = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** The five colours a widget is drawn with: a theme pack's, or the holder's own Bert's (see MyBertPalette). */
data class BERTPalette(
    val backgroundArgb: Long, val panelArgb: Long, val textArgb: Long, val mutedArgb: Long, val accentArgb: Long,
    val highlightArgb: Long = 0xFFFF9433, val onHighlightArgb: Long = 0xFF1F0E02,
)

class BERTThemeStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("bert_theme", Context.MODE_PRIVATE)

    fun load(): BERTThemePack {
        preferences.getString(KEY_THEME, null)?.let { return BERTThemePack.fromId(it) }
        // Never chosen: fresh installs get BERT Violet; upgraders keep Woofhub Night, the default they already had
        // (and the one matching any wallpaper they applied without choosing). Saved, so later updates can't flip it.
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val upgraded = info != null && info.lastUpdateTime > info.firstInstallTime
        return (if (upgraded) BERTThemePack.WOOFHUB_NIGHT else BERTThemePack.DEFAULT).also(::save)
    }

    fun save(theme: BERTThemePack) {
        // Choosing a pack is choosing it: "Match my Bert" steps aside.
        preferences.edit().putString(KEY_THEME, theme.id).putBoolean(KEY_MATCH_MY_BERT, false).apply()
    }

    /** "Match my Bert": widgets take their colours from the holder's chosen Bert. Only ever switched on by the person. */
    var matchMyBert: Boolean
        get() = preferences.getBoolean(KEY_MATCH_MY_BERT, false)
        set(value) { preferences.edit().putBoolean(KEY_MATCH_MY_BERT, value).apply() }

    /** The app's own colours (BERTScheme id): unset means the default, BERT Violet from the brand kit. */
    var appSchemeId: String?
        get() = preferences.getString(KEY_APP_SCHEME, null)
        set(value) { preferences.edit().putString(KEY_APP_SCHEME, value).apply() }

    /** The colours widgets use: the chosen Bert's while "Match my Bert" is on and a Bert is chosen, else the saved pack's. */
    fun loadPalette(): BERTPalette {
        if (matchMyBert) MyBertStore(context).chosen()?.let { return it.palette }
        return load().palette
    }

    companion object {
        private const val KEY_THEME = "active_theme"
        private const val KEY_MATCH_MY_BERT = "match_my_bert"
        private const val KEY_APP_SCHEME = "app_scheme"
    }
}

object BERTWallpaperInstaller {
    const val HOME = WallpaperManager.FLAG_SYSTEM
    const val LOCK = WallpaperManager.FLAG_LOCK
    const val BOTH = HOME or LOCK

    fun apply(context: Context, theme: BERTThemePack, destination: Int): Int {
        val resource = if (destination == LOCK) theme.lockWallpaperRes else theme.homeWallpaperRes
        // The packaged WebP is handed over as-is; decoding it here would hold ~18 MB of ARGB
        // and make WallpaperManager re-encode it.
        val wallpaperId = context.resources.openRawResource(resource).use { stream ->
            WallpaperManager.getInstance(context).setStream(stream, null, false, destination)
        }
        check(wallpaperId > 0) { "Android did not apply the wallpaper" }
        return wallpaperId
    }

    internal fun applyPair(context: Context, theme: BERTThemePack): WallpaperApplyResult =
        applyWallpaperPair(home = { apply(context, theme, HOME) }, lock = { apply(context, theme, LOCK) })
}
