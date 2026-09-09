import {chromium} from '/opt/facelift/node_modules/playwright/index.mjs';
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';

// Exercise the exact APK asset at the private HTTPS origin, with the native CSP.
const root=path.resolve(import.meta.dirname,'../..');
const html=fs.readFileSync(root+'/android/app/src/main/assets/games/lost-trail/index.html');
const manifest=JSON.parse(fs.readFileSync(root+'/android/app/src/main/assets/games/lost-trail/provenance.json'));
const hash=createHash('sha256').update(html).digest('hex');assert.equal(hash,manifest.sha256);
const dir=process.env.EVIDENCE_DIR||'/tmp/bert-mobile-game';fs.mkdirSync(dir,{recursive:true});
const url='https://appassets.androidplatform.net/lost-trail/index.html?app';
const csp="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; base-uri 'none'; form-action 'none'; frame-src 'none'";
const browser=await chromium.launch({args:['--no-sandbox','--disable-dev-shm-usage']});
const result={appSha:execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),game:manifest,layouts:[],errors:[],remoteRequests:[]};
try{
 for(const [width,height] of [[320,520],[390,724],[844,294]]){
  const context=await browser.newContext({viewport:{width,height},isMobile:true,hasTouch:true,deviceScaleFactor:1});
  await context.route('**/*',route=>{
   if(route.request().url().startsWith(url))return route.fulfill({body:html,contentType:'text/html',headers:{'Content-Security-Policy':csp}});
   result.remoteRequests.push(route.request().url());return route.abort();
  });
  const page=await context.newPage();page.on('pageerror',e=>result.errors.push(e.message));
  await page.goto(url+'&qa');await page.waitForFunction(()=>window.__QA?.ready());await page.evaluate(()=>__QA.manual());
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.screenshot({path:`${dir}/title-${width}.png`});
  await page.locator('#start').tap();await page.evaluate(()=>__QA.advance(2));
  const frame=await page.locator('#frame').boundingBox();
  assert.ok(frame.width*frame.height/(width*height)>=.5,'Game must occupy at least half of the available view');
  const toast=await page.locator('#toast').boundingBox();
  assert.ok(toast.y+toast.height<frame.y+frame.height*.65,'Trail messages must stay above the ground and player');
  const controls={};
  for(const key of ['left','right','run','bark','jump']){
   const r=await page.locator(`[data-control=${key}]`).boundingBox();assert.ok(r.width>=48&&r.height>=48,`${key}: touch size`);
   assert.ok(r.x>=0&&r.y>=0&&r.x+r.width<=width+.1&&r.y+r.height<=height+.1,`${key}: inside viewport ${width}`);controls[key]=r;
  }
  for(const id of ['pause','sound']){const r=await page.locator('#'+id).boundingBox();assert.ok(r.width>=48&&r.height>=48);}
  const cdp=await context.newCDPSession(page);
  await page.locator('[data-control=run]').tap();
  const pts=['right','jump'].map((k,i)=>({x:controls[k].x+controls[k].width/2,y:controls[k].y+controls[k].height/2,id:i+1}));
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:pts});await page.evaluate(()=>__QA.advance(18));
  let state=await page.evaluate(()=>__QA.read());assert.ok(state.p.vx>=5&&state.p.y<190&&state.stats.jumps===1);
  await cdp.send('Input.dispatchTouchEvent',{type:'touchCancel',touchPoints:[]});await page.evaluate(()=>__QA.advance(30));
  assert.ok(Math.abs((await page.evaluate(()=>__QA.read())).p.vx)<.1);
  await page.screenshot({path:`${dir}/playing-${width}.png`});
  const tick=(await page.evaluate(()=>__QA.read())).tick;
  assert.equal(await page.evaluate(()=>BertAppGame.back()),true);await page.evaluate(()=>__QA.advance(60));
  assert.equal((await page.evaluate(()=>__QA.read())).tick,tick);
  assert.equal(await page.locator('[data-control=run]').getAttribute('aria-pressed'),'false');
  assert.equal(await page.evaluate(()=>BertAppGame.back()),false);
  await page.locator('#resume').tap();await page.evaluate(()=>BertAppGame.pause());
  assert.equal((await page.evaluate(()=>__QA.read())).screen,'pause');
  await page.locator('#pause-map').tap();assert.equal(await page.evaluate(()=>BertAppGame.back()),true);
  assert.equal((await page.evaluate(()=>__QA.read())).screen,'pause');
  await page.reload();await page.waitForFunction(()=>__QA?.ready());await page.evaluate(()=>__QA.manual());
  assert.match(await page.locator('#start').innerText(),/Continue/);await page.locator('#start').tap();
  state=await page.evaluate(()=>__QA.read());assert.equal(state.p.hp,3);assert.equal(state.p.x,state.checkpoint.x);
  result.layouts.push({width,height,frame,toast,gameAreaFraction:frame.width*frame.height/(width*height),controls,restored:state});await context.close();
 }
 // Real keyboard input earns the first lantern, then a new page reloads its stored state.
 const {routeInput}=await import('/opt/bert-platformer/tools/qa/route-controller.mjs');
 const {createGame,stepGame}=await import('/opt/bert-platformer/src/engine.mjs');
 const ctx=await browser.newContext({viewport:{width:390,height:724},hasTouch:true,isMobile:true});
 await ctx.route('**/*',r=>r.fulfill({body:html,contentType:'text/html',headers:{'Content-Security-Policy':csp}}));
 const page=await ctx.newPage();page.on('pageerror',e=>result.errors.push(e.message));await page.goto(url+'&qa');await page.waitForFunction(()=>__QA?.ready());await page.evaluate(()=>__QA.manual());await page.locator('#start').tap();
 const g=createGame(0),keys={left:'ArrowLeft',right:'ArrowRight',jump:'Space',run:'Shift',bark:'KeyF'};let held={};
 for(let n=0;n<2000&&g.checkpoint.id<0;n++){
  const input=routeInput(g);for(const [key,code]of Object.entries(keys)){if(input[key]&&!held[key])await page.keyboard.down(code);if(!input[key]&&held[key])await page.keyboard.up(code);}held=input;
  stepGame(g,input);await page.evaluate(()=>__QA.advance());
 }
 const earned=await page.evaluate(()=>__QA.read());assert.equal(earned.checkpoint.id,0);assert.ok(earned.coins>0);assert.equal(earned.switches[0].active,true);
 await page.reload();await page.waitForFunction(()=>__QA?.ready());await page.evaluate(()=>__QA.manual());await page.locator('#start').tap();
 const resumed=await page.evaluate(()=>__QA.read());assert.equal(resumed.checkpoint.id,0);assert.equal(resumed.coins,earned.coins);assert.equal(resumed.switches[0].active,true);assert.equal(resumed.p.x,earned.checkpoint.x);
 result.checkpoint={earned,resumed};await page.screenshot({path:dir+'/lantern-resumed.png'});
 // Production load omits the diagnostic flag; no QA hooks and no external requests.
 await page.goto(url);await page.waitForFunction(()=>window.BertAppGame);assert.equal(await page.evaluate(()=>typeof window.__QA),'undefined');
 assert.deepEqual(result.errors,[]);assert.deepEqual(result.remoteRequests,[]);
 fs.writeFileSync(dir+'/results.json',JSON.stringify(result,null,2)+'\n');console.log(`PASS lost-trail-browser at ${result.appSha}: ${hash}`);
}finally{await browser.close();}
