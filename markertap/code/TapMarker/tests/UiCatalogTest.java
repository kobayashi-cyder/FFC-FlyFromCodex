package jp.tapmarker;

import java.io.*;
import java.util.*;
import java.util.function.IntFunction;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Resource contracts plus the actual display presenter; no Android UI emulation. */
public final class UiCatalogTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Map<String,String> strings(String path)throws Exception{
  DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  Document doc=f.newDocumentBuilder().parse(new File(path));Map<String,String> result=new LinkedHashMap<>();NodeList nodes=doc.getElementsByTagName("string");
  for(int i=0;i<nodes.getLength();i++){Element e=(Element)nodes.item(i);String value=e.getTextContent().replace("\\n","\n").replace("\\'","'").replace("\\\"","\"").replace("\\\\","\\");check(!result.containsKey(e.getAttribute("name")),"no duplicated resource key");result.put(e.getAttribute("name"),value);}
  return result;
 }
 static IntFunction<String> resolve(Map<String,String> values)throws Exception{
  Map<Integer,String> ids=new HashMap<>();for(Map.Entry<String,String> value:values.entrySet())ids.put(R.string.class.getField(value.getKey()).getInt(null),value.getValue());
  return id->{String result=ids.get(id);if(result==null)throw new AssertionError("missing native resource "+id);return result;};
 }
 public static void main(String[] args)throws Exception{
  Map<String,String> defaults=strings("res/values/strings.xml"),english=strings("res/values-en/strings.xml"),japanese=strings("res/values-ja/strings.xml");
  check(defaults.equals(english),"unsupported locales have a complete English default; explicit en also supports preferred-language lists");
  check(defaults.keySet().equals(japanese.keySet()),"Japanese and fallback resource sets are complete");
  IntFunction<String> en=resolve(defaults),ja=resolve(japanese);
  check(UiCatalog.render("ホーム",en).equals("Home")&&UiCatalog.render("ホーム",ja).equals("ホーム"),"home uses the selected native resource");
  check(UiCatalog.render("全停止",en).equals("Stop all"),"essential stop control is available in English");
  check(UiCatalog.render("録画ファイルを選択",en).equals("Choose recording file"),"recording controls are translated");
  check(UiCatalog.render("同じ状態に留まる",en).equals("Stay in the same state"),"editor dropdowns use display translations");
  check(UiCatalog.render("仮想2画面 / 自律ON",en).equals("2 virtual displays / Automation on"),"dynamic two-display summary preserves the count");
  check(UiCatalog.render("状態：停止中",en).equals("State: Stopped"),"structured state label and value are both translated");
  check(UiCatalog.render("\n状態：停止中\n\n",en).equals("\nState: Stopped\n\n"),"real newlines and blank lines are preserved");
  String name="私の独自状態 α%1$s / https://example.invalid/path";
  check(UiCatalog.render(name,en).equals(name),"unknown user names, placeholders, and URLs are not rewritten");
  check(UiCatalog.render("farm-default-main",en).equals("farm-default-main"),"automaton identifiers remain untouched");
  check(UiCatalog.render("\"閉じる\"",en).equals("\"閉じる\""),"captured text in quotes is not translated by substring replacement");
  for(Map.Entry<String,Integer> entry:UiCatalog.IDS.entrySet()){
   check(UiCatalog.exact(entry.getKey(),ja).equals(entry.getKey()),"Japanese resource round trip: "+entry.getKey());
   check(!UiCatalog.exact(entry.getKey(),en).equals(entry.getKey()),"English translation exists: "+entry.getKey());
  }
  Document localeDoc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File("res/xml/locales_config.xml"));NodeList locales=localeDoc.getElementsByTagName("locale");Set<String> declared=new HashSet<>();for(int i=0;i<locales.getLength();i++)declared.add(((Element)locales.item(i)).getAttribute("android:name"));
  check(declared.equals(new HashSet<>(Arrays.asList("en","ja"))),"system app-language settings expose only the two shipped languages");
  System.out.println("UiCatalogTest: "+checks+" checks passed; "+UiCatalog.IDS.size()+" native resource entries, English fallback, Japanese round trip, multiline presentation and unchanged identifiers; Android UI selection untested");
 }
}
