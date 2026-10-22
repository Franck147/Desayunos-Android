# Actualización Android 1.1.0

## Instalación sobre la base actual

1. En el SQL Editor de Supabase, ejecutar únicamente `supabase/migrations/202610220004_complete_order.sql`. Requiere las migraciones 001–003 o su equivalente ya instalado. Es reejecutable y no elimina tablas ni datos.
2. Instalar `output/apk/Desayunos-1.1.0-debug.apk` como actualización de la app.
3. Entrar como cliente y revisar Pedidos; entrar como ADMIN para registrar cobros y consultar el cierre de caja.

El archivo 004 agrega `public.complete_order(uuid,text,text)` y sus permisos para usuarios autenticados. Dentro de la función se valida el rol real del perfil. No cambia políticas RLS, cuentas, contraseñas ni configuración de Auth. El SQL debe aplicarse en el proyecto remoto antes de usar el nuevo botón de entrega. Esta entrega no lo ejecuta remotamente.

## Interfaz

- Pedidos con información en bloques verticales: número, fecha, estados, productos, indicaciones, total y pago.
- Estados que cambian de línea; nombres largos con varias líneas; campos y botones con altura flexible.
- Formularios desplazables con teclado abierto y texto ampliado, incluido registro y edición de productos.
- Una sola cabecera por pantalla, navegación inferior con etiquetas cortas y controles separados.
- Pedidos activos separados del historial de completados y cancelados.

## Cobro y entrega

- ADMIN puede registrar efectivo, Yape o Plin. Yape y Plin requieren referencia de operación.
- Un pedido debe pasar por En cola → En preparación → Listo para recoger.
- Si está listo y sin pagar, ADMIN usa **Cobrar y completar**: confirma que recibió el dinero y entregó el desayuno.
- Si ya está pagado, ADMIN o COOK usa **Entregar y completar** y confirma la entrega.
- COOK no puede cobrar ni completar un pedido sin pago.
- Al completar, Supabase guarda `DELIVERED` y la interfaz muestra **Completado · entregado**. Sale de la cola activa y queda en Historial para el cliente y Completados para el personal.
- El nuevo RPC agrupa cobro y entrega en una transacción. Un fallo revierte ambos; reintentar un pedido completado no duplica pago ni historial.
- Yape y Plin son registros manuales de un pago comprobado por el administrador. No hay integración automática con esas billeteras.

## Reportes

Selección de fecha, cobros confirmados, pedidos cobrados, completados, unidades, cancelaciones, desglose por método, productos cobrados y detalle de cobros. Los pendientes de pago se muestran aparte y no aumentan los ingresos.

La fecha de cobro se calcula en America/Lima; la de completado usa la última actualización del pedido. El reporte puede guardarse como CSV mediante el selector de archivos Android. Los importes del reporte se suman con BigDecimal y los definitivos se validan en PostgreSQL.

ADMIN puede cerrar la caja y ver el importe conciliado y la cantidad de pagos. El servidor impide nuevos cobros para una jornada cerrada. COOK tiene acceso al reporte, sin permiso para cerrar caja.

## Verificación local

Resultado: 18 pruebas JVM y 7 pruebas de interfaz aprobadas en el emulador, con ancho de 320 dp y escala de letra hasta 1.6. Las pruebas SQL aprobaron 38 comprobaciones de la base y 15 del nuevo RPC. APK 1.1.0 compilado; Lint terminó sin errores y con 329 advertencias.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --console=plain --max-workers=2
node supabase/tests/run.mjs
node supabase/tests/complete-order.test.mjs
```

Las pruebas de interfaz en `ResponsiveScreensTest.kt` cubren pedidos a 320 dp, texto ampliado, referencia de Yape obligatoria, permisos de cocina, paso al historial, reportes y registro desplazable. Usan datos de prueba locales, sin iniciar sesión ni modificar pedidos reales. La comprobación se realiza en emulador; queda por confirmar el resultado en el Honor 400 del usuario con sus datos reales.

Las pruebas SQL usan PostgreSQL efímero PGlite, sin modificar el Supabase remoto. Comprueban el RPC repetido, restricciones por rol, entrega pagada y rechazo después del cierre; un trigger de prueba simula un fallo de entrega para verificar que el cobro también se revierta.
