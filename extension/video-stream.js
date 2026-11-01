// Consume capture frames directly. Neither a hidden video element nor a window
// timer participates in capture, scaling or encoding. Buffer size is one upstream.
let policy={enabled:false,credit:false,height:720,fps:30,format:'jpeg',epoch:0},running=false;
self.onmessage=({data})=>{
  if(data.type==='policy'){policy=data;return;}
  if(data.type==='start'&&!running){running=true;void consume(data.readable);}
};
async function consume(readable){
  const reader=readable.getReader(),canvas=new OffscreenCanvas(1,1),ctx=canvas.getContext('2d',{alpha:false});let nextDue=-Infinity,at=performance.now(),inputs=0,outputs=0,encodeMs=0,drawMs=0;
  try{
    for(;;){
      const {value:frame,done}=await reader.read();if(done)break;
      try{
        const now=performance.now(),p=policy;inputs++;
        if(now-at>=1000){self.postMessage({stats:{inputFps:inputs*1000/(now-at),outputFps:outputs*1000/(now-at),encodeMs,drawMs}});at=now;inputs=0;outputs=0;}
        const interval=1000/p.fps;
        if(!p.enabled||!p.credit||now<nextDue-2)continue;
        const scale=Math.min(1,p.height/frame.displayHeight,Math.round(p.height*16/9)/frame.displayWidth);
        const w=Math.max(1,Math.floor(frame.displayWidth*scale)),h=Math.max(1,Math.floor(frame.displayHeight*scale));
        if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
        // Keep a deadline instead of resetting to each arrival: small capture
        // jitter must not turn a nominal 30 Hz stream into alternating drops.
        nextDue=(nextDue<now-interval?now:nextDue)+interval;
        policy={...policy,credit:false};ctx.drawImage(frame,0,0,w,h);
        drawMs=performance.now()-now;
        const blob=await canvas.convertToBlob({type:p.format==='jpeg'?'image/jpeg':'image/png',quality:.82});
        outputs++;encodeMs=performance.now()-now;self.postMessage({blob,epoch:p.epoch});
      }finally{frame.close();}
    }
  }catch(error){self.postMessage({error:error.message});}
  finally{reader.releaseLock();}
}
