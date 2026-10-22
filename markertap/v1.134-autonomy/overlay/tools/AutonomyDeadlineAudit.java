import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
public final class AutonomyDeadlineAudit {
 static final Map<String,ClassDef> classes=new HashMap<>();static int checks;
 static ClassDef type(String n){ClassDef c=classes.get("Ljp/tapmarker/"+n+";");if(c==null)throw new AssertionError("missing "+n);return c;}
 static int calls(Method method,String owner,String name){int n=0;if(method.getImplementation()==null)return 0;for(Instruction i:method.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof MethodReference){MethodReference m=(MethodReference)r;if(m.getDefiningClass().equals(owner)&&m.getName().equals(name))n++;}}return n;}
 static Method method(String o,String n){for(Method m:type(o).getMethods())if(m.getName().equals(n))return m;throw new AssertionError(o+"."+n);}
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{for(ClassDef c:DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(35)).getClasses())classes.put(c.getType(),c);
  check(calls(method("FrameCircuit$Frame","checkLease"),"Ljp/tapmarker/AnalysisBudget;","fresh")>0,"pixel sampling has no freshness deadline");check(calls(method("FrameCircuit","run"),"Ljp/tapmarker/AnalysisBudget;","fresh")>=2,"post-computation deadline missing");int delivery=0;for(Method m:type("FrameCircuit").getMethods())if(!m.getName().equals("run"))delivery+=calls(m,"Ljp/tapmarker/AnalysisBudget;","fresh");check(delivery>0,"handler deadline missing");Method trim=method("VirtualWindows","trimFrameCircuit");check(calls(trim,"Ljp/tapmarker/FrameCircuit;","requestTrim")>0&&calls(trim,"Ljp/tapmarker/FrameCircuit;","trim")==0&&calls(trim,"Ljava/util/concurrent/ThreadPoolExecutor;","execute")==0,"blocking trim queued");check(calls(method("FrameCircuit","requestTrim"),"Ljava/util/concurrent/locks/ReentrantLock;","tryLock")>0,"trim can block");int releases=0;for(Method m:type("SplitPilot").getMethods())releases+=calls(m,"Ljp/tapmarker/CaptureCircuit;","releasing");check(releases>=2,"both result paths must release");check(calls(method("AutonomySupervisor","complete"),"Ljp/tapmarker/AnalysisBudget;","fresh")>0,"invalid success accepted");for(Instruction i:method("AutonomySupervisor","observeState").getImplementation().getInstructions())if(i.getOpcode().name().startsWith("IPUT")&&i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof FieldReference){String f=((FieldReference)r).getName();check(!f.equals("unhealthySince")&&!f.equals("failStreak")&&!f.equals("lastFailure"),"cached home clears failure");}}System.out.println("AutonomyDeadlineAudit: "+checks+" packaged-code checks passed");}
}
