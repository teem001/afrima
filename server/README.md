# AfriSmart Node API

Replaces the Java Spring Boot backend. Same WebSocket contract (`/ws`) and media HTTP routes so `web/` keeps working.

## Database

**Default:** in-memory Postgres via PGlite when `AFRISMART_DB_URL` is unset (or `memory`).

**Durable:** set Postgres env vars:

```
AFRISMART_DB_URL=jdbc:postgresql://HOST:5432/DB?sslmode=require
AFRISMART_DB_USER=...
AFRISMART_DB_PASSWORD=...
AFRISMART_JWT_SECRET=long-random-string
```

## Local

```bash
cd server
npm install
npm run dev   # in-memory DB by default
```

Listens on `PORT` (default `8087`). Point the UI with `VITE_WS_URL=ws://127.0.0.1:8087/ws`.
