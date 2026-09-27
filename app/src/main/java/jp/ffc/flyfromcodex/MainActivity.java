package jp.ffc.flyfromcodex;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout content;
    private BancDataset banc;
    private volatile boolean bancReady=false;
    private String bancStatus="BANC v888 実データを初期化中…";
    private long selectedId=0L;
    private final BioState bio=new BioState();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        buildShell();
        showOverview();
        initBanc();
    }

    private void initBanc(){
        new Thread(() -> {
            try{
                BancDataset d=new BancDataset(this);
                d.open(); banc=d; bancReady=true;
                List<BancDataset.Meta> first=d.search("",1);
                if(!first.isEmpty()) selectedId=first.get(0).id;
                bancStatus="BANC v888 / synapses_v2 loaded: neurons="+d.nodeCount()+" / directed pairs="+d.edgeCount()+" / pair threshold=0 (count=1保持)";
            }catch(Exception e){
                bancStatus="BANC実データ初期化失敗: "+e.getMessage();
            }
            runOnUiThread(this::showOverview);
        }).start();
    }

    private void buildShell(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(12,12,12,12);
        root.addView(text("FFC · BANC v888 Native Mapper",24,true));
        HorizontalScrollView hs=new HorizontalScrollView(this); LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(button("概要",this::showOverview)); nav.addView(button("検索",this::showSearch)); nav.addView(button("細胞",this::showCell));
        nav.addView(button("Hops",this::showHops)); nav.addView(button("Graph",this::showGraph)); nav.addView(button("相互作用",this::showInteractions));
        nav.addView(button("データ",this::showData)); nav.addView(button("学習",this::showLearn)); hs.addView(nav); root.addView(hs);
        ScrollView sc=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(4,10,4,80); sc.addView(content);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }

    private TextView text(String s,int sp,boolean bold){ TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.rgb(25,25,30));v.setPadding(10,10,10,10);if(bold)v.setTypeface(null,1);return v; }
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());return b;}
    private void clear(){content.removeAllViews();}
    private void notReady(){content.addView(text(bancStatus,17,true));}

    private void showOverview(){
        clear(); content.addView(text("BANC v888 実マッピング",22,true)); content.addView(text(bancStatus,16,true));
        content.addView(text("構造レイヤ",19,true));
        content.addView(text("root ID → cell type / super class / region / NT → direct synaptic pair → Exact 1–6 hop → FFC相互作用モデル",16,false));
        content.addView(text("弱い接続",19,true));
        content.addView(text("pair単位のcountしきい値は0です。count=1の接続もデータ層から削除しません。画面・hop解析で最小synapse数を1,2,5…へ変更できます。",15,false));
        content.addView(text("実測とモデルの分離",19,true));
        content.addView(text("[BANC] root ID・接続数・annotation・NT等はv888実データ。\n[文献] K⁺緩衝・再取り込み・BBB・代謝型シグナルは生理学的機構。\n[MODEL] 小域濃度・興奮性・plasticityの時系列係数はFFCの計算モデルです。",15,false));
    }

    private void showSearch(){
        clear(); if(!bancReady){notReady();return;}
        content.addView(text("BANC v888ニューロン検索",21,true));
        EditText q=new EditText(this);q.setHint("root ID / cell type / super class / class / region / NT / neuromere");content.addView(q);
        LinearLayout out=new LinearLayout(this);out.setOrientation(LinearLayout.VERTICAL);content.addView(button("検索",()->{
            out.removeAllViews(); out.addView(text("検索中…",14,false)); String query=q.getText().toString();
            new Thread(()->{List<BancDataset.Meta> rows=banc.search(query,100);runOnUiThread(()->{
                out.removeAllViews(); if(rows.isEmpty()){out.addView(text("該当なし",15,false));return;}
                for(BancDataset.Meta m:rows){String label=(m.cellType.isEmpty()?"(untyped)":m.cellType)+" · "+m.id+" | "+m.superClass+" | "+m.region+" | "+m.nt;
                    out.addView(button(label,()->{selectedId=m.id;showCell();}));}
            });}).start();
        }));content.addView(out);
    }

    private void showCell(){
        clear(); if(!bancReady){notReady();return;} if(selectedId==0){content.addView(text("検索からニューロンを選択してください。",17,true));return;}
        content.addView(text("Cell "+selectedId,22,true));
        new Thread(()->{
            try{
                BancDataset.Meta m=banc.meta(selectedId); List<BancDataset.Link> outs=banc.outputs(selectedId,1,0); List<BancDataset.Link> ins=banc.inputs(selectedId,1,0);
                runOnUiThread(()->renderCell(m,outs,ins));
            }catch(Exception e){runOnUiThread(()->content.addView(text("読込エラー: "+e.getMessage(),15,true)));}
        }).start();
    }

    private void renderCell(BancDataset.Meta m,List<BancDataset.Link> outs,List<BancDataset.Link> ins){
        if(m==null){content.addView(text("metadataなし",16,true));return;}
        String neurite=Double.isNaN(m.neuriteUm)?"n/a":String.format(Locale.ROOT,"%.1f µm",m.neuriteUm);
        content.addView(text("cell type: "+m.cellType+"\nsuper class: "+m.superClass+"\ncell class: "+m.cellClass+"\nsubclass: "+m.cellSubClass+"\nregion: "+m.region+"\nside: "+m.side+"\nneuromere: "+m.neuromere+"\nflow: "+m.flow+"\nNT: "+m.nt+"\nneurite: "+neurite+"\nBANC total input synapses: "+banc.inputTotal(selectedId)+"\nBANC total output synapses: "+banc.outputTotal(selectedId),15,false));
        renderLinks("Outputs",outs,true); renderLinks("Inputs",ins,false);
    }

    private void renderLinks(String title,List<BancDataset.Link> ls,boolean outgoing){
        int weak=0;for(BancDataset.Link l:ls)if(l.count==1)weak++;
        content.addView(text(title+" · pair数="+ls.size()+" · count=1="+weak,19,true));
        int strong=Math.min(100,ls.size());
        for(int i=0;i<strong;i++) addLinkLine(ls.get(i),outgoing,"strong→weak");
        if(ls.size()>strong){content.addView(text("…中間を省略…",13,false));int start=Math.max(strong,ls.size()-40);for(int i=start;i<ls.size();i++)addLinkLine(ls.get(i),outgoing,"weak tail");}
    }

    private void addLinkLine(BancDataset.Link l,boolean outgoing,String tag){
        BancDataset.Meta p=banc.meta(l.partner); String t=p==null?"":p.cellType; String nt=p==null?"":p.nt;
        content.addView(text((outgoing?"→ ":"← ")+l.partner+" | syn="+l.count+" | "+t+" | NT="+nt+" | "+tag,13,false));
    }

    private void showHops(){
        clear();if(!bancReady){notReady();return;}content.addView(text("Exact-hop（実BANC v888）",21,true));
        EditText src=new EditText(this);src.setInputType(2);src.setText(Long.toString(selectedId));content.addView(src);
        Spinner hop=new Spinner(this);hop.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1","2","3","4","5","6"}));hop.setSelection(1);content.addView(hop);
        EditText min=new EditText(this);min.setInputType(2);min.setText("1");min.setHint("最小pair synapse count");content.addView(min);
        LinearLayout out=new LinearLayout(this);out.setOrientation(LinearLayout.VERTICAL);
        content.addView(button("計算",()->{
            long s=parseLong(src.getText().toString(),selectedId);int k=Integer.parseInt((String)hop.getSelectedItem());int threshold=Math.max(1,(int)parseLong(min.getText().toString(),1));
            out.removeAllViews();out.addView(text("計算中… count="+threshold+"以上。count=1なら弱い接続も含みます。",14,true));
            new Thread(()->{try{BancDataset.HopResult r=banc.exactHop(s,k,threshold,200);runOnUiThread(()->{
                out.removeAllViews();out.addView(text("Exact "+k+"-hop = "+r.exactCount+" neurons / min count="+threshold,16,true));
                for(long id:r.sampleIds){BancDataset.Meta m=banc.meta(id);out.addView(text(id+" | "+(m==null?"":m.cellType)+" | "+(m==null?"":m.superClass)+" | "+(m==null?"":m.nt),13,false));}
                if(r.exactCount>r.sampleIds.length)out.addView(text("表示は先頭"+r.sampleIds.length+"件。計算自体は全Exact-hop集合です。",13,false));
            });}catch(Exception e){runOnUiThread(()->{out.removeAllViews();out.addView(text("計算エラー: "+e.getMessage(),14,true));});}}).start();
        }));content.addView(out);
    }

    private void showGraph(){
        clear();if(!bancReady){notReady();return;}if(selectedId==0){content.addView(text("ニューロンを選択してください。",16,true));return;}
        content.addView(text("選択ニューロンの実1-hopマップ",21,true));
        try{List<BancDataset.Link> all=banc.outputs(selectedId,1,0);List<BancDataset.Link> sample=strongWeakSample(all,40,20);content.addView(text("全output pair="+all.size()+"。描画は強い40＋弱い20を抽出。元データは削除していません。",14,false));GraphView g=new GraphView(selectedId,sample);content.addView(g,new LinearLayout.LayoutParams(-1,1100));}
        catch(Exception e){content.addView(text("graph error: "+e.getMessage(),14,true));}
    }

    private List<BancDataset.Link> strongWeakSample(List<BancDataset.Link> a,int strong,int weak){ArrayList<BancDataset.Link> r=new ArrayList<>();for(int i=0;i<Math.min(strong,a.size());i++)r.add(a.get(i));for(int i=Math.max(strong,a.size()-weak);i<a.size();i++)r.add(a.get(i));return r;}

    private void showData(){
        clear();content.addView(text("同梱データ",21,true));content.addView(text(bancStatus,16,true));
        content.addView(text("dataset: BANC\nmaterialization: v888\nsynapse detector/edgelist: v2 (paper version)\nsource: Lee Lab public GCS compiled_data/banc_888\npair count threshold: 0\nweak pair: count=1 retained\nautapses: source edgelist buildに従う\nmetadata: root ID / hierarchy / region / side / NT / morphology metrics等",15,false));
        content.addView(text("注意: v2の『size ≥ 5』は元synapse検出側のサイズ条件で、neuron pairをcount≥5に切る条件ではありません。このAPKではedge pairのcountフィルタを生成時にかけません。",14,true));
    }

    private static final class BioState{
        double extracellularK=1.0,transmitter=0,glialK=.5,excitability=.2,metabotropic=0,plasticity=0;int step=0;
        void clamp(){extracellularK=cl(extracellularK,0,3);transmitter=cl(transmitter,0,3);glialK=cl(glialK,0,3);excitability=cl(excitability,0,1);metabotropic=cl(metabotropic,0,1);plasticity=cl(plasticity,0,1);}static double cl(double x,double a,double b){return Math.max(a,Math.min(b,x));}
        void passive(){transmitter*=.86;extracellularK+=(1-extracellularK)*.08;glialK+=(.5-glialK)*.03;excitability+=((.2+Math.max(0,extracellularK-1)*.28)-excitability)*.18;metabotropic*=.97;plasticity*=.998;step++;clamp();}
    }

    private void showInteractions(){
        clear();if(!bancReady){notReady();return;}content.addView(text("BANC構造 → 小域/液体 → FFC状態写像",21,true));BancDataset.Meta m=selectedId==0?null:banc.meta(selectedId);
        content.addView(text("[BANC] selected="+selectedId+" | cell="+(m==null?"":m.cellType)+" | NT="+(m==null?"":m.nt)+" | Σoutput syn="+(selectedId==0?0:banc.outputTotal(selectedId))+"\n[文献] 小域K⁺・伝達物質、グリア緩衝/再取り込み、BBB、受容体シグナル\n[MODEL] 下の連続値と係数。BANC実測濃度ではありません。",14,false));
        content.addView(text("写像: BANC edge → release/drive → local microdomain → receptor/glia → next state → plasticity",16,true));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);content.addView(box);Runnable draw=()->{box.removeAllViews();box.addView(text("step="+bio.step+"\nK⁺小域="+fmt(bio.extracellularK)+"\ntransmitter小域="+fmt(bio.transmitter)+"\nglial load="+fmt(bio.glialK)+"\nexcitability="+fmt(bio.excitability)+"\nmetabotropic="+fmt(bio.metabotropic)+"\nplasticity="+fmt(bio.plasticity),16,true));};draw.run();
        content.addView(button("選択ニューロン発火",()->{double w=selectedStructuralWeight();bio.extracellularK+=.18+.08*w;bio.transmitter+=.30+.22*w;bio.excitability+=.10;bio.passive();draw.run();}));
        content.addView(button("グリアK⁺緩衝",()->{double take=Math.max(0,Math.min(.30,bio.extracellularK-.75));bio.extracellularK-=take;bio.glialK+=take;bio.excitability-=take*.18;bio.passive();draw.run();}));
        content.addView(button("伝達物質再取り込み",()->{bio.transmitter*=.55;bio.passive();draw.run();}));
        content.addView(button("速い受容体応答",()->{bio.excitability+=.20*Math.min(1,bio.transmitter/1.2);bio.passive();draw.run();}));
        content.addView(button("代謝型受容体→slow signal",()->{double d=Math.min(1,bio.transmitter/1.2);bio.metabotropic+=.22*d;bio.plasticity+=.08*bio.metabotropic;bio.passive();draw.run();}));
        content.addView(button("10 step自然緩和",()->{for(int i=0;i<10;i++)bio.passive();draw.run();}));
        content.addView(button("reset",()->{bio.extracellularK=1;bio.transmitter=0;bio.glialK=.5;bio.excitability=.2;bio.metabotropic=0;bio.plasticity=0;bio.step=0;draw.run();}));
    }

    private double selectedStructuralWeight(){if(!bancReady||selectedId==0)return 0;long sum=banc.outputTotal(selectedId);return sum<=0?0:Math.min(1,Math.log1p(sum)/Math.log(100001.0));}
    private String fmt(double x){return String.format(Locale.ROOT,"%.3f",x);}

    private void showLearn(){clear();content.addView(text("読み方",21,true));String[] a={"root IDはv888 materializationと組で扱う。","count=1も構造edgeとして保持。ただし生理的有効性は別問題。","Exact-kは最短距離がk。長さkの全walkとは別。","NT予測だけで受容体依存の効果符号を断定しない。","BANCは静的EM connectome。イオン濃度時系列はBANC実測ではない。","FFC小域モデルは構造データと生理モデルの接続層として表示する。"};for(String s:a)content.addView(text("・"+s,15,false));}

    private long parseLong(String s,long d){try{return Long.parseLong(s.trim());}catch(Exception e){return d;}}

    @Override protected void onDestroy(){super.onDestroy();if(banc!=null)try{banc.close();}catch(Exception ignored){}}

    private final class GraphView extends View{
        Paint line=new Paint(1),node=new Paint(1),label=new Paint(1);Map<Long,PointF> pos=new HashMap<>();List<BancDataset.Link> links;long center;float scale=1,dx=0,dy=0,lx,ly;ScaleGestureDetector sg;
        GraphView(long center,List<BancDataset.Link> links){super(MainActivity.this);this.center=center;this.links=links;setBackgroundColor(Color.rgb(248,248,250));label.setColor(Color.DKGRAY);label.setTextSize(20);pos.put(center,new PointF(0,0));int n=Math.max(1,links.size());for(int i=0;i<links.size();i++){double a=2*Math.PI*i/n;pos.put(links.get(i).partner,new PointF((float)(390*Math.cos(a)),(float)(390*Math.sin(a))));}sg=new ScaleGestureDetector(MainActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){scale=Math.max(.4f,Math.min(4,scale*d.getScaleFactor()));invalidate();return true;}});}
        protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate(getWidth()/2f+dx,getHeight()/2f+dy);c.scale(scale,scale);PointF cp=pos.get(center);for(BancDataset.Link e:links){PointF p=pos.get(e.partner);line.setColor(e.count==1?Color.rgb(190,190,190):Color.rgb(120,120,140));line.setStrokeWidth(Math.max(1.5f,Math.min(12,e.count/2f))/scale);c.drawLine(cp.x,cp.y,p.x,p.y,line);}for(Map.Entry<Long,PointF>x:pos.entrySet()){node.setColor(x.getKey()==center?Color.rgb(210,120,40):Color.rgb(70,100,180));c.drawCircle(x.getValue().x,x.getValue().y,x.getKey()==center?36:25,node);label.setTextSize(18/scale);c.drawText(Long.toString(x.getKey()),x.getValue().x+30,x.getValue().y+5,label);}c.restore();}
        public boolean onTouchEvent(MotionEvent e){sg.onTouchEvent(e);if(!sg.isInProgress()){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lx=e.getX();ly=e.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){dx+=e.getX()-lx;dy+=e.getY()-ly;lx=e.getX();ly=e.getY();invalidate();return true;}}return true;}
    }
}
