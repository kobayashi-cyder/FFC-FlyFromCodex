package jp.ffc.flyfromcodex;

import android.content.Context;
import java.io.*;
import java.util.*;

/**
 * Sparse learned-weight overlay. BANC base connectivity is never modified.
 * Only trained edges are persisted.
 */
final class PlasticityStore {
    static final class EdgeKey {
        final long pre, post;
        EdgeKey(long pre,long post){this.pre=pre;this.post=post;}
        @Override public boolean equals(Object o){
            if(this==o)return true;
            if(!(o instanceof EdgeKey))return false;
            EdgeKey k=(EdgeKey)o; return pre==k.pre && post==k.post;
        }
        @Override public int hashCode(){
            long h=pre*0x9E3779B97F4A7C15L ^ Long.rotateLeft(post,23);
            return (int)(h^(h>>>32));
        }
    }

    private final File file;
    private final HashMap<EdgeKey,Float> delta=new HashMap<>();
    private static final int MAGIC=0x46464350; // FFCP
    private static final int VERSION=1;
    private static final int MAX_EDGES=250_000;

    PlasticityStore(Context context){
        file=new File(context.getFilesDir(),"ffc_plasticity_v1.bin");
        load();
    }

    synchronized float delta(long pre,long post){
        Float v=delta.get(new EdgeKey(pre,post));
        return v==null?0f:v;
    }

    synchronized float multiplier(long pre,long post){
        float d=delta(pre,post);
        return Math.max(0.10f,Math.min(3.00f,1.0f+d));
    }

    synchronized void add(long pre,long post,float amount){
        EdgeKey k=new EdgeKey(pre,post);
        float v=delta.getOrDefault(k,0f)+amount;
        v=Math.max(-0.90f,Math.min(2.00f,v));
        if(Math.abs(v)<0.0005f) delta.remove(k); else delta.put(k,v);
        if(delta.size()>MAX_EDGES) prune();
    }

    synchronized int size(){return delta.size();}

    synchronized double meanAbsDelta(){
        if(delta.isEmpty())return 0;
        double s=0;for(float v:delta.values())s+=Math.abs(v);
        return s/delta.size();
    }

    synchronized void clear(){
        delta.clear();
        if(file.exists())file.delete();
    }

    synchronized void save(){
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))){
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(delta.size());
            for(Map.Entry<EdgeKey,Float> e:delta.entrySet()){
                out.writeLong(e.getKey().pre);out.writeLong(e.getKey().post);out.writeFloat(e.getValue());
            }
        }catch(IOException ignored){}
    }

    private synchronized void load(){
        if(!file.exists())return;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
            if(in.readInt()!=MAGIC)return;
            int ver=in.readInt(); if(ver!=VERSION)return;
            int n=in.readInt(); if(n<0||n>MAX_EDGES)return;
            for(int i=0;i<n;i++){
                long pre=in.readLong(),post=in.readLong();float v=in.readFloat();
                if(Float.isFinite(v)&&Math.abs(v)>=0.0005f)delta.put(new EdgeKey(pre,post),Math.max(-0.9f,Math.min(2f,v)));
            }
        }catch(Exception ignored){delta.clear();}
    }

    private void prune(){
        ArrayList<Map.Entry<EdgeKey,Float>> all=new ArrayList<>(delta.entrySet());
        all.sort(Comparator.comparingDouble(e->Math.abs(e.getValue())));
        int remove=Math.max(1,all.size()-MAX_EDGES+25_000);
        for(int i=0;i<remove;i++)delta.remove(all.get(i).getKey());
    }
}
