# AfriSmart API

Phase 1 backend for the agentic smart-city marketplace: identity, Lagos geo search, catalog, inventory, checkout with an order hash, and AI agents that call marketplace tools.

## Run

No Docker needed. The app uses an embedded H2 database in PostgreSQL mode.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw spring-boot:run
```

API: http://localhost:8087  
Swagger: http://localhost:8087/swagger-ui.html  
H2 console: http://localhost:8087/h2-console (JDBC URL `jdbc:h2:mem:afrismart`, user `sa`, empty password)

The default database is **in memory**: it is re-created and re-seeded on every start. To keep data between restarts, use the `file` profile, which writes to `./data/`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=file
```

If a migration changes and the file database refuses to start (Flyway checksum mismatch), delete `./data/`.

Any JDBC URL can be supplied with `AFRISMART_DB_URL`. Use `SERVER_PORT` to change the port.

## Deploy to Vercel (Container Runtime, Beta)

Vercel has no JVM runtime, but its [Container Runtime](https://vercel.com/docs/functions/runtimes/container) runs any OCI image from a `Dockerfile.vercel` — that's included here (multi-stage Maven build, `PORT` is respected).

1. [vercel.com/new](https://vercel.com/new) → import `teem001/afrima` (no framework preset needed; `Dockerfile.vercel` is auto-detected).
2. Set environment variables:
   - `OPENAI_API_KEY` — enables the AI assistant (optional)
   - `AFRISMART_JWT_SECRET` — a long random string (recommended; otherwise the local dev default is used)
3. Deploy. Your API/WebSocket base will be `https://<project>.vercel.app` and `wss://<project>.vercel.app/ws`.

**Demo caveats on Vercel:** the container scales to zero after ~5 min idle (cold start on the next request), a WebSocket connection is pinned to one Function instance (max connection duration follows your plan's Function limits — the web client reconnects and re-authenticates automatically), and the in-memory H2 database is per-instance and reseeded on restart, so orders/registrations don't survive a scale-down. For production data, set `AFRISMART_DB_URL` to an external Postgres and expect to move realtime fan-out to a shared store. The included `Dockerfile` targets the same image on Render/Railway/Fly without these limits.

## Demo accounts

Password for all: `Password123!`

| Email | Role |
|---|---|
| ada@afrismart.ng | consumer |
| yaba@afrismart.ng | merchant |
| surulere@afrismart.ng | merchant |
| ikeja@afrismart.ng | merchant |

Hero search (from Yaba / UNILAG):

```bash
curl -s "http://localhost:8087/api/v1/search?q=pack%20of%20pens&lat=6.5158&lng=3.3791"
```

Surulere Stationers is seeded with **zero pens** so rerouting has a preferred shop that is out of stock.

## AI agents

The `ai` module follows the same pattern as the Lift backend's `liftai` module: tools are Spring beans implementing `AiTool`, `AiToolRegistry` exposes them to the model, and the model engine is chosen by config. Tools are filtered by the signed-in user's role, not by a mode.

| Tool | Role | What it does |
|---|---|---|
| `SEARCH_PRODUCTS_NEARBY` | consumer | Stores near the user with the product in stock, nearest first |
| `COMPARE_PRICES` | consumer | Price range plus cheapest, nearest, and best-value picks |
| `FIND_ALTERNATIVE_STORES` | consumer | Reroutes away from an out-of-stock store (searches 2× the radius) |
| `GET_STORE_DETAILS` | both | One store's hours, distance, and stock |
| `PLACE_ORDER` | consumer | Places an order (the prompt requires explicit confirmation first) |
| `GET_MY_ORDERS` | consumer | Recent orders |
| `GET_MY_INVENTORY` | merchant | Stock and prices across the merchant's stores |
| `GET_LOW_STOCK_ITEMS` | merchant | Items at or below a threshold, with 7-day sales and a suggested restock |
| `GET_INCOMING_ORDERS` | merchant | Orders at the merchant's stores, by status |
| `UPDATE_STOCK` | merchant | Sets quantity and optionally price (the prompt requires confirmation first) |

### Configure the model

Chat uses any OpenAI-compatible Chat Completions API with tool calling:

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_MODEL=gpt-4o-mini            # optional
export OPENAI_BASE_URL=https://api.openai.com/v1   # optional: point at Groq, OpenRouter, Ollama, etc.
./mvnw spring-boot:run
```

Without a key, `/api/v1/ai/chat` returns `503 AI_NOT_CONFIGURED`. Every other AI endpoint, including direct tool calls, still works.

### Endpoints

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/ai/status` | Public. Engine, whether it is configured, model |
| GET | `/api/v1/ai/tools` | Tool definitions available to the caller's role |
| POST | `/api/v1/ai/chat` | `{ "message", "conversationId"?, "latitude"?, "longitude"? }` |
| POST | `/api/v1/ai/tools/{name}/invoke` | `{ "arguments": {...}, "latitude"?, "longitude"? }`. Runs one tool without the model |
| GET | `/api/v1/ai/conversations` | The caller's conversations |
| GET | `/api/v1/ai/conversations/{id}/messages` | Transcript plus tool calls |

The client should send the device location with each chat message. Tools always use it in preference to coordinates the model supplies. Earlier tool results are fed back to the model on later turns, so it can reuse store and product ids.

Try the reroute without a model:

```bash
TOKEN=$(curl -s -X POST localhost:8087/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ada@afrismart.ng","password":"Password123!"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s -X POST localhost:8087/api/v1/ai/tools/FIND_ALTERNATIVE_STORES/invoke \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"arguments":{"query":"biro","quantity":3,"excludeStoreName":"Surulere"},"latitude":6.5158,"longitude":3.3791}'
```

## Realtime WebSocket

The web app talks to the backend over one WebSocket at `/ws`; the REST endpoints stay for Swagger, curl, and tests. Both transports call the same controllers, so validation, role checks, and error codes are identical.

Client → server: `{ "id": "1", "type": "search", "payload": { ... } }`  
Server → client: `{ "type": "response", "id": "1", "ok": true, "data": ... }` or `{ ..., "ok": false, "error": { "code", "message", "status", "details" } }`  
Server push: `{ "type": "event", "event": "order.placed", "data": ... }`

Sign in on the socket with `auth.login`, `auth.register`, or `auth.resume` (`{ "token" }`, for a saved token after a reconnect). The token is re-checked on every request, so an expired session gets `401` without a reconnect.

| Type | Payload |
|---|---|
| `ping` | none, answers `"pong"` |
| `auth.login` / `auth.register` / `auth.resume` / `auth.logout`, `me` | same bodies as REST |
| `search` | `{ q, lat, lng, radiusMeters? }` |
| `catalog.products`, `stores.list`, `stores.mine` | none |
| `stores.get`, `inventory.list` | `{ storeId }` |
| `inventory.upsert` | `{ storeId, productId, quantity, unitPriceKobo }` |
| `orders.place` | `{ storeId, items: [{ productId, quantity }] }` |
| `orders.mine`, `orders.merchant` | none |
| `orders.get`, `orders.accept` | `{ orderId }` |
| `ai.status` | none |
| `ai.chat` | `{ message, conversationId?, latitude?, longitude?, context? }` |

`ai.chat` streams `ai.progress` events tagged with the request id (`conversation`, `thinking`, `tool_started`, `tool_finished`) before its response. `context` is what the app is showing: `{ screen, searchQuery, locationLabel, radiusMeters, storeName, cart: { storeName, items: [{ productName, quantity, unitPrice }] } }`. It goes into the system prompt; a `searchQuery` tells the agent the message came from the search bar and should be answered as a shopping request.

Pushes, sent after the transaction commits:

| Event | Sent to |
|---|---|
| `order.placed`, `order.updated` | the consumer and the store's merchant |
| `stock.changed` | everyone (`{ storeId, productId, quantity, unitPriceKobo }`) |

## Web app

The React app lives in `web/` and is deployed to Vercel.

```bash
cd web
npm install
npm run dev        # http://localhost:5173, proxies /ws to localhost:8087
```

On Vercel, set `VITE_WS_URL` to the backend socket, e.g. `wss://api.example.com/ws` (Vercel rewrites can't proxy WebSockets). The backend accepts any origin on `/ws` because it authenticates with the token sent in a message, not cookies.

## Tests

```bash
./mvnw test
```

`AfriSmartIntegrationTest` boots the full app on H2 and drives the agent loop with `ScriptedModelEngine`, a fake model that replays queued tool calls, so no API key is needed. `RealtimeIntegrationTest` does the same over a real WebSocket: auth, search-bar chat with progress events, and order/stock pushes.
