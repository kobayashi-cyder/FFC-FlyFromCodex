package jp.tapmarker;
import java.util.*;

/**
 * Sparse preview activity graph. One node per 32x32 preview patch.
 * Nodes excite four immediate neighbours whenever their pixels change.
 * Quiet nodes retain their cached bitmap and are sampled at bounded intervals.
 *
 * This scheduler NEVER controls game recognition, screen capture or input;
 * its only purpose is to avoid redundant *preview* pixel reads and redraws.
 */
final class PreviewConnectome {
 static final int SIDE=32;
 static final long QUIET_MS=600, DEEP_QUIET_MS=1200;
 static final class Node {
  long lastObserved;
  int stable,heat;
 }
 static final class Schedule {
  final String scope;final int width,height,columns;final long capturedAt;
  final BitSet selected;
  Schedule(String scope,int width,int height,long capturedAt,int columns,BitSet selected){
   this.scope=scope;this.width=width;this.height=height;this.capturedAt=capturedAt;
   this.columns=columns;this.selected=(BitSet)selected.clone();
  }
  boolean includes(int x,int y){return selected.get((y/SIDE)*columns+x/SIDE);}
  int count(){return selected.cardinality();}
 }
 String scope="";int width,height,columns,rows;Node[] nodes=new Node[0];
 long inspected,deferred,changes,neighbourSignals;
 void clear(){scope="";width=height=columns=rows=0;nodes=new Node[0];inspected=deferred=changes=neighbourSignals=0;}
 void reset(String key,int w,int h){
  if(scope.equals(key)&&width==w&&height==h)return;
  clear();scope=key;width=w;height=h;columns=(w+SIDE-1)/SIDE;rows=(h+SIDE-1)/SIDE;
  nodes=new Node[columns*rows];
  for(int i=0;i<nodes.length;i++)nodes[i]=new Node();
 }
 Schedule schedule(String key,int w,int h,long now){
  if(w<=0||h<=0)throw new IllegalArgumentException("invalid preview dimensions");
  reset(key,w,h);BitSet due=new BitSet(nodes.length);
  for(int i=0;i<nodes.length;i++){
   Node n=nodes[i];long period=n.stable>=10?DEEP_QUIET_MS:n.stable>=4?QUIET_MS:0;
   if(n.lastObserved==0||n.heat>0||period==0||now<n.lastObserved||now-n.lastObserved>=period)due.set(i);
  }
  deferred+=nodes.length-due.cardinality();
  return new Schedule(key,w,h,now,columns,due);
 }
 void observed(String key,int w,int h,long at,List<RegionPreviewData.Patch> patches,BitSet changed){
  reset(key,w,h);
  for(RegionPreviewData.Patch patch:patches){
   int idx=patch.y/SIDE*columns+patch.x/SIDE;
   if(idx<0||idx>=nodes.length)continue;
   Node n=nodes[idx];n.lastObserved=at;inspected++;
   if(changed.get(idx)){n.heat=5;n.stable=0;changes++;}
   else{n.stable=Math.min(255,n.stable+1);n.heat=Math.max(0,n.heat-1);}
  }
  // Propagate change events to adjacent preview nodes without drawing them.
  // They will be *sampled* next; only actual changed pixels cause a repaint.
  for(int idx=changed.nextSetBit(0);idx>=0;idx=changed.nextSetBit(idx+1)){
   int x=idx%columns,y=idx/columns;
   if(x>0)signal(idx-1);
   if(x+1<columns)signal(idx+1);
   if(y>0)signal(idx-columns);
   if(y+1<rows)signal(idx+columns);
  }
 }
 private void signal(int idx){nodes[idx].heat=Math.max(nodes[idx].heat,2);neighbourSignals++;}
 String summary(){return "nodes="+nodes.length+" / scan="+inspected+" / deferred="+deferred+
  " / changed="+changes+" / connections="+neighbourSignals;}
}
