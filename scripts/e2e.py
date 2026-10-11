#!/usr/bin/env python3
"""Named real-inference scenario. Only owner choices are fixtures; agents use the selected model."""
import argparse, json, pathlib, time, urllib.request, urllib.error, uuid, hashlib
ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--base', default='http://127.0.0.1:8080')
parser.add_argument('--replay', type=pathlib.Path, default=ROOT/'output/tpf-replay')
parser.add_argument('--output', type=pathlib.Path, default=ROOT/'output/evidence')
parser.add_argument('--allow-fixture', action='store_true', help='Explicit business fixture proof, never counted as real inference')
args = parser.parse_args()

def api(path, body=None, method=None):
    req = urllib.request.Request(args.base+'/api'+path, data=json.dumps(body).encode() if body is not None else None,
        headers={'Content-Type':'application/json'}, method=method or ('POST' if body is not None else 'GET'))
    try:
        with urllib.request.urlopen(req, timeout=15) as response: return json.load(response)
    except urllib.error.HTTPError as error:
        raise AssertionError(f'{path}: HTTP {error.code}: {error.read().decode()}') from error

def files(): return {p.name for p in args.replay.glob('*.json') if 'await-control' not in p.name}

def request_and_review(owner, text, expected):
    before=files()
    message_id=str(uuid.uuid4())
    accepted=api('/messages',{'ownerId':owner,'messageId':message_id,'text':text})
    execution=accepted['executionId']; choices=[]; seen=set(); start=time.monotonic()
    print(f'{owner}: agents negotiating ({execution})', flush=True)
    while time.monotonic()-start<240:
        state=api('/executions/'+execution)
        assert state['status'] not in ('FAILED','DLQ'), f'{owner}: inference/execution failed: {state}'
        detail=api('/owners/'+owner)
        pending=[p for p in detail['pending'] if p['executionId']==execution]
        for p in pending:
            if p['id'] in seen: continue
            seen.add(p['id'])
            assert p['kind'] in expected, f'{owner}: unexpected checkpoint {p}'
            # These are explicit synthetic HUMAN responses through the same channel adapter as the UI.
            reply={'ownerId':owner,'messageId':str(uuid.uuid4()),'text':'','interactionId':p['id'],'choice':'approve'}
            choices.append({'interactionId':p['id'],'kind':p['kind'],'ownerId':owner,'choice':'approve'})
            print(f'{owner}: human approves {p["kind"]}', flush=True)
            api('/messages',reply)
        if state['status']=='SUCCEEDED':
            assert choices, f'{owner}: completed without the required human checkpoint: {detail["messages"][-1]}'
            time.sleep(.2)
            return {'ownerId':owner,'requestId':message_id,'executionId':execution,'humanReplies':choices,'nativeSegments':sorted(files()-before),'status':state['status']}
        time.sleep(.3)
    raise AssertionError(f'{owner}: scenario deadline exceeded')

started=time.time()
provider=api('/developer')['provider']
assert args.allow_fixture or ('Gemma' in provider and 'fixture' not in provider.lower()), f'Real inference required, got {provider}'
assert not list(args.replay.glob('*.json')), 'Start a fresh process with an empty replay directory for unambiguous lineage.'
api('/demo/reset',{'onboarding':False})
roots=[]
roots.append(request_and_review('alba','Open a competitive doubles match this Thursday at 19:00 in Barcelona. I play left.',['open']))
match=api('/view')['matches'][0]
assert len(match['players'])==1 and match['organizer']=='alba'
for owner in ('nico','luz','teo'):
    roots.append(request_and_review(owner,'Find me a competitive match this Thursday after work. Negotiate a feasible court side using available vacancies.',['clarify','participation','disclosure']))
    current=api('/view')['matches'][0]
    assert owner in current['players'], f'{owner}: no valid participation after model negotiation: {api("/owners/"+owner)["messages"][-1]}'
match=api('/view')['matches'][0]
assert match['status']=='CONFIRMED' and len(match['players'])==4
assert list(match['players'].values()).count('left')==2 and list(match['players'].values()).count('right')==2
for team in ('A', 'B'):
    assert sorted(side for owner,side in match['players'].items() if match['teams'].get(owner)==team)==['left','right']
assert any(p['kind']=='clarify' for p in roots[1]['humanReplies']), 'Nico must explicitly authorize the 20:00 → 19:00 compromise.'
assert sum(p['kind'] in ('open','participation') for r in roots for p in r['humanReplies'])==4, 'Four humans must approve their own participation.'
before_play=files()
played=api('/matches/'+match['id']+'/simulate',{'ownerId':'alba'})
time.sleep(.2)
roots.append({'ownerId':'alba','executionId':played['executionId'],'humanReplies':[],
    'nativeSegments':sorted(files()-before_play),'status':'SUCCEEDED','stimulus':'simulate-play'})
roots.append(request_and_review('luz','Post-match feedback on our simulated match: I enjoyed the friendly pace. Please infer that I may prefer social matches and propose that style change for review. This is an inference from feedback.',['inference']))
persona=api('/owners/luz')['persona']
assert persona['style']['value']=='social' and persona['style']['source']=='inferred'
assert persona['level']['source']=='declared' and persona['lastPlayedHour']['source']=='observed'
dev=api('/developer'); starts=[e for e in dev['events'] if e['type']=='ROOT_STARTED']
assert len(starts)==6 and len({r['executionId'] for r in roots})==6, 'Exactly six roots, no roots from internal recursion or human replies.'
assert all(api('/executions/'+r['executionId'])['status']=='SUCCEEDED' for r in roots)
# Native exporter is per segment plus a shared Await control stream, verified against 26.9.4 source.
# Preserve original native documents; the dataset adds grouping only, never substitutes events/topology.
controls=sorted(p.name for p in args.replay.glob('*await-control*.json'))
assert len(controls)==1
await_count=sum(len(r['humanReplies']) for r in roots)
assert len(files())==6+await_count, f'Expected {6+await_count} native segments, found {len(files())}'
assert len(list(args.replay.glob('*.json')))==6+await_count+1
args.output.mkdir(parents=True, exist_ok=True)
(args.output/'native').mkdir(exist_ok=True); (args.output/'roots').mkdir(exist_ok=True)
native={p.name:json.loads(p.read_text()) for p in args.replay.glob('*.json')}
control_events=native[controls[0]]['events']
native_root_ids={e.get('attributes',{}).get('tpf.await.execution_id') for e in control_events}; native_root_ids.discard(None)
assert native_root_ids=={r['executionId'] for r in roots if r['humanReplies']}, 'Native Await lineage must match the five conversational roots.'
for name,d in native.items():
    content=json.dumps(d)
    assert 'PRIVATE_CANARY_' not in content, 'Protected persona data must never enter replay.'
    assert d.get('topology') and d.get('events'), f'Missing native telemetry: {name}'
    (args.output/'native'/name).write_bytes((args.replay/name).read_bytes())
for r in roots:
    r['nativeControlDocument']=controls[0]
    # Exact native documents embedded unchanged; lifecycle control document is referenced unchanged.
    doc={**r,'nativeDocuments':[native[name] for name in r['nativeSegments']]}
    (args.output/'roots'/(r['executionId']+'.json')).write_text(json.dumps(doc,indent=2))
assert len(list((args.output/'roots').glob('*.json')))==6
manifest={'scenario':'fourOwnersNegotiateAndLearn','result':'PASS','provider':provider,'tpfVersion':'26.9.4','startedAtEpoch':started,'finishedAtEpoch':time.time(),'expectedRoots':6,'actualRoots':len(roots),'nativeSegmentCount':len(files()),'nativeControlCount':len(controls),'humanReplies':await_count,'roots':roots,'match':api('/view')['matches'][0],'approvedPersona':{k:persona[k] for k in ('style','level','lastPlayedHour')},'businessEvents':dev['events'],'nativeDocuments':[{ 'file':name,'sha256':hashlib.sha256((args.replay/name).read_bytes()).hexdigest()} for name in sorted(native)],'limitations':['Automated proof simulates explicit human replies; interactive UI requires the owner to click.','Native replay exports segments plus Await control events; grouped root files preserve exact native documents.','Deterministic fixtures do not validate model inference.' if 'fixture' in provider.lower() else 'OpenRouter was not exercised when provider is Ollama.']}
(args.output/'demo-execution-dataset.json').write_text(json.dumps(manifest,indent=2))
print(f'PASS: four owners, provider={provider}, six roots, {await_count} human replies, {len(native)} native documents.',flush=True)
