'use client';

import {FormEvent,useEffect,useState} from 'react';
import {Shell} from '@/components/Shell';
import {Loading,ErrorNotice} from '@/components/States';
import {api} from '@/lib/api';
import {youtubeId} from '@/lib/youtube';

type Stats={users:number;activeUsers:number;todaySessions:number;todayAttendance:number;qualifiedAttendance:number;queueDepth:number;failedMessages:number};
type Notification={id:string;email:string;body:string;status:string};
type Program={id:string;name:string};
type Instructor={id:string;name:string;email:string};

export default function Admin(){
 const [stats,setStats]=useState<Stats|null>(null),[notes,setNotes]=useState<Notification[]>([]);
 const [programs,setPrograms]=useState<Program[]>([]),[instructors,setInstructors]=useState<Instructor[]>([]);
 const [error,setError]=useState(''),[created,setCreated]=useState(''),[published,setPublished]=useState('');
 const [onboarded,setOnboarded]=useState(''),[createdSession,setCreatedSession]=useState('');
 useEffect(()=>{Promise.all([api<Stats>('/admin/dashboard'),api<Notification[]>('/admin/notifications'),api<Program[]>('/admin/programs'),api<Instructor[]>('/admin/instructors')]).then(([s,n,p,i])=>{setStats(s);setNotes(n);setPrograms(p);setInstructors(i)}).catch(e=>setError(e.message))},[]);

 async function create(e:FormEvent<HTMLFormElement>){e.preventDefault();setError('');const form=new FormData(e.currentTarget);try{
  const result=await api<{id:string}>('/admin/programs',{method:'POST',body:JSON.stringify({name:form.get('name'),description:form.get('description'),difficulty:form.get('difficulty'),durationMinutes:Number(form.get('duration')),programType:form.get('type')})});
  setCreated(`Program created: ${result.id}`);setPrograms(await api<Program[]>('/admin/programs'));e.currentTarget.reset();
 }catch(e){setError((e as Error).message)}}
 async function onboard(e:FormEvent<HTMLFormElement>){e.preventDefault();setError('');const form=new FormData(e.currentTarget);try{
  const result=await api<{email:string}>('/admin/instructors',{method:'POST',body:JSON.stringify({email:form.get('email'),bio:form.get('bio')})});
  setOnboarded(`${result.email} can now sign in as an instructor.`);setInstructors(await api<Instructor[]>('/admin/instructors'));e.currentTarget.reset();
 }catch(e){setError((e as Error).message)}}
 async function createLive(e:FormEvent<HTMLFormElement>){e.preventDefault();setError('');const form=new FormData(e.currentTarget),raw=String(form.get('youtubeUrl')||'').trim();
  const video=raw?youtubeId(raw):null;if(raw&&!video){setError('Enter a valid YouTube video ID or URL.');return;}
  try{const result=await api<{id:string}>('/admin/sessions',{method:'POST',body:JSON.stringify({programId:form.get('programId'),instructorId:form.get('instructorId')||null,startsAt:new Date().toISOString(),durationMinutes:Number(form.get('durationMinutes')),youtubeVideoId:video})});
   setCreatedSession(`Live session created: ${result.id}`);e.currentTarget.reset();
  }catch(e){setError((e as Error).message)}
 }
 async function publish(e:FormEvent<HTMLFormElement>){e.preventDefault();setError('');const form=new FormData(e.currentTarget);try{
  const response=await fetch('/api/backend/admin/content',{method:'POST',body:form});const data=await response.json();if(!response.ok)throw new Error(data.error||'Unable to publish');setPublished(`Published: ${data.id}`);e.currentTarget.reset();
 }catch(e){setError((e as Error).message)}}

 return <Shell title="Studio overview." subtitle="A clear view of your community and operations.">{error&&<ErrorNotice message={error}/>} {!stats&&!error?<Loading/>:stats&&<>
  <div className="adminStats">{[['Members',stats.users],['Active users',stats.activeUsers],['Today’s sessions',stats.todaySessions],['Today’s attendance',stats.todayAttendance],['Qualified',stats.qualifiedAttendance],['Queue depth',stats.queueDepth],['Failed messages',stats.failedMessages]].map(([label,value])=><div className="adminStat" key={label}><strong>{value}</strong><span>{label}</span></div>)}</div>
  <div className="contentGrid">
   <section className="panel"><span className="eyebrow">FAKE WHATSAPP</span><h2>Outgoing messages</h2>{notes.length?notes.map(n=><div className="messageRow" key={n.id}><div><strong>{n.email}</strong><small>{n.body}</small></div><span className={`status ${n.status.toLowerCase()}`}>{n.status}</span></div>):<p className="muted">Messages will appear here after class reminders or qualified attendance.</p>}</section>
   <form className="panel profileForm" onSubmit={create}><span className="eyebrow">CATALOG</span><h2>Create a program</h2><label>Name<input name="name" required/></label><label>Description<textarea name="description" required/></label><div className="formTwo"><label>Difficulty<select name="difficulty"><option>BEGINNER</option><option>INTERMEDIATE</option><option>ADVANCED</option></select></label><label>Type<select name="type"><option>YOGA</option><option>STRENGTH</option><option>BREATHING</option><option>MEDITATION</option></select></label></div><label>Duration (minutes)<input name="duration" type="number" min="1" defaultValue="45" required/></label><button className="button primary">Create program</button>{created&&<div className="successMessage">{created}</div>}</form>
  </div>
  <div className="contentGrid">
   <form className="panel profileForm spaced" onSubmit={onboard}><span className="eyebrow">INSTRUCTORS</span><h2>Onboard an instructor</h2><p className="muted">Ask them to create a member account first. Promote that account here; they should sign out and sign back in to see their classes.</p><label>Registered email<input name="email" type="email" required/></label><label>Bio (optional)<textarea name="bio"/></label><button className="button primary">Grant instructor access</button>{onboarded&&<div className="successMessage">{onboarded}</div>}<p className="muted">Current instructors: {instructors.map(i=>`${i.name} (${i.email})`).join(', ')||'None yet'}</p></form>
   <form className="panel profileForm spaced" onSubmit={createLive}><span className="eyebrow">LIVE CLASSES</span><h2>Create a live session</h2><label>Program<select name="programId" required>{programs.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select></label><label>Instructor<select name="instructorId" required><option value="">Choose an instructor</option>{instructors.map(i=><option key={i.id} value={i.id}>{i.name} ({i.email})</option>)}</select></label><label>YouTube Live ID or URL<input name="youtubeUrl" placeholder="https://www.youtube.com/live/…" required/></label><label>Duration (minutes)<input name="durationMinutes" type="number" min="1" defaultValue="60" required/></label><button className="button primary">Start test session now</button>{createdSession&&<div className="successMessage">{createdSession}</div>}</form>
  </div>
  <form className="panel profileForm spaced" onSubmit={publish}><span className="eyebrow">LIBRARY</span><h2>Publish an article</h2><label>Title<input name="title" required/></label><label>Body<textarea name="body" required/></label><label>Image (optional)<input name="image" type="file" accept="image/png,image/jpeg,image/webp"/></label><button className="button primary">Publish article</button>{published&&<span className="successMessage">{published}</span>}</form>
 </>}</Shell>;
}
