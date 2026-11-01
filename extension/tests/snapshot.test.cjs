const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
async function harness(){
 const calls=[],messages=[];let release;
 const ctx=vm.createContext({console,URL,setTimeout:()=>0,clearTimeout:()=>{},chrome:{
  storage:{session:{get:async()=>({}),set:async()=>{}}},runtime:{id:'test',onMessage:{addListener(){}},sendMessage:async m=>messages.push(m)},
  debugger:{onEvent:{addListener(){}},onDetach:{addListener(){}},sendCommand:async(t,method,args)=>{
   calls.push({method,args});if(method==='Page.getLayoutMetrics')return {cssVisualViewport:{pageX:0,pageY:120,clientWidth:2560,clientHeight:1440}};
   if(method==='Runtime.evaluate')return {result:{value:2}};
   if(method==='Page.captureScreenshot')return new Promise(r=>release=r);
   return {};
  }},tabs:{onRemoved:{addListener(){}},onUpdated:{addListener(){}},onCreated:{addListener(){}}}
 }});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../background.js'),'utf8'),ctx);await vm.runInContext('restored',ctx);vm.runInContext('tabId=42;bridgeReady=true',ctx);
 return {ctx,calls,messages,release:()=>release({data:'png'})};
}
test('fallback captures the visible viewport without changing layout and requests bounded pixel resizing',async()=>{
 const h=await harness();const pending=vm.runInContext('snapshot()',h.ctx);await new Promise(setImmediate);
 const c=h.calls.find(c=>c.method==='Page.captureScreenshot');assert.equal(c.args.clip,undefined);assert.equal(c.args.captureBeyondViewport,false);
 h.release();await pending;assert.equal(h.messages[0].tabId,42);assert.equal(h.messages[0].data,'png');assert.equal(h.messages[0].maxWidth,2560);assert.equal(h.messages[0].maxHeight,1440);
});
test('late screenshot cannot overwrite a newly selected tab',async()=>{
 const h=await harness();const pending=vm.runInContext('snapshot()',h.ctx);await new Promise(setImmediate);vm.runInContext('tabId=43;generation++',h.ctx);h.release();await pending;assert.equal(h.messages.length,0);
});
test('late screenshot cannot overwrite a newer streaming frame',async()=>{
 const h=await harness();const pending=vm.runInContext('snapshot()',h.ctx);await new Promise(setImmediate);vm.runInContext('lastLiveFrame++',h.ctx);h.release();await pending;assert.equal(h.messages.length,0);
});
