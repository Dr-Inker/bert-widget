const { chromium } = require('/opt/facelift/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = '/opt/bert-widget-qa/release-0.8.0';
const mode = process.argv[2];
assert(['staged', 'public'].includes(mode));
const release = JSON.parse(fs.readFileSync(`${root}/staged/release.json`));
(async () => {
  const browser = await chromium.launch({headless:true, args:['--no-sandbox','--disable-dev-shm-usage']});
  const rows = [];
  try {
    for (const width of [1440, 768, 390, 320]) {
      const page = await browser.newPage({viewport:{width,height:900},deviceScaleFactor:1});
      const errors = [];
      page.on('pageerror', err => errors.push(err.message));
      if (mode === 'staged') await page.route('https://berthalla.io/**', async route => {
        const pathname = new URL(route.request().url()).pathname;
        const name = pathname === '/app/' ? 'index.html' : path.basename(pathname);
        if (['index.html', 'widget.js', 'widget.css', 'release.json', 'app-explore.png', 'app-tools.png'].includes(name)) {
          await route.fulfill({path:`${root}/staged/${name}`, contentType:name.endsWith('.html')?'text/html':name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':name.endsWith('.png')?'image/png':'application/json'});
        } else await route.continue();
      });
      await page.goto('https://berthalla.io/app/', {waitUntil:'networkidle'});
      await page.evaluate(() => document.fonts.ready);
      const measured = await page.evaluate(() => ({
        width:innerWidth, documentWidth:document.documentElement.scrollWidth,
        versions:[...document.querySelectorAll('[data-release-version]')].map(e=>e.textContent),
        links:[...document.querySelectorAll('a[download]')].map(e=>e.href),
        checksum:document.querySelector('[data-checksum]').textContent,
        code:document.querySelector('[data-version-code]').textContent,
        date:document.querySelector('[data-release-date]').getAttribute('datetime'),
        sourceNavigation:!!document.querySelector('nav a[href="#source"]'),
        sourceFooter:!!document.querySelector('footer a[href="https://github.com/Dr-Inker/bert-widget"]'),
        sourceLinks:document.querySelectorAll('a[href="https://github.com/Dr-Inker/bert-widget"]').length,
        cta:(()=>{const r=document.querySelector('#download').getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height};})()
      }));
      assert.equal(measured.documentWidth,width,'horizontal overflow');
      assert.match(await page.locator('.app-inside').innerText(),/The Lost Trail/);
      assert.match(await page.locator('.app-inside').innerText(),/offline/);
      assert(measured.versions.length > 0 && measured.versions.every(v=>v===release.version));
      assert(measured.links.length >= 6 && measured.links.every(v=>v===release.download));
      assert.equal(measured.checksum,release.sha256);
      assert.equal(measured.code,String(release.versionCode));
      assert.equal(measured.date,release.releaseDate);
      assert(measured.sourceLinks >= 2 && measured.sourceNavigation && measured.sourceFooter);
      assert(measured.cta.x >= 0 && measured.cta.x + measured.cta.width <= width);
      assert(measured.cta.height >= 44);
      assert.deepEqual(errors,[]);
      const capture = `${mode}-${width}.png`;
      await page.screenshot({path:`${root}/${capture}`,fullPage:true});
      rows.push({...measured,errors,capture});
      await page.close();
    }
  } finally {await browser.close();}
  const websiteRoot = mode === 'staged' ? '/opt/bert-widget-site-release' : '/opt/berthalla';
  const websiteSha = require('node:child_process').execFileSync('git',['-c',`safe.directory=${websiteRoot}`,'-C',websiteRoot,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
  const result = {mode, websiteSha, sourceSha:fs.readFileSync(`${root}/source-sha.txt`,'utf8').trim(), release, rows};
  fs.writeFileSync(`${root}/${mode}-page.json`,JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify({mode, widths:rows.map(r=>r.width), horizontalOverflow:rows.map(r=>r.documentWidth-r.width), downloads:rows[0].links.length}));
})().catch(e=>{console.error(e);process.exit(1);});
