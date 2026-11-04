package jp.tapmarker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure host regressions for three user screenshots dated 2026-11-05.
 * Frame patterns are compact probes, not uploaded screenshots.
 * A passing test proves neither input delivery nor reward credit.
 */
public final class KaitakuScreenshots20261105RegressionTest {
  static int checks;
  static void ok(boolean pass,String msg){checks++;if(!pass)throw new AssertionError(msg);}
  static final class Frame implements RegionSampler.Pixels {
    final int w=684,h=1536;
    final int[] data=new int[w*h];
    Frame(int color){Arrays.fill(data,color);}
    void fill(float a,float b,float c,float d,int value){
      int x0=(int)(a*w),y0=(int)(b*h),x1=(int)(c*w),y1=(int)(d*h);
      for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)data[y*w+x]=value;
    }
    public int get(int x,int y){return data[y*w+x];}
  }
  static Frame ad(){
    return new Frame(0x1450a3);
  }
  static Frame mission(){
    Frame f=new Frame(0x000000);
    for(float[] row:new float[][]{{.50f,.55f},{.66f,.714f},{.83f,.88f}}){
      float bar=row[0],play=row[1];
      f.fill(.05f,bar-.075f,.94f,play+.035f,0xffffff);
      f.fill(.10f,bar-.013f,.90f,bar+.013f,0x818181);
      f.fill(.68f,play-.027f,.91f,play+.022f,0xf0b300);
    }
    return f;
  }
  static Frame boost(){
    Frame f=new Frame(0x151515);
    f.fill(.07f,.29f,.93f,.42f,0xfff1c2);
    f.fill(.07f,.42f,.93f,.775f,0xffffff);
    f.fill(.14f,.648f,.86f,.723f,0x2d2d2d);
    f.fill(.15f,.65f,.85f,.715f,0xf0b300);
    return f;
  }
  static void visual(){
    Frame a=ad(),m=mission(),b=boost();
    ok(KaitakuMissionVisual.visible(m.w,m.h,m),"two or more mission Play rows");
    ok(!KaitakuMissionVisual.visible(a.w,a.h,a),"Meta 2/2 ad is not mission");
    ok(!KaitakuMissionVisual.visible(b.w,b.h,b),"tap modal is not mission");
    float[] c=KaitakuTapBoostModalVisual.find(b.w,b.h,b);
    ok(c!=null&&c[1]>=.64f&&c[3]<=.72f,"verified button interior, not cancel");
    ok(KaitakuTapBoostModalVisual.find(m.w,m.h,m)==null,"mission Play buttons not confused with modal");
    ok(KaitakuTapBoostModalVisual.find(a.w,a.h,a)==null,"Meta ad not confused with modal");
    ok(KaitakuVideoModalVisual.find(b.w,b.h,b)==null,"tap modal is not coin-chance opt-out");
    // The upper floating panel can hide the title but MUST NOT be used as
    // reason to remove the independent, visible lower anchors.
    b.fill(.18f,.02f,.96f,.327f,0xf6f7f9);
    ok(KaitakuTapBoostModalVisual.find(b.w,b.h,b)!=null,
       "top overlay does not obscure the modal's 14 lower anchors");
    b.fill(.17f,.645f,.87f,.710f,0xffffff);
    ok(KaitakuTapBoostModalVisual.find(b.w,b.h,b)==null,
       "missing yellow ad CTA blocks candidate");
  }
  static void policy(){
    float[] adRect={.16f,.64f,.84f,.71f};
    String page="タップ２倍 1タップで進む量が２倍！";
    KaitakuVideoModalPolicy.Decision d=KaitakuVideoModalPolicy.decide(page,
       Arrays.asList(new KaitakuVideoModalPolicy.Hit("使用する",adRect),
                     new KaitakuVideoModalPolicy.Hit("キャンセル",new float[]{.35f,.735f,.65f,.78f})));
    ok(d.result==KaitakuVideoModalPolicy.Result.START_AD,"tap boost means START_AD, not cancel");
    ok(Arrays.equals(d.bounds,adRect),"named ad bounds retained");
    ok(KaitakuVideoModalPolicy.decide(page,Arrays.asList(
        new KaitakuVideoModalPolicy.Hit("キャンセル",new float[]{.35f,.735f,.65f,.78f})))
        .result==KaitakuVideoModalPolicy.Result.HOLD,"cancel-only is not proof of boost");
    ok(KaitakuVideoModalPolicy.decide("タップ2倍を3回使用する",Arrays.asList(
        new KaitakuVideoModalPolicy.Hit("使用する",adRect)))
        .result==KaitakuVideoModalPolicy.Result.NONE,"mission title alone never starts ad");
    ok(KaitakuVideoModalPolicy.decide(page,Arrays.asList(
        new KaitakuVideoModalPolicy.Hit("使用する",new float[]{.2f,.05f,.8f,.1f})))
        .result==KaitakuVideoModalPolicy.Result.HOLD,"ad button outside modal rejected");
  }
  static void phases(){
    PhaseEngine e=new PhaseEngine();
    ok(KaitakuPreset.install(e),"Kaitaku stock phase graph builds");
    PhaseEngine.Watch timeout=e.rule(KaitakuPreset.P+"ad-timeout");
    ok(timeout!=null&&timeout.timeoutMs>=90000,
       "an ongoing 2-of-2 rewarded ad is not force-returned at 30s");
    e.strictRuntime=true;
    e.enter(KaitakuPreset.P+"control-wait",0);
    Map<String,float[]> s=new HashMap<>();
    s.put(KaitakuPreset.HOME,new float[]{0});
    s.put(KaitakuPreset.P+"tap-ad-start",new float[]{1});
    e.observe(s,100);
    ok(e.poll()==null,"first modal frame does not start ad");
    e.observe(s,200);
    PhaseEngine.Watch w=e.poll();
    ok(w!=null&&w.id.equals(KaitakuPreset.P+"control-wait-tap-ad-start"),
       "second fresh modal frame starts ad");
    e.finish(w,210);
    ok(e.current.equals(KaitakuPreset.P+"ad"),"ad launch moves into ad phase");
    Map<String,float[]> m=new HashMap<>();
    m.put(KaitakuPreset.HOME,new float[]{0});
    m.put(KaitakuPreset.P+"mission-page",new float[]{1});
    e.observe(m,500);
    ok(e.poll()==null,"first post-ad mission frame does not advance");
    e.observe(m,600);
    w=e.poll();
    ok(w!=null&&w.id.equals(KaitakuPreset.P+"ad-mission-page"),
       "two post-ad mission frames resume missions");
    e.finish(w,610);
    ok(e.current.equals(KaitakuPreset.P+"missions"),"no stale ad phase on visible mission");
  }
  static void recorded98Seconds(){
    // Synthesized geometry derived from the uploaded 1080x2424 recording;
    // no personal screen pixels/advertising IDs are committed to the repo.
    Frame coin=new Frame(0x1f3116);
    coin.fill(.15f,.255f,.85f,.316f,0xeebde4);
    coin.fill(.15f,.316f,.85f,.655f,0xffffff);
    coin.fill(.22f,.552f,.78f,.605f,0xf0b300);
    coin.fill(.29f,.607f,.71f,.620f,0x2d2d2d);
    float[] opt=KaitakuVideoModalVisual.find(coin.w,coin.h,coin);
    ok(opt!=null&&opt[1]>=.615f&&opt[3]<=.66f,
       "recording coin popup -> only nonadvertising start line");
    ok(KaitakuTapBoostModalVisual.find(coin.w,coin.h,coin)==null,
       "coin chance must not be mistaken for tap-ad-start");

    Frame tap=new Frame(0x1f3116);
    tap.fill(.07f,.18f,.93f,.29f,0xeebde4);
    tap.fill(.07f,.29f,.93f,.67f,0xffffff);
    tap.fill(.17f,.515f,.83f,.581f,0xf0b300);
    tap.fill(.23f,.588f,.77f,.599f,0x2d2d2d);
    float[] start=KaitakuTapBoostModalVisual.find(tap.w,tap.h,tap);
    ok(start!=null&&start[1]>=.51f&&start[3]<=.59f,
       "recording tap2x popup -> Ad usage button, never cancel");
    ok(KaitakuVideoModalVisual.find(tap.w,tap.h,tap)==null,
       "tap2x must not be dismissed as coin-up");

    Frame mission=new Frame(0x161616);
    mission.fill(0,.13f,1,.22f,0xcba0c2);
    mission.fill(.05f,.29f,.95f,.50f,0xfea52a);
    mission.fill(.04f,.53f,.94f,.70f,0xffffff);
    mission.fill(.10f,.58f,.90f,.614f,0x818181);
    mission.fill(.68f,.63f,.918f,.684f,0xf0b300);
    ok(KaitakuMissionVisual.firstStagePlay(mission.w,mission.h,mission)!=null,
       "recording gray progress, first mission stage Play, pink title");
    ok(KaitakuOrangeClaimEvidence.find(mission.w,mission.h,mission)==null,
       "uncompleted stage Play is not a claimable Get");
    PhaseEngine e=new PhaseEngine();
    ok(KaitakuPreset.install(e),"graph available for new geometry");
    e.current=KaitakuPreset.P+"missions";
    KaitakuActionEvidence actions=new KaitakuActionEvidence(new HashMap<String,float[]>());
    KaitakuEvidence.Result page=new KaitakuEvidence.Result(false,false,new float[]{1,1,0,0});
    Map<String,float[]> out=new HashMap<>();
    actions.sample(e,page,mission.w,mission.h,mission,1000,out);
    ok(KaitakuActionEvidence.present(out,"play"),"stage Play is actionable only in mission phase");
    ok(!KaitakuActionEvidence.present(out,"get"),"non-Get row remains blocked");

    // Switch to a completed stage in the same video layout. Filled bar +
    // independently orange button must beat the generic mission-page return.
    mission.fill(.10f,.58f,.90f,.614f,0xf0b300);
    mission.fill(.68f,.630f,.918f,.684f,0xf39121);
    ok(KaitakuMissionVisual.firstStagePlay(mission.w,mission.h,mission)==null,
       "completed orange Get is not a Play button");
    ok(KaitakuOrangeClaimEvidence.find(mission.w,mission.h,mission)!=null,
       "completed video-layout stage creates a Get candidate");
    out.clear();actions.sample(e,page,mission.w,mission.h,mission,1200,out);
    ok(KaitakuActionEvidence.present(out,"get"),
       "Get is emitted before generic mission recognition");
    ok(!KaitakuActionEvidence.present(out,"play"),
       "Get and Play remain mutually exclusive");

    // Ads and ordinary gameplay never authorize any of these four gestures.
    Frame empty=ad();out.clear();
    actions.sample(e,page,empty.w,empty.h,empty,1300,out);
    for(String key:new String[]{"get","play","close","tap-ad-start"})
      ok(!KaitakuActionEvidence.present(out,key),"ad is not actionable "+key);
  }
  public static void main(String[]args){
    visual();policy();phases();recorded98Seconds();
    System.out.println("KaitakuScreenshots20261105RegressionTest: "+checks+
      " checks passed; physical gestures, full ads and rewards unverified");
  }
}
