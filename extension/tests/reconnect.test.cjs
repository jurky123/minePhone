const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
function harness(){
  let listener,serial=0,stopped=0,now=0;
  const timers=new Map(),sockets=[],messages=[];
  const track={stop(){stopped++;}};
  const stream={getTracks:()=>[track],getVideoTracks:()=>[track]};
  const video={play:async()=>{},videoWidth:0,videoHeight:0};
  class Socket {
    static OPEN=1;
    constructor(){this.readyState=0;this.bufferedAmount=0;this.sent=[];sockets.push(this);}
    send(data){this.sent.push(typeof data==='string'?JSON.parse(data):data);}
    close(){this.readyState=3;}
    open(){this.readyState=1;this.onopen();}
    message(data){this.onmessage({data:JSON.stringify(data)});}
    closed(code){this.readyState=3;this.onclose?.({code,reason:''});}
  }
  const context=vm.createContext({console,Blob,atob,WebSocket:Socket,Date:{now:()=>now},
    setTimeout:(fn,delay)=>{const id=++serial;timers.set(id,{fn,delay});return id;},clearTimeout:id=>timers.delete(id),
    setInterval:(fn,delay)=>{const id=++serial;timers.set(id,{fn,delay});return id;},clearInterval:id=>timers.delete(id),
    document:{getElementById:id=>id==='video'?video:{getContext:()=>({})}},
    navigator:{mediaDevices:{getUserMedia:async()=>stream}},
    chrome:{runtime:{id:'test',onMessage:{addListener:fn=>listener=fn},sendMessage:async msg=>{messages.push(msg);return {};}}}});
  vm.runInContext(fs.readFileSync(path.join(__dirname,'../offscreen.js'),'utf8'),context);
  const send=msg=>new Promise(resolve=>listener({target:'offscreen',...msg},{id:'test'},resolve));
  return {send,sockets,messages,timers,stopped:()=>stopped,advance:ms=>now+=ms,
    retry(){const entry=[...timers].find(([,v])=>v.delay===1000);assert.ok(entry,'retry scheduled');timers.delete(entry[0]);entry[1].fn();}};
}
test('world exit retains chosen session, reconnects and repeats protocol handshake without a code',async()=>{
  const h=harness();assert.equal((await h.send({type:'start',streamId:'chosen',fps:15,viewport:{clientWidth:1280,clientHeight:720}})).ok,true);
  const a=h.sockets[0];a.open();assert.deepEqual(a.sent[0],{type:'hello',protocol:2});a.message({type:'ready'});
  a.closed(1013);assert.equal(h.stopped(),0);h.retry();
  const b=h.sockets[1];b.open();b.message({type:'ready'});
  assert.equal(h.messages.filter(m=>m.type==='bridge-state'&&m.connected).length,2);
  a.message({type:'key',key:'Enter'});assert.equal(h.messages.filter(m=>m.type==='input').length,0);
});
test('manual game disconnect ends the session and does not retry',async()=>{
  const h=harness();await h.send({type:'start',fps:15,viewport:{}});
  h.sockets[0].closed(1000);assert.equal(h.timers.size,0);
  assert.ok(h.messages.some(m=>m.type==='capture-ended'));
});
test('extension stop cancels a pending reconnect and ignores late ready events',async()=>{
  const h=harness();await h.send({type:'start',fps:15,viewport:{}});
  const a=h.sockets[0];a.closed(1006);await h.send({type:'stop'});
  assert.equal(h.timers.size,0);
  a.message({type:'ready'});assert.equal(h.messages.filter(m=>m.connected).length,0);
});

test('only frames for the active metadata tab are forwarded, and stop drops subsequent frames',async()=>{
 const h=harness();await h.send({type:'start'});const ws=h.sockets[0];ws.open();ws.message({type:'ready'});
 await h.send({type:'metadata',state:{type:'browser-state',activeId:42,tabs:[]}});
 await h.send({type:'frame',tabId:99,data:'YQ=='});assert.equal(ws.sent.filter(v=>v instanceof Blob).length,0);
 await h.send({type:'frame',tabId:42,data:'YQ=='});assert.equal(ws.sent.filter(v=>v instanceof Blob).length,1);
 await h.send({type:'stop'});await h.send({type:'frame',tabId:42,data:'YQ=='});assert.equal(ws.sent.filter(v=>v instanceof Blob).length,1);
});

test('backpressure retains latest frame instead of dropping the final screen update',async()=>{
 const h=harness();await h.send({type:'start'});const ws=h.sockets[0];ws.open();ws.message({type:'ready'});
 await h.send({type:'metadata',state:{type:'browser-state',activeId:42,tabs:[]}});ws.bufferedAmount=600000;
 await h.send({type:'frame',tabId:42,data:'YQ=='});await h.send({type:'frame',tabId:42,data:'Yg=='});
 assert.equal(ws.sent.filter(v=>v instanceof Blob).length,0);ws.bufferedAmount=0;
 [...h.timers.values()].find(t=>t.delay===16).fn();
 assert.equal(await ws.sent.filter(v=>v instanceof Blob)[0].text(),'b');
});
test('late close from an old socket cannot cancel the new heartbeat',async()=>{
 const h=harness();await h.send({type:'start'});const a=h.sockets[0];a.open();a.message({type:'ready',jpeg:true});
 a.closed(1013);h.retry();const b=h.sockets[1];b.open();b.message({type:'ready',jpeg:true});
 a.closed(1006);
 assert.ok([...h.timers.values()].some(t=>t.delay===10000));
 assert.ok(h.messages.some(m=>m.type==='bridge-state'&&m.jpeg===true));
});

test('silent connection times out, while received pongs renew the heartbeat',async()=>{
 const h=harness();await h.send({type:'start'});const ws=h.sockets[0];ws.open();ws.message({type:'ready'});
 const tick=[...h.timers.values()].find(t=>t.delay===10000).fn;
 h.advance(30000);tick();assert.equal(ws.readyState,1);assert.equal(ws.sent.at(-1).type,'ping');
 ws.message({type:'pong'});h.advance(30000);tick();assert.equal(ws.readyState,1);
 h.advance(10000);tick();assert.equal(ws.readyState,3);
 ws.closed(4000);h.retry();assert.equal(h.sockets.length,2);
});
