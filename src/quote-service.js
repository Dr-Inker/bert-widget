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

    const payload = await readJsonWithLimit(response, this.config.maxUpstreamResponseBytes);
    const observedAt = this.clock();
    const quote = normalizePair(selectBestBertPair(payload), observedAt);
    this.#cached = { quote, fetchedAt: observedAt };
    return quote;
  }
}

async function readJsonWithLimit(response, maxBytes) {
  const declaredLength = response.headers.get("content-length");
  if (declaredLength !== null) {
    const length = Number(declaredLength);
    if (!Number.isSafeInteger(length) || length < 0 || length > maxBytes) {
      throw new UpstreamError("DEX Screener response exceeded the size limit");
    }
  }

  if (!response.body) throw new UpstreamError("DEX Screener returned an empty response");
  const reader = response.body.getReader();
  const chunks = [];
  let received = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      received += value.byteLength;
      if (received > maxBytes) {
        await reader.cancel("response too large");
        throw new UpstreamError("DEX Screener response exceeded the size limit");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }

  const bytes = new Uint8Array(received);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
  } catch (error) {
    throw new UpstreamError(error instanceof SyntaxError ? "DEX Screener returned invalid JSON" : "DEX Screener returned invalid UTF-8");
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
