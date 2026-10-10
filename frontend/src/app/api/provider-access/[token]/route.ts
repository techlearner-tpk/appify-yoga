import {cookies} from 'next/headers';
import {NextResponse} from 'next/server';
export async function GET(_request:Request,{params}:{params:Promise<{token:string}>}) {
  const {token}=await params;
  const access=(await cookies()).get('access')?.value;
  const headers={'Cache-Control':'no-store','Referrer-Policy':'no-referrer'};
  if(!access || !/^[A-Za-z0-9_-]{43}$/.test(token)) return NextResponse.json({error:'Session authorization required'},{status:401,headers});
  const upstream=await fetch(`${process.env.BACKEND_URL||'http://localhost:8080'}/api/v1/playback/${encodeURIComponent(token)}/open`,{headers:{Authorization:`Bearer ${access}`},redirect:'manual',cache:'no-store'}).catch(()=>null);
  if(!upstream) return NextResponse.json({error:'Service unavailable'},{status:503,headers});
  if(upstream.status!==303) return NextResponse.json({error:'Session access expired or unavailable. Return to your class and join again.'},{status:upstream.status,headers});
  // Forward only the authorized destination; never forward the app's bearer credential.
  const destination=upstream.headers.get('location');
  if(!destination) return NextResponse.json({error:'Session unavailable'},{status:502,headers});
  return new NextResponse(null,{status:303,headers:{...headers,Location:destination}});
}
