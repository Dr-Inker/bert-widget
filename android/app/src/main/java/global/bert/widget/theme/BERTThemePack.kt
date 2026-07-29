package global.bert.widget.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import global.bert.widget.R

enum class BERTThemePack(
    val id: String,
    val displayName: String,
    val subtitle: String,
    @DrawableRes val homeWallpaperRes: Int,
    @DrawableRes val lockWallpaperRes: Int,
    @DrawableRes val previewRes: Int,
    val backgroundArgb: Long,
    val panelArgb: Long,
    val textArgb: Long,
    val mutedArgb: Long,
    val accentArgb: Long,
) {
    MAYOR_PURPLE(
        id = "mayor-purple",
        displayName = "Mayor Purple",
        subtitle = "BERT.GLOBAL · BIG ENERGY",
        homeWallpaperRes = R.drawable.wallpaper_mayor_purple_home,
        lockWallpaperRes = R.drawable.wallpaper_mayor_purple_lock,
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
        homeWallpaperRes = R.drawable.wallpaper_woofhub_night_home,
        lockWallpaperRes = R.drawable.wallpaper_woofhub_night_lock,
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
        homeWallpaperRes = R.drawable.wallpaper_berthalla_nights_home,
        lockWallpaperRes = R.drawable.wallpaper_berthalla_nights_lock,
        previewRes = R.drawable.preview_berthalla_nights,
        backgroundArgb = 0xFF061126,
        panelArgb = 0xFF15314F,
        textArgb = 0xFFFFFFFF,
        mutedArgb = 0xFFAFC1D8,
        accentArgb = 0xFFFF5435,
    );

    companion object {
        fun fromId(id: String?): BERTThemePack = entries.firstOrNull { it.id == id } ?: WOOFHUB_NIGHT
    }
}

class BERTThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences("bert_theme", Context.MODE_PRIVATE)

    fun load(): BERTThemePack = BERTThemePack.fromId(preferences.getString(KEY_THEME, null))

    fun save(theme: BERTThemePack) {
        preferences.edit().putString(KEY_THEME, theme.id).apply()
    }

    companion object { private const val KEY_THEME = "active_theme" }
}

object BERTWallpaperInstaller {
    const val HOME = WallpaperManager.FLAG_SYSTEM
    const val LOCK = WallpaperManager.FLAG_LOCK
    const val BOTH = HOME or LOCK

    fun apply(context: Context, theme: BERTThemePack, destination: Int) {
        val resource = if (destination == LOCK) theme.lockWallpaperRes else theme.homeWallpaperRes
        val bitmap = requireNotNull(BitmapFactory.decodeResource(context.resources, resource)) {
            "Unable to decode ${theme.displayName} wallpaper"
        }
        try {
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, destination)
        } finally {
            bitmap.recycle()
        }
    }

    fun applyPair(context: Context, theme: BERTThemePack) {
        apply(context, theme, HOME)
        apply(context, theme, LOCK)
    }
}
