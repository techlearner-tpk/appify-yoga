'use client';
import {FormEvent,useEffect,useState} from 'react';
import {api} from '@/lib/api';
import {StreamFields,streamFromForm,type StreamConfiguration} from './StreamFields';
type Program={id:string;name:string};
type Session={id:string;program_id:string;starts_at:string};
export function StreamConfigurationEditor({programs,refreshKey}:{programs:Program[];refreshKey:string}) {
 const [scope,setScope]=useState('programs'),[id,setId]=useState(''),[sessions,setSessions]=useState<Session[]>([]),[config,setConfig]=useState<StreamConfiguration|undefined>(),[loaded,setLoaded]=useState(false),[error,setError]=useState(''),[saved,setSaved]=useState(''),[correcting,setCorrecting]=useState(false);
 useEffect(()=>{api<Session[]>('/admin/sessions').then(setSessions).catch(e=>setError(e.message))},[refreshKey]);
 useEffect(()=>{let active=true;setLoaded(false);setSaved('');setError('');setConfig(undefined);if(!id)return;
  api<{configured:boolean;configuration?:StreamConfiguration}>(`/admin/${scope}/${id}/stream`).then(result=>{if(active){setConfig(result.configuration);setLoaded(true)}}).catch(e=>{if(active)setError(e.message)});return()=>{active=false};
 },[scope,id]);
 async function save(e:FormEvent<HTMLFormElement>) {e.preventDefault();setError('');setSaved('');const form=new FormData(e.currentTarget);try{await api(`/admin/${scope}/${id}/stream`,{method:'PUT',body:JSON.stringify(streamFromForm(form))});setSaved('Streaming configuration saved.')}catch(e){setError((e as Error).message)}}
 async function correctPasscode(e:FormEvent<HTMLFormElement>) {e.preventDefault();const form=e.currentTarget;setError('');setSaved('');setCorrecting(true);try{await api(`/admin/sessions/${id}/stream/passcode`,{method:'PUT',body:JSON.stringify({meetingPasscode:new FormData(form).get('correctedPasscode')})});form.reset();setSaved('Zoom passcode updated. Members can finish class and enter again.')}catch(e){setError((e as Error).message)}finally{setCorrecting(false)}}
 return <section className="panel profileForm spaced"><span className="eyebrow">ADMIN ONLY</span><h2>Streaming configuration</h2><p className="muted">Program defaults apply unless a session has its own configuration. Session overrides can change before their start time.</p>
  <label>Configure<select value={scope} onChange={e=>{setScope(e.target.value);setId('')}}><option value="programs">Program default</option><option value="sessions">Session override</option></select></label>
  <label>Configuration target<select value={id} onChange={e=>setId(e.target.value)}><option value="">Choose a {scope==='programs'?'program':'session'}</option>{scope==='programs'?programs.map(p=><option key={p.id} value={p.id}>{p.name}</option>):sessions.map(s=><option key={s.id} value={s.id}>{programs.find(p=>p.id===s.program_id)?.name} · {new Date(s.starts_at).toLocaleString()}</option>)}</select></label>
  {loaded&&<form key={`${scope}:${id}`} className="profileForm" onSubmit={save}><StreamFields configuration={config}/><button className="button primary">Save streaming configuration</button></form>}
  {loaded&&scope==='sessions'&&config?.providerType==='ZOOM'&&<form className="profileForm" onSubmit={correctPasscode}>
   <h3>Correct Zoom passcode</h3><p className="muted">Fix the saved passcode even after class starts. The meeting and provider stay the same. Members must finish class and enter again to retry.</p>
   <label>Actual Zoom passcode<input name="correctedPasscode" type="password" autoComplete="off" required maxLength={128}/></label>
   <button className="button primary" disabled={correcting}>{correcting?'Updating passcode…':'Update Zoom passcode'}</button>
  </form>}
  {saved&&<p role="status" className="successMessage">{saved}</p>}{error&&<p className="errorCard" role="alert">{error}</p>}
 </section>;
}
