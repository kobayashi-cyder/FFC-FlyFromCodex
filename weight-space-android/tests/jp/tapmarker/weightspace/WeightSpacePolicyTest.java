package jp.tapmarker.weightspace;
public final class WeightSpacePolicyTest {
  private static boolean[] inputs(int... positions) {boolean[] result=new boolean[8];for(int p:positions)result[p]=true;return result;}
  private static final Object[][] CASES = {
    {"RESUME",inputs(0,5)}, {"RESUME",inputs(0,5,4)}, {"WAIT",inputs(0,1)},
    {"CLAIM",inputs(0,1,2)}, {"CLAIM",inputs(0,1,2,4)}, {"WAIT",inputs(0,1,2,3)},
    {"RECOVER",inputs(3)}, {"RECOVER",inputs(7)}, {"RECOVER",inputs(3,4)},
    {"WAIT",inputs(6)}, {"WAIT",inputs(0,1,2,6)}, {"WAIT",inputs(3,6)},
    {"WAIT",inputs(4)}, {"WAIT",inputs(0,1,4)}, {"WAIT",inputs(0)}, {"WAIT",inputs(0,1,2,4,6)}
  };
  public static void main(String[] args) {
    WeightSpacePolicy model=new WeightSpacePolicy(false);
    int before=0,after=0;
    for(Object[] c:CASES)if(c[0].equals(model.evaluate((boolean[])c[1]).action))before++;
    model.setPatched(true);
    for(Object[] c:CASES)if(c[0].equals(model.evaluate((boolean[])c[1]).action))after++;
    check(before==15&&after==16,"16-case regression: before="+before+", after="+after);
    check(model.editedWeight()==0,"patch was not applied");
    for(int mask=0;mask<256;mask++){
      boolean[] f=new boolean[8];for(int i=0;i<8;i++)f[i]=(mask&(1<<i))!=0;
      String a=model.evaluate(f).action;
      if(f[6])check(a.equals("WAIT"),"unknown UI must wait: "+mask);
      if(a.equals("CLAIM"))check(f[0]&&f[1]&&f[2]&&!f[3]&&!f[6]&&!f[7],"unsafe claim: "+mask);
      if(a.equals("RESUME"))check(f[0]&&f[5]&&!f[3]&&!f[6]&&!f[7],"unsafe resume: "+mask);
      if(a.equals("RECOVER"))check((f[3]||f[7])&&!f[6],"unsafe recover: "+mask);
    }
    model.setPatched(false);
    check(model.editedWeight()==-12,"rollback not reversible");
    System.out.println("PASS: baseline 15/16; patched 16/16; all 256 binary inputs satisfy invariants; reversible edit");
  }
  private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
}
