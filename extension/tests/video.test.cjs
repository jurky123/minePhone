const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
async function harness(options={}){
 const calls=[],timers=[],clocks=[];let listener;
 const video={play:async()=>{},pause(){},remove(){},readyState:2,videoWidth:1920,videoHeight:1080};
 const canvas={width:1,height:1,getContext:()=>({drawImage(){}}),toDataURL(type,quality){calls.push({type,quality,w:canvas.width,h:canvas.height});options.onEncode?.(ctx);return 'data:'+type+';base64,AQID';}};
 class Clock{constructor(url){this.url=url;this.delays=[];clocks.push(this);}postMessage(delay){this.delays.push(delay);}terminate(){this.stopped=true;}}
 const ctx=vm.createContext({console,performance,Date,Blob,atob,Uint8Array,Worker:options.fallback?undefined:Clock,document:{body:{append(){}},createElement:kind=>kind==='video'?video:canvas},setTimeout:(fn,delay)=>{timers.push({fn,delay});return timers.length;},clearTimeout(){},ready:true,metadata:{activeId:42},socket:{readyState:1,bufferedAmount:0},WebSocket:{OPEN:1},frameVersion:0,frameTimer:null,pendingFrame:null,flushFrame(){calls.push('flush');},chrome:{runtime:{sendMessage:options.pendingNotice?()=>new Promise(()=>{}):async m=>calls.push(m)}}});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../video.js'),'utf8'),ctx);
 ctx.stream={getVideoTracks:()=>[{addEventListener(name,fn){listener=fn;}}]};
 await vm.runInContext("videoPolicy={enabled:true,height:720,fps:30,format:'jpeg'};startVideo(stream,42)",ctx);
 return {ctx,calls,timers,clocks,video,end:()=>listener()};
}
test('video scales to limit and sends binary JPEG without a background worker base64 hop',async()=>{
 const h=await harness();assert.deepEqual(h.calls[0],{type:'image/jpeg',quality:.82,w:1280,h:720});
 const frame=vm.runInContext('pendingFrame',h.ctx);assert.equal(frame.tabId,42);assert.equal(frame.blob.type,'image/jpeg');assert.deepEqual([...new Uint8Array(await frame.blob.arrayBuffer())],[1,2,3]);
});
test('bounded worker clock paces frames and stops with the capture',async()=>{
 const h=await harness();assert.equal(h.clocks[0].url,'video-clock.js');assert.equal(h.clocks[0].delays.length,1);assert.equal(h.timers.length,0);
 h.clocks[0].onmessage();assert.equal(h.calls.filter(x=>x==='flush').length,2);
 vm.runInContext('stopVideo()',h.ctx);assert.ok(h.clocks[0].stopped);h.clocks[0].onmessage();assert.equal(h.calls.filter(x=>x==='flush').length,2);
});
test('paused and congested sessions skip encoding; pause uses a slow clock',async()=>{
 for(const change of ['videoPolicy.enabled=false','socket.bufferedAmount=600000']){
  const h=await harness();vm.runInContext(change,h.ctx);h.clocks[0].onmessage();assert.equal(h.calls.filter(x=>x==='flush').length,1);
  if(change.startsWith('videoPolicy'))assert.equal(h.clocks[0].delays.at(-1),250);
 }
});
test('stale tab or socket cannot receive an encoded image',async()=>{
 for(const change of ['metadata.activeId=43','socket={readyState:1,bufferedAmount:0}','stopVideo()']){
  const h=await harness({onEncode:ctx=>vm.runInContext(change,ctx)});assert.equal(h.calls.filter(x=>x==='flush').length,0);
 }
});
test('worker notice acknowledgement cannot hold the video loop',async()=>{
 const h=await harness({pendingNotice:true});assert.equal(h.clocks[0].delays.length,1);h.clocks[0].onmessage();assert.equal(h.calls.filter(x=>x==='flush').length,2);
});
test('PNG mode is retained and no upscaling is introduced',async()=>{
 const h=await harness();h.video.videoWidth=640;h.video.videoHeight=360;vm.runInContext("videoPolicy.format='png'",h.ctx);h.clocks[0].onmessage();
 const last=h.calls.filter(x=>x.type==='image/png').at(-1);assert.equal(last.w,640);assert.equal(last.h,360);
});
test('track ending terminates the worker',async()=>{const h=await harness();h.end();assert.ok(h.clocks[0].stopped);});
test('window timer fallback remains bounded when workers are unavailable',async()=>{const h=await harness({fallback:true});assert.equal(h.clocks.length,0);assert.equal(h.timers.length,1);assert.ok(h.timers[0].delay>=1);});
