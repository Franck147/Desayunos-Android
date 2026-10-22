"""Actualización de requisitos manteniendo los identificadores HU-01 a HU-30."""
import json
from pathlib import Path

path = Path(__file__).with_name('historias_usuario.json')
stories = json.loads(path.read_text(encoding='utf-8'))
by_id = {s['id']: s for s in stories}

def update(number, **fields):
    by_id[f'HU-{number:02d}'].update(fields)

update(1, criteria=[
    'Dado un registro incompleto o inválido, cuando intento crear mi cuenta, se indican los datos por corregir y se valida la confirmación de contraseña.',
    'Dados datos válidos y la confirmación de correo desactivada, cuando Supabase devuelve una sesión, ingreso con mi perfil CLIENT sin verificación manual.',
    'Dada una pantalla pequeña o letra ampliada, cuando completo el registro, puedo desplazar el formulario y acceder a sus campos y botón con el teclado abierto.'
])
update(4, criteria=[
    'Dada mi sesión activa, cuando abro Mi perfil, visualizo nombre y teléfono en un formulario desplazable que admite letra ampliada.',
    'Dados nombre y teléfono no vacíos, cuando guardo, se persisten en mi perfil y se usan en pedidos posteriores; un fallo no anuncia éxito.',
    'Dado un cliente autenticado, cuando intenta modificar su rol o el perfil de otra persona por la API, el servidor rechaza la operación.'
])
update(5, criteria=[
    'Dada la carta de Supabase, cuando la consulto, veo nombre, descripción, imagen disponible, precio, categoría y stock en tarjetas que permiten varias líneas.',
    'Dado un producto desactivado por administración, cuando se actualiza la carta, deja de ofrecerse al cliente.',
    'Dado un producto sin stock, cuando intento agregarlo, el botón no permite incorporarlo al carrito.'
])
update(6, status='Base en código')
update(7, criteria=[
    'Dado un producto disponible, cuando lo agrego, el carrito actualiza cantidad, subtotal y total estimado sin superar el stock mostrado.',
    'Dada una unidad en el carrito, cuando la reduzco, se retira el artículo; puedo vaciar el carrito si no existe un envío pendiente.',
    'Dados nombres largos y letra ampliada, cuando reviso el carrito, cantidades, indicaciones, totales y confirmación son accesibles mediante desplazamiento.'
])
update(8, status='Base en código', depends='HU-07', criteria=[
    'Dado un artículo del carrito, cuando escribo indicaciones, se asocian solo a ese detalle y se muestra el contador de caracteres.',
    'Dada una nota de 500 caracteres, cuando intento escribir más, el campo conserva como máximo ese límite; el servidor también valida la longitud.',
    'Dado un pedido confirmado con notas, cuando cliente o cocina revisa su detalle, ve las indicaciones junto al producto y con varias líneas si hace falta.'
])
update(11, title='Mis pedidos legibles e historial', criteria=[
    'Dada mi sesión, cuando abro Pedidos, solo veo pedidos vinculados a mi UUID; En atención agrupa PENDING, PREPARING y READY.',
    'Dado un pedido entregado o cancelado, cuando actualizo la vista, aparece en Historial; DELIVERED se muestra como Completado · entregado.',
    'Dados 320 dp de ancho y letra ampliada hasta 1.6, cuando reviso nombres largos, estados e importes, el texto se distribuye sin solaparse y puedo desplazarme hasta el total.'
])
update(13, criteria=[
    'Dada una cuenta COOK o ADMIN, cuando abro Pedidos, veo por defecto los pedidos activos, con los más antiguos primero.',
    'Dados pedidos de distintos estados, cuando filtro por estado, Por cobrar, Completados o Todos, o busco número, cliente o teléfono, la lista corresponde al criterio.',
    'Dado un nuevo pedido en otro dispositivo, cuando se sincroniza el panel en primer plano, aparece la comanda; los completados dejan la cola activa.'
])
update(15, criteria=[
    'Dado un pedido PREPARING, cuando selecciono Marcar listo, el servidor registra READY y el actor; no permite saltar directamente desde PENDING.',
    'Dado un cliente que sigue su pedido, cuando la sincronización en primer plano recibe READY, la pantalla indica Listo para recoger y orienta el retiro en local.',
    'Dado un pedido que cambia a READY durante el seguimiento, cuando el cliente concedió permiso de notificaciones, se emite un aviso local para recogerlo.'
])
update(16, title='Entrega y finalización sin duplicados', depends='HU-15; HU-26; migración 004 aplicada', story='Como cocinero, quiero confirmar la entrega de un pedido listo y pagado para completar su atención y retirarlo de la cola activa.', criteria=[
    'Dado un pedido READY pagado, cuando COOK o ADMIN confirma Entregar y completar, el servidor registra DELIVERED y la interfaz muestra Completado · entregado.',
    'Dado un pedido sin pago, cuando ingresa COOK, la entrega está deshabilitada y el servidor también la rechaza; se solicita el cobro a administración.',
    'Dada una entrega confirmada, cuando se reintenta, no se duplica pago ni evento; el pedido queda en Historial del cliente y Completados del personal.'
])
update(17, status='Base en código', criteria=[
    'Dada una comanda, cuando la consulto, veo número, cliente, teléfono, productos, cantidades, subtotales, total y estado de pago en bloques legibles.',
    'Dadas indicaciones del cliente y nombres largos, cuando reviso la comanda en una pantalla pequeña, el contenido usa varias líneas y permite desplazamiento.',
    'Dado un cambio posterior en nombre o precio del producto, cuando consulto la comanda histórica, se conservan los datos originales de la compra.'
])
update(19, criteria=[
    'Dados datos válidos, cuando ADMIN registra un producto, selecciona categoría, precio, stock y disponibilidad; el editor se cierra después de la confirmación del servidor.',
    'Dados nombre vacío, precio negativo o stock inválido, cuando intento guardar, se informa el error y no se crea un producto incompleto.',
    'Dado un precio, cuando el servidor lo valida, acepta solo importes no negativos con hasta dos decimales; los campos del editor admiten desplazamiento con letra ampliada.'
])
update(20, criteria=[
    'Dado un producto y su versión vigente, cuando guardo, se actualizan nombre, descripción, categoría elegida, precio y disponibilidad.',
    'Dado un producto desactivado, cuando el cliente actualiza la carta, deja de poder seleccionarlo para nuevos pedidos.',
    'Dado un cambio concurrente o un fallo del servidor, cuando intento guardar una edición, se informa el error y el editor permanece abierto para corregir o reintentar.'
])
update(26, title='Cobro verificado y entrega atómica', depends='HU-09; permisos ADMIN; migración 004 aplicada', story='Como administrador, quiero registrar el pago verificado en efectivo, Yape o Plin y completar la entrega cuando el pedido esté listo para evitar cobros o entregas incompletas.', criteria=[
    'Dado un pedido cobrable, cuando confirmo el dinero recibido, el servidor registra importe real, método, actor y fecha; Yape y Plin exigen referencia verificada.',
    'Dado un pedido READY sin pagar, cuando uso Cobrar y completar y confirmo Cobrar y entregar, cobro y entrega se guardan en una transacción; si falla la entrega, se revierte también el pago.',
    'Dado un cobro confirmado, cuando reintento la misma solicitud, no se duplica; si registro datos distintos para un pago existente, el servidor informa el conflicto.'
])
update(27, criteria=[
    'Dada una fecha no futura y el total conciliado, cuando ADMIN confirma el cierre, se guarda fecha de negocio, total, cantidad de pagos y responsable.',
    'Dado un importe cambiado por otro cobro, cuando intento cerrar con un total anterior, el servidor rechaza el cierre y pide actualizar; COOK no puede cerrar caja.',
    'Dada una jornada cerrada, cuando la consulto, veo importe y cantidad conciliados, sin botón habilitado para otro cierre; el servidor impide nuevos cobros de esa jornada.'
])
update(28, title='Reportes diarios y exportación CSV', story='Como administrador, quiero consultar y exportar los cobros y resultados de una fecha para evaluar los ingresos y la operación del negocio.', criteria=[
    'Dada una fecha, cuando consulto el reporte, se suman pagos confirmados según su fecha de cobro en America/Lima y se muestran pedidos cobrados, unidades y desglose por efectivo, Yape y Plin.',
    'Dados pedidos de la jornada, cuando reviso el reporte, veo completados, cancelados, productos y detalle de cobros; los pendientes se muestran aparte y no aumentan los ingresos.',
    'Dado el reporte, cuando elijo Guardar reporte CSV y un destino Android, se exportan resumen, cobros y productos; si no hay pagos, los ingresos y unidades son cero.'
])
path.write_text(json.dumps(stories,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Actualizadas 30 historias, con identificadores originales y 90 criterios.')
