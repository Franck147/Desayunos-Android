-- Ejecutar primero en ensayo, sobre el esquema existente descrito en docs/SUPABASE.md.
-- Migración única y transaccional: si algo falla, no aplicar solo fragmentos.
begin;

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

alter table public.orders add column customer_id uuid references public.profiles(id);
alter table public.orders add column request_id uuid;
alter table public.orders add column request_payload jsonb;
alter table public.orders add column updated_at timestamptz not null default now();
alter table public.orders add column cancelled_reason text;
alter table public.orders add constraint orders_customer_request_unique unique(customer_id, request_id);
alter table public.orders alter column customer_phone drop default;
alter table public.orders alter column created_at set default now();
alter table public.menu_items add column updated_at timestamptz not null default now();
alter table public.menu_items alter column created_at set default now();
alter table public.announcements add column author_id uuid references public.profiles(id);
alter table public.announcements alter column created_at set default now();
update public.profiles set role = 'CLIENT' where role is null;
alter table public.profiles alter column role set not null;
alter table public.profiles add constraint profiles_role_check check(role in ('CLIENT','COOK','ADMIN'));
update public.menu_items set is_available = true where is_available is null;
alter table public.menu_items alter column is_available set not null;
alter table public.menu_items add constraint menu_price_check check(price >= 0 and price = round(price,2));
alter table public.menu_items add constraint menu_stock_check check(available_stock >= 0);
alter table public.menu_items add constraint menu_category_check check(category in ('BREAD','BEVERAGE','EXTRA','COMBO'));
alter table public.orders add constraint orders_status_check check(status in ('PENDING','PREPARING','READY','DELIVERED','CANCELLED'));
alter table public.orders add constraint orders_total_check check(total_amount >= 0 and total_amount = round(total_amount,2));
alter table public.order_items add constraint items_quantity_check check(quantity > 0);
alter table public.order_items add constraint items_amount_check check(unit_price >= 0 and unit_price = round(unit_price,2) and subtotal = unit_price * quantity);

-- Los pares descritos en el esquema son equivalentes. Conservar los fk_*.
alter table public.order_items drop constraint if exists order_items_order_id_fkey;
alter table public.order_items drop constraint if exists order_items_menu_item_id_fkey;
create index if not exists order_items_order_idx on public.order_items(order_id);
create index if not exists order_items_menu_idx on public.order_items(menu_item_id);
create index if not exists orders_customer_date_idx on public.orders(customer_id,created_at desc);
create index if not exists orders_status_date_idx on public.orders(status,created_at);

-- La app anterior enviaba números explícitos; sincronizar la secuencia sin retrocederla.
select setval('public.orders_order_number_seq', greatest(
    coalesce((select max(order_number) from public.orders),0),
    (select last_value from public.orders_order_number_seq), 1), true);

create table public.order_status_history (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null references public.orders(id),
    previous_status text,
    new_status text not null,
    actor_id uuid not null references public.profiles(id),
    reason text,
    created_at timestamptz not null default now()
);
create index on public.order_status_history(order_id,created_at);
create table public.product_stock_movements (
    id uuid primary key default gen_random_uuid(),
    menu_item_id uuid not null references public.menu_items(id),
    quantity_delta integer not null check(quantity_delta <> 0),
    reason text not null check(reason in ('ORDER','CANCELLATION','ADJUSTMENT','INITIAL')),
    order_id uuid references public.orders(id),
    actor_id uuid not null references public.profiles(id),
    created_at timestamptz not null default now(),
    unique(order_id,menu_item_id,reason)
);
create index on public.product_stock_movements(menu_item_id,created_at);
create table public.payments (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null unique references public.orders(id),
    amount numeric not null check(amount >= 0 and amount = round(amount,2)),
    method text not null check(method in ('CASH','YAPE','PLIN')),
    reference text,
    actor_id uuid not null references public.profiles(id),
    created_at timestamptz not null default now()
);
create table public.business_settings (
    id boolean primary key default true check(id),
    accepting_orders boolean not null default true,
    updated_at timestamptz not null default now()
);
insert into public.business_settings default values;

create function private.current_role() returns text
language sql stable security definer set search_path = '' as $$
    select p.role from public.profiles p where p.id = auth.uid()
$$;
revoke all on function private.current_role() from public, anon, authenticated;
grant execute on function private.current_role() to authenticated;

-- No copiar el rol desde user_metadata, que puede modificar el cliente.
create function private.create_profile() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    insert into public.profiles(id,email,full_name,phone,role)
    values(new.id,new.email,new.raw_user_meta_data->>'full_name',new.raw_user_meta_data->>'phone','CLIENT')
    on conflict(id) do nothing;
    return new;
end $$;
revoke all on function private.create_profile() from public, anon, authenticated;
create trigger desayuno_create_profile after insert on auth.users
for each row execute function private.create_profile();
-- Completar perfiles ausentes sin sobrescribir roles existentes.
insert into public.profiles(id,email,full_name,phone,role)
select id,email,raw_user_meta_data->>'full_name',raw_user_meta_data->>'phone','CLIENT'
from auth.users where email is not null on conflict(id) do nothing;

-- Retirar TODAS las políticas de estas tablas: las permisivas se combinan con OR.
do $$ declare p record; begin
    for p in select schemaname,tablename,policyname from pg_policies
      where schemaname='public' and tablename in ('profiles','menu_items','orders','order_items',
      'stock_inventory','announcements','order_status_history','product_stock_movements','payments','business_settings')
    loop execute format('drop policy %I on %I.%I',p.policyname,p.schemaname,p.tablename); end loop;
end $$;
do $$ declare t text; columns_sql text; begin
    foreach t in array array['profiles','menu_items','orders','order_items','stock_inventory','announcements',
      'order_status_history','product_stock_movements','payments','business_settings']
    loop
        execute format('alter table public.%I enable row level security',t);
        execute format('revoke all on public.%I from public, anon, authenticated',t);
        select string_agg(format('%I',column_name),',') into columns_sql
          from information_schema.columns where table_schema='public' and table_name=t;
        execute format('revoke all (%s) on public.%I from public, anon, authenticated',columns_sql,t);
    end loop;
end $$;
grant select on public.profiles,public.menu_items,public.orders,public.order_items,public.announcements,
    public.order_status_history,public.product_stock_movements,public.payments,public.business_settings to authenticated;
grant update(full_name,phone) on public.profiles to authenticated;
create policy profiles_read on public.profiles for select to authenticated using(id=auth.uid());
create policy profiles_contact on public.profiles for update to authenticated using(id=auth.uid()) with check(id=auth.uid());
create policy menu_read on public.menu_items for select to authenticated
using(is_available or (select private.current_role()) in ('COOK','ADMIN'));
create policy orders_read on public.orders for select to authenticated
using(customer_id=auth.uid() or (select private.current_role()) in ('COOK','ADMIN'));
create policy items_read on public.order_items for select to authenticated using(
    exists(select 1 from public.orders o where o.id=order_id));
create policy history_read on public.order_status_history for select to authenticated using(
    exists(select 1 from public.orders o where o.id=order_id));
create policy payments_read on public.payments for select to authenticated using(
    exists(select 1 from public.orders o where o.id=order_id));
create policy movements_read on public.product_stock_movements for select to authenticated
using((select private.current_role()) in ('COOK','ADMIN'));
create policy announcements_read on public.announcements for select to authenticated using(true);
create policy settings_read on public.business_settings for select to authenticated using(true);

-- Todas las escrituras de negocio se realizan mediante funciones autorizadas.
create function public.create_order(p_request_id uuid,p_items jsonb) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    u public.profiles; existing public.orders; result public.orders; product public.menu_items;
    line jsonb; item record; total numeric := 0; canonical jsonb; minutes integer;
begin
    if auth.uid() is null then raise exception 'Sesión requerida'; end if;
    select * into u from public.profiles where id=auth.uid();
    if u.id is null or nullif(btrim(u.full_name),'') is null or nullif(btrim(u.phone),'') is null then
        raise exception 'Complete su nombre y teléfono en el perfil'; end if;
    if p_request_id is null or jsonb_typeof(p_items) is distinct from 'array' then raise exception 'Solicitud inválida'; end if;
    if jsonb_array_length(p_items) not between 1 and 100 then raise exception 'Carrito vacío o demasiado grande'; end if;
    for line in select value from jsonb_array_elements(p_items) loop
        if jsonb_typeof(line->'quantity') is distinct from 'number'
          or (line->>'quantity') !~ '^[0-9]+$'
          or (line->>'quantity')::numeric not between 1 and 100
          or nullif(line->>'menu_item_id','') is null
          or length(coalesce(line->>'notes','')) > 500 then raise exception 'Detalle inválido'; end if;
    end loop;
    -- Orden canónico para idempotencia; un producto puede aparecer con notas diferentes.
    select jsonb_agg(jsonb_build_object('menu_item_id',x.menu_item_id,'quantity',x.quantity,'notes',x.notes)
        order by x.menu_item_id,x.notes) into canonical from (
        select (v->>'menu_item_id')::uuid menu_item_id, coalesce(v->>'notes','') notes,
            sum((v->>'quantity')::integer)::integer quantity
        from jsonb_array_elements(p_items) v group by 1,2) x;
    -- Serializa solo los reintentos de la misma solicitud.
    perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text || p_request_id::text,0));
    select * into existing from public.orders where customer_id=u.id and request_id=p_request_id;
    if existing.id is not null then
        if existing.request_payload <> canonical then raise exception 'El identificador pertenece a otro carrito'; end if;
        return to_jsonb(existing);
    end if;
    perform 1 from public.business_settings where id and accepting_orders for share;
    if not found then raise exception 'La recepción de pedidos está cerrada'; end if;
    -- Bloqueos en orden estable evitan sobreventa y reducen interbloqueos.
    for item in select (v->>'menu_item_id')::uuid id,sum((v->>'quantity')::integer)::integer quantity
        from jsonb_array_elements(canonical) v group by 1 order by 1 loop
        select * into product from public.menu_items where id=item.id for update;
        if product.id is null or not product.is_available then raise exception 'Producto no disponible'; end if;
        if item.quantity > 100 or product.available_stock < item.quantity then raise exception 'Stock insuficiente: %',product.name; end if;
        total := total + product.price * item.quantity;
    end loop;
    select (count(*)::integer + 1)*3 into minutes from public.orders where status in ('PENDING','PREPARING');
    insert into public.orders(customer_id,request_id,request_payload,customer_name,customer_phone,total_amount,status,estimated_minutes)
      values(u.id,p_request_id,canonical,u.full_name,u.phone,total,'PENDING',minutes) returning * into result;
    insert into public.order_items(order_id,menu_item_id,menu_item_name,unit_price,quantity,subtotal,notes)
      select result.id,m.id,m.name,m.price,(v->>'quantity')::integer,m.price*(v->>'quantity')::integer,v->>'notes'
      from jsonb_array_elements(canonical) v join public.menu_items m on m.id=(v->>'menu_item_id')::uuid;
    for item in select menu_item_id,sum(quantity)::integer quantity from public.order_items
        where order_id=result.id group by menu_item_id order by menu_item_id loop
        update public.menu_items set available_stock=available_stock-item.quantity,updated_at=clock_timestamp() where id=item.menu_item_id;
        insert into public.product_stock_movements(menu_item_id,quantity_delta,reason,order_id,actor_id)
          values(item.menu_item_id,-item.quantity,'ORDER',result.id,u.id);
    end loop;
    insert into public.order_status_history(order_id,new_status,actor_id) values(result.id,'PENDING',u.id);
    return to_jsonb(result);
end $$;

create function public.transition_order(p_order_id uuid,p_status text,p_reason text default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare o public.orders; role_name text := private.current_role(); item record; previous text;
begin
    if auth.uid() is null then raise exception 'Sesión requerida'; end if;
    select * into o from public.orders where id=p_order_id for update;
    if o.id is null or not (coalesce(o.customer_id=auth.uid(),false) or coalesce(role_name in ('COOK','ADMIN'),false)) then
        raise exception 'Pedido no accesible'; end if;
    if o.status=p_status then return to_jsonb(o); end if;
    if p_status='CANCELLED' then
        if o.status <> 'PENDING' or nullif(btrim(p_reason),'') is null then
            raise exception 'Solo se cancelan pedidos en cola, indicando un motivo'; end if;
        if exists(select 1 from public.payments where order_id=o.id) then raise exception 'El pedido pagado requiere devolución por administración'; end if;
    elsif role_name is null or role_name not in ('COOK','ADMIN') or not (
        (o.status='PENDING' and p_status='PREPARING') or (o.status='PREPARING' and p_status='READY') or
        (o.status='READY' and p_status='DELIVERED')) then raise exception 'Transición no permitida';
    end if;
    if p_status='DELIVERED' and not exists(select 1 from public.payments where order_id=o.id) then
        raise exception 'Registre el pago antes de entregar'; end if;
    previous := o.status;
    if p_status='CANCELLED' then
        -- Solo devolver reservas creadas por este sistema, nunca inferir stock de pedidos históricos.
        for item in select menu_item_id,-quantity_delta quantity from public.product_stock_movements
          where order_id=o.id and reason='ORDER' order by menu_item_id loop
            update public.menu_items set available_stock=available_stock+item.quantity,updated_at=clock_timestamp() where id=item.menu_item_id;
            insert into public.product_stock_movements(menu_item_id,quantity_delta,reason,order_id,actor_id)
              values(item.menu_item_id,item.quantity,'CANCELLATION',o.id,auth.uid());
        end loop;
    end if;
    update public.orders set status=p_status,updated_at=clock_timestamp(),cancelled_reason=case when p_status='CANCELLED' then p_reason else cancelled_reason end
      where id=o.id returning * into o;
    insert into public.order_status_history(order_id,previous_status,new_status,actor_id,reason)
      values(o.id,previous,p_status,auth.uid(),p_reason);
    return to_jsonb(o);
end $$;

create function public.save_menu_item(p_item jsonb,p_expected_updated_at timestamptz default null) returns void
language plpgsql security definer set search_path = '' as $$
declare old public.menu_items; product_id uuid := (p_item->>'id')::uuid; stock integer := (p_item->>'available_stock')::integer;
begin
    if private.current_role() is distinct from 'ADMIN' then raise exception 'Solo administración puede editar la carta'; end if;
    if nullif(btrim(p_item->>'name'),'') is null or stock is null or stock < 0
       or (p_item->>'price')::numeric is null or (p_item->>'price')::numeric < 0 then raise exception 'Producto inválido'; end if;
    perform pg_advisory_xact_lock(hashtextextended(product_id::text,1));
    select * into old from public.menu_items where id=product_id for update;
    if old.id is not null and (p_expected_updated_at is null or old.updated_at <> p_expected_updated_at) then
        raise exception 'El producto o su stock cambió. Actualice y vuelva a editar'; end if;
    insert into public.menu_items(id,name,description,price,category,available_stock,is_available,image_url)
      values(product_id,btrim(p_item->>'name'),coalesce(p_item->>'description',''),(p_item->>'price')::numeric,
      p_item->>'category',stock,coalesce((p_item->>'is_available')::boolean,true),p_item->>'image_url')
      on conflict(id) do update set name=excluded.name,description=excluded.description,price=excluded.price,
      category=excluded.category,available_stock=excluded.available_stock,is_available=excluded.is_available,
      image_url=excluded.image_url,updated_at=clock_timestamp();
    if stock <> coalesce(old.available_stock,0) then
        insert into public.product_stock_movements(menu_item_id,quantity_delta,reason,actor_id)
          values(product_id,stock-coalesce(old.available_stock,0),case when old.id is null then 'INITIAL' else 'ADJUSTMENT' end,auth.uid());
    end if;
end $$;

create function public.publish_announcement(p_title text,p_message text) returns void
language plpgsql security definer set search_path = '' as $$
begin
    if private.current_role() is distinct from 'ADMIN' then raise exception 'Solo administración puede publicar'; end if;
    if length(btrim(p_title)) not between 1 and 150 or length(btrim(p_message)) not between 1 and 2000 then
        raise exception 'Aviso inválido'; end if;
    insert into public.announcements(title,message,author_id) values(btrim(p_title),btrim(p_message),auth.uid());
end $$;
create function public.record_payment(p_order_id uuid,p_method text,p_reference text default null) returns void
language plpgsql security definer set search_path = '' as $$
declare o public.orders; payment public.payments;
begin
    if private.current_role() is distinct from 'ADMIN' then raise exception 'Solo administración puede confirmar cobros'; end if;
    select * into o from public.orders where id=p_order_id for update;
    if o.id is null or o.status='CANCELLED' then raise exception 'Pedido no cobrable'; end if;
    if p_method is null or p_method not in ('CASH','YAPE','PLIN') then raise exception 'Método inválido'; end if;
    if p_method <> 'CASH' and nullif(btrim(p_reference),'') is null then raise exception 'Indique la referencia verificada del pago'; end if;
    select * into payment from public.payments where order_id=o.id;
    if payment.id is not null then
        if payment.method <> p_method or coalesce(payment.reference,'') <> coalesce(p_reference,'') then
            raise exception 'El pedido ya tiene otro pago confirmado'; end if;
        return;
    end if;
    insert into public.payments(order_id,amount,method,reference,actor_id)
      values(o.id,o.total_amount,p_method,p_reference,auth.uid());
end $$;
create function public.set_accepting_orders(p_accepting boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
    if private.current_role() is distinct from 'ADMIN' then raise exception 'Solo administración puede abrir/cerrar pedidos'; end if;
    update public.business_settings set accepting_orders=p_accepting,updated_at=clock_timestamp() where id;
end $$;

revoke all on function public.create_order(uuid,jsonb),public.transition_order(uuid,text,text),
public.save_menu_item(jsonb,timestamptz),public.publish_announcement(text,text),public.record_payment(uuid,text,text),
public.set_accepting_orders(boolean) from public, anon, authenticated;
grant execute on function public.create_order(uuid,jsonb),public.transition_order(uuid,text,text),
public.save_menu_item(jsonb,timestamptz),public.publish_announcement(text,text),public.record_payment(uuid,text,text),
public.set_accepting_orders(boolean) to authenticated;

notify pgrst, 'reload schema';
commit;
