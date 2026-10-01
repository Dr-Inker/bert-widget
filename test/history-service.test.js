import assert from "node:assert/strict";
import test from "node:test";
import { BERT } from "../src/config.js";
import { HistoryService, normalizeOhlcv } from "../src/history-service.js";
import { createApp } from "../src/server.js";

const POOL = "BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY";
const NOW = new Date("2026-10-01T15:47:00Z");
const NOW_S = Math.floor(NOW.getTime() / 1_000);
const ALIGNED = NOW_S - (NOW_S % 300);

const config = {
  historyUpstreamBase: "https://example.test/api/v2",
  historyUpstreamTimeoutMs: 1_000,
  maxUpstreamResponseBytes: 256 * 1_024,
  historyFreshTtlMs: 300_000,
  historyStaleTtlMs: 3_600_000,
};

const quoteService = (pool = POOL) => ({ getQuote: async () => ({ source: { pairAddress: pool } }) });

function payload(list, mint = BERT.mint) {
  return { data: { attributes: { ohlcv_list: list } }, meta: { base: { address: mint } } };
}

function candle(time, close) {
  return [time, close, close, close, close, 10];
}

test("returns oldest-first closes for the selected pool", async () => {
  let requested;
  const fetchImpl = async (url) => {
    requested = new URL(url);
    return Response.json(payload([candle(ALIGNED, 0.0153), candle(ALIGNED - 300, 0.0151), candle(ALIGNED - 600, 0.015)]));
  };
  const service = new HistoryService({ quoteService: quoteService(), fetchImpl, config, clock: () => NOW });
  const history = await service.getHistory();

  assert.equal(requested.pathname, `/api/v2/networks/solana/pools/${POOL}/ohlcv/minute`);
  assert.equal(requested.searchParams.get("aggregate"), "5");
  assert.equal(requested.searchParams.get("token"), "base");
  assert.deepEqual(history.points, [[ALIGNED - 600, 0.015], [ALIGNED - 300, 0.0151], [ALIGNED, 0.0153]]);
  assert.equal(history.asset.mint, BERT.mint);
  assert.equal(history.source.intervalSeconds, 300);
  assert.equal(history.meta.freshness, "fresh");
});

test("rejects history for a different token", () => {
  assert.throws(() => normalizeOhlcv(payload([candle(ALIGNED, 1), candle(ALIGNED - 300, 1)], "So11111111111111111111111111111111111111112"), NOW), /BERT mint/);
});

test("drops malformed, misaligned, out-of-window and non-positive candles", () => {
  const points = normalizeOhlcv(payload([
    candle(ALIGNED, 0.02),
    candle(ALIGNED - 300, 0.019),
    candle(ALIGNED - 301, 0.5),
    candle(ALIGNED - 25 * 3_600, 0.5),
    candle(ALIGNED + 3_600, 0.5),
    candle(ALIGNED - 600, 0),
    candle(ALIGNED - 900, Number.NaN),
    [ALIGNED - 1_200, 1],
    "junk",
  ]), NOW);
  assert.deepEqual(points, [[ALIGNED - 300, 0.019], [ALIGNED, 0.02]]);
});

test("refuses a pool address that is not a Solana address", async () => {
  const service = new HistoryService({ quoteService: quoteService("../../evil"), fetchImpl: async () => assert.fail("fetched"), config, clock: () => NOW });
  await assert.rejects(service.getHistory(), /Solana address/);
});

test("answers from cache while a slow refresh runs in the background", async () => {
  let now = NOW;
  let release;
  let calls = 0;
  const fetchImpl = async () => {
    calls += 1;
    if (calls === 2) await new Promise((resolve) => { release = resolve; });
    return Response.json(payload([candle(ALIGNED, calls), candle(ALIGNED - 300, 1)]));
  };
  const service = new HistoryService({ quoteService: quoteService(), fetchImpl, config, clock: () => now });
  await service.getHistory();

  now = new Date(NOW.getTime() + 6 * 60_000);
  const served = await service.getHistory(); // Must not wait for the pending upstream call.
  assert.equal(served.meta.freshness, "stale");
  assert.equal(served.points.at(-1)[1], 1);
  assert.equal(calls, 2);

  release();
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));
  const refreshed = await service.getHistory();
  assert.equal(refreshed.meta.freshness, "fresh");
  assert.equal(refreshed.points.at(-1)[1], 2);
});

test("caches fresh history and serves it as stale when the refresh fails", async () => {
  let now = NOW;
  let calls = 0;
  let fail = false;
  const fetchImpl = async () => {
    calls += 1;
    if (fail) throw new Error("network down");
    return Response.json(payload([candle(ALIGNED, 1), candle(ALIGNED - 300, 2)]));
  };
  const service = new HistoryService({ quoteService: quoteService(), fetchImpl, config, clock: () => now });

  await service.getHistory();
  await service.getHistory();
  assert.equal(calls, 1);

  now = new Date(NOW.getTime() + 10 * 60_000);
  fail = true;
  const stale = await service.getHistory();
  assert.equal(stale.meta.freshness, "stale");
  assert.equal(stale.meta.ageSeconds, 600);
  await new Promise((resolve) => setImmediate(resolve));
  assert.match((await service.getHistory()).meta.warning, /network down/);

  now = new Date(NOW.getTime() + 61 * 60_000);
  await assert.rejects(service.getHistory(), /network down/);
});

test("serves history over HTTP and reports 503 when unavailable", async () => {
  const ok = { getHistory: async () => ({ points: [[1, 2]], meta: { freshness: "fresh" } }) };
  const down = { getHistory: async () => { throw new Error("down"); } };
  for (const [historyService, status] of [[ok, 200], [down, 503]]) {
    const server = createApp({ quoteService: quoteService(), historyService, logger: { error() {} } });
    await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
    try {
      const response = await fetch(`http://127.0.0.1:${server.address().port}/v1/bert/history`);
      assert.equal(response.status, status);
      if (status === 200) assert.match(response.headers.get("cache-control"), /max-age=120/);
      else assert.equal(response.headers.get("cache-control"), "no-store");
    } finally {
      await new Promise((resolve) => server.close(resolve));
    }
  }
});
