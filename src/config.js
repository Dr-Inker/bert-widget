export const BERT = Object.freeze({
  chain: "solana",
  mint: "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump",
  name: "Bertram The Pomeranian",
  symbol: "Bert",
});

export const CONFIG = Object.freeze({
  host: process.env.HOST || "127.0.0.1",
  port: readPositiveInteger("PORT", 8787),
  upstreamTimeoutMs: readPositiveInteger("UPSTREAM_TIMEOUT_MS", 5_000),
  freshTtlMs: readPositiveInteger("FRESH_TTL_MS", 60_000),
  staleTtlMs: readPositiveInteger("STALE_TTL_MS", 30 * 60_000),
  upstreamUrl: `https://api.dexscreener.com/token-pairs/v1/${BERT.chain}/${BERT.mint}`,
});

function readPositiveInteger(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined) return fallback;

  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return value;
}
