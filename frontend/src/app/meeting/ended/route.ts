export function GET() {
 return new Response('<!doctype html><html lang="en"><meta charset="utf-8"><title>Class ended</title><body style="background:#111;color:white;font-family:system-ui;padding:24px"><h1>You have left the class.</h1><p>Use Finish class below, or re-enter from your class page to reconnect.</p></body></html>',{headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Frame-Options':'SAMEORIGIN'}});
}
