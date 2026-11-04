package jp.tapmarker;
import java.util.*;

public final class CifaDenseSelectorTest {
 static int checks=0;
 static void yes(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args){
  CifaDenseSelector s=new CifaDenseSelector();
  yes(s.gains().length==16,"readout size");
  double[] original=s.gains();
  for(int route=0;route<4;route++)for(int i=1;i<=250;i++){
   double excitation=.15+(i%60)*.02, confidence=.11+(i%85)*.01;
   CifaDenseSelector.Candidate c=new CifaDenseSelector.Candidate("r"+route+"-"+i,route,excitation,confidence);
   CifaDenseSelector.Selection a=s.propagate(c,false),b=s.propagate(c,false);
   yes(a!=null&&Double.doubleToLongBits(a.activation)==Double.doubleToLongBits(b.activation),"deterministic score");
   yes(a.activation>0&&a.activation<1,"bounded score");
  }
  CifaDenseSelector.Candidate a=new CifaDenseSelector.Candidate("one",2,.8,.8);
  yes(s.propagate(new CifaDenseSelector.Candidate("bad",-1,.8,.8),false)==null,"illegal route");
  yes(s.propagate(new CifaDenseSelector.Candidate("bad",2,Double.NaN,.8),false)==null,"nonfinite");
  yes(s.propagate(new CifaDenseSelector.Candidate("bad",2,0,.8),false)==null,"no evidence");
  CifaDenseSelector.Selection result=s.select(Collections.singletonList(a));
  yes(result!=null&&result.credit.length==16,"dense credit");
  yes(s.reinforce(result.candidate.id,.2),"post-proof reinforcement");
  double[] changed=s.gains();boolean any=false;for(int i=0;i<16;i++)if(changed[i]!=original[i])any=true;
  yes(any,"verified action changes dense readout gain");
  yes(!s.reinforce("one",.2),"cannot repeat credit without new verified selection");
  s.select(Collections.singletonList(a));s.discardCredit();
  yes(!s.reinforce("one",.2),"cancel clears credit");
  double[] saved=s.gains();saved[0]=Double.NaN;saved[1]=999;saved[2]=.83;
  s.restore(saved);
  yes(Double.isFinite(s.gains()[0])&&s.gains()[1]<=1.25&&s.gains()[2]==.83,"invalid gains rejected, valid restored");
  s.disconnectAll();yes(s.select(Collections.singletonList(a))==null,"no enabled hidden units fails closed");
  System.out.println("CifaDenseSelectorTest: "+checks+" checks passed; full dense readout and bounded learning");
 }
}
