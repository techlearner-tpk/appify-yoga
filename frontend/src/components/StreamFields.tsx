'use client';
import {useState} from 'react';
export type StreamConfiguration={providerType:string;enabled:boolean;providerJoinUrl?:string;providerMeetingId?:string;providerVideoId?:string;meetingPasscode?:string;technicalNotes?:string};
export function streamFromForm(form:FormData):StreamConfiguration {
 return {providerType:String(form.get('providerType')),enabled:form.has('streamEnabled'),providerJoinUrl:String(form.get('meetingUrl')||''),providerMeetingId:String(form.get('meetingId')||''),meetingPasscode:String(form.get('meetingPasscode')||''),providerVideoId:String(form.get('youtubeUrl')||''),technicalNotes:String(form.get('technicalNotes')||'')};
}
export function StreamFields({configuration}:{configuration?:StreamConfiguration}) {
 const [provider,setProvider]=useState(configuration?.providerType||'YOUTUBE');
 return <>
  <label>Streaming Provider<select name="providerType" value={provider} onChange={e=>setProvider(e.target.value)}><option value="YOUTUBE">YouTube Live</option><option value="ZOOM">Zoom</option></select></label>
  <label><input name="streamEnabled" type="checkbox" defaultChecked={configuration?.enabled??true}/> Stream enabled</label>
  {provider==='ZOOM'?<>
   <label>Meeting URL<input name="meetingUrl" type="url" defaultValue={configuration?.providerJoinUrl||''} placeholder="https://us02web.zoom.us/j/…"/></label>
   <label>Meeting ID (optional)<input name="meetingId" defaultValue={configuration?.providerMeetingId||''}/></label>
   <label>Meeting Passcode (optional)<input name="meetingPasscode" type="password" autoComplete="off" defaultValue={configuration?.meetingPasscode||''}/></label>
  </>:<label>YouTube Live ID or URL<input name="youtubeUrl" defaultValue={configuration?.providerVideoId||''} placeholder="https://www.youtube.com/live/…"/></label>}
  <label>Technical notes (admin only)<textarea name="technicalNotes" defaultValue={configuration?.technicalNotes||''}/></label>
 </>;
}
