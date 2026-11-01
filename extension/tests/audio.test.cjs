const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
test('worklet mixes stereo to mono, preserves PCM clipping and emits 20ms packets',()=>{
 let Processor;const messages=[];
 class Base{constructor(){this.port={postMessage:b=>messages.push(new Int16Array(b))};}}
 const ctx=vm.createContext({AudioWorkletProcessor:Base,registerProcessor:(name,type)=>Processor=type});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../audio-worklet.js'),'utf8'),ctx);
 const p=new Processor();p.process([[new Float32Array(960).fill(1),new Float32Array(960).fill(-1)]]);
 assert.equal(messages.length,1);assert.equal(messages[0].length,960);assert.ok(messages[0].every(v=>v===0));
 p.process([[new Float32Array(960).fill(2)]]);assert.ok(messages[1].every(v=>v===32767));
 p.process([[new Float32Array(960).fill(-2)]]);assert.ok(messages[2].every(v=>v===-32768));
});
test('audio returns to browser when the game sink heartbeat expires',()=>{
 let clock=1000,watchdog;const gains=[];
 const ctx=vm.createContext({Date:{now:()=>clock},setInterval:fn=>watchdog=fn});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../audio.js'),'utf8'),ctx);
 ctx.gains=gains;vm.runInContext('audioContext={currentTime:0};audioGain={gain:{setTargetAtTime:v=>gains.push(v)}};audioRoute(true)',ctx);
 assert.equal(gains.at(-1),0);clock=2700;watchdog();assert.equal(gains.at(-1),1);
});
