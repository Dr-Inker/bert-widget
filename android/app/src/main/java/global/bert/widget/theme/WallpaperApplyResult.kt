package global.bert.widget.theme

import kotlinx.coroutines.CancellationException

internal data class WallpaperApplyResult(val homeApplied: Boolean, val lockApplied: Boolean) {
    val message: String get() = when {
        homeApplied && lockApplied -> "Home and Lock wallpapers applied."
        homeApplied -> "Home updated. Lock screen couldn’t be changed. Try Lock screen only."
        lockApplied -> "Lock screen updated. Home couldn’t be changed. Try Home only."
        else -> "Neither wallpaper could be applied. Your launcher may restrict wallpaper changes."
    }
}

/** The two Android operations are independent; always report what actually succeeded. */
internal fun applyWallpaperPair(home: () -> Int, lock: () -> Int): WallpaperApplyResult {
    fun attempt(operation: () -> Int): Boolean = try {
        operation() > 0 // WallpaperManager returns zero when the change fails.
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
    return WallpaperApplyResult(attempt(home), attempt(lock))
}
