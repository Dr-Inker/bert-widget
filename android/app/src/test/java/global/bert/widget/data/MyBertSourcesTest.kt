package global.bert.widget.data

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/**
 * My Bert's parsing, from trimmed live answers (recorded 2026-10-12 for public holders seen in Magic Eden's
 * collection activity) and hostile variants of each. No network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MyBertSourcesTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/mybert/$name")).use { it.readBytes().toString(Charsets.UTF_8) }

    private val lister = "runpZTPawgvyKcUkbZrgGj9uPjBkzL5VGMzpS9L58TC" // one listed Bert
    private val staker = "5vC4PEAkJTdEmVCRk6gwSEXAJ5hscEbxgpZfz3RYBikp" // two staked Berts
    private val staked2206 = "Eemxg9w6qCiAR5V9Hbqhms9p6i1snBcaTs46WGQSL6Gc"
    private val staked3238 = "5t74xCNdx5yPht4BvrUcarEA51rzNEbbNJGt4StbPpDN"

    // --- addresses ---

    @Test fun `staking discriminator is the one the research filtered on`() {
        assertEquals("auAFi9Mky1D", MyBertSources.DISCRIMINATOR_BASE58)
    }

    @Test fun `only base58 strings that decode to 32 bytes are wallet addresses`() {
        for (good in listOf(lister, staker, MyBertSources.COLLECTION, "11111111111111111111111111111111")) assertTrue(good, isSolanaPubkey(good))
        val bad = listOf(
            "", "hello", "0OIl0OIl0OIl0OIl0OIl0OIl0OIl0OIl0OIl", // characters outside base58
            lister.dropLast(12), // too short
            lister + "abc", // decodes to more than 32 bytes
            "z".repeat(44), // the right length, too many bytes
            "$lister ", " $lister", // spaces are tidied by cleanWalletInput, never accepted raw
            "https://magiceden.io/u/$lister",
            "abandon ability able about above absent absorb abstract absurd abuse access accident", // a recovery phrase
            "4wBqpZM9xaSheZzJSMawUKKwhdpChKbZ5eu5ky4Vigw3ziCu56Fqwq3o86mHxQ3oBR2PR7Q4mCa2a6DXPxBgy4kG", // a private key's shape
        )
        for (text in bad) assertFalse(text, isSolanaPubkey(text))
        assertEquals(lister, cleanWalletInput("  $lister\n"))
        assertThrows(IllegalArgumentException::class.java) { MyBertSources.magicEdenUrl("not an address") }
    }

    @Test fun `base58 round-trips keys with leading zero bytes`() {
        val bytes = ByteArray(32).also { it[3] = 7; it[31] = 1 }
        assertTrue(Base58.decode(Base58.encode(bytes))!!.contentEquals(bytes))
        assertEquals(null, Base58.decode("0abc"))
    }

    // --- Magic Eden ---

    @Test fun `Magic Eden listed Bert parses with its tag and nothing about price`() {
        val berts = MyBertSources.parseMagicEden(fixture("me-tokens-listed.json"), lister)
        assertEquals(listOf(MyBert("CzWBx1Y6fnXSBiUVrehE8mbTKqe6jJyyfu3piKFYgAPB", 1506, "Green", staked = false, listed = true)), berts)
        val stored = berts.single().toJson().toString()
        assertFalse("price must never be kept: $stored", stored.contains("price", ignoreCase = true) || stored.contains("0.34"))
        assertEquals("https://green-advanced-mule-774.mypinata.cloud/ipfs/bafybeibas2bpfgih2j5dko5w7fiufwgmpqvoznzji3nif2xjix736kybpy/1506.png", berts.single().imageUrl)
        assertEquals(berts.single().imageUrl + "?img-width=256&img-format=webp", berts.single().thumbnailUrl)
    }

    private fun meToken(change: JSONObject.() -> Unit): String {
        val token = JSONArray(fixture("me-tokens-listed.json")).getJSONObject(0).apply(change)
        return JSONArray().put(token).toString()
    }

    @Test fun `Magic Eden lookalikes and foreign images are dropped`() {
        val image = "https://green-advanced-mule-774.mypinata.cloud/ipfs/bafybeibas2bpfgih2j5dko5w7fiufwgmpqvoznzji3nif2xjix736kybpy/1506.png"
        val hostile = mapOf<String, JSONObject.() -> Unit>(
            "other collection symbol" to { put("collection", "bert_coin_v2") },
            "update authority is not the collection" to { put("updateAuthority", "So11111111111111111111111111111111111111112") },
            "owner is someone else" to { put("owner", staker) },
            "number out of the collection" to { put("name", "Bert #3333") },
            "name and art disagree" to { put("name", "Bert #12") },
            "lookalike name" to { put("name", "Bert #1506 (official)") },
            "http image" to { put("image", image.replace("https://", "http://")) },
            "other host" to { put("image", image.replace("green-advanced-mule-774.mypinata.cloud", "evil.example")) },
            "lookalike host" to { put("image", image.replace("mypinata.cloud", "mypinata.cloud.evil.example")) },
            "user info" to { put("image", image.replace("https://", "https://evil.example@")) },
            "other port" to { put("image", image.replace(".cloud/", ".cloud:8443/")) },
            "other IPFS folder" to { put("image", image.replace("bafybeibas2", "bafybeixxx2")) },
            "query on the image" to { put("image", "$image?redirect=https://evil.example") },
            "javascript image" to { put("image", "javascript:alert(1)") },
            "bad mint" to { put("mintAddress", "not-a-mint") },
            "missing image" to { remove("image") },
        )
        for ((label, change) in hostile) assertEquals(label, emptyList<MyBert>(), MyBertSources.parseMagicEden(meToken(change), lister))
        // Unknown backgrounds are dropped, not trusted.
        assertEquals(null, MyBertSources.parseMagicEden(meToken {
            put("attributes", JSONArray().put(JSONObject().put("trait_type", "Background").put("value", "<script>")))
        }, lister).single().background)
    }

    @Test fun `Magic Eden huge or malformed answers are refused`() {
        val token = JSONArray(fixture("me-tokens-listed.json")).getJSONObject(0)
        val huge = JSONArray().apply { repeat(MyBertSources.MAX_TOKENS + 1) { put(token) } }.toString()
        assertThrows(IllegalArgumentException::class.java) { MyBertSources.parseMagicEden(huge, lister) }
        assertThrows(org.json.JSONException::class.java) { MyBertSources.parseMagicEden("{\"tokens\":[]}", lister) }
        assertThrows(org.json.JSONException::class.java) { MyBertSources.parseMagicEden("<html>busy</html>", lister) }
        assertEquals(emptyList<MyBert>(), MyBertSources.parseMagicEden("[1, null, \"x\", []]", lister))
    }

    // --- staking positions ---

    @Test fun `staked positions request filters on type, staker, NFT and open`() {
        val filters = JSONObject(MyBertSources.stakedPositionsRequest(staker)).getJSONArray("params").getJSONObject(1).getJSONArray("filters")
        val pairs = (0 until filters.length()).map { filters.getJSONObject(it).getJSONObject("memcmp").let { m -> m.getInt("offset") to m.getString("bytes") } }
        assertEquals(listOf(0 to "auAFi9Mky1D", 8 to staker, 88 to "1", 97 to "1"), pairs)
        assertEquals(MyBertSources.STAKING_PROGRAM, JSONObject(MyBertSources.stakedPositionsRequest(staker)).getJSONArray("params").getString(0))
    }

    @Test fun `real staking answer gives both staked assets`() {
        val positions = MyBertSources.parseStakedPositions(fixture("rpc-staked-positions.json"), staker)
        assertEquals(listOf(staked2206, staked3238), positions.map { it.asset })
        assertTrue(positions.all { it.depositedAtEpochSeconds in 1_780_000_000..1_800_000_000 })
        // The same answer read for another wallet yields nothing: the staker bytes are checked, not just the filter.
        assertEquals(emptyList<StakedPosition>(), MyBertSources.parseStakedPositions(fixture("rpc-staked-positions.json"), lister))
    }

    private fun positions(vararg data: String, owner: String = MyBertSources.STAKING_PROGRAM, encoding: String = "base64") =
        JSONObject().put("jsonrpc", "2.0").put("id", 1).put("result", JSONArray().apply {
            data.forEach { put(JSONObject().put("pubkey", staked2206).put("account", JSONObject().put("owner", owner).put("data", JSONArray().put(it).put(encoding)))) }
        }).toString()

    private val realPosition: ByteArray get() = Base64.getDecoder().decode(
        JSONObject(fixture("rpc-staked-positions.json")).getJSONArray("result").getJSONObject(0).getJSONObject("account").getJSONArray("data").getString(0))

    private fun mutated(change: (ByteArray) -> ByteArray): String = Base64.getEncoder().encodeToString(change(realPosition.copyOf()))

    @Test fun `hostile staking positions are skipped`() {
        val hostile = mapOf(
            "wrong account type" to mutated { it.also { b -> b[0] = 0 } },
            "someone else's position" to mutated { it.also { b -> b[8] = (b[8] + 1).toByte() } },
            "a token stake" to mutated { it.also { b -> b[88] = 1 } },
            "already claimed" to mutated { it.also { b -> b[97] = 1 } },
            "a flag that is neither" to mutated { it.also { b -> b[97] = 7 } },
            "short account" to mutated { it.copyOf(210) },
            "long account" to mutated { it.copyOf(212) },
            "the BERT token mint as the asset" to mutated { b -> b.also { Base58.decode(BERTQuoteRepository.BERT_MINT)!!.copyInto(it, 98) } },
            "malformed base64" to "yquyLiYwHyRJ!!!not base64%%%",
            "empty data" to "",
        )
        for ((label, data) in hostile) assertEquals(label, emptyList<StakedPosition>(), MyBertSources.parseStakedPositions(positions(data), staker))
        assertEquals("other program", emptyList<StakedPosition>(), MyBertSources.parseStakedPositions(positions(mutated { it }, owner = MyBertSources.STAKING_VAULT), staker))
        assertEquals("other encoding", emptyList<StakedPosition>(), MyBertSources.parseStakedPositions(positions(mutated { it }, encoding = "base58"), staker))
    }

    @Test fun `an asset with two positions keeps its latest deposit`() {
        fun at(seconds: Long) = mutated { b -> b.also { for (i in 0 until 8) it[72 + i] = (seconds shr (8 * i)).toByte() } }
        val positions = MyBertSources.parseStakedPositions(positions(at(1_790_000_000), at(1_790_500_000), at(1_789_000_000)), staker)
        assertEquals(listOf(StakedPosition(staked2206, 1_790_500_000)), positions)
    }

    @Test fun `staking answers that are too big, errors or rate limits are refused`() {
        val many = positions(*Array(201) { mutated { b -> b } })
        assertThrows(IllegalArgumentException::class.java) { MyBertSources.parseStakedPositions(many, staker) }
        val limited = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("error", JSONObject().put("code", 429).put("message", "Too many requests")).toString()
        assertEquals(429, assertThrows(MyBertHttpException::class.java) { MyBertSources.parseStakedPositions(limited, staker) }.status)
        val failed = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("error", JSONObject().put("code", -32010).put("message", "excluded")).toString()
        assertThrows(IllegalStateException::class.java) { MyBertSources.parseStakedPositions(failed, staker) }
        assertThrows(IllegalStateException::class.java) { MyBertSources.parseStakedPositions("{\"result\":{\"value\":[]}}", staker) }
    }

    // --- DAS confirmation ---

    @Test fun `DAS confirms both staked Berts in the vault and the collection`() {
        val berts = MyBertSources.parseStakedAssets(fixture("das-asset-batch.json"), listOf(staked2206, staked3238))
        assertEquals(listOf(MyBert(staked2206, 2206, "Forest", staked = true), MyBert(staked3238, 3238, "City", staked = true)), berts)
    }

    private fun dasAsset(change: JSONObject.() -> Unit): String {
        val asset = JSONObject(fixture("das-asset-batch.json")).getJSONArray("result").getJSONObject(0).apply(change)
        return JSONObject().put("jsonrpc", "2.0").put("id", 1).put("result", JSONArray().put(asset)).toString()
    }

    @Test fun `DAS lookalikes are rejected`() {
        val hostile = mapOf<String, JSONObject.() -> Unit>(
            "another collection" to { put("grouping", JSONArray().put(JSONObject().put("group_key", "collection").put("group_value", "So11111111111111111111111111111111111111112"))) },
            "two collections" to { getJSONArray("grouping").put(JSONObject().put("group_key", "collection").put("group_value", "So11111111111111111111111111111111111111112")) },
            "no grouping" to { remove("grouping") },
            "not in the vault" to { getJSONObject("ownership").put("owner", staker) },
            "burnt" to { put("burnt", true) },
            "not an MPL Core asset" to { put("interface", "ProgrammableNFT") },
            "asset nobody asked about" to { put("id", "CzWBx1Y6fnXSBiUVrehE8mbTKqe6jJyyfu3piKFYgAPB") },
            "image on another host" to { getJSONObject("content").put("links", JSONObject().put("image", "https://evil.example/ipfs/bafybeibas2bpfgih2j5dko5w7fiufwgmpqvoznzji3nif2xjix736kybpy/2206.png")) },
            "name and art disagree" to { getJSONObject("content").getJSONObject("metadata").put("name", "Bert #1") },
        )
        for ((label, change) in hostile) assertEquals(label, emptyList<MyBert>(), MyBertSources.parseStakedAssets(dasAsset(change), listOf(staked2206)))
        val both = fixture("das-asset-batch.json")
        assertThrows(IllegalArgumentException::class.java) { MyBertSources.parseStakedAssets(both, listOf(staked2206)) } // more than asked
        assertThrows(IllegalArgumentException::class.java) { MyBertSources.assetBatchRequest(listOf("bad")) }
        assertThrows(IllegalArgumentException::class.java) { MyBertSources.assetBatchRequest(List(101) { staked2206 }) }
    }

    // --- merge ---

    @Test fun `merge keeps one entry per mint, staking wins, lookalike numbers are dropped`() {
        val held = listOf(MyBert(staked2206, 2206, "Forest", listed = true), MyBert("CzWBx1Y6fnXSBiUVrehE8mbTKqe6jJyyfu3piKFYgAPB", 1506, "Green"))
        val staked = listOf(MyBert(staked2206, 2206, "Forest", staked = true), MyBert(staked3238, 3238, "City", staked = true))
        assertEquals(listOf(1506 to false, 2206 to true, 3238 to true), MyBertSources.merge(held, staked).map { it.number to it.staked })
        val twin = MyBert("So11111111111111111111111111111111111111112", 1506, "Green")
        assertEquals(listOf(2206, 3238), MyBertSources.merge(held + twin, staked).map { it.number })
    }

    @Test fun `image numbers come only from the collection's own folder`() {
        assertEquals(0, MyBertSources.imageNumber(MyBertSources.imageUrl(0)))
        assertEquals(3332, MyBertSources.imageNumber(MyBertSources.imageUrl(3332)))
        assertEquals(null, MyBertSources.imageNumber(MyBertSources.imageUrl(12).replace("12.png", "3333.png")))
        assertEquals(null, MyBertSources.imageNumber(MyBertSources.thumbnailUrl(12)))
        assertEquals(null, MyBertSources.imageNumber("https://GREEN-ADVANCED-MULE-774.mypinata.cloud/ipfs/x/1.png"))
        assertEquals(null, MyBertSources.imageNumber("x".repeat(400)))
    }

    @Test fun `our cached form is re-checked on the way in`() {
        assertEquals(null, MyBert.fromJson(JSONObject().put("mint", "bad").put("number", 1)))
        assertEquals(null, MyBert.fromJson(JSONObject().put("mint", staked2206).put("number", 9_999)))
        val bert = MyBert(staked2206, 2206, "Forest", staked = true)
        assertEquals(bert, MyBert.fromJson(bert.toJson()))
    }
}
