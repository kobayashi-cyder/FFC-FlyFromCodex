import java.io.File;import java.util.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.iface.*;import com.android.tools.smali.dexlib2.iface.instruction.*;import com.android.tools.smali.dexlib2.iface.reference.*;
public final class KaitakuAudit {
 static final Map<String,ClassDef> types=new HashMap<>();static int checks;
 static ClassDef type(String n){ClassDef c=types.get("Ljp/tapmarker/"+n+";");if(c==null)throw new AssertionError(n);return c;}
 static Method method(String owner,String n){for(Method m:type(owner).getMethods())if(m.getName().equals(n)&&!(owner.equals("AppPhases")&&n.equals("load")&&m.getParameterTypes().size()!=1))return m;throw new AssertionError(owner+"."+n);}
 static int calls(Method m,String owner,String name){int n=0;for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof MethodReference){MethodReference v=(MethodReference)r;if(v.getDefiningClass().equals("Ljp/tapmarker/"+owner+";")&&v.getName().equals(name))n++;}}return n;}
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  for(ClassDef c:DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(35)).getClasses())types.put(c.getType(),c);
  for(String[] h:new String[][]{{"AutoPilot","startSingle","KaitakuStartup","start"},{"AutoPilot","canTapUsagi","KaitakuStartup","canTap"},{"AutoPilot","canTapUsagi","AutoPilot","canTapUsagiKaitakuOriginal"},{"AutoPilot","startSingle","AutoPilot","startSingleKaitakuOriginal"},{"AppPhases","load","KaitakuStartup","loaded"},{"AppPhases","loadTarget","KaitakuStartup","loaded"},{"AppPhases","resetPreset","KaitakuStartup","reset"},{"AdaptiveLayout","install","KaitakuLayout","choose"},{"AdaptiveLayout","install","AdaptiveLayout","installKaitakuOriginal"}})check(calls(method(h[0],h[1]),h[2],h[3])==1,"packaged hook "+Arrays.toString(h));
  check(calls(method("KaitakuStartup","canTap"),"AutoPilot","snapshotWindowCurrent")==1,"tap checks current target window");
  check(calls(method("KaitakuLayout","collect"),"KaitakuEvidence","read")==1,"shipping sampler uses paired evidence");
  check(calls(method("KaitakuStartup","loaded"),"KaitakuPreset","install")==1,"package-scoped load installs independent graph");
  check(calls(method("AutoPilot","verifyRegisteredReturn"),"ReturnProgressBridge","intercept")==1,"bounded return gate retained");
  check(calls(method("AutoPilot$1","onSuccess"),"PhysicalFrameHealth","captured")==1,"actual screenshot clock retained");
  check(calls(method("SplitPilot","applySamples"),"KaitakuPreset","home")==1,"display-local home classification uses new package");
  System.out.println("KaitakuAudit: "+checks+" packaged-code hooks verified");
 }
}
