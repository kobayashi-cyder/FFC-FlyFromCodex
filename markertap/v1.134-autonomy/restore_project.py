from pathlib import Path, PurePosixPath
import hashlib, json, zipfile, stat, importlib.util, shutil, sys

ROOT=Path(__file__).resolve().parents[2]
PROJECT=Path(sys.argv[1]).resolve()
if PROJECT.exists() and any(PROJECT.iterdir()): raise RuntimeError('Destination must be empty')
HERE=ROOT/'markertap/v1.134-autonomy'

def once(path,before,after):
    text=path.read_text()
    assert text.count(before)==1,(str(path),text.count(before),before[:100])
    path.write_text(text.replace(before,after))

manifest=json.loads((ROOT/'markertap/v1.130-crashfix/source-manifest.json').read_text())
PROJECT.parent.mkdir(parents=True,exist_ok=True)
archive=PROJECT.parent/'runtime-foundation.zip'
with archive.open('wb') as out:
    for item in manifest['parts']:
        data=(ROOT/'markertap/v1.130-crashfix'/item['name']).read_bytes()
        assert len(data)==item['size_bytes'] and hashlib.sha256(data).hexdigest()==item['sha256']
        out.write(data)
assert hashlib.sha256(archive.read_bytes()).hexdigest()==manifest['sha256']
with zipfile.ZipFile(archive) as z:
    for item in z.infolist():
        p=PurePosixPath(item.filename)
        assert not p.is_absolute() and '..' not in p.parts and not stat.S_ISLNK(item.external_attr>>16)
    z.extractall(PROJECT)
for item in json.loads((ROOT/'markertap/v1.132-stability/overlay-manifest.json').read_text()):
    p=ROOT/'markertap/v1.132-stability/overlay'/item['path']
    assert hashlib.sha256(p.read_bytes()).hexdigest()==item['sha256']
    dst=PROJECT/item['path'];dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dst)
spec=importlib.util.spec_from_file_location('v1133_apply',ROOT/'markertap/v1.133-stability/apply.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);m.apply(PROJECT)

p=PROJECT/'src/jp/tapmarker/FrameCircuit.java'
once(p,'import java.util.function.*;','import java.util.function.*;\nimport java.util.concurrent.locks.ReentrantLock;')
once(p,'private BooleanSupplier valid;private int reads;','private BooleanSupplier valid;private Clock clock;private int reads;')
once(p,'throw new java.util.concurrent.CancellationException("analysis superseded");}',
    'throw new java.util.concurrent.CancellationException("analysis superseded");if(clock!=null&&!AnalysisBudget.fresh(capturedAt,clock.now()))throw new java.util.concurrent.CancellationException("frame freshness deadline exceeded");}')
once(p,'void close(){pixels=null;valid=null;}','void close(){pixels=null;valid=null;clock=null;}')
once(p,'final Workspace workspace=new Workspace();final Clock clock;','final Workspace workspace=new Workspace();final Clock clock;private final ReentrantLock lifecycle=new ReentrantLock();')
once(p,'/** One worker invokes the graph. Synchronization also enforces the lease if a caller errs. */\n synchronized void run(Request request){',
    '/** One worker owns the lease. Memory pressure must never wait for that worker. */\n void run(Request request){\n  lifecycle.lock();\n  try{')
once(p,'case FRESH:{long now=clock.now();signal=input.capturedAt>0&&now>=input.capturedAt&&now-input.capturedAt<=1500;',
    'case FRESH:{signal=AnalysisBudget.fresh(input.capturedAt,clock.now());')
once(p,'frame=new Frame(input,workspace.pixels);frame.valid=request.valid;','frame=new Frame(input,workspace.pixels);frame.valid=request.valid;frame.clock=clock;')
once(p,'case RECHECK:signal=request.valid.getAsBoolean()&&!Thread.currentThread().isInterrupted();break;',
    'case RECHECK:signal=request.valid.getAsBoolean()&&!Thread.currentThread().isInterrupted();if(signal&&!AnalysisBudget.fresh(input.capturedAt,clock.now())){failure=new IllegalStateException("analysis exceeded frame freshness deadline");node=CANCEL;continue;}break;')
once(p,'case PUBLISH:{final Runnable result=publication;request.dispatcher.post(()->{if(request.valid.getAsBoolean())result.run();});completed++;break;}',
    'case PUBLISH:{final Runnable result=publication;final long capturedAt=input.capturedAt;request.dispatcher.post(()->{if(!request.valid.getAsBoolean())return;if(!AnalysisBudget.fresh(capturedAt,clock.now())){request.failed.accept(new IllegalStateException("result expired before delivery"));return;}result.run();});completed++;break;}')
once(p,'}finally{if(frame!=null)frame.close();if(input!=null)input.close();if(trimRequested){workspace.clear();trimRequested=false;}lastTrace=trace;retainedBytes=workspace.retainedBytes();}',
    '}finally{if(frame!=null)frame.close();try{if(input!=null)input.close();}finally{if(trimRequested){workspace.clear();trimRequested=false;}lastTrace=trace;retainedBytes=workspace.retainedBytes();}}\n  }finally{lifecycle.unlock();}')
once(p,'void requestTrim(){trimRequested=true;}\n synchronized void trim(){workspace.clear();retainedBytes=0;trimRequested=false;}',
    'void requestTrim(){\n  trimRequested=true;\n  // A reentrant call during ANALYZE only marks the workspace for retirement.\n  if(lifecycle.isHeldByCurrentThread()||!lifecycle.tryLock())return;\n  try{workspace.clear();retainedBytes=0;trimRequested=false;}finally{lifecycle.unlock();}\n }\n void trim(){requestTrim();}')
p=PROJECT/'src/jp/tapmarker/AutonomySupervisor.java'
once(p,'if(good){','if(good&&AnalysisBudget.fresh(capturedAt,now)&&capturedAt>lastFrameAt){')
once(p,'}else fail("recognition",now);','}else fail(good?"invalid-frame":"recognition",now);')
once(p,'if(home){lastHomeAt=now;unhealthySince=-1;failStreak=0;lastFailure="";}',
    '// The timer may still hold yesterday\'s home flag while recognition is stuck.\n  // Only complete() with a distinct, fresh image can clear a failure episode.\n  if(home&&AnalysisBudget.fresh(lastFrameAt,now))lastHomeAt=now;')
once(p,'if(frameAt<=0||now-frameAt>FRAME_STALE_MS){','if(!AnalysisBudget.fresh(frameAt,now)){')
p=PROJECT/'src/jp/tapmarker/CaptureCircuit.java'
once(p,'The Android frame executor contains only one analysis worker. A producer for','The Android frame executor has two bounded analysis workers. A producer for')
once(p,' synchronized void reset(){inFlight.clear();}',
    ' /** Every delivered result, including duplicate-frame early returns, ends its lease. */\n Runnable releasing(int display,long ticket,Runnable publication){\n  if(publication==null)throw new IllegalArgumentException("publication");\n  return ()->{finish(display,ticket);publication.run();};\n }\n synchronized void reset(){inFlight.clear();}')
p=PROJECT/'src/jp/tapmarker/VirtualWindows.java'
once(p,'void trimFrameCircuit(){ArrayList<FrameCircuit> circuits=new ArrayList<>();for(Window w:windows){w.frameCircuit.requestTrim();circuits.add(w.frameCircuit);}try{frameWork.execute(()->{for(FrameCircuit circuit:circuits)circuit.trim();});}catch(RejectedExecutionException ignored){}}',
    'void trimFrameCircuit(){for(Window w:windows)w.frameCircuit.requestTrim();}')
p=PROJECT/'src/jp/tapmarker/SplitPilot.java'
once(p,'if(frame.capturedAt<=slot.lastFrameAt)return ()->{if(valid(t)&&slot.supervisor.current(analysisTicket)){slot.supervisor.invalidate();later(slot,t,100);}};',
    'if(frame.capturedAt<=slot.lastFrameAt)return frameAdmission.releasing(window.id,captureLease,()->{if(valid(t)&&slot.supervisor.current(analysisTicket)){slot.supervisor.invalidate();s.pilot.virtualWindows.requestFrameRefresh(window);later(slot,t,100);}});')
once(p,'return ()->{frameAdmission.finish(window.id,captureLease);if(!valid(t)||window.closed||!slot.supervisor.current(analysisTicket))return;',
    'return frameAdmission.releasing(window.id,captureLease,()->{if(!valid(t)||window.closed||!slot.supervisor.current(analysisTicket))return;')
once(p,'applySamples(slot,sampledRegions,new Rect(0,0,width,height),t);};','applySamples(slot,sampledRegions,new Rect(0,0,width,height),t);});')
once(PROJECT/'tools/package_apk.py','def manifest_version(data, code=136, version="1.133"):','def manifest_version(data, code=137, version="1.134"):')
once(PROJECT/'tools/PatchRuntime.java','if(text.equals("v1.123"))return "v1.133";','if(text.equals("v1.123"))return "v1.134";')
for name in ['build.sh','run-host-tests.sh']:
    p=PROJECT/name;p.write_text(p.read_text().replace('1.133','1.134').replace('versionCode136','versionCode137'))
once(PROJECT/'setup-toolchain.py','e862b9871770a9707bfee53689f53a60f71ac0d3d22dabf76ec9bb25dceef0a2','bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88')
for item in json.loads((HERE/'overlay-manifest.json').read_text()):
    relative=PurePosixPath(item['path'])
    if relative.is_absolute() or '..' in relative.parts: raise RuntimeError('Unsafe overlay')
    src=HERE/'overlay'/str(relative)
    if hashlib.sha256(src.read_bytes()).hexdigest()!=item['sha256']: raise RuntimeError('Overlay checksum mismatch')
    dst=PROJECT/str(relative)
    if str(relative).startswith('src/'):
        if dst.read_bytes()!=src.read_bytes(): raise RuntimeError('Production patch differs from reviewed source')
    else:
        dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dst)
p=PROJECT/'run-tests.sh'
p.write_text(p.read_text()+'\nfor test in FrameDeadline SupervisorFreshness CaptureLeaseCompletion; do\n java -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.${test}Test\ndone\n')
print('Restored v1.134 production fixes and regression gates')
