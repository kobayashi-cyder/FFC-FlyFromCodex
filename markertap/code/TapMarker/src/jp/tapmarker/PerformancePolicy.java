package jp.tapmarker;

/** Pure policy: preserve image evidence, reduce rendering/copying and input pressure. */
public final class PerformancePolicy {
 public static final int AUTO=0,LIGHT=1,BALANCED=2,FAST=3;
 public static final class Profile {
  public final int mode,width,decodeMs,previewMs,scanMs,minTapMs,busyPollMs,previewWidth;
  public final String name;
  Profile(int mode,String name,int width,int decode,int preview,int scan,int tap,int busy,int previewWidth){
   this.mode=mode;this.name=name;this.width=width;decodeMs=decode;previewMs=preview;scanMs=scan;minTapMs=tap;busyPollMs=busy;this.previewWidth=previewWidth;
  }
 }
 public static final Profile ECO=new Profile(LIGHT,"軽量",540,220,500,350,24,24,270);
 public static final Profile NORMAL=new Profile(BALANCED,"標準",720,140,300,220,12,12,320);
 public static final Profile QUICK=new Profile(FAST,"高速",720,70,180,120,3,6,360);
 public static final Profile F52A=new Profile(LIGHT,"F-52A向け軽量",540,220,500,400,32,24,270);
 public static Profile choose(int mode,boolean lowRam,int memoryClass,long totalMemory,int cores){
  return choose(mode,lowRam,memoryClass,totalMemory,cores,null);
 }
 public static boolean isF52A(String model){return model!=null&&model.replaceAll("[\\s_-]","").equalsIgnoreCase("F52A");}
 public static Profile choose(int mode,boolean lowRam,int memoryClass,long totalMemory,int cores,String model){
  if(mode==LIGHT)return ECO;if(mode==BALANCED)return NORMAL;if(mode==FAST)return QUICK;
  if(isF52A(model))return F52A;
  return lowRam||memoryClass>0&&memoryClass<=128||totalMemory>0&&totalMemory<=3L*1024*1024*1024||cores>0&&cores<=4?ECO:NORMAL;
 }
 public static long startupDelay(Profile profile,int index){return profile==F52A?Math.max(0L,index)*650:0;}
 /** Legacy caller compatibility; recognition profiles no longer cap the input executor. */
 public static int effectiveTap(int requested,Profile p){return Math.max(1,requested);}
 public static long scanInterval(Profile p,long lastCost,int windows,int cores){
  // At least half of each worker's time remains available for drawing and input.
  long factor=Math.max(2,(2L*Math.max(1,windows)+Math.max(1,cores)-1)/Math.max(1,cores));
  return Math.max(p.scanMs,Math.min(1200,Math.max(0,lastCost)*factor));
 }
 public static int[] geometry(Profile p,int screenWidth,int screenHeight,int density,int windows,long heapBytes){
  if(screenWidth<=0||screenHeight<=0||windows<=0)throw new IllegalArgumentException("invalid display geometry");
  int width=Math.min(p.width,screenWidth);double aspect=Math.max(1,Math.min(3.5,screenHeight/(double)screenWidth));
  // Two reusable ARGB arrays per screen. Reserve the rest of the heap for the app.
  long budget=Math.max(8L*1024*1024,Math.min(64L*1024*1024,heapBytes/3));
  int affordable=(int)Math.sqrt(budget/(8.0*windows*aspect));
  if(affordable<Math.min(width,320))throw new IllegalArgumentException("画面数を減らすと開始できます");
  width=Math.min(width,affordable);
  int height=Math.max(width,(int)Math.round(width*aspect));
  return new int[]{width,height,Math.max(72,(int)Math.round(Math.max(72,density)*width/(double)screenWidth))};
 }
 private PerformancePolicy(){}
}
