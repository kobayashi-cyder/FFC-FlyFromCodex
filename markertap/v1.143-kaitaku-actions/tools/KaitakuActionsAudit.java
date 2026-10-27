import java.io.File;import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.iface.*;
public final class KaitakuActionsAudit {
 public static void main(String[] args)throws Exception{
  for(ClassDef c:DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(35)).getClasses())KaitakuAudit.types.put(c.getType(),c);
  for(String[] h:new String[][]{{"KaitakuLayout","collect","KaitakuActionEvidence","sample"},{"KaitakuLayout","sampleRelative","KaitakuUiEvidence","sample"},{"KaitakuUiEvidence","sample","KaitakuNamedControls","apply"},{"KaitakuStartup","loaded","KaitakuPreset","isLegacy"},{"KaitakuStartup","canTap","KaitakuActionEvidence","present"}})
   {Method method=null;for(Method m:KaitakuAudit.type(h[0]).getMethods())if(m.getName().equals(h[1])&&(!h[1].equals("sampleRelative")||m.getParameterTypes().size()==6))method=m;
    KaitakuAudit.check(method!=null&&KaitakuAudit.calls(method,h[2],h[3])==1,"shipping action path "+java.util.Arrays.toString(h));}
  System.out.println("KaitakuActionsAudit: recorded controls, bounded named probe, mini-game tap gate and profile migration packaged");
 }
}
