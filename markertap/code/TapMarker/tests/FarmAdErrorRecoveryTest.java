package jp.tapmarker;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public final class FarmAdErrorRecoveryTest {
 static int checks=0;
 static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
 static float[] detect(int second)throws Exception {
  BufferedImage frame=ImageIO.read(new File("tests/fixtures/video6743/farm-"+second+".png"));
  RegionSampler.Pixels pixels=(x,y)->frame.getRGB(x,y);
  Map<String,float[]> out=new HashMap<>();
  FarmAdErrorEvidence.sample(out,frame.getWidth(),frame.getHeight(),pixels);
  float[] p=FarmAdErrorEvidence.point(out,frame.getWidth(),frame.getHeight());
  if(p!=null){check(p[0]>frame.getWidth()*.45f&&p[0]<frame.getWidth()*.55f,"OK x centre");check(p[1]>frame.getHeight()*.60f&&p[1]<frame.getHeight()*.70f,"OK y centre");}
  return p;
 }
 public static void main(String[] args)throws Exception {
  for(int t:new int[]{6,16,26,42})check(detect(t)==null,"false positive at "+t+"s");
  for(int t:new int[]{52,64,80,95,105})check(detect(t)!=null,"miss at "+t+"s");
  FarmAdErrorRecovery one=new FarmAdErrorRecovery(),two=new FarmAdErrorRecovery();
  one.observe(true,1000,1100);
  check(one.visible(),"first observation must hold tap");check(!one.mayTap(1100),"not yet stable");
  one.observe(true,1000,1200);check(!one.mayTap(1200),"stale duplicate must not confirm");
  one.observe(true,1160,1200);check(one.confirmed()&&one.mayTap(1200),"two fresh frames confirm");
  check(!two.visible(),"each display maintains its own state");
  one.dispatched(1200);check(one.attempts()==1,"first attempt");
  check(!one.mayTap(1201),"cooldown");
  one.observe(true,1500,1550);check(!one.mayTap(1550),"duplicate dialog no repeated tap");
  one.observe(true,4000,4030);check(one.mayTap(4030),"bounded retry");one.dispatched(4030);
  one.observe(true,6900,6910);check(one.mayTap(6910),"third retry");one.dispatched(6910);
  one.observe(true,9800,9830);check(one.exhausted(9830)&&!one.mayTap(9830),"stop after three attempts");
  one.observe(false,9940,9970);check(one.visible(),"one missing frame does not reset");
  one.observe(false,10100,10150);check(!one.visible()&&one.consumeRecovered(),"two fresh missing frames confirm close");
  check(!one.consumeRecovered()&&one.attempts()==0,"reset after success");
  one.observe(true,10200,10300);one.observe(true,10400,10430);check(one.mayTap(10430),"new episode allowed");
  System.out.println("PASS video6743 detector+gate: "+checks+" checks");
 }
}
