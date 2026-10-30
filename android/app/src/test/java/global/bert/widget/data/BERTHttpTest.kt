package global.bert.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.concurrent.thread

/**
 * The default GET tells a timeout while connecting (usually the person's connection) from one after the server took the
 * connection but did not answer (a busy server), so Market can name the right cause. No network: a fake connection, and
 * one server on this machine's loopback that accepts and never answers.
 */
class BERTHttpTest {
    /** A connection that times out where it is told to: while connecting, or once connected, waiting for the answer. */
    private class TimingOut(private val whileConnecting: Boolean) : HttpURLConnection(URL("https://gecko.test/api/v2/pools")) {
        var disconnected = false
        val timeout = SocketTimeoutException(if (whileConnecting) "connect timed out" else "Read timed out")

        override fun connect() {
            if (whileConnecting) throw timeout
            connected = true
        }

        override fun getResponseCode(): Int {
            if (!connected) connect()
            throw timeout
        }

        override fun getInputStream(): InputStream {
            if (!connected) connect()
            throw timeout
        }

        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
    }

    @Test fun aTimeoutWhileConnectingStaysAPlainSocketTimeout() {
        val connection = TimingOut(whileConnecting = true)
        val problem = assertThrows(SocketTimeoutException::class.java) { BERTHttp.fetch(connection, 1_000, 30_000) }
        assertFalse("a connect timeout is not the server being slow", problem is BERTHttpReadTimeoutException)
        assertTrue(connection.disconnected)
    }

    @Test fun aTimeoutOnceConnectedIsAReadTimeout() {
        val connection = TimingOut(whileConnecting = false)
        val problem = assertThrows(SocketTimeoutException::class.java) { BERTHttp.fetch(connection, 1_000, 30_000) }
        assertTrue("was $problem", problem is BERTHttpReadTimeoutException)
        assertSame(connection.timeout, problem.cause)
        assertTrue(connection.disconnected)
    }

    /** The real connection, against a server that takes it and says nothing: the timeout comes after connecting. */
    @Test fun aServerThatTakesTheConnectionButNeverAnswersIsAReadTimeout() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val silent = thread { runCatching { server.accept().use { Thread.sleep(5_000) } } }
            try {
                val problem = assertThrows(SocketTimeoutException::class.java) {
                    BERTHttp.DEFAULT.get("http://127.0.0.1:${server.localPort}/ohlcv", 1_000, 300)
                }
                assertTrue("was $problem", problem is BERTHttpReadTimeoutException)
                assertEquals("No answer in time from 127.0.0.1", problem.message)
            } finally {
                silent.interrupt()
            }
        }
    }
}
