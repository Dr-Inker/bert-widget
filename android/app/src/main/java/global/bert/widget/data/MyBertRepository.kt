package global.bert.widget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import global.bert.widget.theme.BERTPalette
import global.bert.widget.theme.BERTThemeStore
import global.bert.widget.theme.MyBertPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/** GET and POST seam for My Bert, so lookups and images can be tested without a network. Returns the raw body. */
interface MyBertNet {
    fun get(url: String, maxBytes: Int, readTimeoutMs: Int, accept: String): ByteArray
    fun post(url: String, json: String, maxBytes: Int, readTimeoutMs: Int): ByteArray

    companion object {
        /** HTTPS to the three My Bert hosts only, no redirects, a fixed agent, and the same byte caps as the rest of the app. */
        val DEFAULT: MyBertNet = object : MyBertNet {
            override fun get(url: String, maxBytes: Int, readTimeoutMs: Int, accept: String) = request(url, null, maxBytes, readTimeoutMs, accept)
            override fun post(url: String, json: String, maxBytes: Int, readTimeoutMs: Int) = request(url, json, maxBytes, readTimeoutMs, "application/json")

            private fun request(url: String, json: String?, maxBytes: Int, readTimeoutMs: Int, accept: String): ByteArray {
                val target = URL(url)
                require(target.protocol == "https" && target.host in MyBertSources.HOSTS) { "Not a My Bert source" }
                val connection = target.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = readTimeoutMs
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.setRequestProperty("Accept", accept)
                    connection.setRequestProperty("User-Agent", "BERT-Android")
                    if (json != null) {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    }
                    val status = connection.responseCode
                    if (status !in 200..299) throw MyBertHttpException(status)
                    val declared = connection.contentLengthLong
                    require(declared < 0 || declared <= maxBytes) { "Response is too large" }
                    return connection.inputStream.use { BERTHttp.readBytesWithLimit(it, maxBytes) }
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
}

/** What a lookup found. [gap] names a source that did not answer, so the list may be missing some Berts. */
data class MyBertList(val wallet: String, val berts: List<MyBert>, val fetchedAtEpochMillis: Long, val gap: Gap? = null) {
    enum class Gap { HELD, STAKED }
}

/** Why a lookup found nothing to show. */
class MyBertLookupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { OFFLINE, BUSY, FAILED }
}

/** The Bert in use, and the colour its scene was sampled to (solid backgrounds need none). */
data class ChosenBert(val bert: MyBert, val sampledArgb: Int?) {
    val palette: BERTPalette get() = MyBertPalette.forBackground(bert.background, sampledArgb)
}

/**
 * Everything My Bert keeps, all of it in app-private storage (excluded from backup like the rest of the app):
 * the pasted address, the last list (briefly), and the chosen Bert's picture. [forget] removes all of it.
 */
class MyBertStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("bert_mybert", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "mybert")
    internal val thumbnails get() = File(context.cacheDir, "mybert-thumbnails")
    /** The chosen Bert's picture, re-encoded by the app (never a downloaded file as-is). Widgets and cards read it offline. */
    val imageFile: File get() = File(directory, "chosen.png")

    fun wallet(): String? = preferences.getString(KEY_WALLET, null)?.takeIf(::isSolanaPubkey)

    /** Which "Forget this address" and "classic Bert" a piece of work started after; see [keepLookup] and [saveChosen]. */
    fun walletEpoch(): Long = WALLET_EPOCH.get()
    fun choiceEpoch(): Long = CHOICE_EPOCH.get()

    fun saveWallet(wallet: String) {
        require(isSolanaPubkey(wallet))
        if (wallet != wallet()) preferences.edit().putString(KEY_WALLET, wallet).remove(KEY_LIST).apply()
    }

    /**
     * Keeps a finished lookup's address and list, unless "Forget this address" was tapped after it started
     * ([startedAt] from [walletEpoch]): a lookup still in flight must never bring a forgotten address back.
     */
    fun keepLookup(list: MyBertList, startedAt: Long): Boolean = synchronized(LOCK) {
        if (WALLET_EPOCH.get() != startedAt) return false
        saveWallet(list.wallet)
        saveList(list)
        true
    }

    /** Writes a downloaded grid picture to the cache, unless the address was forgotten after the download started. */
    internal fun keepThumbnail(file: File, bytes: ByteArray, startedAt: Long): Boolean = synchronized(LOCK) {
        if (WALLET_EPOCH.get() != startedAt) return false
        runCatching { thumbnails.mkdirs(); file.writeBytes(bytes) }
        true
    }

    /** The last list for [wallet], while it is younger than [LIST_TTL_MILLIS]: reopening the screen does not look again. */
    fun cachedList(wallet: String, now: Long): MyBertList? {
        val json = preferences.getString(KEY_LIST, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        if (json.optString("wallet") != wallet) return null
        val at = json.optLong("fetchedAt")
        // An old list is deleted, not just ignored: the next tap looks again.
        if (now - at !in 0 until LIST_TTL_MILLIS) { preferences.edit().remove(KEY_LIST).apply(); return null }
        val items = json.optJSONArray("berts") ?: return null
        val berts = (0 until minOf(items.length(), MyBertSources.MAX_TOKENS * 2)).mapNotNull { items.optJSONObject(it)?.let(MyBert::fromJson) }
        val gap = json.optString("gap").takeIf { it.isNotEmpty() }?.let { runCatching { MyBertList.Gap.valueOf(it) }.getOrNull() }
        return MyBertList(wallet, berts, at, gap)
    }

    fun saveList(list: MyBertList) {
        preferences.edit().putString(KEY_LIST, JSONObject().put("wallet", list.wallet).put("fetchedAt", list.fetchedAtEpochMillis)
            .putOpt("gap", list.gap?.name).put("berts", JSONArray().apply { list.berts.forEach { put(it.toJson()) } }).toString()).apply()
    }

    fun chosen(): ChosenBert? {
        val json = preferences.getString(KEY_CHOSEN, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val bert = MyBert.fromJson(json) ?: return null
        if (!imageFile.isFile) return null
        return ChosenBert(bert, if (json.has("sampled")) json.optInt("sampled") else null)
    }

    /**
     * Stores [picture] (already decoded and size-checked) as the chosen Bert's image, then records the choice. With
     * [startedAt] (from [choiceEpoch]) nothing is kept if "classic Bert" or "Forget" was tapped while it downloaded.
     */
    fun saveChosen(bert: MyBert, picture: Bitmap, startedAt: Long? = null): ChosenBert = synchronized(LOCK) {
        if (startedAt != null && CHOICE_EPOCH.get() != startedAt) throw CancellationException("My Bert was cleared")
        check(directory.mkdirs() || directory.isDirectory)
        val partial = File(directory, "chosen.png.part")
        try {
            partial.outputStream().use { check(picture.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            check(partial.renameTo(imageFile) || (imageFile.delete() && partial.renameTo(imageFile))) { "Could not keep the picture" }
        } finally {
            partial.delete()
        }
        val sampled = if (bert.background in MyBertSources.SOLID_BACKGROUNDS) null else MyBertPalette.sample(picture)
        preferences.edit().putString(KEY_CHOSEN, bert.toJson().apply { sampled?.let { put("sampled", it) } }.toString()).apply()
        ChosenBert(bert, sampled)
    }

    /** Back to classic Bert everywhere: no chosen picture, and "Match my Bert" off so a later choice never re-themes silently. */
    fun clearChosen() = synchronized(LOCK) {
        CHOICE_EPOCH.incrementAndGet()
        preferences.edit().remove(KEY_CHOSEN).apply()
        imageFile.delete()
        BERTThemeStore(context).matchMyBert = false
    }

    /** Forget the address: the address, the list, the chosen Bert and every cached picture. */
    fun forget() = synchronized(LOCK) {
        WALLET_EPOCH.incrementAndGet()
        clearChosen()
        preferences.edit().clear().apply()
        directory.deleteRecursively()
        thumbnails.deleteRecursively()
    }

    companion object {
        const val LIST_TTL_MILLIS = 10 * 60_000L
        private const val KEY_WALLET = "wallet"
        private const val KEY_LIST = "list"
        private const val KEY_CHOSEN = "chosen"
        // Process-wide, because every screen makes its own store. Forget and classic bump them under LOCK; work that
        // began before a bump writes nothing.
        private val LOCK = Any()
        private val WALLET_EPOCH = AtomicLong()
        private val CHOICE_EPOCH = AtomicLong()
    }
}

/**
 * Finds a wallet's Berts and fetches their pictures. One lookup per tap (no polling): Magic Eden for held and listed
 * Berts, one Solana RPC read for staked positions, and one DAS read to confirm those. The address goes nowhere else.
 */
class MyBertRepository(context: Context, private val net: MyBertNet = MyBertNet.DEFAULT, private val clock: () -> Long = System::currentTimeMillis) {
    val store = MyBertStore(context.applicationContext)

    suspend fun lookup(wallet: String): MyBertList = withContext(Dispatchers.IO) {
        require(isSolanaPubkey(wallet)) { "Invalid wallet address" }
        val startedAt = store.walletEpoch()
        val held = attempt { MyBertSources.parseMagicEden(net.get(MyBertSources.magicEdenUrl(wallet), MyBertSources.MAX_ME_BYTES, 10_000, "application/json").utf8(), wallet) }
        ensureActive()
        val staked = attempt { stakedBerts(wallet) }
        ensureActive()
        if (held.isFailure && staked.isFailure) throw failure(listOfNotNull(held.exceptionOrNull(), staked.exceptionOrNull()))
        val gap = when { held.isFailure -> MyBertList.Gap.HELD; staked.isFailure -> MyBertList.Gap.STAKED; else -> null }
        val list = MyBertList(wallet, MyBertSources.merge(held.getOrDefault(emptyList()), staked.getOrDefault(emptyList())), clock(), gap)
        // Kept only once a lookup has worked, so a mistyped or offline attempt leaves nothing behind, and never after
        // a Forget tapped while it ran.
        if (!store.keepLookup(list, startedAt)) throw CancellationException("Address forgotten during the lookup")
        list
    }

    private fun stakedBerts(wallet: String): List<MyBert> {
        val positions = net.post(MyBertSources.RPC_URL, MyBertSources.stakedPositionsRequest(wallet), MyBertSources.MAX_RPC_BYTES, 15_000).utf8()
        val assets = MyBertSources.parseStakedPositions(positions, wallet).map { it.asset }
        if (assets.isEmpty()) return emptyList()
        val confirmed = net.post(MyBertSources.RPC_URL, MyBertSources.assetBatchRequest(assets), MyBertSources.MAX_DAS_BYTES, 15_000).utf8()
        return MyBertSources.parseStakedAssets(confirmed, assets)
    }

    /** A grid picture: memory, then the cache folder, then Pinata's small copy, then the original. Null when none decodes. */
    suspend fun thumbnail(bert: MyBert): Bitmap? {
        memory.get(bert.number)?.let { return it }
        val startedAt = store.walletEpoch()
        return downloads.withPermit {
            withContext(Dispatchers.IO) {
                val file = File(store.thumbnails, "${bert.number}.img")
                val cached = file.takeIf { it.isFile }?.readBytes()?.let { decodeBounded(it, THUMBNAIL_PX) }
                val bitmap = cached ?: run {
                    val bytes = fetchPicture(bert, preferSmall = true) ?: return@run null
                    decodeBounded(bytes, THUMBNAIL_PX)?.takeIf { store.keepThumbnail(file, bytes, startedAt) }
                }
                // Not remembered once the address is forgotten (a download that finished after Forget is dropped).
                bitmap?.takeIf { store.walletEpoch() == startedAt }?.also { memory.put(bert.number, it) }
            }
        }
    }

    /** Makes [bert] the holder's Bert: the full-size picture (crisp on a 1080px card), kept on the phone. */
    suspend fun choose(bert: MyBert): ChosenBert = withContext(Dispatchers.IO) {
        val startedAt = store.choiceEpoch()
        val bytes = fetchPicture(bert, preferSmall = false) ?: throw MyBertLookupException(MyBertLookupException.Reason.FAILED)
        val picture = decodeBounded(bytes, CHOSEN_PX) ?: throw MyBertLookupException(MyBertLookupException.Reason.FAILED)
        ensureActive()
        store.saveChosen(bert, picture, startedAt)
    }

    private fun fetchPicture(bert: MyBert, preferSmall: Boolean): ByteArray? {
        val small = { net.get(bert.thumbnailUrl, MyBertSources.MAX_THUMBNAIL_BYTES, 10_000, "image/webp,image/png") }
        val original = { net.get(bert.imageUrl, MyBertSources.MAX_IMAGE_BYTES, 15_000, "image/png") }
        for (source in if (preferSmall) listOf(small, original) else listOf(original, small)) {
            val result = attempt(source)
            // Asked to slow down: no second request for the same picture.
            if ((result.exceptionOrNull() as? MyBertHttpException)?.status == 429) return null
            result.getOrNull()?.let { return it }
        }
        return null
    }

    private fun failure(errors: List<Throwable>): MyBertLookupException {
        val reason = when {
            errors.any { it is MyBertHttpException && it.status == 429 } -> MyBertLookupException.Reason.BUSY
            errors.any { it is IOException && it !is MyBertHttpException } -> MyBertLookupException.Reason.OFFLINE
            else -> MyBertLookupException.Reason.FAILED
        }
        return MyBertLookupException(reason, errors.firstOrNull())
    }

    companion object {
        private const val THUMBNAIL_PX = 256
        private const val CHOSEN_PX = 640
        private val memory = object : LruCache<Int, Bitmap>(8 * 1_024 * 1_024) {
            override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
        }
        // Pictures load three at a time: a big wallet never fires a hundred requests at once.
        private val downloads = Semaphore(3)

        internal fun clearMemory() = memory.evictAll()

        /**
         * Decodes untrusted picture bytes no larger than [maxPx] a side. Anything that is not a sane, roughly square
         * picture (a decompression bomb, a banner, a single pixel) is refused before its pixels are allocated.
         */
        internal fun decodeBounded(bytes: ByteArray, maxPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val (w, h) = bounds.outWidth to bounds.outHeight
            if (w !in 64..4_096 || h !in 64..4_096 || maxOf(w, h) > minOf(w, h) * 5 / 4) return null
            var sample = 1
            while (maxOf(w, h) / (sample * 2) >= maxPx) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            if (maxOf(decoded.width, decoded.height) <= maxPx) return decoded
            val scale = maxPx.toFloat() / maxOf(decoded.width, decoded.height)
            return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
    }
}

private fun ByteArray.utf8() = toString(Charsets.UTF_8)

/** runCatching that never swallows cancellation. */
private inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
