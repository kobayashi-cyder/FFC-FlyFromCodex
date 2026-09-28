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
    private LogicFluidEmulator emulator;
    private PlasticityStore plasticity;
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
                d.open(); banc=d;
                List<BancDataset.Meta> first=d.search("",1);
                if(!first.isEmpty()) selectedId=first.get(0).id;
                plasticity=new PlasticityStore(this);
                emulator=new LogicFluidEmulator(d,plasticity);
                emulator.reset(selectedId);
                bancReady=true;
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
        nav.addView(button("Emulator",this::showEmulator)); nav.addView(button("調教",this::showTraining)); nav.addView(button("データ",this::showData)); nav.addView(button("学習",this::showLearn)); hs.addView(nav); root.addView(hs);
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
        content.addView(text("挙動エミュレーション",19,true));
        content.addView(text("Emulatorでは実BANC edgeをイベント駆動で伝播し、細胞外小域ECF・細胞間質液(ISF)・グリアK⁺緩衝・BBB/血リンパ境界を同じtickで更新します。",15,false));
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


    private void showEmulator(){
        clear();
        if(!bancReady || emulator==null){notReady();return;}
        content.addView(text("BANC論理回路 + 細胞外液/間質液 Emulator",21,true));
        content.addView(text(
                "[BANC] 実root IDと全v2 pair接続を使います。count=1も保持。\n" +
                "[文献境界] brain-side K⁺ ≈ 5 mM、血リンパK⁺は高濃度（代表値40 mM）。\n" +
                "[MODEL] 小域→ISF拡散、グリア緩衝、BBB透過、興奮性への係数は挙動比較用の簡略モデルです。\n" +
                "1 tickは物理的なmsへ固定していません。",
                14,false));

        LinearLayout state=new LinearLayout(this);
        state.setOrientation(LinearLayout.VERTICAL);
        content.addView(state);

        EditText min=new EditText(this);
        min.setInputType(2);
        min.setText(Integer.toString(emulator.getMinSynapseCount()));
        min.setHint("最小synapse count（1なら弱い接続を含む）");
        content.addView(min);

        Switch barrier=new Switch(this);
        barrier.setText("BBB permeability stress ×10");
        barrier.setChecked(emulator.isBarrierStressed());
        barrier.setOnCheckedChangeListener((b,checked)->{
            emulator.setBarrierStress(checked);
            drawEmulatorSnapshot(state,emulator.snapshot());
        });
        content.addView(barrier);

        content.addView(button("選択ニューロンを刺激",()->{
            emulator.stimulate(selectedId,1.0f);
            drawEmulatorSnapshot(state,emulator.snapshot());
        }));
        content.addView(button("このニューロンをseedにリセット",()->{
            emulator.reset(selectedId);
            drawEmulatorSnapshot(state,emulator.snapshot());
        }));
        content.addView(button("1 tick進める",()->runEmulatorTicks(1,min,state)));
        content.addView(button("10 tick進める",()->runEmulatorTicks(10,min,state)));
        content.addView(button("50 tick進める",()->runEmulatorTicks(50,min,state)));

        content.addView(text("状態の意味",18,true));
        content.addView(text(
                "microdomain K⁺ = シナプス周辺など局所細胞外液のK⁺。\n" +
                "ISF K⁺ = 脳内の細胞間質液として平均化したK⁺。\n" +
                "ECF volume = 細胞外液量の相対値。K⁺/水恒常性の崩れを可視化するproxy。\n" +
                "glial load = グリアが回収したK⁺負荷のproxy。\n" +
                "excitability gain = ISF K⁺上昇で論理発火閾値が下がるようにしたMODEL係数。\n" +
                "active neurons = 現tickで論理的に活動している実BANC root ID数。",
                14,false));

        drawEmulatorSnapshot(state,emulator.snapshot());
    }

    private void runEmulatorTicks(int steps,EditText minBox,LinearLayout state){
        int threshold=Math.max(1,(int)parseLong(minBox.getText().toString(),1));
        emulator.setMinSynapseCount(threshold);
        state.removeAllViews();
        state.addView(text("計算中… "+steps+" tick / min syn="+threshold,15,true));
        new Thread(()->{
            try{
                LogicFluidEmulator.Snapshot snap=null;
                for(int i=0;i<steps;i++) snap=emulator.step();
                LogicFluidEmulator.Snapshot out=snap==null?emulator.snapshot():snap;
                runOnUiThread(()->drawEmulatorSnapshot(state,out));
            }catch(Exception e){
                runOnUiThread(()->{
                    state.removeAllViews();
                    state.addView(text("Emulator error: "+e.getMessage(),14,true));
                });
            }
        }).start();
    }

    private void drawEmulatorSnapshot(LinearLayout state,LogicFluidEmulator.Snapshot s){
        state.removeAllViews();
        state.addView(text(
                "tick="+s.tick+
                " | active="+s.activeNeurons+
                " | evaluated edges="+s.evaluatedEdges+"\n"+
                "microdomain K⁺="+fmt(s.microdomainK)+" mM\n"+
                "ISF / interstitial K⁺="+fmt(s.interstitialK)+" mM\n"+
                "hemolymph K⁺="+fmt(LogicFluidEmulator.HEMOLYMPH_K_MM)+" mM [boundary]\n"+
                "ECF volume="+fmt(s.extracellularVolume)+" × baseline\n"+
                "transmitter="+fmt(s.transmitter)+" [MODEL]\n"+
                "glial K⁺ load="+fmt(s.glialKLoad)+" [MODEL]\n"+
                "excitability gain="+fmt(s.excitabilityGain)+" ×\n"+
                "BBB permeability="+String.format(Locale.ROOT,"%.6f",s.bbbPermeability),
                16,true));
        if(s.topIds.length>0){
            state.addView(text("上位active root IDs",17,true));
            for(int i=0;i<s.topIds.length;i++){
                BancDataset.Meta m=banc.meta(s.topIds[i]);
                state.addView(text(
                        s.topIds[i]+" | a="+String.format(Locale.ROOT,"%.3f",s.topActivities[i])+
                        " | "+(m==null?"":m.cellType)+
                        " | NT="+(m==null?"":m.nt),
                        13,false));
            }
        }else{
            state.addView(text("活動は消失しています。seed刺激で再開できます。",13,false));
        }
    }


    private void showTraining(){
        clear();
        if(!bancReady || emulator==null){notReady();return;}

        content.addView(text("ハエを調教する · Reward-modulated Plasticity",21,true));
        content.addView(text(
                "BANCの元接続は変更しません。調教で変わるのは別保存された plasticity delta だけです。\n" +
                "刺激→伝播→recent eligibility trace→報酬/罰→最近使ったedgeの有効重み補正、という簡略3-factor学習です。\n" +
                "これは『実ショウジョウバエの学習を完全再現』するモデルではなく、BANC上で学習挙動を試すFFCモデルです。",
                14,false));

        EditText seedBox=new EditText(this);
        seedBox.setInputType(2);
        seedBox.setHint("刺激 root ID");
        seedBox.setText(Long.toString(selectedId));
        content.addView(seedBox);

        EditText targetBox=new EditText(this);
        targetBox.setInputType(2);
        targetBox.setHint("目標 root ID（報酬判定先）");
        targetBox.setText(Long.toString(selectedId));
        content.addView(targetBox);

        EditText ticksBox=new EditText(this);
        ticksBox.setInputType(2);
        ticksBox.setHint("1 episodeのtick数");
        ticksBox.setText("5");
        content.addView(ticksBox);

        EditText episodesBox=new EditText(this);
        episodesBox.setInputType(2);
        episodesBox.setHint("episode数");
        episodesBox.setText("20");
        content.addView(episodesBox);

        EditText lrBox=new EditText(this);
        lrBox.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        lrBox.setHint("学習率");
        lrBox.setText("0.06");
        content.addView(lrBox);

        LinearLayout result=new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);

        content.addView(button("手動: 報酬 +1",()->{
            float lr=(float)parseDouble(lrBox.getText().toString(),0.06);
            int changed=emulator.reinforce(1f,lr);
            Toast.makeText(this,"reward: "+changed+" edges changed",Toast.LENGTH_LONG).show();
            drawTrainingState(result,parseLong(targetBox.getText().toString(),0));
        }));

        content.addView(button("手動: 罰 -1",()->{
            float lr=(float)parseDouble(lrBox.getText().toString(),0.06);
            int changed=emulator.reinforce(-1f,lr);
            Toast.makeText(this,"punishment: "+changed+" edges changed",Toast.LENGTH_LONG).show();
            drawTrainingState(result,parseLong(targetBox.getText().toString(),0));
        }));

        content.addView(button("条件づけ訓練を実行",()->{
            long seed=parseLong(seedBox.getText().toString(),selectedId);
            long target=parseLong(targetBox.getText().toString(),0);
            int ticks=Math.max(1,Math.min(50,(int)parseLong(ticksBox.getText().toString(),5)));
            int episodes=Math.max(1,Math.min(500,(int)parseLong(episodesBox.getText().toString(),20)));
            float lr=(float)Math.max(0.001,Math.min(0.5,parseDouble(lrBox.getText().toString(),0.06)));
            result.removeAllViews();
            result.addView(text("調教中… "+episodes+" episodes",15,true));

            new Thread(()->{
                int hits=0,totalChanged=0;
                try{
                    for(int ep=0;ep<episodes;ep++){
                        emulator.reset(seed);
                        emulator.stimulate(seed,1f);
                        boolean reached=false;
                        for(int t=0;t<ticks;t++){
                            emulator.step();
                            if(target>0 && emulator.isActive(target))reached=true;
                        }
                        if(reached){
                            hits++;
                            totalChanged+=emulator.reinforce(1f,lr);
                        }else{
                            totalChanged+=emulator.reinforce(-0.12f,lr*0.35f);
                        }
                    }
                    int h=hits,c=totalChanged;
                    runOnUiThread(()->{
                        result.removeAllViews();
                        result.addView(text(
                                "訓練完了\n"+
                                "target到達="+h+"/"+episodes+" episodes\n"+
                                "更新edge延べ数="+c+"\n"+
                                "保存済みplasticity edges="+emulator.learnedEdgeCount(),
                                16,true));
                        drawTrainingState(result,target);
                    });
                }catch(Exception e){
                    runOnUiThread(()->{
                        result.removeAllViews();
                        result.addView(text("training error: "+e.getMessage(),14,true));
                    });
                }
            }).start();
        }));

        content.addView(button("学習前のBANCへ戻す（学習差分全消去）",()->{
            emulator.clearTraining();
            Toast.makeText(this,"Plasticity cleared. BANC base graph is unchanged.",Toast.LENGTH_LONG).show();
            drawTrainingState(result,parseLong(targetBox.getText().toString(),0));
        }));

        content.addView(text("どう変わるか",18,true));
        content.addView(text(
                "報酬を受けた直前の伝播経路は次回少し通りやすくなり、罰を受けた経路は少し通りにくくなります。\n" +
                "同じ刺激を繰り返すと、特定の経路へ活動が偏る・目標ニューロンへ到達しやすくなる・別経路が抑えられる、といった挙動を観察できます。\n" +
                "細胞外小域/ISF K⁺とグリア緩衝も毎tick動くので、回路学習と興奮性環境を同時に見られます。",
                14,false));

        content.addView(result);
        drawTrainingState(result,parseLong(targetBox.getText().toString(),0));
    }

    private void drawTrainingState(LinearLayout result,long target){
        LogicFluidEmulator.Snapshot s=emulator.snapshot();
        result.addView(text(
                "learned edges="+s.learnedEdges+
                " | mean |Δw|="+fmt(s.meanAbsPlasticity)+
                " | eligibility="+s.eligibilityEdges+"\n"+
                "cumulative reward="+fmt(s.cumulativeReward)+
                " | last reward="+fmt(s.lastReward)+
                (target>0?"\ntarget "+target+" activity="+String.format(Locale.ROOT,"%.3f",emulator.activityOf(target)):""),
                14,false));
    }

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
    private double parseDouble(String s,double d){try{return Double.parseDouble(s.trim());}catch(Exception e){return d;}}

    @Override protected void onDestroy(){super.onDestroy();if(banc!=null)try{banc.close();}catch(Exception ignored){}}

    private final class GraphView extends View{
        Paint line=new Paint(1),node=new Paint(1),label=new Paint(1);Map<Long,PointF> pos=new HashMap<>();List<BancDataset.Link> links;long center;float scale=1,dx=0,dy=0,lx,ly;ScaleGestureDetector sg;
        GraphView(long center,List<BancDataset.Link> links){super(MainActivity.this);this.center=center;this.links=links;setBackgroundColor(Color.rgb(248,248,250));label.setColor(Color.DKGRAY);label.setTextSize(20);pos.put(center,new PointF(0,0));int n=Math.max(1,links.size());for(int i=0;i<links.size();i++){double a=2*Math.PI*i/n;pos.put(links.get(i).partner,new PointF((float)(390*Math.cos(a)),(float)(390*Math.sin(a))));}sg=new ScaleGestureDetector(MainActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){scale=Math.max(.4f,Math.min(4,scale*d.getScaleFactor()));invalidate();return true;}});}
        protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate(getWidth()/2f+dx,getHeight()/2f+dy);c.scale(scale,scale);PointF cp=pos.get(center);for(BancDataset.Link e:links){PointF p=pos.get(e.partner);line.setColor(e.count==1?Color.rgb(190,190,190):Color.rgb(120,120,140));line.setStrokeWidth(Math.max(1.5f,Math.min(12,e.count/2f))/scale);c.drawLine(cp.x,cp.y,p.x,p.y,line);}for(Map.Entry<Long,PointF>x:pos.entrySet()){node.setColor(x.getKey()==center?Color.rgb(210,120,40):Color.rgb(70,100,180));c.drawCircle(x.getValue().x,x.getValue().y,x.getKey()==center?36:25,node);label.setTextSize(18/scale);c.drawText(Long.toString(x.getKey()),x.getValue().x+30,x.getValue().y+5,label);}c.restore();}
        public boolean onTouchEvent(MotionEvent e){sg.onTouchEvent(e);if(!sg.isInProgress()){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lx=e.getX();ly=e.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){dx+=e.getX()-lx;dy+=e.getY()-ly;lx=e.getX();ly=e.getY();invalidate();return true;}}return true;}
    }
}
