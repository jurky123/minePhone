const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
test('rate limiting sends the final frame and never replays a frame after switching tabs',async()=>{
 let event,serial=0;const timers=new Map(),frames=[];
 const ctx=vm.createContext({console,URL,setTimeout:fn=>{timers.set(++serial,fn);return serial;},clearTimeout:id=>timers.delete(id),chrome:{
  storage:{session:{get:async()=>({}),set:async()=>{}}},runtime:{id:'test',onMessage:{addListener(){}},sendMessage:async m=>frames.push(m)},
  debugger:{onEvent:{addListener:fn=>event=fn},onDetach:{addListener(){}},sendCommand:async()=>({})},
  tabs:{onRemoved:{addListener(){}},onUpdated:{addListener(){}},onCreated:{addListener(){}}}
 }});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../background.js'),'utf8'),ctx);await vm.runInContext('restored',ctx);
 vm.runInContext('tabId=42;bridgeReady=true;lastFrame=Date.now()',ctx);
 for(const data of ['first','last'])await event({tabId:42},'Page.screencastFrame',{sessionId:1,data});
 assert.equal(timers.size,1);await [...timers.values()][0]();assert.equal(frames.at(-1).data,'last');
 await event({tabId:42},'Page.screencastFrame',{sessionId:2,data:'stale'});vm.runInContext('tabId=43',ctx);
 await [...timers.values()].at(-1)();assert.equal(frames.length,1);
});
