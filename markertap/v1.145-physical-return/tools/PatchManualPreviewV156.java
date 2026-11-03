import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.instruction.formats.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.rewriter.*;
import com.android.tools.smali.dexlib2.writer.pool.*;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;

/**
 * SHA-locked original-signed v1.155 -> v1.156 DEX overlay:
 * preserves all original behavior; only adds an idempotent, per-lane
 * manual-touch timeout monitor AFTER the existing manualBegin() method.
 */
public final class PatchManualPreviewV156 {
 static final String ROOT="Ljp/tapmarker/";
 static final String SPLIT=ROOT+"SplitPilot;";
 static final String WINDOW=ROOT+"VirtualWindows$Window;";
 static final String BRIDGE=ROOT+"ManualPreviewTimeoutBridge;";
 static final String POLICY=ROOT+"ManualPreviewTimeoutPolicy;";
 static final Opcodes API=Opcodes.forApi(24);

 static DexFile read(String path)throws Exception{return DexFileFactory.loadDexFile(new File(path),API);}
 static String descriptor(MethodReference m){
  StringBuilder s=new StringBuilder("(");
  for(CharSequence p:m.getParameterTypes())s.append(p);
  return s.append(')').append(m.getReturnType()).toString();
 }
 static ImmutableMethod copy(Method m,String name,int flags,MethodImplementation impl){
  return new ImmutableMethod(m.getDefiningClass(),name,m.getParameters(),m.getReturnType(),
   flags,m.getAnnotations(),m.getHiddenApiRestrictions(),impl);
 }
 static ClassDef replace(ClassDef cls,List<Method> methods){
  return new ImmutableClassDef(cls.getType(),cls.getAccessFlags(),cls.getSuperclass(),
   cls.getInterfaces(),cls.getSourceFile(),cls.getAnnotations(),cls.getFields(),methods);
 }
 static Instruction call(Opcode opcode,MethodReference ref,int... reg){
  int[] a=new int[5];if(reg.length>5)throw new IllegalArgumentException();
  for(int i=0;i<reg.length;i++)a[i]=reg[i];
  return new ImmutableInstruction35c(opcode,reg.length,a[0],a[1],a[2],a[3],a[4],ref);
 }
 static MethodImplementation wrapper(Method m,String oldName) {
  List<Instruction> code=new ArrayList<>();
  MethodReference old=new ImmutableMethodReference(SPLIT,oldName,
   Collections.singletonList(WINDOW),"Z");
  MethodReference hook=new ImmutableMethodReference(BRIDGE,"afterManualBegin",
   Arrays.asList("Ljava/lang/Object;","Ljava/lang/Object;","Z"),"V");
  // registers: v0 result, p0=v1 and p1=v2
  code.add(call(Opcode.INVOKE_DIRECT,old,1,2));
  code.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT,0));
  code.add(call(Opcode.INVOKE_STATIC,hook,1,2,0));
  code.add(new ImmutableInstruction11x(Opcode.RETURN,0));
  return new ImmutableMethodImplementation(3,code,Collections.emptyList(),Collections.emptyList());
 }
 static ClassDef patch(ClassDef cls,int[] hit){
  if(!cls.getType().equals(SPLIT))return cls;
  List<Method> methods=new ArrayList<>();
  for(Method m:cls.getMethods()){
   if(m.getName().equals("manualBegin")&&descriptor(m).equals("("+WINDOW+")Z")){
    if(m.getImplementation()==null)throw new IllegalStateException("abstract manualBegin");
    hit[0]++;
    String old="v156$original$manualBegin";
    int flags=(m.getAccessFlags()&~7)|2|0x1000;
    methods.add(copy(m,old,flags,m.getImplementation()));
    methods.add(copy(m,m.getName(),m.getAccessFlags(),wrapper(m,old)));
   }else methods.add(m);
  }
  return replace(cls,methods);
 }
 static DexFile version(DexFile d){
  return new DexRewriter(new RewriterModule(){
   @Override public Rewriter<Instruction> getInstructionRewriter(Rewriters rewriters){
    Rewriter<Instruction> parent=super.getInstructionRewriter(rewriters);
    return i->{
     Instruction x=parent.rewrite(i);
     if(x instanceof ReferenceInstruction){
      Reference r=((ReferenceInstruction)x).getReference();
      if(r instanceof StringReference&&((StringReference)r).getString().equals("v1.155")){
       StringReference val=new ImmutableStringReference("v1.156");
       if(x instanceof Instruction21c)return new ImmutableInstruction21c(x.getOpcode(),((Instruction21c)x).getRegisterA(),val);
       if(x instanceof Instruction31c)return new ImmutableInstruction31c(x.getOpcode(),((Instruction31c)x).getRegisterA(),val);
      }
     }
     return x;
    };
   }
  }).getDexFileRewriter().rewrite(d);
 }
 static List<ClassDef> expected(String base,String helper)throws Exception {
  Map<String,ClassDef> added=new TreeMap<>();
  for(ClassDef c:read(helper).getClasses()){
   String type=c.getType();
   if(!type.startsWith(BRIDGE.substring(0,BRIDGE.length()-1))&&
      !type.startsWith(POLICY.substring(0,POLICY.length()-1)))
    throw new SecurityException("unapproved helper class "+type);
   if(added.put(type,c)!=null)throw new IllegalStateException("duplicate helper");
  }
  if(!added.containsKey(BRIDGE)||!added.containsKey(POLICY))
   throw new IllegalStateException("missing two helper classes");
  List<ClassDef> result=new ArrayList<>();int[] hit={0};
  for(ClassDef c:read(base).getClasses()){
   if(added.containsKey(c.getType()))throw new IllegalStateException("source class replaced");
   result.add(patch(c,hit));
  }
  if(hit[0]!=1)throw new IllegalStateException("manualBegin ABI mismatch: "+hit[0]);
  result.addAll(added.values());
  return result;
 }
 static byte[] canonical(ClassDef c)throws Exception{
  MemoryDataStore m=new MemoryDataStore();
  DexPool.writeTo(m,new ImmutableDexFile(API,Collections.singleton(c)));
  return m.getData();
 }
 static void merge(String base,String helper,String output)throws Exception {
  DexFile d=version(new ImmutableDexFile(API,expected(base,helper)));
  DexFileFactory.writeDexFile(output,d);
  System.out.println("v1.156 one-method recovery timeout hook integrated; base preserved");
 }
 static void audit(String base,String helper,String output)throws Exception{
  DexFile desired=version(new ImmutableDexFile(API,expected(base,helper)));
  Map<String,ClassDef> exp=new TreeMap<>(),got=new TreeMap<>();
  for(ClassDef c:desired.getClasses())exp.put(c.getType(),c);
  for(ClassDef c:read(output).getClasses())got.put(c.getType(),c);
  if(!exp.keySet().equals(got.keySet()))throw new IllegalStateException("DEX class set changed");
  for(Map.Entry<String,ClassDef> e:exp.entrySet())
   if(!Arrays.equals(canonical(e.getValue()),canonical(got.get(e.getKey()))))
    throw new IllegalStateException("DEX changed outside audited patch "+e.getKey());
  System.out.println("PASS v1.156 DEX audit: "+got.size()+" classes, one wrapped original method");
 }
 public static void main(String[] a)throws Exception{
  if(a.length!=4)throw new IllegalArgumentException("merge|audit signedbase.apk helper.dex final.dex");
  if("merge".equals(a[0]))merge(a[1],a[2],a[3]);
  else if("audit".equals(a[0]))audit(a[1],a[2],a[3]);
  else throw new IllegalArgumentException(a[0]);
 }
}
