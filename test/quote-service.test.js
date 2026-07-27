import assert from "node:assert/strict";
import test from "node:test";
import { BERT } from "../src/config.js";
import { QuoteService } from "../src/quote-service.js";

const config = {
  upstreamUrl: "https://example.test/bert",
  upstreamTimeoutMs: 1_000,
  freshTtlMs: 60_000,
  staleTtlMs: 1_800_000,
};

function upstreamPair() {
  return {
    chainId: "solana",
    dexId: "raydium",
    pairAddress: "pair",
    url: "https://example.test/pair",
    baseToken: { address: BERT.mint, symbol: "Bert" },
    priceUsd: "0.01",
    liquidity: { usd: 100 },
  };
}

test("caches a fresh quote", async () => {
  let calls = 0;
  const fetchImpl = async () => {
    calls += 1;
    return Response.json([upstreamPair()]);
  };
  const service = new QuoteService({ fetchImpl, config });

  await service.getQuote();
  await service.getQuote();
  assert.equal(calls, 1);
});

test("returns cached data as stale when refresh fails", async () => {
  let now = new Date("2026-07-27T12:00:00Z");
  let shouldFail = false;
  const fetchImpl = async () => {
    if (shouldFail) throw new Error("network down");
    return Response.json([upstreamPair()]);
  };
  const service = new QuoteService({ fetchImpl, config, clock: () => now });

  await service.getQuote();
  now = new Date("2026-07-27T12:02:00Z");
  shouldFail = true;
  const result = await service.getQuote();

  assert.equal(result.meta.freshness, "stale");
  assert.equal(result.meta.ageSeconds, 120);
  assert.match(result.meta.warning, /network down/);
});
