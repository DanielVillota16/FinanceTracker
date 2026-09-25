# FinanceTracker

Private Android app (single user, sideloaded) for precise manual recording of
income/expenses, with automatic detection from bank SMS and app notifications.
UI in English, amounts in COP (exact `Long` pesos — never floating point).

## Architecture (offline-first)

```text
SMS / Notification
        ↓
   SourceEvent            raw evidence, never deleted, local-only
        ↓
     Parser               deterministic regex rules, no network/AI
        ↓
ParsedTransaction ─→ PurchaseCorrelation / TransferMatcher (evidence-scored)
        ↓
TransactionCandidate     one logical movement, 1..n source events
        ↓
  Pending Review ─→ user confirms/edits/dismisses
        ↓
Official Transaction ─→ Room (source of truth) ─→ Supabase backup (opt-in)
```

Transfers (`Bancolombia → Nequi`) are structurally distinct and never counted
as income/expenses. Automatic detections never become official records without
explicit confirmation; weak evidence stays separate for the user to resolve.

## Setup

1. Open in Android Studio (or `./gradlew :app:assembleDebug`).
2. **Sync is optional.** Without keys the app is fully offline. To enable
   remote backup, add to gitignored `local.properties`:
   ```properties
   SUPABASE_URL=https://<project>.supabase.co
   SUPABASE_KEY=<publishable key — never the service-role key>
   ```
3. Run `supabase/migrations/20260925000000_finance_tracker_sync.sql` in the
   Supabase SQL editor (tables + owner-only RLS). Enable Email auth (disable
   "Confirm email" or confirm the address).
4. In the app: grant SMS access and notification access from Settings, run a
   historical import, then sign in and Sync now.

Only transactions, accounts, tags and their links sync. Raw SMS/notification
text, source events and candidates never leave the device.

## Testing a detection without spending money

Settings → Test ingestion: paste a bank SMS or a `title + newline + text`
notification body. Re-ingesting is idempotent (reports Duplicate).

## Tests

`./gradlew :app:testDebugUnitTest` — pure JVM tests (parsers, COP amounts,
correlation/matching, fingerprinting, sync decisions, DTOs) plus Robolectric
suites over a real database (migration chain 1→5, idempotent ingestion,
2-events→1-candidate→1-transaction). Room schemas are exported to
`app/schemas/` and migrations are explicit — no destructive fallback.

## Layout

```text
domain/model|parser|correlation|usecase|repository   pure business logic
data/local(entity|dao|database)                      Room (source of truth)
data/remote/supabase                                 client, auth, sync engine
data/repository                                      Room-backed implementations
service/sms|notification                             thin system forwarders
ui/screens|components|navigation                     Compose + Material 3
```
