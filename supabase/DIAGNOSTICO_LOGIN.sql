-- SOLO LECTURA. Ejecutar en SQL Editor; no cambia cuentas, roles ni contraseñas.
-- Copiar el resultado de la primera consulta para diagnosticar el login.
SELECT
    u.email,
    u.id,
    p.role AS rol_app,
    p.id IS NOT NULL AS tiene_perfil,
    u.email_confirmed_at IS NOT NULL AS correo_confirmado,
    coalesce(length(u.encrypted_password),0) > 0 AS tiene_password,
    (SELECT count(*) FROM auth.identities i WHERE i.user_id=u.id) AS identidades,
    ARRAY(
        SELECT column_name
        FROM unnest(ARRAY[
            'confirmation_token','recovery_token','email_change',
            'email_change_token_new','email_change_token_current',
            'phone_change','phone_change_token','reauthentication_token'
        ]) AS fields(column_name)
        WHERE to_jsonb(u) ? column_name
          AND to_jsonb(u)->column_name = 'null'::jsonb
    ) AS campos_texto_en_null
FROM auth.users u
LEFT JOIN public.profiles p ON p.id=u.id
WHERE lower(u.email) IN ('adler@desayunos.com','franck@desayunos.com')
ORDER BY u.email;

-- Triggers existentes: permite detectar funciones adicionales ajenas al proyecto.
SELECT
    t.tgname AS trigger_name,
    pg_get_triggerdef(t.oid) AS trigger_definition,
    n.nspname AS function_schema,
    f.proname AS function_name,
    pg_get_userbyid(f.proowner) AS function_owner,
    f.prosecdef AS security_definer
FROM pg_trigger t
JOIN pg_proc f ON f.oid=t.tgfoid
JOIN pg_namespace n ON n.oid=f.pronamespace
WHERE t.tgrelid='auth.users'::regclass AND NOT t.tgisinternal;

-- Permisos de Auth sobre su propia tabla. No se conceden privilegios nuevos.
SELECT
    has_schema_privilege('supabase_auth_admin','auth','USAGE') AS auth_schema_usage,
    has_table_privilege('supabase_auth_admin','auth.users','SELECT') AS auth_users_select,
    has_table_privilege('supabase_auth_admin','auth.users','UPDATE') AS auth_users_update;
