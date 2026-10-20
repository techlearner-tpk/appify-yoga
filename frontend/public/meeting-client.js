/* Runs only in the isolated, authenticated /meeting/{opaque-token} document. */
(async function () {
 const status=document.getElementById('meeting-status');
 const show=(stage,message)=>{status.dataset.connectionStage=stage;status.hidden=false;status.textContent=message;};
 const fail=(message,error)=>{
  // Vendor errors can include join arguments. Surface only a bounded numeric code.
  delete status.dataset.connectionCode;
  const candidate=error&&error.errorCode;
  const code=typeof candidate==='number'||typeof candidate==='string'?String(candidate):'';
  if(/^[0-9]{1,6}$/.test(code))status.dataset.connectionCode=code;
  show('failed',message+(/^[0-9]{1,6}$/.test(code)?' (connection code '+code+')':''));
 };
 const connected=()=>{delete status.dataset.connectionCode;status.dataset.connectionStage='connected';status.hidden=true;};
 show('authorizing','Authorizing your class…');
 const token=location.pathname.split('/').pop();
 try {
  const response=await fetch('/api/backend/v1/playback/'+encodeURIComponent(token)+'/sdk',{method:'POST',credentials:'same-origin',cache:'no-store'});
  if(!response.ok){fail(response.status===503?'In-app meetings are not configured. Ask your administrator to configure the Zoom Meeting SDK.':'Session access expired or unavailable. Use Finish class, then enter again.');return;}
  const grant=await response.json();
  if(!window.ZoomMtg){fail('The meeting player could not load. Check your connection and re-enter the class.');return;}
  if(typeof ZoomMtg.inMeetingServiceListener==='function'){
   ZoomMtg.inMeetingServiceListener('onJoinSpeed',function(data){
    if(data?.level===4)show('waiting-for-host','Waiting for the host connection.');
    else if(data?.level===5)show('joining','Connecting to your class…');
    else if(data?.level===6)show('waiting-room','Waiting for the host to admit you.');
    else if(data?.level===8)connected();
   });
   ZoomMtg.inMeetingServiceListener('onMeetingStatus',function(data){
    if(data?.status===2)connected();
    else if(data?.status===3)show('disconnected','Your class connection ended. Use Finish class, then enter again to reconnect.');
    else if(data?.status===4)show('reconnecting','Reconnecting to your class…');
   });
  }
  show('initializing','Starting your class player…');
  ZoomMtg.setZoomJSLib('https://source.zoom.us/6.5.0/lib','/av');
  ZoomMtg.preLoadWasm();ZoomMtg.prepareWebSDK();
  ZoomMtg.i18n.load('en-US');
  ZoomMtg.i18n.onLoad(function(){ZoomMtg.init({
   leaveUrl:location.origin+'/meeting/ended',patchJsMedia:true,debug:false,
   disableCORP:!window.crossOriginIsolated,disableInvite:true,meetingInfo:['topic','host'],
   success:function(){show('joining','Connecting to your class…');ZoomMtg.join({signature:grant.signature,meetingNumber:grant.meetingNumber,passWord:grant.password,userName:grant.userName,
    success:connected,
    error:function(error){fail('Unable to join. Ask the host to check the meeting, actual passcode, SDK account and meeting settings. Then re-enter the class.',error);}
   });},
   error:function(error){fail('The meeting player could not start. Check browser permissions and re-enter the class.',error);}
  });});
 } catch {fail('Unable to connect to your class. Check your connection and re-enter the class.');}
})();
