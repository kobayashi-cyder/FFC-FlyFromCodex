#!/usr/bin/env python3
import os, sys, json, sqlite3, urllib.request, hashlib, struct
from pathlib import Path
import numpy as np
import pyarrow.feather as feather

BASE='https://storage.googleapis.com/lee-lab_brain-and-nerve-cord-fly-connectome/compiled_data/banc_888'
META_URL=f'{BASE}/banc_888_meta.feather'
EDGE_URL=f'{BASE}/banc_888_edgelist_simple_v2.feather'
NT_URL=f'{BASE}/banc_888_neurotransmitter_prediction_v2.csv'
OUTDIR=Path(sys.argv[1] if len(sys.argv)>1 else 'app/src/main/assets')
CACHE=Path(os.environ.get('BANC_CACHE','.banc_cache'))
OUTDIR.mkdir(parents=True,exist_ok=True); CACHE.mkdir(parents=True,exist_ok=True)

def dl(url,name):
    p=CACHE/name
    if p.exists() and p.stat().st_size>1024: return p
    print('download',url,flush=True)
    tmp=p.with_suffix(p.suffix+'.tmp')
    urllib.request.urlretrieve(url,tmp)
    tmp.replace(p)
    return p

def col(table,*names):
    ns=set(table.column_names)
    for n in names:
        if n in ns: return n
    return None

def strings(table,name,n):
    if not name: return ['']*n
    vals=table[name].to_pylist()
    return ['' if v is None else str(v) for v in vals]

def nums(table,name,n,kind=float):
    if not name: return [None]*n
    vals=table[name].to_pylist()
    out=[]
    for v in vals:
        if v is None: out.append(None)
        else:
            try: out.append(kind(v))
            except: out.append(None)
    return out

def be_write(f,arr,dtype):
    np.asarray(arr).astype(dtype,copy=False).tofile(f)

def sha256_file(path):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        for b in iter(lambda:f.read(8*1024*1024),b''): h.update(b)
    return h.hexdigest()

meta_path=dl(META_URL,'banc_888_meta.feather')
edge_path=dl(EDGE_URL,'banc_888_edgelist_simple_v2.feather')
nt_path=dl(NT_URL,'banc_888_neurotransmitter_prediction_v2.csv')
print('read feather',flush=True)
meta=feather.read_table(meta_path)
edges=feather.read_table(edge_path,columns=['pre','post','count','pre_count','post_count'])

# Per-neuron NT predictions are released separately from the meta feather.
import csv
nt_map={}
nt_score_map={}
with open(nt_path,'r',encoding='utf-8-sig',newline='') as nf:
    for row in csv.DictReader(nf):
        try: rid=int(row.get('root_id',''))
        except: continue
        nt_map[rid]=row.get('neurotransmitter_predicted','') or ''
        try: nt_score_map[rid]=float(row.get('neurotransmitter_score',''))
        except: nt_score_map[rid]=None

idcol=col(meta,'banc_888_id','root_id','root_888','pt_root_id')
if not idcol: raise RuntimeError('No BANC root-id column')
meta_ids=np.asarray(meta[idcol].to_numpy(zero_copy_only=False),dtype=np.int64)
pre=np.asarray(edges['pre'].to_numpy(zero_copy_only=False),dtype=np.int64)
post=np.asarray(edges['post'].to_numpy(zero_copy_only=False),dtype=np.int64)
count=np.asarray(edges['count'].to_numpy(zero_copy_only=False),dtype=np.int64)
if len(pre)==0 or int(count.min())>1: raise RuntimeError(f'Weak connections missing; minimum pair count={count.min() if len(count) else None}')

ids=np.unique(np.concatenate([meta_ids,pre,post]))
n=len(ids); E=len(pre)
print('nodes',n,'edges',E,'min count',int(count.min()),'max count',int(count.max()),flush=True)
pre_idx=np.searchsorted(ids,pre).astype(np.int32)
post_idx=np.searchsorted(ids,post).astype(np.int32)
count32=count.astype(np.int32)

print('sort outgoing',flush=True)
o=np.lexsort((post_idx,pre_idx))
opre=pre_idx[o]; otarget=post_idx[o]; ocount=count32[o]
out_deg=np.bincount(opre,minlength=n).astype(np.int64)
out_offsets=np.empty(n+1,dtype=np.int64);out_offsets[0]=0;np.cumsum(out_deg,out=out_offsets[1:])
out_total=np.bincount(opre,weights=ocount,minlength=n).astype(np.int64)

print('sort incoming',flush=True)
i=np.lexsort((pre_idx,post_idx))
ipost=post_idx[i]; isource=pre_idx[i]; icount=count32[i]
in_deg=np.bincount(ipost,minlength=n).astype(np.int64)
in_offsets=np.empty(n+1,dtype=np.int64);in_offsets[0]=0;np.cumsum(in_deg,out=in_offsets[1:])
in_total=np.bincount(ipost,weights=icount,minlength=n).astype(np.int64)

graph=OUTDIR/'banc888_v2_graph.bin'
print('write graph',graph,flush=True)
with open(graph,'wb') as f:
    f.write(b'BNC888F1'); f.write(struct.pack('>iq',n,E))
    be_write(f,ids,'>i8'); be_write(f,out_offsets,'>i8'); be_write(f,otarget,'>i4'); be_write(f,ocount,'>i4')
    be_write(f,in_offsets,'>i8'); be_write(f,isource,'>i4'); be_write(f,icount,'>i4'); be_write(f,out_total,'>i8'); be_write(f,in_total,'>i8')

print('write metadata db',flush=True)
db=OUTDIR/'banc888_v2_meta.db'
if db.exists(): db.unlink()
con=sqlite3.connect(db)
con.execute('PRAGMA journal_mode=OFF');con.execute('PRAGMA synchronous=OFF');con.execute('PRAGMA temp_store=MEMORY')
con.execute('''CREATE TABLE neurons(
 id INTEGER PRIMARY KEY, cell_type TEXT, super_class TEXT, cell_class TEXT, cell_sub_class TEXT,
 region TEXT, side TEXT, nt TEXT, neuromere TEXT, flow TEXT,
 neurite_um REAL, volume_nm3 INTEGER, input_count INTEGER, output_count INTEGER)''')
m=len(meta_ids)
cols={
'cell_type':col(meta,'cell_type'),'super_class':col(meta,'super_class'),'cell_class':col(meta,'cell_class'),
'cell_sub_class':col(meta,'cell_sub_class','subclass'),'region':col(meta,'region'),'side':col(meta,'side'),
'nt':None,'neuromere':col(meta,'neuromere'),'flow':col(meta,'flow'),
'neurite_um':col(meta,'l2_cable_length_um','cable_length_um','neurite_length_um'),
'volume_nm3':col(meta,'volume_nm3','volume'),'input_count':col(meta,'input_connections','post_count','input_count','n_inputs'),
'output_count':col(meta,'output_connections','pre_count','output_count','n_outputs')}
S={k:strings(meta,v,m) for k,v in cols.items() if k not in ('neurite_um','volume_nm3','input_count','output_count','nt')}
S['nt']=[nt_map.get(int(v),'') for v in meta_ids]
N={k:nums(meta,v,m,float if k=='neurite_um' else int) for k,v in cols.items() if k in ('neurite_um','volume_nm3','input_count','output_count')}
rows=[]
for x in range(m):
    rows.append((int(meta_ids[x]),S['cell_type'][x],S['super_class'][x],S['cell_class'][x],S['cell_sub_class'][x],S['region'][x],S['side'][x],S['nt'][x],S['neuromere'][x],S['flow'][x],N['neurite_um'][x],N['volume_nm3'][x],N['input_count'][x],N['output_count'][x]))
    if len(rows)>=10000:
        con.executemany('INSERT OR REPLACE INTO neurons VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)',rows); rows.clear()
if rows: con.executemany('INSERT OR REPLACE INTO neurons VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)',rows)
con.executemany('INSERT OR IGNORE INTO neurons(id,cell_type,super_class,cell_class,cell_sub_class,region,side,nt,neuromere,flow) VALUES (?,?,?,?,?,?,?,?,?,?)',((int(v),'','','','','','','','','') for v in ids))
con.commit(); con.execute('VACUUM'); con.close()

manifest={'dataset':'BANC','materialization':888,'synapse_version':'v2','source':'Lee Lab public GCS compiled_data/banc_888',
'meta_url':META_URL,'edge_url':EDGE_URL,'nt_url':NT_URL,'node_count':int(n),'metadata_rows':int(m),'nt_prediction_rows':int(len(nt_map)),'edge_pair_count':int(E),
'min_pair_synapse_count':int(count.min()),'max_pair_synapse_count':int(count.max()),'pair_count_threshold_applied':False,
'weak_connections_preserved':bool(count.min()==1),'graph_bytes':graph.stat().st_size,'meta_db_bytes':db.stat().st_size,
'graph_sha256':sha256_file(graph),'meta_db_sha256':sha256_file(db),'metadata_columns':{**cols,'nt':'neurotransmitter_prediction_v2.csv:neurotransmitter_predicted'}}
(OUTDIR/'banc888_manifest.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False),encoding='utf-8')
print(json.dumps(manifest,indent=2),flush=True)
