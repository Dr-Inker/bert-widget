package global.bert.widget.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Minimal GET seam so direct-source and fallback behaviour can be tested without a network. */
fun interface BERTHttp {
    fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String

    companion object {
        val DEFAULT = BERTHttp { url, maxBytes, readTimeoutMs ->
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = readTimeoutMs
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "BERT-Android")
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode} from ${connection.url.host}")
                val declaredLength = connection.contentLengthLong
                require(declaredLength < 0 || declaredLength <= maxBytes) { "Response is too large" }
                connection.inputStream.use { readUtf8WithLimit(it, maxBytes) }
            } finally {
                connection.disconnect()
            }
        }

        fun readUtf8WithLimit(input: InputStream, maxBytes: Int): String {
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
            return output.toString(Charsets.UTF_8.name())
        }
    }
}

/** Solana base58 address: the case-sensitive form GeckoTerminal needs (DEX Screener URLs lowercase it). */
internal val SOLANA_ADDRESS = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")

/** Marks requests that reached the Berthalla server only because a direct source failed. */
internal fun fallbackUrl(serverUrl: String, directEnabled: Boolean): String =
    if (directEnabled) "$serverUrl?via=fallback" else serverUrl
