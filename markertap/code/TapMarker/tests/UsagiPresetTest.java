package jp.tapmarker;
import java.util.*;import java.io.*;
public class UsagiPresetTest {
 static int checks;static final String P=UsagiPreset.PREFIX;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
 static Map<String,float[]> read(DataInputStream in)throws Exception{Map<String,float[]> m=new HashMap<>();int count=in.readInt();for(int i=0;i<count;i++){String key=in.readUTF();float[] a=new float[in.readInt()];for(int n=0;n<a.length;n++)a[n]=in.readFloat();m.put(key,a);}return m;}
 static PhaseEngine e;static Map<String,Map<String,float[]>> frames;
 static PhaseEngine.Watch event(String state,String frame,long time){e.enter(P+state,0);Map<String,float[]> samples=UsagiPreset.missionSamples(e,frames.get(frame));e.observe(samples,time);PhaseEngine.Watch first=e.poll();check(first==null||first.condition==PhaseEngine.TIMEOUT,"first-frame protection "+state);if(first!=null)return first;e.observe(samples,time+600);return e.poll();}
 static void path(String state,String frame,String next,int action){PhaseEngine.Watch w=event(state,frame,100);check(w!=null,"branch "+state+" / "+frame+" "+e.diagnostic);check(w.action==action,"action "+state);e.finish(w,1000);check(e.current.equals(P+next),"destination "+state+" → "+next);}
 public static void main(String[] args)throws Exception{DataInputStream in=new DataInputStream(new FileInputStream(args[0]));Map<String,float[]> refs=read(in);frames=new HashMap<>();int count=in.readInt();for(int i=0;i<count;i++){String name=in.readUTF();frames.put(name,read(in));}in.close();e=new PhaseEngine();e.strictRuntime=true;PhaseEngine.Phase custom=new PhaseEngine.Phase();custom.id="custom";custom.name="custom";custom.terminal=true;e.phases.put(custom.id,custom);check(UsagiPreset.install(e,refs),"install");check(e.phases.get("custom")==custom,"preserve custom");check(e.phases.size()==20,"19 explicit states plus custom");check(e.validate().isEmpty(),"all references and arrows valid: "+e.validate());check(!UsagiPreset.install(e,refs),"idempotence");for(PhaseEngine.Phase p:e.phases.values())check(p.markersDuring==UsagiPreset.isMain(p.id),"markers only main "+p.id);
 path("main","clear","clear",PhaseEngine.WAIT);
 path("main","offline","classify",PhaseEngine.WAIT);
 path("classify","offline","offline",PhaseEngine.WAIT);
 path("offline","offline","verify",PhaseEngine.CENTER);
 path("classify","bonus","bonus",PhaseEngine.WAIT);
 path("bonus","bonus","verify",PhaseEngine.CENTER);
 path("classify","missions-get","missions",PhaseEngine.WAIT);
 path("missions","missions-get","claim-wait",PhaseEngine.CENTER);
 path("claim-wait","reward","reward",PhaseEngine.WAIT);
 path("reward","reward","classify",PhaseEngine.CENTER);
 path("missions","missions-play","verify",PhaseEngine.CENTER);
 path("classify","use","use",PhaseEngine.WAIT);
 path("use","use","ad",PhaseEngine.WAIT);
 path("use","6592-use","use-wait",PhaseEngine.CENTER);
 path("use","ad","classify",PhaseEngine.WAIT);
 path("verify","game","main",PhaseEngine.WAIT);
 path("verify","clear","clear",PhaseEngine.WAIT);
 PhaseEngine.Watch w=event("classify","ad",8000);check(w!=null&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(P+"ad"),"unknown screen goes to CM recovery");
 w=event("verify","reward",7000);check(w!=null&&w.nextPhase.equals(P+"reward"),"visual reward wins over elapsed recovery timeout");
 e.enter(P+"verify",0);e.observe(frames.get("reward"),7000);check(e.poll()==null,"first specific frame holds fallback timer");e.observe(frames.get("reward"),7600);check(e.poll().nextPhase.equals(P+"reward"),"specific confirmation then reward");
 w=event("verify","game",7000);check(w!=null&&w.nextPhase.equals(P+"main"),"confirmed home overrides elapsed timeout");
 w=event("clear","bonus",4000);check(w!=null&&w.nextPhase.equals(P+"bonus"),"bonus overrides disappearance and elapsed timeout");
 w=event("clear","game",4000);check(w!=null&&w.nextPhase.equals(P+"open-missions"),"visual disappearance overrides elapsed timeout");
 e.enter(P+"ad",0);e.observe(Collections.emptyMap(),1500);check(e.poll().action==PhaseEngine.RETURN_APP,"ad uses intent");
 for(String state:new String[]{"error","account"}){e.enter(P+state,0);e.observe(Collections.emptyMap(),1);w=e.poll();check(w.action==PhaseEngine.BACK&&w.nextPhase.equals(P+"verify"),state+" never confirms unsafe action");}
 e.enter(P+"unknown",0);e.observe(Collections.emptyMap(),1);check(e.poll().action==PhaseEngine.STOP,"unknown stops");
 for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch a:p.watches)check(!(a.regionId.equals(P+"dialog")&&a.action==PhaseEngine.CENTER),"no use tap");
 path("main","6590-clear","clear",PhaseEngine.WAIT);
 path("clear","6590-bonus","bonus",PhaseEngine.WAIT);
 path("bonus","6590-bonus","verify",PhaseEngine.CENTER);
 path("open-missions","6590-game","mission-tap",PhaseEngine.WAIT);
 w=event("mission-tap","6590-game",300);check(w!=null&&w.action==PhaseEngine.CENTER&&w.regionId.equals(P+"game"),"6590 after bonus taps mission button, not main marker");e.finish(w,500);check(e.current.equals(P+"mission-opening"),"tap completion requires actual opening confirmation");
 path("missions","missions-get","claim-wait",PhaseEngine.CENTER);
 path("claim-wait","reward","reward",PhaseEngine.WAIT);
 path("reward","reward","classify",PhaseEngine.CENTER);
 path("classify","missions-play","missions",PhaseEngine.WAIT);
 path("missions","missions-play","verify",PhaseEngine.CENTER);
 path("verify","6590-game","main",PhaseEngine.WAIT);
 w=event("mission-tap","6590-bonus",4000);check(w!=null&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(P+"open-missions"),"popup before mission tap prevents blind header tap");
 PhaseEngine.Watch mission=e.rule(P+"ミッション中央をタップ");mission.condition=PhaseEngine.TIMEOUT;mission.regionId=P+"mission-button";mission.nextPhase=P+"missions";mission.reference=null;e.regions.get(P+"mission-button").bounds=new float[]{.35f,.08f,.55f,.115f};e.phases.get(P+"mission-tap").watches.removeIf(a->a.id.equals(P+"ボタン未確認なら再判別"));check(UsagiPreset.upgrade(e,refs.get("mission-button"))&&mission.condition==PhaseEngine.MATCH&&mission.reference!=null,"existing blind mission tap upgraded to visual confirmation");
 PhaseEngine.Watch bonus=e.rule(P+"広告を使わず開始");bonus.nextPhase=P+"open-missions";check(UsagiPreset.upgrade(e)&&bonus.nextPhase.equals(P+"verify"),"old bonus destination is migrated to display confirmation");check(!UsagiPreset.upgrade(e),"upgrade is idempotent");bonus.nextPhase=P+"unknown";check(!UsagiPreset.upgrade(e)&&bonus.nextPhase.equals(P+"unknown"),"customized transition preserved");
 e.enter(P+"main",0);check(e.activeRegions().size()==3,"main scans only three relevant regions without notice badge");e.enter(P+"ad",0);check(e.activeRegions().size()==7,"ad scans only known recovery regions");
 path("classify","6592-use","use",PhaseEngine.WAIT);
 PhaseEngine.Watch cancel=event("use","6592-use",100);check(cancel!=null&&cancel.regionId.equals(P+"cancel")&&cancel.action==PhaseEngine.CENTER,"6592 targets cancel text, never use button");String before=e.current;long epoch=e.epoch;check(e.confirmsAction(cancel,before,epoch,frames.get("6592-use")),"cancel confirmed again immediately before dispatch");check(!e.confirmsAction(cancel,before,epoch,frames.get("6592-ad")),"advertisement replacing modal cancels action");check(!e.confirmsAction(cancel,before,epoch,frames.get("6592-game")),"game replacing modal cancels action");e.finish(cancel,1000);check(e.current.equals(P+"use-wait"),"tap completion enters confirmation state, not main");check(!e.confirmsAction(cancel,before,epoch,frames.get("6592-use")),"old action cannot fire after transition");
 path("use-wait","6592-game","classify",PhaseEngine.WAIT);
 path("classify","6592-game","main",PhaseEngine.WAIT);
 check(UsagiPreset.mayTap(P+"main",true,false,false,false,true,100,200),"confirmed main permits marker taps");
 check(!UsagiPreset.mayTap(P+"main",true,true,false,false,true,100,200),"screenshot pending pauses old marker taps");
 check(!UsagiPreset.mayTap(P+"main",true,false,false,false,true,100,1100),"old screenshot blocks marker taps");
 check(!UsagiPreset.mayTap(P+"main",false,false,false,false,true,100,200),"advertisement image blocks marker taps even while logical state is main");
 check(!UsagiPreset.mayTap(P+"use",true,false,false,false,true,100,200),"dialog state never runs marker loop");
 check(!UsagiPreset.mayTap(P+"main",true,false,true,false,true,100,200),"pending center action excludes marker gesture");
 check(!UsagiPreset.mayTap(P+"main",true,false,false,true,true,100,200),"intent recovery excludes marker gesture");
 check(!UsagiPreset.mayTap(P+"main",true,false,false,false,false,100,200),"different foreground app excludes marker gesture");
 check(!UsagiPreset.mayTap(P+"main",true,false,false,false,true,300,200),"future observation time rejected");
 e.enter(P+"missions",0);e.observe(frames.get("6592-ad"),9000);PhaseEngine.Watch missing=e.poll();check(missing!=null&&missing.action==PhaseEngine.WAIT&&missing.nextPhase.equals(P+"classify"),"unconfirmed mission never blindly presses back");
 PhaseEngine.Watch u=e.rule(P+"use"),d=e.rule(P+"use-disappeared"),m=e.rule(P+"報酬がない場合は戻る");u.regionId=P+"dialog";u.action=PhaseEngine.BACK;u.nextPhase=P+"classify";u.reference=refs.get("dialog");d.action=PhaseEngine.RETURN_APP;d.nextPhase=P+"verify";m.action=PhaseEngine.BACK;m.nextPhase=P+"verify";e.phases.remove(P+"use-wait");e.regions.remove(P+"cancel");e.phases.get(P+"use").watches.removeIf(a->a.id.equals(P+"キャンセル非表示はCM処理")||a.id.equals(P+"キャンセル未確認"));check(UsagiPreset.upgrade(e,refs),"1.33 incorrect actions migrated");check(u.action==PhaseEngine.CENTER&&u.regionId.equals(P+"cancel")&&u.nextPhase.equals(P+"use-wait"),"migration replaces ignored back with cancel center");check(d.action==PhaseEngine.WAIT&&d.nextPhase.equals(P+"classify")&&!d.fallback,"disappearance does not blindly launch Chrome");check(m.action==PhaseEngine.WAIT&&m.nextPhase.equals(P+"classify"),"migration removes blind mission back");check(!UsagiPreset.upgrade(e,refs),"action migration idempotent");check(e.validate().isEmpty(),"migrated actions have valid regions and references");
Map<String,float[]> purchase=new HashMap<>(frames.get("6594-ready"));purchase.remove(P+"mission-notice");frames.put("purchase-no-notice",purchase);path("main","purchase-no-notice","amount-wait",PhaseEngine.CENTER);
 e.enter(P+"main",0);UsagiPreset.prepareAmount(e,frames.get("6594-ready"));path("amount-wait","6594-upgraded","classify",PhaseEngine.WAIT);
 e.enter(P+"main",0);PhaseEngine.Watch amount=e.rule(P+"amount-tap");check(e.confirmsAction(amount,P+"main",e.epoch,frames.get("6594-ready")),"available upgrade confirms before tap");check(e.confirmsAction(amount,P+"main",e.epoch,frames.get("6594-upgraded")),"necessary label works with two digit cost");check(!e.confirmsAction(amount,P+"main",e.epoch,frames.get("6594-insufficient")),"CM upgrade offer never tapped");check(!e.confirmsAction(amount,P+"main",e.epoch,frames.get("6594-bonus")),"bonus modal never treated as upgrade");
 path("ad","6594-bonus","bonus",PhaseEngine.WAIT);path("ad","reward","reward",PhaseEngine.WAIT);path("ad","game","main",PhaseEngine.WAIT);
 e.phases.remove(P+"amount-wait");e.phases.get(P+"main").watches.removeIf(a->a.id.equals(P+"amount-tap")||a.id.equals(P+"amount-monitor"));e.phases.get(P+"ad").watches.removeIf(a->a.id.startsWith(P+"ad-"));e.rule(P+"Chrome経由で登録アプリへ").fallback=false;check(UsagiPreset.upgrade(e,refs),"1.34 amount and advertisement migration");check(e.phases.containsKey(P+"amount-wait")&&e.rule(P+"ad-bonus")!=null,"existing profile gets new branches");check(!UsagiPreset.upgrade(e,refs),"1.35 migration idempotent");
path("missions","6596-play","verify",PhaseEngine.CENTER);PhaseEngine.Watch play=e.rule(P+"未達成ならゲームへ戻る");play.action=PhaseEngine.BACK;check(UsagiPreset.upgrade(e,refs)&&play.action==PhaseEngine.CENTER,"old back action migrated to play center");check(!UsagiPreset.upgrade(e,refs),"play migration idempotent");e.enter(P+"missions",0);check(e.confirmsAction(play,e.current,e.epoch,frames.get("missions-play")),"play visible before return tap");check(!e.confirmsAction(play,e.current,e.epoch,frames.get("reward")),"reward popup blocks play tap");
path("claim-wait","6598-play","verify",PhaseEngine.CENTER);path("classify","6598-play","missions",PhaseEngine.WAIT);path("missions","6598-play","verify",PhaseEngine.CENTER);path("clear","6598-bonus","bonus",PhaseEngine.WAIT);e.enter(P+"classify",0);e.observe(frames.get("6592-ad"),3000);check(e.poll()==null,"loading screen no longer launches Chrome after 2.5 seconds");
w=event("main","6598-main",100);check(w==null||!w.nextPhase.equals(P+"open-missions"),"unfinished game with badge never opens mission checks");check(e.rule(P+"mission-notice")==null,"badge has no mission transition");path("open-missions","6598-main","mission-tap",PhaseEngine.WAIT);path("mission-tap","6598-main","mission-opening",PhaseEngine.CENTER);
e.enter(P+"main",0);check(!UsagiPreset.missionSamples(e,frames.get("6598-main"),true).containsKey(P+"mission-notice"),"processed notice does not reopen missions every main frame");
path("open-missions","6601-missions","missions",PhaseEngine.WAIT);path("mission-tap","6601-missions","missions",PhaseEngine.WAIT);path("missions","6601-missions","claim-wait",PhaseEngine.CENTER);check(ScanPlan.select(e,P+"get",true).size()==1,"get preflight scans only target");check(ScanPlan.select(e,P+"mission-button",true).size()==2,"mission tap scans button and home");check(ScanPlan.select(e,P+"amount-ready",true).size()==3,"upgrade scans target home and level");check(ScanPlan.select(e,P+"get",false).size()==1,"custom app preflight only target");
AdInterruption interruption=new AdInterruption();for(String state:new String[]{"main","open-missions","mission-tap","missions","claim-wait","reward","amount-wait"}){e.enter(P+state,0);interruption.reset();check(!interruption.observe(e,frames.get("6603-ad"),100),"first ad frame waits "+state);check(!interruption.observe(e,frames.get("6603-ad"),800),"brief transition waits "+state);check(!interruption.observe(e,frames.get("6603-ad"),800),"duplicate screenshot ignored "+state);check(interruption.observe(e,frames.get("6603-ad"),2700),"persistent irregular ad interrupts "+state);}
 e.enter(P+"main",0);interruption.reset();interruption.observe(e,frames.get("6603-ad"),100);check(!interruption.observe(e,frames.get("6603-main"),2700),"home return cancels ad suspicion");e.enter(P+"open-missions",2800);check(!interruption.observe(e,frames.get("6601-missions"),3000),"known mission does not trigger ad recovery");e.enter(P+"reward",3100);check(!interruption.observe(e,frames.get("6603-reward"),3300),"known reward first frame");check(!interruption.observe(e,frames.get("6603-reward"),4000),"known reward second frame");check(!interruption.observe(e,frames.get("6603-reward"),6500),"known reward never interrupted after grace");for(String state:new String[]{"account","error","unknown"}){e.enter(P+state,0);interruption.reset();interruption.observe(e,frames.get("6603-ad"),100);interruption.observe(e,frames.get("6603-ad"),800);check(!interruption.observe(e,frames.get("6603-ad"),3000),"protected state keeps its handling "+state);}
path("main","6605-clear","clear",PhaseEngine.WAIT);check(TransitionTiming.remaining(-1,100)==0,"first capture has no waiting");check(TransitionTiming.remaining(100,100)==350,"capture minimum interval enforced");check(TransitionTiming.remaining(100,300)==150,"only remaining interval awaited");check(TransitionTiming.remaining(100,450)==0,"no wait at capture deadline");check(TransitionTiming.remaining(100,900)==0,"no extra wait after slow capture");for(PhaseEngine.Phase state:e.phases.values())for(PhaseEngine.Watch a:state.watches)if(a.id.startsWith(P))check(a.waitMs==0,"preset transition has no post-action wait");
path("mission-opening","missions-get","missions",PhaseEngine.WAIT);
 path("mission-opening","6601-missions","missions",PhaseEngine.WAIT);
 path("mission-opening","6598-main","mission-tap",PhaseEngine.WAIT);
 path("mission-opening","reward","reward",PhaseEngine.WAIT);
 path("mission-opening","6590-bonus","bonus",PhaseEngine.WAIT);
 e.enter(P+"mission-opening",0);e.observe(frames.get("6603-ad"),100);check(e.poll()==null,"advertisement never counts as mission opening");
 Map<String,float[]> overlap=new HashMap<>(frames.get("6601-missions"));overlap.put(P+"game",refs.get("game"));
 e.enter(P+"open-missions",0);e.observe(overlap,100);e.poll();e.observe(overlap,500);PhaseEngine.Watch overlapResult=e.poll();check(overlapResult!=null&&overlapResult.nextPhase.equals(P+"missions"),"actual mission heading wins over retained home header");
 e.enter(P+"mission-opening",0);e.observe(overlap,100);e.poll();e.observe(overlap,500);overlapResult=e.poll();check(overlapResult!=null&&overlapResult.nextPhase.equals(P+"missions"),"opening confirmation wins over retry");
 mission=e.rule(P+"ミッション中央をタップ");mission.nextPhase=P+"missions";e.phases.remove(P+"mission-opening");check(UsagiPreset.upgrade(e,refs)&&mission.nextPhase.equals(P+"mission-opening"),"existing profile gets confirmed opening and retry");check(!UsagiPreset.upgrade(e,refs),"opening migration idempotent");
 float[] point=UsagiPreset.tapCenter(e,mission,1000,2000);check(Math.abs(point[0]-380)<1&&Math.abs(point[1]-200)<1,"mission tap shifted left to flame icon");
 e.enter(P+"mission-tap",0);for(PhaseEngine.Region scanned:e.activeRegions())check(!scanned.id.equals(P+"mission-touch"),"tap target adds no scan region");
 String originalWatchRegion=mission.regionId;mission.regionId=P+"mission-button";float[] original=e.regions.get(P+"mission-button").bounds;e.regions.get(P+"mission-button").bounds=new float[]{.4f,.1f,.6f,.2f};point=UsagiPreset.tapCenter(e,mission,1000,2000);check(point[0]==500&&point[1]==300,"user modified mission region retains its center");e.regions.get(P+"mission-button").bounds=original;mission.regionId=originalWatchRegion;
 Map<String,float[]> altered=new HashMap<>(frames.get("6598-main"));float[] noisy=altered.get(P+"mission-button").clone();java.util.Arrays.fill(noisy,1f);altered.put(P+"mission-button",noisy);
 e.enter(P+"mission-tap",0);e.observe(altered,100);check(e.poll()==null,"home still needs two fresh frames");e.observe(altered,500);PhaseEngine.Watch gate=e.poll();check(gate!=null&&gate.action==PhaseEngine.CENTER,"changed button pixels no longer block mission tap");check(e.confirmsAction(gate,e.current,e.epoch,altered),"changed text also passes preflight using home reference");check(!e.confirmsAction(gate,e.current,e.epoch,frames.get("6603-ad")),"ad pixels still cancel mission tap");
 check(ScanPlan.select(e,gate.regionId,true).size()==1,"mission preflight now scans only fixed home header");
 System.out.println("PASS "+checks+" checks including recorded-video ROI replay, popup branches, fallback timers, safety states and no use tap. Device not tested.");}
}
