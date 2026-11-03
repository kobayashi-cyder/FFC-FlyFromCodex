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
 * v1.151 -> v1.155 crash-loop rollback, signed-APK DEX overlay. Preserves every original
 * class and method except 3 WirelessAdb methods and explicit virtual opening, each renamed to a
 * private fallback and wrapped. 4 new jp.tapmarker classes admitted.
 * Android service authorization and local ADB TLS handling are untouched.
 */
public final class PatchCrashSafeV155 {
 static final String R="Ljp/tapmarker/";
 static final String ADB=R+"WirelessAdb;";
 static final String BRIDGE=R+"SafeLoopbackAdbBridge;";
 static final String OPEN=R+"SafeEmbeddedOpenBridge;";
 static final String RETRY=R+"LoopbackRetryPolicy;";
 static final String VW=R+"VirtualWindows;";
 static final String ROUTE=R+"LoopbackRoutePolicy;";
 static final Opcodes API=Opcodes.forApi(24);
 static DexFile load(String path)throws Exception {
  return DexFileFactory.loadDexFile(new File(path),API);
 }
 static String desc(MethodReference m){
  StringBuilder b=new StringBuilder("(");
  for(CharSequence x:m.getParameterTypes())b.append(x);
  return b.append(')').append(m.getReturnType()).toString();
 }
 static ImmutableMethod method(Method old,String name,int flags,MethodImplementation body) {
  return new ImmutableMethod(old.getDefiningClass(),name,old.getParameters(),
   old.getReturnType(),flags,old.getAnnotations(),
   old.getHiddenApiRestrictions(),body);
 }
 static ClassDef with(ClassDef old,List<Method> methods) {
  return new ImmutableClassDef(old.getType(),old.getAccessFlags(),
   old.getSuperclass(),old.getInterfaces(),old.getSourceFile(),
   old.getAnnotations(),old.getFields(),methods);
 }
 static MethodReference ref(String owner,String name,String out,String...args){
  return new ImmutableMethodReference(owner,name,Arrays.asList(args),out);
 }
 static Instruction invoke(Opcode opcode,MethodReference r,int... regs) {
  if(regs.length>5)throw new IllegalArgumentException("too many regs");
  int[] v=new int[5];for(int i=0;i<regs.length;i++)v[i]=regs[i];
  return new ImmutableInstruction35c(opcode,regs.length,v[0],v[1],v[2],v[3],v[4],r);
 }
 static MethodImplementation wrapper(Method m,String legacy,int kind){
  // kind 0: wifiConnected()Z; 1: discover(I)V; 2: finish(I)V; 3: open(Z)V.
  List<Instruction> a=new ArrayList<>();
  MethodReference old=ref(m.getDefiningClass(),legacy,m.getReturnType(),m.getParameterTypes().toArray(new String[0]));
  MethodReference bridge;
  if(kind==3){
   // open(Z)V: do not fall back to the obsolete external Shizuku service.
   MethodReference guide=ref(OPEN,"open","Z","Ljava/lang/Object;","Z");
   a.add(invoke(Opcode.INVOKE_STATIC,guide,1,2));
   a.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT,0));
   a.add(new ImmutableInstruction21t(Opcode.IF_EQZ,0,3));
   a.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
   a.add(invoke(Opcode.INVOKE_DIRECT,old,1,2));
   a.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
   return new ImmutableMethodImplementation(3,a,Collections.emptyList(),Collections.emptyList());
  }
  if(kind==0){
   // total registers=2: v0 and p0=v1
   a.add(invoke(Opcode.INVOKE_DIRECT,old,1));
   a.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT,0));
   bridge=ref(BRIDGE,"transportAvailable","Z","Ljava/lang/Object;","Z");
   a.add(invoke(Opcode.INVOKE_STATIC,bridge,1,0));
   a.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT,0));
   a.add(new ImmutableInstruction11x(Opcode.RETURN,0));
   return new ImmutableMethodImplementation(2,a,Collections.emptyList(),Collections.emptyList());
  }
  // total registers=3: v0, p0=v1, p1=v2
  if(kind==1){
   bridge=ref(BRIDGE,"discover","Z","Ljava/lang/Object;","I");
   a.add(invoke(Opcode.INVOKE_STATIC,bridge,1,2));
   a.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT,0));
   a.add(new ImmutableInstruction21t(Opcode.IF_EQZ,0,3));
   a.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
   a.add(invoke(Opcode.INVOKE_DIRECT,old,1,2));
   a.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
  }else if(kind==2){
   // Persist only the actual shell-UID approved local token while it is live.
   bridge=ref(BRIDGE,"rememberVerified","V","Ljava/lang/Object;","I");
   a.add(invoke(Opcode.INVOKE_STATIC,bridge,1,2));
   a.add(invoke(Opcode.INVOKE_DIRECT,old,1,2));
   a.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
  }else throw new IllegalArgumentException("wrapper kind");
  return new ImmutableMethodImplementation(3,a,Collections.emptyList(),Collections.emptyList());
 }
 static ClassDef patch(ClassDef cls,int[] found){
  if(!cls.getType().equals(ADB)&&!cls.getType().equals(VW))return cls;
  List<Method> methods=new ArrayList<>();
  for(Method m:cls.getMethods()){
   int kind=-1;String d=desc(m);
   if(cls.getType().equals(ADB)){
    if(m.getName().equals("wifiConnected")&&d.equals("()Z"))kind=0;
    if(m.getName().equals("discover")&&d.equals("(I)V"))kind=1;
    if(m.getName().equals("finish")&&d.equals("(I)V"))kind=2;
   } else if(m.getName().equals("open")&&d.equals("(Z)V"))kind=3;
   if(kind<0){methods.add(m);continue;}
   if(m.getImplementation()==null)throw new IllegalStateException("abstract entrypoint "+m.getName());
   found[kind]++;
   String original="v155$original$"+m.getName();
   int flags=(m.getAccessFlags()&~7)|2|0x1000;
   methods.add(method(m,original,flags,m.getImplementation()));
   methods.add(method(m,m.getName(),m.getAccessFlags(),wrapper(m,original,kind)));
  }
  return with(cls,methods);
 }
 static DexFile rewritten(DexFile input){
  return new DexRewriter(new RewriterModule(){
   @Override public Rewriter<Instruction> getInstructionRewriter(Rewriters rs) {
    Rewriter<Instruction> parent=super.getInstructionRewriter(rs);
    return i->{
     Instruction x=parent.rewrite(i);
     if(x instanceof ReferenceInstruction){
      Reference r=((ReferenceInstruction)x).getReference();
      if(r instanceof StringReference){
       String original=((StringReference)r).getString();
       String replace=original.equals("v1.151")?"v1.155":original;
       // Replace outdated user-visible messages, preserving all other strings.
       replace=replace.replace("起動時にはWi-Fi接続が必要です。",
          "認証済みのローカルADBポートが有効ならWi-Fiなしでも再接続できます。");
       replace=replace.replace("Wi-Fi復帰後に再接続し、",
          "ローカルADBを優先し、利用できなければWi-Fi復帰後に再接続し、");
       replace=replace.replace("Wi-Fi未接続の場合は停止のみ行います。",
          "ローカルADBも利用できない場合は停止のみ行います。");
       if(!replace.equals(original)){
        StringReference sr=new ImmutableStringReference(replace);
        if(x instanceof Instruction21c)
         return new ImmutableInstruction21c(x.getOpcode(),((Instruction21c)x).getRegisterA(),sr);
        if(x instanceof Instruction31c)
         return new ImmutableInstruction31c(x.getOpcode(),((Instruction31c)x).getRegisterA(),sr);
        throw new IllegalStateException("unexpected string instruction");
       }
      }
     }
     return x;
    };
   }
  }).getDexFileRewriter().rewrite(input);
 }
 static Map<String,ClassDef> overlay(String filename)throws Exception {
  Map<String,ClassDef> out=new TreeMap<>();
  for(ClassDef c:load(filename).getClasses()){
   String t=c.getType();
   if(!(t.startsWith(BRIDGE.substring(0,BRIDGE.length()-1))||
        t.startsWith(ROUTE.substring(0,ROUTE.length()-1))||
        t.startsWith(OPEN.substring(0,OPEN.length()-1))||
        t.startsWith(RETRY.substring(0,RETRY.length()-1))))
    throw new SecurityException("unexpected overlay class: "+t);
   if(out.put(t,c)!=null)throw new IllegalStateException("duplicate overlay class");
  }
  if(!out.containsKey(BRIDGE)||!out.containsKey(ROUTE)||
     !out.containsKey(OPEN)||!out.containsKey(RETRY))
   throw new IllegalStateException("bridge and policy classes required");
  return out;
 }
 static List<ClassDef> expected(String base,String overlay)throws Exception {
  Map<String,ClassDef> add=overlay(overlay);
  List<ClassDef> all=new ArrayList<>();
  int[] count=new int[4];
  for(ClassDef c:load(base).getClasses()){
   if(add.containsKey(c.getType()))throw new IllegalStateException("cannot replace original class "+c.getType());
   all.add(patch(c,count));
  }
  if(!Arrays.equals(count,new int[]{1,1,1,1}))
   throw new IllegalStateException("baseline ABI changed: "+Arrays.toString(count));
  all.addAll(add.values());
  return all;
 }
 static byte[] canonical(ClassDef c)throws Exception{
  MemoryDataStore m=new MemoryDataStore();
  DexPool.writeTo(m,new ImmutableDexFile(API,Collections.singleton(c)));
  return m.getData();
 }
 static void merge(String base,String helper,String output)throws Exception{
  DexFile result=rewritten(new ImmutableDexFile(API,expected(base,helper)));
  DexFileFactory.writeDexFile(output,result);
  System.out.println("v1.155: high-risk v1.153 runtime refresh interceptor omitted; 4 original methods retained");
 }
 static void audit(String base,String helper,String output)throws Exception{
  DexFile plan=rewritten(new ImmutableDexFile(API,expected(base,helper)));
  Map<String,ClassDef> expected=new TreeMap<>(),actual=new TreeMap<>();
  for(ClassDef c:plan.getClasses())expected.put(c.getType(),c);
  for(ClassDef c:load(output).getClasses())actual.put(c.getType(),c);
  if(!expected.keySet().equals(actual.keySet()))
   throw new IllegalStateException("class set changed");
  for(Map.Entry<String,ClassDef> e:expected.entrySet()){
   if(!Arrays.equals(canonical(e.getValue()),canonical(actual.get(e.getKey()))))
    throw new IllegalStateException("DEX mismatch "+e.getKey());
  }
  System.out.println("PASS DEX audit: "+actual.size()+" classes; 3 WirelessAdb + 1 explicit open wrapper; legacy methods preserved");
 }
 public static void main(String[] a)throws Exception {
  if(a.length!=4)throw new IllegalArgumentException("merge|audit base.apk overlay.dex output.dex");
  if("merge".equals(a[0]))merge(a[1],a[2],a[3]);
  else if("audit".equals(a[0]))audit(a[1],a[2],a[3]);
  else throw new IllegalArgumentException(a[0]);
 }
}
