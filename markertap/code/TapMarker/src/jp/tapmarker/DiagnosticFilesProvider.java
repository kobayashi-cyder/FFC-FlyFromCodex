package jp.tapmarker;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.ParcelFileDescriptor;import android.provider.OpenableColumns;import java.io.*;

/** Read-only URI grants for user-initiated incident sharing. */
public final class DiagnosticFilesProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 private File file(Uri uri)throws FileNotFoundException{try{if(uri.getPathSegments().size()!=1)throw new FileNotFoundException("invalid diagnostic URI");return DiagnosticArchive.resolve(new File(getContext().getFilesDir(),"diagnostic-incidents"),uri.getLastPathSegment());}catch(IOException failure){throw new FileNotFoundException(failure.getMessage());}}
 public String getType(Uri uri){return "application/zip";}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){try{File f=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?f.getName():OpenableColumns.SIZE.equals(columns[i])?f.length():null;cursor.addRow(row);return cursor;}catch(FileNotFoundException missing){return null;}}
 public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("read only");}
 public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("read only");}
 public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("read only");}
}
