import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

/** Audits the installed APK code, including version UI and service lifecycle. */
public final class ReleaseRuntimeAudit {
 public static void main(String[] args)throws Exception {
  DexFile dex=DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(35));
  Map<String,ClassDef> classes=new HashMap<>();for(ClassDef c:dex.getClasses())classes.put(c.getType(),c);
  ClassDef service=classes.get("Ljp/tapmarker/TapService;");
  boolean interrupt=false,destroy=false,manualStop=false;
  for(Method method:service.getMethods()){
   if(method.getName().equals("onDestroy")){
    for(Instruction instruction:method.getImplementation().getInstructions())if(instruction instanceof ReferenceInstruction){
     Reference reference=((ReferenceInstruction)instruction).getReference();
     if(reference instanceof MethodReference && ((MethodReference)reference).getName().equals("emergencyStop"))destroy=true;
    }
   }
   if(method.getName().equals("stopAll")&&method.getImplementation()!=null)manualStop=true;
   if(!method.getName().equals("onInterrupt"))continue;
   interrupt=true;int count=0;
   for(Instruction instruction:method.getImplementation().getInstructions()){
    count++;
    if(instruction.getOpcode()!=Opcode.RETURN_VOID)throw new AssertionError("fatal teardown in onInterrupt: "+instruction.getOpcode());
   }
   if(count!=1)throw new AssertionError("feedback interruption callback is not a bounded no-op");
  }
  if(!interrupt||!destroy||!manualStop)throw new AssertionError("manual stop or service destruction lifecycle missing");
  ClassDef activity=classes.get("Ljp/tapmarker/MainActivity;");int labels=0;
  for(Method method:activity.getMethods())if(method.getImplementation()!=null)for(Instruction instruction:method.getImplementation().getInstructions())if(instruction instanceof ReferenceInstruction){
   Reference reference=((ReferenceInstruction)instruction).getReference();
   if(reference instanceof StringReference){String value=((StringReference)reference).getString();
    if(value.matches("v[0-9]+\\.[0-9]+")){
     if(!value.equals("v"+args[1]))throw new AssertionError("display version differs from APK manifest: "+value);
     labels++;
    }
   }
  }
  if(labels!=1)throw new AssertionError("expected one activity version label, found "+labels);
  System.out.println("ReleaseRuntimeAudit: UI "+args[1]+" agrees with APK manifest; feedback interruption preserves session; manual stop and onDestroy shutdown retained");
 }
}
