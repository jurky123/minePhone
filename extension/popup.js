const $ = id => document.getElementById(id);
async function refresh() {
  const state = await chrome.runtime.sendMessage({target:"worker",type:"state"});
  $("status").textContent = (state.status || "请选择要显示的标签页，然后点击连接。无需配对码。")+(state.source?'\n'+state.source:'')+(state.connectionWarning?'\n'+state.connectionWarning:'')+(state.mediaError?'\n'+state.mediaError:'')+(state.video?.error?'\n'+state.video.error:'');
}
(async () => {
  const saved = await chrome.storage.local.get(["fps","quality","captureFormat"]);
  $("fps").value = String(saved.fps || 30);
  $("quality").value=String(saved.quality||1440);
  $("captureFormat").value=saved.captureFormat==='png'?'png':'jpeg';
  await refresh();
})().catch(e => $("status").textContent=e.message);
$("connect").addEventListener("click", async () => {
  $("connect").disabled=true;
  try {
    const [tab]=await chrome.tabs.query({active:true,currentWindow:true});
    const fps=Number($("fps").value),quality=Number($("quality").value),captureFormat=$("captureFormat").value;
    await chrome.storage.local.set({fps,quality,captureFormat});
    $("status").textContent="正在连接…";
    const result=await chrome.runtime.sendMessage({target:"worker",type:"connect",tabId:tab.id,fps,quality,captureFormat});
    if (!result.ok) throw new Error(result.error);
    await refresh();
  } catch(e) { $("status").textContent=e.message; }
  finally { $("connect").disabled=false; }
});
$("stop").addEventListener("click",async()=>{await chrome.runtime.sendMessage({target:"worker",type:"stop"});await refresh();});
$("keepalive").addEventListener("click",async()=>{
  $("keepalive").disabled=true;
  try{const result=await chrome.runtime.sendMessage({target:'worker',type:'keepalive'});if(!result?.ok)throw new Error(result?.error||'操作失败');await refresh();}
  catch(e){$("status").textContent=e.message;}
  finally{$("keepalive").disabled=false;}
});
setInterval(()=>refresh().catch(()=>{}),1500);
