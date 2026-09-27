'use client';
import Link from 'next/link';
import {useEffect,useState} from 'react';
export type MemberSlot={slotId:string;programName:string;displayDate:string;startTime:string;endTime:string;startsAt:string;endsAt:string;joinAvailability:string;selected:boolean;joined:boolean;completed:boolean};
export function ClassAction({session}:{session:MemberSlot}){
  const [now,setNow]=useState(Date.now());
  useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),30000);return()=>clearInterval(timer)},[]);
  const end=new Date(session.endsAt).getTime();
  if(session.completed)return <span className="classEnded">Completed ✓</span>;
  if(session.joinAvailability==='ALREADY_ATTENDED_TODAY')return <span className="classEnded">Another class joined today</span>;
  if(session.joinAvailability==='SESSION_EXPIRED'||now>=end+300000)return <span className="classEnded">Closed</span>;
  if(session.joinAvailability==='MEMBERSHIP_REQUIRED')return <Link href="/profile" className="button outline">Membership needed</Link>;
  const isOpen=session.joinAvailability==='ALLOW';
  return <Link href={`/live/${session.slotId}`} className={`button ${isOpen?'primary':'outline'}`}>{session.joined?'Resume class →':isOpen?'Enter class →':'View class →'}</Link>;
}
