import java.io.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

/** Checks the final APK's integrations, including unchanged latest-version features. */
public final class IntegrationAudit {
 static Map<String,ClassDef> classes=new HashMap<>();static int checks;
 static void hasClass(String name){checks++;if(!classes.containsKey("Ljp/tapmarker/"+name+";"))throw new AssertionError("missing class "+name);}
 static void field(String name,String field,String type){hasClass(name);for(Field f:classes.get("Ljp/tapmarker/"+name+";").getFields())if(f.getName().equals(field)&&f.getType().equals(type))return;throw new AssertionError("missing field "+name+"."+field);}
 static void binding(String root,String owner,String method){
  checks++;String prefix="Ljp/tapmarker/"+root,target="Ljp/tapmarker/"+owner+";";
  for(ClassDef c:classes.values())if(c.getType().equals(prefix+";")||c.getType().startsWith(prefix+"$"))for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction instruction:m.getImplementation().getInstructions())if(instruction instanceof ReferenceInstruction){
   Reference r=((ReferenceInstruction)instruction).getReference();if(r instanceof MethodReference){MethodReference ref=(MethodReference)r;if(ref.getDefiningClass().equals(target)&&ref.getName().equals(method))return;}
  }
  throw new AssertionError("missing runtime binding "+root+" -> "+owner+"."+method);
 }
 static void loadsOwnAsset(){checks++;boolean found=false;for(Method method:classes.get("Ljp/tapmarker/AutoPilot;").getMethods())if(method.getName().equals("ensureCircuit"))for(Instruction instruction:method.getImplementation().getInstructions())if(instruction instanceof ReferenceInstruction){Reference reference=((ReferenceInstruction)instruction).getReference();if(reference instanceof StringReference){String text=((StringReference)reference).getString();if(text.equals("circuit.bin"))throw new AssertionError("AutoPilot opens removed legacy asset");found|=text.equals("custom-connectome.bin");}}if(!found)throw new AssertionError("custom asset load not bound in final APK");}
 public static void main(String[] args)throws Exception{
  DexFile file=DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(35));for(ClassDef c:file.getClasses())classes.put(c.getType(),c);
  for(String name:new String[]{"ConnectomeRuntime","ConnectomeAutonomy","PreviewConnectome","RegionPreviewView","RegionPlan","VirtualWindows","VirtualWindowPanel","BackendRecoveryBudget","WirelessAdb","CrashGuardApplication","ExitDiagnostics","CustomConnectome","ActionConnectome","CustomMemory"})hasClass(name);
  binding("AutoPilot","CustomMemory","load");
  loadsOwnAsset();binding("AutoPilot","CustomConnectome","<init>");binding("AutoPilot","CustomConnectome","encode");binding("ConnectomeAutonomy","ActionConnectome","select");
  field("SplitPilot$Slot","supervisor","Ljp/tapmarker/AutonomySupervisor;");field("VirtualWindows$Window","latest","Ljp/tapmarker/RegionImage;");
  for(String method:new String[]{"tick","begin","current","complete","observeState","retryDelay","recoveryDue"})binding("SplitPilot","AutonomySupervisor",method);
  binding("SplitPilot","RegionPlan","regions");binding("SplitPilot","VirtualWindows","plan");binding("SplitPilot","VirtualWindows","manualPreview");binding("SplitPilot","ConnectomeRuntime","sample");
  binding("VirtualWindows","FrameLockBridge","preview");
  binding("VirtualWindows","FrameLockBridge","copy");
  binding("VirtualWindows","FrameLockBridge","image");
  binding("VirtualShell","ShellOwnerDeath","close");
  binding("ShellOwnerDeath","OwnerDeathCleanup","run");
  binding("FrameLockBridge","RegionPreviewData","capture");
  binding("DiagnosticJournal","DiagnosticFrameCapture","frame");
  binding("DiagnosticFrameCapture","DiagnosticJournal$$ExternalSyntheticLambda0","<init>");
  binding("RegionImage","HardwarePixelSource","<init>");
  binding("HardwarePixelSource","HardwarePixelSource","readable");
  binding("FrameCircuit","FrameCircuit$Input","close");binding("CrashGuardApplication","ExitDiagnostics","start");binding("CrashGuardApplication","ExitDiagnostics","fatal");binding("ProgressErrors","ExitDiagnostics","report");binding("RegionImage","PointPixels","<init>");
  ClassDef tap=classes.get("Ljp/tapmarker/TapService;");boolean lock=false;for(Method method:tap.getMethods())if(method.getName().equals("systemScreenLocked"))lock=true;if(!lock)throw new AssertionError("latest lock-screen guard missing");checks++;
  if(classes.containsKey("Ljp/tapmarker/FlyCircuit;")||classes.containsKey("Ljp/tapmarker/BancSelector;"))throw new AssertionError("legacy biological circuit class retained");
  System.out.println("IntegrationAudit: "+checks+" class, field and APK binding checks passed; supervision, temporal memory, retry adaptation, custom-graph sampling, ROI preview, backend recovery, lock guard and exit recording present");
 }
}
