'use client';

import Link from 'next/link';
import {usePathname,useRouter} from 'next/navigation';
import {useEffect,useState} from 'react';
import {api} from '@/lib/api';

const links=[['/','Today','◉'],['/programs','Programs','◫'],['/challenges','Challenges','✦'],['/progress','Progress','▥'],['/profile','Profile','○']];
type Role='USER'|'INSTRUCTOR'|'ADMIN';

export function Shell({children,title,subtitle}:{children:React.ReactNode,title:string,subtitle?:string}){
 const path=usePathname();const router=useRouter();const [open,setOpen]=useState(false);const [role,setRole]=useState<Role|null>(null);
 useEffect(()=>{api<{role:Role}>('/me').then(user=>setRole(user.role)).catch(()=>{})},[]);
 const canTeach=role==='INSTRUCTOR'||role==='ADMIN',canAdmin=role==='ADMIN';
 async function logout(){await fetch('/api/auth/logout',{method:'POST'});setRole(null);router.push('/login');router.refresh();}
 return <div className="shell">
  <aside className="sidebar">
   <Link className="brand" href="/"><span className="brandMark">✺</span><span>appify<span className="brandAccent">wellness</span></span></Link>
   <div className="sidebarEyebrow">YOUR SPACE</div>
   <nav>{links.map(([url,label,icon])=><Link key={url} className={`navItem ${path===url?'active':''}`} href={url}><span>{icon}</span>{label}</Link>)}<Link className={`navItem ${path==='/library'?'active':''}`} href="/library"><span>☷</span>Library</Link></nav>
   <div className="sidebarFooter">{canTeach&&<Link href="/instructor">Instructor view</Link>}{canAdmin&&<Link href="/admin">Admin portal</Link>}<button onClick={logout}>Sign out ↗</button></div>
  </aside>
  <div className="mainArea"><header className="topbar"><Link className="mobileBrand" href="/"><span className="brandMark">✺</span> appifywellness</Link><span className="topbarNote">A little better, every day</span><button className="menuButton" onClick={()=>setOpen(!open)} aria-label="Open navigation" aria-expanded={open}>☰</button></header>
   {open&&<nav className="mobileMenu">{links.map(([url,label])=><Link onClick={()=>setOpen(false)} key={url} href={url}>{label}</Link>)}<Link onClick={()=>setOpen(false)} href="/library">Library</Link>{canTeach&&<Link onClick={()=>setOpen(false)} href="/instructor">Instructor view</Link>}{canAdmin&&<Link onClick={()=>setOpen(false)} href="/admin">Admin portal</Link>}<button onClick={logout}>Sign out</button></nav>}
   <main><div className="pageHeading"><div><div className="eyebrow">APPIFY WELLNESS</div><h1>{title}</h1>{subtitle&&<p>{subtitle}</p>}</div><span className="datePill">{new Date().toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'})}</span></div>{children}</main>
  </div>
  <nav className="bottomNav">{links.map(([url,label,icon])=><Link key={url} className={path===url?'active':''} href={url}><span>{icon}</span><small>{label}</small></Link>)}</nav>
 </div>;
}
