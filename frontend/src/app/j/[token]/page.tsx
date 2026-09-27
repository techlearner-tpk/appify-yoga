'use client';
import {useParams,useRouter} from 'next/navigation';
import {useEffect,useState} from 'react';
import {Shell} from '@/components/Shell';
import {Loading,ErrorNotice} from '@/components/States';
import {api} from '@/lib/api';
export default function JoinLinkPage(){
 const {token}=useParams<{token:string}>(),router=useRouter();const [error,setError]=useState('');
 useEffect(()=>{api<{slotId:string}>(`/v1/join-links/${encodeURIComponent(token)}`).then(({slotId})=>router.replace(`/live/${slotId}`)).catch(e=>setError(e.message))},[token,router]);
 return <Shell title="Your class" subtitle="Opening your session…">{error?<ErrorNotice message={error}/>:<Loading/>}</Shell>;
}
