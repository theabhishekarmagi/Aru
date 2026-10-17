import test from 'node:test';
import assert from 'node:assert/strict';
import {createHandler} from '../functions/estimate-nutrition/handler.mjs';

test('web preflight permits only explicit bearer-authenticated POST headers, without provider calls',async()=>{
 const handler=createHandler(()=>undefined,()=>{throw new Error('Preflight must not call upstream services')});
 const response=await handler(new Request('https://example.test',{method:'OPTIONS',headers:{Origin:'https://aru-food-journal-web.lemonyibis8.chatgpt.site','Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'authorization,apikey,content-type'}}));
 assert.equal(response.status,204);
 assert.equal(response.headers.get('Access-Control-Allow-Origin'),'*');
 assert.match(response.headers.get('Access-Control-Allow-Headers'),/authorization/);
 assert.equal(response.headers.get('Access-Control-Allow-Credentials'),null);
});
test('browser callers remain denied without a user token and can read the safe error',async()=>{
 const response=await createHandler(()=>undefined)(new Request('https://example.test',{method:'POST',body:'{}',headers:{Origin:'https://example.test'}}));
 assert.equal(response.status,401);
 assert.equal(response.headers.get('Access-Control-Allow-Origin'),'*');
 assert.deepEqual(await response.json(),{error:'account_required'});
});
