import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {beforeEach,describe,expect,it,vi} from 'vitest';
const script=readFileSync('public/meeting-client.js','utf8');
async function player() {
 document.body.innerHTML='<div id="meeting-status"></div>';
 const listeners:Record<string,(data:unknown)=>void>={};
 let joinOptions:{error:(data:unknown)=>void;success:()=>void};
 const sdk={inMeetingServiceListener:(event:string,fn:(data:unknown)=>void)=>{listeners[event]=fn},setZoomJSLib:vi.fn(),preLoadWasm:vi.fn(),prepareWebSDK:vi.fn(),i18n:{load:vi.fn(),onLoad:(fn:()=>void)=>fn()},init:(options:{success:()=>void})=>options.success(),join:(options:typeof joinOptions)=>{joinOptions=options}};
 const logger={log:vi.fn(),warn:vi.fn(),error:vi.fn()};
 await runInNewContext(script,{document,window:{ZoomMtg:sdk,crossOriginIsolated:false},ZoomMtg:sdk,location:{pathname:'/meeting/opaque',origin:'http://localhost:3000'},console:logger,fetch:async()=>({ok:true,json:async()=>({signature:'private-signature',meetingNumber:'12345678901',password:'private-passcode',userName:'Member'})})});
 return {listeners,status:document.getElementById('meeting-status')!,joinOptions:joinOptions!,logger};
}
describe('meeting connection diagnostics',()=>{
 beforeEach(()=>{document.body.innerHTML=''});
 it('distinguishes host waiting, admission and connection without exposing provider details',async()=>{
  const {listeners,status,logger}=await player();
  listeners.onJoinSpeed({level:4,text:'vendor-private-details'});expect(status.dataset.connectionStage).toBe('waiting-for-host');expect(status.hidden).toBe(false);
  listeners.onJoinSpeed({level:6});expect(status.dataset.connectionStage).toBe('waiting-room');
  listeners.onMeetingStatus({status:2});expect(status.dataset.connectionStage).toBe('connected');expect(status.hidden).toBe(true);
  listeners.onMeetingStatus({status:4});expect(status.dataset.connectionStage).toBe('reconnecting');expect(status.hidden).toBe(false);
  expect(document.body.innerHTML).not.toMatch(/private|12345678901/);expect(logger.log).not.toHaveBeenCalled();expect(logger.error).not.toHaveBeenCalled();
 });
 it('reports only numeric error codes and never serializes vendor errors',async()=>{
  const {status,joinOptions}=await player();
  joinOptions.error({errorCode:3004,password:'private-passcode',signature:'private-signature'});
  expect(status.dataset.connectionCode).toBe('3004');expect(status.textContent).toContain('connection code 3004');
  expect(document.body.innerHTML).not.toMatch(/private-passcode|private-signature/);
 });
 it('ignores untrusted error-code text',async()=>{
  const {status,joinOptions}=await player();joinOptions.error({errorCode:'private-passcode'});
  expect(status.dataset.connectionCode).toBeUndefined();expect(document.body.innerHTML).not.toContain('private-passcode');
 });
});
