package jp.tapmarker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

/**
 * Frame-copy work runs without holding DiagnosticJournal's monitor.
 * The immutable thumbnail is queued for encoding on the dedicated,
 * bounded journal worker. No stale pixels escape the frame lease.
 */
final class DiagnosticFrameCapture {
 private DiagnosticFrameCapture(){}

 static void frame(DiagnosticJournal journal,String app,int display,int run,
                   int width,int height,RegionSampler.Pixels pixels,long capturedAt,String forced){
  if(pixels==null||width<=0||height<=0)return;
  final DiagnosticJournal.Lane lane;
  final DiagnosticJournal.Selection selection;
  final String kind,observation;
  final long action;
  synchronized(journal){
   if(journal.closed)return;
   lane=journal.lane(app,display,run);
   kind=forced==null||forced.isEmpty()?lane.policy.kind(capturedAt,false):forced;
   if(kind.isEmpty())return;
   if(!journal.rawCopies.tryAcquire()){journal.dropped++;return;}
   selection=lane.selection;observation=lane.observation;action=lane.action;
  }
  boolean queued=false;
  try{
   int[] size=DiagnosticCapturePolicy.size(width,height);
   int[] thumbnail=thumbnail(pixels,width,height,size[0],size[1]);
   synchronized(journal){
    if(journal.closed||journal.lanes.get(app)!=lane||lane.action!=action||lane.selection!=selection)return;
    lane.policy.accepted(capturedAt,kind);
   }
   journal.worker.execute(()->encode(journal,app,display,run,width,height,capturedAt,
                                      kind,observation,selection,action,lane,size,thumbnail));
   queued=true;
  }catch(RuntimeException|OutOfMemoryError issue){
   synchronized(journal){journal.dropped++;}
  }finally{
   if(!queued)journal.rawCopies.release();
  }
 }

 static int[] thumbnail(RegionSampler.Pixels pixels,int width,int height,int outWidth,int outHeight){
  if(outWidth<=0||outHeight<=0||((long)outWidth)*outHeight>Integer.MAX_VALUE)
   throw new IllegalArgumentException("thumbnail geometry");
  int[] result=new int[outWidth*outHeight];
  for(int y=0;y<outHeight;y++){
   int sy=Math.min(height-1,(int)((y+.5)*height/outHeight));
   for(int x=0;x<outWidth;x++){
    int sx=Math.min(width-1,(int)((x+.5)*width/outWidth));
    result[y*outWidth+x]=pixels.get(sx,sy);
   }
  }
  return result;
 }

 static void encode(DiagnosticJournal journal,String app,int display,int run,int width,int height,
                    long at,String kind,String observation,DiagnosticJournal.Selection selection,
                    long action,DiagnosticJournal.Lane lane,int[] size,int[] copy){
  Bitmap bitmap=null;
  try{
   bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
   bitmap.setPixels(copy,0,size[0],0,0,size[0],size[1]);
   ByteArrayOutputStream raw=new ByteArrayOutputStream();
   if(!bitmap.compress(Bitmap.CompressFormat.JPEG,88,raw))throw new IOException("Diagnostic JPEG encoding failed");
   byte[] marked=null;
   if(Float.isFinite(selection.x)&&Float.isFinite(selection.y)){
    Canvas canvas=new Canvas(bitmap);
    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    float sx=size[0]/(float)width,sy=size[1]/(float)height;
    paint.setColor(Color.CYAN);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(2);
    if(selection.bounds!=null&&selection.bounds.length>=4)
     canvas.drawRect(selection.bounds[0]*sx,selection.bounds[1]*sy,
                     selection.bounds[2]*sx,selection.bounds[3]*sy,paint);
    float x=selection.x*sx,y=selection.y*sy;
    paint.setColor(Color.RED);
    canvas.drawCircle(x,y,8,paint);
    canvas.drawLine(x-12,y,x+12,y,paint);
    canvas.drawLine(x,y-12,x,y+12,paint);
    ByteArrayOutputStream annotated=new ByteArrayOutputStream();
    if(bitmap.compress(Bitmap.CompressFormat.JPEG,90,annotated))marked=annotated.toByteArray();
   }
   JSONObject metadata=new JSONObject();
   metadata.put("app",app).put("display",display).put("run",run)
    .put("frame_uptime_ms",at).put("kind",kind)
    .put("source_width",width).put("source_height",height)
    .put("saved_width",size[0]).put("saved_height",size[1])
    .put("observation",new JSONObject(observation))
    .put("selection",new JSONObject(selection.json));
   DiagnosticArchive.Frame frame=new DiagnosticArchive.Frame(at,raw.toByteArray(),marked,metadata.toString(2));
   synchronized(journal){
    if(journal.closed||journal.lanes.get(app)!=lane)return;
    lane.frames.addLast(frame);
    while(lane.frames.size()>DiagnosticArchive.MAX_FRAMES)lane.frames.removeFirst();
    if(action==lane.action&&(kind.equals("before")||kind.equals("after"))){
     lane.actionFrames.add(frame);
     while(lane.actionFrames.size()>3)lane.actionFrames.remove(0);
    }
   }
  }catch(Exception|OutOfMemoryError issue){
   journal.dropped++;
   Log.w("MarkerTap","Diagnostic frame unavailable",issue);
  }finally{
   if(bitmap!=null)bitmap.recycle();
   journal.rawCopies.release();
  }
 }
}
