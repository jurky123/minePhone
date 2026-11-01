let socket=null,heartbeat=null,retryTimer=null,helloTimer=null,generation=0,ready=false,metadata=null;
let pendingFrame=null,frameTimer=null;
let frameVersion=0;
let lastSentAt=0,lastSentSource='',sendWaitMs=0;
function frameDiagnostics(){
  return {lastSentSource,lastSentAgeMs:lastSentAt?Math.max(0,Date.now()-lastSentAt):null,sendWaitMs,
    videoFrameAgeMs:typeof lastVideoFrameAt==='undefined'||!lastVideoFrameAt?null:Math.max(0,Date.now()-lastVideoFrameAt),
    workerDispatchMs:typeof workerDispatchMs==='undefined'?null:workerDispatchMs,
    track:typeof captureTrack==='undefined'||!captureTrack?null:{readyState:captureTrack.readyState,muted:captureTrack.muted}};
}
async function resizeSnapshot(msg){
  const version=++frameVersion,gen=generation;
  if(!ready||msg.tabId!==metadata?.activeId||typeof msg.data!=='string'||msg.data.length>64*1024*1024)return;
  const binary=atob(msg.data),bytes=new Uint8Array(binary.length);
  for(let i=0;i<bytes.length;i++)bytes[i]=binary.charCodeAt(i);
  if(bytes.length<33)return;
  const header=new DataView(bytes.buffer),w=header.getUint32(16),h=header.getUint32(20);
  if(!w||!h||w>8192||h>8192||w*h>32*1024*1024)return;
  const scale=Math.min(1,Math.min(2560,msg.maxWidth||2560)/w,Math.min(1440,msg.maxHeight||1440)/h);
  const image=await createImageBitmap(new Blob([bytes],{type:'image/png'}));
  let blob;
  try{
    const canvas=new OffscreenCanvas(Math.max(1,Math.floor(w*scale)),Math.max(1,Math.floor(h*scale)));
    canvas.getContext('2d').drawImage(image,0,0,canvas.width,canvas.height);
    blob=await canvas.convertToBlob({type:'image/png'});
  }finally{image.close();}
  if(gen!==generation||version!==frameVersion||!ready||msg.tabId!==metadata?.activeId||blob.size>16*1024*1024)return;
  pendingFrame={tabId:msg.tabId,blob,source:'watchdog',queuedAt:Date.now()};if(frameTimer===null)flushFrame();
}
function flushFrame(){
  frameTimer=null;
  if(!pendingFrame||!ready||socket?.readyState!==WebSocket.OPEN){pendingFrame=null;return;}
  if(socket.bufferedAmount>=512*1024){frameTimer=setTimeout(flushFrame,16);return;}
  const frame=pendingFrame;pendingFrame=null;
  if(frame.tabId!==metadata?.activeId)return;
  if(frame.blob){socket.send(frame.blob);markSent(frame);return;}
  const binary=atob(frame.data),bytes=new Uint8Array(binary.length);
  for(let i=0;i<binary.length;i++)bytes[i]=binary.charCodeAt(i);
  socket.send(new Blob([bytes],{type:frame.format==='jpeg'?'image/jpeg':'image/png'}));
  markSent(frame);
}
function markSent(frame){lastSentAt=Date.now();lastSentSource=frame.source||'cdp';sendWaitMs=frame.queuedAt?Math.max(0,lastSentAt-frame.queuedAt):0;}
function cleanup(){
  lastSentAt=0;lastSentSource='';sendWaitMs=0;
  if(typeof stopAudio==='function')void stopAudio();
  clearTimeout(frameTimer);frameTimer=null;pendingFrame=null;
  generation++;ready=false;clearTimeout(retryTimer);clearTimeout(helloTimer);clearInterval(heartbeat);
  if(socket){socket.onclose=null;socket.onerror=null;socket.close();socket=null;}metadata=null;
}
function ended(error){cleanup();chrome.runtime.sendMessage({target:'worker',type:'capture-ended',error}).catch(()=>{});}
function state(connected,jpeg=false){chrome.runtime.sendMessage({target:'worker',type:'bridge-state',connected,jpeg}).catch(()=>{});}
function start(){
  cleanup();const gen=generation;let backoff=1000;
  function connectBridge(){
    if(gen!==generation)return;
    const ws=new WebSocket('ws://127.0.0.1:19285/browserlink');socket=ws;let lastPong=Date.now();
    helloTimer=setTimeout(()=>{if(!ready&&socket===ws)ws.close();},5000);
    ws.onopen=()=>{if(gen===generation&&socket===ws)ws.send(JSON.stringify({type:'hello',protocol:2}));};
    ws.onmessage=e=>{
      if(gen!==generation||socket!==ws)return;
      try{const data=JSON.parse(e.data);
        if(data.type==='ready'){
          clearTimeout(helloTimer);clearInterval(heartbeat);ready=true;backoff=1000;
          if(metadata)ws.send(JSON.stringify(metadata));state(true,!!data.jpeg);
          heartbeat=setInterval(()=>{if(socket!==ws)return;if(Date.now()-lastPong>35000){ws.close(4000,'Heartbeat timeout');return;}if(ws.readyState===WebSocket.OPEN)ws.send(JSON.stringify({type:'ping'}));},10000);
        }else if(data.type==='pong'){lastPong=Date.now();}
        else if(data.type==='audio-sink'&&ready){if(typeof audioRoute==='function')audioRoute(!!data.enabled);}
        else if(data.type!=='pong'&&ready)chrome.runtime.sendMessage({target:'worker',type:'input',event:data}).catch(()=>{});
      }catch{}
    };
    ws.onerror=()=>{};
    ws.onclose=e=>{
      if(gen!==generation||socket!==ws)return;
      clearTimeout(helloTimer);clearInterval(heartbeat);clearTimeout(frameTimer);frameTimer=null;pendingFrame=null;frameVersion++;ready=false;socket=null;
      if(typeof audioRoute==='function')audioRoute(false);
      if(e.code===1000||e.code===1008){ended(e.reason||'连接已停止');return;}
      chrome.runtime.sendMessage({target:'worker',type:'connection-warning',message:'连接中断 ('+e.code+')，正在自动重连'}).catch(()=>{});
      state(false);retryTimer=setTimeout(connectBridge,backoff);backoff=Math.min(10000,backoff*2);
    };
  }
  connectBridge();
}
chrome.runtime.onMessage.addListener((msg,sender,reply)=>{
  if(msg.target!=='offscreen'||sender.id!==chrome.runtime.id)return;
  if(msg.type==='diagnostics'){reply({...frameDiagnostics(),streamStats:typeof videoStreamStats==='undefined'?null:videoStreamStats,phase:typeof videoPhase==='undefined'?'':videoPhase,buffered:socket?.bufferedAmount,ready,policy:typeof videoPolicy==='undefined'?null:videoPolicy,frames:typeof videoFrames==='undefined'?0:videoFrames,error:typeof videoError==='undefined'?'':videoError,video:(typeof tabVideo!=='undefined'&&!!tabVideo)||(typeof streamWorker!=='undefined'&&!!streamWorker),playback:typeof tabVideo==='undefined'||!tabVideo?null:{ready:tabVideo.readyState,paused:tabVideo.paused,time:tabVideo.currentTime,width:tabVideo.videoWidth,track:tabVideo.srcObject?.getVideoTracks()[0]?.readyState},enabled:typeof videoPolicy!=='undefined'&&videoPolicy.enabled});return;}
  if(msg.type==='audio-start'){
    if(msg.tabId!==metadata?.activeId){reply({ok:false,error:'标签页已切换'});return;}
    startAudio(msg.streamId,msg.tabId,msg.geometry).then(()=>reply({ok:true}),e=>reply({ok:false,error:e.message}));return true;
  }
  if(msg.type==='audio-stop'){
    stopAudio().then(()=>reply({ok:true}),e=>reply({ok:false,error:e.message}));return true;
  }
  if(msg.type==='snapshot'){
    resizeSnapshot(msg).then(()=>reply({ok:true}),e=>reply({ok:false,error:e.message}));return true;
  }
  try{
    if(msg.type==='video-policy'&&typeof videoPolicy!=='undefined'){videoPolicy={enabled:!!msg.enabled,height:msg.height,fps:msg.fps,format:msg.format};if(typeof updateVideoFlow==='function')updateVideoFlow();}
    if(msg.type==='start')start();
    if(msg.type==='stop')cleanup();
    if(msg.type==='metadata'){
      if(metadata?.activeId!==msg.state.activeId){lastSentAt=0;lastSentSource='';sendWaitMs=0;}
      metadata=msg.state;if(ready&&socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify(metadata));
    }
    if(msg.type==='clipboard'&&ready&&socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'clipboard',text:msg.text}));
    if(msg.type==='report'&&ready&&socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'status',message:msg.message}));
    if(msg.type==='frame'&&ready&&socket?.readyState===WebSocket.OPEN&&msg.tabId===metadata?.activeId&&typeof msg.data==='string'&&msg.data.length<=22369624){
      frameVersion++;
      pendingFrame={...msg,source:'cdp',queuedAt:Date.now()};if(frameTimer===null)flushFrame();
    }
    reply({ok:true});
  }catch(e){reply({ok:false,error:e.message});}
});
