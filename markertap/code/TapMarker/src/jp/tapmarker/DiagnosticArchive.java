package jp.tapmarker;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.*;

/** Bounded, app-local incident bytes; no live frame or mutable workspace escapes. */
final class DiagnosticArchive {
 static final int MAX_FRAMES=8,MAX_ARCHIVES=5;
 static final class Frame {
  final long at;final byte[] jpeg,marked;final String metadata;
  Frame(long at,byte[] jpeg,byte[] marked,String metadata){this.at=at;this.jpeg=jpeg;this.marked=marked;this.metadata=metadata;}
 }
 static File resolve(File directory,String name)throws IOException{
  if(name==null||!name.matches("incident-[A-Za-z0-9_.-]+\\.zip")||name.contains(".."))throw new FileNotFoundException("invalid diagnostic name");
  File file=new File(directory,name);if(!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()))throw new FileNotFoundException("invalid diagnostic location");return file;
 }
 static void write(File file,String report,String manifest,List<Frame> frames)throws IOException{
  File directory=file.getParentFile();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("diagnostic directory unavailable");
  File temporary=new File(directory,file.getName()+".tmp");
  try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(temporary))){
   entry(zip,"report.txt",report.getBytes(StandardCharsets.UTF_8));entry(zip,"incident.json",manifest.getBytes(StandardCharsets.UTF_8));
   int index=0;for(Frame frame:frames.subList(Math.max(0,frames.size()-MAX_FRAMES),frames.size())){String stem=String.format(Locale.ROOT,"frame-%02d",++index);entry(zip,stem+".jpg",frame.jpeg);if(frame.marked!=null)entry(zip,stem+"-marked.jpg",frame.marked);entry(zip,stem+".json",frame.metadata.getBytes(StandardCharsets.UTF_8));}
  }catch(IOException failure){temporary.delete();throw failure;}
  if(!temporary.renameTo(file)){temporary.delete();throw new IOException("diagnostic commit failed");}
  File[] files=directory.listFiles((dir,name)->name.endsWith(".zip"));if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(int i=MAX_ARCHIVES;i<files.length;i++)files[i].delete();}
 }
 private static void entry(ZipOutputStream zip,String name,byte[] bytes)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
 private DiagnosticArchive(){}
}
