package jp.tapmarker;

/** Pure-Java regressions for the Nov 5 recording: no input without fresh evidence. */
public final class KaitakuVideoAutonomyRegressionTest {
  static int checks;
  static void ok(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
  static final class Frame implements RegionSampler.Pixels {
    final int w=800,h=1800;final int[] pixels=new int[w*h];
    Frame(int color){fill(0,0,1,1,color);}
    void fill(float x0,float y0,float x1,float y1,int c){
      int a=(int)(x0*w),b=(int)(y0*h),endX=(int)(x1*w),endY=(int)(y1*h);
      for(int y=b;y<endY;y++)for(int x=a;x<endX;x++)pixels[y*w+x]=c;
    }
    public int get(int x,int y){return pixels[y*w+x];}
  }
  static Frame home(){
    Frame f=new Frame(0x83d7fb);
    // Real recording: white top HUD, orange main action, bottom TIPS panel.
    f.fill(.18f,.085f,.35f,.115f,0xffffff);
    f.fill(.40f,.720f,.60f,.765f,0xffcb01);
    f.fill(.30f,.890f,.70f,.930f,0xffffff);
    for(float center:new float[]{.20f,.80f}){
      f.fill(center-.115f,.817f,center+.115f,.869f,0x2d2d2d);
      f.fill(center-.113f,.823f,center+.113f,.862f,0xffffff);
    }
    return f;
  }
  static Frame coinChance(){
    Frame f=new Frame(0x252525);
    // Card geometry is taken from the Nov 5 recording, not the earlier
    // synthetic layout which put yellow pixels at the wrong vertical row.
    f.fill(.05f,.28f,.95f,.355f,0xfff0c2);
    f.fill(.05f,.355f,.95f,.80f,0xffffff);
    f.fill(.14f,.660f,.86f,.727f,0xf0b300);
    f.fill(.14f,.727f,.86f,.742f,0x2d2d2d);
    return f;
  }
  static void visual(){
    Frame home=home();
    ok(KaitakuControlVisual.visible(home.w,home.h,home,false),"left upgrade pod visible");
    ok(KaitakuControlVisual.visible(home.w,home.h,home,true),"right auto pod visible");
    ok(KaitakuVideoModalVisual.find(home.w,home.h,home)==null,"normal home is not coin modal");
    // Expanded terrain may cover all blue separators: upgrade remains valid.
    home.fill(.34f,.805f,.66f,.875f,0xbca55b);
    ok(KaitakuControlVisual.visible(home.w,home.h,home,false),"expanded terrain must not hide upgrade");
    ok(KaitakuControlVisual.visible(home.w,home.h,home,true),"expanded terrain must not hide auto upgrade");
    // A modal covering the central main CTA invalidates upgrade evidence.
    home.fill(.44f,.72f,.56f,.76f,0x252525);
    ok(!KaitakuControlVisual.visible(home.w,home.h,home,false),"modal blocks upgrade");
    ok(!KaitakuControlVisual.visible(home.w,home.h,home,true),"modal blocks auto upgrade");
    Frame chance=coinChance();
    float[] dismiss=KaitakuVideoModalVisual.find(chance.w,chance.h,chance);
    ok(dismiss!=null&&dismiss[1]>=.74f&&dismiss[3]<=.80f,"only nonadvertising opt-out targeted");
    ok(!KaitakuControlVisual.visible(chance.w,chance.h,chance,false),"coin modal blocks upgrades");
    chance.fill(.15f,.638f,.85f,.702f,0xffffff);
    ok(KaitakuVideoModalVisual.find(chance.w,chance.h,chance)==null,"missing yellow CTA rejects modal");
    chance=coinChance();chance.fill(.08f,.28f,.92f,.36f,0x303030);
    ok(KaitakuVideoModalVisual.find(chance.w,chance.h,chance)==null,"missing cream header rejects modal");
  }
  static void claim(){
    Object engine=new Object();long t=1_000_000_000L;
    float[] rect={.685f,.374f,.912f,.411f};
    ok(KaitakuRewardClaimGate.allowAt(engine,rect,t),"first Get candidate");
    ok(KaitakuRewardClaimGate.allowAt(engine,rect,t+500_000_000L),"second frame can confirm");
    ok(!KaitakuRewardClaimGate.allowAt(engine,rect,t+2_500_000_000L),"no immediate duplicate after confirmation window");
    ok(KaitakuRewardClaimGate.allowAt(engine,rect,t+8_100_000_000L),"missed tap can retry after safe delay");
    ok(!KaitakuRewardClaimGate.allowAt(engine,rect,t+11_000_000_000L),"retry not every frame");
    ok(KaitakuRewardClaimGate.allowAt(engine,rect,t+16_200_000_000L),"final bounded retry");
    ok(!KaitakuRewardClaimGate.allowAt(engine,rect,t+24_400_000_000L),"retry cap prevents endless tapping");
    KaitakuRewardClaimGate.receipt(engine);
    ok(!KaitakuRewardClaimGate.allowAt(engine,rect,t+40_000_000_000L),"receipt blocks same row indefinitely");
    KaitakuRewardClaimGate.absent(engine);
    ok(KaitakuRewardClaimGate.allowAt(engine,rect,t+41_000_000_000L),"new Play-only row resets claim gate");
    ok(!KaitakuRewardClaimGate.allowAt(engine,new float[]{-.1f,0,1.2f,1.5f},t),"unsafe bounds never allowed");
  }
  static void ads(){
    AdTwoSecondRecovery k=new AdTwoSecondRecovery();boolean fired=false;
    for(int t=1000;t<=75000;t+=1000){
      boolean hit=k.observe(KaitakuPreset.APP,t,t,true,false);
      ok(!hit,"rewarded Kaitaku ad must not be force-stopped before 75 seconds");
    }
    fired=k.observe(KaitakuPreset.APP,76000,76000,true,false);
    ok(fired,"75-second continuously fresh stuck ad may recover");
    AdTwoSecondRecovery regular=new AdTwoSecondRecovery();
    ok(!regular.observe("com.kurashiru.nouen.usagi",1000,1000,true,false),"ordinary ad first frame");
    ok(!regular.observe("com.kurashiru.nouen.usagi",2000,2000,true,false),"ordinary ad one second");
    ok(regular.observe("com.kurashiru.nouen.usagi",3000,3000,true,false),"other games keep 2-second policy");
    AdTwoSecondRecovery broken=new AdTwoSecondRecovery();
    ok(!broken.observe(KaitakuPreset.APP,1000,1000,true,false),"first frame");
    ok(!broken.observe(KaitakuPreset.APP,31000,31000,true,false),"large frame gap restarts consensus");
    ok(!broken.observe(KaitakuPreset.APP,76000,76000,true,false),"large gap cannot trigger kill");
    ok(!broken.observe(KaitakuPreset.APP,77000,77000,false,true),"home cancels ad episode");
  }
  public static void main(String[] args){
    visual();claim();ads();
    System.out.println("KaitakuVideoAutonomyRegressionTest: "+checks+" checks passed; live gestures not tested");
  }
}
