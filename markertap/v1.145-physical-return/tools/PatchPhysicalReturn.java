import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.instruction.formats.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.rewriter.*;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

/** Pinned 1.144 integrated APK overlay. Unrelated classes and resources are preserved. */
public final class PatchPhysicalReturn {
 static final String ROOT="Ljp/tapmarker/",BRIDGE=ROOT+"PhysicalRecoveryBridge;";
 static final com.android.tools.smali.dexlib2.Opcodes API=com.android.tools.smali.dexlib2.Opcodes.forApi(24);
 static DexFile load(String p)throws Exception{return DexFileFactory.loadDexFile(new File(p),API);}
 static String desc(MethodReference m){StringBuilder b=new StringBuilder("(");for(CharSequence p:m.getParameterTypes())b.append(p);return b.append(')').append(m.getReturnType()).toString();}
 static ImmutableMethod method(Method m,String name,MethodImplementation body){return new ImmutableMethod(m.getDefiningClass(),name,m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),body);}
 static ClassDef withMethods(ClassDef c,List<Method> ms){return new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),ms);}
 static ClassDef getters(ClassDef c){
  List<Method> ms=new ArrayList<>();for(Method m:c.getMethods()){if(m.getName().startsWith("physical"))throw new IllegalStateException("already patched");ms.add(m);}
  for(String[] pair:new String[][]{{"physicalReturns","returns","I"},{"physicalRestarts","restarts","I"},{"physicalNextAt","nextAt","J"}}){
   String name=pair[0],field=pair[1],type=pair[2];boolean wide=type.equals("J");boolean found=false;
   for(Field f:c.getFields())if(f.getName().equals(field)&&f.getType().equals(type))found=true;
   if(!found)throw new IllegalStateException("budget field changed: "+field);
   List<Instruction> ins=Arrays.asList(new ImmutableInstruction22c(wide?Opcode.IGET_WIDE:Opcode.IGET,0,wide?2:1,new ImmutableFieldReference(c.getType(),field,type)),new ImmutableInstruction11x(wide?Opcode.RETURN_WIDE:Opcode.RETURN,0));
   ms.add(new ImmutableMethod(c.getType(),name,Collections.emptyList(),type,0,Collections.emptySet(),Collections.emptySet(),new ImmutableMethodImplementation(wide?3:2,ins,Collections.emptyList(),Collections.emptyList())));
  }
  return withMethods(c,ms);
 }
 static Method redirect(Method m,String owner,String oldName,String target,String newName,List<String> params,String ret,int[] count){
  if(m.getImplementation()==null)return m;
  MethodImplementation original=m.getImplementation();List<Instruction> ins=new ArrayList<>();
  for(Instruction i:original.getInstructions()){
   if(i instanceof ReferenceInstruction&&((ReferenceInstruction)i).getReference() instanceof MethodReference){
    MethodReference ref=(MethodReference)((ReferenceInstruction)i).getReference();
    if(ref.getDefiningClass().equals(owner)&&ref.getName().equals(oldName)){
     MethodReference to=new ImmutableMethodReference(target,newName,params,ret);
     if(i instanceof Instruction35c){Instruction35c x=(Instruction35c)i;i=new ImmutableInstruction35c(Opcode.INVOKE_STATIC,x.getRegisterCount(),x.getRegisterC(),x.getRegisterD(),x.getRegisterE(),x.getRegisterF(),x.getRegisterG(),to);}
     else if(i instanceof Instruction3rc){Instruction3rc x=(Instruction3rc)i;i=new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE,x.getStartRegister(),x.getRegisterCount(),to);}
     else throw new IllegalStateException("unsupported invoke format");
     count[0]++;
    }
   }
   ins.add(i);
  }
  return method(m,m.getName(),new ImmutableMethodImplementation(original.getRegisterCount(),ins,original.getTryBlocks(),original.getDebugItems()));
 }
 static ClassDef patch(ClassDef c){
  if(c.getType().equals(ROOT+"RecoveryEscalation;"))return getters(c);
  if(!c.getType().equals(ROOT+"AutoPilot;")&&!c.getType().equals(ROOT+"ProgressErrors;"))return c;
  List<Method> ms=new ArrayList<>();int[] count={0};int details=0;
  for(Method m:c.getMethods()){
   if(c.getType().equals(ROOT+"AutoPilot;")&&m.getName().equals("phaseCycle")&&desc(m).equals("(I)V")){
    ms.add(redirect(m,ROOT+"ExternalAdHandoffBridge;","defer",BRIDGE,"defer",Arrays.asList(ROOT+"AutoPilot;","I"),"Z",count));
   }else if(c.getType().equals(ROOT+"ProgressErrors;")&&m.getName().equals("observe")){
    ms.add(redirect(m,ROOT+"ProgressWatchdog;","observe",BRIDGE,"observe",Arrays.asList(ROOT+"ProgressWatchdog;",ROOT+"ProgressWatchdog$Sample;","J"),ROOT+"ProgressWatchdog$Event;",count));
   }else if(c.getType().equals(ROOT+"ProgressErrors;")&&m.getName().equals("details")&&desc(m).equals("(Ljava/lang/String;Ljp/tapmarker/AppPhases;Ljp/tapmarker/VirtualWindows$Window;ZJJJ)Ljava/lang/String;")){
    details++;ms.add(method(m,"detailsBeforePhysicalReturn",m.getImplementation()));
    List<String> params=new ArrayList<>();for(CharSequence p:m.getParameterTypes())params.add(p.toString());
    MethodReference old=new ImmutableMethodReference(c.getType(),"detailsBeforePhysicalReturn",params,m.getReturnType());
    MethodReference append=new ImmutableMethodReference(BRIDGE,"append",Arrays.asList("Ljava/lang/String;","Ljava/lang/String;"),"Ljava/lang/String;");
    ms.add(method(m,m.getName(),new ImmutableMethodImplementation(12,Arrays.asList(
     new ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE,1,11,old),new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT,0),
     new ImmutableInstruction35c(Opcode.INVOKE_STATIC,2,0,2,0,0,0,append),new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT,0),new ImmutableInstruction11x(Opcode.RETURN_OBJECT,0)
    ),Collections.emptyList(),Collections.emptyList())));
   }else ms.add(m);
  }
  if(count[0]!=1||c.getType().endsWith("ProgressErrors;")&&details!=1)throw new IllegalStateException("shipping call-site changed: "+c.getType()+" / "+count[0]+" / "+details);
  return withMethods(c,ms);
 }
 static DexFile version(DexFile d){return new DexRewriter(new RewriterModule(){
  public Rewriter<Instruction> getInstructionRewriter(Rewriters r){
   final Rewriter<Instruction> parent=super.getInstructionRewriter(r);
   return i->{Instruction x=parent.rewrite(i);if(x instanceof ReferenceInstruction){Reference ref=((ReferenceInstruction)x).getReference();if(ref instanceof StringReference&&((StringReference)ref).getString().equals("v1.144")){
    StringReference s=new ImmutableStringReference("v1.145");if(x instanceof Instruction21c)return new ImmutableInstruction21c(x.getOpcode(),((Instruction21c)x).getRegisterA(),s);if(x instanceof Instruction31c)return new ImmutableInstruction31c(x.getOpcode(),((Instruction31c)x).getRegisterA(),s);
   }}return x;};
  }
 }).getDexFileRewriter().rewrite(d);}
 static void api(String base,String output)throws Exception{
  DexFile dex=load(base);List<ClassDef> classes=new ArrayList<>();for(ClassDef c:dex.getClasses())if(c.getType().startsWith(ROOT))classes.add(c.getType().endsWith("/RecoveryEscalation;")?getters(c):c);
  try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(Paths.get(output)))){
   for(ClassDef c:classes){
    String n=c.getType().substring(1,c.getType().length()-1);ClassWriter w=new ClassWriter(0);
    int access=c.getAccessFlags()&0xffff;w.visit(org.objectweb.asm.Opcodes.V1_8,access,n,null,c.getSuperclass()==null?"java/lang/Object":c.getSuperclass().substring(1,c.getSuperclass().length()-1),c.getInterfaces().stream().map(x->x.substring(1,x.length()-1)).toArray(String[]::new));
    for(ClassDef child:classes){String cn=child.getType().substring(1,child.getType().length()-1);int at=cn.lastIndexOf('$');if(at>0&&(cn.equals(n)||cn.substring(0,at).equals(n)))w.visitInnerClass(cn,cn.substring(0,at),cn.substring(at+1),org.objectweb.asm.Opcodes.ACC_STATIC|(child.getAccessFlags()&7));}
    for(Field f:c.getFields())w.visitField(f.getAccessFlags()&0xffff,f.getName(),f.getType(),null,null).visitEnd();
    for(Method m:c.getMethods()){
     int flags=m.getAccessFlags()&0xffff;MethodVisitor v=w.visitMethod(flags,m.getName(),desc(m),null,null);
     if((flags&(org.objectweb.asm.Opcodes.ACC_ABSTRACT|org.objectweb.asm.Opcodes.ACC_NATIVE))==0){v.visitCode();String ret=m.getReturnType();
      if(ret.equals("V"))v.visitInsn(org.objectweb.asm.Opcodes.RETURN);
      else if(ret.equals("J")){v.visitInsn(org.objectweb.asm.Opcodes.LCONST_0);v.visitInsn(org.objectweb.asm.Opcodes.LRETURN);}
      else if(ret.equals("D")){v.visitInsn(org.objectweb.asm.Opcodes.DCONST_0);v.visitInsn(org.objectweb.asm.Opcodes.DRETURN);}
      else if(ret.equals("F")){v.visitInsn(org.objectweb.asm.Opcodes.FCONST_0);v.visitInsn(org.objectweb.asm.Opcodes.FRETURN);}
      else if(ret.startsWith("L")||ret.startsWith("[")){v.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);v.visitInsn(org.objectweb.asm.Opcodes.ARETURN);}
      else{v.visitInsn(org.objectweb.asm.Opcodes.ICONST_0);v.visitInsn(org.objectweb.asm.Opcodes.IRETURN);}
      int locals=(flags&org.objectweb.asm.Opcodes.ACC_STATIC)==0?1:0;for(CharSequence p:m.getParameterTypes())locals+=p.toString().equals("J")||p.toString().equals("D")?2:1;
      v.visitMaxs(2,locals);
     }v.visitEnd();
    }
    w.visitEnd();out.putNextEntry(new JarEntry(n+".class"));out.write(w.toByteArray());out.closeEntry();
   }
  }
  System.out.println("Compile-only API signatures from pinned APK: "+classes.size()+" classes (never packaged)");
 }
 static byte[] canonical(ClassDef c)throws Exception{MemoryDataStore store=new MemoryDataStore();DexPool.writeTo(store,new ImmutableDexFile(API,Arrays.asList(c)));return store.getData();}
 static void merge(String base,String patch,String output)throws Exception{
  List<ClassDef> classes=new ArrayList<>();Set<String> names=new HashSet<>();
  for(ClassDef c:load(base).getClasses()){classes.add(patch(c));names.add(c.getType());}
  for(ClassDef c:load(patch).getClasses()){if(!c.getType().startsWith(ROOT+"PhysicalRecoveryBridge")&&!c.getType().startsWith(ROOT+"PhysicalReturnEpisode"))throw new IllegalStateException("unexpected overlay class: "+c.getType());if(!names.add(c.getType()))throw new IllegalStateException("duplicate class: "+c.getType());classes.add(c);}
  DexFileFactory.writeDexFile(output,version(new ImmutableDexFile(API,classes)));System.out.println("Merged "+classes.size()+" classes");
 }
 static void audit(String base,String result)throws Exception{
  Map<String,ClassDef> after=new TreeMap<>();for(ClassDef c:load(result).getClasses())after.put(c.getType(),c);
  Set<String> changed=new TreeSet<>();int preserved=0;
  for(ClassDef c:load(base).getClasses()){
   ClassDef actual=after.remove(c.getType());if(actual==null)throw new IllegalStateException("missing original class: "+c.getType());
   ClassDef expected=version(new ImmutableDexFile(API,Arrays.asList(patch(c)))).getClasses().iterator().next();
   if(!Arrays.equals(canonical(expected),canonical(actual)))throw new IllegalStateException("unexpected modification: "+c.getType());
   if(Arrays.equals(canonical(c),canonical(actual)))preserved++;else changed.add(c.getType());
  }
  if(!changed.containsAll(Arrays.asList(ROOT+"AutoPilot;",ROOT+"ProgressErrors;",ROOT+"RecoveryEscalation;")))throw new IllegalStateException("hooks not integrated");
  for(String n:after.keySet())if(!n.startsWith(ROOT+"PhysicalRecoveryBridge")&&!n.startsWith(ROOT+"PhysicalReturnEpisode"))throw new IllegalStateException("unexpected added type "+n);
  if(after.size()<3)throw new IllegalStateException("new runtime absent");
  System.out.println("Original classes byte-for-byte equivalent after canonical DEX serialization: "+preserved+"; reviewed changes: "+changed+"; added: "+after.keySet());
 }
 public static void main(String[] a)throws Exception{
  if(a[0].equals("api"))api(a[1],a[2]);else if(a[0].equals("merge"))merge(a[1],a[2],a[3]);else if(a[0].equals("audit"))audit(a[1],a[2]);else throw new IllegalArgumentException("api|merge|audit");
 }
}
