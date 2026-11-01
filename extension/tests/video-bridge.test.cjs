const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
async function harness(){
 const workers=[],constraints=[];
 class Worker{constructor(){workers.push(this);this.messages=[];}postMessage(m){this.messages.push(m);}terminate(){this.stopped=true;}}
 const track={applyConstraints:async c=>constraints.push(c),addEventListener(){}};
 const ctx=vm.createContext({Worker,MediaStreamTrackProcessor:class{constructor(){this.readable={};}},Blob,Date,performance,clearTimeout(){},ready:true,metadata:{activeId:42},socket:{readyState:1,bufferedAmount:0},WebSocket:{OPEN:1},frameVersion:0,frameTimer:null,pendingFrame:null,flushFrame(){},chrome:{runtime:{sendMessage:async()=>{}}}});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../video.js'),'utf8'),ctx);
 ctx.stream={getVideoTracks:()=>[track]};await vm.runInContext("videoPolicy={enabled:true,height:720,fps:30,format:'jpeg'};startVideo(stream,42)",ctx);
 return {ctx,worker:workers[0],clock:workers[1],constraints};
}
test('direct stream updates capture limits only when quality changes',async()=>{
 const h=await harness();assert.equal(h.constraints.length,1);assert.equal(h.constraints[0].height.exact,720);
 vm.runInContext('updateVideoFlow();videoPolicy.height=1440;updateVideoFlow()',h.ctx);assert.equal(h.constraints.length,2);assert.equal(h.constraints[1].height.exact,1440);
});

test('capture geometry preserves a wide browser viewport instead of padding to 16:9',async()=>{
 const h=await harness();
 vm.runInContext('captureGeometryValue={width:2400,height:1000};updateVideoFlow()',h.ctx);
 const c=h.constraints.at(-1);assert.equal(c.width.exact,1280);assert.equal(c.height.exact,533);
 vm.runInContext('captureGeometryValue={width:800,height:1200};updateVideoFlow()',h.ctx);
 assert.equal(h.constraints.at(-1).width.exact,480);assert.equal(h.constraints.at(-1).height.exact,720);
});
test('reconnect and tab changes reject frames from old stream epochs',async()=>{
 const h=await harness(),first=h.worker.messages[0].epoch;
 const send=epoch=>h.worker.onmessage({data:{epoch,blob:new Blob(['image'])}});
 send(first);assert.equal(vm.runInContext('videoFrames',h.ctx),1);
 vm.runInContext('socket={readyState:1,bufferedAmount:0};updateVideoFlow()',h.ctx);send(first);assert.equal(vm.runInContext('videoFrames',h.ctx),1);
 send(h.worker.messages.at(-1).epoch);assert.equal(vm.runInContext('videoFrames',h.ctx),2);
 vm.runInContext('metadata.activeId=43',h.ctx);send(h.worker.messages.at(-1).epoch);assert.equal(vm.runInContext('videoFrames',h.ctx),2);
});
test('worker failure terminates capture and exposes its error for fallback',async()=>{
 const h=await harness();h.worker.onmessage({data:{error:'encoder failed'}});assert.ok(h.worker.stopped);assert.equal(vm.runInContext('streamWorker',h.ctx),null);assert.equal(vm.runInContext('videoError',h.ctx),'encoder failed');
});

test('socket congestion resumes on worker ticks without waiting for a page timer',async()=>{
 const h=await harness();
 vm.runInContext('socket.bufferedAmount=600000;updateVideoFlow()',h.ctx);
 assert.equal(h.worker.messages.at(-1).credit,false);assert.equal(h.clock.messages.at(-1),8);
 vm.runInContext('socket.bufferedAmount=0',h.ctx);h.clock.onmessage();
 assert.equal(h.worker.messages.at(-1).credit,true);assert.equal(h.clock.messages.at(-1),null);
});

test('stopped capture terminates congestion clock and rejects its late ticks',async()=>{
 const h=await harness();vm.runInContext('stopVideo()',h.ctx);
 assert.ok(h.clock.stopped);const count=h.worker.messages.length;h.clock.onmessage();
 assert.equal(h.worker.messages.length,count);
});
