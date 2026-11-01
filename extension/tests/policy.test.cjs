const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
async function harness(){
 const calls=[],timers=new Map();let next=1;
 const ctx=vm.createContext({console,URL,setTimeout:(fn,ms)=>{const id=next++;timers.set(id,{fn,ms});return id;},clearTimeout:id=>timers.delete(id),chrome:{
 storage:{session:{get:async()=>({}),set:async()=>{}}},runtime:{id:'test',onMessage:{addListener(){}},sendMessage:async m=>{calls.push(m);return {}; }},
 debugger:{onEvent:{addListener(){}},onDetach:{addListener(){}},sendCommand:async(t,method,args)=>{calls.push({method,args});return {data:'png'};}},
 tabs:{onRemoved:{addListener(){}},onUpdated:{addListener(){}},onCreated:{addListener(){}}}
 }});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../background.js'),'utf8'),ctx);await vm.runInContext('restored',ctx);vm.runInContext('tabId=42;bridgeReady=true',ctx);
 return {ctx,calls,timers,run:s=>vm.runInContext(s,ctx)};
}
test('hidden displays stop pictures without stopping audio, and wake captures a first frame',async()=>{
 const h=await harness();await h.run('streamPolicy({enabled:false,maxHeight:720,maxFps:15})');await h.run('snapshot();cast()');
 assert.deepEqual(h.calls.filter(c=>c.method).map(c=>c.method),['Page.stopScreencast']);
 await h.run('streamPolicy({enabled:true,maxHeight:720,maxFps:15})');await new Promise(setImmediate);
 assert.equal(h.calls.find(c=>c.method==='Page.startScreencast').args.maxHeight,720);
 assert.ok(h.calls.some(c=>c.type==='snapshot'));assert.ok(!h.calls.some(c=>c.type==='audio-stop'));
});
test('adaptive profile obeys manual quality ceiling and unchanged heartbeats do not restart capture',async()=>{
 const h=await harness();h.run('quality=720');await h.run('streamPolicy({enabled:true,maxHeight:1080,maxFps:30})');
 assert.equal(h.calls.find(c=>c.method==='Page.startScreencast').args.maxHeight,720);
 const before=h.calls.filter(c=>c.method==='Page.startScreencast').length;
 await h.run('streamPolicy({enabled:true,maxHeight:1080,maxFps:30})');
 assert.equal(h.calls.filter(c=>c.method==='Page.startScreencast').length,before);
 assert.equal([...h.timers.values()].filter(t=>t.ms===5000).length,1);
});
test('missing demand heartbeat expires to paused; malformed profiles cannot change state',async()=>{
 const h=await harness();await h.run('streamPolicy({enabled:true,maxHeight:720,maxFps:15})');
 [...h.timers.values()].find(t=>t.ms===5000).fn();await new Promise(setImmediate);
 assert.equal(h.run('streamEnabled'),false);
 await h.run('streamPolicy({enabled:true,maxHeight:99999,maxFps:120})');assert.equal(h.run('streamEnabled'),false);
});
