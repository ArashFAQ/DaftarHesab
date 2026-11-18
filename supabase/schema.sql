-- جدول‌های دفتر حساب (یک بار در SQL Editor اجرا شده است)
create table public.customers (
  id          uuid primary key,
  owner       uuid not null default auth.uid() references auth.users(id),
  name        text not null,
  phone       text,
  note        text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  deleted     boolean not null default false
);

create table public.transactions (
  id           uuid primary key,
  owner        uuid not null default auth.uid() references auth.users(id),
  customer_id  uuid not null references public.customers(id),
  kind         text not null check (kind in ('debt','payment')),
  amount       bigint not null check (amount > 0),
  note         text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted      boolean not null default false
);

create index on public.transactions (customer_id);
create index on public.customers (updated_at);
create index on public.transactions (updated_at);

alter table public.customers    enable row level security;
alter table public.transactions enable row level security;

create policy "own customers" on public.customers
  for all to authenticated
  using (owner = auth.uid()) with check (owner = auth.uid());

create policy "own transactions" on public.transactions
  for all to authenticated
  using (owner = auth.uid()) with check (owner = auth.uid());
