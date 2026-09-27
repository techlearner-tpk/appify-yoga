import {cookies} from 'next/headers';
import {NextRequest,NextResponse} from 'next/server';
export async function POST(request:NextRequest,{params}:{params:Promise<{action:string}>}){
  const {action}=await params;
  if(!['login','register','logout','refresh'].includes(action)) return NextResponse.json({error:'Unknown action'},{status:404});
  const origin=request.headers.get('origin');
  if(origin && new URL(origin).host!==request.headers.get('host')) return NextResponse.json({error:'Invalid origin'},{status:403});
  const jar=await cookies();
  const data=action==='refresh'||action==='logout'?{refreshToken:jar.get('refresh')?.value}:await request.json();
  const backend=process.env.BACKEND_URL||'http://localhost:8080';
  const upstream=await fetch(`${backend}/api/auth/${action}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data),cache:'no-store'}).catch(()=>null);
  if(!upstream) return NextResponse.json({error:'Service unavailable'},{status:503});
  const result=await upstream.json().catch(()=>({}));
  if(!upstream.ok) return NextResponse.json(result,{status:upstream.status});
  const response=NextResponse.json(action==='logout'?{ok:true}:{userId:result.userId,role:result.role});
  if(action==='logout'){response.cookies.delete('access');response.cookies.delete('refresh');}
  else {response.cookies.set('access',result.accessToken,{httpOnly:true,sameSite:'lax',secure:process.env.NODE_ENV==='production' && request.nextUrl.protocol==='https:',path:'/',maxAge:900});response.cookies.set('refresh',result.refreshToken,{httpOnly:true,sameSite:'lax',secure:process.env.NODE_ENV==='production' && request.nextUrl.protocol==='https:',path:'/',maxAge:30*86400});}
  return response;
}
