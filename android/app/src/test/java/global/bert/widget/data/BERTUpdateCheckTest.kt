package global.bert.widget.data

import android.app.Application
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Critique 2026-11-12, Shipping: someone who installed the APK from the website was never told a newer BERT exists.
 * The app reads the site's release.json at most once a day and says so quietly; a Play install never asks (Play
 * updates it, and its policy forbids self-update prompts), and nothing is ever downloaded from inside the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTUpdateCheckTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val hour = 3_600_000L
    private var now = 1_794_000_000_000L
    private val url = "https://site.test/widget/release.json"

    /** The website, answering [body]; counts the requests, and fails like a phone with no signal when [offline]. */
    private class Site(var body: String) : BERTHttp {
        val asked = mutableListOf<String>()
        var maxBytes = 0
        var offline = false
        override fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String {
            asked += url
            this.maxBytes = maxBytes
            if (offline) throw IOException("offline")
            return body
        }
    }

    private fun release(version: String, code: Int) =
        """{"version":"$version","versionCode":$code,"minimumAndroid":"8.0","download":"https://berthalla.io/widget/download/bert-widget-$version.apk"}"""

    private fun installedBy(installer: String?) = shadowOf(context.packageManager).setInstallSourceInfo(context.packageName, installer, installer)

    private fun check(site: Site, installed: Int = 34) = BERTUpdateCheck(context, site, url, installedCode = installed, clock = { now })

    @Test fun aHigherVersionCodeOnTheSiteIsNewsAndTheSameOrLowerIsNot() {
        installedBy("com.google.android.packageinstaller") // the APK from the website, opened in the browser
        val site = Site(release("0.14.3", 35))
        val newer = runBlocking { check(site).checkIfDue() }
        assertEquals(BERTRelease("0.14.3", 35), newer)
        assertEquals(listOf(url), site.asked)
        assertTrue("the request keeps a small size cap", site.maxBytes in 1..16 * 1024)
        assertEquals("the Home row shows it", BERTRelease("0.14.3", 35), check(site).homeNotice())
        // The version name is only a label: the build number decides. Same build, or the site still on an older one: nothing.
        for ((body, installed) in listOf(release("0.14.2", 34) to 34, release("0.14.9", 34) to 34, release("0.14.1", 33) to 34, release("0.14.3", 35) to 35)) {
            context.getSharedPreferences("bert_update", Context.MODE_PRIVATE).edit().clear().commit()
            val quiet = Site(body)
            assertNull("$body with $installed installed", runBlocking { check(quiet, installed).checkIfDue() })
            assertNull(check(quiet, installed).newer())
            assertEquals(1, quiet.asked.size)
        }
    }

    @Test fun aPlayInstallNeverAsksTheSiteAndNeverShowsANotice() {
        installedBy("com.android.vending")
        val site = Site(release("0.15.0", 40))
        assertNull(runBlocking { check(site).checkIfDue() })
        assertEquals("no request at all", emptyList<String>(), site.asked)
        assertNull(check(site).newer())
        assertNull(check(site).homeNotice())
        // A release saved before the app moved to Play (installed over the website APK) is not shown either.
        installedBy("com.google.android.packageinstaller")
        runBlocking { check(site).checkIfDue() }
        assertEquals(BERTRelease("0.15.0", 40), check(site).newer())
        installedBy("com.android.vending")
        assertNull(check(site).newer())
        assertNull(check(site).homeNotice())
    }

    @Test fun anInstallerThatCannotBeReadIsTreatedAsPlay() {
        // Robolectric has no install source for the package until a test sets one, as Android would throw for a
        // package it does not know: fail closed, with no request.
        val site = Site(release("0.15.0", 40))
        assertNull(runBlocking { check(site).checkIfDue() })
        assertEquals(emptyList<String>(), site.asked)
    }

    @Test fun theSiteIsAskedAtMostOnceADayEvenWhenItFails() {
        installedBy(null) // adb, or a file manager that does not say
        val site = Site(release("0.14.3", 35))
        site.offline = true
        assertNull(runBlocking { check(site).checkIfDue() })
        assertEquals("a failure is silent", 1, site.asked.size)
        now += 23 * hour
        site.offline = false
        assertNull(runBlocking { check(site).checkIfDue() })
        assertEquals("not again within the day", 1, site.asked.size)
        now += 2 * hour
        assertEquals(BERTRelease("0.14.3", 35), runBlocking { check(site).checkIfDue() })
        assertEquals(2, site.asked.size)
        // Within the day the saved answer stands, with no request.
        now += hour
        assertEquals(BERTRelease("0.14.3", 35), runBlocking { check(site).checkIfDue() })
        assertEquals(2, site.asked.size)
    }

    @Test fun hidingAVersionHidesItForGoodButNotTheNextOne() {
        installedBy("com.google.android.packageinstaller")
        val site = Site(release("0.14.3", 35))
        runBlocking { check(site).checkIfDue() }
        check(site).dismiss(BERTRelease("0.14.3", 35))
        assertNull("hidden on Home", check(site).homeNotice())
        assertEquals("About still says so", BERTRelease("0.14.3", 35), check(site).newer())
        now += 30 * hour
        runBlocking { check(site).checkIfDue() }
        assertEquals(2, site.asked.size)
        assertNull("still hidden a day later", check(site).homeNotice())
        site.body = release("0.14.4", 36)
        now += 30 * hour
        runBlocking { check(site).checkIfDue() }
        assertEquals("a later release shows again", BERTRelease("0.14.4", 36), check(site).homeNotice())
    }

    @Test fun aMalformedOrOddAnswerIsIgnoredAndKeepsWhatWasSaved() {
        installedBy("com.google.android.packageinstaller")
        val site = Site(release("0.14.3", 35))
        runBlocking { check(site).checkIfDue() }
        val broken = listOf(
            "", "not json", "[]", "{}", """{"version":"0.15.0"}""", """{"versionCode":40}""",
            """{"version":"0.15.0","versionCode":"40"}""", """{"version":"0.15.0","versionCode":40.5}""",
            """{"version":"0.15.0","versionCode":-1}""", """{"version":"","versionCode":40}""",
            """{"version":"0.15.0 — tap here","versionCode":40}""", """{"version":"${"9".repeat(40)}","versionCode":40}""",
            """{"version":40,"versionCode":40}""", """<html>Cloudflare</html>""",
        )
        for (body in broken) {
            assertNull(body, BERTRelease.parse(body))
            now += 25 * hour
            site.body = body
            assertEquals("$body leaves the saved answer", BERTRelease("0.14.3", 35), runBlocking { check(site).checkIfDue() })
        }
    }
}
