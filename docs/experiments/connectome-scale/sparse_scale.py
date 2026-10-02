import os,time,json,pathlib,numpy as np
from scipy.spatial import cKDTree
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import breadth_first_order
root=pathlib.Path(__file__).parent;results=[]
for n in [1024,4096,16384,65536,153962,1000000]:
 rng=np.random.default_rng(100+n);start=time.perf_counter();points=rng.random((n,2)).astype(np.float32)
 tree=cKDTree(points);_,neighbours=tree.query(points,k=25,workers=2)
 source=np.repeat(np.arange(n,dtype=np.int32),26);target=np.column_stack([neighbours[:,1:],rng.integers(0,n,(n,2))]).astype(np.int32).ravel()
 keep=source!=target;source=source[keep];target=target[keep];weights=rng.normal(0,.02,len(source)).astype(np.float32)
 circuit=csr_matrix((weights,(target,source)),shape=(n,n));circuit.sum_duplicates();circuit.eliminate_zeros();gen=time.perf_counter()-start
 drive=np.zeros(n,dtype=np.float32);drive[::16]=rng.normal(size=len(drive[::16]));h=np.zeros(n,dtype=np.float32)
 start=time.perf_counter()
 for t in range(32):h=np.tanh(.2*h+circuit@h+drive*np.sin(t/5))
 dyn=time.perf_counter()-start
 # Reachability uses outgoing topology, separately from weighted dynamics.
 adj=csr_matrix((np.ones(len(source),dtype=np.int8),(source,target)),shape=(n,n));adj.sum_duplicates()
 pairs=rng.integers(0,n,(16,2));reached=0
 for a,b in pairs:
  order=breadth_first_order(adj,int(a),directed=True,return_predecessors=False);reached+=bool(np.any(order==b))
 memory=circuit.data.nbytes+circuit.indices.nbytes+circuit.indptr.nbytes+points.nbytes+drive.nbytes+h.nbytes
 row={'nodes':n,'unique_directed_edges':int(circuit.nnz),'density':circuit.nnz/(n*(n-1)),'generation_seconds':gen,'32_activity_ticks_seconds':dyn,'core_circuit_positions_and_state_bytes':memory,'random_pairs':16,'reachable_pairs':reached,'mean_absolute_final_activity':float(np.mean(abs(h)))};results.append(row)
 (root/'sparse-scale-results.json').write_text(json.dumps({'scope':'Synthetic sparse spatial graph; 24 near + 2 random candidate edges per node; random signed weights; 32 tanh activity ticks. Not learned BANC weights, biological generation, accuracy, watts, growth continuity, pruning or APK. 16 reachability queries per scale only. Bytes are persistent core-array sum, not peak process RAM. Independent seeds per size.','results':results},indent=2));print(json.dumps(row),flush=True)
 del circuit,adj,points,tree,neighbours,source,target,weights,keep,drive,h
