-- FinanceTracker remote backup/sync schema.
--
-- AUTH STRATEGY (read before running):
--   * Single-user app using Supabase Auth (email + password). The user signs
--     in once on the device; the Android client (supabase-kt) persists the
--     session in app-private storage and attaches the user's JWT to every
--     request.
--   * Every table carries owner_id uuid NOT NULL DEFAULT auth.uid(), so rows
--     are stamped with the signed-in user on insert.
--   * Row Level Security is ENABLED on all tables with one policy each:
--     FOR ALL USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id).
--     The client authenticates with the user JWT, so auth.uid() is satisfied.
--     The publishable (anon) key alone — no session — sees zero rows, which is
--     why it is safe to ship in the APK. NEVER use the service-role key here.
--   * Dashboard prerequisites: Authentication > Providers > Email enabled.
--     Either disable "Confirm email" or confirm the address, otherwise
--     sign-in from the app will be rejected until confirmed.
--
-- SCOPE: official data only (accounts, tags, transactions, transaction_tags).
-- Raw SMS/notification content, SourceEvents, candidates and other source
-- evidence NEVER leave the device — there are no columns for them here.
--
-- CONFLICT MODEL: client-managed updated_at_ms (epoch millis, matches Room).
-- Last-write-wins; the client treats ties as local-wins (never overwrites a
-- newer local edit with stale remote data).

-- Accounts ---------------------------------------------------------------
create table public.accounts (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  institution text not null default '',
  account_type text not null default '',
  identifier_suffix text not null default '',
  is_owned_by_user boolean not null default true,
  created_at_ms bigint not null,
  updated_at_ms bigint not null
);
create index accounts_owner_id_idx on public.accounts (owner_id);

alter table public.accounts enable row level security;
create policy accounts_owner_all on public.accounts
  for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

-- Tags -------------------------------------------------------------------
create table public.tags (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  created_at_ms bigint not null,
  constraint tags_owner_name_unique unique (owner_id, name)
);
create index tags_owner_id_idx on public.tags (owner_id);

alter table public.tags enable row level security;
create policy tags_owner_all on public.tags
  for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

-- Transactions -----------------------------------------------------------
create table public.transactions (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  type text not null check (type in ('EXPENSE', 'INCOME', 'TRANSFER')),
  amount bigint not null check (amount > 0),
  date_time_ms bigint not null,
  description text not null default '',
  counterparty text not null default '',
  source_account_id uuid references public.accounts (id) on delete set null,
  destination_account_id uuid references public.accounts (id) on delete set null,
  created_at_ms bigint not null,
  updated_at_ms bigint not null
);
create index transactions_owner_id_idx on public.transactions (owner_id);
create index transactions_owner_date_idx on public.transactions (owner_id, date_time_ms desc);

alter table public.transactions enable row level security;
create policy transactions_owner_all on public.transactions
  for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

-- Transaction <-> tag links ----------------------------------------------
create table public.transaction_tags (
  transaction_id uuid not null references public.transactions (id) on delete cascade,
  tag_id uuid not null references public.tags (id) on delete cascade,
  primary key (transaction_id, tag_id)
);

alter table public.transaction_tags enable row level security;
-- Links carry no owner_id; access is gated through ownership of BOTH ends.
create policy transaction_tags_owner_all on public.transaction_tags
  for all using (
    exists (select 1 from public.transactions t
            where t.id = transaction_tags.transaction_id and t.owner_id = auth.uid())
    and exists (select 1 from public.tags g
            where g.id = transaction_tags.tag_id and g.owner_id = auth.uid())
  )
  with check (
    exists (select 1 from public.transactions t
            where t.id = transaction_tags.transaction_id and t.owner_id = auth.uid())
    and exists (select 1 from public.tags g
            where g.id = transaction_tags.tag_id and g.owner_id = auth.uid())
  );
