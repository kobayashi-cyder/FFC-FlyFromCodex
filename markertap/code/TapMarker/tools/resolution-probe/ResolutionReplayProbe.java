package jp.tapmarker;

import java.util.*;

/** A synthetic pixel-resize probe, not an Android low-resolution launch test. */
public final class ResolutionReplayProbe {
 public static void main(String[] args)throws Exception{
  Video6759ReplayTest.load();FarmDialogReplayTest.load();
  System.out.println("width,usagi_get_pass,usagi_cases,farm_close_start_pass,farm_cases");
  for(int width:VirtualResolutionPolicy.WIDTHS){
   int usagi=0,usagiCases=0,farm=0,farmCases=0;
   for(Map.Entry<String,VirtualPresetProfileTest.Image> row:Video6759ReplayTest.frames.entrySet()){
    if(row.getKey().equals("0"))continue;
    VirtualPresetProfileTest.Image im=row.getValue();int height=Math.round(width*im.height/(float)im.width);
    RegionSampler.Pixels pixels=FarmDialogReplayTest.resize(im,width,height,true);
    // Materialize real low-resolution pixels before recognition. Sampling the high-res source
    // at fractional coordinates would retain detail unavailable to a low-resolution display.
    int[] raster=new int[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++)raster[y*width+x]=pixels.get(x,y);
    RegionSampler.Pixels actual=(x,y)->raster[y*width+x];
    UsagiControlReplayTest.Run r=new UsagiControlReplayTest.Run("missions");r.observe(width,height,actual,1000,null);PhaseEngine.Watch watch=r.observe(width,height,actual,1350,null);
    usagiCases++;if(watch!=null&&watch.id.equals(UsagiPreset.PREFIX+"ゲットのみタップ")){float[] point=r.point(watch,width,height);if(point!=null&&point[0]>.68f*width&&point[0]<.94f*width&&point[1]>.55f*height&&point[1]<.63f*height)usagi++;}
   }
   for(String[] row:new String[][]{{"65.5","reward","close"},{"66.2","reward","close"},{"66.4","reward","close"},{"69.8","bonus","start"},{"70.2","bonus","start"},{"70.4","bonus","start"}}){
    VirtualPresetProfileTest.Image im=FarmDialogReplayTest.images.get(row[0]);int height=Math.round(width*im.height/(float)im.width);RegionSampler.Pixels pixels=FarmDialogReplayTest.resize(im,width,height,true);
    int[] raster=new int[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++)raster[y*width+x]=pixels.get(x,y);
    RegionSampler.Pixels actual=(x,y)->raster[y*width+x];
    FarmDialogReplayTest.Run r=new FarmDialogReplayTest.Run();Map<String,float[]> sample=r.sample(row[1],width,height,actual,1000);r.e.observe(sample,7000);r.e.poll();r.e.observe(sample,7200);PhaseEngine.Watch watch=r.e.poll();farmCases++;
    if(watch!=null&&watch.action==PhaseEngine.CENTER&&watch.regionId.equals(FarmPreset.P+row[2])){float[] point=r.layout.pointRelative(watch,sample,width,height);float[] box=(row[2].equals("close")?FarmDialogEvidence.CLOSE:FarmDialogEvidence.START)[1];if(point!=null&&point[0]>box[0]*width&&point[0]<box[2]*width&&point[1]>box[1]*height&&point[1]<box[3]*height)farm++;}
   }
   System.out.println(width+","+usagi+","+usagiCases+","+farm+","+farmCases);
  }
 }
}
