-- Aplicar sobre la base que ya tiene 001-003 o la recuperación V4.
-- Reejecutable. Cobro y entrega se confirman en una sola transacción.
BEGIN;
CREATE OR REPLACE FUNCTION public.complete_order(
    p_order_id uuid,
    p_method text DEFAULT NULL,
    p_reference text DEFAULT NULL
) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $desayuno_complete_order$
DECLARE
    order_row public.orders;
    user_role text := private.current_role();
BEGIN
    IF auth.uid() IS NULL OR user_role IS NULL OR user_role NOT IN ('ADMIN','COOK') THEN
        RAISE EXCEPTION 'Solo el personal autorizado puede completar pedidos';
    END IF;
    SELECT * INTO order_row FROM public.orders WHERE id=p_order_id FOR UPDATE;
    IF order_row.id IS NULL THEN RAISE EXCEPTION 'Pedido no encontrado'; END IF;
    IF order_row.status NOT IN ('READY','DELIVERED') THEN
        RAISE EXCEPTION 'El pedido debe estar listo antes de entregarlo';
    END IF;
    IF order_row.status='READY' THEN
        IF NOT EXISTS (SELECT 1 FROM public.payments WHERE order_id=order_row.id) THEN
            IF user_role <> 'ADMIN' THEN
                RAISE EXCEPTION 'El pedido está pendiente de pago. Solicite el cobro a administración';
            END IF;
            PERFORM public.record_payment(order_row.id,p_method,p_reference);
        ELSIF p_method IS NOT NULL THEN
            -- Validar un reintento con los mismos datos del cobro, sin reemplazarlo.
            PERFORM public.record_payment(order_row.id,p_method,p_reference);
        END IF;
        PERFORM public.transition_order(order_row.id,'DELIVERED',NULL);
        SELECT * INTO order_row FROM public.orders WHERE id=p_order_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.payments WHERE order_id=order_row.id) THEN
        RAISE EXCEPTION 'El pedido histórico no tiene un pago confirmado. Requiere revisión administrativa';
    END IF;
    RETURN to_jsonb(order_row) || jsonb_build_object(
        'order_items',(SELECT coalesce(jsonb_agg(to_jsonb(i) ORDER BY i.id),'[]'::jsonb)
            FROM public.order_items i WHERE i.order_id=order_row.id),
        'payments',(SELECT to_jsonb(p) FROM public.payments p WHERE p.order_id=order_row.id)
    );
END;
$desayuno_complete_order$;
REVOKE ALL ON FUNCTION public.complete_order(uuid,text,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.complete_order(uuid,text,text) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
