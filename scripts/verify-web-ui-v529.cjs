#!/usr/bin/env node
/* Render the real embedded UI at the four acceptance viewports and check its DOM geometry. */
const fs = require('fs');
const http = require('http');
const path = require('path');
const {chromium} = require('playwright');

const root = path.resolve(__dirname, '..');
function triple(file, marker) {
  const source = fs.readFileSync(file, 'utf8');
  const start = source.indexOf(marker);
  if (start < 0) throw new Error(`missing asset marker in ${file}`);
  const body = start + marker.length;
  const end = source.indexOf('\n""".trimIndent()', body);
  if (end < 0) throw new Error(`unterminated asset in ${file}`);
  return source.slice(body, end);
}
const web = path.join(root, 'app/src/main/java/com/example/familyphotoframe/web');
const css = triple(path.join(web, 'WebUiCss.kt'), 'val VALUE: String = """\n');
const js = triple(path.join(web, 'WebUiScript.kt'), 'val VALUE: String = """\n');
let html = triple(path.join(web, 'SetupPage.kt'), 'val HTML: String = """\n');
html = html.replace(/<link rel="stylesheet"[^>]+>/, `<style>${css}</style>`)
  .replace(/<script src="[^"]+" defer><\/script>/, `<script>${js}<\/script>`);

const settings = {playlists: [], brightnessPeriods: [], selectedFolders: [], transitionSelectionMode: 'fixed'};
const status = {appVersion:'0.12.13-prerelease',buildType:'debug',deviceModel:'Test frame',androidSdk:22,
  uptimeText:'1h',online:true,engineState:'RUNNING',sourceName:'SMB',heapUsedMb:42,heapMaxMb:100,
  pssMb:61,imageCacheMb:8,rollbackAvailable:false};
const diagnosticEvent = {schemaVersion:2,sequence:7,atEpochMs:1000,elapsedRealtimeMs:700,
  sessionId:'session-a',severity:'INFO',category:'SOURCE',code:'SOURCE_REFRESH_COMPLETED',origin:'WEB_UI',
  operationId:'operation-1',parentOperationId:null,message:'',fields:{trigger:'REBUILD_WEB_UI',outcome:'SUCCESS'}};
const responses = {
  '/api/v1/security/remembered-browser-policy/public': {data:{enabled:true,defaultExpiry:'SESSION_ONLY'}},
  '/api/v1/security/remembered-browser-policy': {data:{enabled:true,defaultExpiry:'SESSION_ONLY',allowForever:false,maxRememberedBrowsers:8,maxExpirySeconds:31536000}},
  '/api/v1/security/remembered-browsers': {data:[{id:'browser-lap',label:'lap',current:true,status:'ACTIVE',browserSummary:'Chrome browser',osSummary:'Linux',createdAtEpochMs:1000,lastUsedAtEpochMs:2000,expiresAtEpochMs:null}]},
  '/api/v1/settings': {revision:1,data:settings},
  '/api/v1/status': {data:status},
  '/api/v1/presentation/current': {data:{type:'SINGLE',fileName:'photo.jpg',folder:'folder',transition:'fade',committedAtEpochMs:1000,previewRevision:'preview-1'}},
  '/api/v1/diagnostics/summary': {data:{sessions:1}},
  '/api/v1/diagnostics/events': {data:{events:[diagnosticEvent],nextCursor:'session-a~7',hasMore:false,cursorExpired:false,
    summary:{sessions:1,crashes:0,anrs:0,processExits:0,scanFailures:0,sourceFailures:0,lowMemoryEvents:0,droppedEvents:0,sinkErrors:0,rotations:0,retainedBytes:2048,evidenceIncomplete:false},
    health:{queueDepth:0,queueCapacity:1024},warnings:[],operationTimeline:[{operationId:'operation-1',trigger:'REBUILD_WEB_UI',terminalCode:'SOURCE_REFRESH_COMPLETED',durationMs:700,incomplete:false,codes:['SOURCE_REFRESH_REQUESTED','SOURCE_REFRESH_COMPLETED']}],
    filterOptions:{severities:['INFO'],categories:['SOURCE'],sessions:['session-a'],codes:['SOURCE_REFRESH_COMPLETED'],triggers:['REBUILD_WEB_UI'],operations:['operation-1'],origins:['WEB_UI']}}},
};

const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1');
  if (url.pathname === '/') {
    response.writeHead(200, {'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});
    response.end(html); return;
  }
  if (url.pathname === '/api/v1/preview') {
    const jpeg=Buffer.from('/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAEf/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABD/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/EB//xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/EB//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/EB//2Q==','base64');
    response.writeHead(200, {'Content-Type':'image/jpeg','X-Preview-Revision':'preview-1','Cache-Control':'private, max-age=2'});
    response.end(jpeg); return;
  }
  const key = url.pathname;
  const body = responses[key] || {data:{}};
  response.writeHead(200, {'Content-Type':'application/json','Cache-Control':'no-store'});
  response.end(JSON.stringify(body));
});

function assert(condition, message) { if (!condition) throw new Error(message); }
async function selectTab(page, name) {
  await page.evaluate(tab => document.querySelector(`[data-tab-button="${tab}"]`).click(), name);
  await page.waitForTimeout(name === 'diagnostics' ? 150 : 30);
}
async function geometry(page, label) {
  const result = await page.evaluate(() => {
    const overflow = document.documentElement.scrollWidth - window.innerWidth;
    const escaped = Array.from(document.querySelectorAll('.card input,.card select,.card button')).filter(node => node.getClientRects().length > 0).filter(node => {
      const rect=node.getBoundingClientRect(), card=node.closest('.card').getBoundingClientRect();
      return rect.right > card.right + 1 || rect.left < card.left - 1;
    }).map(node => node.id || node.textContent.trim().slice(0,30));
    const cards = Array.from(document.querySelectorAll('.tab-page:not(.hidden) .card'));
    const overlaps=[];
    for(let i=0;i<cards.length;i++) for(let j=i+1;j<cards.length;j++) {
      if(cards[i].contains(cards[j])||cards[j].contains(cards[i])) continue;
      const a=cards[i].getBoundingClientRect(),b=cards[j].getBoundingClientRect();
      if(Math.min(a.right,b.right)-Math.max(a.left,b.left)>1&&Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>1) overlaps.push([i,j]);
    }
    return {overflow,escaped,overlaps};
  });
  assert(result.overflow <= 1, `${label}: page overflows horizontally by ${result.overflow}px`);
  assert(!result.escaped.length, `${label}: controls escaped cards: ${result.escaped.join(', ')}`);
  assert(!result.overlaps.length, `${label}: cards overlap: ${JSON.stringify(result.overlaps)}`);
}

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  const launchOptions = {headless:true};
  if (process.env.FPF_CHROMIUM_EXECUTABLE) launchOptions.executablePath = process.env.FPF_CHROMIUM_EXECUTABLE;
  const browser = await chromium.launch(launchOptions);
  const viewports = [[1440,900],[1024,768],[800,1280],[390,844]];
  try {
    for (const [width,height] of viewports) {
      const page = await browser.newPage({viewport:{width,height}});
      await page.addInitScript(({remembered}) => {sessionStorage.setItem('fpf.web.session','test');sessionStorage.setItem('fpf.web.csrf','test');if(remembered){localStorage.setItem('fpf.rememberedCredential','test-credential');localStorage.setItem('fpf.rememberedBrowserId','browser-lap')}else{localStorage.removeItem('fpf.rememberedCredential');localStorage.removeItem('fpf.rememberedBrowserId')}window.__previewFetches=0;const originalFetch=window.fetch;window.fetch=function(input,options){if(String(input).startsWith('/api/v1/preview'))window.__previewFetches++;return originalFetch.call(this,input,options)};}, {remembered:width===1440});
      await page.goto(`http://127.0.0.1:${port}/`, {waitUntil:'networkidle'});
      if(width===1440){const firstImage=await page.locator('#previewImage').elementHandle();await page.waitForTimeout(3250);const idle=await page.evaluate(image=>({same:image===document.getElementById('previewImage'),fetches:window.__previewFetches,revision:document.getElementById('previewImage').dataset.previewRevision}),firstImage);assert(idle.same,`${width}x${height}: status polling replaced the preview image DOM`);assert(idle.fetches===0,`${width}x${height}: idle page made ${idle.fetches} preview request(s)`);assert(!idle.revision,`${width}x${height}: preview appeared without a button press`);await page.locator('#getPictureButton').click();await page.waitForFunction(()=>document.getElementById('previewImage').dataset.previewRevision==='preview-1');const captured=await page.evaluate(()=>({fetches:window.__previewFetches,revision:document.getElementById('previewImage').dataset.previewRevision,button:document.getElementById('getPictureButton').textContent}));assert(captured.fetches===1,`${width}x${height}: one press made ${captured.fetches} preview requests`);assert(captured.revision==='preview-1',`${width}x${height}: requested preview revision was not applied`);assert(captured.button==='Get picture',`${width}x${height}: capture button did not return to idle`);}
      for (const tab of ['playback','device','diagnostics','backup','about']) {
        await selectTab(page, tab);
        await geometry(page, `${width}x${height} ${tab}`);
      }
      await selectTab(page,'playback');
      const playback = await page.evaluate(() => ({columns:document.querySelectorAll('.playback-column').length,cards:document.querySelectorAll('#tab-playback .playback-column>.card').length,grid:getComputedStyle(document.querySelector('.playback-layout')).gridTemplateColumns}));
      assert(playback.columns===2&&playback.cards===6,`${width}x${height}: playback did not preserve six cards in two stacks`);
      assert(width>=1200?playback.grid.split(' ').length===2:playback.grid.split(' ').length===1,`${width}x${height}: playback breakpoint incorrect (${playback.grid})`);
      await selectTab(page,'device');
      const device=await page.evaluate(()=>{var actions=Array.from(document.querySelectorAll('.remember-policy-actions>button')),list=document.querySelector('.remember-browser-list'),toolbar=document.querySelector('.remember-policy-actions'),forget=document.querySelector('.web-server-actions [data-action="logout-forget"]');return {servers:document.querySelectorAll('#tab-device>.web-server-card').length,remembered:document.querySelectorAll('.web-server-card .remembered-section').length,browserTiles:document.querySelectorAll('.remember-browser-list>.event-item').length,forgetButtons:document.querySelectorAll('.web-server-actions [data-action="logout-forget"]').length,forgetVisible:!!forget&&forget.getClientRects().length>0,actionTops:actions.map(n=>Math.round(n.getBoundingClientRect().top)),policyGap:list&&toolbar?Math.round(list.getBoundingClientRect().top-toolbar.getBoundingClientRect().bottom):0}});
      assert(device.servers===1&&device.remembered===1&&device.forgetButtons===1,`${width}x${height}: Web control hierarchy incorrect`);
      assert(device.browserTiles===1,`${width}x${height}: remembered browser tile is missing`);
      assert(device.forgetVisible===(width===1440),`${width}x${height}: hidden/visible forget-browser state is incorrect`);
      assert(device.policyGap>=20,`${width}x${height}: remembered browser list gap is only ${device.policyGap}px`);
      if(width>=1200){
        assert(new Set(device.actionTops).size===1,`${width}x${height}: security policy buttons are not on one line`);
      }
      await selectTab(page,'diagnostics');
      const diagnostics=await page.evaluate(()=>({actions:document.querySelectorAll('.event-actions>button').length,bottomActions:document.querySelectorAll('#eventList~.button-row').length,event:document.querySelectorAll('#tab-diagnostics .event-item').length,cards:document.querySelectorAll('.diagnostic-device-grid>.card').length,maintenance:document.querySelectorAll('.diagnostic-device-grid>.diagnostic-maintenance-card').length,firstTitle:(document.querySelector('.diagnostic-device-grid>.card h2')||{}).textContent||'',topSignout:document.querySelectorAll('.topbar-actions>[data-action="logout"]').length}));
      assert(diagnostics.actions===4&&!diagnostics.bottomActions&&diagnostics.event===1,`${width}x${height}: diagnostics toolbar/list contract incorrect`);
      assert(diagnostics.cards===3&&diagnostics.maintenance===1&&diagnostics.firstTitle==='Identity'&&diagnostics.topSignout===1,`${width}x${height}: Diagnostics top-card or header sign-out hierarchy incorrect`);
      await selectTab(page,'backup');
      assert(await page.locator('#tab-backup>.card').count()===1,`${width}x${height}: Backup is not one card`);
      await selectTab(page,'about');
      assert(await page.locator('#tab-about [data-action="download-log"]').count()===0,`${width}x${height}: About diagnostics button remains`);
      await page.close();
    }
    console.log('  rendered web UI passed 1440x900, 1024x768, 800x1280, and 390x844 geometry checks');
  } finally {
    await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => {console.error(`  ERROR: ${error.message}`);process.exit(1);});
