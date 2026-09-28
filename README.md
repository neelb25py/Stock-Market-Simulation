# Live Multiplayer Stock-Market Competition

A Spring Boot + React platform for running a fair, real-time trading competition with one shared simulated market, 10–20 teams, 100 stocks, scheduled administrator news, live portfolios, and a return-ranked leaderboard. The original accelerated simulator remains available under the existing APIs and at `/legacy` in the frontend.

## Architecture

- **Backend:** Java 17, Spring Boot 3, JPA, MySQL, Flyway, REST, and Server-Sent Events.
- **Frontend:** React 19, TypeScript, Vite, Chart.js. Organizer console at `/admin`; team login at `/team/login`.
- **Persistence:** MySQL tables for competitions, teams, holdings, trades, stocks, reference/live price points, news impacts, audit events, and final results.
- **Market clock:** the server owns elapsed time. The default one-second tick equals one competition second. Pause freezes ticks, news, and trading.
- **Live delivery:** orders use authenticated REST calls; ticks, news and leaderboard changes are pushed through SSE.

The competition market is separate from the legacy accelerated simulator. All teams see one shared live price per stock.

## Pricing model

Before lock, the server generates and persists the complete seeded independent path. At each live tick it applies the reference **return** to the current live price; it never snaps the live price back to the reference price.

```text
liveReturn = referenceReturn + newsReturn + participantOrderFlowReturn
newLivePrice = max(₹0.01, previousLivePrice × (1 + liveReturn))
```

- `referenceReturn` is deterministic from the locked seed and stock volatility and exists with zero trades.
- `newsReturn` is an admin-defined instant shock or a correctly compounded ramp. A +6% 60-second ramp uses `expm1(log1p(0.06)/60)` per tick, so the news component compounds to +6%.
- `participantOrderFlowReturn = clamp(tanh(decayedNetNotional / liquidityScale) × 0.0005, ±0.0005)`. The 30-second flow window decays exponentially. The same trade moves a liquid mega-cap less than a small-cap.

Each saved live point keeps the three components separately for organizer diagnostics. Team endpoints expose only current prices and live history already observed; future reference points and unreleased news are admin-only.

## Requirements

- Docker Desktop, or Java 17 + Maven 3.9 + Node 22 + MySQL 8
- Ports `3000`, `8000`, and `3307` available for the default Compose setup

## Run with Docker

```bash
export COMPETITION_ADMIN_KEY='replace-with-a-long-random-value'
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend: http://localhost:8000
- Swagger/OpenAPI: http://localhost:8000/swagger-ui/index.html
- MySQL host port: `3307`

The default development admin key is `change-me-now`; override it for every real event. Flyway creates the competition schema automatically. MySQL data persists in the Compose-managed container until it is removed.

## Run locally

```bash
cd stock-market-service
COMPETITION_ADMIN_KEY='replace-me' mvn spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
VITE_API_URL=http://localhost:8000/api npm run dev
```

Configure `spring.datasource.*` in `stock-market-service/src/main/resources/application.properties` for the local MySQL instance.

## Organizer workflow

1. Open `/admin` and enter `COMPETITION_ADMIN_KEY`.
2. Create a competition. Defaults are 3,600 seconds, one-second ticks, and ₹1,000,000 per team.
3. Click **Load 100 defaults**. Optionally click **Fetch latest**; failed symbols retain their prior values and are reported. For offline/reliable setup, import CSV through `POST /api/admin/competitions/{id}/stocks/import` using columns `ticker,companyName,sector,marketCap,volatility,liquidity,startingPrice`.
4. Create 10–20 teams and securely distribute the one-time displayed access codes. Codes and active session tokens are stored only as SHA-256 hashes.
5. Generate the reference market. A normal event creates exactly `stock count × duration` predetermined points (360,000 for 100 × 3,600).
6. Schedule news by elapsed second, impact style (`RAMP` or `INSTANT`), duration, and per-stock percentages. Teams see only released headline/description, not impact values.
7. Review and **Lock**. Stocks, budgets, seed, reference path and news become immutable, and the server records a SHA-256 configuration hash.
8. **Start**, **Pause/Resume**, or **End** from the console. Paused and finished markets reject orders.
9. Monitor live prices, news, and leaderboard. At duration the server finishes automatically, rejects new orders, values all portfolios, and persists frozen standings.
10. Download leaderboard CSV from `/api/admin/competitions/{id}/exports/leaderboard.csv` with the `X-Admin-Key` header.

Administrative changes and emergency price corrections are written to the audit log. Quote-provider failures never erase previously loaded prices; CSV remains the deterministic fallback.

## Team workflow

1. Open `/team/login`.
2. Enter the competition ID, exact team name, and organizer-issued access code.
3. Search the shared 100-stock market, open a live chart, and enter any positive whole-share quantity. There is no arbitrary minimum notional.
4. Buy orders require enough cash; sell orders require enough shares. The server supplies identity, price, timestamp and competition status—client values are never trusted.
5. Watch cash, holdings, P&L, released news and the return-ranked leaderboard update live.

Short selling is disabled by default. Different starting budgets are supported, so ranking uses return percentage rather than absolute portfolio value.

## Tests and validation

```bash
cd stock-market-service && mvn clean test
cd ../frontend && npm ci && npm run build && npm run lint
```

Backend tests cover state transitions, deterministic reference generation, hidden future history, authentication, buy/sell accounting, pause/finish rejection, ramp compounding, bounded/liquidity-sensitive flow, and a 100-stock × 3,600-tick logical market run without waiting an hour.

## API summary

Admin requests use `X-Admin-Key`. Team requests use `Authorization: Bearer <token>`.

- `POST /api/admin/competitions`
- `POST /api/admin/competitions/{id}/stocks/defaults`
- `POST /api/admin/competitions/{id}/stocks/fetch-prices`
- `POST /api/admin/competitions/{id}/stocks/import`
- `POST /api/admin/competitions/{id}/teams/bulk`
- `POST /api/admin/competitions/{id}/reference/generate`
- `POST /api/admin/competitions/{id}/news`
- `POST /api/admin/competitions/{id}/{lock|start|pause|resume|end}`
- `POST /api/team/login`
- `GET /api/competitions/{id}/market`
- `POST /api/competitions/{id}/orders/{buy|sell}`
- `GET /api/competitions/{id}/{portfolio|trades|news|leaderboard}`
- `GET /api/competitions/{id}/stocks/{ticker}/history`
- `GET /api/competitions/{id}/events?token=...`

## Operational limits

This is a competition simulator, not an exchange or brokerage. Market orders execute at the current server price without an institutional order book. The configured quote fetcher uses an external public endpoint only before lock; availability is not guaranteed. Use reviewed CSV snapshots for event-day reliability.
