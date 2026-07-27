import { createServer } from "node:http";
import { BERT, CONFIG } from "./config.js";
import { QuoteService } from "./quote-service.js";

export function createApp({ quoteService = new QuoteService(), logger = console } = {}) {
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

    return json(response, 404, { error: "not_found" });
  });
}

function json(response, status, body, extraHeaders = {}) {
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "x-content-type-options": "nosniff",
    ...extraHeaders,
  });
  response.end(`${JSON.stringify(body)}\n`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const server = createApp();
  server.listen(CONFIG.port, CONFIG.host, () => {
    console.log(`BERT quote service listening on http://${CONFIG.host}:${CONFIG.port}`);
  });
}
