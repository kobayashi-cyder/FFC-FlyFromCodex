package jp.tapmarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Offline, dependency-free, fail-closed reader for Kurashiru's upper-left
 * "10,175pt" HUD. Reads only pixel data from the registered game frame.
 *
 * The reference crop is the coin badge supplied for v1.158; do not treat
 * a game-independent normalized HUD location as proof that every title or
 * aspect ratio uses the same layout. Unknown/unreadable displays return -1.
 */
final class KurashiruPointHud {
  interface Pixels {int get(int x,int y);}
  private static final int GRID_W=12,GRID_H=16;
  // The four glyphs seen in the user-provided HUD, followed by generic bold
  // digit alternatives. These represent digit silhouettes, not a screenshot.
  private static final String[] DIGITS={
    "1:07f3ff7ffffffffe3f03f03f03f03f03f03f03f03f03f01e",
    "0:0f03fc7fe79e70ff0ff0ff0ff07f0ff0ff0f79e7fe3fe1f8",
    "7:fffffffff00f01e01e03c07c0780f00f01f01e03e03e0380",
    "5:7fe7fe7fe700700f00ffcffefff00f00f00f01ffffffe7f8",
    "1:7f0ff0ff0df00f00f00f00f00f00f00f00f00f8fffffffff",
    "0:1f03fc7fe71ef0ff0ff0ff0ff0ff0ff0ff0f71e7fe3fc1f0",
    "7:fffffffff01e01e01e03c03c0780780f00f01e01e01e03c0",
    "5:7fe7fe7fe7007007f87fc7ff01f00f00f00f81fffeffe7f0",
    "2:7f0ffeffe81e01f01e01e03c07c0f03e07c0f80fffffffff",
    "3:7f87fe7fe01e00e01e01e1fc1fe1fe01f00f81ffffffe7f0",
    "4:03c07c0fc0fc1fc1fc3bc33c73ce3cfffffffff03e03c03c",
    "6:07e1fe3fe780700f78ffeffef8ff0ff0f70f78e7fe3fc0f8",
    "8:1f87fe7fe71e70e71e7fc3fc7fef9ef0ff0ff0effe7fe1f8",
    "9:1f07f87fcf1ef1ef0ef1ef1ffff7fe1fe00e01e7fc7f87e0"
  };
  private static final class Shape {
    int x,y,w,h,area;
    Shape(int x,int y,int w,int h,int area){
      this.x=x;this.y=y;this.w=w;this.h=h;this.area=area;
    }
    int end(){return y+h;}
  }
  static long read(Pixels pixels,int width,int height){
    if(pixels==null||width<300||height<560)return -1;
    // Narrow text ROI excludes the round coin logo and the adjacent mission
    // button. It is deliberately conservative rather than screen-wide OCR.
    final int x0=(int)(width*.105f),x1=(int)(width*.392f);
    final int y0=(int)(height*.089f),y1=(int)(height*.111f);
    if(x1-x0<60||y1-y0<12)return -1;
    return readBand(pixels,width,height,x0,y0,x1,y1);
  }
  private static long readBand(Pixels pixels,int width,int height,
                               int x0,int y0,int x1,int y1){
    final int w=x1-x0,h=y1-y0;
    byte[] black=new byte[w*h];
    int white=0;
    try {
      for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        int color=pixels.get(x0+x,y0+y);
        int red=(color>>16)&255,green=(color>>8)&255,blue=color&255;
        if(red>223&&green>223&&blue>223)white++;
        if(Math.max(red,Math.max(green,blue))<125)black[y*w+x]=1;
      }
    }catch(RuntimeException badFrame){return -1;}
    // A genuine white badge must be visible; an advertisement or transition
    // cannot be decoded simply because it happens to have dark numerals.
    if(white<w*h*.43)return -1;
    ArrayList<Shape> shapes=components(black,w,h);
    if(shapes.size()<3||shapes.size()>26)return -1;
    Collections.sort(shapes,Comparator.comparingInt(a->a.x));
    Shape first=null;
    for(Shape c:shapes){
      if(c.y==0||c.end()==h||c.area>h*h*3)continue;
      if(c.h>=h*.35&&c.w>=2&&c.x>=2){first=c;break;}
    }
    if(first==null)return -1;
    int suffix=-1;
    for(int i=1;i+1<shapes.size();i++){
      Shape p=shapes.get(i),t=shapes.get(i+1);
      int dh=first.h,db=first.end();
      if(p.y>=first.y+Math.max(2,dh/5)
        &&p.end()>=db+Math.max(1,dh/7)
        &&p.h>=dh*.65&&p.h<=dh*1.30
        &&p.w>=dh*.40&&p.w<=dh*1.2
        &&t.x>=p.x+p.w&&t.x-(p.x+p.w)<=dh
        &&t.y<=first.y+dh*.30
        &&Math.abs(t.end()-db)<=Math.max(3,dh*.22)
        &&t.h>=dh*.60&&t.h<=dh*1.2){
          suffix=i;break;
      }
    }
    if(suffix<1)return -1;
    long result=0;
    StringBuilder sequence=new StringBuilder();
    for(int i=0;i<suffix;i++){
      Shape c=shapes.get(i);
      if(c.h<=first.h*.56&&c.y>=first.y+first.h*.45
          &&c.w<=first.h*.70){
        sequence.append(',');
        continue;
      }
      if(Math.abs(c.y-first.y)>first.h*.26
          ||c.h<first.h*.70||c.h>first.h*1.3)return -1;
      int digit=identify(c,black,w);
      if(digit<0||sequence.length()>=14)return -1;
      sequence.append((char)('0'+digit));
      if(result>(Long.MAX_VALUE-digit)/10)return -1;
      result=result*10+digit;
    }
    String text=sequence.toString();
    if(!validGrouping(text))return -1;
    return result;
  }
  private static boolean validGrouping(String text){
    if(text.isEmpty()||text.charAt(0)==','||text.endsWith(","))return false;
    int comma=text.indexOf(',');
    if(comma<0)return text.length()<=12;
    if(comma<1||comma>3)return false;
    for(int i=comma;i<text.length();i+=4){
      if(text.charAt(i)!=','||i+4>text.length())return false;
      for(int d=i+1;d<i+4;d++)if(text.charAt(d)<'0'||text.charAt(d)>'9')return false;
    }
    return true;
  }
  private static int identify(Shape c,byte[] b,int rowWidth){
    int[] observed=new int[GRID_H];
    for(int gy=0;gy<GRID_H;gy++){
      int y=c.y+Math.min(c.h-1,(int)((gy+.5)*c.h/GRID_H));
      for(int gx=0;gx<GRID_W;gx++){
        int x=c.x+Math.min(c.w-1,(int)((gx+.5)*c.w/GRID_W));
        if(b[y*rowWidth+x]!=0)observed[gy]|=1<<(GRID_W-1-gx);
      }
    }
    int best=999,second=999,winner=-1;
    int[] distances=new int[10];
    for(int i=0;i<10;i++)distances[i]=999;
    for(String specimen:DIGITS){
      int digit=specimen.charAt(0)-'0',diff=0;
      for(int row=0;row<GRID_H;row++){
        int mask=Integer.parseInt(specimen.substring(2+row*3,5+row*3),16);
        diff+=Integer.bitCount(observed[row]^mask);
      }
      distances[digit]=Math.min(diff,distances[digit]);
    }
    for(int i=0;i<10;i++){
      int distance=distances[i];
      if(distance<best){second=best;best=distance;winner=i;}
      else if(distance<second)second=distance;
    }
    // Require both absolute accuracy and separation from the next digit.
    return best<=38&&second-best>=8?winner:-1;
  }
  private static ArrayList<Shape> components(byte[] pixels,int w,int h){
    int len=w*h;
    byte[] visited=new byte[len];
    int[] stack=new int[len];
    ArrayList<Shape> result=new ArrayList<>();
    for(int offset=0;offset<len;offset++){
      if(pixels[offset]==0||visited[offset]!=0)continue;
      int top=0;stack[top++]=offset;visited[offset]=1;
      int minX=w,minY=h,maxX=-1,maxY=-1,area=0;
      while(top>0){
        int position=stack[--top],x=position%w,y=position/w;
        minX=Math.min(minX,x);maxX=Math.max(maxX,x);
        minY=Math.min(minY,y);maxY=Math.max(maxY,y);area++;
        for(int yy=Math.max(0,y-1);yy<=Math.min(h-1,y+1);yy++)
          for(int xx=Math.max(0,x-1);xx<=Math.min(w-1,x+1);xx++){
            int at=yy*w+xx;
            if(pixels[at]!=0&&visited[at]==0){visited[at]=1;stack[top++]=at;}
          }
      }
      if(area>=4&&maxX-minX>=1&&maxY-minY>=4
          &&minX>0&&minY>0&&maxX<w-1&&maxY<h-1)
        result.add(new Shape(minX,minY,maxX-minX+1,maxY-minY+1,area));
    }
    return result;
  }
  private KurashiruPointHud(){}
}
