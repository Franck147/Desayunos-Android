import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '../../.tools/sql-test/node_modules/@electric-sql/pglite/dist/index.js';

test('reparación de Auth limita cambios a NULL y a los dos correos indicados',async()=>{
  const db=new PGlite();
  try {
    await db.exec(await readFile(new URL('bootstrap.sql',import.meta.url),'utf8'));
    await db.exec(`
      create role supabase_auth_admin;
      grant usage on schema auth to supabase_auth_admin;
      grant select,update on auth.users to supabase_auth_admin;
      alter table auth.users add column email_confirmed_at timestamptz;
      alter table auth.users add column encrypted_password text;
      alter table auth.users add column confirmation_token text;
      alter table auth.users add column recovery_token text;
      alter table auth.users add column email_change_token_new text;
      create table auth.identities(id uuid primary key default gen_random_uuid(),user_id uuid references auth.users(id));
      insert into auth.users(id,email,encrypted_password,recovery_token) values
      ('11111111-1111-4111-8111-111111111111','adler@desayunos.com','HASH_SENTINEL','ACTIVE_TOKEN_SENTINEL'),
      ('22222222-2222-4222-8222-222222222222','franck@desayunos.com','HASH_SENTINEL_2',null),
      ('33333333-3333-4333-8333-333333333333','otro@example.com','HASH_SENTINEL_3',null);
      insert into public.profiles(id,email,role) select id,email,'COOK' from auth.users;
      insert into auth.identities(user_id) select id from auth.users;
    `);
    const diagnostic=await readFile(new URL('../DIAGNOSTICO_LOGIN.sql',import.meta.url),'utf8');
    const before=await db.exec(diagnostic);
    assert.equal(before[0].rows.length,2);
    assert.ok(before[0].rows[0].campos_texto_en_null.includes('confirmation_token'));
    assert.ok(!JSON.stringify(before).includes('HASH_SENTINEL'));
    assert.ok(!JSON.stringify(before).includes('ACTIVE_TOKEN_SENTINEL'));
    const repair=await readFile(new URL('../REPARAR_LOGIN_AUTH.sql',import.meta.url),'utf8');
    const result=await db.exec(repair);
    const summary=result.at(-1).rows;
    assert.ok(summary.every(it=>it.campos_texto_en_null.length === 0 && it.tiene_password && Number(it.identidades) === 1 && it.rol_app === 'COOK'));
    assert.ok(!JSON.stringify(summary).includes('HASH_SENTINEL'));
    const rows=(await db.query('select email,encrypted_password,confirmation_token,recovery_token,email_confirmed_at from auth.users order by email')).rows;
    assert.equal(rows[0].recovery_token,'ACTIVE_TOKEN_SENTINEL');
    assert.equal(rows[0].encrypted_password,'HASH_SENTINEL');
    assert.equal(rows[0].email_confirmed_at,null);
    assert.equal(rows[2].confirmation_token,null,'otro usuario no cambia');
    assert.equal(rows[2].recovery_token,null);
    await db.exec(repair);
    assert.deepEqual((await db.query('select email,encrypted_password,confirmation_token,recovery_token,email_confirmed_at from auth.users order by email')).rows,rows,'repetir no cambia datos válidos');
  } finally { await db.close(); }
});
