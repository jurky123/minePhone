const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
async function harness(){
 const calls=[],tabs=[{id:42,windowId:7,url:'https://example.com/a',title:'First'},{id:43,windowId:7,url:'https://example.com/b',title:'Second'},{id:99,windowId:8,url:'https://example.com/',title:'Other window'},{id:100,windowId:7,url:'chrome://settings/',title:'Settings'}];
 const ctx=vm.createContext({console,URL,setTimeout:()=>0,clearTimeout:()=>{},chrome:{
 storage:{session:{get:async()=>({}),set:async()=>{}}},
 runtime:{id:'test',onMessage:{addListener(){}},sendMessage:async m=>{calls.push(m);return {ok:true};}},
 debugger:{onEvent:{addListener(){}},onDetach:{addListener(){}},attach:async t=>calls.push({attach:t.tabId}),detach:async t=>calls.push({detach:t.tabId}),sendCommand:async(t,method)=>calls.push({command:method,tabId:t.tabId})},
 tabs:{get:async id=>tabs.find(t=>t.id===id),query:async({windowId})=>tabs.filter(t=>t.windowId===windowId),update:async(id,args)=>calls.push({update:id,...args}),remove:async id=>{calls.push({remove:id});tabs.splice(tabs.findIndex(t=>t.id===id),1);},create:async args=>{const tab={id:200,windowId:args.windowId,url:args.url};tabs.push(tab);calls.push({create:tab});return tab;},onRemoved:{addListener(){}},onCreated:{addListener(){}},onUpdated:{addListener(){}}},action:{setBadgeText:async()=>{}}
 }});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../background.js'),'utf8'),ctx);await vm.runInContext('restored',ctx);
 vm.runInContext('tabId=42;browserWindowId=7;bridgeReady=true',ctx);return {ctx,calls};
}
test('switch attaches only selected page, detaches old page and publishes metadata before casting',async()=>{
 const h=await harness();await vm.runInContext('selectTab(43)',h.ctx);
 assert.ok(h.calls.find(c=>c.attach===43));assert.ok(h.calls.find(c=>c.detach===42));
 const meta=h.calls.find(c=>c.type==='metadata');assert.equal(meta.state.activeId,43);assert.equal(meta.state.tabs.length,2);
 assert.ok(h.calls.findIndex(c=>c.type==='metadata')<h.calls.findIndex(c=>c.command==='Page.startScreencast'));
});

test('capture status uses recently sent source rather than worker existence',async()=>{
 const h=await harness();
 const status=v=>{h.ctx.diagnostic=v;return vm.runInContext('captureStatus(diagnostic)',h.ctx);};
 const base={ready:true,enabled:true,video:true,lastSentAgeMs:30};
 assert.match(status({...base,lastSentSource:'watchdog'}),/watchdog/);
 assert.match(status({...base,lastSentSource:'cdp'}),/CDP/);
 assert.match(status({...base,lastSentSource:'tabCapture'}),/标签页视频流/);
 assert.match(status({...base,lastSentSource:'tabCapture',lastSentAgeMs:5000}),/静态页面或采集停滞/);
 assert.match(status({...base,lastSentAgeMs:null}),/近期无画面/);
 assert.match(status({...base,enabled:false}),/采集已暂停/);
 assert.match(status({...base,ready:false}),/等待游戏连接/);
});

test('closing active tab attaches neighbour before removal and keeps bridge connected',async()=>{
 const h=await harness();await vm.runInContext('closeTab(42)',h.ctx);
 assert.ok(h.calls.findIndex(c=>c.attach===43)<h.calls.findIndex(c=>c.remove===42));
 assert.equal(vm.runInContext('tabId',h.ctx),43);assert.equal(vm.runInContext('bridgeReady',h.ctx),true);
});
test('closing last shared tab creates a blank page instead of closing the Chrome window',async()=>{
 const h=await harness();await vm.runInContext('closeTab(43);',h.ctx);await vm.runInContext('closeTab(42)',h.ctx);
 assert.equal(h.calls.find(c=>c.create).create.url,'about:blank');assert.equal(vm.runInContext('tabId',h.ctx),200);
});
test('close rejects other windows and internal pages',async()=>{
 const h=await harness();await assert.rejects(vm.runInContext('closeTab(99)',h.ctx));await assert.rejects(vm.runInContext('closeTab(100)',h.ctx));assert.equal(h.calls.filter(c=>c.remove).length,0);
});
test('switch cannot attach another window or browser-internal pages',async()=>{
 const h=await harness();await assert.rejects(vm.runInContext('selectTab(99)',h.ctx));await assert.rejects(vm.runInContext('selectTab(100)',h.ctx));
 assert.equal(h.calls.filter(c=>c.attach).length,0);
});
test('address navigation accepts web URLs but rejects script, file and internal schemes',async()=>{
 const h=await harness();assert.equal(vm.runInContext("normalizeUrl('example.com/path')",h.ctx),'https://example.com/path');
 for(const u of ['javascript:alert(1)','file:///C:/a','chrome://settings/'])assert.throws(()=>vm.runInContext('normalizeUrl('+JSON.stringify(u)+')',h.ctx));
});
test('newly created tabs with a pending web URL are allowed before navigation commits',async()=>{
 const h=await harness();
 assert.equal(vm.runInContext("allowed({url:'',pendingUrl:'http://127.0.0.1:19287/second.html'})",h.ctx),true);
 assert.equal(vm.runInContext("allowed({url:'https://example.com',pendingUrl:'chrome://settings/'})",h.ctx),false);
});

test('reselecting current tab restarts capture instead of leaving a stopped image',async()=>{
 const h=await harness();await vm.runInContext('selectTab(42)',h.ctx);
 assert.ok(h.calls.some(c=>c.command==='Page.enable'));assert.ok(h.calls.some(c=>c.command==='Page.startScreencast'));
});
test('audio initialization cannot hold the switch operation open',async()=>{
 const h=await harness();vm.runInContext('captureAudio=()=>new Promise(()=>{})',h.ctx);
 await vm.runInContext('selectTab(43)',h.ctx);assert.equal(vm.runInContext('tabId',h.ctx),43);
});
