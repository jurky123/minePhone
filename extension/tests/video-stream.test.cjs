const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
async function run({enabled=true,credit=true,ack=true,fail=false,count=3,jitter=false}={}){
 let now=0,index=0,closed=0,released=false;const messages=[],encodes=[];
 const frames=Array.from({length:count},()=>({displayWidth:1920,displayHeight:1080,close(){closed++;}}));
 const policy={type:'policy',enabled,credit,height:720,fps:30,format:'jpeg',epoch:7};
 class Canvas{constructor(w,h){this.width=w;this.height=h;}getContext(){return {drawImage(){if(fail)throw Error('draw failed');}};}async convertToBlob(o){encodes.push({w:this.width,h:this.height,...o});return new Blob(['pixels'],{type:o.type});}}
 const self={postMessage(m){messages.push(m);if(ack&&!m.error)self.onmessage({data:policy});}};
 const ctx=vm.createContext({self,OffscreenCanvas:Canvas,performance:{now:()=>now},console});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../video-stream.js'),'utf8'),ctx);
 self.onmessage({data:policy});ctx.readable={getReader:()=>({async read(){now+=jitter?(index%2===0?31:35.6666667):40;return index<frames.length?{value:frames[index++],done:false}:{done:true};},releaseLock(){released=true;}})};
 await vm.runInContext('consume(readable)',ctx);return {messages,encodes,closed,released};
}
test('direct frame stream scales and encodes with bounded per-frame credit',async()=>{const r=await run();assert.equal(r.encodes.length,3);assert.equal(r.encodes[0].w,1280);assert.equal(r.encodes[0].h,720);assert.equal(r.encodes[0].type,'image/jpeg');assert.equal(r.closed,3);assert.ok(r.released);assert.ok(r.messages.every(m=>m.epoch===7));});
test('no display demand or socket credit means no image encoding',async()=>{for(const opts of [{enabled:false},{credit:false}]){const r=await run(opts);assert.equal(r.encodes.length,0);assert.equal(r.closed,3);}});
test('unacknowledged frame cannot build an encoding backlog',async()=>{const r=await run({ack:false});assert.equal(r.encodes.length,1);assert.equal(r.closed,3);});
test('encoding failure releases the native frame and reader',async()=>{const r=await run({fail:true});assert.equal(r.closed,1);assert.ok(r.released);assert.match(r.messages[0].error,/draw failed/);});

test('capture jitter does not halve a nominal 30 FPS stream',async()=>{const r=await run({count:90,jitter:true});assert.ok(r.encodes.length>=88);assert.equal(r.closed,90);});
