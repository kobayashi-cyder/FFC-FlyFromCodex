package jp.tapmarker;
import java.io.*;
public class FlyCircuit {
 private float[] stateWorkspace,driveWorkspace,sumWorkspace;
 final int n; final int[] pre,post,input,output;final float[] w;
 public FlyCircuit(InputStream stream)throws IOException {DataInputStream d=new DataInputStream(new BufferedInputStream(stream));if(d.readInt()!=0x464c5931)throw new IOException("bad circuit");n=d.readInt();int e=d.readInt(),ni=d.readInt(),no=d.readInt();for(int i=0;i<n;i++)d.readLong();pre=new int[e];post=new int[e];w=new float[e];for(int i=0;i<e;i++){pre[i]=d.readInt();post[i]=d.readInt();w[i]=d.readFloat();}input=new int[ni];output=new int[no];for(int i=0;i<ni;i++)input[i]=d.readInt();for(int i=0;i<no;i++)output[i]=d.readInt();d.close();}
 public synchronized float[] encode(float[] image){if(stateWorkspace==null){stateWorkspace=new float[n];driveWorkspace=new float[n];sumWorkspace=new float[n];}float[] z=stateWorkspace,drive=driveWorkspace,sum=sumWorkspace;java.util.Arrays.fill(z,0);java.util.Arrays.fill(drive,0);for(int i=0;i<input.length;i++)drive[input[i]]=1.2f*(image[i%image.length]-.5f);for(int t=0;t<10;t++){System.arraycopy(drive,0,sum,0,n);for(int e=0;e<w.length;e++)sum[post[e]]+=w[e]*z[pre[e]];for(int i=0;i<n;i++)z[i]=.75f*z[i]+.25f*(float)Math.tanh(sum[i]);}float[] f=new float[output.length];for(int i=0;i<f.length;i++)f[i]=z[output[i]];return f;}
 public static float distance(float[] a,float[] b){if(a.length!=b.length)return Float.POSITIVE_INFINITY;double d=0;for(int i=0;i<a.length;i++){double v=a[i]-b[i];d+=v*v;}return (float)(d/a.length);}
}
