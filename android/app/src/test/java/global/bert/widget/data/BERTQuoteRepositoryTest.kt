package global.bert.widget.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BERTQuoteRepositoryTest {
    @Test
    fun acceptsOnlyHttpsDexScreenerSolanaPairUrls() {
        val valid = "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"
        assertEquals(valid, BERTQuoteRepository.requireValidDexScreenerPairUrl(valid))

        listOf(
            "http://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
            "intent://scan/#Intent;scheme=zxing;end",
            "https://dexscreener.com.evil.test/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
            "https://user@dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
            "https://dexscreener.com/ethereum/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY?redirect=evil",
        ).forEach { invalid ->
            require(runCatching { BERTQuoteRepository.requireValidDexScreenerPairUrl(invalid) }.isFailure) {
                "Expected URL to be rejected: $invalid"
            }
        }
    }
}
