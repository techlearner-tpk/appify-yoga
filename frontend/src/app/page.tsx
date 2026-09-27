'use client';
import Link from 'next/link';
import {useEffect,useState} from 'react';
import {Shell} from '@/components/Shell';
import {Loading,ErrorNotice,Empty} from '@/components/States';
import {api} from '@/lib/api';
import {ClassAction,MemberSlot} from '@/components/ClassAction';
type Habit={id:string;name:string;done:boolean};
type Today={profile:{display_name:string};sessions:MemberSlot[];habits:Habit[];progress:{streak:{current_days:number;longest_days:number};weeklyConsistency:number;challenges:{name:string;progress_days:number;duration_days:number}[]}};
export default function TodayPage(){
 const [data,setData]=useState<Today|null>(null),[error,setError]=useState('');
 useEffect(()=>{api<Today>('/today').then(setData).catch(e=>setError(e.message))},[]);
 async function habit(id:string){try{await api(`/habits/${id}/complete`,{method:'POST'});setData(d=>d?{...d,habits:d.habits.map(h=>h.id===id?{...h,done:true}:h)}:d)}catch(e){setError((e as Error).message)}}
 const next=data?.sessions.find(s=>new Date(s.endsAt).getTime()>Date.now());
 return <Shell title={data?`Good day, ${data.profile.display_name.split(' ')[0]}.`:'Today'} subtitle="Small moments become lasting habits.">
  {error&&<ErrorNotice message={error}/>}{!data&&!error?<Loading/>:data&&<>
   <div className="heroGrid"><section className="heroCard"><div className="heroDecor">✺</div><span className="eyebrow">YOUR DAILY MOMENT</span><h2>Today is a good day<br/>to show up.</h2><p>Even a few mindful minutes count. Your next step starts here.</p><Link href={next?`/live/${next.slotId}`:'/programs'} className="button light">{next?'View next class →':'Explore programs →'}</Link></section><div className="statGrid"><div className="statCard"><span className="statIcon">🔥</span><strong>{data.progress.streak.current_days}</strong><span>day streak</span><small>Longest: {data.progress.streak.longest_days} days</small></div><div className="statCard warm"><span className="statIcon">◷</span><strong>{data.progress.weeklyConsistency}<small>/7</small></strong><span>this week</span><small>Days you showed up</small></div></div></div>
   <div className="contentGrid"><section className="panel"><div className="sectionHead"><div><span className="eyebrow">MOVE WITH US</span><h2>Today’s classes</h2></div><Link href="/programs">All programs ↗</Link></div>{data.sessions.length?data.sessions.map(s=><div className="classRow" key={s.slotId}><div className="classTime">{s.startTime}</div><div className="classInfo"><strong>{s.programName}</strong><small>{s.displayDate} · {s.startTime}–{s.endTime}{s.selected?' · Selected':''}</small></div><ClassAction session={s}/></div>):<Empty message="No classes today. Explore a program to get started."/>}</section><section className="panel"><div className="sectionHead"><div><span className="eyebrow">KEEP IT SIMPLE</span><h2>Daily habits</h2></div></div>{data.habits.map(h=><button className={`habitRow ${h.done?'done':''}`} onClick={()=>habit(h.id)} disabled={h.done} key={h.id}><span className="habitCheck">{h.done?'✓':''}</span>{h.name}<span className="habitArrow">{h.done?'Done':'Mark done →'}</span></button>)}<div className="panelNote">Every small choice is a step forward.</div></section></div>
   {data.progress.challenges.length>0&&<section className="panel spaced"><div className="sectionHead"><div><span className="eyebrow">GO THE DISTANCE</span><h2>Your challenges</h2></div><Link href="/challenges">View all ↗</Link></div>{data.progress.challenges.map((c,i)=><div className="challengeMini" key={i}><strong>{c.name}</strong><span>{c.progress_days}/{c.duration_days} days</span><div className="progressTrack"><div style={{width:`${Math.min(100,c.progress_days/c.duration_days*100)}%`}}/></div></div>)}</section>}
  </>}
 </Shell>
}
