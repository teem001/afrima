# AfriSmart Node API

Replaces the Java Spring Boot backend. Same WebSocket contract (`/ws`) and media HTTP routes so `web/` keeps working.

## Env (Docker / Vercel)

```
AFRISMART_DB_URL=jdbc:postgresql://HOST:5432/DB?sslmode=require
AFRISMART_DB_USER=...
AFRISMART_DB_PASSWORD=...
AFRISMART_JWT_SECRET=long-random-string
```

`postgresql://USER:PASS@HOST:5432/DB?sslmode=require` also works as `AFRISMART_DB_URL`.

## Local

```bash
cd server
npm install
export AFRISMART_DB_URL=postgresql://postgres:postgres@127.0.0.1:5432/afrismart
export AFRISMART_JWT_SECRET=dev-secret
npm run dev
```

Listens on `PORT` (default `8087`). Point the UI with `VITE_WS_URL=ws://127.0.0.1:8087/ws`.
