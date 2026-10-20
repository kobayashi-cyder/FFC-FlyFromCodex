package jp.tapmarker;
import java.util.*;import java.util.regex.*;import java.text.Normalizer;
/** Parse only an explicitly opened Android Settings pairing-code dialog. Never log its contents. */
final class PairingInfo {
 final String host,code;final int port;
 PairingInfo(String host,int port,String code){this.host=host;this.port=port;this.code=code;}
 static final Pattern V4=Pattern.compile("(?<![0-9])((?:[0-9]{1,3}\\.){3}[0-9]{1,3}):([0-9]{1,5})(?![0-9])");
 static final Pattern V6=Pattern.compile("\\[([0-9a-fA-F:%._a-zA-Z0-9]+)\\]:([0-9]{1,5})(?![0-9])");
 static boolean validPort(int port){return port>0&&port<=65535;}
 static boolean validCode(String code){return code!=null&&code.matches("[0-9]{6}");}
 static PairingInfo parse(String pkg,List<String> texts){
  if(!"com.android.settings".equals(pkg))return null;
  Set<String> codes=new HashSet<>(),endpoints=new HashSet<>();boolean labelled=false;
  for(String raw:texts){String text=Normalizer.normalize(raw==null?"":raw,Normalizer.Form.NFKC).trim();String lower=text.toLowerCase(Locale.ROOT);
   if(lower.contains("pairing code")||text.contains("ペア設定コード")||text.contains("ペアリングコード"))labelled=true;
   if(validCode(text))codes.add(text);
   for(Pattern p:new Pattern[]{V4,V6}){Matcher m=p.matcher(text);while(m.find()){int port=Integer.parseInt(m.group(2));if(validPort(port))endpoints.add(m.group(1)+"|"+port);}}
  }
  if(!labelled||codes.size()!=1||endpoints.size()!=1)return null;
  String[] address=endpoints.iterator().next().split("\\|",2);
  return new PairingInfo(address[0],Integer.parseInt(address[1]),codes.iterator().next());
 }
}
