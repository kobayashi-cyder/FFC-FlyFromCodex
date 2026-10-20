package jp.tapmarker;
import java.io.*;import java.util.*;
public class CircuitWorkspaceTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static float[] original(FlyCircuit c,float[] image){float[] z=new float[c.n],drive=new float[c.n],sum=new float[c.n];for(int i=0;i<c.input.length;i++)drive[c.input[i]]=1.2f*(image[i%image.length]-.5f);for(int t=0;t<10;t++){System.arraycopy(drive,0,sum,0,c.n);for(int e=0;e<c.w.length;e++)sum[c.post[e]]+=c.w[e]*z[c.pre[e]];for(int i=0;i<c.n;i++)z[i]=.75f*z[i]+.25f*(float)Math.tanh(sum[i]);}float[] out=new float[c.output.length];for(int i=0;i<out.length;i++)out[i]=z[c.output[i]];return out;}
 public static void main(String[] args)throws Exception{
  FlyCircuit c=new FlyCircuit(new FileInputStream("assets/circuit.bin"));Random r=new Random(165);float[] first=null;
  for(int trial=0;trial<12;trial++){float[] input=new float[768];for(int i=0;i<input.length;i++)input[i]=r.nextFloat();float[] actual=c.encode(input);check(Arrays.equals(actual,original(c,input)),"workspace resets exactly for every new frame");if(trial==0){first=input;actual[0]=123;}}
  check(Arrays.equals(c.encode(first),original(c,first)),"caller output edits and previous frames cannot contaminate circuit state");
  System.out.println("CircuitWorkspaceTest: "+checks+" checks passed; per-encode large workspace allocations removed");
 }
}
