package jp.tapmarker;
import android.content.*;import org.json.*;
class AppLogic {
 final Context context;float[] region;float[] curve=new float[0];long duration=500;String prompt="マーカーを連打。遷移を記録。完了で停止";PromptLogic.Plan plan=new PromptLogic.Plan();String pkg="",storageKey="";
 AppLogic(Context c){context=c;}
 void load(String app){pkg=app;storageKey=GameProfiles.key(context,app);region=null;curve=new float[0];duration=500;prompt="マーカーを連打。遷移を記録。完了で停止";try{JSONObject j=new JSONObject(context.getSharedPreferences("regionalLogic",0).getString(storageKey,"{}"));JSONArray a=j.optJSONArray("region");if(a!=null&&a.length()==4)region=read(a);a=j.optJSONArray("curve");if(a!=null)curve=read(a);duration=j.optLong("duration",500);prompt=j.optString("prompt",prompt);plan=PromptLogic.parse(prompt);}catch(Exception e){plan=PromptLogic.parse("マーカーを連打。遷移を記録。完了で停止");}}
 static float[] read(JSONArray a)throws JSONException{float[] r=new float[a.length()];for(int i=0;i<r.length;i++)r[i]=(float)a.getDouble(i);return r;}
 static JSONArray array(float[] f)throws JSONException{JSONArray a=new JSONArray();for(float v:f)a.put((double)v);return a;}
 void save(){try{JSONObject j=new JSONObject();if(region!=null)j.put("region",array(region));j.put("curve",array(curve));j.put("duration",duration);j.put("prompt",prompt);context.getSharedPreferences("regionalLogic",0).edit().putString(storageKey,j.toString()).apply();}catch(Exception e){throw new IllegalStateException("領域・曲線・指示の保存に失敗",e);}}
 String scope(){return storageKey+":"+RegionMath.key(region);}
}
