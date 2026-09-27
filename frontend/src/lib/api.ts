export class ApiError extends Error {constructor(public status:number,message:string){super(message)}}
const accessMessages:Record<string,string>={LOGIN_REQUIRED:'Please sign in.',MEMBERSHIP_REQUIRED:'A current membership is needed for this class.',PROGRAM_NOT_ENROLLED:'Join this program to attend.',SESSION_NOT_FOUND:'This class is unavailable.',SESSION_NOT_OPEN:'This class has not opened yet.',SESSION_EXPIRED:'This class has closed.',ALREADY_ATTENDED_TODAY:'You have already joined another class for this program today.',ASSET_NOT_READY:'Your class is being prepared. Please check back soon.',ACCOUNT_DISABLED:'This account cannot access classes.'};
export async function api<T=unknown>(path:string,options:RequestInit={}):Promise<T>{
  const response=await fetch(`/api/backend${path}`,{...options,headers:{'Content-Type':'application/json',...options.headers}});
  if(response.status===401){const next=location.pathname+location.search;location.href=`/login?next=${encodeURIComponent(next)}`;throw new Error('Please sign in');}
  const body=await response.json().catch(()=>({}));
  if(!response.ok) throw new ApiError(response.status,accessMessages[body.outcome]||body.error||`Request failed (${response.status})`);
  return body as T;
}
export function stamp(value:string){return new Date(value).toLocaleString(undefined,{weekday:'short',hour:'numeric',minute:'2-digit'});}
