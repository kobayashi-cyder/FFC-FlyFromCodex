package jp.tapmarker;
/** Only the red, outlined badge at the right of the mission button is inspected. */
final class UsagiNotice {
 static boolean visible(int width,int height,float[] transform,RegionSampler.Pixels pixels){
  float[] roi=AdaptiveLayout.map(new float[]{.533f,0,.595f,.034f},transform);
  if(!AdaptiveLayout.inside(roi))return false;int[] b=RegionMath.bounds(roi,width,height);
  int red=0,white=0,dark=0,minX=b[2],minY=b[3],maxX=-1,maxY=-1;
  int dx=Math.max(1,(b[2]-b[0])/45),dy=Math.max(1,(b[3]-b[1])/45);
  for(int y=b[1];y<b[3];y+=dy)for(int x=b[0];x<b[2];x+=dx){int c=pixels.get(x,y),r=c>>16&255,g=c>>8&255,blue=c&255;
   if(r>=180&&g>=30&&g<135&&blue<100&&r-g>70){red++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
   if(r>205&&g>205&&blue>180)white++;if(r<85&&g<85&&blue<85)dark++;
  }
  return red>=8&&white>=3&&dark>=3&&maxX-minX>=width*.012f&&maxY-minY>=height*.007f;
 }
}
