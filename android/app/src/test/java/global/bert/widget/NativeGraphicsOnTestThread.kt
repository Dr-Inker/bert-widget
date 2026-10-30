package global.bert.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import org.junit.rules.ExternalResource
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Robolectric's native graphics look up java.nio's buffer classes the first time a graphics call needs them, and that
 * lookup can fail when it first happens on a background thread: the whole test JVM then aborts (SIGABRT, exit 134, with
 * "JniConstants: Class not found: java/nio/ShortBuffer" or FloatBuffer, logged from a thread that is not the test's). The
 * app does some of its picture work on Dispatchers.IO (My Bert's picture, Create's thumbnails, a chosen Bert's download),
 * so a class run alone in a fresh JVM could be the first to get there off the test thread, and it aborted about one run in
 * four. This rule does one small decode, draw and buffer copy on the test thread before each test, so those lookups are
 * always made here first and every later call, on any thread, finds them done. It is a test harness fix: on a phone the
 * platform has loaded all of this long before the app runs.
 *
 * Classes whose screens or repositories touch pictures off the main thread use it as a rule; it costs a few milliseconds.
 */
class NativeGraphicsOnTestThread : ExternalResource() {
    override fun before() {
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(png, 0, png.size)) { "native graphics are not available" }
        val target = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        Canvas(target).drawBitmap(decoded, 0f, 0f, null)
        target.copyPixelsToBuffer(ByteBuffer.allocate(target.byteCount))
    }
}
