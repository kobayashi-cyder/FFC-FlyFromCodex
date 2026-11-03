import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.rewriter.*;
import com.android.tools.smali.dexlib2.writer.pool.*;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;

/**
 * SHA-pinned v1.151 -> v1.153 DEX integration.
 * Non-invasive wrappers preserve original method bodies as private legacy
 * methods, and use a typed object-only bridge to avoid decompiled source ABI
 * drift. No original classes are silently replaced.
 */
public final class PatchEmbeddedRecoveryV153 {
 static final String R="Ljp/tapmarker/";
 static final String VW=R+"VirtualWindows;";
 static final String BR=R+"EmbeddedRecoveryBridge;";
 static final String NW=R+"VirtualWindows$Window;";
 static final Opcodes API=Opcodes.forApi(24);
 static DexFile load(String p)throws Exception{return DexFileFactory.loadDexFile(new File(p),API);}
 static String desc(MethodReference m){
  StringBuilder b=new StringBuilder("(");
  for(CharSequence p:m.getParameterTypes())b.append(p);
  return b.append(')').append(m.getReturnType()).toString();
 }
 static ImmutableMethod m(Method old,String name,int flags,MethodImplementation body){
  return new ImmutableMethod(old.getDefiningClass(),name,old.getParameters(),
    old.getReturnType(),flags,old.getAnnotations(),
    old.getHiddenApiRestrictions(),body);
 }
 static ClassDef with(ClassDef old,List<Method> methods){
  return new ImmutableClassDef(old.getType(),old.getAccessFlags(),
   old.getSuperclass(),old.getInterfaces(),old.getSourceFile(),
   old.getAnnotations(),old.getFields(),methods);
 }
 static MethodReference ref(String owner,String name,String result,String... params) {
  return new ImmutableMethodReference(owner,name,Arrays.asList(params),result);
 }
 static Instruction invoke(Opcode opcode,MethodReference r,int... v){
  int[] slots={0,0,0,0,0};
  if(v.length>5)throw new IllegalArgumentException("non-range invoke");
  for(int i=0;i<v.length;i++)slots[i]=v[i];
  return new ImmutableInstruction35c(opcode,v.length,slots[0],slots[1],
    slots[2],slots[3],slots[4],r);
 }
 static MethodImplementation wrapper(Method method,String renamed,int kind){
  // Registers v0, p0=v1, p1=v2 for 1-argument methods.
  List<Instruction> a=new ArrayList<>();
  MethodReference bridge;
  MethodReference legacy=ref(method.getDefiningClass(),renamed,
    method.getReturnType(),method.getParameterTypes().toArray(new String[0]));
  if(kind==3) {
   // startAutonomy()V: reset only per-display refresh episodes on explicit start.
   bridge=ref(BR,"explicitStart","V","Ljava/lang/Object;");
   a.add(invoke(Opcode.INVOKE_STATIC,bridge,0));
   a.add(invoke(Opcode.INVOKE_DIRECT,legacy,0));
   a.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
   return new ImmutableMethodImplementation(1,a,Collections.emptyList(),Collections.emptyList());
  }
  if(kind==0)bridge=ref(BR,"privilegedOpen","Z","Ljava/lang/Object;","Z");
  else if(kind==1)bridge=ref(BR,"allowCheck","Z","Ljava/lang/Object;","Ljava/lang/Object;");
  else bridge=ref(BR,"displayRemoved","Z","Ljava/lang/Object;","I");
  a.add(invoke(Opcode.INVOKE_STATIC,bridge,1,2));
  a.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT,0));
  if(kind==1) {
   // false: block stale/unsafe input; true: run complete original checks.
   a.add(new ImmutableInstruction21t(Opcode.IF_NEZ,0,4));
   a.add(new ImmutableInstruction11n(Opcode.CONST_4,0,0));
   a.add(new ImmutableInstruction11x(Opcode.RETURN,0));
   a.add(invoke(Opcode.INVOKE_DIRECT,legacy,1,2));
   a.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT,0));
   a.add(new ImmutableInstruction11x(Opcode.RETURN,0));
  } else {
   // true: bridge consumed the request; false: existing fallback path.
   a.add(new ImmutableInstruction21t(Opcode.IF_EQZ,0,3));
   a.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
   a.add(invoke(Opcode.INVOKE_DIRECT,legacy,1,2));
   a.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
  }
  return new ImmutableMethodImplementation(3,a,Collections.emptyList(),Collections.emptyList());
 }
 static ClassDef patch(ClassDef cls,int[] hits) {
  boolean vw=cls.getType().equals(VW);
  boolean listener=cls.getType().startsWith(R+"VirtualWindows$");
  if(!vw&&!listener)return cls;
  List<Method> methods=new ArrayList<>();
  for(Method m:cls.getMethods()){
   String d=desc(m);
   int kind=-1;
   if(vw&&m.getName().equals("open")&&d.equals("(Z)V"))kind=0;
   else if(vw&&m.getName().equals("check")&&d.equals("("+NW+")Z"))kind=1;
   else if(vw&&m.getName().equals("startAutonomy")&&d.equals("()V"))kind=3;
   else if(listener&&m.getName().equals("onDisplayRemoved")&&d.equals("(I)V"))kind=2;
   if(kind<0){methods.add(m);continue;}
   hits[kind]++;
   if(m.getImplementation()==null)throw new IllegalStateException("abstract recovery target");
   String legacy="v153$original$"+m.getName();
   int flags=(m.getAccessFlags()&~7)|2|0x1000; // private+synthetic
   methods.add(m(m,legacy,flags,m.getImplementation()));
   methods.add(m(m,m.getName(),m.getAccessFlags(),wrapper(m,legacy,kind)));
  }
  return with(cls,methods);
 }
 static DexFile version(DexFile d){
  return new DexRewriter(new RewriterModule(){
   @Override public Rewriter<Instruction> getInstructionRewriter(Rewriters rs){
    Rewriter<Instruction> parent=super.getInstructionRewriter(rs);
    return i->{
     Instruction x=parent.rewrite(i);
     if(x instanceof ReferenceInstruction){
      Reference r=((ReferenceInstruction)x).getReference();
      if(r instanceof StringReference&&((StringReference)r).getString().equals("v1.151")){
       StringReference s=new ImmutableStringReference("v1.153");
       if(x instanceof Instruction21c)return new ImmutableInstruction21c(x.getOpcode(),
          ((Instruction21c)x).getRegisterA(),s);
       if(x instanceof Instruction31c)return new ImmutableInstruction31c(x.getOpcode(),
          ((Instruction31c)x).getRegisterA(),s);
      }
     }
     return x;
    };
   }
  }).getDexFileRewriter().rewrite(d);
 }
 static boolean newClass(String t){return t.startsWith(BR.substring(0,BR.length()-1))
  ||t.startsWith(R+"VirtualRefreshPolicy");}
 static Map<String,ClassDef> overlay(String file)throws Exception {
  Map<String,ClassDef> map=new TreeMap<>();
  for(ClassDef c:load(file).getClasses()){
   if(!newClass(c.getType()))throw new IllegalStateException("unapproved overlay class "+c.getType());
   if(map.put(c.getType(),c)!=null)throw new IllegalStateException("duplicate class");
  }
  if(!map.containsKey(BR)||!map.containsKey(R+"VirtualRefreshPolicy;"))
   throw new IllegalStateException("bridge and per-window policy both required");
  return map;
 }
 static List<ClassDef> expected(String base,String over)throws Exception {
  Map<String,ClassDef> added=overlay(over);
  List<ClassDef> all=new ArrayList<>();
  int[] hits=new int[4];
  for(ClassDef c:load(base).getClasses()){
   if(added.containsKey(c.getType()))throw new IllegalStateException("overlay replaces existing class");
   all.add(patch(c,hits));
  }
  if(!Arrays.equals(hits,new int[]{1,1,1,1}))
   throw new IllegalStateException("baseline ABI changed; hook count="+Arrays.toString(hits));
  all.addAll(added.values());
  return all;
 }
 static byte[] canonical(ClassDef c)throws Exception {
  MemoryDataStore m=new MemoryDataStore();
  DexPool.writeTo(m,new ImmutableDexFile(API,Collections.singleton(c)));
  return m.getData();
 }
 static void merge(String baseline,String over,String out)throws Exception {
  DexFile result=version(new ImmutableDexFile(API,expected(baseline,over)));
  DexFileFactory.writeDexFile(out,result);
  System.out.println("MarkerTap v1.153 DEX overlay integrated; original methods retained.");
 }
 static void audit(String baseline,String over,String out)throws Exception {
  DexFile desired=version(new ImmutableDexFile(API,expected(baseline,over)));
  Map<String,ClassDef> expected=new TreeMap<>(),actual=new TreeMap<>();
  for(ClassDef c:desired.getClasses())expected.put(c.getType(),c);
  for(ClassDef c:load(out).getClasses())actual.put(c.getType(),c);
  if(!actual.keySet().equals(expected.keySet()))
   throw new IllegalStateException("DEX class population changed");
  for(Map.Entry<String,ClassDef> e:expected.entrySet()){
   if(!Arrays.equals(canonical(e.getValue()),canonical(actual.get(e.getKey()))))
    throw new IllegalStateException("DEX audit mismatch "+e.getKey());
  }
  System.out.println("PASS exact DEX audit: "+actual.size()+" classes, four original methods preserved as private legacy paths");
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=4)throw new IllegalArgumentException("merge|audit baseline.apk overlay.dex final.dex");
  if(args[0].equals("merge"))merge(args[1],args[2],args[3]);
  else if(args[0].equals("audit"))audit(args[1],args[2],args[3]);
  else throw new IllegalArgumentException(args[0]);
 }
}
