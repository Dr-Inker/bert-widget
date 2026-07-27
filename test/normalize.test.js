import assert from "node:assert/strict";
import test from "node:test";
import { BERT } from "../src/config.js";
import { normalizePair, selectBestBertPair } from "../src/normalize.js";

function pair(overrides = {}) {
  return {
    chainId: "solana",
    dexId: "raydium",
    pairAddress: "best-pair",
    url: "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    baseToken: { address: BERT.mint, symbol: "Bert" },
    quoteToken: { address: "sol", symbol: "SOL" },
    priceUsd: "0.009",
    priceChange: { h24: -2.5 },
    marketCap: 9_000_000,
    volume: { h24: 70_000 },
    liquidity: { usd: 700_000 },
    ...overrides,
  };
}

test("selects the most liquid valid pool", () => {
  const selected = selectBestBertPair([
    pair({ pairAddress: "small", liquidity: { usd: 10 } }),
    pair({ pairAddress: "large", liquidity: { usd: 1_000 } }),
  ]);
  assert.equal(selected.pairAddress, "large");
});

test("rejects a pool where BERT is only the quote token", () => {
  assert.throws(() => selectBestBertPair([
    pair({
      baseToken: { address: "other", symbol: "OTHER" },
      quoteToken: { address: BERT.mint, symbol: "Bert" },
    }),
  ]), /No valid BERT base-token pool/);
});

test("normalizes numeric strings and missing optional fields", () => {
  const result = normalizePair(
    pair({ marketCap: undefined }),
    new Date("2026-07-27T12:00:00Z"),
  );
  assert.equal(result.quote.priceUsd, 0.009);
  assert.equal(result.quote.marketCapUsd, null);
  assert.equal(result.source.observedAt, "2026-07-27T12:00:00.000Z");
});

test("accepts only HTTPS DEX Screener Solana pair URLs", () => {
  const accepted = normalizePair(pair({ url: "https://www.dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY" }));
  assert.equal(accepted.source.pairUrl, "https://www.dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY");

  for (const url of [
    "http://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    "intent://scan/#Intent;scheme=zxing;end",
    "https://dexscreener.com.evil.test/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    "https://user@dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    "https://dexscreener.com/ethereum/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY?redirect=evil",
  ]) {
    assert.throws(() => normalizePair(pair({ url })), /not an allowed DEX Screener/);
  }
});
