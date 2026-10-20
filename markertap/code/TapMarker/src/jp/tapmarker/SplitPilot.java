package jp.tapmarker;
import android.accessibilityservice.*;import android.graphics.*;import android.os.*;import android.content.*;import android.view.accessibility.*;import java.util.*;
import android.view.Display;
/** Independent engines/input lanes; virtual frames use one leased processing workspace. */
final class SplitPilot {
 final TapService s;volatile boolean active;boolean virtualMode,parallelVirtual;volatile int token,index;long lastShot;Slot[] slots;
 static final class Slot {final RewardReceipt receipt=new RewardReceipt();final FarmAdErrorRecovery farmAdError=new FarmAdErrorRecovery();final ContinuousTapSession mainTaps=new ContinuousTapSession();Tapper tapper;UpgradeActions upgrades;BancRuntime banc;LearnedLayout repairLayout=new LearnedLayout();LearnedLayout learned;AdaptiveLayout layout;VirtualPresetProfile virtualProfile;AppPhases phases;String pkg;boolean home,stopped;long checked,lastShot,processingMs,observedAt,lastHomeProbe;final ManualIntervention manual=new ManualIntervention();final DisplayRecovery displayRecovery=new DisplayRecovery();volatile long lastFrameAt;volatile String sampleError="";String recognition="未判定";boolean cyclePending;int scheduleVersion;int retries,missionTaps;RecoveryBudget recovery=new RecoveryBudget(),missionBudget=new RecoveryBudget(4);MissionJourney journey=new MissionJourney();CoinMissionJourney coinJourney=new CoinMissionJourney();AdInterruption interruption=new AdInterruption();AdExitPolicy adExit=new AdExitPolicy();Slot(TapService s,String pkg){this.pkg=pkg;phases=new AppPhases(s);phases.load(pkg);if(!phases.engine.windowRelative){if(pkg.equals(UsagiPreset.APP))phases.installUsagi();else if(pkg.equals(FarmPreset.APP))phases.installFarm();else if(pkg.equals(CoinPreset.APP))phases.installCoin();else if(pkg.equals(ShoppingPreset.APP))phases.installShopping();else if(pkg.equals(BoxPreset.APP))phases.installBox();else if(pkg.equals(PoipoiPreset.APP))phases.installPoipoi();}if(!phases.loadIssue.isEmpty())throw new IllegalArgumentException(phases.loadIssue);if(phases.engine.phases.isEmpty())throw new IllegalArgumentException(s.appLabel(pkg)+"：オートマトン未設定");phases.engine=phases.engine.runtimeCopy();CoinNoAdPolicy.apply(phases.engine);phases.runtimeOnly=true;learned=phases.engine.windowRelative?new LearnedLayout():null;layout=AdaptiveLayout.install(phases.engine,pkg,phases.references(PresetRegistry.asset(pkg)),phases.references("virtual-home-preset.json"));if(layout!=null){layout.relativeEnabled=!phases.customized;if(!phases.customized)layout.configureTargets(pkg,phases.references("visual-targets.json"));virtualProfile=layout.profile;if(!phases.customized)AdCloseAutomaton.install(phases.engine,pkg);}if(FarmPreset.APP.equals(pkg)&&!phases.customized&&!phases.engine.windowRelative)FarmControlEvidence.install(phases.engine);phases.engine.strictRuntime=true;phases.engine.current=phases.engine.initial;String error=phases.engine.validateStart();if(!error.isEmpty())throw new IllegalArgumentException(error);phases.engine.enter(phases.engine.initial,SystemClock.uptimeMillis());if(CoinPreset.APP.equals(pkg)&&!phases.customized)coinJourney.route(phases.engine.current);AutomatonNormalizer.normalizeRuntime(phases.engine);upgrades=new UpgradeActions(s,pkg,phases.engine);banc=new BancRuntime(s,pkg,phases.storage(),phases.engine);}}
 SplitPilot(TapService s){this.s=s;}
 void geometryChanged(){if(!active||virtualMode)return;token++;for(Slot slot:slots){pauseMain(slot);slot.home=false;slot.checked=0;slot.observedAt=0;slot.cyclePending=false;slot.scheduleVersion++;if(slot.upgrades!=null)slot.upgrades.cancel("画面配置を再確認");if(slot.banc!=null)slot.banc.cancel();if(slot.layout!=null)slot.layout.relocator.clear();if(slot.learned!=null)slot.learned=new LearnedLayout();slot.phases.engine.restartObservation(SystemClock.uptimeMillis());}later(token,150);}
 void resetRun(){if(slots!=null)for(Slot slot:slots){pauseMain(slot);if(slot.upgrades!=null)slot.upgrades.cancel("停止中");if(slot.banc!=null)slot.banc.cancel();}active=false;token++;virtualMode=false;parallelVirtual=false;}
 void stop(){resetRun();if(s.pilot!=null&&s.pilot.virtualWindows!=null)s.pilot.virtualWindows.pause();s.pilot.panelTouchable(true);s.touchable(true);}
 void start(){start(s.pilot.splitLauncher.targets());}
 void start(String[] targets){s.stop();virtualMode=false;if(Build.VERSION.SDK_INT<34){s.pilot.note("複数画面自律にはAndroid14以降が必要です");return;}ArrayList<Slot> ready=new ArrayList<>();try{for(String pkg:targets){if(s.pilot.splitLauncher.bounds(pkg)==null){s.pilot.note(s.appLabel(pkg)+"のウインドウが未表示です");return;}ready.add(new Slot(s,pkg));}}catch(Exception failure){s.pilot.note("状態設定："+failure.getMessage());return;}if(ready.isEmpty()){s.pilot.note("対象アプリを選択してください");return;}slots=ready.toArray(new Slot[0]);active=true;index=0;lastShot=0;int t=++token;s.compactPanel(true);next(t);}
 void startVirtual(String[] targets){
  if(active&&virtualMode)return;
  ArrayList<Slot> ready=new ArrayList<>();try{for(String pkg:targets){VirtualWindows.Window w=s.pilot.virtualWindows.window(pkg);if(w==null||!w.launched||w.frameAt<=0){s.pilot.virtualWindows.startFailed(s.appLabel(pkg)+"：仮想画面の起動を確認できません");return;}Slot slot=new Slot(s,pkg);slot.phases.runtimeOnly=true;if(!slot.phases.customized&&!slot.phases.engine.windowRelative&&PresetRegistry.supports(pkg))slot.phases.engine.enter(PresetRegistry.verify(pkg),SystemClock.uptimeMillis());s.pilot.dailyRunner.install(slot.phases.engine,pkg,true);ready.add(slot);}}catch(Exception e){s.pilot.virtualWindows.startFailed("仮想状態設定："+e.getMessage());return;}
  if(ready.isEmpty()){s.pilot.virtualWindows.startFailed("自律する仮想画面がありません");return;}
  VirtualRunActivation.start(new VirtualRunActivation.Host(){
   public boolean running(){return active&&virtualMode;}
   public void preparePhysical(){resetRun();s.prepareVirtualRun();if(PcControl.ownsRun(s))s.pilot.wireless.pauseMaintenance();else s.pilot.wireless.resumeMaintenance();}
   public boolean arm(){return s.pilot.virtualWindows.arm();}
   public void activate(){slots=ready.toArray(new Slot[0]);virtualMode=true;parallelVirtual=s.pilot.virtualWindows.remote!=null;active=true;index=0;lastShot=0;int t=++token;s.pilot.virtualWindows.started();if(parallelVirtual){for(int i=0;i<slots.length;i++)later(slots[i],t,VirtualLoadPolicy.scanOffset(i,slots.length,s.performance.profile().scanMs));}else next(t);}
  });
 }
 void nextVirtual(int t){nextVirtual(slots[index++%slots.length],t);}
 void nextVirtual(Slot slot,int t){
  if(!valid(t)||slot.cyclePending)return;slot.cyclePending=true;if(slot.manual.holding(SystemClock.uptimeMillis())){pauseMain(slot);later(slot,t,150);return;}
  if(slot.stopped){VirtualWindows.Window stopped=s.pilot.virtualWindows.window(slot.pkg);if(stopped!=null)s.pilot.virtualWindows.state(stopped,"停止状態");boolean all=true;for(Slot other:slots)all&=other.stopped;if(all){s.stop("全画面の状態機械が停止状態になった");return;}later(slot,t,350);return;}
  VirtualWindows.Window w=s.pilot.virtualWindows.window(slot.pkg);if(w!=null&&recoverDisplay(slot,w,t))return;if(w!=null){String actual=s.pilot.virtualWindows.screen(w).pkg;long now=SystemClock.uptimeMillis();w.restoreReceipt.observePackage(slot.pkg.equals(actual),w.frameAt,now);w.inputRecovery.observePackage(slot.pkg,actual,w.frameAt,now);if(recoverInputTarget(slot,w,t))return;}if(w==null||!s.pilot.virtualWindows.check(w)){slot.home=false;pauseMain(slot);later(slot,t,350);return;}
  long period=PerformancePolicy.scanInterval(s.performance.profile(),slot.processingMs,slots.length,s.performance.cores);
  long wait=Math.max(0,period-(SystemClock.uptimeMillis()-slot.lastShot));
  s.h.postDelayed(()->{if(!valid(t)||w.closed)return;slot.lastShot=SystemClock.uptimeMillis();sampleVirtual(slot,w,t);},wait);
 }
 boolean recoverDisplay(Slot slot,VirtualWindows.Window window,int t){
  PhaseEngine e=slot.phases.engine;PhaseEngine.Phase phase=e.phases.get(e.current);
  String prefix=PresetRegistry.prefix(slot.pkg),state=e.current.startsWith(prefix)?e.current.substring(prefix.length()):e.current;
  if(slot.phases.customized||e.windowRelative||!PresetRegistry.supports(slot.pkg)||phase==null||phase.terminal||Arrays.asList("account","error","unknown").contains(state))return false;
  DisplayRecovery recovery=slot.displayRecovery;long now=SystemClock.uptimeMillis();
  int action=recovery.poll(slot.adExit.recovery.exhausted()||window.inputRecovery.held(),now);
  if(action==DisplayRecovery.STOP){stopSlot(slot,"対象画面を再作成しても主画面を確認できませんでした / "+recovery.report(now),t);return true;}
  if(recovery.pending()||window.rebuilding){pauseMain(slot);later(slot,t,350);return true;}
  if(action!=DisplayRecovery.REBUILD)return false;
  if(DialogSafety.classify(s.pilot.virtualWindows.screen(window).text)!=0){stopSlot(slot,"保護表示のため画面の再作成を保留",t);return true;}
  int ticket=recovery.begin(now);if(ticket<0)return false;
  slot.home=false;pauseMain(slot);slot.manual.reset();slot.banc.cancel();if(slot.upgrades!=null)slot.upgrades.cancel("対象画面を再作成中");e.invalidateObservation();
  slot.phases.record("復帰回数の上限を確認 / 対象画面を1回だけ再作成 / "+slot.adExit.recovery.report(now));
  if(s.progressErrors!=null)s.progressErrors.fatal(slot.pkg,e,"DISPLAY_REBUILD","復帰上限後、対象の仮想画面だけを再作成します");
  s.pilot.virtualWindows.rebuild(window,()->valid(t)&&!slot.stopped&&recovery.current(ticket),()->{
   if(!recovery.finished(ticket,true,SystemClock.uptimeMillis())||!valid(t))return;
   slot.adExit.newDisplay();slot.interruption.reset();slot.farmAdError.reset();slot.repairLayout=new LearnedLayout();slot.lastFrameAt=slot.observedAt=slot.lastShot=0;
   if(slot.layout!=null){slot.layout.resetSize(0,0);slot.layout.relocator.clear();slot.layout.forceHomeProbe=true;}
   e.enter(RecoveryRoute.target(slot.pkg,e.current,false,false,slot.coinJourney.blocksMain()),SystemClock.uptimeMillis());
   slot.phases.record("対象画面の再作成を受付 / 復帰回数は保持・新しい画像で主画面を確認");later(slot,t,0);
  },reason->{if(recovery.finished(ticket,false,SystemClock.uptimeMillis())&&valid(t))stopSlot(slot,"対象画面の再作成に失敗："+reason,t);});
  later(slot,t,350);return true;
 }
 boolean recoverInputTarget(Slot slot,VirtualWindows.Window window,int t){
  InputTargetRecovery recovery=window.inputRecovery;if(!recovery.pending)return false;s.pilot.virtualWindows.requestFrameRefresh(window);
  slot.home=false;pauseMain(slot);long now=SystemClock.uptimeMillis();VirtualWindows windows=s.pilot.virtualWindows;
  if(recovery.confirm(window.pkg.equals(windows.screen(window).pkg),window.frameAt,now)){
   PhaseEngine engine=slot.phases.engine;String verify=RecoveryRoute.target(slot.pkg,engine.current,slot.phases.customized,engine.windowRelative,slot.coinJourney.blocksMain());
   engine.enter(verify,now);slot.interruption.reset();slot.banc.cancel();slot.farmAdError.reset();slot.lastFrameAt=0;window.inputError="";window.frameIssue="";window.frameFailures=0;window.missingScreenAt=0;window.screenInfo=null;windows.state(window,"対象アプリの表示を再確認 / 主画面を判定中");later(slot,t,0);return true;
  }
  windows.state(window,recovery.status());int ticket=recovery.begin(now);
  if(ticket<0){later(slot,t,350);return true;}
  if(slot.upgrades!=null)slot.upgrades.cancel("入力先を再確認中");if(slot.banc!=null)slot.banc.cancel();
  boolean restart=recovery.attempts>=InputTargetRecovery.MAX_RESTORES;
  if(restart)slot.phases.record("外部画面からの通常復帰を確認できないため、対象ゲームのタスク再起動を要求 / display="+window.id);
  windows.restore(window,restart,()->valid(t)&&recovery.pending&&recovery.revision==ticket,()->{recovery.finished(ticket,true,SystemClock.uptimeMillis());window.screenInfo=null;later(slot,t,InputTargetRecovery.RECHECK_MS);},()->{recovery.finished(ticket,false,SystemClock.uptimeMillis());later(slot,t,500);});return true;
 }
 ArrayList<PhaseEngine.Region> monitored(Slot slot){
  PhaseEngine e=slot.phases.engine;ArrayList<PhaseEngine.Region> regions=new ArrayList<>(e.activeRegions());
  for(String key:slot.pkg.equals(UsagiPreset.APP)?new String[]{UsagiPreset.PREFIX+"game",UsagiPreset.PREFIX+"amount-level"}:slot.pkg.equals(FarmPreset.APP)?new String[]{FarmPreset.P+"harvest"}:slot.pkg.equals(CoinPreset.APP)?new String[]{CoinPreset.P+"home",CoinPreset.P+"fever"}:slot.pkg.equals(BoxPreset.APP)?new String[]{BoxPreset.P+"game"}:slot.pkg.equals(PoipoiPreset.APP)?new String[]{PoipoiPreset.P+"game"}:new String[0]){PhaseEngine.Region r=e.regions.get(key);if(r!=null&&!regions.contains(r))regions.add(r);}if(slot.upgrades!=null)slot.upgrades.addRegions(regions);return new ArrayList<>(slot.banc.regions(e,regions,SystemClock.uptimeMillis()));
 }
 void sampleVirtual(Slot slot,VirtualWindows.Window window,int t){
  if(slot.manual.holding(SystemClock.uptimeMillis())){pauseMain(slot);later(slot,t,150);return;}
  PhaseEngine engine=slot.phases.engine;String state=engine.current;long epoch=engine.epoch;ArrayList<PhaseEngine.Region> regions=monitored(slot);
  Map<String,float[]> namedPopup=new HashMap<>();
  if(!slot.phases.customized&&!engine.windowRelative&&GamePopupEvidence.supported(slot.pkg)){long popupStarted=SystemClock.uptimeMillis();Map<String,float[]> popup=GamePopupUiControls.read(s,slot.pkg,window.id,null,window.width,window.height);if(GamePopupEvidence.present(popup)&&SystemClock.uptimeMillis()-window.frameAt<1500){if(popupStarted-slot.lastHomeProbe<2000){slot.observedAt=SystemClock.uptimeMillis();slot.processingMs=SystemClock.uptimeMillis()-popupStarted;DailyCompletion.continuation(engine,popup);applySamples(slot,popup,new Rect(0,0,window.width,window.height),t);return;}namedPopup.putAll(popup);slot.lastHomeProbe=popupStarted;if(slot.layout!=null)slot.layout.forceHomeProbe=true;}}
  final Map<String,float[]> namedControls=namedPopup;
  s.pilot.virtualWindows.samplePixels(window,()->valid(t),frame->{
   if(frame.capturedAt<=slot.lastFrameAt)return ()->{if(valid(t))later(slot,t,100);};
   final int width=frame.width,height=frame.height;final long capturedAt=frame.capturedAt;
   long started=SystemClock.uptimeMillis();RegionSampler.Pixels framePixels=FrameSampleCache.wrap(frame.width,frame.height,frame);Map<String,float[]> samples=new HashMap<>();boolean ok=true;slot.sampleError="";
   try{if(valid(t)&&!window.closed){if(slot.learned!=null)samples=slot.learned.sample(engine,frame.width,frame.height,framePixels,frame.capturedAt);else if(slot.layout!=null)samples=slot.layout.sampleRelative(regions,frame.width,frame.height,framePixels,frame.capturedAt);else for(PhaseEngine.Region r:regions)samples.put(r.id,RegionSampler.sample(frame.width,frame.height,r.bounds,framePixels));if(slot.layout!=null)slot.layout.forceHomeProbe=false;if(!namedControls.isEmpty())samples.putAll(namedControls);if(FarmPreset.APP.equals(slot.pkg)&&!slot.phases.customized)FarmAdErrorEvidence.sample(samples,frame.width,frame.height,framePixels);DailyCompletion.sample(engine,samples,frame.width,frame.height,framePixels,null);if(!GamePopupEvidence.fast(samples)){RecordedRepairSampling.sample(engine,samples,regions,frame.width,frame.height,framePixels,null);RecordedRepairSampling.adapt(engine,samples,frame.width,frame.height,framePixels,slot.repairLayout,frame.capturedAt);slot.banc.sample(engine,samples,frame.width,frame.height,framePixels,frame.capturedAt,null);}}}
   catch(Exception failure){slot.sampleError=VirtualWindows.brief(failure);ok=false;}catch(OutOfMemoryError pressure){slot.sampleError="OutOfMemoryError";s.performance.memoryPressure();ok=false;}
   final boolean sampled=ok;final Map<String,float[]> sampledRegions=samples;final long elapsed=SystemClock.uptimeMillis()-started;
   return ()->{if(!valid(t)||window.closed)return;slot.processingMs=elapsed;slot.lastFrameAt=capturedAt;slot.observedAt=capturedAt;if(!sampled||SystemClock.uptimeMillis()-capturedAt>1500){slot.home=false;pauseMain(slot);s.pilot.virtualWindows.state(window,sampled?"画像が古いため再取得中（判定 "+elapsed+"ms）":"画像判定を再試行中："+slot.sampleError);if(!sampled)slot.phases.record("領域判定失敗："+slot.sampleError+" / "+state);later(slot,t,500);return;}if(!state.equals(engine.current)||epoch!=engine.epoch){later(slot,t,0);return;}if(FarmPreset.APP.equals(slot.pkg)&&!slot.phases.customized)FarmUiControls.augment(s,engine,window.id,null,width,height,sampledRegions);if(!slot.phases.customized)AdUiControls.augment(s,engine,slot.pkg,window.id,null,width,height,sampledRegions);applySamples(slot,sampledRegions,new Rect(0,0,width,height),t);};
  },()->{if(valid(t)){slot.home=false;pauseMain(slot);s.pilot.note(s.appLabel(slot.pkg)+"：仮想画面の画像取得を待機");later(slot,t,500);}});
 }
 String pcPhase(String pkg){if(!active||!virtualMode)return "stopped";if(slots!=null)for(Slot slot:slots)if(pkg.equals(slot.pkg))return slot.stopped?"stopped":slot.home?"automation":"observing";return "observing";}
 boolean valid(int t){return active&&token==t;}
 void later(int t,long delay){s.h.postDelayed(()->next(t),Math.max(0,delay));}
 void later(Slot slot,int t,long delay){slot.cyclePending=false;if(!parallelVirtual){later(t,delay);return;}int version=++slot.scheduleVersion;s.h.postDelayed(()->{if(valid(t)&&parallelVirtual&&version==slot.scheduleVersion)nextVirtual(slot,t);},Math.max(0,delay));}
 void next(int t){if(!valid(t))return;if(virtualMode){nextVirtual(t);return;}if(s.guardDialog()){later(t,500);return;}Slot slot=slots[index++%slots.length];if(slot.stopped){boolean all=true;for(Slot other:slots)all&=other.stopped;if(all){stop();s.pilot.note("全アプリ停止状態");return;}later(t,50);return;}

 AccessibilityWindowInfo selected=null;Rect bounds=null;for(AccessibilityWindowInfo w:s.getWindows()){if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;AccessibilityNodeInfo n=w.getRoot();if(n==null)continue;boolean match=slot.pkg.contentEquals(n.getPackageName()==null?"":n.getPackageName());n.recycle();if(match){selected=w;bounds=new Rect();w.getBoundsInScreen(bounds);break;}}
 if(selected==null||bounds==null||bounds.isEmpty()){slot.home=false;pauseMain(slot);s.pilot.note(s.appLabel(slot.pkg)+"：未表示のため待機");later(t,350);return;}if(!s.pilot.splitLauncher.exposed(slot.pkg,bounds)){slot.home=false;pauseMain(slot);s.pilot.note(s.appLabel(slot.pkg)+"：重なりを解消すると再開");later(t,350);return;}final Rect rect=new Rect(bounds);final int id=selected.getId();long wait=TransitionTiming.remaining(lastShot,SystemClock.uptimeMillis());s.h.postDelayed(()->capture(slot,id,rect,t),wait);
 }
 void capture(Slot slot,int id,Rect rect,int t){
  if(!valid(t))return;lastShot=SystemClock.uptimeMillis();
  s.takeScreenshotOfWindow(id,s.getMainExecutor(),new AccessibilityService.TakeScreenshotCallback(){
   public void onFailure(int code){slot.home=false;pauseMain(slot);if(valid(t)){s.pilot.note("複数画面：画像取得失敗 "+code);later(t,1200);}}
   public void onSuccess(AccessibilityService.ScreenshotResult result){
    Bitmap hardware=null,image=null;try{hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());if(hardware!=null)image=hardware.copy(Bitmap.Config.ARGB_8888,false);}catch(OutOfMemoryError pressure){s.performance.memoryPressure();}finally{if(hardware!=null)hardware.recycle();result.getHardwareBuffer().close();}
    if(image==null){later(t,1200);return;}
    if(!valid(t)){image.recycle();return;}Rect current=s.pilot.splitLauncher.bounds(slot.pkg);
    if(current==null||!current.equals(rect)){image.recycle();slot.home=false;pauseMain(slot);later(t,0);return;}
    Bitmap local=image;
    if(image.getWidth()!=rect.width()||image.getHeight()!=rect.height()){
     if(image.getWidth()>=rect.right&&image.getHeight()>=rect.bottom){local=Bitmap.createBitmap(image,rect.left,rect.top,rect.width(),rect.height());if(local!=image)image.recycle();}
     else{image.recycle();slot.home=false;pauseMain(slot);later(t,500);return;}
    }
    observe(slot,local,rect,t);
   }
  });
 }
 void observe(Slot slot,Bitmap image,Rect rect,int t){
  PhaseEngine e=slot.phases.engine;String state=e.current;long epoch=e.epoch;ArrayList<PhaseEngine.Region> regions=monitored(slot);long captured=SystemClock.uptimeMillis();
  try{s.pilot.worker.execute(()->{
   Map<String,float[]> samples=new HashMap<>();boolean ok=true;slot.sampleError="";
   try{RegionSampler.Pixels pixels=BitmapPixels.of(image);if(slot.learned!=null)samples=slot.learned.sample(e,image.getWidth(),image.getHeight(),pixels,captured);else if(slot.layout!=null)samples=slot.layout.sampleRelative(regions,image.getWidth(),image.getHeight(),pixels,captured);else for(PhaseEngine.Region r:regions)samples.put(r.id,RegionSampler.sample(image.getWidth(),image.getHeight(),r.bounds,pixels));if(FarmPreset.APP.equals(slot.pkg)&&!slot.phases.customized)FarmAdErrorEvidence.sample(samples,image.getWidth(),image.getHeight(),pixels);DailyCompletion.sample(e,samples,image.getWidth(),image.getHeight(),pixels,null);RecordedRepairSampling.sample(e,samples,regions,image.getWidth(),image.getHeight(),pixels,null);RecordedRepairSampling.adapt(e,samples,image.getWidth(),image.getHeight(),pixels,slot.repairLayout,captured);slot.banc.sample(e,samples,image.getWidth(),image.getHeight(),pixels,captured,null);}
   catch(Exception failure){slot.sampleError=VirtualWindows.brief(failure);ok=false;}catch(OutOfMemoryError pressure){slot.sampleError="OutOfMemoryError";s.performance.memoryPressure();ok=false;}finally{image.recycle();}
   final Map<String,float[]> result=samples;final boolean sampled=ok;
   s.h.post(()->{if(!valid(t))return;if(!sampled||SystemClock.uptimeMillis()-captured>1500){slot.home=false;pauseMain(slot);later(t,500);return;}if(!state.equals(e.current)||epoch!=e.epoch){later(t,0);return;}slot.observedAt=captured;if(!slot.phases.customized){AdUiControls.augment(s,e,slot.pkg,virtualMode?s.pilot.virtualWindows.window(slot.pkg).id:android.view.Display.DEFAULT_DISPLAY,rect,rect.width(),rect.height(),result);}if(FarmPreset.APP.equals(slot.pkg)&&!slot.phases.customized)FarmUiControls.augment(s,e,android.view.Display.DEFAULT_DISPLAY,rect,rect.width(),rect.height(),result);applySamples(slot,result,rect,t);});
  });}catch(java.util.concurrent.RejectedExecutionException stopped){image.recycle();if(valid(t))later(t,500);}
 }
 void applySamples(Slot slot,Map<String,float[]> samples,Rect rect,int t){if(!valid(t))return;if(slot.manual.holding(SystemClock.uptimeMillis())){pauseMain(slot);later(slot,t,150);return;}PhaseEngine e=slot.phases.engine;boolean usagi=!e.windowRelative&&slot.pkg.equals(UsagiPreset.APP),farm=!e.windowRelative&&slot.pkg.equals(FarmPreset.APP),coin=!e.windowRelative&&slot.pkg.equals(CoinPreset.APP),shopping=!e.windowRelative&&slot.pkg.equals(ShoppingPreset.APP),box=!e.windowRelative&&slot.pkg.equals(BoxPreset.APP),poipoi=!e.windowRelative&&slot.pkg.equals(PoipoiPreset.APP);long now=SystemClock.uptimeMillis();String receiptNote=slot.receipt.observe(e,samples,slot.observedAt,now);if(!receiptNote.isEmpty())slot.phases.record(receiptNote);slot.recognition=RecognitionDiagnostics.describe(e,samples)+(slot.receipt.status.isEmpty()?"":" / "+slot.receipt.status);if(farm&&!slot.phases.customized&&handleFarmAdError(slot,samples,rect,t,now))return;if(!slot.phases.customized){slot.adExit.filter(e,samples,rect.width(),rect.height(),slot.observedAt,now);slot.displayRecovery.observeHome(RecoveryEvidence.home(e,samples),slot.observedAt,now);}String recognitionBefore=slot.recognition;String recognitionAfter=RecognitionDiagnostics.describe(e,samples);slot.recognition="判定時："+recognitionBefore+" / 操作抑制後："+recognitionAfter;if(virtualMode&&s.progressErrors!=null)s.progressErrors.observed(slot,samples,slot.observedAt,recognitionBefore,recognitionAfter);PhaseEngine.Watch home=e.rule(UsagiPreset.PREFIX+"return");slot.home=usagi?home!=null&&samples.get(home.regionId)!=null&&home.distance(samples.get(home.regionId))<=home.threshold:farm?FarmPreset.home(e,samples):shopping?ShoppingPreset.playable(e,samples):box?BoxPreset.playable(e,samples):poipoi?PoipoiPreset.playable(e,samples):coin&&CoinPreset.playable(e,samples);slot.checked=slot.observedAt;if(slot.home){slot.retries=0;slot.recovery.reset();if(e.phases.get(e.current).markersDuring&&!slot.banc.pending())slot.banc.progress(slot.observedAt);}
 if(!slot.phases.customized&&slot.interruption.observe(e,samples,slot.observedAt,now)){slot.banc.cancel();String recovery=AdInterruption.recoveryState(e);e.enter(usagi?slot.journey.route(recovery):recovery,now);slot.home=false;pauseMain(slot);slot.phases.record("CM等の割込みを新しい画像で確認 / "+e.current);later(slot,t,0);return;}e.observe(usagi&&!slot.phases.customized?UsagiPreset.missionSamples(e,samples):samples,now);reviewUpgrades(slot,samples,rect,t);PhaseEngine.Watch w=slot.phases.customized?slot.banc.core.pollRegistered(e):slot.adExit.withFallback(e,slot.banc.core.pollRegistered(e),samples,now);s.rememberAutomationIssue(slot.pkg,e,e.diagnostic);if(!slot.home||e.awaitingBranch()||w!=null&&(w.action!=PhaseEngine.WAIT||!w.nextPhase.isEmpty()&&!w.nextPhase.equals(e.current)))pauseMain(slot);if(virtualMode){VirtualWindows.Window display=s.pilot.virtualWindows.window(slot.pkg);if(display!=null)s.pilot.virtualWindows.state(display,(slot.home?"主画面確認済み：":"主画面の認識待ち：")+e.phases.get(e.current).name+(slot.layout==null?"":" / "+slot.layout.status));}String adStatus=slot.phases.customized?"":slot.adExit.status(e,samples,now);s.pilot.note(adStatus.isEmpty()?slots.length+"画面 / "+s.appLabel(slot.pkg)+"："+e.phases.get(e.current).name:adStatus+" / "+s.appLabel(slot.pkg));
 if(w==null){if(handleBanc(slot,samples,rect,t))return;if(e.windowRelative)s.pilot.note(slots.length+"画面 / "+s.appLabel(slot.pkg)+"："+e.phases.get(e.current).name+" / "+e.diagnostic+" / "+slot.learned.status);if(e.phases.get(e.current).terminal){if(s.pilot.dailyRunner.completed(slot.pkg,e))return;stopSlot(slot,"終了状態："+e.phases.get(e.current).name,t);return;}if(e.phases.get(e.current).markersDuring&&slot.home&&!e.awaitingBranch()&&!(usagi&&!slot.phases.customized&&slot.journey.blocksMain())){float[] p=slot.layout==null?samples.get(AdaptiveLayout.MAIN):slot.layout.mainPointRelative(samples,rect.width(),rect.height());if(p==null&&slot.layout!=null){slot.home=false;pauseMain(slot);later(slot,t,0);return;}if(p==null)p=WindowCoordinates.point(0,0,rect.width(),rect.height(),.5f,usagi?.78f:coin?CoinPreset.MAIN_Y:shopping?ShoppingPreset.MAIN_Y:box?BoxPreset.MAIN_Y:poipoi?PoipoiPreset.MAIN_Y:.76f);else p=p.clone();p[0]+=rect.left;p[1]+=rect.top;startMainTaps(slot,rect,p,t);later(slot,t,0);}else later(slot,t,0);return;}
 if(virtualMode&&s.progressErrors!=null&&w.action!=PhaseEngine.WAIT)s.progressErrors.control(slot,w,samples,rect.width(),rect.height());if(slot.banc.enabled()){if(!slot.banc.core.permitNormal(e,w,samples,now)){e.cancelIfCurrent(w,e.current,e.epoch);if(!handleBanc(slot,samples,rect,t))later(slot,t,500);return;}if(w.action!=PhaseEngine.WAIT&&w.condition==PhaseEngine.MATCH)slot.banc.progress(now);}String before=e.current;long epoch=e.epoch;Runnable done=()->{if(!valid(t))return;if(!e.finishIfCurrent(w,before,epoch,SystemClock.uptimeMillis())){later(slot,t,0);return;}if(poipoi&&!slot.phases.customized&&slot.receipt.started(w,SystemClock.uptimeMillis()))slot.phases.record(slot.receipt.status);if(usagi&&!slot.phases.customized){if(UsagiPreset.leavesMissions(w))slot.journey.completedPlay();String routed=slot.journey.route(e.current);if(!routed.equals(e.current))e.enter(routed,SystemClock.uptimeMillis());if(e.current.equals(UsagiPreset.PREFIX+"missions")){slot.missionTaps=0;slot.missionBudget.reset();}}s.pilot.dailyRunner.entered(e,slot.pkg);if(coin&&!slot.phases.customized)slot.coinJourney.completed(e,w,SystemClock.uptimeMillis());if(coin&&e.current.equals(CoinPreset.P+"missions")||shopping&&e.current.equals(ShoppingPreset.P+"missions")||poipoi&&e.current.equals(PoipoiPreset.P+"missions")){slot.missionTaps=0;slot.missionBudget.reset();}later(slot,t,0);};
 if(w.action==PhaseEngine.STOP){stopSlot(slot,"停止条件："+(w.name.isEmpty()?w.id:w.name),t);return;}if(w.action==PhaseEngine.WAIT){done.run();return;}if(e.windowRelative&&(w.action==PhaseEngine.CENTER||w.action==PhaseEngine.CURVE||w.action==PhaseEngine.MARKERS)){runLearned(slot,w,before,epoch,samples,rect,t,done);return;}if(e.windowRelative&&w.action==PhaseEngine.BACK){Runnable failed=()->{e.cancelIfCurrent(w,before,epoch);s.pilot.note(s.appLabel(slot.pkg)+"：対象画面の戻るを待機 / 仮想画面では端末内接続が必要です");later(slot,t,500);};if(virtualMode)s.pilot.virtualWindows.back(s.pilot.virtualWindows.window(slot.pkg),()->valid(t),done,failed);else if(slot.pkg.equals(s.pilot.packageNow())&&s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))done.run();else failed.run();return;}if(w.action==PhaseEngine.RETURN_APP){slot.banc.cancel();int exit=slot.phases.customized?PhaseEngine.RETURN_APP:slot.adExit.choose(e,w,samples,SystemClock.uptimeMillis());if(exit==PhaseEngine.WAIT){e.cancelIfCurrent(w,before,epoch);later(slot,t,500);return;}if(exit==PhaseEngine.BACK){adBack(slot,t,w,before,epoch,done);return;}restore(slot,t,w,before,epoch);return;}if(w.action==PhaseEngine.BACK){adBack(slot,t,w,before,epoch,done);return;}if(RecordedRepairSampling.local(e,w)&&w.action==PhaseEngine.CURVE){float[] path=RecordedRepairSampling.path(e,w,samples,rect.width(),rect.height());if(path==null){e.cancelIfCurrent(w,before,epoch);later(slot,t,500);return;}path=LearnedInput.offset(path,rect.left,rect.top);gesture(slot,rect,path,w.duration,t,done,()->{e.cancelIfCurrent(w,before,epoch);later(slot,t,500);});return;}if(w.action==PhaseEngine.CENTER){if(w.condition==PhaseEngine.MATCH&&!e.confirmsAction(w,before,epoch,samples)){later(slot,t,0);return;}if((w.id.equals(FarmPreset.P+"upgrade-ready")||w.id.equals(UsagiPreset.PREFIX+"amount-tap")||w.id.equals(UsagiPreset.PREFIX+"ミッション中央をタップ"))&&!slot.home){if(slot.phases.customized)e.cancelIfCurrent(w,before,epoch);else e.enter(usagi?UsagiPreset.PREFIX+"classify":FarmPreset.P+"check",now);later(slot,t,0);return;}if(w.id.equals(UsagiPreset.PREFIX+"ミッション中央をタップ")||coin&&w.id.equals(CoinPreset.P+"mission-button-tap")||poipoi&&w.id.equals(PoipoiPreset.P+"mission-button-tap")||shopping&&(w.id.equals(ShoppingPreset.P+"mission-button-tap")||w.id.equals(ShoppingPreset.P+"mission-shop-button-tap"))){if(!slot.missionBudget.acquire(now)){e.cancelIfCurrent(w,before,epoch);later(slot,t,120);return;}slot.missionTaps++;}if(w.id.equals(UsagiPreset.PREFIX+"amount-tap"))UsagiPreset.prepareAmount(e,samples);PhaseEngine.Region r=e.regions.get(w.regionId);float[] p=samples.containsKey(w.regionId+GamePopupEvidence.POINT)?GamePopupEvidence.point(w.regionId,samples,rect.width(),rect.height()):samples.containsKey(w.regionId+AdCloseAutomaton.CONTROL_POINT)?AdCloseAutomaton.controlPoint(w.regionId,samples,rect.width(),rect.height()):RecordedRepairSampling.local(e,w)?RecordedRepairSampling.point(e,w,samples,rect.width(),rect.height()):samples.containsKey(w.regionId+FarmControlEvidence.POINT)||FarmControlEvidence.namedOnly(w)?FarmControlEvidence.point(w.regionId,samples,rect.width(),rect.height()):slot.layout!=null?slot.layout.pointRelative(w,samples,rect.width(),rect.height()):AdCloseAutomaton.handles(w.regionId)?samples.get(w.regionId+AdCloseAutomaton.POINT):usagi?UsagiPreset.tapCenter(e,w,rect.width(),rect.height()):RegionMath.center(r.bounds,rect.width(),rect.height());if(p==null){e.cancelIfCurrent(w,before,epoch);later(slot,t,0);return;}p[0]+=rect.left;p[1]+=rect.top;if(!slot.phases.customized)slot.adExit.controlSent(w,SystemClock.uptimeMillis());tap(slot,rect,p,60,t,done,()->{e.cancelIfCurrent(w,before,epoch);later(slot,t,500);});return;}stopSlot(slot,"複数画面で未対応の曲線・マーカー操作",t);
 }
 boolean handleFarmAdError(Slot slot,Map<String,float[]> samples,Rect rect,int t,long now){
  boolean found=FarmAdErrorEvidence.point(samples,rect.width(),rect.height())!=null;
  slot.farmAdError.observe(found,slot.observedAt,now);
  if(slot.farmAdError.consumeRecovered()){
   slot.home=false;
   slot.phases.engine.enter(FarmPreset.P+"check",now);
   slot.banc.cancel();slot.phases.record("農園：広告エラーの消失を確認 / 画面再判定");
   pauseMain(slot);later(slot,t,0);return true;
  }
  if(!slot.farmAdError.visible())return false;
  slot.home=false;pauseMain(slot);slot.banc.cancel();
  if(slot.upgrades!=null)slot.upgrades.cancel("農園：広告表示失敗を確認");
  if(virtualMode){VirtualWindows.Window window=s.pilot.virtualWindows.window(slot.pkg);if(window!=null)s.pilot.virtualWindows.state(window,"農園：広告エラーのOKを確認中 / "+slot.farmAdError.attempts()+"回送信");}
  if(slot.farmAdError.exhausted(now)){
   stopSlot(slot,"農園：広告エラーOKを3回試しても閉じません。手動で画面を確認してください",t);return true;
  }
  if(!slot.farmAdError.mayTap(now)){
   s.pilot.note("農園：広告エラーのOK確認中 / 再撮影待機");later(slot,t,250);return true;
  }
  float[] point=FarmAdErrorEvidence.point(samples,rect.width(),rect.height());
  if(point==null){later(slot,t,250);return true;}
  point[0]+=rect.left;point[1]+=rect.top;
  slot.farmAdError.dispatched(now);
  slot.phases.record("農園：広告表示失敗のOKを新しい画像で確認 / "+slot.farmAdError.attempts()+"回目");
  tap(slot,rect,point,60,t,()->later(slot,t,400),()->later(slot,t,500));
  return true;
 }
 boolean handleBanc(Slot slot,Map<String,float[]> samples,Rect rect,int t){
  if(!valid(t)||!slot.banc.enabled()||slot.stopped)return false;
  VirtualWindows.Window window=virtualMode?s.pilot.virtualWindows.window(slot.pkg):null;
  if(virtualMode&&(window==null||window.closed))return false;
  int display=window==null?android.view.Display.DEFAULT_DISPLAY:window.id;
  Set<String> blocked=new HashSet<>();if(!slot.phases.customized&&(slot.journey.blocksMain()||slot.coinJourney.blocksMain()))for(PhaseEngine.Phase p:slot.phases.engine.phases.values())if(p.markersDuring)blocked.add(p.id);
  BancAutonomy.Decision d=slot.banc.review(slot.phases.engine,samples,display,rect,slot.observedAt,blocked);if(d.kind!=BancAutonomy.NONE)pauseMain(slot);
  if(d.kind==BancAutonomy.NONE)return false;
  slot.home=false;pauseMain(slot);
  if(d.kind==BancAutonomy.RESYNC){if(blocked.contains(d.target)||!slot.banc.core.commitResync(d,slot.phases.engine,SystemClock.uptimeMillis()))return false;slot.phases.record("BANC自律："+d.reason+" / "+d.target);}
  else if(d.kind==BancAutonomy.CONTROL){if(!s.guardDialog()&&slot.banc.activate(d,slot.phases.engine,samples,display,rect))slot.phases.record("BANC自律："+d.control.label+" を送信 / "+d.reason+" / 回路出力 "+d.activation);}
  String reason=d.kind==BancAutonomy.CONTROL?d.control.label+" / 操作結果の確認":d.reason;s.pilot.note(s.appLabel(slot.pkg)+" / BANC自律："+reason);if(window!=null)s.pilot.virtualWindows.state(window,"BANC自律："+reason);later(slot,t,500);return true;
 }
 void runLearned(Slot slot,PhaseEngine.Watch watch,String state,long epoch,Map<String,float[]> samples,Rect rect,int t,Runnable done){
  PhaseEngine e=slot.phases.engine;Runnable failed=()->{if(valid(t)){e.cancelIfCurrent(watch,state,epoch);later(slot,t,250);}};
  if(slot.learned==null||!e.confirmsAction(watch,state,epoch,samples)){failed.run();return;}
  float[] local=LearnedInput.local(watch,e.regions.get(watch.regionId),slot.learned,rect.width(),rect.height()),points=LearnedInput.offset(local,rect.left,rect.top);
  if(!LearnedInput.inside(points,rect.left,rect.top,rect.width(),rect.height())){failed.run();return;}
  if(watch.action==PhaseEngine.MARKERS){if(!ObservationLearner.validPoints(watch.markerPoints)){failed.run();s.pilot.note(s.appLabel(slot.pkg)+"：学習した連打対象を再確認");return;}learnedMarkers(slot,points,watch,t,0,SystemClock.uptimeMillis()+Math.min(1000,watch.duration),done,failed);return;}
  gesture(slot,rect,points,watch.duration,t,done,failed);
 }
 void learnedMarkers(Slot slot,float[] points,PhaseEngine.Watch watch,int t,int index,long deadline,Runnable done,Runnable failed){if(!valid(t))return;if(SystemClock.uptimeMillis()>=deadline){done.run();return;}int i=index%(points.length/2)*2;Rect rect=virtualMode?new Rect(0,0,s.pilot.virtualWindows.window(slot.pkg).width,s.pilot.virtualWindows.window(slot.pkg).height):s.pilot.splitLauncher.bounds(slot.pkg);if(rect==null){failed.run();return;}gesture(slot,rect,new float[]{points[i],points[i+1]},1,t,()->s.h.postDelayed(()->learnedMarkers(slot,points,watch,t,index+1,deadline,done,failed),s.tapNextDelay(slot.pkg,s.tapPressMillis(slot.pkg,1))),failed);}
 void gesture(Slot slot,Rect rect,float[] points,long duration,int t,Runnable done,Runnable failed){
  if(!valid(t))return;if(virtualMode){VirtualWindows.Window w=s.pilot.virtualWindows.window(slot.pkg);if(w==null){failed.run();return;}s.pilot.virtualWindows.gesture(w,points,duration,()->valid(t)&&!slot.manual.holding(SystemClock.uptimeMillis()),()->{if(valid(t)){s.count++;done.run();}},failed);return;}
  if(s.busy){s.h.postDelayed(()->gesture(slot,rect,points,duration,t,done,failed),s.busyPoll());return;}
  Rect current=s.pilot.splitLauncher.bounds(slot.pkg);if(current==null||!rect.equals(current)||!s.pilot.splitLauncher.exposed(slot.pkg,current)||!LearnedInput.inside(points,rect.left,rect.top,rect.width(),rect.height())||s.guardDialog()){failed.run();return;}
  boolean covered=s.pilot.panelCovers(points[0],points[1]);if(covered&&duration>200){failed.run();s.pilot.note("曲線の開始位置からパネルを離してください");return;}s.busy=true;s.touchable(false);if(covered)s.pilot.panelTouchable(false);s.h.postDelayed(()->{if(!valid(t)){s.busy=false;s.pilot.panelTouchable(true);s.touchable(true);return;}Rect live=s.pilot.splitLauncher.bounds(slot.pkg);if(live==null||!live.equals(rect)||!s.pilot.splitLauncher.exposed(slot.pkg,live)||s.guardDialog()){s.busy=false;s.pilot.panelTouchable(true);s.touchable(true);failed.run();return;}Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,points.length==2?s.tapPressMillis(slot.pkg,duration):duration)).build();boolean ok=s.dispatchTrackedGesture(slot.pkg,g,new AccessibilityService.GestureResultCallback(){public void onCompleted(GestureDescription g){s.busy=false;s.pilot.panelTouchable(true);s.touchable(true);if(valid(t)){s.count++;done.run();}}public void onCancelled(GestureDescription g){s.busy=false;s.pilot.panelTouchable(true);s.touchable(true);if(valid(t))failed.run();}},s.h);if(!ok){s.busy=false;s.pilot.panelTouchable(true);s.touchable(true);failed.run();}},32);
 }
 void stopSlot(Slot slot,String reason,int t){slot.displayRecovery.cancel();s.rememberAutomationIssue(slot.pkg,slot.phases.engine,reason);pauseMain(slot);slot.stopped=true;if(virtualMode){VirtualWindows.Window display=s.pilot.virtualWindows.window(slot.pkg);if(display!=null){display.stopReason=reason;s.pilot.virtualWindows.state(display,"停止状態");s.pilot.virtualWindows.record("#"+display.id+" "+s.appLabel(slot.pkg)+" 停止："+reason);}}slot.phases.record(reason);if(s.progressErrors!=null&&!reason.startsWith("終了状態")&&!reason.startsWith("停止条件"))s.progressErrors.fatal(slot.pkg,slot.phases.engine,"LANE_STOP",reason);later(slot,t,0);}
 void reviewUpgrades(Slot slot,Map<String,float[]> samples,Rect rect,int t){
  if(slot.upgrades==null)return;int display=Display.DEFAULT_DISPLAY;if(virtualMode){VirtualWindows.Window window=s.pilot.virtualWindows.window(slot.pkg);if(window==null){slot.upgrades.cancel("対象画面がありません");return;}display=window.id;}
  boolean blocked=!slot.phases.customized&&(slot.journey.blocksMain()||slot.coinJourney.blocksMain());
  java.util.function.BooleanSupplier allowed=()->valid(t)&&!slot.stopped&&!slot.manual.holding(SystemClock.uptimeMillis())&&ContinuousTapPolicy.allow(slot.phases.engine,slot.home,blocked,false,false,true,slot.observedAt,SystemClock.uptimeMillis());
  slot.upgrades.review(samples,slot.observedAt,allowed.getAsBoolean(),display,rect,watch->{float[] point=slot.layout==null?RegionMath.center(slot.phases.engine.regions.get(watch.regionId).bounds,rect.width(),rect.height()):slot.layout.pointRelative(watch,samples,rect.width(),rect.height());if(point!=null){point=point.clone();point[0]+=rect.left;point[1]+=rect.top;}return point;},allowed);
 }
 Slot manualSlot(VirtualWindows.Window w){if(!active||!virtualMode||w==null||slots==null)return null;for(Slot slot:slots)if(slot.pkg.equals(w.pkg))return slot;return null;}
 boolean manualBegin(VirtualWindows.Window w){Slot slot=manualSlot(w);if(slot==null)return false;long now=SystemClock.uptimeMillis();slot.manual.begin(now);pauseMain(slot);slot.home=false;slot.banc.cancel();if(slot.upgrades!=null)slot.upgrades.cancel("手動操作のため一時停止");w.inputLane.cancel();PhaseEngine e=slot.phases.engine;String verify=RecoveryRoute.target(slot.pkg,e.current,slot.phases.customized,e.windowRelative,slot.coinJourney.blocksMain());e.enter(verify,now);slot.lastFrameAt=0;if(s.progressErrors!=null)s.progressErrors.journal.manual(slot.pkg,w.id,token);s.pilot.virtualWindows.state(w,"手動操作中 / この画面の自動入力を一時停止");later(slot,token,150);return true;}
 void manualCancel(VirtualWindows.Window w){Slot slot=manualSlot(w);if(slot==null)return;slot.manual.release(SystemClock.uptimeMillis(),true);later(slot,token,150);}
 void manualTap(VirtualWindows.Window w,float[] point){Slot slot=manualSlot(w);if(slot==null)return;final int run=token,ticket=slot.manual.release(SystemClock.uptimeMillis(),false);final int display=w.id;
  s.h.postDelayed(()->{if(!valid(run)||!slot.manual.current(ticket)||w.closed||w.id!=display)return;s.pilot.virtualWindows.tap(w,point,60,()->valid(run)&&slot.manual.current(ticket)&&!w.closed&&w.id==display,()->{slot.manual.completed(ticket,SystemClock.uptimeMillis());slot.phases.record("仮想画面の手動タップを送信 / 主画面は新しい画像で再確認");later(slot,run,150);},()->{slot.manual.completed(ticket,SystemClock.uptimeMillis());s.pilot.note("仮想画面への手動入力を受け付けませんでした");later(slot,run,150);});},75);
 }
 void pauseMain(Slot slot){slot.mainTaps.pause();if(slot.tapper!=null)slot.tapper.pause("判断側から連打を保留");}
 String recoveryReport(String pkg,long now){if(slots!=null)for(Slot slot:slots)if(slot.pkg.equals(pkg))return "画像照合（状態条件を除く）："+slot.recognition+"\n復帰処理："+slot.adExit.recovery.report(now)+" / "+slot.adExit.popupExits.report()+" / "+slot.adExit.popupReceipt.report()+" / "+slot.displayRecovery.report(now);return "";}
 String tapperReport(String pkg){if(slots!=null)for(Slot slot:slots)if(slot.pkg.equals(pkg))return (slot.tapper==null?"タッパー未開始":slot.tapper.report())+(slot.upgrades==null?"":"\n"+slot.upgrades.report())+(slot.sampleError.isEmpty()?"":"\n領域判定エラー："+slot.sampleError);return "タッパー未開始";}
 void startMainTaps(Slot slot,Rect rect,float[] point,int t){
  boolean blocked=!slot.phases.customized&&(slot.journey.blocksMain()||slot.coinJourney.blocksMain());
  if(!valid(t)||slot.stopped||slot.manual.holding(SystemClock.uptimeMillis())||!ContinuousTapPolicy.allow(slot.phases.engine,slot.home,blocked,false,false,true,slot.observedAt,SystemClock.uptimeMillis())){pauseMain(slot);return;}
  int display=Display.DEFAULT_DISPLAY;
  if(virtualMode){VirtualWindows.Window window=s.pilot.virtualWindows.window(slot.pkg);if(window==null){pauseMain(slot);return;}display=window.id;}
  if(slot.tapper==null)slot.tapper=new Tapper(SystemClock::uptimeMillis,(task,delay)->s.h.postDelayed(task,delay),new AndroidTapDriver(s),new Tapper.Listener(){
   public void state(String state){s.holdInput(s.appLabel(slot.pkg)+"："+state);}
   public void result(Tapper.Command command,int result){if(result==Tapper.COMPLETED){s.count++;s.refresh();}}
  });
  slot.mainTaps.arm(slot.phases.engine,new int[]{rect.left,rect.top,rect.right,rect.bottom},point);
  try{int[] bounds={rect.left,rect.top,rect.right,rect.bottom};slot.tapper.update(new Tapper.Command(slot.pkg,display,t+":"+slot.phases.engine.current+":"+slot.phases.engine.epoch,bounds,s.mainTapPoints(point,bounds,display),s.tapPressMillis(slot.pkg,1),s.effectiveInterval(),slot.observedAt+ContinuousTapPolicy.MAX_FRAME_AGE_MS,true,0,s.contacts().spread));}
  catch(IllegalArgumentException invalid){pauseMain(slot);s.pilot.note(s.appLabel(slot.pkg)+"：タップ位置を再確認");}
 }
 void retryTap(Slot slot,int t,Runnable failed,long delay){slot.home=false;pauseMain(slot);if(!valid(t))return;if(failed!=null)failed.run();else later(slot,t,delay);}
 void tap(Slot slot,Rect rect,float[] p,long duration,int t,Runnable done){tap(slot,rect,p,duration,t,done,null);}
 void tap(Slot slot,Rect rect,float[] p,long duration,int t,Runnable done,Runnable failed){if(!valid(t))return;if(virtualMode){VirtualWindows.Window w=s.pilot.virtualWindows.window(slot.pkg);if(w==null){retryTap(slot,t,failed,500);return;}s.pilot.virtualWindows.tap(w,p,duration,()->valid(t)&&!slot.manual.holding(SystemClock.uptimeMillis()),()->{if(valid(t)){s.count++;done.run();}},()->retryTap(slot,t,failed,500));return;}Rect current=s.pilot.splitLauncher.bounds(slot.pkg);if(current==null||!current.equals(rect)||!s.pilot.splitLauncher.exposed(slot.pkg,current)||!rect.contains((int)p[0],(int)p[1])){retryTap(slot,t,failed,0);return;}if(s.busy){retryTap(slot,t,failed,5);return;}s.touchable(false);s.pilot.panelTouchable(false);s.busy=true;s.h.postDelayed(()->{if(s.guardDialog()){s.busy=false;s.pilot.panelTouchable(true);retryTap(slot,t,failed,500);return;}if(!valid(t)){s.busy=false;s.pilot.panelTouchable(true);return;}Rect latest=s.pilot.splitLauncher.bounds(slot.pkg);if(latest==null||!latest.equals(rect)||!s.pilot.splitLauncher.exposed(slot.pkg,latest)){s.busy=false;s.pilot.panelTouchable(true);retryTap(slot,t,failed,0);return;}Path path=new Path();path.moveTo(p[0],p[1]);GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,s.tapPressMillis(slot.pkg,duration))).build();boolean accepted=s.dispatchTrackedGesture(slot.pkg,gesture,new AccessibilityService.GestureResultCallback(){public void onCompleted(GestureDescription g){s.busy=false;s.pilot.panelTouchable(true);if(valid(t)){s.count++;done.run();}}public void onCancelled(GestureDescription g){s.busy=false;s.pilot.panelTouchable(true);retryTap(slot,t,failed,500);}},s.h);if(!accepted){s.busy=false;s.pilot.panelTouchable(true);retryTap(slot,t,failed,500);}},32);}
 void holdRestore(Slot slot,int t,PhaseEngine.Watch rule,String state,long epoch,String reason){if(!valid(t))return;if(!state.equals(slot.phases.engine.current)||epoch!=slot.phases.engine.epoch){later(slot,t,0);return;}slot.home=false;pauseMain(slot);slot.phases.engine.cancelIfCurrent(rule,state,epoch);if(!slot.phases.customized&&!slot.phases.engine.windowRelative&&PresetRegistry.supports(slot.pkg))slot.phases.engine.enter(RecoveryRoute.target(slot.pkg,state,false,false,slot.coinJourney.blocksMain()),SystemClock.uptimeMillis());if(virtualMode){VirtualWindows.Window display=s.pilot.virtualWindows.window(slot.pkg);if(display!=null)s.pilot.virtualWindows.state(display,reason);}s.pilot.note(s.appLabel(slot.pkg)+"："+reason);later(slot,t,120);}
 void adBack(Slot slot,int t,PhaseEngine.Watch rule,String before,long epoch,Runnable done){
  slot.home=false;pauseMain(slot);Runnable success=()->{if(!valid(t)||!slot.phases.engine.current.equals(before)||slot.phases.engine.epoch!=epoch)return;slot.adExit.sent(SystemClock.uptimeMillis());slot.phases.record("対象画面で広告から戻る / プレイ・インストールは押しません");done.run();};Runnable failed=()->{if(!valid(t)||!slot.phases.engine.current.equals(before)||slot.phases.engine.epoch!=epoch)return;slot.adExit.rejected();slot.phases.engine.cancelIfCurrent(rule,before,epoch);later(slot,t,500);};
  if(virtualMode){VirtualWindows.Window window=s.pilot.virtualWindows.window(slot.pkg);if(window==null){failed.run();return;}s.pilot.virtualWindows.back(window,()->valid(t)&&slot.phases.engine.current.equals(before)&&slot.phases.engine.epoch==epoch,success,failed);return;}
  if(s.busy||s.guardDialog()){slot.phases.engine.cancelIfCurrent(rule,before,epoch);later(slot,t,500);return;}
  if(slot.pkg.equals(s.pilot.packageNow())&&s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))success.run();else failed.run();
 }
 void restore(Slot slot,int t,PhaseEngine.Watch rule,String before,long epoch){
  slot.home=false;pauseMain(slot);long now=SystemClock.uptimeMillis();
  final boolean preset=!slot.phases.customized&&!slot.phases.engine.windowRelative&&PresetRegistry.supports(slot.pkg);
  if(preset&&!slot.adExit.returnAllowed(now)){
   slot.phases.engine.cancelIfCurrent(rule,before,epoch);
   String status=slot.adExit.recovery.status(now);if(status.isEmpty())status="復帰：新しい表示を再確認中";
   if(virtualMode){VirtualWindows.Window waiting=s.pilot.virtualWindows.window(slot.pkg);if(waiting!=null)s.pilot.virtualWindows.state(waiting,status);}
   s.pilot.note(s.appLabel(slot.pkg)+"："+status);later(slot,t,500);return;
  }
  if(!slot.recovery.acquire(now)){
   slot.phases.engine.cancelIfCurrent(rule,before,epoch);
   if(virtualMode){VirtualWindows.Window waiting=s.pilot.virtualWindows.window(slot.pkg);if(waiting!=null)s.pilot.virtualWindows.state(waiting,"復帰要求の間隔待ち / 画面を監視中");}
   later(slot,t,500);return;
  }
  slot.retries++;final boolean restart=preset&&slot.adExit.restartNeeded(now);
  Intent intent=s.getPackageManager().getLaunchIntentForPackage(slot.pkg);
  if(intent==null){holdRestore(slot,t,rule,before,epoch,"登録アプリの起動先を再確認中");return;}
  if(restart)slot.phases.record("通常復帰で表示が変わらないため対象ゲームのタスクを再起動 / "+slot.adExit.recovery.report(now));
  Runnable completed=()->{
   if(!valid(t))return;
   if(!before.equals(slot.phases.engine.current)||epoch!=slot.phases.engine.epoch){later(slot,t,0);return;}
   if(preset)slot.phases.engine.enter(RecoveryRoute.target(slot.pkg,before,false,false,slot.coinJourney.blocksMain()),SystemClock.uptimeMillis());
   else slot.phases.engine.finishIfCurrent(rule,before,epoch,SystemClock.uptimeMillis());
   later(slot,t,0);
  };
  java.util.function.BooleanSupplier current=()->valid(t)&&before.equals(slot.phases.engine.current)&&epoch==slot.phases.engine.epoch&&(!restart||slot.adExit.restartCurrent(SystemClock.uptimeMillis()));
  if(virtualMode){
   VirtualWindows.Window w=s.pilot.virtualWindows.window(slot.pkg);if(preset)slot.adExit.returned(now,restart);
   s.pilot.virtualWindows.restore(w,restart,current,completed,()->holdRestore(slot,t,rule,before,epoch,"仮想画面への復帰要求失敗・監視継続"));return;
  }
  try{
   if(!current.getAsBoolean()){slot.phases.engine.cancelIfCurrent(rule,before,epoch);later(slot,t,0);return;}
   if(preset)slot.adExit.returned(now,restart);
   s.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT|(restart?Intent.FLAG_ACTIVITY_CLEAR_TASK:Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)));completed.run();
  }catch(Exception ex){holdRestore(slot,t,rule,before,epoch,"分割画面への復帰要求失敗・監視継続");}
 }
}
