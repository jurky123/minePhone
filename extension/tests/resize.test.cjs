const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
async function harness(){
 const sent=[],sizes=[];let finish;
 class Canvas{constructor(w,h){this.width=w;this.height=h;sizes.push([w,h]);}getContext(){return {drawImage(){}};}convertToBlob(){return new Promise(r=>finish=()=>r(new Blob(['png'])));}}
 const ctx=vm.createContext({console,Blob,atob,OffscreenCanvas:Canvas,createImageBitmap:async()=>({close(){}}),setTimeout:()=>0,clearTimeout(){},clearInterval(){},WebSocket:{OPEN:1},chrome:{runtime:{id:'test',onMessage:{addListener(){}}}}});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../offscreen.js'),'utf8'),ctx);
 ctx.sent=sent;vm.runInContext('ready=true;metadata={activeId:42};socket={readyState:1,bufferedAmount:0,send:b=>sent.push(b)}',ctx);
 const png=Buffer.alloc(33);png.writeUInt32BE(3840,16);png.writeUInt32BE(2160,20);ctx.msg={tabId:42,data:png.toString('base64'),maxWidth:1280,maxHeight:720};
 return {ctx,sent,sizes,finish:()=>finish()};
}
test('fallback pixels are resized to selected limit without changing webpage layout',async()=>{
 const h=await harness(),pending=vm.runInContext('resizeSnapshot(msg)',h.ctx);await new Promise(setImmediate);assert.deepEqual(h.sizes,[[1280,720]]);h.finish();await pending;assert.equal(h.sent.length,1);
});
test('resizing result is discarded if live frame or session changes while encoding',async()=>{
 for(const change of ['frameVersion++','generation++','metadata={activeId:43}']){
  const h=await harness(),pending=vm.runInContext('resizeSnapshot(msg)',h.ctx);await new Promise(setImmediate);vm.runInContext(change,h.ctx);h.finish();await pending;assert.equal(h.sent.length,0);
 }
});
