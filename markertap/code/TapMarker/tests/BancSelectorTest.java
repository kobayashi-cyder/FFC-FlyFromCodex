package jp.tapmarker;
import java.util.*;import java.nio.file.*;

public class BancSelectorTest {
 static int checks;
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  BancSelector circuit=new BancSelector();int vectors=0;
  for(String line:Files.readAllLines(Paths.get("tests/banc-selector-reference.tsv"))){String[] row=line.split("\\t");if(!row[0].equals("OUTPUT"))continue;int route=Integer.parseInt(row[1]);double drive=Double.parseDouble(row[2]),expected=Double.parseDouble(row[3]);BancSelector.Selection s=circuit.select(Collections.singletonList(new BancSelector.Candidate("r"+route,route,drive,1)));check(s!=null&&Math.abs(s.signedOutput-expected)<1e-12,"native output equals original BANC888 JS route "+route+" drive "+drive);vectors++;}
  check(vectors==20,"all 4 routes and 5 drives compared to original selector");
  BancSelector.Candidate a=new BancSelector.Candidate("a",2,.8,.8),b=new BancSelector.Candidate("b",2,.82,.97);
  check(circuit.select(Arrays.asList(a,b)).candidate.id.equals("b"),"same task binding ranks by actual propagated activation");
  double[] untouched=circuit.gains();check(!circuit.reinforce("a",1),"unselected action cannot claim reward");check(Arrays.equals(untouched,circuit.gains()),"wrong action leaves gains intact");
  check(circuit.reinforce("b",.2),"selected action receives verified outcome credit");check(!circuit.reinforce("b",.2),"same episode cannot be rewarded twice");
  BancSelector reference=new BancSelector();reference.select(Collections.singletonList(new BancSelector.Candidate("credit",2,.9,1)));reference.reinforce("credit",.2);
  for(String line:Files.readAllLines(Paths.get("tests/banc-selector-reference.tsv"))){String[] row=line.split("\\t");if(row[0].equals("GAIN"))check(Math.abs(reference.gains()[Integer.parseInt(row[1])]-Double.parseDouble(row[2]))<1e-12,"native outcome credit equals original JS edge "+row[1]);}
  check(circuit.select(Arrays.asList(new BancSelector.Candidate("bad",-1,1,1),new BancSelector.Candidate("nan",1,Double.NaN,1)))==null,"invalid route and nonfinite drive rejected");
  circuit.disconnectAll();check(circuit.select(Arrays.asList(a,b))==null,"all BANC edges cut means no action signal");
  circuit.restore(new double[]{1});check(circuit.gains().length==16,"invalid-size gain restoration ignored");
  BancSelector negative=new BancSelector();negative.select(Collections.singletonList(a));negative.reinforce("a",-.5);boolean reduced=false;for(double gain:negative.gains())reduced|=gain<1;check(reduced,"failed result reduces eligible edge gains");
  System.out.println("BancSelectorTest: "+checks+" checks passed; original-JS parity and graph ablation, not biological superiority");
 }
}
