import {cookies} from 'next/headers';

// Isolate the vendor's React 18 client and global CSS from our React 19 application.
// Pinned official CDN distribution; no signing credentials or meeting details in HTML.
export async function GET(_request:Request,{params}:{params:Promise<{token:string}>}) {
 const {token}=await params;
 const headers={'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Frame-Options':'SAMEORIGIN','Content-Security-Policy':"frame-ancestors 'self'; base-uri 'none'; object-src 'none'"};
 if(!/^[A-Za-z0-9_-]{43}$/.test(token)||!(await cookies()).has('access'))return new Response('Please return to your class and sign in.',{status:401,headers});
 const version='6.5.0',cdn=`https://source.zoom.us/${version}`;
 return new Response(`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Your class</title>
 <style>html,body{margin:0;height:100%;background:#111;color:#fff;font-family:system-ui}#meeting-status{position:relative;z-index:100000;padding:16px;background:#111}#meeting-status[hidden]{display:none}button{padding:10px}</style></head>
 <body><div id="meeting-status" role="status" aria-live="polite">Connecting to your class…</div><div id="zmmtg-root"></div><div id="aria-notify-area"></div>
 ${['react','react-dom','redux','redux-thunk','react-redux','lodash'].map(name=>`<script src="${cdn}/lib/vendor/${name}.min.js"></script>`).join('')}
 <script src="${cdn}/zoom-meeting-${version}.min.js"></script><script src="/meeting-client.js"></script></body></html>`,{headers});
}
