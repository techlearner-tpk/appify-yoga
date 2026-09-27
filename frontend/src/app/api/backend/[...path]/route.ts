import {cookies} from 'next/headers';
import {NextRequest,NextResponse} from 'next/server';
async function proxy(request:NextRequest,{params}:{params:Promise<{path:string[]}>}){
  const {path}=await params;const token=(await cookies()).get('access')?.value;
  if(!token) return NextResponse.json({error:'Please sign in'},{status:401});
  const method=request.method;const origin=request.headers.get('origin');
  if(method!=='GET' && origin && new URL(origin).host!==request.headers.get('host')) return NextResponse.json({error:'Invalid origin'},{status:403});
  const backend=process.env.BACKEND_URL||'http://localhost:8080';
  const url=`${backend}/api/${path.map(encodeURIComponent).join('/')}${request.nextUrl.search}`;
  const upstream=await fetch(url,{method,headers:{Authorization:`Bearer ${token}`,'Content-Type':request.headers.get('content-type')||'application/json'},body:method==='GET'?undefined:await request.arrayBuffer(),cache:'no-store'}).catch(()=>null);
  if(!upstream) return NextResponse.json({error:'Service unavailable'},{status:503});
  return new NextResponse(await upstream.text(),{status:upstream.status,headers:{'Content-Type':upstream.headers.get('Content-Type')||'application/json','Cache-Control':'no-store','Referrer-Policy':'no-referrer'}});
}
export const GET=proxy;export const POST=proxy;export const PUT=proxy;export const PATCH=proxy;export const DELETE=proxy;
