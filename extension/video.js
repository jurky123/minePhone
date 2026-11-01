// Process capture frames in a bounded worker; fall back to a worker-paced video
// element on older Chrome versions. Only binary images cross the socket.
let videoPhase='idle',videoError='',videoFrames=0,tabVideo=null,videoTimer=null,videoClock=null,videoGeneration=0,videoPolicy={enabled:true,height:1440,fps:30,format:'png'};
let videoStreamStats=[],streamWorker=null,streamTab=null,streamConnection=null,streamEpoch=0,captureTrack=null,captureHeight='',captureGeometryValue=null,videoRetry=null;
let lastVideoFrameAt=0,workerDispatchMs=0;
function captureSize(geometry,height){
  const w=geometry?.width||2560,h=geometry?.height||1440;
  const scale=Math.min(1,height/h,Math.round(height*16/9)/w);
  return {width:Math.max(2,Math.round(w*scale)),height:Math.max(2,Math.round(h*scale))};
}
function noticeVideo(id,gen){
  void chrome.runtime.sendMessage({target:'worker',type:'video-alive',tabId:id}).then(r=>{
    if(gen!==videoGeneration||!r?.geometry)return;
    captureGeometryValue=r.geometry;updateVideoFlow();
  }).catch(()=>{});
}
function updateVideoFlow(){
  clearTimeout(videoRetry);videoRetry=null;
  const captureFps=Math.max(5,Math.min(30,videoPolicy.fps));
  const size=captureSize(captureGeometryValue,videoPolicy.height),key=size.width+'x'+size.height+'@'+captureFps;
  if(captureTrack&&captureHeight!==key){
    captureHeight=key;
    const track=captureTrack;
    void track.applyConstraints({width:{exact:size.width},height:{exact:size.height},frameRate:{max:captureFps}}).catch(e=>{if(track===captureTrack)videoError=e.message;});
  }
  if(!streamWorker)return;
  if(streamConnection!==socket){streamConnection=socket;streamEpoch++;}
  streamWorker.postMessage({type:'policy',...videoPolicy,epoch:streamEpoch,enabled:ready&&videoPolicy.enabled,credit:ready&&socket?.readyState===WebSocket.OPEN&&socket.bufferedAmount<512*1024});
  if(ready&&videoPolicy.enabled&&socket?.readyState===WebSocket.OPEN&&socket.bufferedAmount>=512*1024){
    if(videoClock)videoClock.postMessage(8);else videoRetry=setTimeout(updateVideoFlow,8);
  }else videoClock?.postMessage(null);
}
function stopVideo(){
  videoGeneration++;clearTimeout(videoTimer);videoTimer=null;
  clearTimeout(videoRetry);videoRetry=null;
  videoClock?.terminate();videoClock=null;
  streamWorker?.terminate();streamWorker=null;streamTab=null;
  captureTrack=null;captureHeight=0;
  lastVideoFrameAt=0;
  if(tabVideo){tabVideo.pause();tabVideo.srcObject=null;tabVideo.remove();tabVideo=null;}
}
async function startVideo(stream,id,geometry){
  stopVideo();videoError='';videoFrames=0;const gen=videoGeneration,track=stream.getVideoTracks()[0];if(!track)return;
  captureTrack=typeof track.applyConstraints==='function'?track:null;captureGeometryValue=geometry;videoStreamStats=[];
  if(typeof MediaStreamTrackProcessor!=='undefined'&&typeof Worker!=='undefined'){
    const processor=new MediaStreamTrackProcessor({track,maxBufferSize:1}),worker=new Worker('video-stream.js');
    streamWorker=worker;streamTab=id;let lastNotice=0;
    videoClock=new Worker('video-clock.js');videoClock.onmessage=()=>{if(gen===videoGeneration)updateVideoFlow();};
    worker.onmessage=({data})=>{
      if(gen!==videoGeneration||streamWorker!==worker||metadata?.activeId!==id)return;
      if(data.stats){videoStreamStats=[data.stats];if(ready&&socket?.readyState===WebSocket.OPEN&&socket.bufferedAmount<512*1024)socket.send(JSON.stringify({type:'capture-stats',...data.stats,workerDispatchMs,buffered:socket.bufferedAmount}));return;}
      if(data.error){videoError=data.error;stopVideo();return;}
      if(data.blob&&data.epoch===streamEpoch&&streamConnection===socket&&ready&&videoPolicy.enabled&&socket?.readyState===WebSocket.OPEN&&socket.bufferedAmount<512*1024&&data.blob.size<=16*1024*1024){
        lastVideoFrameAt=Date.now();workerDispatchMs=Number.isFinite(data.producedAt)?Math.max(0,lastVideoFrameAt-data.producedAt):0;
        videoPhase='stream';videoFrames++;frameVersion++;pendingFrame={tabId:id,blob:data.blob,source:'tabCapture',queuedAt:Date.now()};if(frameTimer===null)flushFrame();
        if(Date.now()-lastNotice>1000){lastNotice=Date.now();noticeVideo(id,gen);}
      }
      updateVideoFlow();
    };
    worker.onerror=()=>{videoError='视频处理线程异常，请重新连接';stopVideo();};
    updateVideoFlow();worker.postMessage({type:'start',readable:processor.readable},[processor.readable]);
    track.addEventListener('ended',()=>{if(gen===videoGeneration)stopVideo();},{once:true});
    return;
  }
  const video=document.createElement('video');tabVideo=video;video.muted=true;video.playsInline=true;video.srcObject=stream;
  document.body.append(video);await video.play();
  if(gen!==videoGeneration||tabVideo!==video)return;
  const canvas=document.createElement('canvas'),context=canvas.getContext('2d',{alpha:false,willReadFrequently:true});let lastNotice=0;
  const current=()=>gen===videoGeneration&&tabVideo===video&&metadata?.activeId===id;
  // A hidden document's window timers can be aligned to one second. Keep the
  // bounded capture clock in a dedicated worker; still encode only one frame at a time.
  const clock=typeof Worker==='undefined'?null:new Worker('video-clock.js');videoClock=clock;
  if(clock){
    clock.onmessage=()=>{if(current())void frame();};
    clock.onerror=()=>{videoError='视频调度线程异常，请重新连接';stopVideo();};
  }
  async function frame(){
    const began=performance.now();videoPhase='checking';
    try{
      if(!current())return;
      if(ready&&videoPolicy.enabled&&video.readyState>=2&&video.videoWidth&&socket?.readyState===WebSocket.OPEN&&socket.bufferedAmount<512*1024){
        const scale=Math.min(1,videoPolicy.height/video.videoHeight,Math.round(videoPolicy.height*16/9)/video.videoWidth);
        const w=Math.max(1,Math.floor(video.videoWidth*scale)),h=Math.max(1,Math.floor(video.videoHeight*scale));
        if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
        context.drawImage(video,0,0,w,h);
        const connection=socket;videoPhase='encoding';
        const format=videoPolicy.format==='jpeg'?'image/jpeg':'image/png';
        const data=canvas.toDataURL(format,.82),binary=atob(data.slice(data.indexOf(',')+1)),bytes=new Uint8Array(binary.length);
        for(let i=0;i<binary.length;i++)bytes[i]=binary.charCodeAt(i);
        const blob=new Blob([bytes],{type:format});
        if(!current()||connection!==socket||!ready||!videoPolicy.enabled||blob.size>16*1024*1024)return;
        lastVideoFrameAt=Date.now();videoPhase='encoded';videoFrames++;frameVersion++;pendingFrame={tabId:id,blob,source:'tabCapture',queuedAt:Date.now()};if(frameTimer===null)flushFrame();
        // Status delivery must never hold the next video frame hostage to CDP.
        if(Date.now()-lastNotice>1000){lastNotice=Date.now();noticeVideo(id,gen);}
      }
    }catch(e){videoError=e.message;}
    finally{if(current()){
      const delay=videoPolicy.enabled?Math.max(1,1000/videoPolicy.fps-(performance.now()-began)):250;
      if(clock)clock.postMessage(delay);else videoTimer=setTimeout(frame,delay);
    }}
  }
  track.addEventListener('ended',()=>{if(gen===videoGeneration)stopVideo();},{once:true});
  void frame();
}
