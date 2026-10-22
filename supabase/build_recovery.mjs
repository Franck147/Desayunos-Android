import { readFile, writeFile } from 'node:fs/promises';

// Adaptar el contrato existente sin eliminar datos ni volver a crear objetos existentes.
let sql = await readFile(new URL('SUPABASE_COMPLETO_V3.sql',import.meta.url),'utf8');
sql = sql.slice(sql.indexOf('BEGIN;'));
sql = sql.replace(/\badd column (?!if not exists)/gi,'add column if not exists ');
let number=0;
function ensureConstraint(table,name,definition) {
  const tag=`$desayuno_ensure_constraint_${++number}$`;
  return `DO LANGUAGE plpgsql ${tag}
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint WHERE conrelid='${table}'::regclass AND conname='${name}') THEN
        ALTER TABLE ${table} ADD CONSTRAINT ${name} ${definition};
    END IF;
END;
${tag};`;
}
sql=sql.replace(/alter table (public\.\w+) add constraint (\w+) ([^;]+);/gi,
  (_,table,name,definition)=>ensureConstraint(table,name,definition));
sql=sql.replace(/\bcreate table (?!if not exists)/gi,'create table if not exists ');
sql=sql.replace(/\bcreate function /gi,'create or replace function ');
sql=sql.replace(/create index on public\.(\w+)\(([^)]+)\);/gi,(_,table,columns)=>
  `create index if not exists ${table}_${columns.replaceAll(',','_')}_idx on public.${table}(${columns});`);
sql=sql.replace(/create index (?!if not exists)(\w+) on /gi,'create index if not exists $1 on ');
sql=sql.replace(/create trigger (\w+) (after insert|before insert) on ([\w.]+)/gi,
  (_,name,when,table)=>`drop trigger if exists ${name} on ${table};\ncreate trigger ${name} ${when} on ${table}`);
sql=sql.replace(/create policy (\w+) on ([\w.]+)/gi,
  (_,name,table)=>`drop policy if exists ${name} on ${table};\ncreate policy ${name} on ${table}`);
sql=sql.replace('insert into public.business_settings default values;',
  'insert into public.business_settings default values on conflict(id) do nothing;');

// ADD COLUMN IF NOT EXISTS no añade una FK si la columna ya existía sin ella.
const fks=[
  ['public.orders','orders_customer_id_fkey','foreign key(customer_id) references public.profiles(id)'],
  ['public.announcements','announcements_author_id_fkey','foreign key(author_id) references public.profiles(id)'],
  ['public.order_items','fk_order_items_order','foreign key(order_id) references public.orders(id)'],
  ['public.order_items','fk_order_items_menu','foreign key(menu_item_id) references public.menu_items(id)']
].map(args=>ensureConstraint(...args)).join('\n\n');
const tables=['profiles','orders','order_items','menu_items','announcements','stock_inventory',
  'order_status_history','product_stock_movements','payments','business_settings','daily_cash_closures'];
const enableRls=tables.map(table=>`ALTER TABLE public.${table} ENABLE ROW LEVEL SECURITY;`).join('\n');
sql=sql.replace("NOTIFY pgrst, 'reload schema';",`${fks}\n\n${enableRls}\n\nNOTIFY pgrst, 'reload schema';`);
const header=`-- DESAYUNOS: RECUPERACION V4, REEJECUTABLE
-- Para el esquema original o los cambios parciales de 001/002/003/V3.
-- Conserva pedidos, productos, usuarios, pagos, historial y cierres.
-- Completa columnas/relaciones ausentes y reinstala las reglas de acceso y RPC.
-- No es un instalador de base vacia ni corrige automaticamente datos incompatibles.
-- No usar el script V3 de una sola ejecucion para recuperar una base parcial.
-- Ejecutar ROLLBACK; por separado antes, si la consulta anterior dejo una transaccion abortada.
-- Copiar TODO este archivo en una consulta nueva de SQL Editor.
-- Reinstala las politicas de negocio del proyecto y conserva las de otros buckets.
-- Generado con node supabase/build_recovery.mjs.

`;
await writeFile(new URL('SUPABASE_RECUPERACION_V4.sql',import.meta.url),header+sql,'utf8');
console.log('Generado SUPABASE_RECUPERACION_V4.sql.');
