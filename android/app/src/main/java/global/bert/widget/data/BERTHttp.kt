package global.bert.widget.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Minimal GET seam so direct-source and fallback behaviour can be tested without a network. */
fun interface BERTHttp {
    fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String

    companion object {
        val DEFAULT = BERTHttp { url, maxBytes, readTimeoutMs -> fetch(URL(url).openConnection() as HttpURLConnection, maxBytes, readTimeoutMs) }

        /** [DEFAULT]'s request on a connection already opened, so a test can hand it one that fails where it chooses. */
        internal fun fetch(connection: HttpURLConnection, maxBytes: Int, readTimeoutMs: Int): String {
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = readTimeoutMs
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "BERT-Android")
                // Connecting on its own first, so a timeout here (a connection that couldn't be made in time) stays apart
                // from one after it (a server that took the connection and then didn't answer); see BERTHttpReadTimeoutException.
                connection.connect()
                try {
                    if (connection.responseCode !in 200..299) throw BERTHttpStatusException(connection.responseCode, connection.url.host)
                    val declaredLength = connection.contentLengthLong
                    require(declaredLength < 0 || declaredLength <= maxBytes) { "Response is too large" }
                    return connection.inputStream.use { readUtf8WithLimit(it, maxBytes) }
                } catch (timeout: SocketTimeoutException) {
                    throw BERTHttpReadTimeoutException(connection.url.host, timeout)
                }
            } finally {
                connection.disconnect()
            }
        }

        fun readUtf8WithLimit(input: InputStream, maxBytes: Int): String =
            readBytesWithLimit(input, maxBytes).toString(Charsets.UTF_8)

        /** Reads the whole stream, failing as soon as it passes [maxBytes] (a declared length can lie). */
        fun readBytesWithLimit(input: InputStream, maxBytes: Int): ByteArray {
            require(maxBytes > 0) { "Response limit must be positive" }
            val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1_024))
            val buffer = ByteArray(8 * 1_024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "Response is too large" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}

/**
 * The server answered, but not with a 2xx: a refusal (GeckoTerminal's 401 for history older than its free API serves) or
 * a rate limit (429) rather than a dropped connection, which arrives as an IOException. Still an IllegalStateException, as
 * the plain error() it replaces was, so existing catches are unchanged.
 */
class BERTHttpStatusException(val status: Int, host: String) : IllegalStateException("HTTP $status from $host")

/**
 * GeckoTerminal (or another source) took the connection but did not answer within the read timeout: the server is slow
 * or busy, not the person's connection. A connect timeout, which on Android is a SocketTimeoutException too, stays plain,
 * because that one usually is the connection (weak signal, or a captive portal that never lets the request through). Still
 * a SocketTimeoutException, so every existing catch of one, or of an IOException, is unchanged.
 */
class BERTHttpReadTimeoutException(host: String, cause: SocketTimeoutException) : SocketTimeoutException("No answer in time from $host") {
    init { initCause(cause) }
}

/** Solana base58 address: the case-sensitive form GeckoTerminal needs (DEX Screener URLs lowercase it). */
internal val SOLANA_ADDRESS = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")

/** Marks requests that reached the Berthalla server only because a direct source failed. */
internal fun fallbackUrl(serverUrl: String, directEnabled: Boolean): String =
    if (directEnabled) "$serverUrl?via=fallback" else serverUrl
