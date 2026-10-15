package global.bert.widget.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.util.Base64

/**
 * One Bert from the official BERT NFT collection that a pasted wallet holds: in the wallet, listed on Magic Eden or
 * staked in the BERT staking vault. Read-only: there is no price here, by design, and nothing here can be spent.
 */
data class MyBert(val mint: String, val number: Int, val background: String?, val staked: Boolean = false, val listed: Boolean = false) {
    val name: String get() = "Bert #$number"
    val imageUrl: String get() = MyBertSources.imageUrl(number)
    val thumbnailUrl: String get() = MyBertSources.thumbnailUrl(number)

    fun toJson(): JSONObject = JSONObject().put("mint", mint).put("number", number).putOpt("background", background)
        .put("staked", staked).put("listed", listed)

    companion object {
        /** Our own cached form, re-checked on the way in (an older build or a damaged file must not slip through). */
        fun fromJson(json: JSONObject): MyBert? {
            val mint = json.optString("mint").takeIf(::isSolanaPubkey) ?: return null
            val number = json.optInt("number", -1).takeIf { it in MyBertSources.NUMBERS } ?: return null
            val background = json.optString("background", "").takeIf { it in MyBertSources.BACKGROUNDS }
            return MyBert(mint, number, background, json.optBoolean("staked"), json.optBoolean("listed"))
        }
    }
}

/** Solana's base58 alphabet, for public keys only (no checksum). */
internal object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val FIFTY_EIGHT = BigInteger.valueOf(58)

    fun encode(bytes: ByteArray): String {
        var value = BigInteger(1, bytes)
        val out = StringBuilder()
        while (value.signum() > 0) {
            val (quotient, remainder) = value.divideAndRemainder(FIFTY_EIGHT)
            out.append(ALPHABET[remainder.toInt()])
            value = quotient
        }
        repeat(bytes.takeWhile { it == 0.toByte() }.size) { out.append('1') }
        return out.reverse().toString()
    }

    fun decode(text: String): ByteArray? {
        if (text.isEmpty() || text.length > 64) return null
        var value = BigInteger.ZERO
        for (char in text) {
            val digit = ALPHABET.indexOf(char)
            if (digit < 0) return null
            value = value.multiply(FIFTY_EIGHT).add(BigInteger.valueOf(digit.toLong()))
        }
        val body = value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
            .let { if (value.signum() == 0) ByteArray(0) else it }
        return ByteArray(text.takeWhile { it == '1' }.length) + body
    }
}

/** A Solana public key: the base58 shape (SOLANA_ADDRESS) that also decodes to exactly 32 bytes. */
internal fun isSolanaPubkey(text: String): Boolean = SOLANA_ADDRESS.matches(text) && Base58.decode(text)?.size == 32

/** A pasted wallet address, tidied: surrounding spaces and line breaks from a copy are dropped, nothing else is changed. */
internal fun cleanWalletInput(text: String): String = text.trim().trim('\u200B', '\uFEFF')

/**
 * The three sources and how every answer is checked. Every response is untrusted: sizes and counts are capped before
 * parsing, and an item survives only if it proves it is from the official collection. Image addresses are rebuilt
 * from the Bert's number on the one known gateway, never taken from a response.
 */
object MyBertSources {
    const val COLLECTION = "AYh4yso4fZUVHEMMpS3wXxwZt1TgYnbFPb5c4y5LDFFS"
    const val MAGIC_EDEN_SYMBOL = "bert_coin"
    const val STAKING_PROGRAM = "BcTJUjVtpYZ2mozwHxGZdJRfQbEfCoZyEqwus8W2cajq"
    const val STAKING_VAULT = "mu5h6byDpZBzugjEuswVSUTqEshhmi6M5C1nwZhB8T1"
    const val MAGIC_EDEN_HOST = "api-mainnet.magiceden.dev"
    const val RPC_URL = "https://api.mainnet-beta.solana.com"
    const val RPC_HOST = "api.mainnet-beta.solana.com"
    const val IMAGE_HOST = "green-advanced-mule-774.mypinata.cloud"
    private const val IMAGE_CID = "bafybeibas2bpfgih2j5dko5w7fiufwgmpqvoznzji3nif2xjix736kybpy"
    /** Every host My Bert may contact. The Berthalla server is deliberately not one of them. */
    val HOSTS = setOf(MAGIC_EDEN_HOST, RPC_HOST, IMAGE_HOST)

    /** 3,333 were minted, numbered 0 to 3332. */
    val NUMBERS = 0..3332
    val SOLID_BACKGROUNDS = listOf("Green", "Yellow", "Blue", "Pink", "Red")
    val BACKGROUNDS = SOLID_BACKGROUNDS + listOf("Forest", "Heaven", "Moon", "City")

    const val MAX_TOKENS = 100
    const val MAX_ME_BYTES = 400 * 1_024
    const val MAX_RPC_BYTES = 256 * 1_024
    const val MAX_DAS_BYTES = 512 * 1_024
    const val MAX_THUMBNAIL_BYTES = 512 * 1_024
    const val MAX_IMAGE_BYTES = 2 * 1_024 * 1_024
    private const val MAX_POSITIONS = 200

    // Staking position account, as reverse-engineered and verified against mainnet (bert-staking-watch, 2026-10-01).
    private val POSITION_DISCRIMINATOR = byteArrayOf(202.toByte(), 171.toByte(), 178.toByte(), 46, 38, 48, 31, 36)
    private const val POSITION_SIZE = 211
    private const val OWNER_OFFSET = 8
    private const val DEPOSIT_TIME_OFFSET = 72
    private const val IS_TOKEN_OFFSET = 88
    private const val CLAIMED_OFFSET = 97
    private const val ASSET_OFFSET = 98
    internal val DISCRIMINATOR_BASE58: String = Base58.encode(POSITION_DISCRIMINATOR)

    private val NAME = Regex("^Bert #(\\d{1,4})$")
    private val IMAGE_PATH = Regex("^/ipfs/$IMAGE_CID/(\\d{1,4})\\.png$")

    fun imageUrl(number: Int): String {
        require(number in NUMBERS)
        return "https://$IMAGE_HOST/ipfs/$IMAGE_CID/$number.png"
    }

    /** Pinata's resized copy: a quarter of the bytes. The original stays the fallback. */
    fun thumbnailUrl(number: Int): String = imageUrl(number) + "?img-width=256&img-format=webp"

    fun magicEdenUrl(wallet: String): String {
        require(isSolanaPubkey(wallet)) { "Invalid wallet address" }
        return "https://$MAGIC_EDEN_HOST/v2/wallets/$wallet/tokens?collection_symbol=$MAGIC_EDEN_SYMBOL&offset=0&limit=$MAX_TOKENS"
    }

    /** The Bert's number from an image address, only when it is exactly the collection's art on the known gateway. */
    internal fun imageNumber(url: String?): Int? {
        if (url == null || url.length > 300) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != IMAGE_HOST || uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return IMAGE_PATH.matchEntire(uri.rawPath ?: return null)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in NUMBERS }
    }

    private fun nameNumber(name: String?): Int? = name?.let { NAME.matchEntire(it) }?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in NUMBERS }

    private fun background(attributes: JSONArray?): String? {
        if (attributes == null || attributes.length() > 32) return null
        for (index in 0 until attributes.length()) {
            val trait = attributes.optJSONObject(index) ?: continue
            if (trait.optString("trait_type") == "Background") return trait.optString("value").takeIf { it in BACKGROUNDS }
        }
        return null
    }

    /**
     * Magic Eden's wallet tokens (held and listed). A token counts only when Magic Eden files it under the collection,
     * its update authority is the collection itself (how MPL Core ties an asset to its collection), its owner is the
     * pasted wallet, and its name and art agree on one number.
     */
    fun parseMagicEden(raw: String, wallet: String): List<MyBert> {
        val tokens = JSONArray(raw)
        require(tokens.length() <= MAX_TOKENS) { "Too many tokens" }
        val berts = mutableListOf<MyBert>()
        for (index in 0 until tokens.length()) {
            val token = tokens.optJSONObject(index) ?: continue
            val mint = token.optString("mintAddress").takeIf(::isSolanaPubkey) ?: continue
            if (token.optString("collection") != MAGIC_EDEN_SYMBOL) continue
            if (token.optString("updateAuthority") != COLLECTION) continue
            if (token.optString("owner") != wallet) continue
            val number = nameNumber(token.optString("name")) ?: continue
            if (imageNumber(token.optString("image")) != number) continue
            berts += MyBert(mint, number, background(token.optJSONArray("attributes")), staked = false,
                listed = token.optString("listStatus") == "listed")
        }
        return berts
    }

    /** JSON-RPC body: the wallet's open NFT positions in the staking program (NFT, not token; not yet claimed). */
    fun stakedPositionsRequest(wallet: String): String {
        require(isSolanaPubkey(wallet)) { "Invalid wallet address" }
        fun memcmp(offset: Int, bytes: String) = JSONObject().put("memcmp", JSONObject().put("offset", offset).put("bytes", bytes))
        val zero = Base58.encode(byteArrayOf(0))
        return JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getProgramAccounts")
            .put("params", JSONArray().put(STAKING_PROGRAM).put(JSONObject().put("encoding", "base64")
                .put("filters", JSONArray().put(memcmp(0, DISCRIMINATOR_BASE58)).put(memcmp(OWNER_OFFSET, wallet))
                    .put(memcmp(IS_TOKEN_OFFSET, zero)).put(memcmp(CLAIMED_OFFSET, zero)))))
            .toString()
    }

    /**
     * Staked assets from the positions: every account is re-checked byte by byte (owner program, size, type, staker,
     * NFT, open), and an asset with more than one position keeps its latest deposit.
     */
    fun parseStakedPositions(raw: String, wallet: String): List<StakedPosition> {
        val walletBytes = requireNotNull(Base58.decode(wallet)?.takeIf { it.size == 32 }) { "Invalid wallet address" }
        val positions = rpcResult(raw) as? JSONArray ?: error("Unexpected staking response")
        require(positions.length() <= MAX_POSITIONS) { "Too many staking positions" }
        val latest = linkedMapOf<String, Long>()
        for (index in 0 until positions.length()) {
            val account = positions.optJSONObject(index)?.optJSONObject("account") ?: continue
            if (account.optString("owner") != STAKING_PROGRAM) continue
            val data = account.optJSONArray("data") ?: continue
            if (data.length() != 2 || data.optString(1) != "base64") continue
            val bytes = runCatching { Base64.getDecoder().decode(data.optString(0)) }.getOrNull() ?: continue
            if (bytes.size != POSITION_SIZE) continue
            if (!bytes.copyOfRange(0, 8).contentEquals(POSITION_DISCRIMINATOR)) continue
            if (!bytes.copyOfRange(OWNER_OFFSET, OWNER_OFFSET + 32).contentEquals(walletBytes)) continue
            if (bytes[IS_TOKEN_OFFSET] != 0.toByte() || bytes[CLAIMED_OFFSET] != 0.toByte()) continue
            val asset = Base58.encode(bytes.copyOfRange(ASSET_OFFSET, ASSET_OFFSET + 32))
            if (asset == BERTQuoteRepository.BERT_MINT) continue
            val deposited = littleEndianLong(bytes, DEPOSIT_TIME_OFFSET)
            if ((latest[asset] ?: Long.MIN_VALUE) < deposited) latest[asset] = deposited
        }
        return latest.entries.take(MAX_TOKENS).map { StakedPosition(it.key, it.value) }
    }

    fun assetBatchRequest(ids: List<String>): String {
        require(ids.isNotEmpty() && ids.size <= MAX_TOKENS && ids.all(::isSolanaPubkey)) { "Invalid asset list" }
        return JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getAssetBatch")
            .put("params", JSONObject().put("ids", JSONArray(ids))).toString()
    }

    /**
     * The staked assets, confirmed on-chain by DAS: an MPL Core asset in the collection's group, held by the staking
     * vault and not burnt. Assets nobody asked about are ignored.
     */
    fun parseStakedAssets(raw: String, requested: Collection<String>): List<MyBert> {
        val assets = rpcResult(raw) as? JSONArray ?: error("Unexpected asset response")
        require(assets.length() <= requested.size) { "More assets than requested" }
        val berts = mutableListOf<MyBert>()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val id = asset.optString("id").takeIf { it in requested } ?: continue
            if (asset.optString("interface") != "MplCoreAsset" || asset.optBoolean("burnt", false)) continue
            if (asset.optJSONObject("ownership")?.optString("owner") != STAKING_VAULT) continue
            val groups = asset.optJSONArray("grouping") ?: continue
            val collections = (0 until minOf(groups.length(), 8)).mapNotNull { groups.optJSONObject(it) }
                .filter { it.optString("group_key") == "collection" }.map { it.optString("group_value") }
            if (collections != listOf(COLLECTION)) continue
            val content = asset.optJSONObject("content") ?: continue
            val metadata = content.optJSONObject("metadata") ?: continue
            val number = nameNumber(metadata.optString("name")) ?: continue
            val image = content.optJSONObject("links")?.optString("image")?.takeIf { it.isNotEmpty() }
                ?: content.optJSONArray("files")?.optJSONObject(0)?.optString("uri")
            if (imageNumber(image) != number) continue
            berts += MyBert(id, number, background(metadata.optJSONArray("attributes")), staked = true, listed = false)
        }
        return berts
    }

    /** One entry per mint, in number order. On-chain staking outranks a marketplace listing of the same Bert. */
    fun merge(held: List<MyBert>, staked: List<MyBert>): List<MyBert> {
        val byMint = linkedMapOf<String, MyBert>()
        held.forEach { byMint.putIfAbsent(it.mint, it) }
        staked.forEach { byMint[it.mint] = it }
        // Two mints claiming one number is a lookalike: neither is shown.
        val counts = byMint.values.groupingBy { it.number }.eachCount()
        return byMint.values.filter { counts[it.number] == 1 }.sortedBy { it.number }
    }

    private fun rpcResult(raw: String): Any? {
        val response = JSONObject(raw)
        response.optJSONObject("error")?.let { error ->
            if (error.optInt("code") == 429) throw MyBertHttpException(429)
            error("RPC error")
        }
        return response.opt("result")
    }

    private fun littleEndianLong(bytes: ByteArray, offset: Int): Long =
        (0 until 8).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i)) }
}

/** An open NFT position in the staking program: the asset and when it was deposited (unix seconds). */
data class StakedPosition(val asset: String, val depositedAtEpochSeconds: Long)

/** A non-2xx answer; 429 means the service asked us to slow down. */
class MyBertHttpException(val status: Int) : java.io.IOException("HTTP $status")
