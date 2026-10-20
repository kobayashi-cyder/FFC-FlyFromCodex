package jp.tapmarker;
import java.util.*;import java.util.zip.*;import java.io.*;

/** Match the supplied Close lettering, only on a light background in an ad corner. */
final class AdCloseWordDetector {
 static final byte[] TEMPLATE=template();
 static byte[] template(){try{byte[] packed=unhex("78daa594db95c3200c44298446284485b81117a242688442b47af13224664ff852c8359666c684302ca4f0be2264a20cf1908f3765c44cf721cf780a21153ae429476b1d1b7f215e75a2b1bc2ac72b65aafc55880a267d90c6522a723e24709e5b03e02ef530901286cd8a373d81e47f7bd4de67bbbeb9e1a1d56dfe7e88aaf38997a6419c61de16f6fe37bc88c2ce44de648f64357d02c28ee773b0dcbc79c7aa7f492662e3b543f99d10fdc978fb29abbf51a5d4df31bbbe7a8a9ab0c90f53fd377aa99b58c85bf5216acf4d844053098f2cc1b2b38bbfae74cabb1578c8c36d56dc66e32b5fa750f484b72f77e4cdf9ab5558bf1c94fce3208df17d200f596d445a99f22c7c458d3714ab333d9e3ff0cbb306d220bcd816575ec74e5093246b9977e47ba8da97e9d7c5c89b9462a04a9980373afff4ebebbc9b3c00f02bbd4fb2ae398cfc12f76ecddb17bf8876593acdff110fd3ebdff37fd91c32ef093ffaf51f3ef6f377f9f72ed29467e7777a7ade60e5bba7cddf9e9f39cf3ff1565b8bedfa9ff33ff1925b1b5c2eeab2cbffd2cfe7fcf32ddc7895527995f2c1370d1bffc8ffcc7bfef5da52def3af0104a897bee73fedf236e67cb9affe00372bcfa3");ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new InflaterInputStream(new ByteArrayInputStream(packed))){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}byte[] result=out.toByteArray();if(result.length==0||result.length%768!=0)throw new IllegalStateException("Close template");return result;}catch(IOException ex){throw new IllegalStateException(ex);}}
 static byte[] unhex(String text){byte[] data=new byte[text.length()/2];for(int i=0;i<data.length;i++)data[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return data;}
 static boolean ink(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.max(r,Math.max(g,b))<=170&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=40;}
 static final class Glyph {int l,t,r,b;Glyph(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;}}
 static List<float[]> find(int width,int height,boolean left,RegionSampler.Pixels pixels){
  float[] band=AdCornerViewport.band(left?new float[]{0,0,.25f,.14f}:new float[]{.75f,0,1,.14f},width,height,pixels);int[] box=RegionMath.bounds(band,width,height);box[1]=Math.max(box[1],AdCornerViewport.top(width,height,pixels));int w=box[2]-box[0],h=box[3]-box[1];if(h<=0)return new ArrayList<>();
  boolean[] mask=new boolean[w*h],seen=new boolean[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)mask[y*w+x]=ink(pixels.get(box[0]+x,box[1]+y));
  List<Glyph> glyphs=new ArrayList<>();int[] queue=new int[w*h];int min=Math.max(4,Math.round(width*.010f)),max=Math.max(min,Math.round(width*.06f));
  for(int seed=0;seed<mask.length;seed++){
   if(!mask[seed]||seen[seed])continue;int used=0,end=1,l=w,r=0,t=h,b=0;queue[0]=seed;seen[seed]=true;
   while(used<end){int n=queue[used++],x=n%w,y=n/w;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=x+dx,ny=y+dy;if(nx<0||ny<0||nx>=w||ny>=h)continue;int next=ny*w+nx;if(mask[next]&&!seen[next]){seen[next]=true;queue[end++]=next;}}}
   int gh=b-t+1,gw=r-l+1;if(gh>=min&&gh<=max&&gw<=max&&end>=gh)glyphs.add(new Glyph(l+box[0],t+box[1],r+box[0]+1,b+box[1]+1));
  }
  glyphs.sort(Comparator.comparingInt(g->g.l));List<float[]> result=new ArrayList<>();
  for(Glyph first:glyphs){int l=first.l,t=first.t,r=first.r,b=first.b,count=1,fh=first.b-first.t;
   for(Glyph next:glyphs){if(next.l<r||next==first||Math.abs(next.b-first.b)>fh*.35f||next.l-r>fh*1.1f)continue;
    r=next.r;t=Math.min(t,next.t);b=Math.max(b,next.b);count++;if(count>6)break;float aspect=(r-l)/(float)(b-t);
    if(count>=4&&aspect>=2.3f&&aspect<=3.8f&&matches(width,height,new int[]{l,t,r,b},pixels)){
     float[] point={(l+r)/2f,(t+b)/2f};boolean duplicate=false;for(float[] old:result)if(Math.hypot(old[0]-point[0],old[1]-point[1])<width*.025f)duplicate=true;if(!duplicate)result.add(point);
    }
   }
  }
  return result;
 }
 static boolean matches(int width,int height,int[] b,RegionSampler.Pixels pixels){
  int w=b[2]-b[0],h=b[3]-b[1];float[] actual=new float[768];double ink=0;
  for(int y=0;y<16;y++)for(int x=0;x<48;x++){int sum=0;for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++){int px=b[0]+Math.min(w-1,(int)((x+(dx+.5f)/3)*w/48)),py=b[1]+Math.min(h-1,(int)((y+(dy+.5f)/3)*h/16));if(ink(pixels.get(px,py)))sum++;}actual[y*48+x]=sum/9f;ink+=sum/9.0;}
  double score=0;for(int offset=0;offset<TEMPLATE.length;offset+=768){double intersection=0,union=0;for(int i=0;i<768;i++){double ref=(TEMPLATE[offset+i]&255)/255.0;intersection+=Math.min(actual[i],ref);union+=Math.max(actual[i],ref);}if(union>0)score=Math.max(score,intersection/union);}
  if(score<.58||ink/768<.15||ink/768>.65)return false;
  int light=0,total=0,margin=Math.max(2,h/4);for(int y=Math.max(0,b[1]-margin);y<Math.min(height,b[3]+margin);y++)for(int x=Math.max(0,b[0]-margin);x<Math.min(width,b[2]+margin);x++){if(x>=b[0]&&x<b[2]&&y>=b[1]&&y<b[3])continue;total++;if(AdCloseDetector.lightBackground(pixels.get(x,y)))light++;}
  return total>0&&light>=total*.85;
 }
 private AdCloseWordDetector(){}
}
