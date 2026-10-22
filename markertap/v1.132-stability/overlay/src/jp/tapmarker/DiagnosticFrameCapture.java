package jp.tapmarker;

/** Pixel work never holds the journal monitor used by touch/manual controls. */
final class DiagnosticFrameCapture {
 private DiagnosticFrameCapture() {}
 static void frame(DiagnosticJournal journal,String app,int display,int run,int width,int height,
                   RegionSampler.Pixels pixels,long capturedAt,String forced) {
  final DiagnosticJournal.Lane lane;
  final DiagnosticJournal.Selection selection;
  final String kind,observation;
  final long action;
  synchronized(journal) {
   if(journal.closed||pixels==null||width<=0||height<=0)return;
   lane=journal.lane(app,display,run);
   kind=forced==null||forced.isEmpty()?lane.policy.kind(capturedAt,false):forced;
   if(kind.isEmpty())return;
   if(!journal.rawCopies.tryAcquire()){journal.dropped++;return;}
   selection=lane.selection;observation=lane.observation;action=lane.action;
  }
  boolean handedOff=false;
  try {
   final int[] size=DiagnosticCapturePolicy.size(width,height);
   final int[] thumbnail=thumbnail(pixels,width,height,size[0],size[1]);
   synchronized(journal) {
    // Manual input, action, close, or a replacement run invalidates this capture.
    if(journal.closed||journal.lanes.get(app)!=lane||lane.action!=action||lane.selection!=selection)return;
    lane.policy.accepted(capturedAt,kind);
   }
   journal.worker.execute(new DiagnosticJournal$$ExternalSyntheticLambda0(journal,size,thumbnail,
    selection,width,height,app,display,run,capturedAt,kind,observation,lane,action));
   // The preserved encoder releases the permit in its finally block.
   handedOff=true;
  } catch(RuntimeException|OutOfMemoryError failure) {
   synchronized(journal){journal.dropped++;}
  } finally {
   if(!handedOff)journal.rawCopies.release();
  }
 }
 static int[] thumbnail(RegionSampler.Pixels pixels,int width,int height,int outWidth,int outHeight) {
  int[] result=new int[outWidth*outHeight];
  for(int y=0;y<outHeight;y++) {
   int sy=Math.min(height-1,(int)((y+.5)*height/outHeight));
   for(int x=0;x<outWidth;x++) {
    int sx=Math.min(width-1,(int)((x+.5)*width/outWidth));
    result[y*outWidth+x]=pixels.get(sx,sy);
   }
  }
  return result;
 }
}
