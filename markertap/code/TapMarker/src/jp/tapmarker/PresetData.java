package jp.tapmarker;
import android.content.res.AssetManager;import org.json.*;import java.util.*;import java.io.*;
/** Cache the small immutable factory references; each caller owns its map and arrays. */
final class PresetData {
 private static final Map<String,Map<String,float[]>> CACHE=new HashMap<>();
 static synchronized Map<String,float[]> read(AssetManager assets,String name)throws Exception{
  Map<String,float[]> stored=CACHE.get(name);
  if(stored==null){
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=assets.open(name)){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
   JSONObject object=new JSONObject(out.toString("UTF-8"));stored=new HashMap<>();Iterator<String> keys=object.keys();
   while(keys.hasNext()){String key=keys.next();JSONArray a=object.optJSONArray(key);if(a!=null)stored.put(key,AppLogic.read(a));}
   if(CACHE.size()>=5)CACHE.clear();CACHE.put(name,stored);
  }
  Map<String,float[]> result=new HashMap<>();for(Map.Entry<String,float[]> e:stored.entrySet())result.put(e.getKey(),e.getValue().clone());return result;
 }
 static synchronized void clear(){CACHE.clear();}
 private PresetData(){}
}
