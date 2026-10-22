#!/usr/bin/env python3
"""Fail-closed v1.133 forward port; preserve the exact v1.132 source lineage."""
from pathlib import Path


def replace_once(path, before, after):
    src = Path(path).read_text(encoding="utf-8")
    if src.count(before) != 1:
        raise RuntimeError(f"{path}: expected one unchanged anchor, got {src.count(before)}")
    Path(path).write_text(src.replace(before, after), encoding="utf-8")


def apply(project):
    src = project / "src/jp/tapmarker/VirtualWindows.java"
    replace_once(src,
        ' final FrameCircuit frameCircuit=new FrameCircuit(SystemClock::uptimeMillis);\n final ThreadPoolExecutor frameWork=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(8),task->new Thread(task,"TapMarker-frame-circuit"));',
        ' // Separate mutable image analysis per display. No JNI or pixel work under the other display lock.\n final ThreadPoolExecutor frameWork=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(4),task->new Thread(task,"TapMarker-frame-circuit"));')
    replace_once(src, 'final Object frameLock=new Object();final DisplayLane inputLane',
        'final Object frameLock=new Object();final FrameCircuit frameCircuit=new FrameCircuit(SystemClock::uptimeMillis);final java.util.concurrent.atomic.AtomicBoolean analysisPending=new java.util.concurrent.atomic.AtomicBoolean();final DisplayLane inputLane')
    replace_once(src,
        'result.append("\\n処理回路：8 nodes / worker=1 / workspace=").append(String.format(java.util.Locale.ROOT,"%.2f MiB",frameCircuit.retainedBytes/1048576.0)).append(" / complete=").append(frameCircuit.completed).append(" / cancelled=").append(frameCircuit.cancelled)',
        'result.append(circuitStatus())')
    replace_once(src, ' String eventText(){',
        ' String circuitStatus(){long retained=0,completed=0,cancelled=0;for(Window w:windows){retained+=w.frameCircuit.retainedBytes;completed+=w.frameCircuit.completed;cancelled+=w.frameCircuit.cancelled;}return "\\n処理回路：8 nodes / worker=2 / workspace="+String.format(java.util.Locale.ROOT,"%.2f MiB",retained/1048576.0)+" / complete="+completed+" / cancelled="+cancelled;}\n String eventText(){')
    replace_once(src,
        '  try{frameWork.execute(()->frameCircuit.run(request));}catch(RejectedExecutionException stopped){if(current.getAsBoolean())failed.run();}',
        '  if(!w.analysisPending.compareAndSet(false,true)){s.h.postDelayed(()->{if(current.getAsBoolean())failed.run();},120);return;}\n  try{frameWork.execute(()->{try{w.frameCircuit.run(request);}finally{w.analysisPending.set(false);}});}catch(RejectedExecutionException stopped){w.analysisPending.set(false);if(current.getAsBoolean())failed.run();}')
    replace_once(src,
        ' void trimFrameCircuit(){frameCircuit.requestTrim();try{frameWork.execute(frameCircuit::trim);}catch(RejectedExecutionException ignored){}}',
        ' void trimFrameCircuit(){ArrayList<FrameCircuit> circuits=new ArrayList<>();for(Window w:windows){w.frameCircuit.requestTrim();circuits.add(w.frameCircuit);}try{frameWork.execute(()->{for(FrameCircuit circuit:circuits)circuit.trim();});}catch(RejectedExecutionException ignored){}}')
    replace_once(src, ' void closeViews(){generation++;for(Window w:new ArrayList<>(windows))',
        ' void closeViews(){generation++;trimFrameCircuit();for(Window w:new ArrayList<>(windows))')
    replace_once(src, 'windows.clear();try{trimFrameCircuit();}catch(Throwable ignored){}try{refreshHeadings();}',
        'windows.clear();try{refreshHeadings();}')

    test = project / "tests/FrameCircuitTest.java"
    insert = """
 static void independentWindows()throws Exception{
  FrameCircuit first=new FrameCircuit(()->1000),second=new FrameCircuit(()->1000);
  ExecutorService pool=Executors.newFixedThreadPool(2);
  CountDownLatch bothEntered=new CountDownLatch(2),release=new CountDownLatch(1);
  AtomicInteger done=new AtomicInteger();
  List<Future<?>> jobs=new ArrayList<>();
  for(int i=0;i<2;i++){
   final int color=0xff123456+i;
   final FrameCircuit circuit=i==0?first:second;
   jobs.add(pool.submit(()->circuit.run(request(source(20,20,color,1000),()->true,frame->{
    check(frame.get(0,0)==color,"isolated window retains correct image");
    bothEntered.countDown();
    try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("parallel test timed out");}
    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    return ()->done.incrementAndGet();
   },Runnable::run,failure->{throw new AssertionError(failure);}))));
  }
  try{check(bothEntered.await(2,TimeUnit.SECONDS),"two window circuits must analyze simultaneously, not serialize");}
  finally{release.countDown();}
  try{for(Future<?> job:jobs)job.get(3,TimeUnit.SECONDS);}
  finally{pool.shutdownNow();}
  check(done.get()==2&&first.completed==1&&second.completed==1,"both windows publish independently");
  check(first.workspace.allocations==1&&second.workspace.allocations==1,"each window has its own bounded workspace");
 }
"""
    replace_once(test, ' public static void main(String[] args)throws Exception{normal();cancellations();failures();concurrent();',
        insert + ' public static void main(String[] args)throws Exception{normal();cancellations();failures();concurrent();independentWindows();')

    replace_once(project / "tools/package_apk.py",
        'def manifest_version(data, code=135, version="1.132"):',
        'def manifest_version(data, code=136, version="1.133"):')
    replace_once(project / "tools/PatchRuntime.java",
        'if(text.equals("v1.123"))return "v1.132";',
        'if(text.equals("v1.123"))return "v1.133";')
    for name in ["build.sh", "run-host-tests.sh"]:
        path = project / name
        source = path.read_text(encoding="utf-8")
        if "1.132" not in source:
            raise RuntimeError(name + ": unexpected version lineage")
        path.write_text(source.replace("1.132", "1.133").replace("versionCode135", "versionCode136"), encoding="utf-8")
    # Existing v1.132 tests and signature checks remain mandatory.
    return ["virtual-window-isolation", "dual-circuit-parallel-regression", "manifest-1.133-code-136"]


if __name__ == "__main__":
    import sys
    print(apply(Path(sys.argv[1])))
