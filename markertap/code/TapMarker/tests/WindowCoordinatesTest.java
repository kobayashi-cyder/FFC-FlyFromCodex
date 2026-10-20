package jp.tapmarker;
public class WindowCoordinatesTest {
 static void check(boolean b){if(!b)throw new AssertionError("window coordinates");}
 public static void main(String[] args){float[] top=WindowCoordinates.point(0,60,900,900,.5f,.78f),bottom=WindowCoordinates.point(0,1000,900,900,.5f,.76f);check(top[0]==450&&top[1]==762);check(bottom[0]==450&&bottom[1]==1684);float[] shovel=WindowCoordinates.point(0,1000,900,900,.105f,.8115f);check(shovel[0]<200&&shovel[1]>1700&&shovel[1]<1900);check(!WindowCoordinates.same(new int[]{0,60,900,900},new int[]{0,1000,900,900}));float[] edge=WindowCoordinates.point(0,1000,900,900,1,1);check(edge[0]==899&&edge[1]==1899);System.out.println("PASS 5 relative window coordinate checks");}
}
