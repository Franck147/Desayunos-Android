-- Reparación de las dos cuentas existentes mostradas en el diagnóstico.
-- Ejecutar en SQL Editor como postgres. No crea usuarios ni cambia contraseñas.
-- Adler -> ADMIN; Franck -> COOK. Cambiar esta asignación aquí si corresponde.
-- Para futuras altas usar Authentication/API; este parche repara identidades ausentes.
BEGIN;

DO LANGUAGE plpgsql $desayuno_check_identity_repair$
BEGIN
    IF (SELECT count(*) FROM auth.users WHERE lower(email) IN
        ('adler@desayunos.com','franck@desayunos.com')) <> 2 THEN
        RAISE EXCEPTION 'Deben existir exactamente las dos cuentas indicadas; no se modificó ninguna';
    END IF;
    IF (SELECT count(*) FROM public.profiles p JOIN auth.users u ON u.id=p.id
        WHERE lower(u.email) IN ('adler@desayunos.com','franck@desayunos.com')) <> 2 THEN
        RAISE EXCEPTION 'Falta un perfil; revisar DIAGNOSTICO_LOGIN.sql';
    END IF;
    IF EXISTS (SELECT 1 FROM auth.users WHERE lower(email) IN
        ('adler@desayunos.com','franck@desayunos.com') AND
        (email_confirmed_at IS NULL OR coalesce(encrypted_password,'')='')) THEN
        RAISE EXCEPTION 'Las cuentas deben tener correo confirmado y contraseña configurada en Auth';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='auth'
        AND table_name='identities' AND column_name='provider_id' AND data_type IN ('text','character varying')) THEN
        RAISE EXCEPTION 'Esta versión de auth.identities requiere revisión; usar la API administrativa de Auth';
    END IF;
    IF EXISTS (
        SELECT 1 FROM auth.users u JOIN auth.identities i ON i.provider='email'
          AND (i.provider_id=u.id::text OR lower(i.identity_data->>'email')=lower(u.email))
        WHERE lower(u.email) IN ('adler@desayunos.com','franck@desayunos.com') AND i.user_id<>u.id
    ) THEN
        RAISE EXCEPTION 'Hay una identidad asociada a otra cuenta; no se reasignan identidades automáticamente';
    END IF;
END;
$desayuno_check_identity_repair$;

INSERT INTO auth.identities (
    id, user_id, provider_id, provider, identity_data, created_at, updated_at
)
SELECT
    gen_random_uuid(), u.id, u.id::text, 'email',
    jsonb_build_object('sub',u.id::text,'email',u.email,'email_verified',true),
    now(), now()
FROM auth.users u
WHERE lower(u.email) IN ('adler@desayunos.com','franck@desayunos.com')
  AND NOT EXISTS (SELECT 1 FROM auth.identities i WHERE i.user_id=u.id AND i.provider='email')
ON CONFLICT (provider_id,provider) DO NOTHING;

DO LANGUAGE plpgsql $desayuno_verify_email_identities$
BEGIN
    IF (SELECT count(*) FROM auth.users u WHERE lower(u.email) IN
        ('adler@desayunos.com','franck@desayunos.com') AND EXISTS (
            SELECT 1 FROM auth.identities i WHERE i.user_id=u.id AND i.provider='email'
              AND i.provider_id=u.id::text AND lower(i.identity_data->>'email')=lower(u.email)
        )) <> 2 THEN
        RAISE EXCEPTION 'No se pudieron verificar las dos identidades email. Se revierte la reparación';
    END IF;
END;
$desayuno_verify_email_identities$;

-- Roles de la APLICACIÓN; no modificar auth.users.role a ADMIN/COOK.
UPDATE public.profiles p
SET role=CASE lower(u.email) WHEN 'adler@desayunos.com' THEN 'ADMIN' ELSE 'COOK' END
FROM auth.users u
WHERE p.id=u.id AND lower(u.email) IN ('adler@desayunos.com','franck@desayunos.com')
  AND p.role IS DISTINCT FROM CASE lower(u.email) WHEN 'adler@desayunos.com' THEN 'ADMIN' ELSE 'COOK' END;

COMMIT;

SELECT u.email,p.role AS rol_app,
    (SELECT count(*) FROM auth.identities i WHERE i.user_id=u.id AND i.provider='email') AS identidades_email,
    u.email_confirmed_at IS NOT NULL AS correo_confirmado
FROM auth.users u JOIN public.profiles p ON p.id=u.id
WHERE lower(u.email) IN ('adler@desayunos.com','franck@desayunos.com') ORDER BY u.email;
