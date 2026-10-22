import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '../../.tools/sql-test/node_modules/@electric-sql/pglite/dist/index.js';

async function fixture() {
  const db=new PGlite();
  await db.exec(await readFile(new URL('bootstrap.sql',import.meta.url),'utf8'));
  await db.exec(`
    alter table auth.users add column email_confirmed_at timestamptz;
    alter table auth.users add column encrypted_password text;
    create table auth.identities(id uuid primary key,user_id uuid references auth.users(id),
      provider_id text not null,provider text not null,identity_data jsonb not null,
      email text generated always as (identity_data->>'email') stored,
      created_at timestamptz,updated_at timestamptz,unique(provider_id,provider));
    insert into auth.users(id,email,encrypted_password,email_confirmed_at) values
      ('11111111-1111-4111-8111-111111111111','adler@desayunos.com','PASSWORD_HASH_1',now()),
      ('22222222-2222-4222-8222-222222222222','franck@desayunos.com','PASSWORD_HASH_2',now()),
      ('33333333-3333-4333-8333-333333333333','otro@example.com','PASSWORD_HASH_3',now());
    insert into public.profiles(id,email,role) select id,email,'CLIENT' from auth.users;
  `);
  return db;
}
const script=await readFile(new URL('../REPARAR_IDENTIDADES_Y_ROLES.sql',import.meta.url),'utf8');
test('restaura las dos identidades y roles sin cambiar usuarios ni duplicar registros',async()=>{
  const db=await fixture();
  try {
    const users=(await db.query('select * from auth.users order by id')).rows;
    const result=(await db.exec(script)).at(-1).rows;
    assert.deepEqual(result.map(it=>[it.email,it.rol_app,Number(it.identidades_email)]),[
      ['adler@desayunos.com','ADMIN',1],['franck@desayunos.com','COOK',1]]);
    const identities=(await db.query('select * from auth.identities order by user_id')).rows;
    assert.ok(identities.every(it=>it.provider_id===it.user_id && it.identity_data.sub===it.user_id));
    await db.exec(script);
    assert.deepEqual((await db.query('select * from auth.identities order by user_id')).rows,identities);
    assert.deepEqual((await db.query('select * from auth.users order by id')).rows,users);
    assert.equal((await db.query("select role from public.profiles where email='otro@example.com'")).rows[0].role,'CLIENT');
  } finally { await db.close(); }
});
test('conflicto de identidad revierte sin reasignarla ni elevar roles',async()=>{
  const db=await fixture();
  try {
    await db.exec(`insert into auth.identities(id,user_id,provider_id,provider,identity_data)
      values(gen_random_uuid(),'33333333-3333-4333-8333-333333333333',
      '11111111-1111-4111-8111-111111111111','email','{"email":"adler@desayunos.com"}')`);
    await assert.rejects(db.exec(script),/otra cuenta/);
    await db.exec('rollback');
    assert.ok((await db.query('select role from public.profiles')).rows.every(it=>it.role==='CLIENT'));
    assert.equal((await db.query('select count(*) from auth.identities')).rows[0].count,1);
  } finally { await db.close(); }
});
