import {ApiError,parseRequest,validateEstimate,instructions,outputSchema} from './contract.mjs';
const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
async function limitedJson(response,limit) {
 const reader=response.body?.getReader(); if(!reader)throw new ApiError('invalid_request',400);
 const chunks=[];let size=0;
 for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>limit){await reader.cancel();throw new ApiError('payload_too_large',413);}chunks.push(value);}
 const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
 try{return JSON.parse(new TextDecoder().decode(bytes));}catch{throw new ApiError('invalid_json',400);}
}
export function createHandler(env,fetcher=fetch) {
 return async request=>{
  let userId, input, reserved=false;
  const url=env('SUPABASE_URL'),serviceKey=env('SUPABASE_SERVICE_ROLE_KEY');
  async function rpc(name,body){
   const r=await fetcher(url+'/rest/v1/rpc/'+name,{method:'POST',headers:{apikey:serviceKey,Authorization:'Bearer '+serviceKey,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(8000)});
   if(!r.ok)throw new ApiError('service_unavailable',503);
   return r.status===204?null:await r.json();
  }
  try {
   if(request.method!=='POST')return json({error:'method_not_allowed'},405);
   const authorization=request.headers.get('Authorization');
   if(!authorization?.startsWith('Bearer ')||authorization.length>10000)throw new ApiError('account_required',401);
   if(!url||!serviceKey)throw new ApiError('service_unavailable',503);
   const auth=await fetcher(url+'/auth/v1/user',{headers:{apikey:serviceKey,Authorization:authorization},signal:AbortSignal.timeout(8000)});
   if(!auth.ok)throw new ApiError('account_required',401);
   const user=await auth.json();
   if(!user.id||user.is_anonymous===true)throw new ApiError('account_required',401);
   userId=user.id;
   input=parseRequest(await limitedJson(request,12000));
   const key=env('OPENROUTER_API_KEY');if(!key)throw new ApiError('not_configured',503);
   const model=env('OPENROUTER_MODEL')||'nvidia/nemotron-3-super-120b-a12b:free';
   if(!model.endsWith(':free'))throw new ApiError('provider_configuration',503);
   const fingerprint=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify({provider:'openrouter',model,input}))))).map(n=>n.toString(16).padStart(2,'0')).join('');
   const budget=await rpc('reserve_nutrition_request',{p_user:userId,p_request:input.requestId,p_fingerprint:fingerprint});
   const wrap=estimate=>({entryId:input.entryId,requestId:input.requestId,revision:input.revision,estimate});
   if(budget.state==='complete')return json(wrap(budget.result));
   if(budget.state==='rate_limited')throw new ApiError('rate_limited',429);
   if(budget.state!=='reserved')throw new ApiError('request_conflict',409);
   reserved=true;
   const response=await fetcher('https://openrouter.ai/api/v1/chat/completions',{
    method:'POST',headers:{Authorization:'Bearer '+key,'Content-Type':'application/json'},signal:AbortSignal.timeout(30000),
    body:JSON.stringify({model,messages:[{role:'system',content:instructions},{role:'user',content:input.text}],max_tokens:4000,reasoning:{enabled:false},provider:{require_parameters:true,allow_fallbacks:false,max_price:{prompt:0,completion:0}},response_format:{type:'json_schema',json_schema:{name:'nutrition',strict:true,schema:outputSchema}}})
   });
   if(!response.ok) {
    // Log only status; provider messages may contain user data.
    console.warn(JSON.stringify({event:'nutrition_provider_error',status:response.status}));
    if(response.status===402)throw new ApiError('provider_quota',503);
    if(response.status===401||response.status===403)throw new ApiError('provider_configuration',503);
    throw new ApiError(response.status===429?'provider_rate_limit':'provider_unavailable',503);
   }
   const result=await limitedJson(response,128000);
   if(result.error)throw new ApiError('provider_unavailable',503);
   const choice=result.choices?.[0];
   if(choice?.message?.refusal)throw new ApiError('no_food',422);
   if(choice?.finish_reason!=='stop')throw new ApiError('incomplete_estimate',502);
   const output=choice.message?.content;
   if(typeof output!=='string')throw new ApiError('invalid_response',502);
   let raw;try{raw=JSON.parse(output);}catch{throw new ApiError('invalid_response',502);}
   const estimate=validateEstimate(raw,model);
   await rpc('finish_nutrition_request',{p_user:userId,p_request:input.requestId,p_result:estimate});
   return json(wrap(estimate));
  }catch(error){
   if(reserved){try{await rpc('finish_nutrition_request',{p_user:userId,p_request:input.requestId,p_result:null});}catch{/* Reservation remains charged; never silently repeat a provider call. */}}
   if(error instanceof ApiError)return json({error:error.code},error.status);
   return json({error:error?.name==='TimeoutError'?'timeout':'service_unavailable'},503);
  }
 };
}
