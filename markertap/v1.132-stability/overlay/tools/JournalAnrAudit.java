import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

/** Rejects the actual v1.131 journal-lock path and per-tile native rendering. */
public final class JournalAnrAudit {
 static Map<String,ClassDef> classes=new HashMap<>();
 static ClassDef type(String name){ClassDef c=classes.get("Ljp/tapmarker/"+name+";");if(c==null)throw new AssertionError("missing "+name);return c;}
 static boolean calls(Method method,String owner,String name){
  if(method.getImplementation()==null)return false;
  for(Instruction i:method.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){
   Reference r=((ReferenceInstruction)i).getReference();if(r instanceof MethodReference){MethodReference m=(MethodReference)r;if(m.getDefiningClass().equals(owner)&&m.getName().equals(name))return true;}
  }return false;
 }
 public static void main(String[] args)throws Exception {
  for(ClassDef c:DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(35)).getClasses())classes.put(c.getType(),c);
  boolean frame=false;
  for(Method m:type("DiagnosticJournal").getMethods())if(m.getName().equals("frame")&&m.getParameterTypes().contains("Ljp/tapmarker/RegionSampler$Pixels;")){
   if((m.getAccessFlags()&(0x20|0x20000))!=0)throw new AssertionError("journal capture entrypoint synchronized");
   if(!calls(m,"Ljp/tapmarker/DiagnosticFrameCapture;","frame"))throw new AssertionError("old journal capture still holds its monitor during thumbnail");
   for(Instruction i:m.getImplementation().getInstructions())if(i.getOpcode()==Opcode.MONITOR_ENTER)throw new AssertionError("pixel capture wrapper acquires journal monitor");
   frame=true;
  }
  if(!frame)throw new AssertionError("pixel capture entrypoint absent");
  boolean readback=false,source=false,encoder=false;
  for(Method m:type("HardwarePixelSource").getMethods())if(m.getName().equals("readable")&&calls(m,"Landroid/graphics/Bitmap;","copy"))readback=true;
  for(Method m:type("RegionImage").getMethods()){
   source|=m.getName().equals("fromHardware")&&calls(m,"Ljp/tapmarker/HardwarePixelSource;","<init>");
   if(calls(m,"Landroid/graphics/Picture;","<init>"))throw new AssertionError("per-tile hardware rendering retained");
  }
  int fields=0;for(Field f:type("DiagnosticJournal$$ExternalSyntheticLambda0").getFields())fields++;
  for(Method m:type("DiagnosticJournal$$ExternalSyntheticLambda0").getMethods())if(m.getName().equals("run")&&calls(m,"Ljp/tapmarker/DiagnosticJournal;","lambda$1$jp-tapmarker-DiagnosticJournal"))encoder=true;
  if(!readback||!source||!encoder||fields<12)throw new AssertionError("readback binding or preserved real diagnostic encoder missing");
  System.out.println("JournalAnrAudit: lock-free capture wrapper, exact shared readback, real diagnostic encoder verified");
 }
}
