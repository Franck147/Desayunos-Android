-- Reparación limitada para "Database error querying schema" cuando Auth encuentra
-- NULL en campos que espera como texto. No corrige otros errores de Auth.
-- Solo Adler y Franck. Conserva contraseñas, UUID, roles, estado de confirmación,
-- identidades y cualquier token no nulo. No concede permisos nuevos.
-- Referencia: https://supabase.com/kb/troubleshooting/auth-error-500-database-error-querying-schema
BEGIN;

DO LANGUAGE plpgsql $desayuno_fix_auth_text$
DECLARE
    field_name text;
BEGIN
    FOREACH field_name IN ARRAY ARRAY[
        'confirmation_token', 'recovery_token', 'email_change',
        'email_change_token_new', 'email_change_token_current',
        'phone_change', 'phone_change_token', 'reauthentication_token'
    ] LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema='auth' AND table_name='users'
              AND column_name=field_name AND data_type IN ('text','character varying')
        ) THEN
            EXECUTE format(
                'UPDATE auth.users SET %I = %L WHERE lower(email) IN (%L,%L) AND %I IS NULL',
                field_name, '', 'adler@desayunos.com', 'franck@desayunos.com', field_name
            );
        END IF;
    END LOOP;
END;
$desayuno_fix_auth_text$;

COMMIT;

-- Verificación sin mostrar contraseñas ni valores de tokens.
SELECT
    u.email,
    p.role AS rol_app,
    p.id IS NOT NULL AS tiene_perfil,
    u.email_confirmed_at IS NOT NULL AS correo_confirmado,
    coalesce(length(u.encrypted_password),0) > 0 AS tiene_password,
    (SELECT count(*) FROM auth.identities i WHERE i.user_id=u.id) AS identidades,
    ARRAY(
        SELECT column_name FROM unnest(ARRAY[
            'confirmation_token', 'recovery_token', 'email_change',
            'email_change_token_new', 'email_change_token_current',
            'phone_change', 'phone_change_token', 'reauthentication_token'
        ]) AS fields(column_name)
        WHERE to_jsonb(u) ? column_name
          AND to_jsonb(u)->column_name = 'null'::jsonb
    ) AS campos_texto_en_null
FROM auth.users u
LEFT JOIN public.profiles p ON p.id=u.id
WHERE lower(u.email) IN ('adler@desayunos.com','franck@desayunos.com')
ORDER BY u.email;
