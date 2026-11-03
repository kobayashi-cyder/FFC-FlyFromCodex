import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.instruction.formats.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.rewriter.*;
import com.android.tools.smali.dexlib2.writer.pool.*;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;

/** Adds 3 entrypoint wrappers to an exact authenticated v1.156 release DEX. */
public final class PatchAdV157 {
 static final String ROOT="Ljp/tapmarker/",BRIDGE=ROOT+"AdV157DexBridge;";
 static final String POLICY=ROOT+"AdTwoSecondRecovery;";
 static final String AUTO=ROOT+"AutoPilot;",SPLIT=ROOT+"SplitPilot;";
 static final String SLOT=ROOT+"SplitPilot$Slot;",SHELL=ROOT+"VirtualShell;";
 static final Opcodes API=Opcodes.forApi(24);
 static DexFile read(String path)throws Exception{return DexFileFactory.loadDexFile(new File(path),API);}
 static String descriptor(MethodReference m){
   StringBuilder b=new StringBuilder("(");
   for(CharSequence p:m.getParameterTypes())b.append(p);
   return b.append(')').append(m.getReturnType()).toString();
 }
 static ImmutableMethod copy(Method m,String name,int flags,MethodImplementation impl){
   return new ImmutableMethod(m.getDefiningClass(),name,m.getParameters(),m.getReturnType(),
       flags,m.getAnnotations(),m.getHiddenApiRestrictions(),impl);
 }
 static ClassDef replace(ClassDef c,List<Method> methods){
   return new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),
       c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
 }
 static Instruction call(Opcode opcode,MethodReference ref,int...r){
   int[] v=new int[5];if(r.length>5)throw new IllegalArgumentException("max five invocation registers");
   System.arraycopy(r,0,v,0,r.length);
   return new ImmutableInstruction35c(opcode,r.length,v[0],v[1],v[2],v[3],v[4],ref);
 }
 static MethodImplementation wrapped(String kind){
   List<Instruction> code=new ArrayList<>();
   if("physical".equals(kind)){
     MethodReference hook=new ImmutableMethodReference(BRIDGE,"runPhysical",
       Arrays.asList("Ljava/lang/Object;","I"),"V");
     code.add(call(Opcode.INVOKE_STATIC,hook,0,1));
     code.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
     return new ImmutableMethodImplementation(2,code,Collections.emptyList(),Collections.emptyList());
   }
   if("virtual".equals(kind)){
     MethodReference hook=new ImmutableMethodReference(BRIDGE,"runVirtual",
       Arrays.asList("Ljava/lang/Object;","Ljava/lang/Object;","Ljava/util/Map;",
         "Landroid/graphics/Rect;","I"),"V");
     code.add(call(Opcode.INVOKE_STATIC,hook,0,1,2,3,4));
     code.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
     return new ImmutableMethodImplementation(5,code,Collections.emptyList(),Collections.emptyList());
   }
   MethodReference hook=new ImmutableMethodReference(BRIDGE,"shell",
     Arrays.asList("Ljava/lang/Object;","I","Landroid/os/Parcel;","Landroid/os/Parcel;","I"),"Z");
   code.add(call(Opcode.INVOKE_STATIC,hook,1,2,3,4,5));
   code.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT,0));
   code.add(new ImmutableInstruction11x(Opcode.RETURN,0));
   return new ImmutableMethodImplementation(6,code,Collections.emptyList(),Collections.emptyList());
 }
 static ClassDef patch(ClassDef original,int[] hits){
   String name=original.getType();
   if(!name.equals(AUTO)&&!name.equals(SPLIT)&&!name.equals(SHELL))return original;
   List<Method> result=new ArrayList<>();
   for(Method m:original.getMethods()){
     String kind=null,desc=descriptor(m);
     if(name.equals(AUTO)&&m.getName().equals("phaseCycle")&&desc.equals("(I)V")){
       kind="physical";hits[0]++;
     }else if(name.equals(SPLIT)&&m.getName().equals("applySamples")&&
         desc.equals("("+SLOT+"Ljava/util/Map;Landroid/graphics/Rect;I)V")){
       kind="virtual";hits[1]++;
     }else if(name.equals(SHELL)&&m.getName().equals("onTransact")&&
         desc.equals("(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z")){
       kind="shell";hits[2]++;
     }
     if(kind==null){result.add(m);continue;}
     if(m.getImplementation()==null)throw new IllegalStateException("Not a concrete method "+m.getName());
     int privateSynthetic=(m.getAccessFlags()&~7)|2|0x1000;
     result.add(copy(m,"v157$original$"+m.getName(),privateSynthetic,m.getImplementation()));
     result.add(copy(m,m.getName(),m.getAccessFlags(),wrapped(kind)));
   }
   return replace(original,result);
 }
 static DexFile version(DexFile source){
   return new DexRewriter(new RewriterModule(){
     @Override public Rewriter<Instruction> getInstructionRewriter(Rewriters rewrite){
       Rewriter<Instruction> parent=super.getInstructionRewriter(rewrite);
       return original->{
         Instruction i=parent.rewrite(original);
         if(i instanceof ReferenceInstruction){
           Reference r=((ReferenceInstruction)i).getReference();
           if(r instanceof StringReference&&"v1.156".equals(((StringReference)r).getString())){
             StringReference changed=new ImmutableStringReference("v1.157");
             if(i instanceof Instruction21c)
               return new ImmutableInstruction21c(i.getOpcode(),((Instruction21c)i).getRegisterA(),changed);
             if(i instanceof Instruction31c)
               return new ImmutableInstruction31c(i.getOpcode(),((Instruction31c)i).getRegisterA(),changed);
           }
         }
         return i;
       };
     }
   }).getDexFileRewriter().rewrite(source);
 }
 static List<ClassDef> expected(String base,String extra)throws Exception{
   Map<String,ClassDef> newClasses=new TreeMap<>();
   for(ClassDef c:read(extra).getClasses()){
     String t=c.getType();
     if(!t.startsWith(BRIDGE.substring(0,BRIDGE.length()-1))&&
        !t.startsWith(POLICY.substring(0,POLICY.length()-1)))
       throw new SecurityException("Unexpected overlay class "+t);
     if(newClasses.put(t,c)!=null)throw new IllegalStateException("Duplicate "+t);
   }
   if(!newClasses.containsKey(BRIDGE)||!newClasses.containsKey(POLICY))
     throw new IllegalStateException("Missing approved helper classes");
   List<ClassDef> classes=new ArrayList<>();
   int[] changed={0,0,0};
   for(ClassDef c:read(base).getClasses()){
     if(newClasses.containsKey(c.getType()))throw new SecurityException("Replacement class "+c.getType());
     classes.add(patch(c,changed));
   }
   if(!Arrays.equals(changed,new int[]{1,1,1}))
     throw new IllegalStateException("Signed baseline ABI changed "+Arrays.toString(changed));
   classes.addAll(newClasses.values());
   return classes;
 }
 static byte[] canonical(ClassDef def)throws Exception{
   MemoryDataStore d=new MemoryDataStore();
   DexPool.writeTo(d,new ImmutableDexFile(API,Collections.singleton(def)));
   return d.getData();
 }
 static void merge(String base,String helper,String merged)throws Exception{
   DexFileFactory.writeDexFile(merged,version(new ImmutableDexFile(API,expected(base,helper))));
   System.out.println("v1.157: 3 controlled wrappers integrated; original methods retained");
 }
 static void audit(String base,String helper,String merged)throws Exception{
   Map<String,ClassDef> expected=new TreeMap<>(),actual=new TreeMap<>();
   for(ClassDef c:version(new ImmutableDexFile(API,expected(base,helper))).getClasses())
     expected.put(c.getType(),c);
   for(ClassDef c:read(merged).getClasses())actual.put(c.getType(),c);
   if(!expected.keySet().equals(actual.keySet()))throw new IllegalStateException("Unexpected DEX classes");
   for(String key:expected.keySet())
     if(!Arrays.equals(canonical(expected.get(key)),canonical(actual.get(key))))
       throw new IllegalStateException("DEX preservation audit mismatch: "+key);
   System.out.println("PASS: DEX preservation audit, classes="+actual.size());
 }
 public static void main(String[] args)throws Exception{
   if(args.length!=4)throw new IllegalArgumentException("merge|audit base.apk new.dex output.dex");
   if("merge".equals(args[0]))merge(args[1],args[2],args[3]);
   else if("audit".equals(args[0]))audit(args[1],args[2],args[3]);
   else throw new IllegalArgumentException(args[0]);
 }
}
