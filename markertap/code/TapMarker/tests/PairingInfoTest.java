package jp.tapmarker;
import java.util.Arrays;
public final class PairingInfoTest {
 static int checks;
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static PairingInfo parse(String...texts){return PairingInfo.parse("com.android.settings",Arrays.asList(texts));}
 public static void main(String[] args){
  PairingInfo jp=parse("ペア設定コード", "012345", "IPアドレスとポート", "192.168.1.8:38123");
  check(jp!=null&&jp.code.equals("012345")&&jp.port==38123&&jp.host.equals("192.168.1.8"),"Japanese dialog and leading-zero code");
  PairingInfo en=parse("Wi-Fi pairing code", "１２３４５６", "[fe80::a%wlan0]:54321");
  check(en!=null&&en.code.equals("123456")&&en.port==54321,"English dialog, full-width digits and IPv6");
  check(PairingInfo.parse("com.example.fake",Arrays.asList("pairing code","123456","127.0.0.1:12345"))==null,"reject another app");
  check(parse("123456","127.0.0.1:12345")==null,"reject data without pairing label");
  check(parse("pairing code","123456","234567","127.0.0.1:12345")==null,"reject ambiguous codes");
  check(parse("pairing code","123456","127.0.0.1:12345","127.0.0.1:23456")==null,"reject ambiguous endpoints");
  check(parse("pairing code","123456")==null,"hidden endpoint requires manual entry");
  check(parse("pairing code","127.0.0.1:12345")==null,"hidden code requires manual entry");
  for(String port:new String[]{"0","65536","999999"})check(parse("pairing code","123456","127.0.0.1:"+port)==null,"invalid port "+port);
  check(parse("pairing code","12345","127.0.0.1:12345")==null,"short code rejected");
  check(parse("pairing code","123456","123456","127.0.0.1:12345")!=null,"duplicate accessibility text tolerated");
  String token="1234567890abcdef1234567890abcdef";
  check(LocalLaunch.command("/data/app/~~a_==/jp.tapmarker-b==/base.apk",token).contains("jp.tapmarker.VirtualStart"),"modern Android installation path accepted");
  for(String path:new String[]{"/data/app/x/base.apk';id;#","/data/app/x/$(id)/base.apk","/data/app/x/`id`/base.apk","/data/app/../x/base.apk","/sdcard/base.apk"}){
   boolean rejected=false;try{LocalLaunch.command(path,token);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"unsafe APK path rejected");
  }
  check(!LocalLaunch.validToken(token+";id"),"launch token cannot inject a command");
  System.out.println("PairingInfoTest: "+checks+" checks passed; Settings accessibility not tested on device");
 }
}
