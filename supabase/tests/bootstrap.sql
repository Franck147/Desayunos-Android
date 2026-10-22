-- Solo para el PostgreSQL efímero del runner local. NUNCA ejecutar en Supabase.
create role anon;
create role authenticated;
create schema auth;
create table auth.users(id uuid primary key,email text,raw_user_meta_data jsonb default '{}');
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
grant usage on schema auth to authenticated,anon;
grant execute on function auth.uid() to authenticated,anon;
create schema storage;
create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
create table storage.objects(id uuid primary key default gen_random_uuid(),bucket_id text,name text);
alter table storage.objects enable row level security;
grant usage on schema storage to authenticated,anon;
grant select,insert,update,delete on storage.objects to authenticated,anon;
create table public.profiles(id uuid primary key references auth.users(id),email text unique not null,full_name text,phone text,role text default 'CLIENT',created_at timestamptz default now());
create table public.menu_items(id uuid primary key default gen_random_uuid(),name text not null,description text default '',price numeric not null default 0,category text not null default 'BREAD',available_stock integer not null default 0,is_available boolean default true,image_url text,created_at timestamptz default now());
create sequence public.orders_order_number_seq;
create table public.orders(id uuid primary key default gen_random_uuid(),order_number integer unique not null default nextval('public.orders_order_number_seq'),customer_name text not null,customer_phone text not null default '987654321',total_amount numeric not null default 0,status text not null default 'PENDING',estimated_minutes integer default 3,created_at timestamptz default now());
create table public.order_items(id uuid primary key default gen_random_uuid(),order_id uuid not null,menu_item_id uuid,menu_item_name text not null,unit_price numeric not null,quantity integer not null default 1,subtotal numeric not null,notes text default '',
constraint fk_order_items_order foreign key(order_id) references public.orders(id),constraint order_items_order_id_fkey foreign key(order_id) references public.orders(id),
constraint fk_order_items_menu foreign key(menu_item_id) references public.menu_items(id),constraint order_items_menu_item_id_fkey foreign key(menu_item_id) references public.menu_items(id));
create table public.stock_inventory(id uuid primary key default gen_random_uuid(),bread_units integer not null default 50,quinua_liters integer not null default 20,maca_liters integer not null default 15,soya_liters integer not null default 15,extras_count integer not null default 40,updated_at timestamptz default now());
create table public.announcements(id uuid primary key default gen_random_uuid(),title text not null,message text not null,created_at timestamptz default now());
-- Fixture histórica: secuencia atrasada y pedido sin propietario.
insert into public.orders(order_number,customer_name) values(700,'Histórico');
create policy "Acceso total a orders" on public.orders for all using(true) with check(true);
