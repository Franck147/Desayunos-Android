begin;
create table public.daily_cash_closures (
    business_date date primary key,
    total_amount numeric not null,
    payment_count integer not null,
    actor_id uuid not null references public.profiles(id),
    created_at timestamptz not null default now()
);
alter table public.daily_cash_closures enable row level security;
revoke all on public.daily_cash_closures from public,anon,authenticated;
grant select on public.daily_cash_closures to authenticated;
create policy closures_admin on public.daily_cash_closures for select to authenticated
using((select private.current_role())='ADMIN');

-- El cierre y el registro de pagos comparten el bloqueo de jornada.
create function private.check_open_cash_day() returns trigger
language plpgsql security definer set search_path='' as $$
declare day date := (new.created_at at time zone 'America/Lima')::date;
begin
    perform pg_advisory_xact_lock(hashtextextended('cash:' || day::text,0));
    if exists(select 1 from public.daily_cash_closures where business_date=day) then
        raise exception 'La caja de esta jornada ya está cerrada'; end if;
    return new;
end $$;
revoke all on function private.check_open_cash_day() from public,anon,authenticated;
create trigger check_cash_day before insert on public.payments
for each row execute function private.check_open_cash_day();

create function public.close_cash_day(p_date date,p_expected_total numeric) returns jsonb
language plpgsql security definer set search_path='' as $$
declare total numeric; amount_count integer; closure public.daily_cash_closures;
begin
    if private.current_role() is distinct from 'ADMIN' then raise exception 'Solo administración puede cerrar caja'; end if;
    if p_date is null or p_date > (now() at time zone 'America/Lima')::date then raise exception 'Fecha inválida'; end if;
    perform pg_advisory_xact_lock(hashtextextended('cash:' || p_date::text,0));
    select * into closure from public.daily_cash_closures where business_date=p_date;
    if closure.business_date is not null then return to_jsonb(closure); end if;
    select coalesce(sum(amount),0),count(*) into total,amount_count from public.payments
      where created_at >= (p_date::timestamp at time zone 'America/Lima')
      and created_at < ((p_date+1)::timestamp at time zone 'America/Lima');
    if p_expected_total is null or p_expected_total <> total then raise exception 'Los cobros cambiaron. Actualice antes de cerrar'; end if;
    insert into public.daily_cash_closures(business_date,total_amount,payment_count,actor_id)
      values(p_date,total,amount_count,auth.uid()) returning * into closure;
    return to_jsonb(closure);
end $$;
revoke all on function public.close_cash_day(date,numeric) from public,anon,authenticated;
grant execute on function public.close_cash_day(date,numeric) to authenticated;
create index payments_date_idx on public.payments(created_at);
notify pgrst,'reload schema';
commit;
