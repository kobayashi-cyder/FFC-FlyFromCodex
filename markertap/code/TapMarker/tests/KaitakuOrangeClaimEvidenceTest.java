package jp.tapmarker;

/** Pure Java fixtures: an orange pill alone is not proof of a claimable mission. */
public final class KaitakuOrangeClaimEvidenceTest {
  static int checks;
  static void ok(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
  static final class Frame implements RegionSampler.Pixels {
    final int w=684,h=1536;final int[] data=new int[w*h];
    Frame(){fill(0,0,w,h,0x050505);fill(0,0,w,315,0xd9cda5);
      fill(40,420,645,658,0xffffff);
      fill(58,496,617,533,0xf0b300);
      fill(465,574,622,633,0xf07d2c);
      fill(40,680,645,915,0xffffff);
      fill(58,753,617,790,0x818181);
      fill(450,828,622,885,0xf0b300);
    }
    public int get(int x,int y){return data[y*w+x];}
    void fill(int x0,int y0,int x1,int y1,int color){for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)data[y*w+x]=color;}
  }
  public static void main(String[]args){
    Frame f=new Frame();float[] r=KaitakuOrangeClaimEvidence.find(f.w,f.h,f);
    ok(r!=null&&r[0]<.70f&&r[2]>.90f&&r[1]<.40f&&r[3]>.40f,"filled mission plus orange pill is located");
    f=new Frame();f.fill(465,574,622,633,0xffffff);
    ok(KaitakuOrangeClaimEvidence.find(f.w,f.h,f)==null,"no orange button");
    f=new Frame();f.fill(465,574,622,633,0xf0b300);
    ok(KaitakuOrangeClaimEvidence.find(f.w,f.h,f)==null,"yellow Play does not count as Get");
    f=new Frame();f.fill(58,496,617,533,0x818181);
    ok(KaitakuOrangeClaimEvidence.find(f.w,f.h,f)==null,"gray incomplete progress bar does not authorize Get");
    f=new Frame();f.fill(0,0,684,315,0x070707);
    ok(KaitakuOrangeClaimEvidence.find(f.w,f.h,f)==null,"no daily mission heading structure");
    f=new Frame();f.fill(465,574,622,633,0xffffff);f.fill(465,1290,622,1350,0xf07d2c);
    ok(KaitakuOrangeClaimEvidence.find(f.w,f.h,f)==null,"orange banner outside mission does not count");
    ok(KaitakuRewardClaimGate.allow(new Object(),new float[]{.685f,.374f,.912f,.411f}),"first visual candidate is permitted");
    Object e=new Object();float[] bounds={.685f,.374f,.912f,.411f};
    ok(KaitakuRewardClaimGate.allow(e,bounds),"first named candidate");
    ok(KaitakuRewardClaimGate.allow(e,bounds),"second fresh frame remains eligible");
    KaitakuRewardClaimGate.receipt(e);
    ok(KaitakuRewardClaimGate.allow(e,bounds),"independent receipt permits another claim");
    System.out.println("KaitakuOrangeClaimEvidenceTest: "+checks+" checks passed");
  }
}
