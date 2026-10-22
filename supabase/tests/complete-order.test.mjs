import { PGlite } from '../../.tools/sql-test/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

// Base local efímera: no usa credenciales ni modifica Supabase remoto.
const db = new PGlite();
let checks=0;
const sql=(query,params=[])=>db.query(query,params);
const value=async(query,params=[])=>Object.values((await sql(query,params)).rows[0])[0];
const equal=(actual,expected,message)=>{assert.deepEqual(actual,expected,message);checks++;};
const reject=async(query,params,pattern)=>{
  await db.exec('savepoint denied');
  let failure;
  try {await sql(query,params);} catch(error){failure=error;}
  await db.exec('rollback to savepoint denied; release savepoint denied');
  assert.ok(failure,'La operación debe rechazarse');
  assert.match(failure.message,pattern); checks++;
};
const ids={client:'11111111-1111-4111-8111-111111111111',cook:'22222222-2222-4222-8222-222222222222',admin:'33333333-3333-4333-8333-333333333333',product:'44444444-4444-4444-8444-444444444444'};
const user=async(name)=>{
  await db.exec('reset role');
  await sql("select set_config('request.jwt.claim.sub',$1,false)",[ids[name]]);
  await db.exec('set role authenticated');
};
const ready=async(quantity)=>{
  await user('client');
  const order=await value('select public.create_order(gen_random_uuid(),$1::jsonb)',[JSON.stringify([{menu_item_id:ids.product,quantity}])]);
  await user('cook');
  await sql("select public.transition_order($1,'PREPARING')",[order.id]);
  await sql("select public.transition_order($1,'READY')",[order.id]);
  return order;
};
try {
  for(const file of ['tests/bootstrap.sql','migrations/202610220001_core.sql','migrations/202610220003_cash_closures.sql','migrations/202610220004_complete_order.sql','migrations/202610220004_complete_order.sql']) {
    await db.exec(await readFile(new URL('../'+file,import.meta.url),'utf8'));
  }
  await db.exec('begin');
  for(const name of ['client','cook','admin']) {
    await sql('insert into auth.users(id,email,raw_user_meta_data) values($1,$2,$3)',[ids[name],name+'@test.invalid',JSON.stringify({full_name:name,phone:'999888777'})]);
  }
  await sql("update public.profiles set role='COOK' where id=$1",[ids.cook]);
  await sql("update public.profiles set role='ADMIN' where id=$1",[ids.admin]);
  await db.exec(`create function public.fail_delivery_test() returns trigger language plpgsql as $$
    begin if new.new_status='DELIVERED' and current_setting('test.fail_delivery',true)='on'
    then raise exception 'Fallo de entrega simulado'; end if; return new; end $$;
    create trigger fail_delivery_test before insert on public.order_status_history
    for each row execute function public.fail_delivery_test();`);
  await user('admin');
  await sql('select public.save_menu_item($1::jsonb)',[JSON.stringify({id:ids.product,name:'Pan',price:3.5,available_stock:10,category:'BREAD'})]);
  const first=await ready(2);
  await user('client');
  await reject("select public.complete_order($1,'CASH','')",[first.id],/personal autorizado/);
  await user('cook');
  await reject("select public.complete_order($1,'CASH','')",[first.id],/pendiente de pago/);
  await user('admin');
  await db.exec("select set_config('test.fail_delivery','on',false)");
  await reject("select public.complete_order($1,'CASH','')",[first.id],/simulado/);
  equal(Number(await value('select count(*) from public.payments')),0,'Fallo de entrega revierte también el cobro');
  equal(await value('select status from public.orders where id=$1',[first.id]),'READY');
  await db.exec("select set_config('test.fail_delivery','off',false)");
  const result=await value("select public.complete_order($1,'CASH','')",[first.id]);
  equal(result.status,'DELIVERED');
  equal(Number(result.payments.amount),7);
  equal(result.order_items.length,1);
  await sql("select public.complete_order($1,'CASH','')",[first.id]);
  equal(Number(await value('select count(*) from public.payments')),1,'Reintentar no cobra dos veces');
  equal(Number(await value("select count(*) from public.order_status_history where order_id=$1 and new_status='DELIVERED'",[first.id])),1,'Reintentar no duplica la entrega');
  const second=await ready(1);
  await user('admin');
  await reject("select public.complete_order($1,'YAPE','')",[second.id],/referencia/);
  await sql("select public.record_payment($1,'YAPE','REF-001')",[second.id]);
  await user('cook');
  equal((await value('select public.complete_order($1)',[second.id])).status,'DELIVERED','Cocina puede entregar un pedido pagado');
  const third=await ready(1);
  await user('admin');
  await sql("select public.close_cash_day((now() at time zone 'America/Lima')::date,10.5)");
  await reject("select public.complete_order($1,'CASH','')",[third.id],/cerrada/);
  equal(await value('select status from public.orders where id=$1',[third.id]),'READY');
  equal(Number(await value('select count(*) from public.payments where order_id=$1',[third.id])),0);
  await db.exec('rollback');
  console.log(`PASS: ${checks} comprobaciones de cobro y entrega atómicos.`);
} finally {await db.close();}
