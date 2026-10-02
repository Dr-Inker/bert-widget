// Staged: serve the release worktree's /app/ page and release.json over the live site. Public: the live site.
const {chromium}=require('/opt/facelift/node_modules/playwright');
const assert=require('node:assert/strict');const fs=require('node:fs');
const root='/opt/bert-widget-qa/release-0.8.1';const site='/opt/bert-widget-site-release/website';
const mode=process.argv[2];assert(['staged','public'].includes(mode));
const release=JSON.parse(fs.readFileSync(`${root}/staged/release.json`));
const href=`/widget/download/bert-widget-${release.version}.apk?sha=${release.sha256.slice(0,8)}`;
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});const rows=[];
 try{
  for(const width of [1440,768,390,320]){
   const page=await browser.newPage({viewport:{width,height:900},reducedMotion:'reduce'});
   const errors=[];page.on('pageerror',e=>errors.push(e.message));
   if(mode==='staged'){
    await page.route('https://berthalla.io/app/',r=>r.fulfill({path:`${site}/app/index.html`,contentType:'text/html'}));
    await page.route('https://berthalla.io/widget/release.json*',r=>r.fulfill({path:`${site}/widget/release.json`,contentType:'application/json'}));
   }
   const response=await page.goto('https://berthalla.io/app/',{waitUntil:'networkidle'});assert.equal(response.status(),200);
   await page.evaluate(()=>document.fonts.ready);
   await page.evaluate(()=>document.querySelectorAll('img[loading="lazy"]').forEach(i=>i.loading='eager'));
   await page.waitForFunction(()=>[...document.images].every(i=>i.complete&&i.naturalWidth>0));
   const row=await page.evaluate(()=>({width:innerWidth,documentWidth:document.documentElement.scrollWidth,
     versions:[...new Set([...document.querySelectorAll('[data-release-version]')].map(e=>e.textContent))],
     code:document.querySelector('[data-version-code]')?.textContent,
     downloads:[...document.querySelectorAll('a[download]')].map(a=>a.getAttribute('href')),checksum:document.querySelector('[data-checksum]').textContent}));
   assert.equal(row.width,row.documentWidth,'horizontal overflow');assert.equal(row.downloads.length,6);
   assert(row.downloads.every(h=>h===href||h===release.download),JSON.stringify(row.downloads));
   assert.deepEqual(row.versions,[release.version]);assert.equal(row.code,String(release.versionCode));
   assert.equal(row.checksum,release.sha256);assert.deepEqual(errors,[]);
   await page.screenshot({path:`${root}/${mode}-${width}.png`,fullPage:false});
   rows.push({...row,errors});await page.close();
  }
 }finally{await browser.close()}
 fs.writeFileSync(`${root}/${mode}-page.json`,JSON.stringify({mode,release,rows},null,2)+'\n');
 console.log(JSON.stringify({mode,widths:rows.map(r=>r.width),overflow:rows.map(r=>r.documentWidth-r.width),downloads:6,version:release.version}));
})().catch(e=>{console.error(e);process.exit(1)});
