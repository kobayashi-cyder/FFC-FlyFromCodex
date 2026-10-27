import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

/** Assert the new code is called by the shipping callback and classifier. */
public final class RewardWindowAudit {
 static final Map<String,ClassDef> types=new HashMap<>();static int checks;
 static ClassDef type(String name){ClassDef c=types.get("Ljp/tapmarker/"+name+";");if(c==null)throw new AssertionError("missing "+name);return c;}
 static Method method(String owner,String name){for(Method m:type(owner).getMethods())if(m.getName().equals(name))return m;throw new AssertionError(owner+"."+name);}
 static int calls(Method m,String owner,String name){int n=0;for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof MethodReference){MethodReference ref=(MethodReference)r;if(ref.getDefiningClass().equals("Ljp/tapmarker/"+owner+";")&&ref.getName().equals(name))n++;}}return n;}
 static boolean field(Method m,String owner,String name){for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof FieldReference){FieldReference ref=(FieldReference)r;if(ref.getDefiningClass().equals("Ljp/tapmarker/"+owner+";")&&ref.getName().equals(name))return true;}}return false;}
 static boolean text(Method m,String value){for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof StringReference&&((StringReference)r).getString().equals(value))return true;}return false;}
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  for(ClassDef c:DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(35)).getClasses())types.put(c.getType(),c);
  Method callback=method("AutoPilot$1","onSuccess");
  check(calls(callback,"PhysicalFrameHealth","captured")==1,"real screenshot success must record capture receipt");
  check(calls(callback,"AutoPilot$1","onSuccessOriginal")==1,"original bitmap conversion and cleanup retained");
  check(field(callback,"AutoPilot$1","val$expected"),"frozen run token, not current live token, is passed");
  check(field(callback,"AutoPilot$1","val$windowBounds"),"captured window dimensions retained in diagnostics");
  check(field(method("PhysicalFrameHealth","captured"),"AutoPilot","token"),"stale callback fenced by run token");
  check(field(method("PhysicalFrameHealth","captured"),"AutoPilot","active"),"stopped callback cannot create live capture evidence");
  check(calls(method("ProgressErrors","observe"),"PhysicalFrameHealth","capturedAt")==1,"watchdog consumes actual capture clock");
  check(calls(method("ProgressErrors","details"),"PhysicalFrameHealth","report")==1,"actual raw geometry appears in report");
  check(field(method("ProgressWatchdog","observe"),"ProgressWatchdog$Sample","separateAnalysis"),"physical analysis separated from capture");
  check(text(method("ProgressWatchdog","observe"),"ANALYSIS_UNAVAILABLE"),"analysis failure has distinct diagnostic code");
  check(calls(method("FarmDialogEvidence","<init>"),"FarmRewardReference","addTo")==1,"paired reward reference is used by live classifier");
  check(calls(method("FarmDialogEvidence","score"),"PhaseEngine","blockDistance")>0,"independent heading/label scoring retained");
  check(calls(method("GamePopupEvidence","quick"),"FarmDialogEvidence","locate")==1,"reward masks run before expensive home/layout classification");
  check(calls(method("GamePopupEvidence","quick"),"GamePopupEvidence","insideWindow")==2,"both anchors must belong to the target window");
  check(method("ContentViewport","invalid").getImplementation()!=null,"out-of-frame error diagnostics retained");
  check(calls(method("AutoPilot","verifyRegisteredReturn"),"ReturnProgressBridge","intercept")==1,"previous return safety wrapper retained");
  System.out.println("RewardWindowAudit: "+checks+" packaged-code checks passed");
 }
}
