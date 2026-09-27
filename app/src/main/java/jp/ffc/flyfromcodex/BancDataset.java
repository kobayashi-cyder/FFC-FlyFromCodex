package jp.ffc.flyfromcodex;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.*;
import java.util.*;

final class BancDataset implements Closeable {
    static final String GRAPH_ASSET = "banc888_v2_graph.bin";
    static final String META_ASSET = "banc888_v2_meta.db";
    private static final byte[] MAGIC = new byte[]{'B','N','C','8','8','8','F','1'};

    static final class Meta {
        long id;
        String cellType="", superClass="", cellClass="", cellSubClass="", region="", side="", nt="", neuromere="", flow="";
        double neuriteUm=Double.NaN;
        long volumeNm3=0, inputCount=0, outputCount=0;
    }

    static final class Link {
        long partner;
        int count;
        Link(long partner, int count){ this.partner=partner; this.count=count; }
    }

    static final class HopResult {
        int exactCount;
        long[] sampleIds;
        HopResult(int exactCount, long[] sampleIds){ this.exactCount=exactCount; this.sampleIds=sampleIds; }
    }

    private final Context context;
    private RandomAccessFile graph;
    private SQLiteDatabase metaDb;
    private int n;
    private long e;
    private long[] ids;
    private long[] outOffsets, inOffsets, outTotals, inTotals;
    private long rootBase, outOffsetBase, outTargetBase, outCountBase, inOffsetBase, inSourceBase, inCountBase, outTotalBase, inTotalBase;

    BancDataset(Context context){ this.context=context.getApplicationContext(); }

    void open() throws IOException {
        File graphFile = copyAssetIfNeeded(GRAPH_ASSET);
        File metaFile = copyAssetIfNeeded(META_ASSET);
        graph = new RandomAccessFile(graphFile, "r");
        byte[] magic = new byte[8]; graph.readFully(magic);
        if(!Arrays.equals(magic, MAGIC)) throw new IOException("BANC graph magic mismatch");
        n = graph.readInt();
        e = graph.readLong();
        if(n <= 0 || e <= 0) throw new IOException("Invalid BANC graph counts");

        rootBase = 20L;
        outOffsetBase = rootBase + 8L*n;
        outTargetBase = outOffsetBase + 8L*(n+1L);
        outCountBase = outTargetBase + 4L*e;
        inOffsetBase = outCountBase + 4L*e;
        inSourceBase = inOffsetBase + 8L*(n+1L);
        inCountBase = inSourceBase + 4L*e;
        outTotalBase = inCountBase + 4L*e;
        inTotalBase = outTotalBase + 8L*n;

        ids = new long[n];
        outOffsets = new long[n+1];
        inOffsets = new long[n+1];
        outTotals = new long[n];
        inTotals = new long[n];
        graph.seek(rootBase); for(int i=0;i<n;i++) ids[i]=graph.readLong();
        graph.seek(outOffsetBase); for(int i=0;i<=n;i++) outOffsets[i]=graph.readLong();
        graph.seek(inOffsetBase); for(int i=0;i<=n;i++) inOffsets[i]=graph.readLong();
        graph.seek(outTotalBase); for(int i=0;i<n;i++) outTotals[i]=graph.readLong();
        graph.seek(inTotalBase); for(int i=0;i<n;i++) inTotals[i]=graph.readLong();

        metaDb = SQLiteDatabase.openDatabase(metaFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    private File copyAssetIfNeeded(String name) throws IOException {
        File dest = new File(context.getFilesDir(), name);
        AssetManager am = context.getAssets();
        if(dest.exists() && dest.length()>0) return dest;
        File tmp = new File(context.getFilesDir(), name+".tmp");
        try(InputStream in=am.open(name, AssetManager.ACCESS_STREAMING); OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp), 1024*1024)){
            byte[] buf=new byte[1024*1024]; int r; while((r=in.read(buf))!=-1) out.write(buf,0,r);
        }
        if(dest.exists()) dest.delete();
        if(!tmp.renameTo(dest)) throw new IOException("Cannot install asset "+name);
        return dest;
    }

    int nodeCount(){ return n; }
    long edgeCount(){ return e; }

    int indexOf(long id){ return Arrays.binarySearch(ids, id); }
    long outputTotal(long id){ int i=indexOf(id); return i<0?0:outTotals[i]; }
    long inputTotal(long id){ int i=indexOf(id); return i<0?0:inTotals[i]; }

    Meta meta(long id){
        if(metaDb==null) return null;
        try(Cursor c=metaDb.rawQuery("SELECT id,cell_type,super_class,cell_class,cell_sub_class,region,side,nt,neuromere,flow,neurite_um,volume_nm3,input_count,output_count FROM neurons WHERE id=?", new String[]{Long.toString(id)})){
            if(!c.moveToFirst()) return null;
            Meta m=new Meta();
            m.id=c.getLong(0); m.cellType=s(c,1); m.superClass=s(c,2); m.cellClass=s(c,3); m.cellSubClass=s(c,4);
            m.region=s(c,5); m.side=s(c,6); m.nt=s(c,7); m.neuromere=s(c,8); m.flow=s(c,9);
            if(!c.isNull(10)) m.neuriteUm=c.getDouble(10); if(!c.isNull(11))m.volumeNm3=c.getLong(11);
            if(!c.isNull(12))m.inputCount=c.getLong(12); if(!c.isNull(13))m.outputCount=c.getLong(13);
            return m;
        }
    }
    private String s(Cursor c,int i){ return c.isNull(i)?"":c.getString(i); }

    List<Meta> search(String q, int limit){
        ArrayList<Meta> out=new ArrayList<>();
        q=q==null?"":q.trim();
        if(q.matches("\\d{6,}")){
            try{ Meta m=meta(Long.parseLong(q)); if(m!=null) out.add(m); return out; }catch(Exception ignored){}
        }
        String like="%"+q+"%";
        String sql="SELECT id,cell_type,super_class,cell_class,cell_sub_class,region,side,nt,neuromere,flow,neurite_um,volume_nm3,input_count,output_count FROM neurons " +
                "WHERE cell_type LIKE ? OR super_class LIKE ? OR cell_class LIKE ? OR cell_sub_class LIKE ? OR region LIKE ? OR nt LIKE ? OR neuromere LIKE ? OR flow LIKE ? LIMIT ?";
        String[] a=new String[]{like,like,like,like,like,like,like,like,Integer.toString(limit)};
        try(Cursor c=metaDb.rawQuery(sql,a)){
            while(c.moveToNext()){
                Meta m=new Meta(); m.id=c.getLong(0); m.cellType=s(c,1);m.superClass=s(c,2);m.cellClass=s(c,3);m.cellSubClass=s(c,4);m.region=s(c,5);m.side=s(c,6);m.nt=s(c,7);m.neuromere=s(c,8);m.flow=s(c,9);
                if(!c.isNull(10))m.neuriteUm=c.getDouble(10);if(!c.isNull(11))m.volumeNm3=c.getLong(11);if(!c.isNull(12))m.inputCount=c.getLong(12);if(!c.isNull(13))m.outputCount=c.getLong(13); out.add(m);
            }
        }
        return out;
    }

    List<Link> outputs(long id, int minCount, int limit) throws IOException { return links(id,true,minCount,limit); }
    List<Link> inputs(long id, int minCount, int limit) throws IOException { return links(id,false,minCount,limit); }

    private synchronized List<Link> links(long id, boolean outgoing, int minCount, int limit) throws IOException {
        int idx=indexOf(id); if(idx<0) return Collections.emptyList();
        long[] offs=outgoing?outOffsets:inOffsets; long start=offs[idx], end=offs[idx+1];
        int len=(int)Math.min(Integer.MAX_VALUE,end-start);
        int[] partners=new int[len], counts=new int[len];
        long pbase=outgoing?outTargetBase:inSourceBase, cbase=outgoing?outCountBase:inCountBase;
        graph.seek(pbase+4L*start); for(int i=0;i<len;i++) partners[i]=graph.readInt();
        graph.seek(cbase+4L*start); for(int i=0;i<len;i++) counts[i]=graph.readInt();
        ArrayList<Link> result=new ArrayList<>();
        for(int i=0;i<len;i++) if(counts[i]>=minCount) result.add(new Link(ids[partners[i]],counts[i]));
        result.sort((a,b)->Integer.compare(b.count,a.count));
        if(limit>0 && result.size()>limit) return new ArrayList<>(result.subList(0,limit));
        return result;
    }

    HopResult exactHop(long sourceId, int k, int minCount, int sampleLimit) throws IOException {
        int source=indexOf(sourceId); if(source<0) return new HopResult(0,new long[0]);
        BitSet seen=new BitSet(n); seen.set(source);
        int[] current=new int[]{source};
        for(int depth=1;depth<=k;depth++){
            IntBag next=new IntBag(Math.min(n, Math.max(64,current.length*4)));
            for(int idx:current){
                long start=outOffsets[idx], end=outOffsets[idx+1]; int len=(int)(end-start);
                synchronized(this){
                    graph.seek(outTargetBase+4L*start); int[] ps=new int[len]; for(int i=0;i<len;i++)ps[i]=graph.readInt();
                    graph.seek(outCountBase+4L*start); for(int i=0;i<len;i++){ int c=graph.readInt(); if(c>=minCount && !seen.get(ps[i])){seen.set(ps[i]); next.add(ps[i]);} }
                }
            }
            current=next.toArray();
            if(current.length==0) break;
        }
        int take=Math.min(sampleLimit,current.length); long[] sample=new long[take]; for(int i=0;i<take;i++)sample[i]=ids[current[i]];
        return new HopResult(current.length,sample);
    }

    private static final class IntBag{
        int[] a; int n=0; IntBag(int cap){a=new int[Math.max(16,cap)];}
        void add(int x){ if(n==a.length)a=Arrays.copyOf(a,a.length*2);a[n++]=x; }
        int[] toArray(){ return Arrays.copyOf(a,n); }
    }

    @Override public void close(){ if(metaDb!=null)metaDb.close(); try{if(graph!=null)graph.close();}catch(Exception ignored){} }
}
