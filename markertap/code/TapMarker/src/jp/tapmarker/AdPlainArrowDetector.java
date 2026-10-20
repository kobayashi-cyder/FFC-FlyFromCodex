package jp.tapmarker;
import java.util.*;

/** Observed white right-arrow glyph inside a leading black ad band, never a CTA. */
final class AdPlainArrowDetector {
 private static final String[] HEX={"0000380000007c00000078000000f0010000e0030000c0070000800f0000801f0000003e0000807ffeffff7ffffffffffffffffffeffff7f0000807f0000003e0000801f0000800f0000c0070000e0030000f0010000780000007c0000003800","00003c00000078000000f8000000e0010000c0070000c00f0000000f0000001e0000001e000000ffffffffffffffffffffffffff000080ff000000ff0000001e0000000f0000000f0000c00f0000e0010000e0010000f80000007c0000003c00","0000380000007800000078000000f8000000f0010000e0070000c0070000800f0000001f0000003fffffff7fffffff7fffffffffffffff7f0000003f0000001f0000800f0000c0070000e0070000f0010000f800000078000000780000003800"};
 private static final byte[][] PATTERNS=patterns();
 static byte[][] patterns(){byte[][] out=new byte[HEX.length][];for(int i=0;i<HEX.length;i++){out[i]=new byte[HEX[i].length()/2];for(int j=0;j<out[i].length;j++)out[i][j]=(byte)Integer.parseInt(HEX[i].substring(j*2,j*2+2),16);}return out;}
 static boolean white(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.min(r,Math.min(g,b))>205&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<25;}
 static List<float[]> find(int width,int height,boolean left,RegionSampler.Pixels pixels){
  int band=AdCornerViewport.top(width,height,pixels);if(band<Math.max(5,width*.02f))return Collections.emptyList();
  int x0=left?0:width*3/4,x1=left?width/4:width,w=x1-x0,h=Math.min(band,(int)(height*.24f));if(w<1||h<1)return Collections.emptyList();
  boolean[] mask=new boolean[w*h],seen=new boolean[w*h];int[] queue=new int[w*h];List<float[]> result=new ArrayList<>();
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)mask[y*w+x]=white(pixels.get(x+x0,y));
  int min=Math.max(4,Math.round(width*.009f)),max=Math.max(min,Math.round(width*.055f));
  for(int seed=0;seed<mask.length;seed++){
   if(!mask[seed]||seen[seed])continue;int read=0,end=1,l=w,r=0,t=h,b=0;seen[seed]=true;queue[0]=seed;
   while(read<end){int p=queue[read++],x=p%w,y=p/w;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=x+dx,ny=y+dy;if(nx<0||nx>=w||ny<0||ny>=h)continue;int next=ny*w+nx;if(mask[next]&&!seen[next]){seen[next]=true;queue[end++]=next;}}}
   int cw=r-l+1,ch=b-t+1;float aspect=cw/(float)ch,fill=end/(float)(cw*ch);if(ch<min||ch>max||aspect<1.20f||aspect>2.25f||fill<.14f||fill>.55f)continue;
   int margin=Math.max(2,ch/4),ring=0,dark=0;for(int y=Math.max(0,t-margin);y<=Math.min(h-1,b+margin);y++)for(int x=Math.max(0,l-margin);x<=Math.min(w-1,r+margin);x++)if(x<l||x>r||y<t||y>b){int c=pixels.get(x0+x,y);ring++;if(Math.max(c>>16&255,Math.max(c>>8&255,c&255))<=40)dark++;}
   if(ring<12||dark<ring*.90f)continue;
   byte[] actual=new byte[96];for(int gy=0;gy<24;gy++)for(int gx=0;gx<32;gx++){int count=0;for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++){int x=l+Math.min(cw-1,(int)((gx+(dx+.5f)/3)*cw/32)),y=t+Math.min(ch-1,(int)((gy+(dy+.5f)/3)*ch/24));if(mask[y*w+x])count++;}int p=gy*32+gx;if(count>=5)actual[p/8]|=1<<(p%8);}
   float score=0;for(byte[] reference:PATTERNS){int intersection=0,union=0;for(int i=0;i<actual.length;i++){intersection+=Integer.bitCount((actual[i]&reference[i])&255);union+=Integer.bitCount((actual[i]|reference[i])&255);}if(union>0)score=Math.max(score,intersection/(float)union);}
   if(score>=.58f)result.add(new float[]{x0+(l+r+1)/2f,(t+b+1)/2f});
  }
  return result;
 }
 private AdPlainArrowDetector(){}
}
