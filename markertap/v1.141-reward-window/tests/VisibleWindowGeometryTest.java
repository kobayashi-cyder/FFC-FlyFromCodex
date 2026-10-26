package jp.tapmarker;

public final class VisibleWindowGeometryTest {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static final RegionSampler.Pixels pixels=(x,y)->{if(x<0||y<0||x>=1080||y>=2424)throw new AssertionError("out-of-image read");return 0xff80bb60;};
 static void invalid(int[] bounds){try{ContentViewport.locate(1080,2424,pixels,bounds);throw new AssertionError("invalid scope accepted");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("image=1080x2424")&&expected.getMessage().contains("window="),"invalid geometry has diagnostic dimensions");}}
 public static void main(String[] args){
  for(int[] bounds:new int[][]{{0,0,1080,2424},{0,0,1080,2472},{-24,-48,1104,2472},{Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE}}){
   ContentViewport v=ContentViewport.locate(1080,2424,pixels,bounds);
   check(v.left==0&&v.top==0&&v.width==1080&&v.height==2424,"only visible intersection is sampled");
   float[] p=ContentViewport.offset(new float[]{540,1604},v.metadata(),1080,2424);
   check(p!=null&&p[0]==540&&p[1]==1604,"no scaling or duplicate offset");
  }
  ContentViewport v=ContentViewport.locate(1080,2424,pixels,new int[]{120,80,900,2500});
  check(v.left==120&&v.top==80&&v.width==780&&v.height==2344,"visible inset window preserves its origin");
  float[] p=ContentViewport.offset(new float[]{100,100},v.metadata(),1080,2424);
  check(p[0]==220&&p[1]==180,"window origin applied exactly once");
  check(ContentViewport.offset(new float[]{100,100},v.metadata(),1081,2424)==null,"old size cannot supply an input point");
  for(int[] b:new int[][]{{0,0,0,100},{0,0,100,0},{100,0,0,100},{0,100,100,0},{1080,0,1200,200},{0,2424,100,2500},{-100,-100,-1,-1},{0,0,10}})invalid(b);
  try{ContentViewport.locate(0,2424,pixels,null);throw new AssertionError("zero image accepted");}catch(IllegalArgumentException expected){check(true,"zero-sized image rejected");}
  v=ContentViewport.locate(1080,2424,pixels,null);check(v.width==1080&&v.height==2424,"null scope retains known image bounds");
  ContentViewport letterbox=ContentViewport.locate(1440,1000,(x,y)->x<500||x>=940?0xff000000:0xff70bd64,null);
  check(letterbox.left==500&&letterbox.width==440,"paired side-bar behavior retained");
  System.out.println("VisibleWindowGeometryTest: "+checks+" checks passed");
 }
}
