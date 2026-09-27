export function youtubeId(value:string|null|undefined):string|null{
  if(!value)return null;
  const raw=value.trim();
  if(/^[A-Za-z0-9_-]{11}$/.test(raw))return raw;
  try{
    const url=new URL(raw);
    if(['youtube.com','www.youtube.com','m.youtube.com'].includes(url.hostname)){
      const id=url.pathname==='/watch'?url.searchParams.get('v'):url.pathname.match(/^\/(?:live|embed)\/([A-Za-z0-9_-]{11})$/)?.[1];
      return id&&/^[A-Za-z0-9_-]{11}$/.test(id)?id:null;
    }
    if(url.hostname==='youtu.be'){
      const id=url.pathname.slice(1);return /^[A-Za-z0-9_-]{11}$/.test(id)?id:null;
    }
  }catch{}
  return null;
}
