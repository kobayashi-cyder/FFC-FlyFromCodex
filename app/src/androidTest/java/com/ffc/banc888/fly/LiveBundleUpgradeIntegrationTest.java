package com.ffc.banc888.fly;
import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class LiveBundleUpgradeIntegrationTest {
 @Test public void apkUpgradeAndIncompleteCacheUseBundledAssets() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
   scenario.onActivity(activity->{try {
    JSONObject manifest;
    try(InputStream in=activity.getAssets().open("live-manifest.json")){manifest=new JSONObject(new String(readAll(in),StandardCharsets.UTF_8));}
    String bundle="b_"+manifest.getString("commit").substring(0,12)+"_123456789";
    File dir=new File(activity.getFilesDir(),"dev_live_public/"+bundle);dir.mkdirs();
    for(String name:AppConfig.LIVE_ASSETS)try(InputStream in=activity.getAssets().open(name);FileOutputStream out=new FileOutputStream(new File(dir,name))){out.write(readAll(in));}
    try(FileOutputStream out=new FileOutputStream(new File(dir,"ready.marker"))){out.write(manifest.toString().getBytes(StandardCharsets.UTF_8));}
    android.content.SharedPreferences prefs=activity.getSharedPreferences("banc_dev",Context.MODE_PRIVATE);
    prefs.edit().putInt("installed_apk_version",BuildConfig.VERSION_CODE).putBoolean("live",true).putString("current_bundle",bundle).commit();
    DevLiveManager compatible=new DevLiveManager(activity);assertTrue(compatible.isLive());
    assertTrue(new File(dir,"execution-controls.js").delete());
    prefs.edit().putBoolean("live",true).commit();DevLiveManager incomplete=new DevLiveManager(activity);assertFalse(incomplete.isLive());assertTrue(incomplete.currentPageUrl().contains("/assets/"));
    prefs.edit().putInt("installed_apk_version",BuildConfig.VERSION_CODE-1).putBoolean("live",true).commit();DevLiveManager upgrade=new DevLiveManager(activity);assertFalse(upgrade.isLive());assertEquals(AppConfig.APP_ORIGIN+"/assets/index.html",upgrade.currentPageUrl());assertTrue(dir.isDirectory());
    prefs.edit().putBoolean("live",false).remove("current_bundle").commit();
   }catch(Exception e){throw new AssertionError(e);}});
  }
 }
 private static byte[] readAll(InputStream in) throws Exception {java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();}
}
