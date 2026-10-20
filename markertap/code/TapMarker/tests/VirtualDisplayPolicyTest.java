package jp.tapmarker;
public class VirtualDisplayPolicyTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  for(int sdk=24;sdk<=36;sdk++)for(boolean shell:new boolean[]{false,true}){
   int flags=VirtualDisplayPolicy.flags(sdk,shell);
   check((flags&256)!=0,"all creation paths destroy contents instead of moving games onto display zero");
   check((flags&8)!=0&&(flags&16)==0,"own content, never automatic screen mirroring");
   check(((flags&1024)!=0)==(shell&&sdk>=33),"trusted flag only requested by authorized execution backend");
   check(((flags&16384)!=0)==(shell&&sdk>=34),"independent focus uses supported OS version");
   check((flags&65536)==0||(flags&16384)!=0,"no-steal flag requires independent focus");
  }
  System.out.println("VirtualDisplayPolicyTest: "+checks+" checks passed; OS flag acceptance not tested");
 }
}
