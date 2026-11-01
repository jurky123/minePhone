const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../background.js'),'utf8');
async function run({existing=false,owned=false,supported=true,action='toggle'}={}){
 let closed=0,opened=0;const children=[];
 const pip={document:{body:{dataset:owned?{browserlinkKeepalive:'1'}:{},style:{},append(...nodes){children.push(...nodes);}},createElement:()=>({})},close(){closed++;}};
 const api={window:existing?pip:null,async requestWindow(){opened++;return pip;}};
 const ctx=vm.createContext({window:{documentPictureInPicture:supported?api:null}});
 vm.runInContext(source.slice(source.indexOf('async function pageKeepalive'),source.indexOf('async function keepalive(')),ctx);
 const result=await vm.runInContext(`pageKeepalive(${JSON.stringify(action)})`,ctx);
 return {result,closed,opened,pip,children};
}
test('visible helper is marked, reversible, and never moves page video',async()=>{const r=await run();assert.equal(r.opened,1);assert.equal(r.closed,0);assert.equal(r.pip.document.body.dataset.browserlinkKeepalive,'1');assert.equal(r.children.length,2);r.children[1].onclick();assert.equal((await run({existing:true,owned:true})).result,'closed');});
test('existing user PiP is not replaced or closed',async()=>{await assert.rejects(()=>run({existing:true}),/已有画中画/);const r=await run({existing:true,action:'close'});assert.equal(r.closed,0);assert.equal(r.opened,0);});
test('cleanup never opens a window and unsupported browsers fail clearly',async()=>{const r=await run({action:'close'});assert.equal(r.opened,0);await assert.rejects(()=>run({supported:false}),/不支持/);assert.equal((await run({supported:false,action:'close'})).result,'closed');});
