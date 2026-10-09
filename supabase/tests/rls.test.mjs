import { PGlite } from '@electric-sql/pglite';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
const db = new PGlite();
// Local PostgreSQL harness only; hosted Supabase provides these roles/functions.
await db.exec(`create role anon; create role authenticated;
create schema auth; create table auth.users(id uuid primary key);
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
create function auth.jwt() returns jsonb language sql stable as $$ select current_setting('request.jwt.claims',true)::jsonb $$;
grant usage on schema public, auth to authenticated, anon;
insert into auth.users values ('11111111-1111-1111-1111-111111111111'),('22222222-2222-2222-2222-222222222222');`);
const migrations = new URL('../migrations/', import.meta.url);
for (const file of (await readdir(migrations)).filter(f => f.endsWith('.sql')).sort()) {
  await db.exec(await readFile(new URL(file, migrations), 'utf8'));
}
const a = '11111111-1111-1111-1111-111111111111', b = '22222222-2222-2222-2222-222222222222';
async function asUser(id, anonymous = false) {
  await db.exec(`reset role; set role authenticated;`);
  await db.query(`select set_config('request.jwt.claim.sub',$1,false),set_config('request.jwt.claims',$2,false)`,[id,JSON.stringify({sub:id,is_anonymous:anonymous})]);
}
for (const table of ['journal_entries','saved_meals','nutrition_goals']) {
  await asUser(a);
  if(table === 'journal_entries') await db.query(`insert into public.${table}(id,user_id,journal_date,time_zone,body) values($1,$1,'2026-10-08','Asia/Kolkata','test')`,[a]);
  if(table === 'saved_meals') await db.query(`insert into public.${table}(id,user_id,name,body,estimate) values($1,$1,'test','test','{}')`,[a]);
  if(table === 'nutrition_goals') await db.query(`insert into public.${table}(user_id,calories_kcal) values($1,2000)`,[a]);
  assert.equal((await db.query(`select * from public.${table}`)).rows.length,1);
  await assert.rejects(db.query(`update public.${table} set user_id=$1`,[b]));
  await asUser(b);
  assert.equal((await db.query(`select * from public.${table}`)).rows.length,0);
  assert.equal((await db.query(`update public.${table} set revision=2 returning *`)).rows.length,0);
  await asUser(a,true);
  assert.equal((await db.query(`select * from public.${table}`)).rows.length,0);
  await db.exec('reset role; set role anon');
  await assert.rejects(db.query(`select * from public.${table}`));
}
await asUser(a);
for(const amount of ['-1','0','NaN','Infinity']) await assert.rejects(db.query('update public.nutrition_goals set calories_kcal=$1',[amount]));
await db.close();
console.log('Passed: owner access, cross-account read/update/reassignment denial, anonymous denial, and goal validation.');
