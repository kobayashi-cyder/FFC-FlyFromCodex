package jp.tapmarker;
import java.util.*;

/** Exact image sharing within one graph. A separate pool isolates each runtime. */
public final class ReferencePool {
 private static final class Key {
  final float[] value;final int hash;
  Key(float[] value){this.value=value;hash=Arrays.hashCode(value);}
  public int hashCode(){return hash;}
  public boolean equals(Object other){return other instanceof Key&&Arrays.equals(value,((Key)other).value);}
 }
 private final Map<Key,Integer> indexes=new HashMap<>();
 public final List<float[]> values=new ArrayList<>();
 public int store(float[] value){
  if(value==null)return -1;Integer index=indexes.get(new Key(value));if(index!=null)return index;
  float[] copy=value.clone();int next=values.size();values.add(copy);indexes.put(new Key(copy),next);return next;
 }
 public float[] copy(float[] value){return value==null?null:values.get(store(value));}
 private float[][] copyRows(float[][] rows){
  if(rows==null)return null;
  // Row containers remain private to their watch: editors may replace a row.
  float[][] result=new float[rows.length][];for(int i=0;i<rows.length;i++)result[i]=copy(rows[i]);return result;
 }
 public static void share(PhaseEngine engine){
  ReferencePool pool=new ReferencePool();
  for(PhaseEngine.Phase phase:engine.phases.values())for(PhaseEngine.Watch watch:phase.watches){
   watch.reference=pool.copy(watch.reference);
   watch.guardReferences=pool.copyRows(watch.guardReferences);
   watch.runtimeAlternative=pool.copy(watch.runtimeAlternative);
   watch.runtimePairedReferences=pool.copyRows(watch.runtimePairedReferences);
  }
 }
}
