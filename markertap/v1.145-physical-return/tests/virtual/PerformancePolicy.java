package jp.tapmarker;
final class PerformancePolicy {
 static final int LIGHT=1,BALANCED=2,FAST=3;
 static final class Profile { final int mode,scanMs;Profile(int m,int t){mode=m;scanMs=t;} }
 static long scanInterval(Profile p,long cost,int windows,int cores){
   long factor=Math.max(2,(2L*Math.max(1,windows)+Math.max(1,cores)-1)/Math.max(1,cores));
   return Math.max(p.scanMs,Math.min(1200,Math.max(0,cost)*factor));
 }
}
