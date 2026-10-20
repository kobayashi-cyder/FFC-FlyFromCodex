package jp.tapmarker;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Compressed, app-scoped edit history. Failed writes never consume an entry. */
public final class EditHistory {
 public interface Writer { boolean write(String value); }
 private static final int MAX_BYTES=6*1024*1024;
 private static final class Bucket {
  final ArrayDeque<byte[]> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
 }
 private final LinkedHashMap<String,Bucket> apps=new LinkedHashMap<>();
 private final int limit;
 public EditHistory(){this(12);}
 public EditHistory(int limit){if(limit<1)throw new IllegalArgumentException("履歴数が無効です");this.limit=limit;}
 public static byte[] pack(String value){try{ByteArrayOutputStream output=new ByteArrayOutputStream();try(GZIPOutputStream gzip=new GZIPOutputStream(output)){gzip.write(value.getBytes("UTF-8"));}return output.toByteArray();}catch(IOException failure){throw new IllegalStateException("設定の退避に失敗しました",failure);}}
 public static String unpack(byte[] bytes){try{ByteArrayOutputStream output=new ByteArrayOutputStream();try(GZIPInputStream input=new GZIPInputStream(new ByteArrayInputStream(bytes))){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(output.size()+n>16*1024*1024)throw new IOException("設定が大きすぎます");output.write(buffer,0,n);}}return new String(output.toByteArray(),"UTF-8");}catch(IOException failure){throw new IllegalStateException("退避した設定を読み込めません",failure);}}
 public void record(String app,String before,String after){if(before.equals(after))return;Bucket b=apps.get(app);if(b==null){b=new Bucket();apps.put(app,b);}b.undo.addLast(pack(before));b.redo.clear();while(b.undo.size()>limit)b.undo.removeFirst();trim();}
 public boolean canUndo(String app){Bucket b=apps.get(app);return b!=null&&!b.undo.isEmpty();}
 public boolean canRedo(String app){Bucket b=apps.get(app);return b!=null&&!b.redo.isEmpty();}
 public String restore(String app,String current,boolean redo,Writer writer){Bucket b=apps.get(app);if(b==null)return null;ArrayDeque<byte[]> from=redo?b.redo:b.undo,to=redo?b.undo:b.redo;if(from.isEmpty())return null;String value=unpack(from.peekLast());byte[] reverse=pack(current);if(!writer.write(value))throw new IllegalStateException("保存できなかったため設定を戻していません");from.removeLast();to.addLast(reverse);while(to.size()>limit)to.removeFirst();trim();return value;}
 private int bytes(){int n=0;for(Bucket b:apps.values()){for(byte[] v:b.undo)n+=v.length;for(byte[] v:b.redo)n+=v.length;}return n;}
 private void trim(){while(bytes()>MAX_BYTES){boolean removed=false;for(Bucket b:apps.values()){if(!b.undo.isEmpty()){b.undo.removeFirst();removed=true;break;}if(!b.redo.isEmpty()){b.redo.removeFirst();removed=true;break;}}if(!removed)break;}}
}
