import http from 'k6/http';
import {check,sleep} from 'k6';
export const options={stages:[{duration:__ENV.WARMUP||'30s',target:Number(__ENV.VUS||100)},{duration:__ENV.DURATION||'1m',target:Number(__ENV.VUS||100)},{duration:__ENV.COOLDOWN||'15s',target:0}],thresholds:{http_req_failed:['rate<0.05'],http_req_duration:['p(95)<500']}};
const base=__ENV.API_URL||'http://host.docker.internal:8080';
export function setup(){
  if(!__ENV.EMAIL_PREFIX)return;
  for(let i=1;i<=Number(__ENV.VUS||100);i++){
    const response=http.post(`${base}/api/auth/register`,JSON.stringify({email:`${__ENV.EMAIL_PREFIX}${i}@example.test`,password:__ENV.PASSWORD||'DemoPass123!',displayName:`Load Member ${i}`}),{headers:{'Content-Type':'application/json'}});
    check(response,{'load account ready':r=>r.status===200||r.status===409});
  }
}
export default function(){
  const email=__ENV.EMAIL_PREFIX?`${__ENV.EMAIL_PREFIX}${__VU}@example.test`:(__ENV.EMAIL||'member@example.test');
  const login=http.post(`${base}/api/auth/login`,JSON.stringify({email,password:__ENV.PASSWORD||'DemoPass123!'}),{headers:{'Content-Type':'application/json'}});
  check(login,{'login succeeds':r=>r.status===200});if(login.status!==200)return;
  const token=login.json('accessToken');const headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
  check(http.get(`${base}/api/today`,{headers}),{'today succeeds':r=>r.status===200});
  check(http.get(`${base}/api/sessions`,{headers}),{'sessions succeed':r=>r.status===200});
  if(__ENV.SESSION_ID){
    const sessionId=__ENV.SESSION_ID;
    const send=(action)=>http.post(`${base}/api/attendance/${action}`,JSON.stringify({sessionId,requestId:`k6-${__VU}-${__ITER}-${action}-${Date.now()}`}),{headers});
    check(send('start'),{'attendance starts':r=>r.status===200});
    check(send('heartbeat'),{'heartbeat succeeds':r=>r.status===200});
    check(send('complete'),{'attendance completes':r=>r.status===200});
  }
  sleep(1);
}
