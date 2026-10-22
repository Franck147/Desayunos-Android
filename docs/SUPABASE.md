# Supabase: cambios y puesta en marcha

Implementación del 22 de octubre de 2026. Los SQL están preparados y probados localmente; **no se han aplicado al proyecto remoto**. Android ahora requiere estas migraciones y ya no utiliza accesos de prueba ni datos de ejemplo.

## Orden de aplicación

**Si aparece `column "customer_id" ... already exists`, no repetir V3.** La base ya contiene al menos parte del cambio. Ejecutar `ROLLBACK;` por separado y usar `supabase/SUPABASE_RECUPERACION_V4.sql` completo en una consulta nueva. V4 admite objetos de estas migraciones ya instalados, conserva datos y completa las relaciones ausentes. Reinstala RPC, triggers y políticas de negocio y mantiene la protección de Storage. No corrige automáticamente esquemas ajenos ni datos incompatibles.

La recuperación se genera con `node supabase/build_recovery.mjs` después de generar V3. Se probó con el esquema original, con customer_id existente sin FK, con 001 aplicada y con políticas antiguas de Storage. Cada escenario pasó 44 comprobaciones, incluidas dos reejecuciones adicionales que verifican que datos e índices permanecen iguales. Comandos: `node supabase/tests/run.mjs --recovery --partial-column --legacy-storage` y `node supabase/tests/run.mjs --recovery --existing-core --legacy-storage`.

También está disponible `supabase/SUPABASE_COMPLETO.sql`, que reúne 001, 002 y 003 en una sola transacción. Es una alternativa a ejecutar los tres archivos: **no ejecutar ambos métodos ni usarlo si ya aplicaste alguna de las migraciones**. Actualiza el esquema original existente; no crea una base desde cero. Validación local: `node supabase/tests/run.mjs --combined`.

La revisión 3 también se entrega como `supabase/SUPABASE_COMPLETO_V3.sql`, idéntica al archivo completo probado. Cada bloque tiene delimitadores identificados y el bloque de Storage declara explícitamente el lenguaje. Se genera desde las migraciones con `node supabase/build_bundle.mjs`. Ante un error de sintaxis de una consulta anterior, ejecutar `ROLLBACK;`, abrir una consulta nueva y pegar el archivo V3 entero, sin mezclar fragmentos con versiones previas. Recuperación probada con `node supabase/tests/run.mjs --combined --legacy-storage --retry-after-failure`: 44 comprobaciones aprobadas.

Si SQL Editor muestra “Potential issues detected”, ejecutar V3 con **Run without RLS**: esta opción deja ejecutar el SQL preparado, que ya habilita RLS y configura sus políticas. La habilitación de las tablas principales está dentro de un bloque dinámico y el aviso del editor puede no reconocerla. “Run and enable RLS” agrega SQL generado por el editor; esta migración ya incluye esa configuración. El aviso de operaciones destructivas corresponde a retirar FK redundantes, sustituir políticas y revocar permisos amplios; el archivo no incluye DROP TABLE, TRUNCATE ni DELETE de datos.

1. Respaldar la base y los objetos de Storage; crear un entorno de ensayo con el mismo esquema. Conservar una copia de las políticas actuales para revisar y recuperar su definición si hace falta.
2. Ejecutar `supabase/preflight.sql` (solo lectura). Comprobar que corresponde al esquema compartido. Los contadores de datos inválidos deben ser cero; corregir datos según el negocio, sin eliminarlos automáticamente. Revisar triggers de alta de usuarios que ya existan y las FK: el script espera que existan `fk_order_items_order` y `fk_order_items_menu`.
3. Reservar una ventana de mantenimiento y detener escrituras de la app anterior: enviaba números locales y escribía directamente en tablas. Evitar que convivan clientes antiguos con el nuevo contrato.
4. Ejecutar **completo y una sola vez**, en orden:
   - `supabase/migrations/202610220001_core.sql`
   - `supabase/migrations/202610220002_storage.sql`
   - `supabase/migrations/202610220003_cash_closures.sql`
5. Si usas Supabase CLI, usar su registro de migraciones y `supabase db push` contra el proyecto de ensayo primero. Si usas SQL Editor, registrar qué archivos se aplicaron; no volver a ejecutarlos como si fueran idempotentes. Cada archivo tiene BEGIN/COMMIT y un fallo debe revertir ese archivo completo.
6. Asignar al encargado el rol ADMIN desde SQL Editor con una cuenta confiable; asignar COOK solo al personal autorizado. Todos los registros nuevos reciben CLIENT.
7. Configurar y sincronizar Android Studio, iniciar sesión y verificar los casos de aceptación de esta guía antes de habilitar clientes.

No ejecutar `supabase/tests/bootstrap.sql` en Supabase: crea un entorno falso exclusivo de las pruebas locales. Tampoco ejecutar el SQL contextual del chat como si fuera una migración.

## 001: estructura y relaciones

| Tabla | Cambios |
| --- | --- |
| profiles | role obligatorio, limitado a CLIENT/COOK/ADMIN; completar perfiles ausentes; trigger de creación a partir de auth.users, siempre CLIENT |
| orders | customer_id → profiles.id; request_id y request_payload para idempotencia; updated_at y cancelled_reason; UNIQUE(customer_id, request_id); validación de estado e importe; retirar teléfono ficticio por defecto |
| order_items | Retirar las dos FK redundantes `order_items_*_fkey`, conservando `fk_order_items_*`; exigir cantidad positiva, precio válido y subtotal coherente |
| menu_items | updated_at, disponibilidad no nula, categorías conocidas, precio no negativo con máximo dos decimales y stock no negativo |
| announcements | author_id → profiles.id; escrituras autorizadas y persistentes |
| order_status_history (nueva) | Pedido, estado anterior/nuevo, actor, motivo y fecha |
| product_stock_movements (nueva) | Producto, variación, motivo, pedido opcional, actor y fecha; reservas y devoluciones únicas por pedido/producto/motivo |
| payments (nueva) | Un pago íntegro confirmado por pedido, importe del servidor, método CASH/YAPE/PLIN, referencia, actor y fecha |
| business_settings (nueva) | Fila única con accepting_orders para abrir o cerrar recepción |
| stock_inventory | Conservada sin borrar datos; retirar acceso desde la app. Ya no se usa como segundo contador independiente |

Se agregan índices de FK/consultas de pedidos, detalles, historial y movimientos. Se adelanta la secuencia de order_number al máximo existente sin retrocederla. Los nuevos números se generan en PostgreSQL; habrá saltos normales de secuencia si una transacción falla.

Los defaults de created_at de pedidos, carta y avisos pasan a `now()`. Los datos siguen siendo timestamptz y Android usa America/Lima para reportes. No se reinterpreta ni modifica la fecha de registros históricos.

Los pedidos anteriores conservan customer_id NULL. Solo el personal autorizado puede consultarlos; no se atribuyen por nombre o teléfono. Conciliar su propiedad manualmente si corresponde. Las cancelaciones históricas no devuelven stock si no existe una reserva registrada por el nuevo sistema.

## Funciones SQL del contrato Android

Todas las RPC requieren authenticated, verifican identidad/rol en servidor y usan SECURITY DEFINER con search_path vacío y nombres calificados. Se revoca EXECUTE a PUBLIC y anon. No se concede escritura directa de negocio a Android.

| Función | Parámetros | Comportamiento |
| --- | --- | --- |
| create_order | p_request_id uuid, p_items jsonb | Obtiene propietario/contacto desde Auth/profiles; valida cantidades y productos; consolida detalles; bloquea stock en orden; calcula precios, subtotal y total; genera número, cabecera, detalles, historial y movimientos en una transacción |
| transition_order | p_order_id uuid, p_status text, p_reason text nullable | Cocina/ADMIN: PENDING → PREPARING → READY → DELIVERED. Cliente propietario o personal: PENDING → CANCELLED con motivo y sin pago. Entregar requiere pago. Repetir el mismo estado no duplica efectos |
| save_menu_item | p_item jsonb, p_expected_updated_at timestamptz nullable | Solo ADMIN; crea/edita carta y registra variaciones de stock. La versión del producto abierto en el editor debe coincidir para evitar sobrescribir cambios recientes |
| publish_announcement | p_title text, p_message text | Solo ADMIN; título 1–150 caracteres y mensaje 1–2000 |
| record_payment | p_order_id uuid, p_method text, p_reference text nullable | Solo ADMIN; toma el total real del pedido. Yape/Plin requieren referencia verificada. Repetir el mismo pago no duplica cobros; otro método/referencia se rechaza |
| set_accepting_orders | p_accepting boolean | Solo ADMIN; abre/cierra recepción. Reintentar un pedido ya confirmado sigue devolviendo el mismo pedido aunque la recepción cierre |
| close_cash_day | p_date date, p_expected_total numeric | Solo ADMIN; verifica el total confirmado, guarda cierre único y bloquea cobros posteriores en esa jornada |

Ejemplo del cuerpo de create_order enviado por Android:

```json
{
  "p_request_id": "66666666-6666-4666-8666-666666666666",
  "p_items": [
    {"menu_item_id": "55555555-5555-4555-8555-555555555555", "quantity": 2, "notes": "Sin salsa"}
  ]
}
```

Los precios y el importe no se aceptan del cliente. La app persiste el identificador y el carrito enviado antes de llamar la RPC. Ante pérdida de conexión conserva ese intento, impide editarlo y permite reintentar sin duplicar el pedido, incluso después de reiniciar la app. Un rechazo 4xx definitivo permite corregir el carrito. El servidor rechaza reutilizar el mismo identificador con otro contenido.

## RLS y permisos

Se retiran todas las políticas previas de las tablas de negocio incluidas en 001, porque mantener una política ALL/public/true anularía el aislamiento. Se habilita RLS y se revocan los grants amplios de tablas.

| Recurso | CLIENT | COOK | ADMIN |
| --- | --- | --- | --- |
| profiles | Leer y actualizar nombre/teléfono propios | Igual | Igual; gestión de roles desde entorno confiable |
| menu_items | Leer disponibles | Leer todos | Leer todos; editar por RPC |
| orders/details/history/payments | Leer propios; crear/cancelar mediante RPC | Leer pedidos; transicionar/cancelar mediante RPC | Igual y confirmar pagos |
| product_stock_movements | Sin acceso | Lectura | Lectura; ajustes por save_menu_item |
| announcements/settings | Lectura | Lectura | Lectura; cambios mediante RPC |
| stock_inventory | Sin acceso | Sin acceso | Sin acceso desde Android; datos históricos conservados |
| daily_cash_closures | Sin acceso | Sin acceso | Lectura/cierre por RPC |

El usuario no puede editar su email/UUID/rol desde profiles. RLS por fila se complementa con permisos de columnas para limitar la edición de contacto. Los roles no se toman de user_metadata ni de una opción visual de Android.

Asignación inicial, tras crear y confirmar las cuentas reales en Auth:

```sql
-- Reemplazar por los UUID reales, verificados en Authentication → Users.
update public.profiles set role = 'ADMIN' where id = 'UUID-REAL-DEL-ENCARGADO'::uuid;
update public.profiles set role = 'COOK' where id = 'UUID-REAL-DEL-COCINERO'::uuid;
```

No publicar una RPC general para modificar roles ni incluir service_role en Android.

## 002: Storage

- Bucket público `desayunos-imagenes`, máximo 5 MB, tipos JPEG/PNG/WebP.
- Solo ADMIN autenticado puede subir/listar objetos mediante las nuevas políticas. La descarga de la URL pública de una imagen publicada no requiere sesión.
- Archivos con UUID y extensión según MIME; Android no sustituye un fallo por una URI local.
- No se permiten borrados ni reemplazos desde Android en esta versión; revisar archivos huérfanos desde administración si una subida termina y falla el guardado del producto.

El archivo 002 adapta las políticas existentes de `storage.objects` para que conserven su comportamiento en otros buckets y dejen `desayunos-imagenes` bajo las nuevas reglas ADMIN. Las políticas permisivas anteriores excluyen este bucket; las restrictivas anteriores lo exceptúan para que no anulen las nuevas reglas. Conserva los archivos y los nombres de las políticas previas, incluidas `Acceso publico de lectura a imagenes` y `Permitir subir imagenes de desayunos`. Reemplaza únicamente las dos políticas propias `desayuno_images_*` si ya existían.

Si el script completo anterior falló con el mensaje de políticas existentes, ejecutar `ROLLBACK;` para cerrar cualquier transacción abortada y volver a ejecutar el archivo completo actualizado. Los cambios de esquema/datos anteriores al error pertenecían a la misma transacción y no se confirmaron. Si se aplicó 001 por separado, continuar con 002 actualizado y después 003, sin repetir 001.

## 003: caja

Se crea `daily_cash_closures` con fecha de negocio como PK, total, número de pagos, actor y fecha de cierre. Un trigger en payments comparte un bloqueo por jornada con close_cash_day: el cierre no puede conciliar un total mientras entra un pago sin tenerlo en cuenta. Los límites de jornada se calculan en America/Lima.

Cerrar la caja requiere conciliar previamente. Si se intenta cerrar con una vista desactualizada, el servidor exige refrescar. Tras cerrar la jornada no se aceptan nuevos cobros en ella. El siguiente día abre implícitamente una nueva jornada. No existe reapertura desde Android en esta versión.

No hay devoluciones/reembolsos automáticos: un pedido pagado no se puede cancelar. Implementar la operación auditada de devolución antes de ampliar esa regla. Registrar Yape/Plin es una confirmación manual del encargado; no integra ni verifica automáticamente un proveedor de pagos.

## Auth y Android Studio

Ante `Database error querying schema`, consultar Logs → Auth. La guía oficial relaciona este error con campos de texto nulos en auth.users, aunque también hay que revisar el detalle del log. `supabase/DIAGNOSTICO_LOGIN.sql` es de solo lectura para Adler/Franck; `supabase/REPARAR_LOGIN_AUTH.sql` normaliza exclusivamente los campos de texto conocidos que estén NULL para esas dos cuentas, conserva valores no nulos, roles, contraseñas e identidades y devuelve una comprobación sin secretos. Esta reparación cubre esa causa, no demuestra ni corrige todos los errores de Auth. Prueba local: `node --test supabase/tests/login-auth.test.mjs`. Si persiste, revisar el error interno de los logs antes de modificar permisos, triggers o cuentas.

El diagnóstico remoto compartido después mostró perfiles CLIENT, correos confirmados, contraseña presente, campos de texto sin NULL e identidades=0 para Adler/Franck. `supabase/REPARAR_IDENTIDADES_Y_ROLES.sql` repara las identidades email ausentes en el esquema actual con provider_id, manteniendo UUID/contraseñas y sin reasignar identidades de terceros. Asigna ADMIN a Adler y COOK a Franck en profiles; no modifica auth.users.role. Solo crea una identidad cuando falta, por lo que admite reejecución. Tras aplicarlo, deben verse los roles previstos e identidades_email=1. Esto repara inconsistencias observadas; el login real todavía debe comprobarse. Se verificaron repetición, preservación de datos y rechazo de conflictos con `node --test supabase/tests/identidades.test.mjs`. Para nuevas altas, usar siempre Authentication/API. La estructura de identidad se contrastó con el [código oficial de Supabase Auth](https://github.com/supabase/auth/blob/master/internal/api/admin.go).

Para crear un ADMIN, un COOK y dos CLIENT con contraseña válida, usar `supabase/crear_usuarios.mjs`. La guía `docs/USUARIOS_PRUEBA.md` contiene los comandos PowerShell y una alternativa desde Authentication + SQL Editor. El script usa la API administrativa de Auth, genera contraseñas aleatorias y no requiere instalar dependencias. Sus cuatro pruebas locales se ejecutan con `node --test supabase/tests/usuarios.test.mjs`.

- Mantener Email/Password habilitado. Si Confirm email está activado, el registro muestra que debe confirmar y no fabrica una sesión.
- Revisar Site URL y Redirect URLs para que la confirmación de correo tenga un destino válido. Esta entrega no implementa deep links de recuperación de contraseña.
- El trigger crea profiles para altas posteriores; el backfill completa usuarios existentes con email sin sobrescribir perfiles/roles previos.
- Sesión guardada con Android Keystore y preferencias cifradas; renovación de access_token con refresh_token y limpieza ante renovación inválida. Se excluyen sesión e intentos pendientes de backup/transferencia.
- Cerrar sesión en esta entrega elimina la sesión local. La revocación global de otras sesiones se administra desde Auth.

En `local.properties` (ignorado por Git), o como variables de entorno de compilación:

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_ANON_KEY=TU-CLAVE-PUBLICA
```

Se trasladó a local.properties la configuración pública que ya tenía este checkout. En otra máquina hay que configurarla antes de ejecutar la app. No es una clave secreta: la protección depende de JWT, grants, RPC y RLS.

## Sincronización, Realtime y alcance pendiente

La versión implementada carga datos del servidor al iniciar sesión, refresca cada 10 segundos con la app en primer plano y permite actualizar manualmente. Cocina y cliente comparten datos persistentes entre teléfonos. No hay que modificar `supabase_realtime` para este mecanismo.

La notificación local se muestra al cliente cuando detecta que uno de sus pedidos pasó a READY mientras la app estaba sincronizando. **No es push con la app cerrada**. Realtime por WebSocket y push con FCM/backend quedan como la siguiente mejora; habilitar una publicación por sí solo no las implementa.

Se eligió la estrategia de stock de productos vendibles del plan. No se descuentan litros ni ingredientes por recetas y no se mantiene el antiguo contador global. Recetas, porciones, componentes de combos, compras, merma, proveedores, programación por cupos, sedes y fidelización siguen pendientes de reglas concretas de negocio. El stock de un combo representa combos preparados/vendibles independientes, no una deducción automática de componentes.

Los importes definitivos se calculan con numeric en PostgreSQL. Las pantallas aún usan Double para sus modelos de presentación; el valor mostrado del carrito es una estimación y el pedido confirmado devuelve el precio vigente. La migración completa del modelo de presentación a céntimos/BigDecimal queda pendiente.

## Pruebas reproducibles

Actualización 1.1.0: aplicar también `supabase/migrations/202610220004_complete_order.sql`. Agrega el cobro y la entrega atómicos con validación de roles y reintentos sin duplicados. Consultar `docs/ACTUALIZACION_INTERFAZ_Y_PAGOS.md` para la interfaz, reportes, instalación y pruebas actuales. Los resultados siguientes corresponden a la entrega inicial.

Resultado de esta entrega: compilación y APK debug correctos; 12 pruebas Android/JVM aprobadas; 35 comprobaciones SQL aprobadas. Lint terminó con 0 errores, 326 advertencias y 1 sugerencia. No se ejecutó la app en un dispositivo/emulador ni se aplicaron cambios al proyecto Supabase remoto. Los reportes locales están en `app/build/reports/tests/testDebugUnitTest/index.html` y `app/build/reports/lint-results-debug.html`; el APK en `app/build/outputs/apk/debug/app-debug.apk`.

Android:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain --max-workers=2
```

SQL sin Supabase real (Node instalado; descarga inicial del motor):

```powershell
npm.cmd install --prefix .tools/sql-test --cache .tools/npm-cache --no-audit --no-fund @electric-sql/pglite@0.5.11
node supabase/tests/run.mjs
```

El runner crea PostgreSQL efímero en memoria, emula auth.uid()/roles/Storage, aplica las tres migraciones y prueba permisos, propietarios, importes, idempotencia, stock, pagos y cierre. No modifica tu backend y no demuestra concurrencia real de varias conexiones ni reproduce todo el servicio Auth/Storage.

Antes de producción, usar dos dispositivos/cuentas en ensayo para verificar: login real/confirmación, cliente sin permisos de cocina, nombres iguales con historial separado, competición por la última unidad, corte de red después de enviar, reintento tras reinicio, edición de producto con versión antigua, pago verificado, cancelación con devolución única, cierre de caja y upload permitido solo al ADMIN.

Las fuentes de referencia son [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [funciones](https://supabase.com/docs/guides/database/functions), [sesiones](https://supabase.com/docs/guides/auth/sessions) y [Storage](https://supabase.com/docs/guides/storage/security/access-control) de Supabase.
