import { createServer } from "node:http";
import { BERT, CONFIG } from "./config.js";
import { HistoryService } from "./history-service.js";
import { QuoteService } from "./quote-service.js";

export function createApp({
  quoteService = new QuoteService(),
  historyService = new HistoryService({ quoteService }),
  logger = console,
} = {}) {
  return createServer(async (request, response) => {
    const url = new URL(request.url ?? "/", "http://localhost");

    if (request.method === "GET" && url.pathname === "/healthz") {
      return json(response, 200, { status: "ok", asset: BERT.mint });
    }

    if (request.method === "GET" && url.pathname === "/v1/bert/quote") {
      try {
        const quote = await quoteService.getQuote();
        return json(response, 200, quote, {
          "cache-control": "public, max-age=30, stale-if-error=300",
        });
      } catch (error) {
        logger.error("quote request failed", error);
        return json(response, 503, {
          error: "quote_unavailable",
          message: "BERT market data is temporarily unavailable",
        });
      }
    }

    if (request.method === "GET" && url.pathname === "/v1/bert/history") {
      try {
        const history = await historyService.getHistory();
        return json(response, 200, history, {
          "cache-control": "public, max-age=120, stale-if-error=1800",
        });
      } catch (error) {
        logger.error("history request failed", error);
        return json(response, 503, {
          error: "history_unavailable",
          message: "BERT price history is temporarily unavailable",
        });
      }
    }

    return json(response, 404, { error: "not_found" });
  });
}

function json(response, status, body, extraHeaders = {}) {
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "x-content-type-options": "nosniff",
    // The service owns Cache-Control (nginx must not add another); errors are never cacheable.
    "cache-control": "no-store",
    ...extraHeaders,
  });
  response.end(`${JSON.stringify(body)}\n`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const quoteService = new QuoteService();
  const historyService = new HistoryService({ quoteService });
  const server = createApp({ quoteService, historyService });
  // Warm the history cache so the first phone request does not wait on a cold upstream.
  historyService.getHistory().catch((error) => console.error("history warm-up failed", error));
  server.listen(CONFIG.port, CONFIG.host, () => {
    console.log(`BERT quote service listening on http://${CONFIG.host}:${CONFIG.port}`);
  });
}
