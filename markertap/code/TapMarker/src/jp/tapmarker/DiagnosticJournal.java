package jp.tapmarker;

import android.content.*;import android.graphics.*;import android.os.*;import android.net.Uri;import org.json.*;
import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;

/** Bounded copies of frames already acquired by MarkerTap; compression never blocks input IO. */
final class DiagnosticJournal {
 final Context context;final Handler main=new Handler(Looper.getMainLooper());
 final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(12),r->{Thread t=new Thread(r,"MarkerTap-diagnostics");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 final Semaphore rawCopies=new Semaphore(4);final Map<String,Lane> lanes=new LinkedHashMap<>();final AtomicInteger serial=new AtomicInteger();
 volatile boolean closed;volatile long dropped;
 static final class Selection {final String json;final float x,y;final float[] bounds;Selection(String json,float x,float y,float[] bounds){this.json=json;this.x=x;this.y=y;this.bounds=bounds==null?null:bounds.clone();}}
 static final Selection EMPTY=new Selection("{}",Float.NaN,Float.NaN,null);
 static final class Lane {
  final String app;final int display,run;final DiagnosticCapturePolicy policy=new DiagnosticCapturePolicy();
  final ArrayDeque<DiagnosticArchive.Frame> frames=new ArrayDeque<>();final List<DiagnosticArchive.Frame> actionFrames=new ArrayList<>();
  Selection selection=EMPTY;long action;String observation="{}";
  Lane(String app,int display,int run){this.app=app;this.display=display;this.run=run;}
 }
 DiagnosticJournal(Context context){this.context=context.getApplicationContext();}
 synchronized Lane lane(String app,int display,int run){Lane lane=lanes.get(app);if(lane==null||lane.display!=display||lane.run!=run){lane=new Lane(app,display,run);lanes.put(app,lane);if(lanes.size()>6)lanes.remove(lanes.keySet().iterator().next());}return lane;}
 void observed(String app,int display,int run,PhaseEngine e,Map<String,float[]> samples,long at,String before,String after,String recovery){
  try{JSONObject json=new JSONObject();json.put("state",e.current).put("state_epoch",e.epoch).put("observed_uptime_ms",at).put("recognition_before_filter",before).put("recognition_after_filter",after).put("recovery",recovery).put("home_evidence",RecoveryEvidence.home(e,samples));synchronized(this){lane(app,display,run).observation=json.toString();}}
  catch(JSONException ignored){}
 }
 void control(String app,int display,int run,PhaseEngine e,PhaseEngine.Watch rule,Map<String,float[]> samples,int width,int height){
  try{JSONObject json=new JSONObject();json.put("rule",rule.id).put("rule_name",rule.name).put("region",rule.regionId).put("state",e.current).put("state_epoch",e.epoch).put("decision_uptime_ms",SystemClock.uptimeMillis());
   float[] point=GamePopupEvidence.point(rule.regionId,samples,width,height);if(point==null)point=AdCloseAutomaton.controlPoint(rule.regionId,samples,width,height);
   float[] bounds=samples.get(rule.regionId+GamePopupEvidence.EVIDENCE_BOUNDS),source=samples.get(rule.regionId+GamePopupEvidence.DETECTOR),score=samples.get(rule.regionId+GamePopupEvidence.VISUAL_DISTANCE);
   json.put("detector",source==null?"registered-rule":GamePopupEvidence.detectorName((int)source[0]));json.put("pixel_distance",score!=null&&Float.isFinite(score[0])?score[0]:JSONObject.NULL);json.put("evidence_bounds",bounds==null?JSONObject.NULL:new JSONArray(bounds));json.put("bounds_kind",source!=null&&(int)source[0]==1?"named-control-visible-bounds":bounds==null?"unavailable":"template-comparison-region");
   synchronized(this){Lane lane=lane(app,display,run);lane.selection=new Selection(json.toString(),point==null?Float.NaN:point[0],point==null?Float.NaN:point[1],bounds);lane.policy.controlPending=true;}
  }catch(JSONException ignored){}
 }
 synchronized void manual(String app,int display,int run){Lane lane=lane(app,display,run);lane.selection=new Selection("{\"detector\":\"manual-preview\"}",Float.NaN,Float.NaN,null);lane.policy.controlPending=true;}
 synchronized void priorityControl(String app,int display,int run){Lane lane=lane(app,display,run);lane.selection=new Selection("{\"detector\":\"priority-control\"}",Float.NaN,Float.NaN,null);lane.policy.controlPending=true;}
 boolean action(String app,int display,int run,String type,float x,float y,long now){
  synchronized(this){Lane lane=lane(app,display,run);if(!lane.policy.beginAction(type,now))return false;lane.action++;lane.actionFrames.clear();try{JSONObject json=new JSONObject(lane.selection.json);json.put("action",type).put("action_uptime_ms",now).put("tap_x",Float.isFinite(x)?x:JSONObject.NULL).put("tap_y",Float.isFinite(y)?y:JSONObject.NULL);lane.selection=new Selection(json.toString(),x,y,lane.selection.bounds);}catch(JSONException ignored){}return true;}
 }
 void frame(String app,int display,int run,int width,int height,RegionSampler.Pixels pixels,long at,String forced){
  final Lane lane;final String kind,observation;final Selection selection;final long action;final int[] size,copy;
  synchronized(this){
   if(closed||pixels==null||width<=0||height<=0)return;
   lane=lane(app,display,run);kind=forced.isEmpty()?lane.policy.kind(at,false):forced;if(kind.isEmpty())return;
   if(!rawCopies.tryAcquire()){dropped++;return;}
   try{size=DiagnosticCapturePolicy.size(width,height);copy=FramePixels.thumbnail(pixels,width,height,size[0],size[1]);}catch(RuntimeException|OutOfMemoryError unavailable){rawCopies.release();dropped++;return;}
   lane.policy.accepted(at,kind);observation=lane.observation;selection=lane.selection;action=lane.action;
  }
  try{worker.execute(()->{Bitmap bitmap=null;try{
   bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);bitmap.setPixels(copy,0,size[0],0,0,size[0],size[1]);ByteArrayOutputStream raw=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,88,raw))throw new IOException("JPEG encoding failed");
   byte[] marked=null;if(Float.isFinite(selection.x)&&Float.isFinite(selection.y)){Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float sx=size[0]/(float)width,sy=size[1]/(float)height;paint.setColor(Color.CYAN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);if(selection.bounds!=null&&selection.bounds.length>=4)canvas.drawRect(selection.bounds[0]*sx,selection.bounds[1]*sy,selection.bounds[2]*sx,selection.bounds[3]*sy,paint);float x=selection.x*sx,y=selection.y*sy;paint.setColor(Color.RED);canvas.drawCircle(x,y,8,paint);canvas.drawLine(x-12,y,x+12,y,paint);canvas.drawLine(x,y-12,x,y+12,paint);ByteArrayOutputStream annotated=new ByteArrayOutputStream();if(bitmap.compress(Bitmap.CompressFormat.JPEG,90,annotated))marked=annotated.toByteArray();}
   JSONObject metadata=new JSONObject();metadata.put("app",app).put("display",display).put("run",run).put("frame_uptime_ms",at).put("kind",kind).put("source_width",width).put("source_height",height).put("saved_width",size[0]).put("saved_height",size[1]).put("observation",new JSONObject(observation)).put("selection",new JSONObject(selection.json));
   DiagnosticArchive.Frame frame=new DiagnosticArchive.Frame(at,raw.toByteArray(),marked,metadata.toString(2));synchronized(this){if(lanes.get(app)!=lane)return;lane.frames.addLast(frame);while(lane.frames.size()>DiagnosticArchive.MAX_FRAMES)lane.frames.removeFirst();if(action==lane.action&&(kind.equals("before")||kind.equals("after"))){lane.actionFrames.add(frame);while(lane.actionFrames.size()>3)lane.actionFrames.remove(0);}}
  }catch(Exception|OutOfMemoryError unavailable){dropped++;android.util.Log.w("MarkerTap","Diagnostic frame unavailable",unavailable);}finally{if(bitmap!=null)bitmap.recycle();rawCopies.release();}});}catch(RejectedExecutionException busy){rawCopies.release();dropped++;}
 }
 String reserve(String app){return "incident-"+System.currentTimeMillis()+"-"+Integer.toHexString(app.hashCode())+"-"+serial.incrementAndGet()+".zip";}
 boolean incident(String app,int display,int run,String name,String report,long detectedAt){
  final Lane lane;synchronized(this){lane=lanes.get(app);}
  try{worker.execute(()->{try{
   List<DiagnosticArchive.Frame> frames=new ArrayList<>();String observation="{}";int missing=0;
   synchronized(this){if(lane!=null&&lane.display==display&&lane.run==run){frames.addAll(lane.actionFrames);for(DiagnosticArchive.Frame frame:lane.frames)if(!frames.contains(frame))frames.add(frame);observation=lane.observation;missing=lane.policy.posts;}}
   frames.sort(Comparator.comparingLong(f->f.at));if(frames.size()>DiagnosticArchive.MAX_FRAMES){List<DiagnosticArchive.Frame> retained=new ArrayList<>();if(lane!=null)synchronized(this){retained.addAll(lane.actionFrames);}for(int i=frames.size()-1;i>=0&&retained.size()<DiagnosticArchive.MAX_FRAMES;i--)if(!retained.contains(frames.get(i)))retained.add(frames.get(i));retained.sort(Comparator.comparingLong(f->f.at));frames=retained;}
   JSONObject manifest=new JSONObject();manifest.put("app",app).put("display",display).put("run",run).put("detected_uptime_ms",detectedAt).put("frame_count",frames.size()).put("unavailable_post_frames",missing).put("dropped_capture_requests",dropped).put("last_observation",new JSONObject(observation)).put("automatic_upload",false);
   File file=DiagnosticArchive.resolve(new File(context.getFilesDir(),"diagnostic-incidents"),name);DiagnosticArchive.write(file,report,manifest.toString(2),frames);
  }catch(Exception|OutOfMemoryError failure){android.util.Log.w("MarkerTap","Diagnostic archive unavailable",failure);context.getSharedPreferences("automationDiagnostics",0).edit().putString("archiveError",failure.getClass().getSimpleName()+": "+failure.getMessage()).apply();}});return true;}catch(RejectedExecutionException busy){dropped++;return false;}
 }
 synchronized boolean current(String app,int display,int run){Lane lane=lanes.get(app);return !closed&&lane!=null&&lane.display==display&&lane.run==run;}
 void close(){closed=true;worker.shutdown();synchronized(this){lanes.clear();}}
 static void share(Context context,String name){
  if(name==null||name.isEmpty()){UiStrings.toast(context,"診断画像を保存中か、画像付き診断がまだありません。少し待って再度開いてください。",1).show();return;}
  try{File file=DiagnosticArchive.resolve(new File(context.getFilesDir(),"diagnostic-incidents"),name);if(!file.isFile()){UiStrings.toast(context,"診断画像を保存中か、画像付き診断がまだありません。少し待って再度開いてください。",1).show();return;}
   Uri uri=new Uri.Builder().scheme("content").authority(context.getPackageName()+".diagnostics").appendPath(file.getName()).build();Intent send=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("MarkerTap diagnostic",uri));context.startActivity(Intent.createChooser(send,UiStrings.text(context,"画像付き診断を共有")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }catch(Exception unavailable){UiStrings.toast(context,"画像付き診断を共有できませんでした。",1).show();}
 }
}
