import { readFile, writeFile } from 'node:fs/promises';

const migrations = ['202610220001_core.sql','202610220002_storage.sql','202610220003_cash_closures.sql'];
const sections = [];
for (const [index, name] of migrations.entries()) {
  let sql = await readFile(new URL(`migrations/${name}`,import.meta.url),'utf8');
  sql = sql.replace(/^\s*(?:begin;|commit;|notify pgrst[^\r\n]*)\s*$/gim,'').trim();
  const parts = sql.split('$$');
  if (parts.length % 2 !== 1) throw new Error(`Delimitadores sin pareja: ${name}`);
  // Cada cuerpo tiene su propio delimitador, evitando confundir funciones y DO.
  for (let part=1; part<parts.length; part+=2) {
    const tag = `$desayuno_m${index+1}_block${(part+1)/2}$`;
    parts[part]=tag + parts[part] + tag;
  }
  sql=parts.join('');
  sections.push(`-- ============================================================\n-- supabase/migrations/${name}\n-- ============================================================\n${sql}`);
}
const header = `-- DESAYUNOS: SCRIPT COMPLETO, REVISION 3
-- Actualiza el esquema original existente. No crea una base desde cero.
-- Ejecutar completo UNA SOLA VEZ. No combinar con migraciones ya aplicadas.
-- Si el intento anterior del archivo completo fallo, ejecutar ROLLBACK; primero.
-- Usar una consulta NUEVA de SQL Editor y pegar TODO este archivo.
-- Incluye estructura, RLS, RPC, Storage y cierre de caja.
-- Conserva datos y adapta las politicas de otros buckets sin eliminar archivos.
-- No contiene credenciales ni asigna roles privilegiados automaticamente.
-- Las secuencias pueden adelantarse aunque una transaccion se revierta.
-- Generado por: node supabase/build_bundle.mjs

BEGIN;

`;
const footer = `

NOTIFY pgrst, 'reload schema';
COMMIT;

-- Tras aplicar, verificar los UUID reales en Authentication > Users.
-- Descomentar y sustituir los UUID solo cuando esten verificados:
-- UPDATE public.profiles SET role='ADMIN' WHERE id='UUID-REAL-DEL-ENCARGADO'::uuid;
-- UPDATE public.profiles SET role='COOK' WHERE id='UUID-REAL-DEL-COCINERO'::uuid;
`;
const script=header + sections.join('\n\n') + footer;
for (const name of ['SUPABASE_COMPLETO.sql','SUPABASE_COMPLETO_V3.sql']) {
  await writeFile(new URL(name,import.meta.url),script,'utf8');
}
console.log('Generados SUPABASE_COMPLETO.sql y SUPABASE_COMPLETO_V3.sql.');
