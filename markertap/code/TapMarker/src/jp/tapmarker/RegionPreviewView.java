package jp.tapmarker;
import android.content.Context;import android.graphics.Bitmap;import android.view.View;import android.widget.*;
import java.util.*;
/** Separate child render nodes let unchanged patches retain their existing bitmap. */
final class RegionPreviewView extends FrameLayout {
 final RegionPreviewData.Cache cache=new RegionPreviewData.Cache();final Map<String,ImageView> views=new LinkedHashMap<>();
 long changedPatches,reusedPatches;
 RegionPreviewView(Context context){super(context);setClipChildren(true);}
 boolean ready(){return cache.capturedAt>0;}
 void clear(){removeAllViews();views.clear();cache.clear();}
 void publish(RegionPreviewData data){
  if(data.scope.equals(cache.scope)&&data.capturedAt<cache.capturedAt)return;
  boolean reset=cache.resetFor(data);if(reset){removeAllViews();views.clear();}
  for(RegionPreviewData.Patch patch:data.patches){
   int[] pixels=cache.merged(patch);if(pixels==null){reusedPatches++;continue;}
   Bitmap bitmap=Bitmap.createBitmap(pixels,patch.width,patch.height,Bitmap.Config.ARGB_8888);
   changedPatches++;ImageView child=views.get(patch.key());
   if(child==null){child=new ImageView(getContext());child.setScaleType(ImageView.ScaleType.FIT_XY);child.setClickable(false);views.put(patch.key(),child);addView(child,new FrameLayout.LayoutParams(1,1));}
   child.setTag(patch);child.setImageBitmap(bitmap);cache.pixels.put(patch.key(),pixels);
  }
  cache.capturedAt=data.capturedAt;if(reset||isLayoutRequested())requestLayout();
 }
 @Override protected void onMeasure(int widthSpec,int heightSpec){
  super.onMeasure(widthSpec,heightSpec);float[] viewport=VirtualPreview.viewport(cache.nativeWidth,cache.nativeHeight,getMeasuredWidth(),getMeasuredHeight());if(viewport==null)return;
  for(ImageView child:views.values()){RegionPreviewData.Patch p=(RegionPreviewData.Patch)child.getTag();
   int w=Math.max(1,Math.round((p.x+p.width)*(viewport[2]-viewport[0])/cache.width)-Math.round(p.x*(viewport[2]-viewport[0])/cache.width));
   int h=Math.max(1,Math.round((p.y+p.height)*(viewport[3]-viewport[1])/cache.height)-Math.round(p.y*(viewport[3]-viewport[1])/cache.height));
   child.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
  }
 }
 @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
  float[] viewport=VirtualPreview.viewport(cache.nativeWidth,cache.nativeHeight,getWidth(),getHeight());if(viewport==null)return;
  for(ImageView child:views.values()){RegionPreviewData.Patch p=(RegionPreviewData.Patch)child.getTag();
   int x=Math.round(viewport[0]+p.x*(viewport[2]-viewport[0])/cache.width),y=Math.round(viewport[1]+p.y*(viewport[3]-viewport[1])/cache.height);
   int r=Math.round(viewport[0]+(p.x+p.width)*(viewport[2]-viewport[0])/cache.width),b=Math.round(viewport[1]+(p.y+p.height)*(viewport[3]-viewport[1])/cache.height);
   child.layout(x,y,r,b);
  }
 }
}
