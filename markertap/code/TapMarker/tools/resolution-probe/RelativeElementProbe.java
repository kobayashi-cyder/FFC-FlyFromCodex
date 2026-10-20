package jp.tapmarker;

import java.util.*;
import java.io.*;

/** Offline ablation of the existing learner's normalized visual regions. No Android execution. */
public final class RelativeElementProbe {
 static final class Scene {String name,label;ObservationLearner.Frame frame;Scene(String n,String l,ObservationLearner.Frame f){name=n;label=l;frame=f;}}
 static final class Model {String name;ObservationLearner.Signature signature;Model(String n,ObservationLearner.Signature s){name=n;signature=s;}}
 static ObservationLearner.Frame frame(VirtualPresetProfileTest.Image image){return new ObservationLearner.Frame(image.width,image.height,image.rgb);}
 static ObservationLearner.Frame resized(ObservationLearner.Frame image,int width){
  int height=Math.max(1,Math.round(width*image.height/(float)image.width));int[] pixels=new int[width*height];
  for(int y=0;y<height;y++)for(int x=0;x<width;x++){
   float fx=(x+.5f)*image.width/width-.5f,fy=(y+.5f)*image.height/height-.5f;
   int x0=Math.max(0,Math.min(image.width-1,(int)Math.floor(fx))),y0=Math.max(0,Math.min(image.height-1,(int)Math.floor(fy))),x1=Math.min(image.width-1,x0+1),y1=Math.min(image.height-1,y0+1);float dx=Math.max(0,fx-x0),dy=Math.max(0,fy-y0);int c=0xff000000;
   for(int shift:new int[]{16,8,0}){float a=((image.pixels[y0*image.width+x0]>>shift&255)*(1-dx)+(image.pixels[y0*image.width+x1]>>shift&255)*dx),b=((image.pixels[y1*image.width+x0]>>shift&255)*(1-dx)+(image.pixels[y1*image.width+x1]>>shift&255)*dx);c|=Math.round(a*(1-dy)+b*dy)<<shift;}pixels[y*width+x]=c;
  }return new ObservationLearner.Frame(width,height,pixels);
 }
 static ObservationLearner.Frame compact(ObservationLearner.Frame f){return ObservationLearner.Frame.capture(f.width,f.height,(x,y)->f.pixels[y*f.width+x]);}
 static boolean matches(ObservationLearner.Signature signature,int count,ObservationLearner.Frame frame){
  if(signature.bounds.size()<count)return false;
  for(int i=0;i<count;i++)if(FlyCircuit.distance(signature.refs.get(i),frame.sample(signature.bounds.get(i)))>.006f)return false;
  return true;
 }
 static ObservationLearner.Frame mask(ObservationLearner.Frame source,float[] box){int[] p=source.pixels.clone();int[] b=RegionMath.bounds(box,source.width,source.height);for(int y=b[1];y<b[3];y++)for(int x=b[0];x<b[2];x++)p[y*source.width+x]=0xff777777;return new ObservationLearner.Frame(source.width,source.height,p);}
 public static void main(String[] args)throws Exception{
  Video6759ReplayTest.load();FarmDialogReplayTest.load();
  ArrayList<Scene> training=new ArrayList<>(),test=new ArrayList<>();
  training.add(new Scene("get-train","usagi-get",frame(Video6759ReplayTest.frames.get("0"))));
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:Video6759ReplayTest.frames.entrySet())if(!row.getKey().equals("0"))test.add(new Scene("get-"+row.getKey(),"usagi-get",frame(row.getValue())));
  int play=0;for(Map.Entry<String,VirtualPresetProfileTest.Image> row:UsagiControlReplayTest.missions.entrySet()){Scene s=new Scene("play-"+row.getKey(),"usagi-play",frame(row.getValue()));if(play++<2)training.add(s);else test.add(s);}
  Set<String> farmPositive=new HashSet<>(Arrays.asList("65.5","66.2","66.4","69.8","70.2","70.4"));
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:FarmDialogReplayTest.images.entrySet()){
   String key=row.getKey(),label=key.equals("65.5")||key.equals("66.2")||key.equals("66.4")?"farm-close":key.equals("69.8")||key.equals("70.2")||key.equals("70.4")?"farm-start":"unknown";Scene s=new Scene("farm-"+key,label,frame(row.getValue()));
   if(key.equals("66.2")||key.equals("70.2"))training.add(s);else if(!farmPositive.contains(key)&&training.size()<9)training.add(s);else test.add(s);
  }
  int unknown=0;for(Map.Entry<String,VirtualPresetProfileTest.Image> row:VirtualRecoveryReplayTest.images.entrySet()){Scene s=new Scene("recovery-"+row.getKey(),"unknown",frame(row.getValue()));if(unknown++%3==0)training.add(s);else test.add(s);}
  PrintWriter metadata=new PrintWriter("build/relative-element-metadata.txt","UTF-8"),details=new PrintWriter("build/relative-element-details.csv","UTF-8");details.println("width,regions,scene,expected,accepted");
  ArrayList<Model> models=new ArrayList<>();for(String label:new String[]{"usagi-get","usagi-play","farm-close","farm-start"}){
   Scene source=null,peer=null;for(Scene s:training)if(s.label.equals(label)){if(source==null)source=s;else peer=s;}
   ArrayList<ObservationLearner.Frame> others=new ArrayList<>();for(Scene s:training)if(!s.label.equals(label))others.add(compact(s.frame));
   float[] target=label.equals("usagi-get")?new float[]{.8f,.59f}:label.equals("usagi-play")?new float[]{.8f,.58f}:label.equals("farm-close")?new float[]{.5f,.662f}:new float[]{.51f,.8405f};
   ObservationLearner.Signature signature=ObservationLearner.signature(compact(source.frame),peer==null?null:compact(peer.frame),target,others,6);models.add(new Model(label,signature));
   metadata.println("MODEL "+label+" regions="+signature.bounds.size());for(float[] b:signature.bounds)metadata.println(Arrays.toString(b));
  }
  metadata.println("TRAIN "+training.size()+" TEST "+test.size());for(Scene s:training)metadata.println("TRAIN "+s.name+" "+s.label);for(Scene s:test)metadata.println("TEST "+s.name+" "+s.label);
  metadata.flush();
  System.out.println("width,regions,positive_cases,correct_unique,wrong_action,abstain,negative_comparisons,false_accepts,masked_target_cases,masked_target_false_accepts");
  for(int width:new int[]{32,48,64,96,128,160,200,240,280,320,360,400,480,540,640,720})for(int count=1;count<=6;count++){
   int positive=0,correct=0,wrong=0,abstain=0,negatives=0,falseAccepts=0,maskedCases=0,maskedFalse=0;
   for(Scene scene:test){ObservationLearner.Frame image=resized(scene.frame,width);ArrayList<String> accepted=new ArrayList<>();
    for(Model model:models){boolean match=matches(model.signature,count,image);if(match)accepted.add(model.name);if(!scene.label.equals(model.name)){negatives++;if(match)falseAccepts++;}}
    details.println(width+","+count+","+scene.name+","+scene.label+","+(accepted.isEmpty()?"ABSTAIN":String.join("|",accepted)));
    if(!scene.label.equals("unknown")){positive++;if(accepted.size()==1&&accepted.get(0).equals(scene.label))correct++;else if(accepted.size()==1)wrong++;else abstain++;
     for(Model model:models)if(model.name.equals(scene.label)){maskedCases++;if(matches(model.signature,count,mask(image,model.signature.bounds.get(0))))maskedFalse++;}
    }else if(accepted.size()==1)wrong++;else abstain++;
   }
   System.out.println(width+","+count+","+positive+","+correct+","+wrong+","+abstain+","+negatives+","+falseAccepts+","+maskedCases+","+maskedFalse);
  }
  metadata.close();details.close();
 }
}
