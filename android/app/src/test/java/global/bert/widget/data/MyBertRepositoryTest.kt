package global.bert.widget.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import global.bert.widget.theme.BERTThemeStore
import global.bert.widget.theme.MyBertPalette
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.net.URI

/** My Bert's lookup, failure handling, storage and forgetting, against a fake network that records every request. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MyBertRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val staker = MyBertFixtures.STAKER
    @get:Rule val graphics = global.bert.widget.NativeGraphicsOnTestThread()

    @Before fun clean() {
        MyBertStore(context).forget()
        MyBertRepository.clearMemory()
    }

    @Test fun `a lookup asks Magic Eden and the Solana RPC only, and merges held, listed and staked Berts`() = runBlocking {
        val net = MyBertFixtures.FakeNet()
        val list = MyBertRepository(context, net, clock = { 1_000L }).lookup(staker)
        assertEquals(listOf(1506 to "listed", 2206 to "staked", 3238 to "staked"),
            list.berts.map { it.number to if (it.staked) "staked" else if (it.listed) "listed" else "held" })
        assertNull(list.gap)
        assertEquals(listOf("GET ${MyBertSources.magicEdenUrl(staker)}", "POST ${MyBertSources.RPC_URL} getProgramAccounts",
            "POST ${MyBertSources.RPC_URL} getAssetBatch"), net.requests)
        assertTrue(net.requests.all { URI(it.split(' ')[1]).host in MyBertSources.HOSTS })
        assertFalse("the address must never reach Berthalla", net.requests.any { "berthalla" in it })
        // The address is kept on the phone, with the list.
        assertEquals(staker, MyBertStore(context).wallet())
        assertEquals(list, MyBertStore(context).cachedList(staker, 1_000L + 60_000))
    }

    @Test fun `no staked positions means no DAS call`() = runBlocking {
        val net = MyBertFixtures.FakeNet(positions = { MyBertFixtures.json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[]}") })
        val list = MyBertRepository(context, net).lookup(staker)
        assertEquals(listOf(1506), list.berts.map { it.number })
        assertEquals(2, net.requests.size)
    }

    @Test fun `failures say why, and one working source still shows what it found`() {
        fun lookup(net: MyBertNet) = runBlocking { MyBertRepository(context, net).lookup(staker) }
        fun reason(net: MyBertNet) = assertThrows(MyBertLookupException::class.java) { lookup(net) }.reason
        val offline = { throw java.net.UnknownHostException("no network") }
        val limited = { throw MyBertHttpException(429) }
        assertEquals(MyBertLookupException.Reason.OFFLINE, reason(MyBertFixtures.FakeNet(magicEden = offline, positions = offline)))
        assertEquals(MyBertLookupException.Reason.BUSY, reason(MyBertFixtures.FakeNet(magicEden = limited, positions = offline)))
        val rpcLimited = { MyBertFixtures.json("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":429,\"message\":\"Too many requests\"}}") }
        assertEquals(MyBertLookupException.Reason.BUSY, reason(MyBertFixtures.FakeNet(magicEden = { throw MyBertHttpException(500) }, positions = rpcLimited)))
        assertEquals(MyBertLookupException.Reason.FAILED, reason(MyBertFixtures.FakeNet(magicEden = { MyBertFixtures.json("<html>") }, positions = { MyBertFixtures.json("{}") })))
        assertNull("a lookup that found nothing keeps no address", MyBertStore(context).wallet())

        val heldMissing = lookup(MyBertFixtures.FakeNet(magicEden = limited))
        assertEquals(MyBertList.Gap.HELD, heldMissing.gap)
        assertEquals(listOf(2206, 3238), heldMissing.berts.map { it.number })
        val stakedMissing = lookup(MyBertFixtures.FakeNet(assets = offline))
        assertEquals(MyBertList.Gap.STAKED, stakedMissing.gap)
        assertEquals(listOf(1506), stakedMissing.berts.map { it.number })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { MyBertRepository(context, MyBertFixtures.FakeNet()).lookup("nope") } }
    }

    @Test fun `the list is cached briefly and only for its own address`() = runBlocking {
        val store = MyBertStore(context)
        MyBertRepository(context, MyBertFixtures.FakeNet(), clock = { 10_000L }).lookup(staker)
        assertNotNull(store.cachedList(staker, 10_000L + MyBertStore.LIST_TTL_MILLIS - 1))
        assertNull(store.cachedList(MyBertFixtures.LISTER, 10_001L))
        // Expired (or a clock that went backwards): the list is deleted, not just ignored.
        assertNull(store.cachedList(staker, 9_000L))
        assertFalse(context.getSharedPreferences("bert_mybert", 0).contains("list"))
        MyBertRepository(context, MyBertFixtures.FakeNet(), clock = { 10_000L }).lookup(staker)
        assertNull(store.cachedList(staker, 10_000L + MyBertStore.LIST_TTL_MILLIS))
        assertFalse(context.getSharedPreferences("bert_mybert", 0).contains("list"))
        MyBertRepository(context, MyBertFixtures.FakeNet(), clock = { 10_000L }).lookup(staker)
        store.saveWallet(MyBertFixtures.LISTER) // a new address drops the old list
        assertNull(store.cachedList(staker, 10_001L))
    }

    @Test fun `Forget or classic Bert tapped while something is in flight wins, and that work writes nothing afterwards`() {
        // A lookup whose last answer arrives after Forget keeps no address and no list.
        val forgetDuringLookup = MyBertFixtures.FakeNet(assets = { MyBertStore(context).forget(); MyBertFixtures.resource("das-asset-batch.json") })
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking { MyBertRepository(context, forgetDuringLookup).lookup(staker) } }
        assertNull(MyBertStore(context).wallet())
        assertTrue(context.getSharedPreferences("bert_mybert", 0).all.isEmpty())

        // A picture that finishes downloading after "Go back to classic Bert" is not kept.
        val bert = runBlocking { MyBertRepository(context, MyBertFixtures.FakeNet()).lookup(staker) }.berts.first()
        val classicDuringDownload = MyBertFixtures.FakeNet(original = { MyBertStore(context).clearChosen(); MyBertFixtures.picture(bert.imageUrl) })
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking { MyBertRepository(context, classicDuringDownload).choose(bert) } }
        assertNull(MyBertStore(context).chosen())
        assertFalse(MyBertStore(context).imageFile.exists())
        assertEquals("classic Bert keeps the address", staker, MyBertStore(context).wallet())

        // A grid picture that arrives after Forget is cached neither on disk nor in memory.
        MyBertRepository.clearMemory()
        val forgetDuringThumbnail = MyBertFixtures.FakeNet(small = { MyBertStore(context).forget(); MyBertFixtures.picture(bert.imageUrl) })
        assertNull(runBlocking { MyBertRepository(context, forgetDuringThumbnail).thumbnail(bert) })
        assertFalse(MyBertStore(context).thumbnails.exists())
        val after = MyBertFixtures.FakeNet()
        assertNotNull(runBlocking { MyBertRepository(context, after).thumbnail(bert) })
        assertEquals("not served from memory", 1, after.requests.size)
    }

    @Test fun `choosing keeps the full-size picture on the phone and samples a scene background`() = runBlocking {
        val net = MyBertFixtures.FakeNet()
        val repository = MyBertRepository(context, net)
        val bert = repository.lookup(staker).berts.first { it.number == 2206 }
        val chosen = repository.choose(bert)
        assertTrue("the original is fetched first", net.requests.last() == "GET ${bert.imageUrl}")
        assertEquals(chosen, MyBertStore(context).chosen())
        val kept = BitmapFactory.decodeFile(MyBertStore(context).imageFile.path)
        assertEquals(256, kept.width)
        assertNotNull("Forest is a scene: its colour is sampled", chosen.sampledArgb)
        assertTrue(chosen.palette != global.bert.widget.theme.BERTThemePack.BERT_NAVY.palette)
        // A solid background needs no sample.
        val green = repository.choose(repository.lookup(staker).berts.first { it.number == 1506 })
        assertNull(green.sampledArgb)
        assertEquals(MyBertPalette.forBackground("Green", null), green.palette)
    }

    @Test fun `a missing original falls back to the small copy, and a picture that is not a picture is refused`() = runBlocking {
        val bert = MyBert(MyBertFixtures.STAKED_2206, 2206, "Forest", staked = true)
        val fallback = MyBertFixtures.FakeNet(original = { throw MyBertHttpException(404) })
        MyBertRepository(context, fallback).choose(bert)
        assertEquals(listOf("GET ${bert.imageUrl}", "GET ${bert.thumbnailUrl}"), fallback.requests)

        MyBertStore(context).clearChosen()
        val junk = MyBertFixtures.FakeNet(original = { "<html>not a picture</html>".toByteArray() }, small = { ByteArray(0) })
        assertThrows(MyBertLookupException::class.java) { runBlocking { MyBertRepository(context, junk).choose(bert) } }
        assertNull(MyBertStore(context).chosen())
        val banner = MyBertFixtures.png(400, 60) // not square: refused before decoding pixels
        assertNull(MyBertRepository(context, MyBertFixtures.FakeNet(small = { banner }, original = { banner })).thumbnail(bert))
        // A header claiming 20,000 x 20,000 pixels (1.6 GB decoded) is refused from its header alone.
        assertNull(MyBertRepository.decodeBounded(MyBertFixtures.pngHeader(20_000, 20_000), 256))
        assertNotNull(MyBertRepository.decodeBounded(MyBertFixtures.resource("bert-2206.png"), 256))
    }

    @Test fun `thumbnails are cached on disk and in memory`() = runBlocking {
        val net = MyBertFixtures.FakeNet()
        val bert = MyBert(MyBertFixtures.STAKED_2206, 2206, "Forest", staked = true)
        assertNotNull(MyBertRepository(context, net).thumbnail(bert))
        MyBertRepository.clearMemory()
        assertNotNull(MyBertRepository(context, net).thumbnail(bert))
        assertEquals("one download for two looks", listOf("GET ${bert.thumbnailUrl}"), net.requests)
    }

    @Test fun `forgetting removes the address, the list, the chosen Bert, every picture and Match my Bert`() = runBlocking {
        val repository = MyBertRepository(context, MyBertFixtures.FakeNet())
        val list = repository.lookup(staker)
        list.berts.forEach { repository.thumbnail(it) }
        repository.choose(list.berts.first())
        BERTThemeStore(context).matchMyBert = true
        assertTrue(MyBertStore(context).thumbnails.listFiles()!!.isNotEmpty())

        MyBertStore(context).forget()
        assertNull(MyBertStore(context).wallet())
        assertNull(MyBertStore(context).cachedList(staker, System.currentTimeMillis()))
        assertNull(MyBertStore(context).chosen())
        assertFalse(MyBertStore(context).imageFile.exists())
        assertFalse(MyBertStore(context).thumbnails.exists())
        assertFalse(BERTThemeStore(context).matchMyBert)
        assertTrue(context.getSharedPreferences("bert_mybert", 0).all.isEmpty())
    }

    @Test fun `going back to classic Bert switches Match my Bert off, so a later choice never re-themes silently`() = runBlocking {
        val repository = MyBertRepository(context, MyBertFixtures.FakeNet())
        repository.choose(repository.lookup(staker).berts.first())
        BERTThemeStore(context).matchMyBert = true
        assertEquals(MyBertStore(context).chosen()!!.palette, BERTThemeStore(context).loadPalette())
        MyBertStore(context).clearChosen()
        assertFalse(BERTThemeStore(context).matchMyBert)
        assertEquals(BERTThemeStore(context).load().palette, BERTThemeStore(context).loadPalette())
        assertEquals("the address stays until forgotten", staker, MyBertStore(context).wallet())
    }

    @Test fun `choosing a theme pack turns Match my Bert off`() {
        val store = BERTThemeStore(context)
        store.matchMyBert = true
        store.save(global.bert.widget.theme.BERTThemePack.MAYOR_PURPLE)
        assertFalse(store.matchMyBert)
    }

    @Test fun `the real network only talks HTTPS to My Bert's hosts`() {
        for (url in listOf("https://berthalla.io/widget/api/quote", "http://${MyBertSources.MAGIC_EDEN_HOST}/v2/x", "https://${MyBertSources.IMAGE_HOST}.evil.example/x.png"))
            assertThrows(url, IllegalArgumentException::class.java) { MyBertNet.DEFAULT.get(url, 1_000, 1_000, "application/json") }
        assertThrows(IllegalArgumentException::class.java) { MyBertNet.DEFAULT.post("https://berthalla.io/", "{}", 1_000, 1_000) }
    }

    @Test fun `byte caps hold even when the declared length lies`() {
        val big = ByteArray(10_001)
        assertThrows(IllegalArgumentException::class.java) { BERTHttp.readBytesWithLimit(big.inputStream(), 10_000) }
        assertEquals(10_000, BERTHttp.readBytesWithLimit(ByteArray(10_000).inputStream(), 10_000).size)
    }
}

/** Shared by the data, UI and widget tests: real-shaped answers for one public holder, and a recording fake network. */
internal object MyBertFixtures {
    const val STAKER = "5vC4PEAkJTdEmVCRk6gwSEXAJ5hscEbxgpZfz3RYBikp"
    const val LISTER = "runpZTPawgvyKcUkbZrgGj9uPjBkzL5VGMzpS9L58TC"
    const val STAKED_2206 = "Eemxg9w6qCiAR5V9Hbqhms9p6i1snBcaTs46WGQSL6Gc"

    fun resource(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/mybert/$name")).use { it.readBytes() }
    fun json(text: String) = text.toByteArray()

    /** Magic Eden's answer for the staker: the recorded listed Bert, re-addressed to this wallet (synthetic, same shape). */
    fun magicEdenForStaker(): ByteArray = JSONArray(resource("me-tokens-listed.json").toString(Charsets.UTF_8))
        .also { it.getJSONObject(0).put("owner", STAKER) }.toString().toByteArray()

    fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Just a PNG signature and header chunk: enough for a bounds decode to read the claimed size. */
    fun pngHeader(width: Int, height: Int): ByteArray {
        val body = java.nio.ByteBuffer.allocate(17).put("IHDR".toByteArray()).putInt(width).putInt(height)
            .put(8).put(6).put(0).put(0).put(0).array()
        val crc = java.util.zip.CRC32().apply { update(body) }.value.toInt()
        return byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) + java.nio.ByteBuffer.allocate(4).putInt(13).array() + body +
            java.nio.ByteBuffer.allocate(4).putInt(crc).array()
    }

    /** Pictures by number from the fixtures (2206 Forest, 1506 Green); any other number gets 2206's face. */
    fun picture(url: String): ByteArray = resource(if ("/1506.png" in url) "bert-1506.png" else "bert-2206.png")

    class FakeNet(
        val magicEden: () -> ByteArray = ::magicEdenForStaker,
        val positions: () -> ByteArray = { resource("rpc-staked-positions.json") },
        val assets: () -> ByteArray = { resource("das-asset-batch.json") },
        val small: (() -> ByteArray)? = null,
        val original: (() -> ByteArray)? = null,
    ) : MyBertNet {
        // Pictures load on several threads at once.
        val requests: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

        override fun get(url: String, maxBytes: Int, readTimeoutMs: Int, accept: String): ByteArray {
            requests += "GET $url"
            val host = URI(url).host
            val body = when {
                host == MyBertSources.MAGIC_EDEN_HOST -> magicEden()
                host == MyBertSources.IMAGE_HOST && "?" in url -> small?.invoke() ?: picture(url)
                host == MyBertSources.IMAGE_HOST -> original?.invoke() ?: picture(url)
                else -> error("unexpected $url")
            }
            require(body.size <= maxBytes) { "Response is too large" }
            return body
        }

        override fun post(url: String, json: String, maxBytes: Int, readTimeoutMs: Int): ByteArray {
            val method = JSONObject(json).getString("method")
            requests += "POST $url $method"
            require(url == MyBertSources.RPC_URL) { "unexpected $url" }
            val body = when (method) { "getProgramAccounts" -> positions(); "getAssetBatch" -> assets(); else -> error(method) }
            require(body.size <= maxBytes) { "Response is too large" }
            return body
        }
    }
}
