import assert from 'node:assert/strict';
const checks=[];
async function check(name,url,verify){try{const r=await fetch(url,{signal:AbortSignal.timeout(15000)});assert.equal(r.status,200);if(verify)await verify(r);checks.push({name,result:'PASS'});}catch(e){checks.push({name,result:'FAIL',error:e.message});}}
await check('MinIO live','http://localhost:9000/minio/health/live');
await check('Prometheus health','http://localhost:9090/-/healthy');
await check('Tempo ready','http://localhost:3200/ready');
await check('Schema Registry','http://localhost:8090/subjects',async r=>assert.ok(Array.isArray(await r.json())));
await check('Grafana database health','http://localhost:3000/api/health',async r=>assert.equal((await r.json()).database,'ok'));
await check('All seven services scraped by Prometheus','http://localhost:9090/api/v1/targets',async r=>{const targets=(await r.json()).data.activeTargets;const expected=['auth-service','trip-service','driver-service','geospatial-service','matching-service','billing-service','gateway-service'];for(const svc of expected){const target=targets.find(t=>t.scrapeUrl.includes(svc+':'));assert.ok(target,svc+' target present');assert.equal(target.health,'up',svc+' scrape: '+target.lastError);}});
console.log(JSON.stringify({suite:'infrastructure',checks},null,2));assert.ok(checks.every(c=>c.result==='PASS'),'Infrastructure checks failed');
