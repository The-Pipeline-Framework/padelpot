#!/usr/bin/env node
// Actual UI gestures, real provider calls, and read-only API assertions. No fixture fallback.
import { parseArgs } from 'node:util';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
const {values: options} = parseArgs({options: {
  base: {type: 'string', default: 'http://127.0.0.1:8080'},
  output: {type: 'string', default: resolve(root, 'output/browser-evidence')},
  'playwright-module': {type: 'string', default: resolve(root, 'output/browser-tools/node_modules/playwright/index.mjs')},
  'browser-executable': {type: 'string'},
  'record-video': {type: 'boolean', default: false}
}});
const { chromium } = await import(pathToFileURL(resolve(options['playwright-module'])).href);
import { mkdir, writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

const base = options.base.replace(/\/$/, '');
const output = resolve(options.output);
await mkdir(output, {recursive: true});
const recording = options['record-video'];
const browser = await chromium.launch({headless: true, executablePath: options['browser-executable'], slowMo: recording ? 120 : 0});
const context = await browser.newContext({viewport: {width:1300,height:1000}, timezoneId:'Europe/Madrid',
  ...(recording ? {recordVideo: {dir: output, size: {width:1300,height:1000}}} : {})});
const page = await context.newPage();
const video = page.video();
let passed = false;
const messages=[], checkpoints=[], approved=new Set(), errors=[];
const names={alba:'Alba Sol',nico:'Nico Rios',luz:'Luz Vega',teo:'Teo Mar'};
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
page.on('pageerror', e=>errors.push(e.message));
page.on('request', request=>{
  if(request.method()==='POST' && request.url().endsWith('/api/messages'))
    messages.push({submittedAt:new Date().toISOString(),body:request.postDataJSON()});
});
async function api(path) {
  const response=await page.request.get(base+'/api'+path);
  assert.ok(response.ok(), path+' returned '+response.status());
  return response.json();
}
async function audit() {
  const view=await api('/view');
  assert.ok(view.matches.length<=1);
  const match=view.matches[0];
  if(match) {
    for(const owner of Object.keys(match.players)) assert.ok(approved.has(owner),'Unauthorized participation: '+owner);
    if(match.status==='CONFIRMED') assert.equal(approved.size,4,'Confirmed before four owner approvals');
  }
  return match||null;
}
async function select(owner) {
  await page.locator('#owners').getByRole('button',{name:new RegExp(names[owner])}).click();
  await page.waitForFunction(name=>document.querySelector('#owner-name').textContent.startsWith(name),names[owner]);
  if(recording) await delay(1200);
}
async function type(text) {
  if(recording) {
    await page.locator('#message').pressSequentially(text,{delay:80});
    await delay(700);
  } else await page.locator('#message').fill(text);
  await page.locator('#send').click();
}
async function pending(owner) {
  for(let i=0;i<600;i++) {
    const detail=await api('/owners/'+owner);
    assert.ok(!['FAILED','DLQ'].includes(detail.latestRequest?.status),'Failed execution for '+owner+': '+JSON.stringify(detail.latestRequest));
    await audit();
    if(detail.pending.length) {
      const review=detail.pending[0];
      await page.locator('#decision').getByText(review.prompt,{exact:true}).waitFor({timeout:10000});
      return review;
    }
    assert.ok(detail.latestRequest?.status!=='SUCCEEDED','Ended without an approval for '+owner+': '+JSON.stringify(detail.messages.at(-1)));
    await delay(200);
  }
  throw new Error('Negotiation deadline for '+owner);
}
async function approve(owner,review,viaText=false) {
  const before=await audit();
  assert.ok(!before?.players[owner]);
  assert.notEqual(before?.status,'CONFIRMED');
  const count=messages.length;
  await delay(recording ? 4500 : 1200);
  assert.equal(messages.length,count,'Rendering sent a message without human action');
  assert.deepEqual(await audit(),before,'Waiting changed participation');
  checkpoints.push({ownerId:owner,kind:review.kind,interactionId:review.id,executionId:review.executionId,prompt:review.prompt,beforeApproval:before});
  // Authorize only the imminent human gesture; inspect the resulting correlated channel message.
  approved.add(owner);
  if(viaText) await type('approve');
  else await page.locator('#decision').getByRole('button',{name:'Approve participation',exact:true}).click();
  for(let i=0;i<300;i++) {
    const detail=await api('/owners/'+owner);
    assert.ok(!['FAILED','DLQ'].includes(detail.latestRequest?.status));
    const match=await audit();
    if(detail.latestRequest?.status==='SUCCEEDED' && match?.players[owner]) {
      const sent=messages.filter(m=>m.body.interactionId===review.id);
      assert.equal(sent.length,1);
      assert.equal(sent[0].body.ownerId,owner);
      assert.ok(sent[0].body.choice==='approve'||sent[0].body.text==='approve');
      console.log(owner+': real UI approval committed '+match.teams[owner]+'-'+match.players[owner]);
      if(recording) await delay(2500);
      return;
    }
    await delay(200);
  }
  throw new Error('Commit deadline for '+owner);
}
try {
  const initial = await api('/developer');
  assert.match(initial.provider, /Gemma 4.*OpenRouter/, 'This test requires real Gemma 4 through OpenRouter.');
  assert.equal(initial.events.filter(e=>e.type==='ROOT_STARTED').length, 0, 'Use a fresh test server.');
  await page.goto(base);
  await page.locator('#suggestions').getByRole('button',{name:'Open Thursday at 19:00',exact:true}).waitFor();
  assert.equal((await api('/view')).matches.length,0);
  if(recording) await delay(2500);
  await page.locator('#suggestions').getByRole('button',{name:'Open Thursday at 19:00',exact:true}).click();
  if(recording) await delay(1500);
  await page.locator('#send').click();
  const alba=await pending('alba'); assert.equal(alba.kind,'open'); await approve('alba',alba);
  for(const owner of ['nico','luz','teo']) {
    await select(owner);
    await page.locator('#suggestions').getByRole('button',{name:'Find me a match',exact:true}).click();
    if(recording) await delay(1500);
    await page.locator('#send').click();
    let review=await pending(owner);
    if(owner==='nico') {
      assert.equal(review.kind,'clarify');
      const before=await audit();
      if(recording) await delay(4500);
      await type("I'm flexible");
      for(let i=0;i<300;i++) {
        const d=await api('/owners/nico');
        assert.ok(!['FAILED','DLQ'].includes(d.latestRequest?.status));
        assert.deepEqual(await audit(),before,'Time clarification committed participation');
        if(!d.pending.some(p=>p.id===review.id)) break;
        await delay(200);
      }
      review=await pending(owner);
      console.log('nico: typed time permission resumed negotiation without joining');
    }
    assert.equal(review.kind,'participation');
    if(owner==='teo') {
      // Owner switching and delayed rendering must not manufacture the fourth approval.
      const before=await audit();
      await select('alba'); await delay(recording ? 2500 : 1500); await select('teo');
      assert.deepEqual(await audit(),before);
      assert.equal(Object.keys(before.players).length,3);
      assert.equal(before.status,'OPEN');
    }
    await approve(owner,review,owner==='luz');
  }
  const match=await audit();
  assert.equal(match.status,'CONFIRMED'); assert.equal(match.rosterValid,true);
  for(const team of ['A','B'])
    assert.deepEqual(Object.entries(match.players).filter(([id])=>match.teams[id]===team).map(([,side])=>side).sort(),['left','right']);
  const developer=await api('/developer');
  assert.match(developer.provider,/Gemma 4.*OpenRouter/);
  assert.equal(developer.events.filter(e=>e.type==='ROOT_STARTED').length,4);
  assert.equal(developer.events.filter(e=>e.type==='HUMAN_APPROVED').length,4);
  assert.equal(messages.filter(m=>!m.body.interactionId).length,4);
  assert.equal(messages.filter(m=>m.body.interactionId).length,5);
  assert.deepEqual(errors,[]);
  const conversations={};
  for(const owner of Object.keys(names)) conversations[owner]=await api('/owners/'+owner);
  await select('alba');
  await page.waitForFunction(()=>document.querySelector('.match-card .status')?.textContent.includes('confirmed'));
  if(recording) await delay(6000);
  await page.screenshot({path:output+'/padelpot-real-gemma.jpg',type:'jpeg',quality:90,fullPage:true});
  await writeFile(output+'/browser-evidence.json',JSON.stringify({result:'PASS',provider:developer.provider,match,checkpoints,channelMessages:messages,conversations,businessEvents:developer.events,errors},null,2));
  passed = true;
  console.log('PASS: real Gemma through actual web chat; four roots, five correlated replies, four explicit approvals, two valid teams.');
} catch(error) {
  await page.screenshot({path:output+'/failure.png',fullPage:true}).catch(()=>{});
  await writeFile(output+'/browser-failure.json',JSON.stringify({result:'FAIL',error:String(error),messages,checkpoints,view:await api('/view').catch(()=>null),developer:await api('/developer').catch(()=>null)},null,2));
  throw error;
} finally {
  await context.close();
  try {
    if(video) {
      const destination=resolve(output, passed ? 'padelpot-happy-path.webm' : 'failed-run.webm');
      await video.saveAs(destination);
      await video.delete();
      console.log('Screen recording: '+destination);
    }
  } finally {await browser.close();}
}
