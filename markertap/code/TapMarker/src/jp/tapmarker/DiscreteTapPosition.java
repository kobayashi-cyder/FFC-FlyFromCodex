package jp.tapmarker;

/** Freeze one integer-pixel contact point for each independent DOWN/UP tap. */
final class DiscreteTapPosition {
 static float coordinate(float value,int lower,int upper){return Math.max(lower,Math.min(upper-1,Math.round(value)));}
 static float[] point(float x,float y,int[] bounds){return new float[]{coordinate(x,bounds[0],bounds[2]),coordinate(y,bounds[1],bounds[3])};}
 static float[] points(float[] input,int[] bounds){float[] out=input.clone();for(int i=0;i<out.length;i+=2){out[i]=coordinate(out[i],bounds[0],bounds[2]);out[i+1]=coordinate(out[i+1],bounds[1],bounds[3]);}return out;}
 private DiscreteTapPosition(){}
}
