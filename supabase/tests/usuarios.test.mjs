import test from 'node:test';
import assert from 'node:assert/strict';
import { accounts, provision } from '../crear_usuarios.mjs';

const url='https://test.supabase.co';
const key='fake-server-key-for-test';
const reply=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
function fakeServer({failProfile=false,duplicate=false}={}) {
  const calls=[]; const users=new Map();
  let rejectProfile=failProfile;
  return {calls,setProfileHealthy(){ rejectProfile=false; },async fetch(endpoint,options) {
    const path=new URL(endpoint).pathname;
    const body=options.body ? JSON.parse(options.body) : undefined;
    calls.push({path,...options,body});
    assert.equal(options.headers.apikey,key);
    if(path === '/auth/v1/admin/users' && options.method === 'POST') {
      if(duplicate) return reply({code:'email_exists',msg:'User already registered'},422);
      const id=`11111111-1111-4111-8111-${String(users.size+1).padStart(12,'0')}`;
      const user={id,email:body.email,app_metadata:body.app_metadata}; users.set(id,user); return reply(user);
    }
    if(path.startsWith('/auth/v1/admin/users/')) return reply(users.get(path.split('/').at(-1)));
    if(path === '/rest/v1/profiles' && options.method === 'PATCH') {
      if(rejectProfile) return reply({message:'Perfil no disponible'},503);
      return reply([{role:body.role}]);
    }
    throw new Error(`Solicitud no esperada: ${path}`);
  }};
}
test('crea 4 usuarios con contraseñas distintas y roles; reintentar no recrea Auth',async()=>{
  const ledger={url,users:[]}; const saved=[]; const server=fakeServer();
  const save=async value=>saved.push(structuredClone(value));
  const results=await provision({url,key,ledger,save,fetchImpl:server.fetch});
  assert.equal(results.length,4);
  assert.ok(results.every(it=>it.status === 'READY'));
  assert.equal(new Set(ledger.users.map(it=>it.password)).size,4);
  assert.ok(ledger.users.every(it=>it.password.length >= 24));
  assert.equal(saved[0].users[0].status,'PENDING','contraseña persistida antes del alta');
  assert.equal(JSON.stringify(ledger).includes(key),false);
  const posts=server.calls.filter(it=>it.path === '/auth/v1/admin/users');
  assert.equal(posts.length,4);
  assert.ok(posts.every(it=>it.body.email_confirm === true && !('role' in it.body.user_metadata)));
  assert.deepEqual(server.calls.filter(it=>it.path === '/rest/v1/profiles').map(it=>it.body.role),['ADMIN','COOK','CLIENT','CLIENT']);
  const originalPasswords=ledger.users.map(it=>it.password);
  await provision({url,key,ledger,save,fetchImpl:server.fetch});
  assert.equal(server.calls.filter(it=>it.path === '/auth/v1/admin/users').length,4);
  assert.deepEqual(ledger.users.map(it=>it.password),originalPasswords);
});
test('correo existente desconocido no recibe rol ni contraseña nueva',async()=>{
  const ledger={url,users:[]}; const server=fakeServer({duplicate:true});
  const result=await provision({url,key,plan:accounts.slice(0,1),ledger,save:async()=>{},fetchImpl:server.fetch});
  assert.equal(result[0].status,'FAILED');
  assert.equal(server.calls.length,1); assert.match(result[0].error,/No se cambió su contraseña/);
});
test('fallo del perfil se recupera sin volver a crear la cuenta',async()=>{
  const ledger={url,users:[]}; const server=fakeServer({failProfile:true});
  const args={url,key,plan:accounts.slice(0,1),ledger,save:async()=>{},fetchImpl:server.fetch};
  assert.equal((await provision(args))[0].status,'PROFILE_PENDING');
  server.setProfileHealthy();
  assert.equal((await provision(args))[0].status,'READY');
  assert.equal(server.calls.filter(it=>it.path === '/auth/v1/admin/users').length,1);
});
test('otro proyecto se rechaza antes de acceder a la red',async()=>{
  await assert.rejects(provision({url,key,ledger:{url:'https://other.supabase.co',users:[]},save:async()=>{},fetchImpl:()=>{ throw new Error('No debería llamar'); }}),/otro proyecto/);
});
