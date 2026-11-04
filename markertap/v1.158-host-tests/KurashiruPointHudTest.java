package jp.tapmarker;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class KurashiruPointHudTest {
  private static int checks=0;
  private static void equals(long want,long actual,String label){
    checks++;if(want!=actual)throw new AssertionError(label+" expected="+want+" actual="+actual);
  }
  private static BufferedImage badge(String text,int width,int height,boolean white){
    BufferedImage screen=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
    Graphics2D g=screen.createGraphics();
    g.setColor(white?Color.WHITE:new Color(80,100,140));
    g.fillRect(0,0,width,height);
    g.setColor(Color.BLACK);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                       RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(new Font("DejaVu Sans",Font.BOLD,Math.max(11,(int)(35.0*width/1080))));
    g.drawString(text,(int)(123.0*width/1080),(int)(254.0*height/2424));
    g.dispose();
    return screen;
  }
  private static long read(BufferedImage image){
    return KurashiruPointHud.read(image::getRGB,image.getWidth(),image.getHeight());
  }
  public static void main(String[]args){
    equals(10175,read(badge("10,175pt",1080,2424,true)),"real HUD-format balance");
    equals(10175,read(badge("10,175pt",540,1212,true)),"half-resolution badge");
    equals(9,read(badge("9pt",1080,2424,true)),"single digit");
    equals(123456,read(badge("123,456pt",1080,2424,true)),"long balance");
    equals(-1,read(badge("10,175",1080,2424,true)),"numeric text missing pt must fail");
    equals(-1,read(badge("10,175pt",1080,2424,false)),"nonwhite ad background must fail");
    equals(-1,read(badge("mission",1080,2424,true)),"irrelevant text must fail");
    System.out.println("KurashiruPointHudTest: "+checks+" checks passed");
  }
}
