# FinanceTracker — Final Implementation Plan

## 1. Project Context

Build a private Android application called **FinanceTracker**.

* Package: `doug.financetracker`
* Platform: Android
* Language: Kotlin
* UI: Jetpack Compose + Material 3
* Architecture: layered, offline-first
* Local database: Room
* Remote persistence/sync: Supabase
* UI language: English
* Currency: COP
* Distribution: private/sideloaded application
* User: single user
* Primary purpose: precise manual recording of income and expenses, with automatic transaction detection from supported financial SMS messages and Android notifications.

The application should remain intentionally focused. Do not build a full accounting, budgeting, investment, or financial-planning platform.

---

# 2. Core Financial Model

The application must distinguish between three fundamentally different transaction types:

```text
EXPENSE
Money leaves the user's financial ecosystem.

INCOME
Money enters the user's financial ecosystem from an external party.

TRANSFER
Money moves between accounts owned by the user.
```

This distinction is fundamental because transfers must **not** be counted as income or expenses.

Example:

```text
Bancolombia  -$100,000
Nequi        +$100,000
```

This represents:

```text
TRANSFER
Bancolombia → Nequi
$100,000
```

It is not:

```text
-$100,000 expense
+$100,000 income
```

Future reporting must therefore be able to calculate:

```text
Total Income   = SUM(INCOME)
Total Expenses = SUM(EXPENSE)
Transfers      = SUM(TRANSFER)
```

Transfers are excluded from income/expense totals.

---

# 3. Account Ownership

Introduce a first-class concept of financial accounts.

Initially support:

* Bancolombia
* Nequi
* DAVIbank
* BBVA
* Cash

The model should allow additional accounts later.

Conceptually:

```kotlin
Account(
    id,
    name,
    institution,
    accountType,
    identifierSuffix,
    isOwnedByUser
)
```

Only retain the minimum account identifier necessary, preferably the last four digits or another non-sensitive suffix.

Examples:

```text
Bancolombia ****8494
Nequi
DAVIbank Visa Oro
BBVA ****1234
Cash
```

The `isOwnedByUser` property is important for determining whether a transfer is internal.

---

# 4. Transaction Model

An official transaction should contain structured financial information rather than being tied directly to an SMS or notification.

Conceptually:

```kotlin
Transaction(
    id,
    type,                 // EXPENSE, INCOME, TRANSFER
    amount,
    dateTime,
    description,
    counterparty,
    sourceAccountId,
    destinationAccountId,
    tagIds,
    status,
    createdAt,
    updatedAt
)
```

For transfers:

```text
sourceAccount
destinationAccount
amount
```

should represent the movement explicitly.

For expenses:

```text
sourceAccount
counterparty
amount
description
tags
```

For income:

```text
destinationAccount
counterparty
amount
description
tags
```

Do not force every field to be populated when it is not applicable.

---

# 5. Counterparty vs Description

Keep these concepts separate.

### Counterparty

Who received or sent the money.

Examples:

```text
AIRBNB * HMJYTBHWZW
DLOCAL COLOMBIA SAS
GOOGLE *Google One
BOLD SA*20 DE JU
GUILLERMO ANDRES LOPEZ NARVAEZ
```

### Description

The user's own human-readable explanation.

Examples:

```text
Airbnb trip
Monthly Google One subscription
Lunch
Money received from work
```

### Tags

Reusable classifications.

Examples:

```text
viaje
comida
suscripciones
trabajo
hogar
```

The three concepts should remain independent.

---

# 6. Source → Interpretation → Transaction Pipeline

Never allow an SMS or notification to directly create an official transaction.

Use this pipeline:

```text
SMS / Android Notification
            ↓
       SourceEvent
            ↓
          Parser
            ↓
   ParsedTransaction
            ↓
 Candidate / Duplicate / Transfer Matching
            ↓
      Pending Review
            ↓
       User confirms
            ↓
   Official Transaction
```

This separation is one of the most important architectural decisions in the project.

---

# 7. Source Events

Create a `SourceEvent` entity representing raw evidence received from an external source.

Sources initially include:

```text
SMS
Google Wallet notification
BBVA notification
```

Conceptually:

```kotlin
SourceEvent(
    id,
    sourceType,
    sourceIdentifier,
    receivedAt,
    eventTime,
    rawContent,
    fingerprint,
    parsedStatus
)
```

Examples:

```text
SMS / Bancolombia
Notification / Google Wallet
Notification / BBVA
```

Multiple source events may correspond to the same financial transaction.

For example:

```text
Bancolombia SMS
       +
Google Wallet notification
       ↓
same logical transaction
```

Do not delete either source event.

---

# 8. Idempotency

Source ingestion must be idempotent.

If:

* the same SMS is imported twice,
* the notification listener receives the same notification twice,
* historical SMS import runs again,

the database must not create duplicate `SourceEvent` records.

Create a stable fingerprint based on appropriate normalized source information, such as:

```text
source
package/sender
normalized content
event timestamp
```

Back this up with a database uniqueness constraint where appropriate.

Idempotency must be tested explicitly.

---

# 9. ParsedTransaction

Parsers should produce structured interpretations instead of directly modifying the database.

Conceptually:

```kotlin
ParsedTransaction(
    amount,
    direction,
    transactionKind,
    institution,
    sourceAccountHint,
    destinationAccountHint,
    counterparty,
    timestamp,
    reference,
    confidence,
    warnings
)
```

Where:

```text
direction:
    INCOMING
    OUTGOING

transactionKind:
    EXPENSE
    INCOME
    TRANSFER
    UNKNOWN
```

The parser should be allowed to return uncertainty.

Example:

```text
Possible type:
TRANSFER / EXPENSE

Reason:
Destination account ownership is unknown.
```

That item should go to Pending Review instead of making an unsafe assumption.

---

# 10. Transaction Direction Detection

Recognize Spanish financial language as parsing signals.

Incoming examples:

```text
recibiste
recibiste una transferencia
te enviaron
consignaron
recibiste dinero
```

Outgoing examples:

```text
transferiste
pagaste
compraste
retiraste
enviaste
```

These are parsing signals, not universal rules.

The parser must rely on the complete message format for each institution.

---

# 11. Current Parser Implementations

Create isolated parsers:

```text
TransactionParser
    ├── BancolombiaParser
    ├── NequiParser
    ├── DavibankParser
    ├── BbvaNotificationParser
    └── GoogleWalletParser
```

The exact implementation should be based on the real message formats provided during development.

### Bancolombia examples to support

Purchases:

```text
Compraste $29.000,00 en BOLD SA*20 DE JU con tu T.Deb *0757...
```

Transfers out:

```text
Transferiste $4,500.00 desde tu cuenta *8494...
```

BRE-B transfers:

```text
transferiste $60,000.00 a la llave...
```

QR payments:

```text
pagaste $40,000.00 por codigo QR...
```

ATM withdrawals:

```text
Retiraste $100.000,00 en EXIT_LA70_2...
```

Scheduled bill payments:

```text
informa pago Factura Programada IGS MULTIASISTE...
```

Incoming transfers:

```text
recibiste una transferencia de DLOCAL COLOMBIA SAS por $3,050,707.00...
```

The DLOCAL example must be interpreted as:

```text
INCOME
amount = 3,050,707 COP
institution = Bancolombia
destination account = ****8494
counterparty = DLOCAL COLOMBIA SAS
date = 2026-05-22 08:30
```

### NEQUI

Support examples such as:

```text
NEQUI: Pagaste 1.000,00 en GOOGLE *Google One
NEQUI: Pagaste 9.300,00 en GOOGLE YouTube
```

### DAVIbank

Support examples such as:

```text
DAVIbank: Realizaste transaccion en AIRBNB * HMJYTBHWZW por 492,292...
```

Amounts must correctly handle Colombian/institution-specific formatting.

---

# 12. Money Representation

Do not use floating-point numbers for financial amounts.

Represent COP amounts as integer minor units or an equivalent exact representation.

For COP, where there are normally no fractional pesos, a `Long` representing pesos is sufficient for the current application.

Example:

```text
$3,050,707 COP
→ 3050707L
```

Parsing must correctly handle formats such as:

```text
3.050.707
3,050,707
3.050.707,00
3,050,707.00
```

according to the source's known formatting rules.

Do not implement one universal "guess the number" regex.

---

# 13. Transfer Matching

Transfers are a special matching problem.

Example:

```text
Bancolombia
-$100,000
10:02

Nequi
+$100,000
10:03
```

Potential result:

```text
TRANSFER
Bancolombia → Nequi
$100,000
```

Matching should consider:

* same amount
* opposite direction
* compatible accounts
* known user-owned accounts
* close timestamps
* references
* institution information
* account identifiers

Do not match solely because two events have the same amount.

The result should have a confidence level.

Only sufficiently strong matches should be automatically associated. Ambiguous cases must remain in Pending Review.

---

# 14. ATM Withdrawals and Cash

Treat Cash as a user-owned account.

Therefore:

```text
Bancolombia → Cash
$100,000
```

is a transfer.

Later:

```text
Cash → Restaurant
$30,000
```

is an expense.

This prevents an ATM withdrawal from being counted as spending before the cash is actually spent.

Do not build sophisticated cash accounting initially. Just model Cash as an account and allow the user to confirm the movement.

---

# 15. Duplicate Detection

Duplicate detection must be confidence-based.

### High confidence

Same:

* amount
* direction/type
* close timestamp
* merchant/counterparty
* account/reference

→ eligible for automatic association.

### Medium confidence

Same:

* amount
* close timestamp
* similar counterparty

→ flag for review.

### Low confidence

Only same amount.

→ do not automatically merge.

Never merge transactions solely because the amounts are equal.

---

# 16. Pending Review

Automatic detections must enter a Pending Review workflow.

A pending item should show enough information for the user to decide quickly:

```text
Expense
$29,000 COP

BOLD SA*20 DE JU
Bancolombia ****0757
Sep 21, 2026 · 8:27 AM

Description: [editable]
Tags: [editable]

[Confirm] [Edit] [Dismiss]
```

For transfers:

```text
Transfer
Bancolombia → Nequi
$100,000 COP

Sep 22, 2026 · 10:02 AM

[Confirm] [Edit] [Dismiss]
```

For uncertain detections:

```text
Possible transfer / expense

The destination account could not be identified.
```

The user must resolve the ambiguity.

---

# 17. Manual Transaction Entry

Manual entry is a first-class feature.

The form should include:

```text
Type
[Expense] [Income] [Transfer]

Amount

Date / Time

Description

Counterparty

Account

Tags
```

When `Transfer` is selected:

```text
From account
To account
Amount
Date / Time
Description
Tags
```

Do not show irrelevant fields.

Manual transactions should not require a source event.

---

# 18. Tags

Tags should be reusable entities rather than free-form strings duplicated across transactions.

Examples:

```text
viaje
comida
hogar
trabajo
suscripciones
transporte
```

The user should be able to:

* create a tag
* reuse a tag
* assign multiple tags
* edit/remove tags later

Do not build a complex tag hierarchy initially.

---

# 19. Room Database

Room is the local source of truth.

Expected relationship:

```text
UI
 ↓
ViewModel
 ↓
Use Case
 ↓
Repository
 ↓
Room
```

The application must remain functional without network access.

Potential entities:

```text
Account
Transaction
Tag
TransactionTag
SourceEvent
TransactionCandidate
PendingReview
```

The exact schema can be refined during implementation.

---

# 20. Repository Architecture

Keep data access behind repositories/interfaces.

Example:

```text
TransactionRepository
AccountRepository
TagRepository
SourceEventRepository
PendingReviewRepository
SyncRepository
```

The UI must not directly access Room or Supabase.

---

# 21. Supabase

Supabase provides remote persistence/synchronization.

Do not make Supabase the immediate source of truth.

Use:

```text
Room
  ↓
Sync Engine
  ↓
Supabase
```

The app must remain usable if:

* there is no internet
* Supabase is temporarily unavailable
* synchronization fails
* the user is offline for several days

Never embed a Supabase service-role secret in the Android application.

Use client-safe credentials and appropriate Row Level Security.

---

# 22. Synchronization

Initially keep synchronization simple.

A local transaction can have metadata such as:

```text
createdAt
updatedAt
syncStatus
remoteId
```

Potential sync states:

```text
SYNCED
PENDING_UPLOAD
PENDING_UPDATE
PENDING_DELETE
ERROR
```

If a local user edit conflicts with an older remote version, do not overwrite the newer local modification with stale remote data.

A simple timestamp/version-based strategy is sufficient initially.

Do not build a complex distributed conflict-resolution system.

---

# 23. SMS Integration

Use Android SMS APIs for the private application.

Required capabilities:

```text
READ_SMS
RECEIVE_SMS
```

New SMS:

```text
SMS BroadcastReceiver
        ↓
SourceEvent
        ↓
Parser
        ↓
Pending Review
```

Do not perform network calls from the SMS receiver.

The receiver should hand the event to the application's persistence/processing pipeline and return quickly.

---

# 24. Historical SMS Import

After the user grants SMS access, offer an initial import.

Possible options:

```text
Last 7 days
Last 30 days
Last 90 days
All available
```

Only process supported financial messages.

Do not upload the user's unrelated SMS messages.

Historical import must use the exact same ingestion pipeline as new SMS messages:

```text
SMS
→ SourceEvent
→ fingerprint
→ parser
→ candidate
→ review
```

Because ingestion is idempotent, running the import again must not duplicate events.

---

# 25. Notification Listener

Use Android's `NotificationListenerService`.

Initially monitor relevant applications such as:

```text
Google Wallet
BBVA
```

The service should:

1. receive notification
2. identify package
3. extract relevant notification fields
4. create `SourceEvent`
5. persist locally
6. pass it through the parser pipeline

Do not make the notification service responsible for business logic.

---

# 26. Source Isolation

Android-specific APIs should stay isolated from domain logic.

For example:

```text
service/
    sms/
        SmsReceiver.kt
        SmsHistoryImporter.kt

    notification/
        FinanceNotificationListener.kt
```

These classes should capture Android events and convert them into application-level source events.

They should not contain:

* duplicate detection logic
* transaction matching logic
* Supabase calls
* complex financial parsing
* UI logic

---

# 27. Parser Design

Use deterministic rules and regular expressions.

Do not use an LLM or external AI service to interpret transactions.

Reasons:

* offline operation
* deterministic behavior
* easier testing
* predictable financial classification
* privacy
* lower complexity

Each parser should have focused responsibilities:

```text
Can this parser handle the message?
        ↓
Extract fields
        ↓
Validate fields
        ↓
Produce ParsedTransaction
```

Malformed messages should produce warnings/errors rather than silently creating incorrect transactions.

---

# 28. Application Package Structure

Start approximately with:

```text
doug.financetracker/
│
├── MainActivity.kt
│
├── data/
│   ├── local/
│   │   ├── database/
│   │   ├── dao/
│   │   └── entity/
│   │
│   ├── remote/
│   │   └── supabase/
│   │
│   └── repository/
│
├── domain/
│   ├── model/
│   ├── parser/
│   ├── duplicate/
│   ├── transfer/
│   └── usecase/
│
├── service/
│   ├── sms/
│   └── notification/
│
├── ui/
│   ├── navigation/
│   ├── screens/
│   │   ├── transactions/
│   │   ├── pending/
│   │   ├── addtransaction/
│   │   └── settings/
│   │
│   ├── components/
│   └── theme/
│
└── util/
```

Adjust the structure if the existing Android project suggests a better organization.

Do not create packages merely for the sake of following this diagram.

---

# 29. UI Scope — Phase 1

Initial screens:

```text
Transactions
Pending Review
Add Transaction
Settings
```

### Transactions

Show:

* date
* description/counterparty
* amount
* type
* tags

Provide basic:

* search
* date filtering
* type filtering
* tag filtering

Do not build a dashboard yet.

### Pending Review

Show automatically detected candidates.

Allow:

* confirm
* edit
* dismiss
* inspect source evidence

### Add Transaction

Support manual:

```text
Expense
Income
Transfer
```

### Settings

Initially include:

* accounts
* tags
* notification-source configuration/status
* SMS import
* sync status
* application information

---

# 30. Future Reporting Compatibility

Do not implement reporting yet.

However, the data model must support it.

Future reports can calculate:

```text
Income
Expenses
Transfers
Net cash movement
Expenses by tag
Expenses by counterparty
Expenses by account
Income by source
Monthly trends
```

The most important requirement is that internal transfers are structurally identifiable so they can be excluded from income/expense calculations.

---

# 31. Testing Strategy

Testing is important because financial parsing errors can create incorrect records.

### Unit tests

Test:

* each parser
* amount parsing
* Colombian number formats
* date parsing
* direction detection
* transaction classification
* account identification
* transfer matching
* duplicate detection
* source fingerprinting

Use the actual SMS examples supplied during development as fixtures.

### Edge cases

Test:

* malformed messages
* missing amounts
* missing dates
* unexpected whitespace
* different decimal separators
* thousands separators
* duplicated notifications
* identical amounts on different transactions
* messages arriving out of order
* transactions with no counterparty
* ambiguous transfers
* failed parsing
* unsupported messages

### Repository/database tests

Verify:

* transactions survive app restarts
* unique fingerprints prevent duplicate events
* tags work correctly
* transfers retain both accounts
* source events remain after confirmation

---

# 32. Security and Privacy

This is a private financial application.

Principles:

* store only necessary financial information
* avoid logging raw SMS content
* avoid logging notification content
* keep raw source evidence local unless there is a specific reason to synchronize it
* never put service-role credentials in the APK
* use Supabase RLS
* minimize account identifiers
* do not send unrelated SMS content anywhere
* do not make network calls during source capture unless absolutely necessary

Debug logging should avoid sensitive values.

---

# 33. Development Workflow

Before modifying code:

1. Inspect the complete existing Android project.
2. Inspect:

   * `build.gradle.kts`
   * `settings.gradle.kts`
   * `libs.versions.toml`
   * manifest
   * existing Compose/theme code
   * SDK versions
   * dependencies
   * `.agents/skills/mobile-android-design`
3. Determine what is already provided by the scaffold.
4. Do not replace existing infrastructure unnecessarily.

Then implement incrementally.

After each significant phase:

```text
Compile
↓
Run unit tests
↓
Fix errors
↓
Inspect implementation
↓
Continue
```

Avoid implementing the entire application in one massive change.

---

# 34. Implementation Phases

## Phase 0 — Project Inspection

Understand the existing scaffold and dependencies.

Deliverable:

```text
Known-good baseline project
```

---

## Phase 1 — Foundation

Implement:

* application architecture
* Room
* Account
* Transaction
* Tag
* TransactionTag
* manual CRUD
* transaction list
* add/edit transaction
* type selector
* transfer account selection
* basic filtering/search
* navigation

Definition of done:

```text
I can manually record, edit, delete, search, and filter
expenses, income, and transfers.
```

---

## Phase 2 — Pending Review

Implement:

* SourceEvent
* TransactionCandidate
* Pending Review UI
* confirmation
* editing
* dismissal
* source-event relationships
* provenance display

Definition of done:

```text
A detected financial event can be reviewed and converted
into an official transaction without losing its source evidence.
```

---

## Phase 3 — Parsers

Implement:

* Bancolombia parser
* NEQUI parser
* DAVIbank parser
* exact amount/date parsing
* incoming/outgoing classification
* counterparty extraction
* account hints
* parser confidence/warnings

Create comprehensive unit tests using real message examples.

Definition of done:

```text
Known financial messages produce correct structured interpretations.
Unknown/malformed messages fail safely.
```

---

## Phase 4 — Notification Detection

Implement:

* NotificationListenerService
* Google Wallet detection
* BBVA detection
* package filtering
* notification SourceEvent creation
* idempotency

Definition of done:

```text
Relevant notifications become Pending Review items without
blocking or crashing the application.
```

---

## Phase 5 — SMS Detection

Implement:

* SMS permissions
* incoming SMS receiver
* historical SMS importer
* supported sender filtering
* SourceEvent creation
* idempotency

Definition of done:

```text
New and historical supported financial SMS messages enter
the same processing pipeline.
```

---

## Phase 6 — Duplicate and Transfer Matching

Implement:

* duplicate detection
* source-event grouping
* confidence scoring
* internal account detection
* transfer matching
* Cash account handling
* ambiguous-case review

Definition of done:

```text
Multiple pieces of evidence can represent one logical
transaction without creating duplicate financial records.

Internal account movements are represented as transfers
rather than income/expenses.
```

---

## Phase 7 — Supabase Synchronization

Implement:

* Supabase schema/migrations
* local sync metadata
* upload queue
* retry behavior
* conflict handling
* initial synchronization
* RLS

Definition of done:

```text
The app works completely offline and synchronizes changes
when connectivity is available.
```

---

## Phase 8 — Hardening

Implement/finalize:

* accessibility
* error handling
* malformed-data handling
* performance
* database migrations
* permission-state handling
* sync failure recovery
* notification/SMS edge cases
* security review
* test coverage
* documentation

Definition of done:

```text
The app is stable enough for daily personal use.
```

---

# 35. Explicit Non-Goals

Do not implement these unless they become necessary later:

* budgeting
* financial goals
* investment tracking
* bank API integrations
* automatic bank login
* complex accounting
* multi-user support
* cloud-based AI transaction classification
* advanced analytics dashboards
* complicated recurring-payment engines
* sophisticated cash accounting
* automatic financial decisions

The first objective is reliable transaction capture and classification.

---

# 36. Definition of Done

The first stable version should satisfy all of the following:

### Manual transactions

* [ ] Create expense
* [ ] Create income
* [ ] Create transfer
* [ ] Edit transactions
* [ ] Delete transactions
* [ ] Assign tags
* [ ] Search/filter transactions
* [ ] Select accounts

### Automatic detection

* [ ] Bancolombia SMS
* [ ] NEQUI SMS
* [ ] DAVIbank SMS
* [ ] Google Wallet notifications
* [ ] BBVA notifications
* [ ] Historical SMS import
* [ ] Idempotent ingestion

### Review

* [ ] Pending Review
* [ ] Confirm
* [ ] Edit
* [ ] Dismiss
* [ ] Source evidence retained

### Classification

* [ ] Expense
* [ ] Income
* [ ] Transfer
* [ ] Internal account recognition
* [ ] Cash withdrawals represented correctly
* [ ] Ambiguous cases require review

### Data integrity

* [ ] Duplicate detection
* [ ] Transfer matching
* [ ] Exact monetary values
* [ ] Correct dates/times
* [ ] Parser tests
* [ ] Database constraints

### Offline-first

* [ ] Core functionality works without internet
* [ ] Room is local source of truth
* [ ] Sync retries
* [ ] Sync failures do not block the app

### Supabase

* [ ] RLS configured
* [ ] No service-role secret in client
* [ ] Local changes synchronize
* [ ] Conflict behavior is deterministic

### Privacy

* [ ] Raw financial messages are not unnecessarily uploaded
* [ ] Sensitive information is not logged
* [ ] Only supported financial SMS is processed
* [ ] Account identifiers are minimized

---

# 37. Guiding Principle

The application should favor **correctness and explicit user confirmation over aggressive automation**.

The system should be comfortable saying:

```text
"I found something that looks like a transaction,
but I'm not certain what it is."
```

rather than silently creating an incorrect financial record.

The architecture should therefore preserve the distinction between:

```text
What the bank/notification said
        ↓
What the parser interpreted
        ↓
What the application thinks the transaction is
        ↓
What the user explicitly confirmed
```

That distinction is the foundation of FinanceTracker.
