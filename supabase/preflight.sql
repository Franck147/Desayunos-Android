-- SOLO LECTURA. Revisar estos resultados antes de aplicar las migraciones.
select version();
select tablename,rowsecurity from pg_tables where schemaname='public';
select schemaname,tablename,policyname,cmd,roles,qual,with_check from pg_policies
where schemaname in ('public','storage') order by schemaname,tablename,policyname;
select conrelid::regclass as table_name,conname,pg_get_constraintdef(oid) as definition
from pg_constraint where connamespace='public'::regnamespace order by 1,2;
select event_object_schema,event_object_table,trigger_name,action_statement
from information_schema.triggers where event_object_schema in ('public','auth');
select 'profiles_role' issue,count(*) from public.profiles where role is not null and role not in ('CLIENT','COOK','ADMIN')
union all select 'menu_invalid',count(*) from public.menu_items where price<0 or price<>round(price,2) or available_stock<0 or category not in ('BREAD','BEVERAGE','EXTRA','COMBO')
union all select 'orders_invalid',count(*) from public.orders where total_amount<0 or total_amount<>round(total_amount,2) or status not in ('PENDING','PREPARING','READY','DELIVERED','CANCELLED')
union all select 'details_invalid',count(*) from public.order_items where quantity<=0 or unit_price<0 or unit_price<>round(unit_price,2) or subtotal<>unit_price*quantity;
select id,email,role from public.profiles order by created_at;
select id,name,public,file_size_limit,allowed_mime_types from storage.buckets;
select schemaname,tablename from pg_publication_tables where pubname='supabase_realtime';
