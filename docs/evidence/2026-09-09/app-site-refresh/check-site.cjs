const {chromium}=require('/opt/facelift/node_modules/playwright');
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const child=require('node:child_process');
const root='/opt/bert-widget-qa/app-site-refresh';
const mode=process.argv[2]||'public';
assert(['staged','public'].includes(mode));
const contentRoot=mode==='public'?'/opt/berthalla/website':`${root}/staged`;
const out=process.env.APP_SITE_EVIDENCE||root;
fs.mkdirSync(out,{recursive:true});
const mime={'.html':'text/html','.css':'text/css','.js':'application/javascript','.json':'application/json','.png':'image/png','.jpg':'image/jpeg','.svg':'image/svg+xml','.woff2':'font/woff2','.mp4':'video/mp4'};
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const results=[];
 try{
  for(const width of (process.env.APP_SITE_WIDTHS||'1440,768,390,320').split(',').map(Number)) for(const pathname of ['/','/widget/']){
   const page=await browser.newPage({viewport:{width,height:900},deviceScaleFactor:1,reducedMotion:'reduce'});
   const errors=[];page.on('pageerror',e=>errors.push(e.message));
   if(mode!=='public') await page.route('https://berthalla.io/**',async route=>{
    let name=decodeURIComponent(new URL(route.request().url()).pathname);
    if(name.endsWith('/'))name+='index.html';
    const staged=path.join(contentRoot,name);
    const live=path.join('/opt/berthalla/website',name);
    const file=fs.existsSync(staged)?staged:live;
    if(fs.existsSync(file)&&fs.statSync(file).isFile())await route.fulfill({path:file,contentType:mime[path.extname(file)]||'application/octet-stream'});
    else await route.continue();
   });
   await page.goto('https://berthalla.io'+pathname,{waitUntil:'domcontentloaded'});
   await page.evaluate(()=>document.fonts.ready);
   console.log(`${mode} ${pathname} ${width}: loaded`);
   await page.evaluate(()=>document.querySelectorAll('img[loading="lazy"]').forEach(i=>i.loading='eager'));
   await page.waitForFunction(()=>[...document.images].every(i=>i.complete&&i.naturalWidth>0));
   await page.evaluate(()=>scrollTo({top:0,behavior:'instant'}));
   const measured=await page.evaluate(()=>({
    width:innerWidth,documentWidth:document.documentElement.scrollWidth,
    title:document.title,h1:document.querySelector('h1').innerText,
    appLinks:[...document.querySelectorAll('a[href="/widget/"]')].map(e=>e.textContent.trim()||e.getAttribute('aria-label')),
    brokenAnchors:[...document.querySelectorAll('a[href^="#"]')].map(e=>e.getAttribute('href')).filter(h=>h.length>1&&!document.getElementById(h.slice(1))),
    images:[...document.images].map(i=>({src:new URL(i.src).pathname,loaded:i.complete&&i.naturalWidth>0})),
    downloadLinks:[...document.querySelectorAll('a[download]')].map(e=>e.href),
    ctas:[...document.querySelectorAll('#download,.nav__download,.app-promo .btn')].map(e=>{const r=e.getBoundingClientRect();return {text:e.innerText,x:r.x,width:r.width,height:r.height}}),
    heroHeight:document.querySelector('.hero').getBoundingClientRect().height,
    sourceNav:!!document.querySelector('nav a[href="#source"]'),
    sourceFooter:!!document.querySelector('footer a[href="https://github.com/Dr-Inker/bert-widget"]'),
    artWidth:document.querySelector('.app-stage,.app-promo__art').getBoundingClientRect().width,
    artHeight:document.querySelector('.app-stage,.app-promo__art').getBoundingClientRect().height,
   }));
   if(measured.documentWidth!==width)console.log(await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(e=>e.getBoundingClientRect().right>innerWidth+.5&&e.getBoundingClientRect().right<innerWidth+100).map(e=>({tag:e.tagName,cls:e.className,right:e.getBoundingClientRect().right,text:e.textContent.slice(0,80)})).slice(0,15)));
   assert.equal(measured.documentWidth,width,`${pathname} overflow at ${width}`);
   assert.deepEqual(measured.brokenAnchors,[],'broken anchors');assert.deepEqual(errors,[]);
   assert(measured.ctas.every(r=>r.height>=44&&r.x>=0&&r.x+r.width<=width+.5),'CTA bounds');
   if(pathname==='/widget/'){
    assert.equal(measured.title,'BERT — your Android app, artwork and widgets');
    assert(measured.downloadLinks.length===6&&measured.downloadLinks.every(h=>h==='https://berthalla.io/widget/download/bert-widget-0.5.0.apk?sha=c47fbee4'));
    assert.equal(await page.locator('[data-checksum]').textContent(),'c47fbee48bc37db5d0974fa453825f34288d9d9983a603033edafcc5040df4e0');
    assert(measured.sourceNav&&measured.sourceFooter);
    assert.equal(await page.locator('.app-feature').count(),4);
    assert.equal(await page.locator('.sizes .widget-card').count(),2);
    assert.equal(await page.locator('.theme-card').count(),3);
    await page.getByText('Can I make and save cards offline?',{exact:true}).click();
    assert(await page.locator('details[open]').filter({hasText:'Can I make'}).count()===1);
   }else{
    assert(measured.appLinks.length>=4&&measured.appLinks.every(x=>! /widget/i.test(x)));
    assert.equal(await page.locator('#music').count(),1);
    assert.equal(await page.locator('.hero__video').count(),1);
    assert.equal(await page.locator('#unmute').count(),1);
    if(width<=620)assert(measured.heroHeight>=700&&measured.heroHeight<=790);
   }
   await page.evaluate(()=>scrollTo({top:0,behavior:'instant'}));
   const label=pathname==='/'?'home':'app';
   await page.screenshot({path:`${out}/${mode}-${label}-${width}.png`,fullPage:true});
   if(width===1440||width===390){
    await page.locator(pathname==='/'?'.app-promo':'.hero').screenshot({path:`${out}/${mode}-${label}-${width}-feature.png`});
    if(pathname==='/widget/')await page.locator('.app-inside').screenshot({path:`${out}/${mode}-inside-${width}.png`});
   }
   results.push({pathname,...measured,errors});await page.close();
  }
  const page=await browser.newPage({javaScriptEnabled:false,viewport:{width:390,height:844}});
  if(mode!=='public')await page.route('https://berthalla.io/**',async route=>{ let name=new URL(route.request().url()).pathname; if(name.endsWith('/'))name+='index.html'; const file=fs.existsSync(path.join(contentRoot,name))?path.join(contentRoot,name):path.join('/opt/berthalla/website',name); if(fs.existsSync(file))await route.fulfill({path:file,contentType:mime[path.extname(file)]||'application/octet-stream'});else await route.continue(); });
  await page.goto('https://berthalla.io/widget/',{waitUntil:'domcontentloaded'});
  assert.equal(await page.locator('[data-checksum]').textContent(),'c47fbee48bc37db5d0974fa453825f34288d9d9983a603033edafcc5040df4e0');
  assert.equal(await page.locator('#download').getAttribute('href'),'/widget/download/bert-widget-0.5.0.apk?sha=c47fbee4');
  await page.close();
 }finally{await browser.close()}
 const sha=child.execFileSync('git',['-c','safe.directory=/opt/berthalla','-C','/opt/berthalla','rev-parse','HEAD'],{encoding:'utf8'}).trim();
 const hashes=Object.fromEntries(['index.html','style.css','widget/index.html','widget/widget.css'].map(f=>[f,crypto.createHash('sha256').update(fs.readFileSync(`${contentRoot}/${f}`)).digest('hex')]));
 fs.writeFileSync(`${out}/${mode}-site.json`,JSON.stringify({mode,websiteSha:sha,hashes,noJavaScriptDownload:true,results},null,2)+'\n');
 console.log(JSON.stringify({mode,websiteSha:sha,viewports:results.map(r=>({path:r.pathname,width:r.width,overflow:r.documentWidth-r.width})),noJavaScriptDownload:true}));
})().catch(e=>{console.error(e);process.exit(1)});
