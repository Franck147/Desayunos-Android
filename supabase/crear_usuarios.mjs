import { randomBytes } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Cuentas ficticias para desarrollo. Cambiar los correos aquí si se necesitan reales.
export const accounts = [
  { email:'admin@desayunos.test', name:'Administrador de prueba', phone:'900000001', role:'ADMIN' },
  { email:'cocina@desayunos.test', name:'Cocinero de prueba', phone:'900000002', role:'COOK' },
  { email:'cliente1@desayunos.test', name:'Cliente de prueba 1', phone:'900000003', role:'CLIENT' },
  { email:'cliente2@desayunos.test', name:'Cliente de prueba 2', phone:'900000004', role:'CLIENT' }
];
const marker = 'desayunos-development-users-v1';
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** API administrativa oficial: no escribe directamente auth.users ni auth.identities. */
export async function provision({url,key,plan=accounts,ledger,save,fetchImpl=fetch}) {
  const endpoint=new URL(url);
  if(endpoint.protocol !== 'https:' || endpoint.pathname !== '/' || endpoint.search || endpoint.hash || endpoint.username || endpoint.password)
    throw new Error('SUPABASE_URL debe ser el origen HTTPS del proyecto, sin rutas ni credenciales.');
  if(!key) throw new Error('Falta SUPABASE_SERVICE_ROLE_KEY. No usar la clave anon.');
  if(ledger.url !== endpoint.origin) throw new Error('El archivo de credenciales pertenece a otro proyecto. No se modificó ninguna cuenta.');
  for(const account of plan) {
    if(!['ADMIN','COOK','CLIENT'].includes(account.role) || !account.email.includes('@')) throw new Error('Plan de usuarios inválido');
  }
  async function request(path,method='GET',payload) {
    const response=await fetchImpl(endpoint.origin+path,{
      method,headers:{ apikey:key,Authorization:`Bearer ${key}`,'Content-Type':'application/json',Prefer:'return=representation' },
      body:payload === undefined ? undefined : JSON.stringify(payload),signal:AbortSignal.timeout(30_000)
    });
    const text=await response.text();
    let result;
    try { result=text ? JSON.parse(text) : null; } catch { throw new Error(`Respuesta no válida del servidor (${response.status})`); }
    if(!response.ok) {
      const duplicate=['email_exists','user_already_exists'].includes(result?.error_code ?? result?.code)
        || /already (registered|exists)/i.test(result?.msg ?? result?.message ?? '');
      throw new Error(duplicate
        ? 'El correo ya existe. No se cambió su contraseña ni se le asignó otro rol; revíselo en Authentication → Users.'
        : `Supabase (${response.status}): ${result?.msg ?? result?.message ?? result?.error_description ?? 'operación rechazada'}`);
    }
    return result;
  }
  const results=[];
  for(const account of plan) {
    let record=ledger.users.find(it=>it.email === account.email);
    if(!record) {
      record={...account,password:randomBytes(18).toString('base64url')+'aA1!',id:null,status:'PENDING'};
      ledger.users.push(record);
      // Guardar antes de crear Auth para conservar la contraseña si se pierde la conexión.
      await save(ledger);
    }
    try {
      let user;
      if(record.id) {
        if(!uuid.test(record.id)) throw new Error('UUID local inválido');
        const response=await request(`/auth/v1/admin/users/${record.id}`);
        user=response.user ?? response;
      } else {
        const response=await request('/auth/v1/admin/users','POST',{
          email:account.email,password:record.password,email_confirm:true,
          user_metadata:{full_name:account.name,phone:account.phone},app_metadata:{desayunos_seed:marker}
        });
        user=response.user ?? response;
      }
      if(!uuid.test(user.id ?? '') || user.email?.toLowerCase() !== account.email.toLowerCase()
        || user.app_metadata?.desayunos_seed !== marker) throw new Error('No se verificó la identidad de esta cuenta de prueba. No se modificó su perfil.');
      record.id=user.id; record.status='PROFILE_PENDING'; delete record.error;
      await save(ledger);
      const profiles=await request(`/rest/v1/profiles?id=eq.${user.id}`,'PATCH',{
        full_name:account.name,phone:account.phone,role:account.role
      });
      if(!Array.isArray(profiles) || profiles.length !== 1 || profiles[0].role !== account.role)
        throw new Error('No se actualizó el perfil. Compruebe que el trigger de profiles esté instalado.');
      record.role=account.role; record.status='READY';
    } catch(error) {
      record.status=record.id ? 'PROFILE_PENDING' : 'FAILED';
      record.error=error.message;
    }
    await save(ledger);
    results.push({email:record.email,role:record.role,status:record.status,error:record.error ?? ''});
  }
  return results;
}

async function main() {
  const url=process.env.SUPABASE_URL;
  const key=process.env.SUPABASE_SERVICE_ROLE_KEY;
  if(!url || !key) throw new Error('Configure SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY en esta terminal. Consulte docs/USUARIOS_PRUEBA.md.');
  const directory=new URL('../.tools/',import.meta.url);
  const credentialsFile=new URL('usuarios-desayunos.json',directory);
  await mkdir(directory,{recursive:true});
  let ledger;
  try { ledger=JSON.parse(await readFile(credentialsFile,'utf8')); }
  catch(error) {
    if(error.code !== 'ENOENT') throw new Error('No se pudo leer el archivo local de credenciales. Consérvelo y revíselo antes de reintentar.');
    ledger={url:new URL(url).origin,users:[]};
  }
  const save=async value=>writeFile(credentialsFile,JSON.stringify(value,null,2)+'\n',{encoding:'utf8',mode:0o600});
  const results=await provision({url,key,ledger,save});
  console.table(results);
  console.log(`Credenciales locales: ${fileURLToPath(credentialsFile)}`);
  console.log('Solo las cuentas READY están listas para iniciar sesión. No comparta este archivo ni la clave administrativa.');
  if(results.some(it=>it.status !== 'READY')) process.exitCode=1;
}
if(process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error=>{ console.error(error.message); process.exitCode=1; });
}
