import { PGlite } from '../../.tools/sql-test/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

// PostgreSQL local efímero; sin red ni credenciales reales.
const db = new PGlite();
let assertions = 0;
const sql = (text,params=[]) => db.query(text,params);
const value = async (text,params=[]) => Object.values((await sql(text,params)).rows[0])[0];
const check = (actual,expected,message) => { assert.deepEqual(actual,expected,message); assertions++; };
const reject = async (text,params=[],pattern) => {
  await db.exec('savepoint denied');
  let failure;
  try { await sql(text,params); } catch(e) { failure=e; }
  await db.exec('rollback to savepoint denied; release savepoint denied');
  assert.ok(failure,`Debía rechazarse: ${text}`);
  if(pattern) assert.match(failure.message,pattern);
  assertions++;
};
const ids = { a:'11111111-1111-4111-8111-111111111111', b:'22222222-2222-4222-8222-222222222222',
  cook:'33333333-3333-4333-8333-333333333333', admin:'44444444-4444-4444-8444-444444444444',product:'55555555-5555-4555-8555-555555555555' };
const user = async (id,role='authenticated') => {
  await db.exec('reset role');
  await sql("select set_config('request.jwt.claim.sub',$1,false)",[id ?? '']);
  await db.exec(`set role ${role}`);
};
try {
  const recovery = process.argv.includes('--recovery');
  const files = recovery
    ? ['tests/bootstrap.sql','SUPABASE_RECUPERACION_V4.sql']
    : process.argv.includes('--combined')
    ? ['tests/bootstrap.sql','SUPABASE_COMPLETO.sql']
    : ['tests/bootstrap.sql','migrations/202610220001_core.sql','migrations/202610220002_storage.sql','migrations/202610220003_cash_closures.sql'];
  for(const file of files) {
    const content = await readFile(new URL('../'+file,import.meta.url),'utf8');
    if (file === 'SUPABASE_COMPLETO.sql') {
      check(content,await readFile(new URL('../SUPABASE_COMPLETO_V3.sql',import.meta.url),'utf8'),'V3 coincide con el script probado');
      if(process.argv.includes('--retry-after-failure')) {
        const failed = content.replace('-- supabase/migrations/202610220002_storage.sql',
          `-- supabase/migrations/202610220002_storage.sql\nDO LANGUAGE plpgsql $simulated_failure$ BEGIN RAISE EXCEPTION 'Fallo previo simulado'; END; $simulated_failure$;`);
        let failure;
        try { await db.exec(failed); } catch(e) { failure=e; }
        assert.match(failure?.message ?? '',/Fallo previo simulado/); assertions++;
        await db.exec('ROLLBACK');
        check(Number(await value("select count(*) from information_schema.columns where table_schema='public' and table_name='orders' and column_name='customer_id'")),0,'fallo revierte columnas nuevas');
      }
    }
    await db.exec(content);
    if(file === 'tests/bootstrap.sql' && process.argv.includes('--existing-core')) {
      await db.exec(await readFile(new URL('../migrations/202610220001_core.sql',import.meta.url),'utf8'));
    }
    if(file === 'tests/bootstrap.sql' && process.argv.includes('--partial-column')) {
      await db.exec('alter table public.orders add column customer_id uuid');
    }
    if(file === 'tests/bootstrap.sql' && process.argv.includes('--legacy-storage')) {
      await db.exec(`
        create policy "Acceso publico de lectura a imagenes" on storage.objects for select using(true);
        create policy "Permitir subir imagenes de desayunos" on storage.objects for insert to authenticated with check(true);
        create policy legacy_other_actions on storage.objects for all to authenticated using(bucket_id='other-bucket');
        create policy legacy_restriction on storage.objects as restrictive for all to authenticated
          using(bucket_id <> 'locked-bucket') with check(bucket_id <> 'locked-bucket');
      `);
    }
  }
  await db.exec('begin');
  for(const name of ['a','b','cook','admin']) {
    await sql('insert into auth.users(id,email,raw_user_meta_data) values($1,$2,$3)',
      [ids[name],name+'@test.invalid',JSON.stringify({full_name:'Mismo nombre',phone:'999888777',role:'ADMIN'})]);
  }
  check(await value('select role from public.profiles where id=$1',[ids.a]),'CLIENT','metadata no eleva rol');
  await sql("update public.profiles set role='ADMIN' where id=$1",[ids.admin]);
  await sql("update public.profiles set role='COOK' where id=$1",[ids.cook]);
  await db.exec(`create function public.test_detail_failure() returns trigger language plpgsql as $$
    begin if new.notes='TEST_FAIL' then raise exception 'Fallo de detalle simulado'; end if; return new; end $$;
    create trigger test_detail_failure before insert on public.order_items for each row execute function public.test_detail_failure();`);
  await user(ids.admin);
  await sql("insert into storage.objects(bucket_id,name) values('desayunos-imagenes','admin.jpg')");
  check(Number(await value("select count(*) from storage.objects where bucket_id='desayunos-imagenes'")),1,'ADMIN puede subir/listar imágenes');
  await sql('select public.save_menu_item($1::jsonb)',[JSON.stringify({id:ids.product,name:'Pan',price:2.5,available_stock:3,category:'BREAD'})]);
  const staleVersion=await value('select updated_at from public.menu_items where id=$1',[ids.product]);
  await user(ids.a);
  const items=JSON.stringify([{menu_item_id:ids.product,quantity:2,notes:'Sin salsa',unit_price:0.01}]);
  const request='66666666-6666-4666-8666-666666666666';
  const order=await value('select public.create_order($1,$2::jsonb)',[request,items]);
  check(order.customer_id,ids.a); check(Number(order.total_amount),5,'precio de servidor'); check(order.order_number,701,'secuencia histórica');
  check(await value('select available_stock from public.menu_items where id=$1',[ids.product]),1);
  check((await value('select public.create_order($1,$2::jsonb)',[request,items])).id,order.id,'reintento no duplica');
  check(Number(await value('select count(*) from public.order_items where order_id=$1',[order.id])),1);
  await reject('select public.create_order(gen_random_uuid(),$1::jsonb)',[JSON.stringify([{menu_item_id:ids.product,quantity:1,notes:'TEST_FAIL'}])],/simulado/);
  check(Number(await value('select count(*) from public.orders')),1,'fallo de detalle revierte cabecera');
  check(await value('select available_stock from public.menu_items where id=$1',[ids.product]),1,'fallo de detalle conserva stock');
  await reject('select public.create_order($1,$2::jsonb)',[request,JSON.stringify([{menu_item_id:ids.product,quantity:1}])],/otro carrito/);
  await reject('select public.create_order(gen_random_uuid(),$1::jsonb)',[items],/Stock insuficiente/);
  check(Number(await value('select count(*) from public.orders')),1,'fallo no crea cabecera');
  await reject('select public.create_order(gen_random_uuid(),$1::jsonb)',[JSON.stringify([{menu_item_id:ids.product,quantity:0}])],/Detalle inválido/);
  await reject("update public.profiles set role='ADMIN' where id=$1",[ids.a]);
  await reject("update public.menu_items set available_stock=999 where id=$1",[ids.product]);
  await reject('insert into public.orders(customer_name,customer_phone) values($1,$2)',['hack','999']);
  await reject("select public.transition_order($1,'READY')",[order.id],/Transición/);
  await reject("insert into storage.objects(bucket_id,name) values('desayunos-imagenes','hack.jpg')");
  check(Number(await value("select count(*) from storage.objects where bucket_id='desayunos-imagenes'")),0,'cliente no lista imágenes de administración');
  if(process.argv.includes('--legacy-storage')) {
    await sql("insert into storage.objects(bucket_id,name) values('other-bucket','preserved.jpg')");
    check(Number(await value("select count(*) from storage.objects where bucket_id='other-bucket'")),1,'otros buckets conservan acceso');
    await sql("update storage.objects set name='edited.jpg' where bucket_id='other-bucket'");
    check(await value("select name from storage.objects where bucket_id='other-bucket'"),'edited.jpg','UPDATE conserva WITH CHECK implícito');
    await reject("insert into storage.objects(bucket_id,name) values('locked-bucket','blocked.jpg')");
  }
  await user(ids.b);
  check(Number(await value('select count(*) from public.orders')),0,'mismo nombre no comparte pedidos');
  check(Number(await value('select count(*) from public.order_items')),0);
  await reject("select public.transition_order($1,'CANCELLED','No es mío')",[order.id],/no accesible/);
  await reject("select public.transition_order((select id from public.orders where order_number=700),'CANCELLED','Histórico')",[],/no accesible/);
  await reject("select public.transition_order((select id from public.orders limit 1),'CANCELLED','No')",[],/no accesible/);
  await user(ids.a);
  await sql("select public.transition_order($1,'CANCELLED','Cambio de planes')",[order.id]);
  await sql("select public.transition_order($1,'CANCELLED','Reintento')",[order.id]);
  check(await value('select available_stock from public.menu_items where id=$1',[ids.product]),3,'devolución única');
  const paidOrder=await value('select public.create_order(gen_random_uuid(),$1::jsonb)',[JSON.stringify([{menu_item_id:ids.product,quantity:1}])]);
  await user(ids.cook);
  await reject("select public.record_payment($1,'CASH','')",[paidOrder.id],/administración/);
  await sql("select public.transition_order($1,'PREPARING')",[paidOrder.id]);
  await sql("select public.transition_order($1,'READY')",[paidOrder.id]);
  await reject("select public.transition_order($1,'DELIVERED')",[paidOrder.id],/pago/);
  await user(ids.admin);
  await reject('select public.save_menu_item($1::jsonb,$2::timestamptz)',[JSON.stringify({id:ids.product,name:'Viejo',price:2.5,available_stock:99,category:'BREAD'}),staleVersion],/cambió/);
  await reject("select public.record_payment($1,'YAPE','')",[paidOrder.id],/referencia/);
  await sql("select public.record_payment($1,'CASH','')",[paidOrder.id]);
  await sql("select public.record_payment($1,'CASH','')",[paidOrder.id]);
  check(Number(await value('select count(*) from public.payments')),1,'pago único');
  await reject("select public.record_payment($1,'PLIN','otra')",[paidOrder.id],/otro pago/);
  await sql("select public.transition_order($1,'DELIVERED')",[paidOrder.id]);
  await sql("select public.set_accepting_orders(false)");
  await user(ids.a);
  await reject('select public.create_order(gen_random_uuid(),$1::jsonb)',[items],/cerrada/);
  await user(ids.admin);
  await sql("select public.set_accepting_orders(true)");
  await sql("select public.close_cash_day((now() at time zone 'America/Lima')::date,2.5)");
  await user(ids.a);
  const afterClose=await value('select public.create_order(gen_random_uuid(),$1::jsonb)',[JSON.stringify([{menu_item_id:ids.product,quantity:1}])]);
  await user(ids.admin);
  await reject("select public.record_payment($1,'CASH','')",[afterClose.id],/cerrada/);
  await user(null,'anon');
  await reject("insert into storage.objects(bucket_id,name) values('desayunos-imagenes','anon.jpg')");
  await reject('select * from public.orders');
  await reject('select public.create_order(gen_random_uuid(),$1::jsonb)',[items]);
  await db.exec('reset role');
  if(recovery) {
    await db.exec('commit');
    const snapshot=async()=>{
      const result={};
      for(const table of ['profiles','orders','order_items','menu_items','announcements','order_status_history','product_stock_movements','payments','business_settings','daily_cash_closures']) {
        result[table]=(await sql(`select to_jsonb(t) row from public.${table} t order by to_jsonb(t)::text`)).rows;
      }
      result.indexes=(await sql("select indexname from pg_indexes where schemaname='public' order by indexname")).rows;
      return result;
    };
    const before=await snapshot();
    const recoverySql=await readFile(new URL('../SUPABASE_RECUPERACION_V4.sql',import.meta.url),'utf8');
    await db.exec(recoverySql);
    check(await snapshot(),before,'recuperación repetida conserva datos e índices');
    await db.exec(recoverySql);
    check(await snapshot(),before,'tercera ejecución conserva datos e índices');
    check(Number(await value("select count(*) from pg_constraint where conrelid='public.orders'::regclass and conname='orders_customer_id_fkey'")),1,'FK de customer_id instalada una sola vez');
  } else await db.exec('rollback');
  console.log(`PASS: ${assertions} comprobaciones SQL (migraciones, RLS, stock, identidad, pagos, cierre e idempotencia).`);
} finally { await db.close(); }
