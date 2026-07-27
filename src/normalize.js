import { BERT } from "./config.js";

export function selectBestBertPair(payload) {
  if (!Array.isArray(payload)) {
    throw new QuoteDataError("Upstream response is not an array");
  }

  const candidates = payload.filter((pair) => {
    return (
      pair?.chainId === BERT.chain &&
      pair?.baseToken?.address === BERT.mint &&
      pair?.baseToken?.symbol?.toLowerCase() === BERT.symbol.toLowerCase() &&
      finiteNumber(pair?.priceUsd) !== null
    );
  });

  if (candidates.length === 0) {
    throw new QuoteDataError("No valid BERT base-token pool found");
  }

  return candidates.sort((a, b) => liquidity(b) - liquidity(a))[0];
}

export function normalizePair(pair, observedAt = new Date()) {
  const priceUsd = requiredNumber(pair.priceUsd, "priceUsd");
  if (priceUsd <= 0) throw new QuoteDataError("priceUsd must be positive");

  return {
    asset: { ...BERT },
    quote: {
      priceUsd,
      change24hPct: optionalNumber(pair?.priceChange?.h24),
      marketCapUsd: optionalNumber(pair?.marketCap),
      volume24hUsd: optionalNumber(pair?.volume?.h24),
      liquidityUsd: optionalNumber(pair?.liquidity?.usd),
    },
    source: {
      name: "dexscreener",
      pairAddress: requiredString(pair?.pairAddress, "pairAddress"),
      dex: requiredString(pair?.dexId, "dexId"),
      pairUrl: requiredDexScreenerPairUrl(pair?.url),
      observedAt: observedAt.toISOString(),
    },
  };
}

function liquidity(pair) {
  return finiteNumber(pair?.liquidity?.usd) ?? -1;
}

function requiredNumber(value, field) {
  const parsed = finiteNumber(value);
  if (parsed === null) throw new QuoteDataError(`${field} is missing or invalid`);
  return parsed;
}

function optionalNumber(value) {
  return finiteNumber(value);
}

function finiteNumber(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function requiredString(value, field) {
  if (typeof value !== "string" || value.length === 0) {
    throw new QuoteDataError(`${field} is missing or invalid`);
  }
  return value;
}

function requiredDexScreenerPairUrl(value) {
  const raw = requiredString(value, "url");
  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new QuoteDataError("url is not a valid URL");
  }

  const allowedHost = url.hostname === "dexscreener.com" || url.hostname === "www.dexscreener.com";
  const solanaPairPath = /^\/solana\/[1-9A-HJ-NP-Za-km-z]+\/?$/.test(url.pathname);
  if (
    url.protocol !== "https:" ||
    !allowedHost ||
    url.port !== "" ||
    url.username !== "" ||
    url.password !== "" ||
    url.search !== "" ||
    url.hash !== "" ||
    !solanaPairPath
  ) {
    throw new QuoteDataError("url is not an allowed DEX Screener Solana pair URL");
  }
  return url.href;
}

export class QuoteDataError extends Error {
  constructor(message) {
    super(message);
    this.name = "QuoteDataError";
  }
}
