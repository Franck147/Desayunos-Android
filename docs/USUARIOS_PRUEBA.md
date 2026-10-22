# Crear cuentas de prueba

El script `supabase/crear_usuarios.mjs` crea usuarios mediante la API administrativa de Supabase Auth y después asigna los roles en profiles. Ejecutarlo en una terminal de confianza con Node 18 o posterior; no se pega en SQL Editor ni requiere instalar paquetes. Las migraciones de perfiles/roles deben estar instaladas.

| Correo de prueba | Rol |
| --- | --- |
| admin@desayunos.test | ADMIN |
| cocina@desayunos.test | COOK |
| cliente1@desayunos.test | CLIENT |
| cliente2@desayunos.test | CLIENT |

Son correos ficticios y contactos de prueba. El alta usa email_confirm=true para permitir probar el login sin enviar correos. Para cuentas reales, cambiar los correos en el arreglo accounts del script y verificar la identidad de sus titulares antes de usar la confirmación administrativa. Si se quieren dos administradores, cambiar COOK por ADMIN en el segundo registro antes de ejecutar.

## Ejecución en PowerShell

Desde la carpeta del proyecto:

```powershell
$env:SUPABASE_URL = 'https://edqupobvdavowrvgcjzs.supabase.co'
$seedKey = Read-Host 'Pega la clave service_role del proyecto (no se mostrará)' -AsSecureString
$env:SUPABASE_SERVICE_ROLE_KEY = [System.Net.NetworkCredential]::new('', $seedKey).Password
try {
    node supabase/crear_usuarios.mjs
} finally {
    Remove-Item Env:SUPABASE_SERVICE_ROLE_KEY -ErrorAction SilentlyContinue
    $seedKey.Dispose()
}
```

Usar la clave administrativa **service_role**, nunca la anon/publishable. No pegar la clave en este chat, en Android ni en un archivo versionado. La clave se utiliza solo en esta terminal y no se escribe en el archivo de credenciales. El endpoint administrativo requiere una clave de servidor, según la [documentación oficial](https://supabase.com/docs/reference/javascript/auth-admin-createuser).

## Contraseñas y reintentos

Las contraseñas son aleatorias, distintas por usuario y se guardan localmente en `.tools/usuarios-desayunos.json`, directorio ignorado por Git. Abrir el archivo local para consultar email/password e iniciar sesión en Android. Contiene contraseñas en texto legible: mantenerlo privado. El script muestra estados y correos en consola, sin imprimir contraseñas ni claves.

READY significa que Auth y el rol en profiles se configuraron. Si Auth se creó y falló la asignación de perfil, volver a ejecutar con el mismo archivo local reintenta ese paso sin cambiar la contraseña. No se cambian ni elevan cuentas preexistentes desconocidas con el mismo correo. Si se pierde la respuesta del alta, el script puede informar que el correo ya existe: revisar la cuenta en Authentication → Users antes de continuar. No se borra ninguna cuenta automáticamente.

## Alternativa con SQL Editor

Si se prefiere no usar una clave administrativa en la terminal, crear las cuatro cuentas en **Authentication → Users → Add user** con sus contraseñas y confirmación. Después ejecutar:

```sql
-- Ejecutar solo después de crear las cuentas y verificar estos correos.
UPDATE public.profiles
SET role = CASE email
    WHEN 'admin@desayunos.test' THEN 'ADMIN'
    WHEN 'cocina@desayunos.test' THEN 'COOK'
    ELSE 'CLIENT'
END
WHERE email IN (
    'admin@desayunos.test', 'cocina@desayunos.test',
    'cliente1@desayunos.test', 'cliente2@desayunos.test'
);

SELECT id, email, full_name, role
FROM public.profiles
WHERE email IN (
    'admin@desayunos.test', 'cocina@desayunos.test',
    'cliente1@desayunos.test', 'cliente2@desayunos.test'
);
```

Un INSERT en profiles por sí solo no crea una contraseña ni una cuenta válida de Auth. Esta alternativa SQL asigna roles a cuentas creadas desde Authentication.
