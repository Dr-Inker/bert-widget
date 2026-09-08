package global.bert.widget.theme

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class WallpaperApplyResultTest {
    @Test fun bothSuccessesAreReported() {
        val changed = mutableListOf<String>()
        val result = applyWallpaperPair({ changed += "home"; 17 }, { changed += "lock"; 29 })
        assertEquals(listOf("home", "lock"), changed)
        assertTrue(result.homeApplied && result.lockApplied)
        assertEquals("Home and Lock wallpapers applied.", result.message)
    }

    @Test fun lockFailurePreservesAndReportsSuccessfulHomeChange() {
        var homeChanged = false
        val result = applyWallpaperPair({ homeChanged = true; 17 }, { throw SecurityException("Lock denied") })
        assertTrue(homeChanged)
        assertTrue(result.homeApplied)
        assertFalse(result.lockApplied)
        assertEquals("Home updated. Lock screen couldn’t be changed. Try Lock screen only.", result.message)
    }

    @Test fun failedHomeStillAllowsIndependentLockChange() {
        var lockChanged = false
        val result = applyWallpaperPair({ throw SecurityException("Home denied") }, { lockChanged = true; 29 })
        assertTrue(lockChanged)
        assertFalse(result.homeApplied)
        assertTrue(result.lockApplied)
        assertEquals("Lock screen updated. Home couldn’t be changed. Try Home only.", result.message)
    }

    @Test fun neitherSuccessIsNeverReportedAsApplied() {
        val result = applyWallpaperPair({ error("Home failed") }, { error("Lock failed") })
        assertFalse(result.homeApplied || result.lockApplied)
        assertEquals("Neither wallpaper could be applied. Your launcher may restrict wallpaper changes.", result.message)
    }

    @Test fun cancellationDoesNotContinueMutatingAnotherSurface() {
        var lockAttempted = false
        assertThrows(CancellationException::class.java) {
            applyWallpaperPair({ throw CancellationException("Leaving screen") }, { lockAttempted = true; 29 })
        }
        assertFalse(lockAttempted)
    }

    @Test fun zeroWallpaperIdIsFailureEvenWithoutAnException() {
        var lockAttempted = false
        val result = applyWallpaperPair({ 0 }, { lockAttempted = true; 29 })
        assertTrue(lockAttempted)
        assertFalse(result.homeApplied)
        assertTrue(result.lockApplied)
        val neither = applyWallpaperPair({ 0 }, { 0 })
        assertFalse(neither.homeApplied || neither.lockApplied)
    }
}
