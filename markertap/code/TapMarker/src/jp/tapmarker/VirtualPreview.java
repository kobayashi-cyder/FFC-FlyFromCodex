package jp.tapmarker;

/** Preview geometry only. Display buffers and automation coordinates stay unchanged. */
final class VirtualPreview {
 static final int[] PERCENTAGES={100,75,50,35};
 static int percent(int value){for(int candidate:PERCENTAGES)if(candidate==value)return value;return 100;}

 static int[] size(int baseWidth,int displayWidth,int displayHeight,int barHeight,
                   int screenWidth,int screenHeight,int percentage,boolean minimized,int titleWidth){
  if(baseWidth<=0||displayWidth<=0||displayHeight<=0||barHeight<0||screenWidth<=0||screenHeight<=barHeight)
   throw new IllegalArgumentException("invalid preview geometry");
  int available=Math.max(1,Math.min(screenWidth,(int)Math.floor((screenHeight-barHeight)*(double)displayWidth/displayHeight)));
  int full=Math.min(baseWidth,available);
  if(minimized)return new int[]{Math.max(1,Math.min(full,titleWidth)),barHeight+1};
  int width=Math.max(1,Math.round(full*percent(percentage)/100f));
  return new int[]{width,Math.min(screenHeight,barHeight+Math.max(1,Math.round(width*(float)displayHeight/displayWidth)))};
 }

 /** FIT_CENTER bounds, using the actual displayed thumbnail's aspect ratio. */
 static float[] viewport(int imageWidth,int imageHeight,int viewWidth,int viewHeight){
  if(imageWidth<=0||imageHeight<=0||viewWidth<=0||viewHeight<=0)return null;
  float scale=Math.min(viewWidth/(float)imageWidth,viewHeight/(float)imageHeight);
  float width=imageWidth*scale,height=imageHeight*scale;
  float left=(viewWidth-width)/2,top=(viewHeight-height)/2;
  return new float[]{left,top,left+width,top+height};
 }

 static float[] point(int display,int displayWidth,int displayHeight,int imageWidth,int imageHeight,
                      int viewWidth,int viewHeight,float x,float y){
  if(display<=0||displayWidth<=0||displayHeight<=0||!Float.isFinite(x)||!Float.isFinite(y))return null;
  float[] rect=viewport(imageWidth,imageHeight,viewWidth,viewHeight);
  if(rect==null||x<rect[0]||x>=rect[2]||y<rect[1]||y>=rect[3])return null;
  return VirtualTarget.map(display,displayWidth,displayHeight,(x-rect[0])/(rect[2]-rect[0]),(y-rect[1])/(rect[3]-rect[1]));
 }
 private VirtualPreview(){}
}
