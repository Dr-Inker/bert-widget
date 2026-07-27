import { CONFIG } from "./config.js";
import { normalizePair, selectBestBertPair } from "./normalize.js";

export class QuoteService {
  #cached = null;
  #inflight = null;

  constructor({ fetchImpl = fetch, config = CONFIG, clock = () => new Date() } = {}) {
    this.fetchImpl = fetchImpl;
    this.config = config;
    this.clock = clock;
  }

  async getQuote() {
    const now = this.clock();
    if (this.#cached && now - this.#cached.fetchedAt < this.config.freshTtlMs) {
      return envelope(this.#cached.quote, "fresh", now);
    }

    try {
      const quote = await this.#refresh();
      return envelope(quote, "fresh", this.clock());
    } catch (error) {
      const fallbackNow = this.clock();
      if (this.#cached && fallbackNow - this.#cached.fetchedAt < this.config.staleTtlMs) {
        return envelope(this.#cached.quote, "stale", fallbackNow, safeError(error));
      }
      throw error;
    }
  }

  async #refresh() {
    if (this.#inflight) return this.#inflight;

    this.#inflight = this.#fetchQuote().finally(() => {
      this.#inflight = null;
    });
    return this.#inflight;
  }

  async #fetchQuote() {
    const response = await this.fetchImpl(this.config.upstreamUrl, {
      headers: { accept: "application/json", "user-agent": "bert-widget/0.1" },
      signal: AbortSignal.timeout(this.config.upstreamTimeoutMs),
    });

    if (!response.ok) {
      throw new UpstreamError(`DEX Screener returned HTTP ${response.status}`);
    }

    const payload = await response.json();
    const observedAt = this.clock();
    const quote = normalizePair(selectBestBertPair(payload), observedAt);
    this.#cached = { quote, fetchedAt: observedAt };
    return quote;
  }
}

function envelope(quote, freshness, now, warning = null) {
  const observedAt = new Date(quote.source.observedAt);
  return {
    ...quote,
    meta: {
      freshness,
      ageSeconds: Math.max(0, Math.floor((now - observedAt) / 1_000)),
      ...(warning ? { warning } : {}),
    },
  };
}

function safeError(error) {
  if (error instanceof Error) return error.message.slice(0, 160);
  return "Upstream refresh failed";
}

export class UpstreamError extends Error {
  constructor(message) {
    super(message);
    this.name = "UpstreamError";
  }
}
