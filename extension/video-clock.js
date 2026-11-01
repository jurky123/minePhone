// One requested tick at a time: no interval backlog or busy loop.
let timer;
self.onmessage=({data})=>{
  clearTimeout(timer);
  if(Number.isFinite(data))timer=setTimeout(()=>self.postMessage(null),Math.max(1,Math.min(1000,data)));
};
