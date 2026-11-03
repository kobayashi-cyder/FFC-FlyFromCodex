import java.io.*;
import java.util.*;
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

/**
 * STRICT incremental DEX patch on the public, SHA-pinned signed v1.150 release.
 * Only the VirtualWindows constructor and preview method may be rewritten;
 * the two VirtualFrameQueue types may be replaced with ABI-equivalent classes.
 * Other original methods (except 1.150->1.151 version strings) are unchanged.
 * No signature is forged or retained: APK packaging/signing is external.
 */
public final class PatchVirtualLanesV151 {
 static final String R="Ljp/tapmarker/";
 static final com.android.tools.smali.dexlib2.Opcodes API=com.android.tools.smali.dexlib2.Opcodes.forApi(24);
 static DexFile load(String path)throws Exception{return DexFileFactory.loadDexFile(new File(path),API);}
 static String desc(MethodReference m){StringBuilder b=new StringBuilder("(");for(CharSequence p:m.getParameterTypes())b.append(p);return b.append(')').append(m.getReturnType()).toString();}
 static ImmutableMethod method(Method m,MethodImplementation body){
  return new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),
   m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),body);
 }
 static ClassDef withMethods(ClassDef c,List<Method> methods){
  return new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),
   c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
 }
 static boolean queue(String name){return name.equals(R+"VirtualFrameQueue;")||name.equals(R+"VirtualFrameQueue$Request;");}
 static boolean bridge(String name){return name.startsWith(R+"VirtualPreviewVisibilityBridge")||name.startsWith(R+"VirtualCopyExecutorFactory");}
 static void abi(ClassDef baseline,ClassDef overlay){
  if(overlay==null)throw new IllegalStateException("missing VirtualFrameQueue overlay "+baseline.getType());
  for(Field f:baseline.getFields()){
   boolean found=false;for(Field n:overlay.getFields())if(n.getName().equals(f.getName())&&n.getType().equals(f.getType()))found=true;
   if(!found)throw new IllegalStateException("field ABI break: "+baseline.getType()+"."+f.getName());
  }
  for(Method m:baseline.getMethods()){
   boolean found=false;for(Method n:overlay.getMethods())if(n.getName().equals(m.getName())&&desc(n).equals(desc(m)))found=true;
   if(!found)throw new IllegalStateException("method ABI break: "+baseline.getType()+"."+m.getName()+desc(m));
  }
 }
 static Instruction changedInvoke(Instruction invoke,MethodReference target){
  if(invoke instanceof Instruction35c){
   Instruction35c x=(Instruction35c)invoke;
   return new ImmutableInstruction35c(Opcode.INVOKE_STATIC,x.getRegisterCount(),x.getRegisterC(),x.getRegisterD(),
    x.getRegisterE(),x.getRegisterF(),x.getRegisterG(),target);
  }
  if(invoke instanceof Instruction3rc){
   Instruction3rc x=(Instruction3rc)invoke;
   return new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE,x.getStartRegister(),x.getRegisterCount(),target);
  }
  throw new IllegalStateException("unsupported instruction format "+invoke.getClass().getName());
 }
 static Method patchPreview(Method m,int[] count){
  if(m.getImplementation()==null)return m;
  MethodImplementation original=m.getImplementation();List<Instruction> instructions=new ArrayList<>();
  MethodReference to=new ImmutableMethodReference(R+"VirtualPreviewVisibilityBridge;","setVisibility",
     Arrays.asList("Landroid/view/View;","I"),"V");
  for(Instruction instruction:original.getInstructions()){
   if(instruction instanceof ReferenceInstruction){
    Reference ref=((ReferenceInstruction)instruction).getReference();
    if(ref instanceof MethodReference){
     MethodReference call=(MethodReference)ref;
     if((call.getDefiningClass().equals("Landroid/view/View;")
       ||call.getDefiningClass().equals("Landroid/widget/LinearLayout;"))
       &&call.getName().equals("setVisibility")&&desc(call).equals("(I)V")){
      instruction=changedInvoke(instruction,to);count[0]++;
     }
    }
   }
   instructions.add(instruction);
  }
  return method(m,new ImmutableMethodImplementation(original.getRegisterCount(),instructions,
      original.getTryBlocks(),original.getDebugItems()));
 }
 static Method patchCopyConstructor(Method m,int[] count){
  if(m.getImplementation()==null)return m;
  MethodImplementation original=m.getImplementation();List<Instruction> instructions=new ArrayList<>();
  for(Instruction x:original.getInstructions())instructions.add(x);
  MethodReference to=new ImmutableMethodReference(R+"VirtualCopyExecutorFactory;","create",
      Arrays.asList("Ljava/util/concurrent/ThreadFactory;"),"Ljava/util/concurrent/ExecutorService;");
  for(int i=0;i+2<instructions.size();i++){
   Instruction invoke=instructions.get(i),move=instructions.get(i+1),assign=instructions.get(i+2);
   if(!(invoke instanceof ReferenceInstruction)||move.getOpcode()!=Opcode.MOVE_RESULT_OBJECT
       ||!(move instanceof OneRegisterInstruction)||!(assign instanceof Instruction22c)
       ||assign.getOpcode()!=Opcode.IPUT_OBJECT)continue;
   Reference methodRef=((ReferenceInstruction)invoke).getReference();
   Reference fieldRef=((Instruction22c)assign).getReference();
   if(!(methodRef instanceof MethodReference)||!(fieldRef instanceof FieldReference))continue;
   MethodReference call=(MethodReference)methodRef;FieldReference field=(FieldReference)fieldRef;
   if(!call.getDefiningClass().equals("Ljava/util/concurrent/Executors;")
     ||!call.getName().equals("newSingleThreadExecutor")
     ||!desc(call).equals("(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")
     ||!field.getDefiningClass().equals(R+"VirtualWindows;")||!field.getName().equals("frameCopyWork"))continue;
   if(((OneRegisterInstruction)move).getRegisterA()!=((Instruction22c)assign).getRegisterA())
     throw new IllegalStateException("frame-copy register sequence changed");
   instructions.set(i,changedInvoke(invoke,to));count[0]++;
  }
  return method(m,new ImmutableMethodImplementation(original.getRegisterCount(),instructions,
     original.getTryBlocks(),original.getDebugItems()));
 }
 static ClassDef virtualWindows(ClassDef original){
  List<Method> methods=new ArrayList<>();int[] visibility={0},copy={0};int countPreview=0,countCtor=0;
  for(Method m:original.getMethods()){
   if(m.getName().equals("updatePreviewVisibility")){countPreview++;methods.add(patchPreview(m,visibility));}
   else if(m.getName().equals("<init>")){countCtor++;methods.add(patchCopyConstructor(m,copy));}
   else methods.add(m);
  }
  if(countPreview!=1||visibility[0]!=1||countCtor!=1||copy[0]!=1)
   throw new IllegalStateException("VirtualWindows hook mismatch: preview="+countPreview+"/"+visibility[0]+", copy="+countCtor+"/"+copy[0]);
  return withMethods(original,methods);
 }
 static DexFile version(DexFile d){
  return new DexRewriter(new RewriterModule(){
   public Rewriter<Instruction> getInstructionRewriter(Rewriters rs){
    final Rewriter<Instruction> parent=super.getInstructionRewriter(rs);
    return i->{Instruction x=parent.rewrite(i);
     if(x instanceof ReferenceInstruction){
      Reference ref=((ReferenceInstruction)x).getReference();
      if(ref instanceof StringReference&&((StringReference)ref).getString().equals("v1.150")){
       StringReference s=new ImmutableStringReference("v1.151");
       if(x instanceof Instruction21c)return new ImmutableInstruction21c(x.getOpcode(),((Instruction21c)x).getRegisterA(),s);
       if(x instanceof Instruction31c)return new ImmutableInstruction31c(x.getOpcode(),((Instruction31c)x).getRegisterA(),s);
      }
     }
     return x;};
   }
  }).getDexFileRewriter().rewrite(d);
 }
 static byte[] canonical(ClassDef c)throws Exception{
  MemoryDataStore mem=new MemoryDataStore();
  DexPool.writeTo(mem,new ImmutableDexFile(API,Arrays.asList(c)));
  return mem.getData();
 }
 static void merge(String base,String overlay,String dest)throws Exception{
  Map<String,ClassDef> patches=new TreeMap<>();
  for(ClassDef c:load(overlay).getClasses()){
   if(!queue(c.getType())&&!bridge(c.getType()))
    throw new IllegalStateException("unapproved overlay "+c.getType());
   patches.put(c.getType(),c);
  }
  Set<String> seen=new HashSet<>();List<ClassDef> all=new ArrayList<>();
  for(ClassDef c:load(base).getClasses()){
   seen.add(c.getType());
   if(queue(c.getType())){abi(c,patches.get(c.getType()));all.add(patches.get(c.getType()));}
   else if(c.getType().equals(R+"VirtualWindows;"))all.add(virtualWindows(c));
   else all.add(c);
  }
  if(!seen.contains(R+"VirtualWindows;")||!seen.contains(R+"VirtualFrameQueue;")||!seen.contains(R+"VirtualFrameQueue$Request;"))
   throw new IllegalStateException("required baseline classes absent");
  for(ClassDef c:patches.values()){
   if(queue(c.getType())){if(!seen.contains(c.getType()))throw new IllegalStateException("unexpected queue class");continue;}
   if(!seen.add(c.getType()))throw new IllegalStateException("duplicate bridge "+c.getType());
   all.add(c);
  }
  if(!patches.containsKey(R+"VirtualPreviewVisibilityBridge;")||!patches.containsKey(R+"VirtualCopyExecutorFactory;"))
   throw new IllegalStateException("both bridge classes required");
  DexFileFactory.writeDexFile(dest,version(new ImmutableDexFile(API,all)));
  System.out.println("v1.151 merged classes="+all.size());
 }
 static void audit(String base,String result,String overlay)throws Exception{
  Map<String,ClassDef> after=new TreeMap<>(),patches=new TreeMap<>();
  for(ClassDef c:load(result).getClasses())after.put(c.getType(),c);
  for(ClassDef c:load(overlay).getClasses())patches.put(c.getType(),c);
  int unchanged=0,changed=0;
  for(ClassDef before:load(base).getClasses()){
   ClassDef actual=after.remove(before.getType());if(actual==null)throw new IllegalStateException("class removed "+before.getType());
   ClassDef patched=queue(before.getType())?patches.get(before.getType()):
      before.getType().equals(R+"VirtualWindows;")?virtualWindows(before):before;
   ClassDef expected=version(new ImmutableDexFile(API,Arrays.asList(patched))).getClasses().iterator().next();
   if(!Arrays.equals(canonical(expected),canonical(actual)))
    throw new IllegalStateException("unexpected DEX mutation "+before.getType());
   if(Arrays.equals(canonical(before),canonical(actual)))unchanged++;else changed++;
  }
  if(!after.keySet().stream().allMatch(PatchVirtualLanesV151::bridge))
   throw new IllegalStateException("unexpected added type "+after.keySet());
  if(!after.containsKey(R+"VirtualPreviewVisibilityBridge;")||!after.containsKey(R+"VirtualCopyExecutorFactory;"))
   throw new IllegalStateException("required bridge absent");
  for(ClassDef added:after.values()){
   ClassDef expected=patches.get(added.getType());
   if(expected==null||!Arrays.equals(canonical(expected),canonical(added)))
    throw new IllegalStateException("bridge bytecode altered "+added.getType());
  }
  System.out.println("DEX audit passed: unchanged="+unchanged+", changes="+changed+", added="+after.keySet());
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=4)throw new IllegalArgumentException("merge|audit baseline.apk overlay.dex output.dex");
  if(args[0].equals("merge"))merge(args[1],args[2],args[3]);
  else if(args[0].equals("audit"))audit(args[1],args[3],args[2]);
  else throw new IllegalArgumentException("unknown mode");
 }
}
