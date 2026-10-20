package jp.tapmarker;
import java.util.Arrays;
public final class PopupControlBoundsTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  int[] scope={0,0,320,718},full={30,610,150,660},cut={30,710,150,760};
  check(Arrays.equals(PopupControlBounds.visible(full,scope,false),full),"ordinary named control preserves its complete bounds");
  check(PopupControlBounds.visible(cut,scope,false)==null,"clipped rewards and ad-start buttons are not accepted");
  check(Arrays.equals(PopupControlBounds.visible(cut,scope,true),new int[]{30,710,150,718}),"named Close uses the exposed eight-pixel strip");
  check(PopupControlBounds.visible(new int[]{30,716,150,760},scope,true)==null,"two exposed pixels do not justify a click");
  check(PopupControlBounds.visible(new int[]{-100,700,100,750},scope,true)==null,"mostly off-screen controls are rejected");
  check(PopupControlBounds.visible(new int[]{10,719,200,760},scope,true)==null,"entirely off-screen controls are rejected");
  check(PopupControlBounds.visible(new int[]{0,500,320,900},scope,true)==null,"a whole panel is not a named Close target");
  check(Arrays.equals(PopupControlBounds.visible(new int[]{130,810,250,860},new int[]{100,100,420,818},true),new int[]{130,810,250,818}),"moved windows preserve physical coordinates");
  System.out.println("PopupControlBoundsTest: "+checks+" checks passed; Android node ownership and clickability still require device validation");
 }
}
