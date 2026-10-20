package jp.tapmarker;
import java.util.*;
public final class TransitionMemory {
 final Map<String,Integer> counts=new LinkedHashMap<>();
 static String key(String scope,String from,String to){return scope+"|"+from+"|"+to;}
 public void observe(String scope,String from,String to){if(from==null||from.isEmpty()||to==null||to.isEmpty()||from.equals(to))return;String k=key(scope,from,to);counts.put(k,Math.min(1000000,counts.getOrDefault(k,0)+1));}
 public boolean permits(String scope,String from,String to){return from==null||from.isEmpty()||from.equals(to)||counts.getOrDefault(key(scope,from,to),0)>0;}
}
