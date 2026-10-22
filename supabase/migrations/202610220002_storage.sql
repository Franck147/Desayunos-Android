begin;
-- Mantener las políticas existentes para otros buckets y excluir este bucket.
-- Las permisivas no podrán conceder acceso aquí; las restrictivas no bloquearán
-- las nuevas reglas ADMIN. No se borran archivos ni políticas de otros buckets.
DO LANGUAGE plpgsql $desayuno_storage_policies$
DECLARE
    p record;
    expression_sql text;
    statement_sql text;
BEGIN
    for p in select * from pg_policies where schemaname='storage' and tablename='objects'
      and policyname not in ('desayuno_images_insert','desayuno_images_select')
    loop
        statement_sql := format('alter policy %I on storage.objects',p.policyname);
        if p.cmd in ('ALL','SELECT','UPDATE','DELETE') then
            expression_sql := coalesce(p.qual,'true');
            if p.permissive='PERMISSIVE' then
                expression_sql := format('(%s) AND bucket_id <> %L',expression_sql,'desayunos-imagenes');
            else
                expression_sql := format('(%s) OR bucket_id = %L',expression_sql,'desayunos-imagenes');
            end if;
            statement_sql := statement_sql || format(' USING (%s)',expression_sql);
        end if;
        if p.cmd in ('ALL','INSERT','UPDATE') then
            expression_sql := coalesce(p.with_check,p.qual,'true');
            if p.permissive='PERMISSIVE' then
                expression_sql := format('(%s) AND bucket_id <> %L',expression_sql,'desayunos-imagenes');
            else
                expression_sql := format('(%s) OR bucket_id = %L',expression_sql,'desayunos-imagenes');
            end if;
            statement_sql := statement_sql || format(' WITH CHECK (%s)',expression_sql);
        end if;
        execute statement_sql;
    end loop;
END;
$desayuno_storage_policies$;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('desayunos-imagenes','desayunos-imagenes',true,5242880,array['image/jpeg','image/png','image/webp'])
on conflict(id) do update set public=true,file_size_limit=excluded.file_size_limit,allowed_mime_types=excluded.allowed_mime_types;
drop policy if exists desayuno_images_insert on storage.objects;
drop policy if exists desayuno_images_select on storage.objects;
create policy desayuno_images_insert on storage.objects for insert to authenticated
with check(bucket_id='desayunos-imagenes' and (select private.current_role())='ADMIN');
create policy desayuno_images_select on storage.objects for select to authenticated
using(bucket_id='desayunos-imagenes' and (select private.current_role())='ADMIN');
-- Sin UPDATE/DELETE desde Android: archivos con nombre UUID e historial estable.
commit;
