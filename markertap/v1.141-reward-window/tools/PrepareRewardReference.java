package jp.tapmarker;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;

/** Derive only two label masks; no balance, banner, or overlay is retained. */
public final class PrepareRewardReference {
 public static void main(String[] args)throws Exception{
  BufferedImage source=ImageIO.read(new File(args[0]));Path module=Paths.get(args[1]);
  java.util.List<String> encoded=new ArrayList<>();
  for(int factor:new int[]{1,2})for(Object mode:new Object[]{RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,RenderingHints.VALUE_INTERPOLATION_BILINEAR}){
   BufferedImage image=new BufferedImage(source.getWidth()*factor,source.getHeight()*factor,BufferedImage.TYPE_INT_RGB);
   Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,mode);g.drawImage(source,0,0,image.getWidth(),image.getHeight(),null);g.dispose();
   float[] sample=FarmDialogEvidence.sample(image.getWidth(),image.getHeight(),FarmPreset.P+"close",new float[]{1,1,0,0},image::getRGB);
   byte[] counts=new byte[512];for(int i=0;i<counts.length;i++){
    if(sample[i*3]!=sample[i*3+1]||sample[i*3]!=sample[i*3+2])throw new AssertionError("non-mask reference");
    counts[i]=(byte)Math.round(sample[i*3]*9);
   }
   StringBuilder digits=new StringBuilder();for(byte count:counts)digits.append((char)('0'+count));String value=digits.toString();if(!encoded.contains(value))encoded.add(value);
  }
  StringBuilder java=new StringBuilder("package jp.tapmarker;\nimport java.util.*;\n/** Two independent ink masks from the supplied 2026-10-27 farm reward. */\nfinal class FarmRewardReference {\n private static final String[] DATA={\n");
  for(String value:encoded)java.append("  \"").append(value).append("\",\n");
  java.append(" };\n static void addTo(List<float[]> refs){for(String text:DATA){if(text.length()!=512)throw new IllegalStateException(\"farm reward mask\");float[] ref=new float[1536];for(int i=0;i<text.length();i++){int count=text.charAt(i)-'0';if(count<0||count>9)throw new IllegalStateException(\"farm reward mask value\");float v=count/9f;ref[i*3]=v;ref[i*3+1]=v;ref[i*3+2]=v;}refs.add(ref);}}\n private FarmRewardReference(){}\n}\n");
  Files.write(module.resolve("overlay/src/jp/tapmarker/FarmRewardReference.java"),java.toString().getBytes("UTF-8"));
  Path fixture=module.resolve("tests/farm-reward-20261027-strips.bin.gz");
  try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(fixture)))){
   out.writeInt(source.getWidth());out.writeInt(source.getHeight());out.writeInt(2);
   for(float[] box:new float[][]{{.18f,.49f,.82f,.55f},{.36f,.63f,.64f,.69f}}){
    int[] b=RegionMath.bounds(box,source.getWidth(),source.getHeight());for(int v:b)out.writeInt(v);
    for(int y=b[1];y<b[3];y++)for(int x=b[0];x<b[2];x++)out.writeInt(source.getRGB(x,y));
   }
  }
  System.out.println("Prepared "+encoded.size()+" paired reward masks and two cropped test strips");
 }
}
