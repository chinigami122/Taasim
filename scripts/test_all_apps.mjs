import assert from 'node:assert/strict';
import { client, driver, admin, trip, request, account } from './smoke_demo.mjs';
const base = process.env.BASE || 'http://localhost:8080';
const outcomes = [];
async function test(name, run) {
  try { await run(); outcomes.push({name,result:'PASS'}); }
  catch (error) { outcomes.push({name,result:'FAIL',error:error.message}); }
}
async function status(path, token, expected, method='GET', body) {
  const response = await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(10000)});
  assert.equal(response.status,expected,`${method} ${path}`);
  return response;
}
const outsider = await account('CLIENT','isolation');
for (const [app,port,title] of [['client',5173,'TaaSim'],['driver',5174,'Driver Partner'],['admin',5175,'Operations']]) {
 await test(`${app} container serves HTML and production assets`,async()=>{
   const root=await fetch(`http://localhost:${port}/`);assert.equal(root.status,200);const html=await root.text();assert.ok(html.includes(title));
   const script=html.match(/src="([^"]+\.js)"/);assert.ok(script,'built JavaScript asset');
   const asset=await fetch(`http://localhost:${port}${script[1]}`);assert.equal(asset.status,200);assert.ok((asset.headers.get('content-type')||'').includes('javascript'));
 });
}
for (const port of [8080,8081,8082,8083,8084,8085,8086]) await test(`service ${port} health`,async()=>{const r=await fetch(`http://localhost:${port}/actuator/health`,{signal:AbortSignal.timeout(10000)});assert.equal(r.status,200);assert.equal((await r.json()).status,'UP');});
await test('invalid login rejected',()=>status('/api/auth/login',null,401,'POST',{email:'no-such-user@taasim.demo',password:'invalid'}));
await test('anonymous request rejected',()=>status('/api/trips/history',null,401));
await test('client cannot publish GPS',()=>status('/api/drivers/location',client.token,403,'POST',{lat:33.6,lon:-7.53,speed:0}));
await test('client cannot access admin drivers',()=>status('/api/admin/drivers',client.token,403));
await test('driver cannot access admin billing',()=>status('/api/admin/billing/summary',driver.token,403));
await test('driver cannot accept another driver trip',()=>status('/api/drivers/trips/somebody-else/accept',driver.token,403,'PUT'));
await test('client cannot read another client trip',()=>status(`/api/trips/${trip.tripId}`,outsider.token,403));
await test('client cannot read another client fare',()=>status(`/api/billing/trips/${trip.tripId}`,outsider.token,403));
await test('client cannot list another client billing',()=>status(`/api/billing/clients/${client.id}`,outsider.token,403));
await test('driver cannot list another driver billing',()=>status('/api/billing/drivers/somebody-else',driver.token,403));
await test('forged headers cannot grant fare access on direct port',async()=>{
 const response=await fetch(`http://localhost:8086/api/billing/trips/${trip.tripId}`,{headers:{Authorization:`Bearer ${outsider.token}`,'X-User-Role':'ADMIN','X-User-Id':client.id}});assert.equal(response.status,403);
});
await test('client cannot create another client payment intent',()=>status(`/api/billing/trips/${trip.tripId}/intent`,outsider.token,403,'POST'));
await test('unconfigured checkout reports unavailable',()=>status(`/api/billing/trips/${trip.tripId}/intent`,client.token,503,'POST'));
await test('driver earnings enforce ownership',()=>status('/api/drivers/earnings/summary?driverId=somebody-else',driver.token,403));
await test('client history contains completed ride',async()=>{const h=await request('/api/trips/history',client.token);assert.ok(h.trips.some(t=>t.tripId===trip.tripId));});
await test('outsider history excludes completed ride',async()=>{const h=await request('/api/trips/history',outsider.token);assert.ok(!h.trips.some(t=>t.tripId===trip.tripId));});
await test('admin trip filters return matching status',async()=>{const trips=await request('/api/admin/trips?status=COMPLETED',admin.token);assert.ok(trips.length>0);assert.ok(trips.every(t=>t.status==='COMPLETED'));});
await test('admin seven-day chart has seven ordered dates',async()=>{const series=await request('/api/admin/billing/summary?days=7',admin.token);assert.equal(series.length,7);assert.ok(series.every((day,i)=>i===0||day.date>series[i-1].date));});
await test('admin invalid report range rejected',()=>status('/api/admin/billing/summary?days=91',admin.token,400));
await test('offline driver absent from live fleet',async()=>{const positions=await request('/api/admin/drivers/live-positions',admin.token);assert.ok(!positions.some(p=>p.driverId===driver.id));});
console.log(JSON.stringify({suite:'all-apps',outcomes},null,2));
assert.ok(outcomes.every(t=>t.result==='PASS'),'One or more application checks failed');
