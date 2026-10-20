package jp.tapmarker;
public final class ChangeTapDetector {
 float[] baseline;
 public void reset(){baseline=null;}
 public boolean observe(float[] current,float threshold){if(baseline==null){baseline=current.clone();return false;}if(FlyCircuit.distance(baseline,current)>threshold){baseline=current.clone();return true;}return false;}
}
