import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.writer.pool.*;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;

/**
 * v1.158 candidate: replace ONLY AdV157DexBridge and its generated lambda
 * classes in the exact signed v1.157 DEX. All old classes are retained.
 *
 * This intentionally does not touch PanelModeController, PanelModePolicy,
 * TapService, AutoPilot, SplitPilot or any of their lambda classes.
 *
 * Structural verification does not certify Android runtime behavior.
 */
public final class PatchRewardV158 {
 private static final Opcodes API=Opcodes.forApi(24);
 private static final String ROOT="Ljp/tapmarker/";
 private static final String BRIDGE=ROOT+"AdV157DexBridge;";
 private static final String LAMBDA=ROOT+"AdV157DexBridge$$ExternalSyntheticLambda";
 private static final String[] EXTRA={
   ROOT+"RewardV158Observer",ROOT+"RewardEvaluationConnectome",
   ROOT+"RewardLanePlanner",ROOT+"KurashiruPointHud"
 };

 private static DexFile dex(String file)throws Exception{
   return DexFileFactory.loadDexFile(new File(file),API);
 }
 private static boolean bridge(String t){
   return BRIDGE.equals(t)||t.startsWith(LAMBDA);
 }
 private static boolean extra(String t){
   for(String prefix:EXTRA)if(t.startsWith(prefix))return true;
   return false;
 }
 private static Map<String,ClassDef> gather(DexFile file)throws Exception{
   Map<String,ClassDef> out=new TreeMap<>();
   for(ClassDef c:file.getClasses()){
     if(out.put(c.getType(),c)!=null)throw new IllegalStateException("Duplicate DEX class: "+c.getType());
   }
   return out;
 }
 private static Map<String,ClassDef> expected(String baseline,String overlay)throws Exception{
   Map<String,ClassDef> old=gather(dex(baseline)),add=gather(dex(overlay));
   if(!old.containsKey(BRIDGE)||!add.containsKey(BRIDGE))
     throw new IllegalStateException("Missing original or new AdV157DexBridge");
   if(!old.containsKey(ROOT+"PanelModeController;")||
      !old.containsKey(ROOT+"PanelModePolicy;"))
     throw new IllegalStateException("Signed v1.157 baseline lacks minimize panel");
   for(String name:add.keySet())if(!bridge(name)&&!extra(name))
     throw new SecurityException("Unexpected binary overlay class: "+name);
   for(String name:add.keySet())if(old.containsKey(name)&&!bridge(name))
     throw new SecurityException("Attempt to replace an unrelated shipped class: "+name);
   Map<String,ClassDef> result=new TreeMap<>(old);
   result.putAll(add);
   // Explicitly assure preservation of prior owner-controlled v1.157 hooks.
   for(String name:new String[]{ROOT+"AutoPilot;",ROOT+"SplitPilot;",
       ROOT+"VirtualShell;",ROOT+"PanelModeController;",ROOT+"PanelModePolicy;"})
     if(result.get(name)!=old.get(name))
       throw new SecurityException("Existing v1.157 class was replaced: "+name);
   return result;
 }
 private static byte[] canonical(ClassDef c)throws Exception{
   MemoryDataStore d=new MemoryDataStore();
   DexPool.writeTo(d,new ImmutableDexFile(API,Collections.singleton(c)));
   return d.getData();
 }
 private static void merge(String base,String overlay,String out)throws Exception{
   Map<String,ClassDef> classes=expected(base,overlay);
   DexFileFactory.writeDexFile(out,new ImmutableDexFile(API,classes.values()));
   System.out.println("v1.158 candidate DEX graft class count="+classes.size());
 }
 private static void audit(String base,String overlay,String merged)throws Exception{
   Map<String,ClassDef> expected=expected(base,overlay),actual=gather(dex(merged));
   if(!actual.keySet().equals(expected.keySet()))
     throw new SecurityException("Class set mismatch. Expected "+expected.size()
         +" got "+actual.size());
   for(String name:expected.keySet()){
     if(!Arrays.equals(canonical(expected.get(name)),canonical(actual.get(name))))
       throw new SecurityException("Canonical class mismatch: "+name);
   }
   Map<String,ClassDef> old=gather(dex(base));
   for(String name:old.keySet())if(!bridge(name) &&
       !Arrays.equals(canonical(old.get(name)),canonical(actual.get(name))))
     throw new SecurityException("Original class changed without permission: "+name);
   System.out.println("PASS: All "+old.size()+" shipped v1.157 classes retained; "
       +"all original non-bridge class bytecode semantics preserved; panel classes unchanged.");
 }
 public static void main(String[] args)throws Exception{
   if(args.length!=4)throw new IllegalArgumentException(
     "merge|audit signed-v157.apk new-classes.dex merged.dex");
   if("merge".equals(args[0]))merge(args[1],args[2],args[3]);
   else if("audit".equals(args[0]))audit(args[1],args[2],args[3]);
   else throw new IllegalArgumentException(args[0]);
 }
}
