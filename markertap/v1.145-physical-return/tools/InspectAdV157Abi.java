import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
/** Read-only audit of the actual SHA-pinned, released v1.156 APK's DEX ABI. */
public final class InspectAdV157Abi {
 static String sig(Method m) {
  StringBuilder s=new StringBuilder("(");
  for(CharSequence p:m.getParameterTypes())s.append(p);
  return s.append(")").append(m.getReturnType()).toString();
 }
 public static void main(String[] args)throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("expected original signed APK path");
  DexFile file=DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(24));
  String root="Ljp/tapmarker/";
  Map<String,String[]> required=new LinkedHashMap<>();
  required.put(root+"AutoPilot;",new String[]{"phaseCycle(I)V","homeVisible()Z","schedulePhase(IJ)V"});
  required.put(root+"SplitPilot;",new String[]{"applySamples(Ljp/tapmarker/SplitPilot$Slot;Ljava/util/Map;Landroid/graphics/Rect;I)V","later(Ljp/tapmarker/SplitPilot$Slot;IJ)V"});
  required.put(root+"VirtualShell;",new String[]{"onTransact(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z"});
  required.put(root+"VirtualBridgeProvider;",new String[]{"connected()Landroid/os/IBinder;"});
  required.put(root+"AdHomeRecovery;",new String[]{"visible(Ljava/util/Map;II)Z"});
  required.put(root+"PlayableAdEvidence;",new String[]{"visible(Ljava/util/Map;II)Z"});
  required.put(root+"AdExitPolicy;",new String[]{"adState(Ljava/lang/String;)Z"});
  required.put(root+"RecoveryEvidence;",new String[]{"home(Ljp/tapmarker/PhaseEngine;Ljava/util/Map;)Z"});
  required.put(root+"VirtualWindows;",new String[]{"window(Ljava/lang/String;)Ljp/tapmarker/VirtualWindows$Window;"});
  required.put(root+"VirtualProtocol;",new String[]{"implementation()I"});
  Map<String,ClassDef> classes=new HashMap<>();
  for(ClassDef c:file.getClasses())classes.put(c.getType(),c);
  List<String> missing=new ArrayList<>();
  for(Map.Entry<String,String[]> pair:required.entrySet()) {
   ClassDef c=classes.get(pair.getKey());
   if(c==null){missing.add(pair.getKey()+" (class missing)");continue;}
   Set<String> signatures=new TreeSet<>();
   for(Method m:c.getMethods())signatures.add(m.getName()+sig(m));
   for(String signature:pair.getValue()) {
    boolean ok=signatures.contains(signature);
    System.out.println((ok?"PASS":"MISSING")+" "+c.getType()+"."+signature);
    if(!ok)missing.add(c.getType()+"."+signature);
   }
  }
  for(String cls:new String[]{"AutoPilot","SplitPilot","VirtualShell","VirtualWindows","VirtualProtocol"}) {
   ClassDef c=classes.get(root+cls+";");
   if(c==null)continue;
   System.out.println("FIELDS "+cls);
   for(Field f:c.getFields())System.out.println("  "+f.getName()+" : "+f.getType());
   System.out.println("METHODS "+cls);
   for(Method m:c.getMethods())if(m.getName().equals("launch")||m.getName().equals("stopMarkers")||m.getName().equals("pauseMain")||m.getName().equals("note"))System.out.println("  "+m.getName()+sig(m));
  }
  if(!missing.isEmpty())throw new IllegalStateException("STOP: v1.156 DEX differs from v1.157 assumptions: "+missing);
  System.out.println("PASS: Actual released v1.156 DEX supports checked hook points; no APK was built or signed.");
 }
}
