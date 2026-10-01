import { BERT, CONFIG } from "./config.js";
import { UpstreamError, readJsonWithLimit, safeError } from "./quote-service.js";

const SOLANA_ADDRESS = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/;
const INTERVAL_SECONDS = 300;
const WINDOW_SECONDS = 24 * 60 * 60;
const MAX_POINTS = WINDOW_SECONDS / INTERVAL_SECONDS + 1;

/**
 * 24 hours of 5-minute closes for the pool the quote service currently selects, so a fresh install
 * shows a real chart instead of waiting for on-device observations to accumulate.
 */
export class HistoryService {
  #cached = null;
  #inflight = null;

  constructor({ quoteService, fetchImpl = fetch, config = CONFIG, clock = () => new Date() }) {
    this.quoteService = quoteService;
    this.fetchImpl = fetchImpl;
    this.config = config;
    this.clock = clock;
  }

  #lastError = null;

  /**
   * Cold GeckoTerminal responses take 10-20 s, so cached history is returned immediately and refreshed in
   * the background once it is older than the fresh TTL. Only an empty or expired cache waits for upstream.
   */
  async getHistory() {
    const now = this.clock();
    if (this.#cached) {
      const age = now - this.#cached.fetchedAt;
      if (age < this.config.historyStaleTtlMs) {
        if (age >= this.config.historyFreshTtlMs) this.#refreshInBackground();
        const fresh = age < this.config.historyFreshTtlMs;
        return envelope(this.#cached.history, fresh ? "fresh" : "stale", now, fresh ? null : this.#lastError);
      }
    }

    const history = await this.#refresh();
    return envelope(history, "fresh", this.clock());
  }

  #refreshInBackground() {
    this.#refresh().catch((error) => {
      this.#lastError = safeError(error);
    });
  }

  async #refresh() {
    if (this.#inflight) return this.#inflight;
    this.#inflight = this.#fetchHistory().then((history) => {
      this.#lastError = null;
      return history;
    }).finally(() => {
      this.#inflight = null;
    });
    return this.#inflight;
  }

  async #fetchHistory() {
    const quote = await this.quoteService.getQuote();
    const pool = quote.source.pairAddress;
    if (typeof pool !== "string" || !SOLANA_ADDRESS.test(pool)) {
      throw new UpstreamError("Selected pool address is not a Solana address");
    }

    const url = new URL(`${this.config.historyUpstreamBase}/networks/${BERT.chain}/pools/${pool}/ohlcv/minute`);
    url.search = new URLSearchParams({
      aggregate: String(INTERVAL_SECONDS / 60),
      limit: String(MAX_POINTS),
      currency: "usd",
      token: "base",
    }).toString();

    const response = await this.fetchImpl(url, {
      headers: { accept: "application/json", "user-agent": "bert-widget/0.1" },
      signal: AbortSignal.timeout(this.config.historyUpstreamTimeoutMs),
    });
    if (!response.ok) throw new UpstreamError(`GeckoTerminal returned HTTP ${response.status}`);

    const payload = await readJsonWithLimit(response, this.config.maxUpstreamResponseBytes, "GeckoTerminal");
    const fetchedAt = this.clock();
    const history = {
      asset: { ...BERT },
      source: { name: "geckoterminal", pairAddress: pool, intervalSeconds: INTERVAL_SECONDS },
      points: normalizeOhlcv(payload, fetchedAt),
      fetchedAt: fetchedAt.toISOString(),
    };
    this.#cached = { history, fetchedAt };
    return history;
  }
}

/** Validated [epochSeconds, closeUsd] pairs, oldest first, inside the trailing 24-hour window. */
export function normalizeOhlcv(payload, now) {
  if (payload?.meta?.base?.address !== BERT.mint) {
    throw new UpstreamError("GeckoTerminal history is not for the BERT mint");
  }
  const list = payload?.data?.attributes?.ohlcv_list;
  if (!Array.isArray(list) || list.length > 1_000) {
    throw new UpstreamError("GeckoTerminal history has no candle list");
  }

  const nowSeconds = Math.floor(now.getTime() / 1_000);
  const byTime = new Map();
  for (const candle of list) {
    if (!Array.isArray(candle) || candle.length < 5) continue;
    const [time, , , , close] = candle;
    if (!Number.isSafeInteger(time) || time % INTERVAL_SECONDS !== 0) continue;
    if (time < nowSeconds - WINDOW_SECONDS || time > nowSeconds + 60) continue;
    if (typeof close !== "number" || !Number.isFinite(close) || close <= 0) continue;
    byTime.set(time, close);
  }

  const points = [...byTime.entries()].sort((a, b) => a[0] - b[0]);
  if (points.length < 2) throw new UpstreamError("GeckoTerminal history has too few valid candles");
  return points;
}

function envelope(history, freshness, now, warning = null) {
  const { fetchedAt, ...body } = history;
  return {
    ...body,
    meta: {
      freshness,
      fetchedAt,
      ageSeconds: Math.max(0, Math.floor((now - new Date(fetchedAt)) / 1_000)),
      ...(warning ? { warning } : {}),
    },
  };
}
