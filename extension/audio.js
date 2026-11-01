// Capture only the selected tab, never microphone or system audio.
let audioContext=null,audioStream=null,audioGain=null,audioNode=null,audioGeneration=0,audioTab=null,audioLease=0;
function audioRoute(enabled){
  audioLease=enabled?Date.now():0;
  if(audioGain&&audioContext)audioGain.gain.setTargetAtTime(enabled?0:1,audioContext.currentTime,.015);
}
setInterval(()=>{if(audioLease&&Date.now()-audioLease>1600)audioRoute(false);},250);
async function stopAudio(){
  if(typeof stopVideo==='function')stopVideo();
  audioGeneration++;audioTab=null;audioRoute(false);
  const stream=audioStream,context=audioContext;
  audioStream=null;audioContext=null;audioGain=null;
  if(audioNode)audioNode.port.onmessage=null;audioNode=null;
  stream?.getTracks().forEach(track=>track.stop());
  if(context)await context.close().catch(()=>{});
}
async function startAudio(streamId,tabId,geometry){
  await stopAudio();const generation=audioGeneration;
  let stream,context;
  try{
    const size=typeof captureSize==='function'?captureSize(geometry,1440):{width:2560,height:1440};
    stream=await navigator.mediaDevices.getUserMedia({audio:{mandatory:{chromeMediaSource:'tab',chromeMediaSourceId:streamId}},video:{mandatory:{chromeMediaSource:'tab',chromeMediaSourceId:streamId,minWidth:size.width,maxWidth:size.width,minHeight:size.height,maxHeight:size.height,maxFrameRate:30}}});
    if(generation!==audioGeneration){stream.getTracks().forEach(t=>t.stop());return;}
    context=new AudioContext({sampleRate:48000,latencyHint:'interactive'});
    if(context.sampleRate!==48000)throw new Error('浏览器未提供 48 kHz 音频');
    audioStream=stream;audioContext=context;audioTab=tabId;
    if(typeof startVideo==='function')void startVideo(stream,tabId,geometry).catch(e=>videoError=e.message);
    await context.audioWorklet.addModule('audio-worklet.js');
    if(generation!==audioGeneration){stream.getTracks().forEach(t=>t.stop());await context.close().catch(()=>{});return;}
    const source=context.createMediaStreamSource(stream);
    audioGain=context.createGain();audioGain.gain.value=1;source.connect(audioGain).connect(context.destination);
    audioNode=new AudioWorkletNode(context,'browser-pcm');
    const silent=context.createGain();silent.gain.value=0;source.connect(audioNode).connect(silent).connect(context.destination);
    audioNode.port.onmessage=event=>{
      if(generation!==audioGeneration||!ready||socket?.readyState!==WebSocket.OPEN||metadata?.activeId!==tabId||socket.bufferedAmount>2*1024*1024)return;
      const pcm=new Uint8Array(event.data);if(pcm.length!==1920)return;
      const packet=new Uint8Array(16+pcm.length),view=new DataView(packet.buffer);
      view.setUint32(0,0x424c4155);view.setUint32(4,48000,true);view.setUint32(8,960,true);view.setInt32(12,tabId,true);packet.set(pcm,16);socket.send(packet.buffer);
    };
    await context.resume();
  }catch(error){
    stream?.getTracks().forEach(t=>t.stop());
    if(generation===audioGeneration)await stopAudio();else if(context)await context.close().catch(()=>{});
    throw error;
  }
}
