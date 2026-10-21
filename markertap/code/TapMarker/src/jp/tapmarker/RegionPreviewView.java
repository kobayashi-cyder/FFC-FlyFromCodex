package jp.tapmarker;
import android.content.Context;import android.graphics.Bitmap;import android.view.View;import android.widget.*;
import java.util.*;
/** Separate child render nodes let unchanged patches retain their existing bitmap. */
final class RegionPreviewView extends FrameLayout {
 final RegionPreviewData.Cache cache=new RegionPreviewData.Cache();final PreviewConnectome graph=new PreviewConnectome();
 final Map<String,ImageView> views=new LinkedHashMap<>();final Map<String,Bitmap> bitmaps=new LinkedHashMap<>();
 long changedPatches,reusedPatches;
 RegionPreviewView(Context context){super(context);setClipChildren(true);}
 boolean ready(){return cache.capturedAt>0;}
 void discardViews(){for(ImageView child:views.values())child.setImageDrawable(null);removeAllViews();views.clear();bitmaps.clear();}
 void clear(){discardViews();cache.clear();graph.clear();}
 PreviewConnectome.Schedule schedule(RegionPlan plan,int requestedWidth,long capturedAt){
  int width=Math.min(plan.width,requestedWidth);
  int height=Math.max(1,Math.round(width*plan.height/(float)plan.width));
  return graph.schedule(plan.previewKey,width,height,capturedAt);
 }
 void publish(RegionPreviewData data){
  if(data.scope.equals(cache.scope)&&data.capturedAt<cache.capturedAt)return;
  boolean reset=cache.resetFor(data);if(reset)discardViews();
  BitSet changedNodes=new BitSet();int columns=(data.width+PreviewConnectome.SIDE-1)/PreviewConnectome.SIDE;
  for(RegionPreviewData.Patch patch:data.patches){
   int[] pixels=cache.merged(patch);if(pixels==null){reusedPatches++;continue;}
   Bitmap bitmap=bitmaps.get(patch.key());
   if(bitmap==null){bitmap=Bitmap.createBitmap(patch.width,patch.height,Bitmap.Config.ARGB_8888);bitmaps.put(patch.key(),bitmap);}
   // Mutating an existing bitmap avoids an allocation and surface upload for static tiles.
   bitmap.setPixels(pixels,0,patch.width,0,0,patch.width,patch.height);
   changedNodes.set(patch.y/PreviewConnectome.SIDE*columns+patch.x/PreviewConnectome.SIDE);
   changedPatches++;ImageView child=views.get(patch.key());
   if(child==null){child=new ImageView(getContext());child.setScaleType(ImageView.ScaleType.FIT_XY);child.setClickable(false);views.put(patch.key(),child);addView(child,new FrameLayout.LayoutParams(1,1));}
   child.setTag(patch);if(child.getDrawable()==null)child.setImageBitmap(bitmap);else child.invalidate();cache.pixels.put(patch.key(),pixels);
  }
  graph.observed(data.scope,data.width,data.height,data.capturedAt,data.patches,changedNodes);
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
