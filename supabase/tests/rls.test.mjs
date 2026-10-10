import { PGlite } from '@electric-sql/pglite';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
const db = new PGlite();
// Local PostgreSQL harness only; hosted Supabase provides these roles/functions.
await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
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
await assert.rejects(db.query("select public.reserve_nutrition_request($1,$1,'x')",[a]));
await db.exec('reset role; set role service_role');
const reserve = async(id, fingerprint='same') => (await db.query('select public.reserve_nutrition_request($1,$2,$3) as result',[a,id,fingerprint])).rows[0].result;
assert.equal((await reserve(a)).state,'reserved');
assert.equal((await reserve(a)).state,'pending');
assert.equal((await reserve(a,'different')).state,'conflict');
await db.query("select public.finish_nutrition_request($1,$1,'{\"test\":true}')",[a]);
assert.equal((await reserve(a)).state,'complete');
for(let i=1;i<5;i++) assert.equal((await reserve(`30000000-0000-4000-8000-${String(i).padStart(12,'0')}`)).state,'reserved');
assert.equal((await reserve(b)).state,'rate_limited');
await db.exec("reset role; update aru_private.nutrition_requests set created_at=now()-interval '2 minutes'; set role service_role;");
assert.equal((await reserve(b)).state,'reserved');
for(let i=6;i<40;i++) {
  await db.exec("reset role; update aru_private.nutrition_requests set created_at=now()-interval '2 minutes'; set role service_role;");
  assert.equal((await reserve(`30000000-0000-4000-8000-${String(i).padStart(12,'0')}`)).state,'reserved');
}
await db.exec("reset role; update aru_private.nutrition_requests set created_at=now()-interval '2 minutes'; set role service_role;");
assert.equal((await reserve('30000000-0000-4000-8000-999999999999')).state,'rate_limited');
assert.equal((await db.query('select public.reserve_nutrition_request($1,$2,$3) as result',[b,'40000000-0000-4000-8000-999999999999','global-cap'])).rows[0].result.state,'rate_limited');
await db.close();
console.log('Passed: owner access, cross-account read/update/reassignment denial, anonymous denial, and goal validation.');
