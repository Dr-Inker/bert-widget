const {chromium}=require('/opt/facelift/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const root=process.env.APP_ROUTE_EVIDENCE||'/opt/bert-widget-qa/app-route-migration';
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});const rows=[];
 try{
  for(const width of [1440,768,390,320]){
   const page=await browser.newPage({viewport:{width,height:900},reducedMotion:'reduce'});
   const errors=[];page.on('pageerror',e=>errors.push(e.message));
   const response=await page.goto('https://berthalla.io/app/',{waitUntil:'domcontentloaded'});assert.equal(response.status(),200);
   await page.evaluate(()=>document.fonts.ready);
   await page.evaluate(()=>document.querySelectorAll('img[loading="lazy"]').forEach(i=>i.loading='eager'));
   await page.waitForFunction(()=>[...document.images].every(i=>i.complete&&i.naturalWidth>0));
   const row=await page.evaluate(()=>({url:location.href,width:innerWidth,documentWidth:document.documentElement.scrollWidth,canonical:document.querySelector('link[rel="canonical"]')?.href,ogUrl:document.querySelector('meta[property="og:url"]')?.content,downloads:[...document.querySelectorAll('a[download]')].map(a=>a.href),checksum:document.querySelector('[data-checksum]').textContent,images:document.images.length}));
   assert.equal(row.url,'https://berthalla.io/app/');assert.equal(row.canonical,row.url);assert.equal(row.ogUrl,row.url);
   assert.equal(row.width,row.documentWidth);assert.equal(row.downloads.length,6);
   assert(row.downloads.every(h=>h==='https://berthalla.io/widget/download/bert-widget-0.5.0.apk?sha=c47fbee4'));
   assert.equal(row.checksum,'c47fbee48bc37db5d0974fa453825f34288d9d9983a603033edafcc5040df4e0');assert.deepEqual(errors,[]);
   if(width===390)await page.screenshot({path:`${root}/app-390.png`,fullPage:true});
   rows.push({...row,errors});await page.close();
  }
  const page=await browser.newPage({javaScriptEnabled:false});await page.goto('https://berthalla.io/app/',{waitUntil:'domcontentloaded'});
  assert.equal(await page.locator('#download').getAttribute('href'),'/widget/download/bert-widget-0.5.0.apk?sha=c47fbee4');await page.close();
 }finally{await browser.close()}
 fs.writeFileSync(`${root}/page-results.json`,JSON.stringify({rows,noJavaScriptDownload:true},null,2)+'\n');
 console.log(JSON.stringify({canonical:'https://berthalla.io/app/',widths:rows.map(r=>r.width),overflow:rows.map(r=>r.documentWidth-r.width),downloads:6,noJavaScriptDownload:true}));
})().catch(e=>{console.error(e);process.exit(1)});
