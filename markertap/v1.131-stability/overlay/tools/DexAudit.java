import java.io.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
public final class DexAudit {
 static Map<String,ClassDef> classes=new HashMap<>();
 static int invokes;
 static void invokeArguments(Method context,Instruction instruction,MethodReference target,Set<String> errors){
  String opcode=instruction.getOpcode().name();
  if(!opcode.startsWith("INVOKE_")||opcode.startsWith("INVOKE_POLYMORPHIC"))return;
  if(!(instruction instanceof VariableRegisterInstruction))return;
  int required=opcode.startsWith("INVOKE_STATIC")?0:1;
  for(CharSequence parameter:target.getParameterTypes())required+=parameter.toString().equals("J")||parameter.toString().equals("D")?2:1;
  int given=((VariableRegisterInstruction)instruction).getRegisterCount();
  String where=context.getDefiningClass()+"->"+context.getName()+" -> "+target;
  if(given!=required)errors.add("invoke register count "+given+" != "+required+" at "+where);
  int available=context.getImplementation().getRegisterCount();
  if(instruction instanceof RegisterRangeInstruction){
   int start=((RegisterRangeInstruction)instruction).getStartRegister();
   if(start+given>available)errors.add("invoke register range exceeds method registers at "+where);
  }else if(instruction instanceof FiveRegisterInstruction){
   FiveRegisterInstruction call=(FiveRegisterInstruction)instruction;
   int[] registers={call.getRegisterC(),call.getRegisterD(),call.getRegisterE(),call.getRegisterF(),call.getRegisterG()};
   for(int i=0;i<given;i++)if(registers[i]>=available)errors.add("invoke register exceeds method registers at "+where);
  }
  invokes++;
 }
 static String key(MethodReference m){return m.getName()+m.getParameterTypes()+m.getReturnType();}
 static boolean method(String owner,MethodReference ref,Set<String> seen){
  if(owner==null||!seen.add(owner))return false;ClassDef c=classes.get(owner);if(c==null)return owner.equals("Ljava/lang/Object;")?Arrays.asList("toString","getClass","hashCode","equals","clone","finalize","wait","notify","notifyAll").contains(ref.getName()):!owner.startsWith("Ljp/tapmarker/");
  for(Method m:c.getMethods())if(key(m).equals(key(ref)))return true;
  for(String parent:c.getInterfaces())if(method(parent,ref,seen))return true;
  return !ref.getName().equals("<init>")&&method(c.getSuperclass(),ref,seen);
 }
 static boolean field(String owner,FieldReference ref,Set<String> seen){
  if(owner==null||!seen.add(owner))return false;ClassDef c=classes.get(owner);if(c==null)return !owner.startsWith("Ljp/tapmarker/");
  for(Field f:c.getFields())if(f.getName().equals(ref.getName())&&f.getType().equals(ref.getType()))return true;
  return field(c.getSuperclass(),ref,seen);
 }
 public static void main(String[] args)throws Exception{
  DexFile dex=DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(35));for(ClassDef c:dex.getClasses())classes.put(c.getType(),c);
  Set<String> errors=new TreeSet<>();int references=0;
  for(ClassDef c:dex.getClasses())for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){
   Reference ref=((ReferenceInstruction)ins).getReference();
   if(ref instanceof MethodReference){MethodReference r=(MethodReference)ref;invokeArguments(m,ins,r,errors);if(r.getDefiningClass().startsWith("Ljp/tapmarker/")){references++;if(!method(r.getDefiningClass(),r,new HashSet<>()))errors.add("missing method "+r+" from "+c.getType()+"->"+m.getName());}}
   if(ref instanceof FieldReference){FieldReference r=(FieldReference)ref;if(r.getDefiningClass().startsWith("Ljp/tapmarker/")){references++;if(!field(r.getDefiningClass(),r,new HashSet<>()))errors.add("missing field "+r+" from "+c.getType()+"->"+m.getName());}}
  }
  for(String e:errors)System.out.println(e);if(!errors.isEmpty())throw new IllegalStateException(errors.size()+" broken references");
  System.out.println("DexAudit: "+classes.size()+" classes; "+references+" app method/field references and "+invokes+" invoke register layouts checked");
 }
}
