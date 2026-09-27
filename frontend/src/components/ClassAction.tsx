'use client';
import Link from 'next/link';
import {useEffect,useState} from 'react';
export function ClassAction({session}:{session:{id:string;starts_at:string;ends_at:string}}){
  const [now,setNow]=useState(Date.now());
  useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),30000);return()=>clearInterval(timer)},[]);
  const start=new Date(session.starts_at).getTime(),end=new Date(session.ends_at).getTime();
  if(now>=end)return <span className="classEnded">Completed ✓</span>;
  if(now<start)return <Link href={`/live/${session.id}`} className="button outline">In {Math.ceil((start-now)/60000)} min ↗</Link>;
  return <Link href={`/live/${session.id}`} className="button primary">Join live ↗</Link>;
}
