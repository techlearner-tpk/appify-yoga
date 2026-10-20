/* Runs only in the isolated, authenticated /meeting/{opaque-token} document. */
(async function () {
 const status=document.getElementById('meeting-status');
 const fail=message=>{status.hidden=false;status.textContent=message;};
 const token=location.pathname.split('/').pop();
 try {
  const response=await fetch('/api/backend/v1/playback/'+encodeURIComponent(token)+'/sdk',{method:'POST',credentials:'same-origin',cache:'no-store'});
  if(!response.ok){fail(response.status===503?'In-app meetings are not configured. Ask your administrator to configure the Zoom Meeting SDK.':'Session access expired or unavailable. Use Finish class, then enter again.');return;}
  const grant=await response.json();
  if(!window.ZoomMtg){fail('The meeting player could not load. Check your connection and re-enter the class.');return;}
  ZoomMtg.setZoomJSLib('https://source.zoom.us/6.5.0/lib','/av');
  ZoomMtg.preLoadWasm();ZoomMtg.prepareWebSDK();
  ZoomMtg.i18n.load('en-US');
  ZoomMtg.i18n.onLoad(function(){ZoomMtg.init({
   leaveUrl:location.origin+'/meeting/ended',patchJsMedia:true,debug:false,
   disableCORP:!window.crossOriginIsolated,disableInvite:true,meetingInfo:['topic','host'],
   success:function(){ZoomMtg.join({signature:grant.signature,meetingNumber:grant.meetingNumber,passWord:grant.password,userName:grant.userName,
    success:function(){status.hidden=true;},
    error:function(){fail('Unable to join. Ask the host to check the meeting, actual passcode, SDK account and meeting settings. Then re-enter the class.');}
   });},
   error:function(){fail('The meeting player could not start. Check browser permissions and re-enter the class.');}
  });});
 } catch {fail('Unable to connect to your class. Check your connection and re-enter the class.');}
})();
