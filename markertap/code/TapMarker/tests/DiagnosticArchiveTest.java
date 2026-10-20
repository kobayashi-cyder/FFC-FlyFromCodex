package jp.tapmarker;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;

public final class DiagnosticArchiveTest {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  DiagnosticCapturePolicy a=new DiagnosticCapturePolicy(),b=new DiagnosticCapturePolicy();
  for(int i=0;i<20000;i++)check(!a.beginAction("タップ",i),"ordinary rapid taps do not copy or rearm diagnostic frames");
  check(a.posts==0&&b.posts==0,"another display remains untouched");a.controlPending=true;check(a.beginAction("タップ",21000),"a selected control requests diagnostic frames");check(!a.beginAction("タップ",21001),"a control request is consumed once");
  check(a.kind(20900,true).equals("before"),"before image predates the input");a.accepted(20900,"before");check(!a.kind(21000,false).equals("after"),"same-time image is not post-input evidence");
  check(a.kind(21100,false).equals("after"),"first fresh frame is post-input");a.accepted(21100,"after");check(!a.kind(21100,false).equals("after"),"repeated timestamp is not a second image");check(a.kind(21200,false).isEmpty(),"post images require temporal separation");
  check(a.kind(21400,false).equals("after"),"second fresh post image");a.accepted(21400,"after");check(a.posts==0&&b.posts==0,"post sequence completes per display");
  int[] size=DiagnosticCapturePolicy.size(1080,2424);check(size[0]==540&&size[1]==1212,"large frames are bounded while preserving aspect ratio");size=DiagnosticCapturePolicy.size(320,718);check(size[0]==320&&size[1]==718,"reported source size is preserved exactly");
  Path directory=Files.createTempDirectory("markertap-diagnostics-test");try{
   for(String name:new String[]{"../x.zip","incident-../x.zip","other.zip","incident-..zip"}){boolean rejected=false;try{DiagnosticArchive.resolve(directory.toFile(),name);}catch(IOException expected){rejected=true;}check(rejected,"no traversal or unrelated internal files: "+name);}
   List<DiagnosticArchive.Frame> frames=new ArrayList<>();for(int i=0;i<12;i++)frames.add(new DiagnosticArchive.Frame(i,new byte[]{(byte)0xff,(byte)0xd8,(byte)i},null,"{\"frame\":"+i+"}"));
   File target=DiagnosticArchive.resolve(directory.toFile(),"incident-test-0.zip");DiagnosticArchive.write(target,"frozen report","{\"app\":\"coin\",\"display\":4}",frames);
   try(ZipFile zip=new ZipFile(target)){check(zip.getEntry("report.txt")!=null&&zip.getEntry("incident.json")!=null,"report and frame identity travel together");int images=0;for(Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();)if(it.nextElement().getName().endsWith(".jpg"))images++;check(images==8,"incident frame count is bounded");check(zip.getEntry("frame-01.json")!=null,"each frame retains capture metadata");}
   for(int i=1;i<8;i++)DiagnosticArchive.write(DiagnosticArchive.resolve(directory.toFile(),"incident-test-"+i+".zip"),"report","{}",frames);
   check(directory.toFile().listFiles((d,n)->n.endsWith(".zip")).length==DiagnosticArchive.MAX_ARCHIVES,"bounded disk history");check(directory.toFile().listFiles((d,n)->n.endsWith(".tmp")).length==0,"no partial archive exposed");
  }finally{try(java.util.stream.Stream<Path> walk=Files.walk(directory)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}}
  System.out.println("DiagnosticArchiveTest: "+checks+" checks; rapid-tap isolation, real archive IO, identity metadata, path containment, frame and disk limits; Android JPEG and sharing require device validation");
 }
}
