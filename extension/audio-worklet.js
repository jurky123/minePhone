class BrowserPcm extends AudioWorkletProcessor {
  constructor(){super();this.samples=new Int16Array(960);this.used=0;}
  process(inputs){
    const channels=inputs[0];if(!channels?.length)return true;
    for(let i=0;i<channels[0].length;i++){
      let value=0;for(const channel of channels)value+=channel[i]||0;
      value=Math.max(-1,Math.min(1,value/channels.length));
      this.samples[this.used++]=Math.round(value*(value<0?32768:32767));
      if(this.used===960){this.port.postMessage(this.samples.buffer,[this.samples.buffer]);this.samples=new Int16Array(960);this.used=0;}
    }
    return true;
  }
}
registerProcessor('browser-pcm',BrowserPcm);
