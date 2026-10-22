import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.rewriter.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.iface.instruction.formats.*;
import com.android.tools.smali.dexlib2.iface.value.*;
import com.android.tools.smali.dexlib2.immutable.value.ImmutableStringEncodedValue;

/** Compile-only API overlays and surgical DEX replacement against the supplied APK. */
public final class PatchRuntime {
 static final String PKG="jp/tapmarker/";
 static void api(Path baseline,Path out)throws Exception{
  for(String name:new String[]{"VirtualWindows","VirtualWindows$Window","DiagnosticJournal","VirtualBridgeProvider"}){
   ClassReader reader=new ClassReader(Files.readAllBytes(baseline.resolve(PKG+name+".class")));ClassWriter writer=new ClassWriter(0);
   reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
    final Set<String> methods=new HashSet<>();
    public FieldVisitor visitField(int access,String field,String descriptor,String signature,Object value){return super.visitField(access,field,name.equals("VirtualWindows$Window")&&field.equals("latest")?"Ljp/tapmarker/RegionImage;":descriptor,signature,value);}
    public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){methods.add(n+d);return super.visitMethod(a,n,d,s,e);}
    void stub(String n,String d,boolean stat){if(methods.contains(n+d))return;MethodVisitor m=writer.visitMethod(stat?Opcodes.ACC_STATIC:0,n,d,n.equals("samplePixels")?d.replace("Ljava/util/function/Function;","Ljava/util/function/Function<Ljp/tapmarker/FrameCircuit$Frame;Ljava/lang/Runnable;>;"):null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,16);m.visitEnd();}
    public void visitEnd(){
     if(name.equals("VirtualWindows")){
      stub("samplePixels","(Ljp/tapmarker/VirtualWindows$Window;Ljp/tapmarker/RegionPlan;Ljava/util/function/BooleanSupplier;Ljava/util/function/Function;Ljava/lang/Runnable;)V",false);
      stub("plan","(Ljp/tapmarker/VirtualWindows$Window;Ljp/tapmarker/RegionPlan;)V",false);stub("manualPreview","(Ljp/tapmarker/VirtualWindows$Window;)V",false);
     }
     if(name.equals("DiagnosticJournal"))stub("frame","(Ljava/lang/String;IIIILjp/tapmarker/RegionSampler$Pixels;JLjava/lang/String;)V",false);
     if(name.equals("VirtualBridgeProvider"))stub("emergencyStop","()V",true);
     super.visitEnd();
    }
   },0);
   Path path=out.resolve(PKG+name+".class");Files.createDirectories(path.getParent());Files.write(path,writer.toByteArray());
  }
 }
 static void merge(String original,String patch,String output)throws Exception{
  com.android.tools.smali.dexlib2.Opcodes op=com.android.tools.smali.dexlib2.Opcodes.forApi(24);
  DexFile base=DexFileFactory.loadDexFile(new File(original),op),changes=DexFileFactory.loadDexFile(new File(patch),op);
  Set<String> owners=new TreeSet<>();for(ClassDef c:changes.getClasses()){String n=c.getType();int at=n.indexOf('$');owners.add(at<0?n.substring(0,n.length()-1):n.substring(0,at));}
  final Map<String,String> rename=new HashMap<>();for(ClassDef c:changes.getClasses())if(c.getType().contains("$$ExternalSynthetic")){String type=c.getType();rename.put(type,type.substring(0,type.length()-1)+"$Integrated125;");}
  changes=new DexRewriter(new RewriterModule(){public Rewriter<String> getTypeRewriter(Rewriters rewriters){return type->rename.containsKey(type)?rename.get(type):type;}}).getDexFileRewriter().rewrite(changes);
  Set<String> shared=new TreeSet<>();
  for(ClassDef c:base.getClasses()){
   String name=c.getType();int split=name.indexOf('$');String root=split<0?name.substring(0,name.length()-1):name.substring(0,split);if(owners.contains(root))continue;
   for(Method method:c.getMethods())if(method.getImplementation()!=null)for(Instruction instruction:method.getImplementation().getInstructions())if(instruction instanceof ReferenceInstruction){
    Reference reference=((ReferenceInstruction)instruction).getReference();String owner=reference instanceof MethodReference?((MethodReference)reference).getDefiningClass():reference instanceof FieldReference?((FieldReference)reference).getDefiningClass():reference instanceof TypeReference?((TypeReference)reference).getType():"";
    if(owner.contains("$$ExternalSynthetic"))shared.add(owner);
   }
  }

  owners.add("Ljp/tapmarker/FlyCircuit");
  List<ClassDef> merged=new ArrayList<>();int kept=0,replaced=0;
  for(ClassDef c:base.getClasses()){
   String n=c.getType();int at=n.indexOf('$');String owner=at<0?n.substring(0,n.length()-1):n.substring(0,at);
   if(owners.contains(owner)&&!shared.contains(n))replaced++;else{merged.add(c.getType().equals("Ljp/tapmarker/TapService;")?stabilizeAccessibility(c):c.getType().equals("Ljp/tapmarker/VirtualWindows;")?stabilizeVirtualWindows(c):c.getType().equals("Ljp/tapmarker/AutoPilot;")?migrateLoader(c):c.getType().equals("Ljp/tapmarker/VirtualShell;")?migrateShell(c):c);kept++;}
  }
  merged.addAll(changes.getClasses());DexFile finalFile=new DexRewriter(customRewriters(true)).getDexFileRewriter().rewrite(new ImmutableDexFile(op,merged));DexFileFactory.writeDexFile(output,finalFile);
  System.out.println("Shared synthetic types: "+shared);
  System.out.println("Preserved "+kept+" classes; replaced "+replaced+" classes with "+changes.getClasses().size()+" classes; owners="+owners);
 }
 static final String[][] TYPES={{"FlyCircuit","CustomConnectome"},{"BancSelector","ActionConnectome"},{"BancRuntime","ConnectomeRuntime"},{"BancAutonomy","ConnectomeAutonomy"},{"BancKnowHow","ConnectomeKnowHow"},{"BancGraphIdentity","ConnectomeGraphIdentity"},{"BancSettings","ConnectomeSettings"},{"BancUiControls","ConnectomeUiControls"}};
 public static String customType(String type){int count=0;while(count<type.length()&&type.charAt(count)=='[')count++;String prefix=type.substring(0,count),base=type.substring(count);for(String[] pair:TYPES){String old="Ljp/tapmarker/"+pair[0];if(base.equals(old+";")||base.startsWith(old+"$"))return prefix+"Ljp/tapmarker/"+pair[1]+base.substring(old.length());}return type;}
 public static String customText(String text){
  if(text.equals("v1.123"))return "v1.131";
  if(text.equals("circuit.bin"))return "custom-connectome.bin";
  if(text.startsWith("BANC888-native-1|"))return text; // Existing non-executable memory-key salt.
  if(text.startsWith("htem/BANC-project /"))return "MarkerTap/self-authored/action-gate-v1";
  return text.replace("BANC888：6集約ノード・16接続","自作行動回路：16ノード・24接続").replace("BANC888","自作回路").replace("BANC","自作回路");
 }
 /** dexlib does not rewrite const-string instructions via StringReference alone. */
 public static RewriterModule customRewriters(final boolean renameTypes){return new RewriterModule(){
  public Rewriter<String> getTypeRewriter(Rewriters r){return renameTypes?PatchRuntime::customType:super.getTypeRewriter(r);}
  public Rewriter<StringReference> getStringReferenceRewriter(Rewriters r){return value->new ImmutableStringReference(customText(value.getString()));}
  public Rewriter<Instruction> getInstructionRewriter(Rewriters r){
   final Rewriter<Instruction> parent=super.getInstructionRewriter(r);
   return instruction->{Instruction rewritten=parent.rewrite(instruction);
    if(rewritten instanceof ReferenceInstruction){Reference ref=((ReferenceInstruction)rewritten).getReference();if(ref instanceof StringReference){
     ImmutableStringReference text=new ImmutableStringReference(customText(((StringReference)ref).getString()));
     if(rewritten instanceof Instruction21c)return new ImmutableInstruction21c(rewritten.getOpcode(),((Instruction21c)rewritten).getRegisterA(),text);
     if(rewritten instanceof Instruction31c)return new ImmutableInstruction31c(rewritten.getOpcode(),((Instruction31c)rewritten).getRegisterA(),text);
    }}return rewritten;
   };
  }
  public Rewriter<EncodedValue> getEncodedValueRewriter(Rewriters r){final Rewriter<EncodedValue> parent=super.getEncodedValueRewriter(r);return value->value instanceof StringEncodedValue?new ImmutableStringEncodedValue(customText(((StringEncodedValue)value).getValue())):parent.rewrite(value);}
 };}
 /** Shell-side pre-start barrier. The original methods are kept intact under
  * new private names; only the entrypoints are wrapped, so input and virtual
  * display semantics remain those of the current integrated foundation. */
 /** Accessibility feedback interruption is not service destruction or user stop.
  * This service emits no spoken/haptic feedback; retain the active session.
  * Explicit stop and onDestroy keep their original shutdown behavior. */
 static ClassDef stabilizeAccessibility(ClassDef original){
  List<Method> methods=new ArrayList<>();boolean found=false;
  for(Method method:original.getMethods()){
   if(method.getName().equals("onInterrupt") && method.getParameterTypes().isEmpty() && method.getReturnType().equals("V")){
    found=true;
    ImmutableMethodImplementation body=new ImmutableMethodImplementation(1,
     Arrays.asList(new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(method.getDefiningClass(),method.getName(),method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),body));
   }else methods.add(method);
  }
  if(!found)throw new IllegalStateException("Accessibility interruption callback missing");
  return new ImmutableClassDef(original.getType(),original.getAccessFlags(),original.getSuperclass(),original.getInterfaces(),original.getSourceFile(),original.getAnnotations(),original.getFields(),methods);
 }
 static ClassDef migrateShell(ClassDef original){
  final String owner=original.getType();
  final List<Method> methods=new ArrayList<>();
  boolean sawCreate=false,sawRegister=false,sawOwnerDeath=false;
  for(Method method:original.getMethods()){
   if(method.getName().equals("registerOwner") && method.getParameterTypes().size()==1){
    sawRegister=true;
    methods.add(new ImmutableMethod(owner,"registerOwnerOriginal",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),method.getImplementation()));
    ImmutableMethodReference orig=new ImmutableMethodReference(owner,"registerOwnerOriginal",Arrays.asList("Landroid/os/IBinder;"),"V");
    ImmutableMethodReference after=new ImmutableMethodReference("Ljp/tapmarker/ResidualFence;","registered",Arrays.asList(owner),"V");
    ImmutableMethodImplementation code=new ImmutableMethodImplementation(2,Arrays.asList(
      new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,2,0,1,0,0,0,orig),
      new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,0,0,0,0,0,after),
      new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(owner,"registerOwner",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),code));
   }else if(method.getName().equals("create") && method.getParameterTypes().size()==5){
    sawCreate=true;
    methods.add(new ImmutableMethod(owner,"createOriginal",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),method.getImplementation()));
    ImmutableMethodReference before=new ImmutableMethodReference("Ljp/tapmarker/ResidualFence;","beforeCreate",Arrays.asList(owner),"V");
    ImmutableMethodReference orig=new ImmutableMethodReference(owner,"createOriginal",Arrays.asList("I","I","I","Ljava/lang/String;","Landroid/view/Surface;"),"I");
    // six input registers (this+5 arguments), one local for the return value.
    ImmutableMethodImplementation code=new ImmutableMethodImplementation(7,Arrays.asList(
      new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,1,0,0,0,0,before),
      new ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE,1,6,orig),
      new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),
      new ImmutableInstruction11x(Opcode.RETURN,0)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(owner,"create",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),code));
   }else if(method.getName().equals("lambda$1$jp-tapmarker-VirtualShell") && method.getParameterTypes().isEmpty() && method.getReturnType().equals("V")){
    sawOwnerDeath=true;
    ImmutableMethodReference cleanup=new ImmutableMethodReference("Ljp/tapmarker/ShellOwnerDeath;","close",Arrays.asList(owner),"V");
    ImmutableMethodImplementation code=new ImmutableMethodImplementation(1,Arrays.asList(
      new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,0,0,0,0,0,cleanup),
      new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(owner,method.getName(),method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),code));
   }else methods.add(method);
  }
  if(!sawCreate||!sawRegister||!sawOwnerDeath)throw new IllegalStateException("Shell integration needs registration, display creation and owner-death cleanup");
  return new ImmutableClassDef(original.getType(),original.getAccessFlags(),original.getSuperclass(),original.getInterfaces(),original.getSourceFile(),original.getAnnotations(),original.getFields(),methods);
 }
 /** Replace precisely two methods; all other virtual display and input paths stay unchanged.
  * The new bridge has no access to the image while frameLock is held. */
 static ClassDef stabilizeVirtualWindows(ClassDef original){
  final String bridge="Ljp/tapmarker/FrameLockBridge;";
  final List<Method> methods=new ArrayList<>();
  boolean replacedPreview=false,replacedCopy=false,replacedBitmap=false;
  for(Method method:original.getMethods()){
   final String name=method.getName();
   ImmutableMethodImplementation body=null;
   if(name.equals("lambda$23$jp-tapmarker-VirtualWindows") && method.getParameterTypes().size()==7){
    ImmutableMethodReference target=new ImmutableMethodReference(bridge,"preview",Arrays.asList(
     "Ljp/tapmarker/VirtualWindows;","Ljp/tapmarker/VirtualWindows$Window;","I",
     "Ljp/tapmarker/RegionPlan;","I","Ljp/tapmarker/PreviewConnectome$Schedule;","I","I"),"V");
    body=new ImmutableMethodImplementation(8,Arrays.asList(
     new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE,0,8,target),
     new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    replacedPreview=true;
   }else if(name.equals("image") && method.getParameterTypes().size()==1 &&
            method.getReturnType().equals("Landroid/graphics/Bitmap;")){
    ImmutableMethodReference target=new ImmutableMethodReference(bridge,"image",Arrays.asList(
     "Ljp/tapmarker/VirtualWindows$Window;"),"Landroid/graphics/Bitmap;");
    body=new ImmutableMethodImplementation(2,Arrays.asList(
     new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,1,0,0,0,0,target),
     new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT,0),
     new ImmutableInstruction11x(Opcode.RETURN_OBJECT,0)),Collections.emptyList(),Collections.emptyList());
    replacedBitmap=true;
   }else if(name.equals("lambda$34") && method.getParameterTypes().size()==5){
    ImmutableMethodReference target=new ImmutableMethodReference(bridge,"copy",Arrays.asList(
     "Ljp/tapmarker/VirtualWindows$Window;","Ljava/util/function/BooleanSupplier;",
     "Ljp/tapmarker/RegionPlan$Builder;","[J","Ljp/tapmarker/FrameCircuit$Workspace;"),"Ljp/tapmarker/FrameCircuit$Input;");
    body=new ImmutableMethodImplementation(5,Arrays.asList(
     new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE,0,5,target),
     new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT,0),
     new ImmutableInstruction11x(Opcode.RETURN_OBJECT,0)),Collections.emptyList(),Collections.emptyList());
    replacedCopy=true;
   }
   methods.add(body==null?method:new ImmutableMethod(method.getDefiningClass(),name,
     method.getParameters(),method.getReturnType(),method.getAccessFlags(),
     method.getAnnotations(),method.getHiddenApiRestrictions(),body));
  }
  if(!replacedPreview||!replacedCopy||!replacedBitmap)throw new IllegalStateException("FrameLock bridge target methods missing: preview="+replacedPreview+",copy="+replacedCopy+",image="+replacedBitmap);
  return new ImmutableClassDef(original.getType(),original.getAccessFlags(),original.getSuperclass(),
   original.getInterfaces(),original.getSourceFile(),original.getAnnotations(),original.getFields(),methods);
 }
 static ClassDef migrateLoader(ClassDef original){
  List<Method> methods=new ArrayList<>();
  for(Method method:original.getMethods()){
   if(method.getName().equals("load")&&method.getParameterTypes().isEmpty()){
    ImmutableMethodReference target=new ImmutableMethodReference("Ljp/tapmarker/CustomMemory;","load",Arrays.asList("Ljp/tapmarker/AutoPilot;"),"V");
    ImmutableMethodImplementation code=new ImmutableMethodImplementation(1,Arrays.asList(new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,0,0,0,0,0,target),new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(method.getDefiningClass(),method.getName(),method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),code));
   }else methods.add(method);
  }
  return new ImmutableClassDef(original.getType(),original.getAccessFlags(),original.getSuperclass(),original.getInterfaces(),original.getSourceFile(),original.getAnnotations(),original.getFields(),methods);
 }
 public static void main(String[] args)throws Exception{if(args[0].equals("api"))api(Paths.get(args[1]),Paths.get(args[2]));else if(args[0].equals("merge"))merge(args[1],args[2],args[3]);else throw new IllegalArgumentException("command");}
}
