package jp.tapmarker;

public final class RewardV158VirtualHudTest {
  static int checks;
  static void ok(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
  public static final class Source {
    int count,x,y,w,h;
    public void read(int left,int top,int width,int height,int[] into,int stride){
      count++;x=left;y=top;w=width;h=height;
      ok(stride==w&&into.length==w*h,"exact crop, no over-allocation");
      for(int i=0;i<into.length;i++)into[i]=0xffffffff;
    }
  }
  public static void main(String[] args)throws Exception{
    Source s=new Source();
    ok(RewardV158VirtualHud.readHud(s,1080,1920)==-1,
       "unrecognized white HUD does not invent a balance");
    ok(s.count==1,"one bounded region read");
    ok(s.x==(int)(1080*.105f)&&s.y==(int)(1920*.089f),
       "read only upper-left balance region");
    ok(s.w==(int)(1080*.392f)-(int)(1080*.105f),
       "correct width");
    ok(s.h==(int)(1920*.111f)-(int)(1920*.089f),
       "correct height");
    ok(RewardV158VirtualHud.readHud(s,0,1920)==-1,
       "invalid geometry fails closed");
    ok(s.count==1,"invalid geometry never reads framebuffer");
    ok(RewardV158VirtualHud.readHud(null,1080,1920)==-1,
       "null source fails closed");
    ok(s.count==1,"null source never reads framebuffer");
    System.out.println("RewardV158VirtualHudTest: "+checks+" checks passed");
  }
}
