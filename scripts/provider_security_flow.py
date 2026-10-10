#!/usr/bin/env python3
"""Stage 1 provider/API/security checks. Uses the local Compose database for expiry fixtures."""
import hashlib
import json
import os
import subprocess
import uuid
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen, build_opener, HTTPRedirectHandler

BASE = os.getenv('API_URL', 'http://localhost:8080')
PASSWORD = os.getenv('DEMO_PASSWORD', 'DemoPass123!')
class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def call(path, method='GET', body=None, token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        with build_opener(NoRedirect).open(req,timeout=20) as r:
            return r.status,json.loads(r.read() or '{}'),dict(r.headers)
    except HTTPError as e:
        raw=e.read()
        return e.code,json.loads(raw or '{}'),dict(e.headers)

def expect(status,result):
    assert result[0]==status, (result[0],status) # Never echo provider secrets/tokens on failure.
    return result[1]

def sql(statement):
    r=subprocess.run(['docker','compose','exec','-T','postgres','psql','-U','wellness','-d','wellness','-At','-v','ON_ERROR_STOP=1'],input=statement,text=True,capture_output=True)
    assert r.returncode==0,'Database fixture failed'
    return r.stdout.strip()

def digest(token): return hashlib.sha256(token.encode()).hexdigest()
def safe(value):
    text=json.dumps(value).lower()
    for hidden in ['zoom.us','youtube.com','providerjoinurl','providervideoid','providermeetingid','meetingpasscode','stage1-private','2afajz1hd-e']:
        assert hidden not in text,'Member response contains provider configuration'

admin=expect(200,call('/api/auth/login','POST',{'email':'admin@example.test','password':PASSWORD}))['accessToken']
program=expect(200,call('/api/admin/programs','POST',{'name':'Provider Security '+uuid.uuid4().hex[:8],'description':'Isolated provider tests','durationMinutes':15},admin))['id']
zoom={'providerType':'ZOOM','enabled':True,'providerJoinUrl':'https://us02web.zoom.us/j/12345678901?pwd=stage1-private','providerMeetingId':'12345678901','meetingPasscode':'stage1-private','technicalNotes':'admin only'}
youtube={'providerType':'YOUTUBE','enabled':True,'providerVideoId':'2afajz1hd-E'}
expect(200,call(f'/api/admin/programs/{program}/stream','PUT',zoom,admin))
users=[]
for index in range(3):
    email=f'provider-{uuid.uuid4().hex[:12]}@example.test'
    users.append(expect(200,call('/api/auth/register','POST',{'email':email,'password':PASSWORD,'displayName':'Provider Member'}))['accessToken'])
    if index<2: expect(200,call(f'/api/programs/{program}/enroll','POST',{},users[-1]))
starts=(datetime.now(timezone.utc)+timedelta(minutes=2)).isoformat()
slot=expect(200,call('/api/admin/sessions','POST',{'programId':program,'startsAt':starts,'durationMinutes':15},admin))['id']
path=f'/api/v1/session-slots/{slot}/join'
expect(403,call(path,'POST',{}))
expect(403,call(path,'POST',{},users[2]))
for user in users[:2]:
    safe(expect(200,call('/api/sessions',token=user)));safe(expect(200,call(f'/api/sessions/{slot}',token=user)))
    expect(403,call(f'/api/admin/sessions/{slot}/stream',token=user))
    expect(403,call(f'/api/admin/sessions/{slot}/stream','PUT',zoom,user))
instructor=expect(200,call('/api/auth/login','POST',{'email':'instructor@example.test','password':PASSWORD}))['accessToken']
expect(403,call(f'/api/admin/programs/{program}/stream',token=instructor))
# Disabled program configuration denies join and a session override can explicitly enable it.
expect(200,call(f'/api/admin/programs/{program}/stream','PUT',{**zoom,'enabled':False},admin))
expect(409,call(path,'POST',{},users[0]))
expect(200,call(f'/api/admin/sessions/{slot}/stream','PUT',zoom,admin))
link=expect(200,call(f'/api/v1/join-links/{slot}','POST',{},users[0]))['token']
expect(404,call('/api/v1/join-links/'+link,token=users[1]))
expect(200,call('/api/v1/join-links/'+link,token=users[0]))
joined=expect(200,call(path,'POST',{},users[0]));safe(joined)
token=joined['playbackToken'];experience=expect(200,call('/api/v1/playback/'+token,token=users[0]));safe(experience)
assert experience['playerType']=='REDIRECT' and experience['redirectUrl'].startswith('/api/provider-access/')
expect(404,call('/api/v1/playback/'+token+'/open',token=users[1]))
redirect=call('/api/v1/playback/'+token+'/open',token=users[0]);expect(303,redirect)
assert redirect[2]['Location']==zoom['providerJoinUrl']
assert redirect[2]['Cache-Control']=='no-store'
attendance=expect(200,call('/api/attendance/'+slot,token=users[0]));assert attendance['watched_seconds']==0 and not attendance['qualified']
expect(403,call('/api/attendance/heartbeat','POST',{'sessionId':slot,'requestId':str(uuid.uuid4()),'playbackToken':token},users[0]))
# Configuration changes before start are honored by new grants and revoke the old grants.
expect(200,call(f'/api/admin/sessions/{slot}/stream','PUT',youtube,admin))
expect(409,call('/api/v1/playback/'+token+'/open',token=users[0]))
joined=expect(200,call(path,'POST',{},users[0]));token=joined['playbackToken'];experience=expect(200,call('/api/v1/playback/'+token,token=users[0]))
assert experience['playerType']=='EMBEDDED' and experience['embedUrl'].startswith('https://www.youtube-nocookie.com/embed/')
expect(409,call('/api/v1/playback/'+token+'/open',token=users[0]))
user_id=sql("select user_id from playback_session where token_hash='"+digest(token)+"';")
assert str(uuid.UUID(user_id))==user_id
sql(f"update membership_entitlement set valid_until=now()-interval '1 second' where user_id='{user_id}';")
expect(403,call(path,'POST',{},users[0]));expect(403,call('/api/v1/playback/'+token,token=users[0]))
sql(f"update membership_entitlement set valid_until=now()+interval '1 day' where user_id='{user_id}'; update app_user set disabled=true where id='{user_id}';")
expect(403,call(path,'POST',{},users[0]))
sql(f"update app_user set disabled=false where id='{user_id}'; update session_join_link set expires_at=now()-interval '1 second' where token_hash='{digest(link)}'; update playback_session set expires_at=now()-interval '1 second' where token_hash='{digest(token)}';")
expect(409,call('/api/v1/join-links/'+link,token=users[0]));expect(409,call('/api/v1/playback/'+token,token=users[0]))
sql(f"update session set starts_at=now()-interval '1 minute' where id='{slot}';")
expect(409,call(f'/api/admin/sessions/{slot}/stream','PUT',zoom,admin))
# Ciphertext and normal logs must not contain meeting credentials.
assert sql(f"select encrypted_configuration from session_stream_configuration where program_id='{program}';").startswith('v1:')
assert 'stage1-private' not in sql(f"select encrypted_configuration from session_stream_configuration where program_id='{program}';")
logs=subprocess.run(['docker','compose','logs','--no-color','backend-api','frontend','scheduler','background-worker'],capture_output=True,text=True,check=True).stdout
assert all(secret not in logs for secret in ['stage1-private',zoom['providerJoinUrl'],youtube['providerVideoId']]),'Provider secrets appeared in logs'
print('Provider security passed: inherited/overridden configuration, encrypted secrets, authorized redirect/embed, disabled streams, membership checks, user-bound grants, token expiry, entry-only attendance, no log leaks')
