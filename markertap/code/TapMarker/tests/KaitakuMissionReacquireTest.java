package jp.tapmarker;
public final class KaitakuMissionReacquireTest {
 static void yes(boolean c,String msg){if(!c)throw new AssertionError(msg);}
 static boolean run(KaitakuMissionReacquire g,String state,boolean foreground,boolean mission,boolean home,
                    boolean ad,boolean unknown,long at,long now){
   return g.observe(state,foreground,mission,home,ad,unknown,at,now);
 }
 public static void main(String[] args){
  int checks=0;String open="kaitaku-default-mission-opening",verify="kaitaku-default-verify";
  for(String state:new String[]{open,verify}){
   KaitakuMissionReacquire g=new KaitakuMissionReacquire();
   yes(!run(g,state,true,true,false,false,false,1000,1005),"must wait for second frame");checks++;
   yes(run(g,state,true,true,false,false,false,1600,1610),"must recover from verified page");checks++;
  }
  for(int flag=0;flag<5;flag++){
   KaitakuMissionReacquire g=new KaitakuMissionReacquire();
   yes(!run(g,open,true,true,false,false,false,1000,1020),"first frame");checks++;
   boolean[] b={true,true,false,false,false};
   if(flag==0)b[0]=false;if(flag==1)b[1]=false;if(flag==2)b[2]=true;
   if(flag==3)b[3]=true;if(flag==4)b[4]=true;
   yes(!run(g,open,b[0],b[1],b[2],b[3],b[4],1600,1610),"unsafe evidence");checks++;
   yes(!run(g,open,true,true,false,false,false,2200,2210),"must restart counter");checks++;
   yes(run(g,open,true,true,false,false,false,2800,2810),"safe readmission");checks++;
  }
  KaitakuMissionReacquire g=new KaitakuMissionReacquire();
  yes(!run(g,open,true,true,false,false,false,1000,1010),"first");checks++;
  yes(!run(g,open,true,true,false,false,false,1000,1010),"duplicate");checks++;
  yes(run(g,open,true,true,false,false,false,1600,1610),"distinct");checks++;
  yes(!run(g,open,true,true,false,false,false,1700,4000),"stale");checks++;
  yes(!run(g,open,true,true,false,false,false,2500,2510),"restart");checks++;
  yes(!run(g,open,true,true,false,false,false,6000,6010),"too large gap");checks++;
  yes(!run(g,"kaitaku-default-ad",true,true,false,false,false,6600,6610),"wrong phase");checks++;
  yes(!run(g,open,true,true,false,false,false,7000,7010),"new phase");checks++;
  yes(run(g,open,true,true,false,false,false,7600,7610),"second");checks++;
  System.out.println("PASS: "+checks+" mission reacquire assertions");
 }
}
