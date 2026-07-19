# Daftar · دفتر — For Sarafs

A native Android ledger app for Afghanistan's money-changing trade (sarafi): hawala
transfers, customer accounts, FX trading with cost-basis P&L, partner settlements,
and printable statements. Built end-to-end from the high-fidelity HTML prototype
(`daftarapp_12.html`) and integrated with the Daftar REST API.

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3, light-only "paper & ink" theme)
- **MVVM** with unidirectional data flow (`StateFlow` → `collectAsStateWithLifecycle`)
- **Clean Architecture** — `domain` / `data` / `ui` layers in a single module
- **Hilt** for dependency injection
- **Navigation Compose** for the screen graph
- Bundled fonts: Fraunces (display serif), Inter (body), JetBrains Mono (labels/codes),
  Noto Naskh Arabic (Pashto accents)

## Architecture

```
com.daftar.app
├── core/           # Formatters, TimeProvider — no Android dependencies
├── domain/
│   ├── model/      # Asset, Hawala, Counterparty, Customer, FxTrade, Investment,
│   │               # RateBook, CashDrawer, LedgerEntry, PnlReport, …
│   ├── repository/ # Interfaces the UI depends on (SOLID: DIP)
│   └── usecase/    # Business rules: positions, balances, cost basis, P&L,
│                   # hawala issuance, FX booking, settlements, …
├── data/
│   ├── seed/       # Prototype demo dataset
│   ├── remote/     # HTTP transport, typed API facade, DTO mappers, sync, mutations
│   └── repository/ # StateFlow-backed read cache hydrated from the server
├── di/             # Hilt modules binding data → domain
└── ui/
    ├── theme/      # Palette, typography, shapes (mirrors the prototype's CSS vars)
    ├── common/     # Reusable primitives (MonoLabel, chips, inputs, sheets, toast)
    ├── components/ # App-specific shared pieces (ledger feed, badges, detail rows)
    ├── navigation/ # Route table
    └── feature/    # One package per screen: ViewModel + composables
```

### Screens implemented (parity with the prototype)

| Area | Screens |
|---|---|
| Tabs | Home (cash drawer card, ledger preview, New Entry FAB) · General Ledger (today ribbon, filters, search) · Hawalas (volume, filters, grouped list) · Accounts (Customers / Partners sub-tabs) · Shop |
| Details | Partner detail · Customer detail · Customer entry detail (balance impact, conversion, receipt) · Hawala detail (pickup code, timeline, mark paid) |
| Entry flows | New Entry chooser · New Hawala (cash/account sender, percent/fixed commission, confirm) · Customer entry (deposit/withdrawal/charge/advance, cross-currency conversion) · FX trade (canonical rate quoting, P&L preview, confirm) · Settlement (manual rate sheet, net-off) |
| Ledgers & reports | FX ledger (open positions, avg cost, day-grouped history) · P&L (FX + commissions + revaluation) · Investments (equity log, ROI) |
| Statements | Customer statement · Partner statement · Business statement (period + type filters) |
| Shop | Count Cash (variance) · Live Rates + Update Rates · Asset management (currencies & metals) · Default currency · Initial setup (first-run welcome) |

### Domain logic ported from the prototype

- Partner positions (`send +`, `receive −`, signed settlement entries; pending excluded)
- Customer balances and per-entry running balances
- Weighted-average FX cost basis, open positions, realized/unrealized P&L
- P&L aggregation (FX realized + hawala commissions + rate-move revaluation)
- Sequential 6-digit pickup codes; percent/fixed commissions
- Cross-currency conversion bridging through AFN; canonical pair quoting
  ("1 USD = 71.80 AFN", never "1 AFN = 0.0139 USD")
- Cash drawer movements from FX legs, investments, counts, and initial setup

## Building

```bash
./gradlew :app:assembleDebug     # requires Android SDK 35
./gradlew :app:testDebugUnitTest # domain + API transport/contract tests
```

> The cloud sandbox this project was authored in blocks `dl.google.com`, so the
> Android Gradle Plugin and androidx artifacts could not be resolved there. The
> domain/data/core layers and their unit tests were compiled and run green with
> the standalone Kotlin 2.0.21 compiler; the full app build should be run locally
> or in CI with normal network access.

## API integration

The app uses the backend's `/api/v1` contract through a single authenticated
transport and typed `DaftarApi` facade. Domain and UI code depend on repository
ports; backend DTOs stay in `data/remote`. Successful writes refresh the read
cache so server-generated IDs, linked ledger rows, balances, and P&L remain
authoritative.

The emulator default is `http://10.0.2.2:3000/`. Override it for a device or
deployed environment:

```bash
./gradlew :app:assembleDebug -PDAFTAR_API_BASE_URL=https://api.example.com/
```

The deterministic suite verifies every documented method/path offline. An
optional live smoke test registers a fresh account and decodes the initial API
snapshot when a backend is available:

```bash
DAFTAR_INTEGRATION_BASE_URL=http://localhost:3000/ ./gradlew :app:testDebugUnitTest
```
