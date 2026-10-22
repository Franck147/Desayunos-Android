# Acceso y notificaciones - Android 1.2.0

## Cambios

- Login con identidad visual de desayuno, fondo suave, tarjetas de acceso Cliente/Personal y formulario con jerarquía clara.
- Campos con iconos, contraseña visible/oculta, navegación del teclado, validación, error accesible y estado de envío. Las contraseñas no se conservan al cambiar de acceso ni al terminar un ingreso correcto.
- Formularios desplazables, roles que se apilan con letra ampliada y colores adaptados a tema claro/oscuro.
- Campana con contador de avisos sin leer. Perfil, actualización y cierre de sesión pasan al menú de opciones para liberar espacio en la cabecera.
- Centro de notificaciones para clientes y personal: última actualización por pedido y anuncios del negocio; filtros Todas/Sin leer, lectura individual y marcar todas.
- Lecturas guardadas localmente por cuenta; un nuevo estado de pedido genera un identificador distinto. Se muestran como máximo las 100 actualizaciones más recientes.
- Avisos del teléfono para pedidos listos y cambios observados de preparación, entrega y cancelación del cliente. Cocina/administración recibe nuevas comandas detectadas después de cargar su primera lista.
- Icono propio, contenido expandible y acceso al pedido al tocar el aviso. La navegación comprueba cuenta y pedido disponible antes de mostrarlo.
- Los avisos repetidos se evitan mediante un registro local por cuenta; al cerrar sesión se limpian los avisos del teléfono. No se anuncian todas las comandas históricas al ingresar como personal.
- El permiso Android se solicita solo al pulsar Activar notificaciones en el centro. Si ya se pidió o se bloquearon los avisos, el botón lleva a los ajustes del sistema. Rechazar el permiso no bloquea los avisos dentro de la app.

## Alcance de entrega

APK: `output/apk/Desayunos-1.2.0-debug.apk`. Actualiza la instalación anterior. Este cambio de login y notificaciones no requiere SQL adicional; el flujo de cobro y entrega sigue requiriendo la migración 004 entregada anteriormente.

La sincronización se ejecuta con la app en primer plano. Esta versión no incorpora FCM ni un servicio push desde Supabase y no promete recibir nuevos cambios con la app cerrada. Los avisos del teléfono ya publicados pueden abrir la app y dirigir al pedido; al volver se consultan los datos vigentes. El primer acceso puede mostrar un pedido listo que aún no se había avisado.

El centro muestra el estado actual de cada pedido, no un historial completo de todos sus eventos. El historial de estados se conserva en Supabase; su consulta administrativa completa sigue siendo una mejora pendiente. Las lecturas de avisos son locales a cada teléfono, no se sincronizan entre dispositivos.

## Figma

Propuesta editable: https://www.figma.com/design/fpkLG13kOD8u4fq0DoXyKa

Incluye login y centro de notificaciones con texto, jerarquía y componentes editables; componentes Material de campos/botones, variantes de selección de acceso y tarjetas de avisos, tokens de color de la aplicación y tipografía Roboto. No son capturas aplanadas. Los datos de pedidos de la propuesta son ejemplos de diseño.

## Validación

Pruebas locales JVM: privacidad entre clientes, cambios de estado, carga inicial de personal sin avisos históricos, orden y límite del centro, identificadores de lectura y reglas existentes de API/reportes.

Pruebas de interfaz en emulador: login inválido y error del servidor, acceso del personal sin registro, registro a 320 dp con letra 1.6, avisos leídos y acceso a su pedido, anuncios, activación voluntaria, bandeja vacía, estado ocupado en modo oscuro y regresiones de pedidos/pagos/reportes.

No se instalan APK en el teléfono físico durante la validación. La comprobación con Supabase real y el Honor 400 queda pendiente de la instalación del usuario.

Las decisiones de permiso y apertura siguen las guías oficiales de Android: https://developer.android.com/develop/ui/compose/notifications/notification-permission y https://developer.android.com/develop/ui/compose/notifications/create-notification.
