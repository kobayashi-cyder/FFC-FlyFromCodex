import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

/** Inspect APK call sites, not merely the presence of unused helper classes. */
public final class ReturnProgressAudit {
 static final Map<String,ClassDef> classes=new HashMap<>();static int checks;
 static ClassDef type(String n){ClassDef c=classes.get("Ljp/tapmarker/"+n+";");if(c==null)throw new AssertionError("missing "+n);return c;}
 static Method method(String owner,String name){for(Method m:type(owner).getMethods())if(m.getName().equals(name))return m;throw new AssertionError(owner+"."+name);}
 static int calls(Method m,String owner,String name){int n=0;if(m.getImplementation()==null)return n;for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof MethodReference){MethodReference ref=(MethodReference)r;if(ref.getDefiningClass().equals("Ljp/tapmarker/"+owner+";")&&ref.getName().equals(name))n++;}}return n;}
 static boolean field(Method m,String owner,String name){for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof FieldReference){FieldReference ref=(FieldReference)r;if(ref.getDefiningClass().equals("Ljp/tapmarker/"+owner+";")&&ref.getName().equals(name))return true;}}return false;}
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  for(ClassDef c:DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(35)).getClasses())classes.put(c.getType(),c);
  Method entry=method("AutoPilot","verifyRegisteredReturn");
  check(calls(entry,"ReturnProgressBridge","intercept")==1,"shipping return preflight not redirected");
  check(calls(entry,"AutoPilot","verifyRegisteredReturnOriginal")==1,"original custom-automaton fallback missing");
  check(method("AutoPilot","verifyRegisteredReturnOriginal").getImplementation()!=null,"original method lost");
  check(calls(method("ExternalAdHandoffBridge","defer"),"ReturnProgressBridge","monitor")==1,"episode monitor not wired into actual phase cycle");
  int valid=0,gate=0,launch=0,diagnostic=0;
  for(Method m:type("ReturnProgressBridge").getMethods()){
   valid+=calls(m,"AutoPilot","validReturn");gate+=calls(m,"ReturnProgressGate","decide");
   launch+=calls(m,"AutoPilot","launchRegisteredReturn");diagnostic+=calls(m,"ProgressErrors","fatal");
  }
  check(valid>=2,"stale async callback fencing missing");check(gate==1,"bounded gate unused");
  check(launch>=2,"existing intent/restart budget bypassed");check(diagnostic==1,"terminal diagnostic missing");
  check(field(method("ProgressErrors","save"),"AutoPilot","token"),"physical save uses wrong owner run");
  check(calls(method("ProgressErrors","save"),"DiagnosticJournal","observed")>0,"physical observation not frozen");
  check(calls(method("ProgressErrors","save"),"PhysicalDiagnosticCapture","save")==1,"real-display screenshot path missing");
  int status=0;for(ClassDef c:classes.values())if(c.getType().startsWith("Ljp/tapmarker/PhysicalDiagnosticCapture"))for(Method m:c.getMethods())status+=calls(m,"DiagnosticJournal","captureStatus");
  check(status>=2,"screenshot success/failure status missing");
  System.out.println("ReturnProgressAudit: "+checks+" packaged-code checks passed");
 }
}
