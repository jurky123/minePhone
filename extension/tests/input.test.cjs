const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
async function harness(geometry=null){
  const calls=[];
  const context=vm.createContext({console,setTimeout,clearTimeout,chrome:{
    storage:{session:{get:async()=>({}),set:async()=>{}}},
    runtime:{id:'test',onMessage:{addListener(){}},sendMessage:async()=>({ok:true})},
    debugger:{onEvent:{addListener(){}},onDetach:{addListener(){}},sendCommand:async(target,method,args)=>{
      calls.push({target,method,args});
      if(method==='Page.getLayoutMetrics')return {cssLayoutViewport:{clientWidth:1280,clientHeight:720}};
      if(method==='Page.getNavigationHistory')return {currentIndex:1,entries:[{id:10},{id:20},{id:30}]};
      if(method==='Runtime.evaluate')return {result:{value:args.expression==='({width:innerWidth,height:innerHeight})'&&geometry?geometry:'中文选中内容'}};
      return {};
    }},tabs:{onRemoved:{addListener(){}},onUpdated:{addListener(){}},onCreated:{addListener(){}}},action:{setBadgeText:async()=>{}}
  }});
  vm.runInContext(fs.readFileSync(path.join(__dirname,'../background.js'),'utf8'),context);
  await vm.runInContext('restored',context);
  vm.runInContext('tabId=42',context);
  return {calls,context,send:async input=>{context.input=input;await vm.runInContext('dispatch(input,42,generation)',context);}};
}
test('normalized page click maps to CSS viewport pixels and releases button',async()=>{
  const h=await harness();
  await h.send({type:'pointer',action:'down',x:.5,y:.25,button:0,modifiers:0});
  await h.send({type:'pointer',action:'up',x:.5,y:.25,button:0,modifiers:0});
  const events=h.calls.filter(x=>x.method==='Input.dispatchMouseEvent');
  assert.equal(events[0].args.x,640);assert.equal(events[0].args.y,180);
  assert.equal(events[0].args.buttons,1);assert.equal(events[1].args.buttons,0);
  assert.equal(events[0].target.tabId,42);
});

test('pointer mapping includes scrollbar width captured in the video',async()=>{
 const h=await harness({width:1300,height:740});
 await h.send({type:'pointer',action:'down',x:.99,y:.5,button:0});
 const e=h.calls.find(c=>c.method==='Input.dispatchMouseEvent');
 assert.equal(e.args.x,1287);assert.equal(e.args.y,370);
});

test('double click retains count on press and release',async()=>{
 const h=await harness();for(let i=0;i<2;i++)for(const action of ['down','up'])await h.send({type:'pointer',action,x:.3,y:.3,button:0});
 assert.deepEqual(h.calls.filter(c=>c.method==='Input.dispatchMouseEvent').map(c=>c.args.clickCount),[1,1,2,2]);
});
test('undo and redo send browser key codes and modifiers',async()=>{
 const h=await harness();await h.send({type:'key',key:'z',modifiers:2});await h.send({type:'key',key:'y',modifiers:2});
 assert.deepEqual(h.calls.filter(c=>c.args?.type==='rawKeyDown').map(c=>[c.args.code,c.args.windowsVirtualKeyCode,c.args.modifiers]),[['KeyZ',90,2],['KeyY',89,2]]);
});
test('cut copies the selection before deleting it',async()=>{
 const h=await harness();await h.send({type:'clipboard',cut:true});
 assert.equal(h.calls[0].method,'Runtime.evaluate');assert.equal(h.calls[1].args.key,'Backspace');
});
test('right click and wheel preserve explicit input types',async()=>{
  const h=await harness();await h.send({type:'pointer',action:'down',x:0,y:0,button:1});
  assert.equal(h.calls.at(-1).args.button,'right');assert.equal(h.calls.at(-1).args.buttons,2);
  await h.send({type:'pointer',action:'wheel',x:.4,y:.4,button:-1,delta:100});
  assert.equal(h.calls.at(-1).args.type,'mouseWheel');assert.equal(h.calls.at(-1).args.deltaY,100);
});
test('Unicode text stays intact and payload is bounded',async()=>{
  const h=await harness();await h.send({type:'text',text:'你好，Minecraft 😀'});
  assert.equal(h.calls.at(-1).args.text,'你好，Minecraft 😀');
  await h.send({type:'text',text:'x'.repeat(5000)});assert.equal(h.calls.at(-1).args.text.length,4096);
});
test('back uses browser navigation history, not arbitrary script evaluation',async()=>{
  const h=await harness();await h.send({type:'command',action:'back'});
  assert.equal(h.calls.at(-1).method,'Page.navigateToHistoryEntry');assert.equal(h.calls.at(-1).args.entryId,10);
});
test('unknown keys and invalid coordinates do not dispatch',async()=>{
  const h=await harness();await h.send({type:'key',key:'arbitrary'});
  await h.send({type:'pointer',action:'down',x:NaN,y:1});assert.equal(h.calls.length,0);
});
test('ended sessions cannot send input into a newly attached tab',async()=>{
  const h=await harness();vm.runInContext('tabId=99',h.context);
  await assert.rejects(h.send({type:'text',text:'no'}),/Session ended/);assert.equal(h.calls.length,0);
});
