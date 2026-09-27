export async function api<T=unknown>(path:string,options:RequestInit={}):Promise<T>{
  const response=await fetch(`/api/backend${path}`,{...options,headers:{'Content-Type':'application/json',...options.headers}});
  if(response.status===401){location.href='/login';throw new Error('Please sign in');}
  const body=await response.json().catch(()=>({}));
  if(!response.ok) throw new Error(body.error||`Request failed (${response.status})`);
  return body as T;
}
export function stamp(value:string){return new Date(value).toLocaleString(undefined,{weekday:'short',hour:'numeric',minute:'2-digit'});}
