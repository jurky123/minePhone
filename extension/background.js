let tabId=null,browserWindowId=null,status="请选择要显示的网页，然后点击连接。",connecting=false,bridgeReady=false;
let viewport=null,viewportTime=0,buttons=0,inputQueue=[],processing=false,generation=0,fps=30,lastFrame=0,quality=1440,pendingFrame=null,frameTimer=null;
let watchdog=null,snapshotBusy=false,lastLiveFrame=0;
let streamEnabled=true,policyHeight=1440,policyFps=30,policyTimer=null,policyRevision=0;
let connectionWarning='',mediaError='',captureFormat='jpeg',supportsJpeg=false,videoTab=null,lastVideoAlive=0;
const videoLive=()=>videoTab===tabId&&Date.now()-lastVideoAlive<3500;
function captureStatus(video){
  if(tabId===null)return '';
  if(!bridgeReady||!video?.ready)return '等待游戏连接';
  if(!video.enabled)return '采集已暂停 · 游戏暂无显示需求';
  const age=video.lastSentAgeMs;
  if(!Number.isFinite(age)||age>3500)return '近期无画面发送 · 静态页面或采集停滞';
  const sources={tabCapture:'标签页视频流',cdp:'CDP 连续截屏',watchdog:'watchdog 补帧 · 视频流未及时更新'};
  return (sources[video.lastSentSource]||'画面来源未知')+' · 最近发送 '+Math.round(age)+' ms 前';
}
const effectiveFormat=()=>captureFormat==='jpeg'&&supportsJpeg?'jpeg':'png';
const effectiveQuality=()=>Math.min(quality,policyHeight);
// Executed only in the explicitly connected tab, following the popup action.
// Never move the site's video or replace an existing user Picture-in-Picture.
async function pageKeepalive(action){
  const api=window.documentPictureInPicture;
  if(!api){if(action==='close')return 'closed';throw new Error('此浏览器或网页不支持可见小窗');}
  const existing=api.window;
  if(existing){
    if(existing.document.body?.dataset.browserlinkKeepalive==='1'){existing.close();return 'closed';}
    if(action==='close')return 'unchanged';
    throw new Error('请先关闭已有画中画窗口，再开启后台流畅模式');
  }
  if(action==='close')return 'closed';
  const pip=await api.requestWindow({width:240,height:160,disallowReturnToOpener:true});
  pip.document.title='BrowserLink 后台流畅模式';
  const body=pip.document.body;body.dataset.browserlinkKeepalive='1';
  body.style.cssText='margin:10px;background:#142536;color:#d9fff0;font:13px system-ui';
  const label=pip.document.createElement('p');label.textContent='保持此小窗可见，再切回 Minecraft。';
  const close=pip.document.createElement('button');close.textContent='关闭流畅模式';close.onclick=()=>pip.close();
  body.append(label,close);return 'opened';
}
async function keepalive(action='toggle',id=tabId){
  if(id===null)throw new Error('请先连接一个普通网页');
  const result=await chrome.debugger.sendCommand({tabId:id},'Runtime.evaluate',{
    expression:`(${pageKeepalive.toString()})(${JSON.stringify(action)})`,awaitPromise:true,returnByValue:true,userGesture:action==='toggle'
  });
  if(result?.exceptionDetails)throw new Error(result.exceptionDetails.exception?.description||'小窗打开失败，请回到已连接网页后重试');
  return result?.result?.value;
}
async function streamPolicy(msg){
  if(typeof msg.enabled!=='boolean'||![720,1080,1440].includes(msg.maxHeight)||![15,30].includes(msg.maxFps))return;
  clearTimeout(policyTimer);
  // A missing game heartbeat stops capture; it never stops audio or detaches the tab.
  policyTimer=setTimeout(()=>enqueue({type:'stream-policy',enabled:false,maxHeight:policyHeight,maxFps:policyFps,expired:true}),5000);
  const changed=streamEnabled!==msg.enabled||policyHeight!==msg.maxHeight||policyFps!==msg.maxFps;
  streamEnabled=msg.enabled;policyHeight=msg.maxHeight;policyFps=msg.maxFps;
  if(msg.expired){clearTimeout(policyTimer);policyTimer=null;}
  await chrome.runtime.sendMessage({target:'offscreen',type:'video-policy',enabled:streamEnabled,height:effectiveQuality(),fps:Math.min(fps,policyFps),format:effectiveFormat()}).catch(()=>{});
  if(!changed)return;
  policyRevision++;clearTimeout(frameTimer);frameTimer=null;pendingFrame=null;
  if(tabId===null||!bridgeReady)return;
  await chrome.debugger.sendCommand({tabId},'Page.stopScreencast').catch(()=>{});
  if(streamEnabled)await cast();
}
const restored=chrome.storage.session.get(["tabId","browserWindowId","status","fps","quality","captureFormat"]).then(s=>{captureFormat=s.captureFormat==='png'?'png':'jpeg';tabId=s.tabId??null;browserWindowId=s.browserWindowId??null;status=s.status||status;fps=s.fps||30;quality=[720,1080,1440].includes(s.quality)?s.quality:1440;});
async function setStatus(value){status=value;await chrome.storage.session.set({tabId,browserWindowId,status,fps,quality,captureFormat});}
const pageUrl=tab=>tab.pendingUrl||tab.url||'';
const allowed=tab=>/^https?:\/\//i.test(pageUrl(tab))||pageUrl(tab)==='about:blank';
function normalizeUrl(value){
  let url=String(value||'').trim();if(!url)throw new Error('请输入网址');
  if(!/^[a-z][a-z0-9+.-]*:/i.test(url))url='https://'+url;
  const parsed=new URL(url);if(!['http:','https:'].includes(parsed.protocol))throw new Error('仅支持 HTTP/HTTPS 网页');
  return parsed.href;
}
async function offscreen(){
  if(!(await chrome.runtime.getContexts({contextTypes:['OFFSCREEN_DOCUMENT']})).length)
    await chrome.offscreen.createDocument({url:'offscreen.html',reasons:['BLOBS','USER_MEDIA','AUDIO_PLAYBACK','WORKERS'],justification:'Stream the selected tab picture and audio to local Minecraft, using a worker to pace video frames while the browser is covered'});
}
async function captureAudio(id){
  if(!chrome.tabCapture)return;
  const gen=generation;
  await chrome.runtime.sendMessage({target:'offscreen',type:'audio-stop'}).catch(()=>{});
  if(id!==tabId||gen!==generation)return;
  try{
    const streamId=await chrome.tabCapture.getMediaStreamId({targetTabId:id});
    if(id!==tabId||gen!==generation)return;
    const geometry=await captureGeometry(id);
    const result=await chrome.runtime.sendMessage({target:'offscreen',type:'audio-start',streamId,tabId:id,geometry});
    if(!result?.ok)throw new Error(result?.error||'音频初始化失败');mediaError='';
    if(id===tabId)await setStatus('已连接：音频可从最近交互的游戏设备发出');
  }catch(error){
    if(id===tabId&&gen===generation){
      const message='标签页视频/音频未启用，请在 Chrome 点击连接：'+error.message;mediaError=message;
      await setStatus(message);await chrome.runtime.sendMessage({target:'offscreen',type:'report',message}).catch(()=>{});
    }
  }
}
async function publishTabs(){
  if(tabId===null||browserWindowId===null)return;
  const id=tabId,gen=generation,windowId=browserWindowId;
  const tabs=(await chrome.tabs.query({windowId})).filter(allowed).slice(0,64);
  const active=tabs.find(t=>t.id===id)||await chrome.tabs.get(id);
  if(id!==tabId||gen!==generation)return;
  await chrome.runtime.sendMessage({target:'offscreen',type:'metadata',state:{type:'browser-state',activeId:id,url:pageUrl(active).slice(0,2048),tabs:tabs.map(t=>({id:t.id,title:(t.title||pageUrl(t)||'New tab').slice(0,80)}))}}).catch(()=>{});
}
async function snapshot(id=tabId){
  if(snapshotBusy||!streamEnabled||!bridgeReady||id===null||id!==tabId)return;
  snapshotBusy=true;const gen=generation,live=lastLiveFrame,revision=policyRevision;
  const current=()=>id===tabId&&gen===generation&&bridgeReady&&streamEnabled&&revision===policyRevision;
  try{
    // Capture the viewport without CDP clip scaling (which can change the visible
    // content scale on minimized windows). Resize the resulting pixels offscreen.
    const result=await chrome.debugger.sendCommand({tabId:id},'Page.captureScreenshot',{format:'png',fromSurface:true,captureBeyondViewport:false});
    if(current()&&lastLiveFrame===live&&typeof result.data==='string')await chrome.runtime.sendMessage({target:'offscreen',type:'snapshot',tabId:id,data:result.data,maxWidth:Math.round(effectiveQuality()*16/9),maxHeight:effectiveQuality()});
  }catch(error){/* Navigation can briefly destroy the renderer; watchdog retries. */}
  finally{snapshotBusy=false;}
}
function watchFrames(){
  clearTimeout(watchdog);
  if(!bridgeReady||tabId===null)return;
  watchdog=setTimeout(async()=>{if(streamEnabled&&!videoLive()&&Date.now()-lastLiveFrame>1200)await snapshot();watchFrames();},1000);
}
async function cast(id=tabId){
  if(!bridgeReady||!streamEnabled||videoLive()||id===null||id!==tabId)return;
  const gen=generation;
  await chrome.debugger.sendCommand({tabId:id},'Page.enable');

  if(id!==tabId||gen!==generation||!bridgeReady)return;
  await chrome.debugger.sendCommand({tabId:id},'Page.startScreencast',{format:effectiveFormat(),quality:82,maxWidth:Math.round(effectiveQuality()*16/9),maxHeight:effectiveQuality(),everyNthFrame:1});
  if(id!==tabId||gen!==generation)return;
  void snapshot(id);watchFrames();
}
async function stop(reason='已断开连接'){
  const old=tabId;videoTab=null;lastVideoAlive=0;tabId=null;browserWindowId=null;generation++;bridgeReady=false;inputQueue=[];buttons=0;viewport=null;clearTimeout(frameTimer);frameTimer=null;pendingFrame=null;
  clearTimeout(watchdog);watchdog=null;clearTimeout(policyTimer);policyTimer=null;streamEnabled=true;policyHeight=1440;policyFps=30;policyRevision++;
  await setStatus(reason);
  await chrome.runtime.sendMessage({target:'offscreen',type:'stop'}).catch(()=>{});
  if(old!==null){await keepalive('close',old).catch(()=>{});await chrome.debugger.detach({tabId:old}).catch(()=>{});}
  await chrome.action.setBadgeText({text:''});
}
async function connect(msg){
  if(connecting)throw new Error('连接正在进行，请稍候。');connecting=true;let gen;
  try{
    const tab=await chrome.tabs.get(msg.tabId);if(!allowed(tab))throw new Error('请先打开普通 HTTP/HTTPS 网页');
    await stop('正在连接…');gen=generation;await offscreen();if(gen!==generation)throw new Error('连接已取消');
    await chrome.debugger.attach({tabId:tab.id},'1.3');
    if(gen!==generation){await chrome.debugger.detach({tabId:tab.id}).catch(()=>{});throw new Error('连接已取消');}
    tabId=tab.id;browserWindowId=tab.windowId;captureFormat=msg.captureFormat==='png'?'png':'jpeg';fps=Math.max(5,Math.min(30,msg.fps||30));quality=[720,1080,1440].includes(msg.quality)?msg.quality:1440;
    await setStatus('已选择网页窗口，等待游戏进入世界；无需配对码');
    await chrome.action.setBadgeBackgroundColor({color:'#70d9b4'});await chrome.action.setBadgeText({text:'WAIT'});
    const result=await chrome.runtime.sendMessage({target:'offscreen',type:'start'});
    if(!result?.ok)throw new Error(result?.error||'连接失败');await publishTabs();await captureAudio(tabId);
  }catch(e){if(gen===undefined||gen===generation)await stop('连接失败：'+e.message);throw e;}finally{connecting=false;}
}
async function selectTab(id){
  if(!Number.isInteger(id)||tabId===null)return;
  const tab=await chrome.tabs.get(id);if(tab.windowId!==browserWindowId||!allowed(tab))throw new Error('该标签页不在连接窗口内或不是普通网页');
  if(id===tabId){await chrome.tabs.update(id,{active:true});await publishTabs();await cast(id);return;}
  const old=tabId,gen=generation;
  await chrome.debugger.attach({tabId:id},'1.3');
  if(gen!==generation){await chrome.debugger.detach({tabId:id}).catch(()=>{});return;}
  generation++;tabId=id;buttons=0;viewport=null;lastClick=null;inputQueue=[];lastFrame=0;clearTimeout(frameTimer);frameTimer=null;pendingFrame=null;
  lastLiveFrame=0;
  await keepalive('close',old).catch(()=>{});
  await chrome.debugger.detach({tabId:old}).catch(()=>{});
  await chrome.tabs.update(id,{active:true});
  await setStatus('已连接：可在游戏顶部切换标签页');await publishTabs();await cast(id);void captureAudio(id).catch(()=>{});
}
async function closeTab(id){
  if(!Number.isInteger(id)||tabId===null)return;
  const tab=await chrome.tabs.get(id);
  if(!tab||tab.windowId!==browserWindowId||!allowed(tab))throw new Error('只能关闭连接窗口内的网页');
  if(id===tabId){
    const tabs=(await chrome.tabs.query({windowId:browserWindowId})).filter(allowed);
    const index=tabs.findIndex(t=>t.id===id);
    let next=tabs[index+1]||tabs[index-1];
    if(!next)next=await chrome.tabs.create({windowId:browserWindowId,url:'about:blank',active:true});
    await selectTab(next.id);
  }
  await chrome.tabs.remove(id);await publishTabs();
}
let lastClick=null;
async function captureGeometry(id){
  const r=await chrome.debugger.sendCommand({tabId:id},'Runtime.evaluate',{expression:'({width:innerWidth,height:innerHeight})',returnByValue:true});
  const g=r?.result?.value;
  return g&&g.width>0&&g.height>0?g:null;
}
async function dispatch(msg, id, gen) {
  if(id!==tabId||gen!==generation)throw new Error('Session ended');
  if(msg.type==='stream-policy'){await streamPolicy(msg);return;}
  const send=async(method,params={})=>{
    if (id!==tabId||gen!==generation) throw new Error("Session ended");
    return chrome.debugger.sendCommand({tabId:id},method,params);
  };
  if (msg.type==="pointer") {
    if (!Number.isFinite(msg.x)||!Number.isFinite(msg.y)) return;
    if (!viewport || Date.now()-viewportTime>1000 || msg.action==="down") {
      const metrics=await send("Page.getLayoutMetrics");
      const geometry=await captureGeometry(id).catch(()=>null);
      // Capture includes scrollbars; layoutViewport excludes them.
      viewport=geometry?{clientWidth:geometry.width,clientHeight:geometry.height}:metrics.cssLayoutViewport||metrics.layoutViewport; viewportTime=Date.now();
    }
    const x=Math.max(0,Math.min(.99999,msg.x))*viewport.clientWidth;
    const y=Math.max(0,Math.min(.99999,msg.y))*viewport.clientHeight;
    const names=["left","right","middle"], bits=[1,2,4];
    if (msg.action==="down") buttons|=bits[msg.button]||0;
    if (msg.action==="up") buttons&=~(bits[msg.button]||0);
    const type={down:"mousePressed",up:"mouseReleased",move:"mouseMoved",wheel:"mouseWheel"}[msg.action];
    if (!type) return;
    const args={type,x,y,button:names[msg.button]||"none",buttons,modifiers:msg.modifiers&15};
    if (msg.action==="down"||msg.action==="up") {
      if(msg.action==='down'){
        const now=Date.now();const repeated=lastClick&&lastClick.id===id&&lastClick.button===msg.button&&now-lastClick.time<500&&Math.hypot(lastClick.x-x,lastClick.y-y)<5;
        lastClick={id,button:msg.button,x,y,time:now,count:repeated?lastClick.count%3+1:1};
      }
      args.clickCount=lastClick?.count||1;
    }
    if (msg.action==="wheel") {args.deltaX=msg.modifiers&8?Math.max(-2000,Math.min(2000,msg.delta||0)):0;args.deltaY=msg.modifiers&8?0:Math.max(-2000,Math.min(2000,msg.delta||0));}
    await send("Input.dispatchMouseEvent",args);
  } else if (msg.type==="text" && typeof msg.text==="string") {
    await send("Input.insertText",{text:msg.text.slice(0,4096)});
  } else if (msg.type==="key") {
    const keys={Enter:13,Backspace:8,Delete:46,Tab:9,ArrowLeft:37,ArrowUp:38,ArrowRight:39,ArrowDown:40,Home:36,End:35,PageUp:33,PageDown:34,a:65,z:90,y:89,Escape:27,Space:32};
    if (!keys[msg.key]) return;
    const args={key:msg.key==='Space'?' ':msg.key,code:msg.key.length===1?"Key"+msg.key.toUpperCase():msg.key,windowsVirtualKeyCode:keys[msg.key],modifiers:msg.modifiers&15};
    await send("Input.dispatchKeyEvent",{...args,type:"rawKeyDown"});
    if (msg.key==="Enter"||msg.key==="Space") {const text=msg.key==="Enter"?"\r":" ";await send("Input.dispatchKeyEvent",{...args,type:"char",text,unmodifiedText:text});}
    await send("Input.dispatchKeyEvent",{...args,type:"keyUp"});
  } else if(msg.type==='close-tab'){
    await closeTab(msg.tabId);
  } else if(msg.type==='clipboard'){
    const result=await send('Runtime.evaluate',{expression:`(()=>{const e=document.activeElement;if(e&&e.type==='password')return '';if(e&&typeof e.selectionStart==='number')return e.value.slice(e.selectionStart,e.selectionEnd);return window.getSelection()?.toString()||'';})()`,returnByValue:true});
    const text=result.result?.value;
    if(typeof text==='string'&&text){
      await chrome.runtime.sendMessage({target:'offscreen',type:'clipboard',text:text.slice(0,65536)});
      if(msg.cut)await dispatch({type:'key',key:'Backspace',modifiers:0},id,gen);
    }
  } else if (msg.type==="switch-tab") {
    await selectTab(msg.tabId);
  } else if (msg.type==="navigate") {
    const url=msg.newTab&&!String(msg.url||'').trim()?'about:blank':normalizeUrl(msg.url);
    if(msg.newTab){const tab=await chrome.tabs.create({windowId:browserWindowId,url,active:true});await selectTab(tab.id);}
    else await chrome.tabs.update(tabId,{url});
    await publishTabs();
  } else if (msg.type==="tabs-request") {
    await publishTabs();
  } else if (msg.type==="command") {
    if(msg.action==='zoom-in'||msg.action==='zoom-out'||msg.action==='zoom-reset'){
      const current=await chrome.tabs.getZoom(id);
      await chrome.tabs.setZoom(id,msg.action==='zoom-reset'?1:Math.max(.25,Math.min(5,current*(msg.action==='zoom-in'?1.1:1/1.1))));
      viewport=null;
    } else if (msg.action==="reload") await send("Page.reload");
    else if (msg.action==="back"||msg.action==="forward") {
      const history=await send("Page.getNavigationHistory");
      const entry=history.entries[history.currentIndex+(msg.action==="back"?-1:1)];
      if(entry) await send("Page.navigateToHistoryEntry",{entryId:entry.id});
    }
  }
}

function enqueue(msg){
  if(tabId===null)return;
  if(msg.type==='stream-policy'){inputQueue=inputQueue.filter(x=>x.type!=='stream-policy');}
  if(msg.type==='pointer'&&msg.action==='move'&&inputQueue.at(-1)?.action==='move')inputQueue[inputQueue.length-1]=msg;
  else if(inputQueue.length<128)inputQueue.push(msg);
  if(!processing)drain();
}
async function drain(){
  processing=true;
  try{while(inputQueue.length&&tabId!==null){const msg=inputQueue.shift(),id=tabId,gen=generation;
    try{await dispatch(msg,id,gen);}catch(e){if(gen===generation){await setStatus('操作失败：'+e.message);await chrome.runtime.sendMessage({target:'offscreen',type:'report',message:e.message}).catch(()=>{});}}
  }}finally{processing=false;}
}
chrome.runtime.onMessage.addListener((msg,sender,reply)=>{
  if(msg.target!=='worker'||sender.id!==chrome.runtime.id)return;
  (async()=>{
    await restored;
    if(msg.type==='connection-warning'){connectionWarning=msg.message;return {ok:true};}
    if(msg.type==='video-alive'&&msg.tabId===tabId){const id=tabId,gen=generation,was=videoLive();videoTab=id;lastVideoAlive=Date.now();if(!was){clearTimeout(frameTimer);frameTimer=null;pendingFrame=null;void chrome.debugger.sendCommand({tabId:id},'Page.stopScreencast').catch(()=>{});}const geometry=await captureGeometry(id).catch(()=>null);return id===tabId&&gen===generation?{ok:true,geometry}:{ok:false};}
    if(msg.type==='state'){
      const video=await chrome.runtime.sendMessage({target:'offscreen',type:'diagnostics'}).catch(()=>null);
      return {status,tabId,mediaError,connectionWarning,video,source:captureStatus(video)};
    }
    if(msg.type==='connect'){await connect(msg);return {ok:true};}
    if(msg.type==='keepalive'){
      const result=await keepalive();
      await setStatus(result==='opened'?'后台流畅模式已开启：保持小窗可见，再切回游戏；换页后需重新开启':'后台流畅模式已关闭');
      return {ok:true};
    }
    if(msg.type==='stop'){await stop();return {ok:true};}
    if(msg.type==='bridge-state'){
      if(tabId===null)return {ok:true};bridgeReady=!!msg.connected;supportsJpeg=!!msg.jpeg;if(bridgeReady)connectionWarning='';
      await setStatus(bridgeReady?'已连接：可在游戏顶部切换标签页':'已选择网页窗口，等待游戏；将自动重连');
      await chrome.action.setBadgeText({text:bridgeReady?'ON':'WAIT'});
      if(bridgeReady){await publishTabs();await cast();}
      else {clearTimeout(watchdog);watchdog=null;await chrome.debugger.sendCommand({tabId},'Page.stopScreencast').catch(()=>{});}
      return {ok:true};
    }
    if(msg.type==='capture-ended'){await stop(msg.error?'连接结束：'+msg.error:'已断开连接');return {ok:true};}
    if(msg.type==='input'){enqueue(msg.event);return {ok:true};}
  })().then(reply,e=>reply({ok:false,error:e.message}));return true;
});
chrome.debugger.onEvent.addListener(async(source,method,params)=>{
  await restored;
  if(method!=='Page.screencastFrame')return;
  await chrome.debugger.sendCommand(source,'Page.screencastFrameAck',{sessionId:params.sessionId}).catch(()=>{});
  if(source.tabId!==tabId||!bridgeReady||!streamEnabled||videoLive())return;
  lastLiveFrame=Date.now();
  pendingFrame={target:'offscreen',type:'frame',tabId:source.tabId,data:params.data,format:effectiveFormat()};
  if(frameTimer===null)frameTimer=setTimeout(flushFrame,Math.max(0,1000/Math.min(fps,policyFps)-(Date.now()-lastFrame)));
});
async function flushFrame(){
  frameTimer=null;const frame=pendingFrame;pendingFrame=null;
  if(!frame||frame.tabId!==tabId||!bridgeReady||!streamEnabled||videoLive())return;
  lastFrame=Date.now();
  await chrome.runtime.sendMessage(frame).catch(()=>{});
}
// Keep the final changed frame even when it arrives inside the FPS interval.
chrome.runtime.onSuspend?.addListener(()=>{
  clearTimeout(frameTimer);clearTimeout(watchdog);clearTimeout(policyTimer);
});
chrome.debugger.onDetach.addListener(async(source,reason)=>{await restored;if(source.tabId===tabId)await stop('浏览器调试连接已断开：'+reason);});
chrome.tabs.onRemoved.addListener(async id=>{await restored;if(id===tabId)await stop('所选标签页已关闭');else await publishTabs().catch(()=>{});});
chrome.tabs.onUpdated.addListener(async(id,changes,tab)=>{await restored;if(tab.windowId===browserWindowId&&(changes.title||changes.url||changes.status==='complete')){
  if(id===tabId&&!allowed(tab)){await stop('所选标签页已转到不支持的页面');return;}await publishTabs().catch(()=>{});
  if(id===tabId&&changes.status==='complete')await cast(id).catch(()=>{});
}});
chrome.tabs.onCreated.addListener(async tab=>{await restored;if(tab.windowId===browserWindowId)await publishTabs().catch(()=>{});});

