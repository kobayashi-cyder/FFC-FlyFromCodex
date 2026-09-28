package dev.ffc.ce3;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

@SuppressLint("SetTextI18n")
public class BuilderActivity extends Activity {
    private final BuilderEngine engine = new BuilderEngine();
    private LinearLayout canvasList;
    private LinearLayout editor;
    private TextView summary;
    private TextView compiled;
    private TextView selectedTitle;
    private boolean advanced = false;
    private static final String PREF = "ce3_builder_v08";
    private static final String KEY = "workspace";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshAll();
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView tv(String s, int sp) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(23,32,51));
        v.setPadding(dp(6),dp(6),dp(6),dp(6));
        return v;
    }

    private TextView card(String title, String body) {
        TextView v = tv(title + "\n" + body, 14);
        v.setBackgroundColor(Color.WHITE);
        v.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(10));
        v.setLayoutParams(lp);
        return v;
    }

    private TextView header(String s) {
        TextView v = tv(s, 18);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(dp(2),dp(14),dp(2),dp(6));
        return v;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,251));

        LinearLayout top = new LinearLayout(this);
        Button back = btn("← 戻る");
        back.setOnClickListener(v -> finish());
        TextView title = tv("Ce3 回路ビルダー", 22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(back, new LinearLayout.LayoutParams(dp(92),dp(52)));
        top.addView(title, new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(top);

        TextView sub = tv("部品を置く → 選ぶ → 意味で調整 → コピー/Instance/Fork → 保存", 13);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(dp(14),0,dp(14),dp(8));
        root.addView(sub);

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(2),dp(14),dp(28));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        body.addView(card("この画面の考え方",
                "MORE/LESSの数値を毎回考えません。\n" +
                "まずWTA / Memory / Gate / AND-likeなどの部品を置き、\n" +
                "「必要入力」「感度」「抑制」「記憶」を意味で調整します。\n" +
                "下の詳細表示がMORE/LESSへ自動変換します。"));

        body.addView(header("1. LIBRARYから部品を置く"));
        LinearLayout lib1 = new LinearLayout(this);
        addLibButton(lib1, "AND-like", "T-AND");
        addLibButton(lib1, "Gate", "T-GATE");
        addLibButton(lib1, "WTA", "T-WTA");
        body.addView(lib1);
        LinearLayout lib2 = new LinearLayout(this);
        addLibButton(lib2, "Memory", "T-MEM");
        addLibButton(lib2, "Custom", "T-CUSTOM");
        body.addView(lib2);

        body.addView(header("2. CANVAS"));
        summary = card("ワークスペース","");
        body.addView(summary);

        canvasList = new LinearLayout(this);
        canvasList.setOrientation(LinearLayout.VERTICAL);
        body.addView(canvasList);

        LinearLayout actions = new LinearLayout(this);
        Button copy = btn("COPY");
        Button inst = btn("INSTANCE");
        Button fork = btn("FORK");
        Button del = btn("削除");
        copy.setOnClickListener(v -> { if(engine.selectedBlock()!=null){engine.copySelected();refreshAll();}});
        inst.setOnClickListener(v -> { if(engine.selectedBlock()!=null){engine.instanceSelected();refreshAll();}});
        fork.setOnClickListener(v -> { if(engine.selectedBlock()!=null){engine.forkSelected();refreshAll();}});
        del.setOnClickListener(v -> { if(engine.selectedBlock()!=null){engine.deleteSelected();refreshAll();}});
        actions.addView(copy,new LinearLayout.LayoutParams(0,dp(50),1));
        actions.addView(inst,new LinearLayout.LayoutParams(0,dp(50),1));
        actions.addView(fork,new LinearLayout.LayoutParams(0,dp(50),1));
        actions.addView(del,new LinearLayout.LayoutParams(0,dp(50),1));
        body.addView(actions);

        TextView relation = card("COPY / INSTANCE / FORK",
                "COPY = 完全に独立した複製\n" +
                "INSTANCE = 同じTemplateを共有。1つの設定変更が同系Instanceへ反映\n" +
                "FORK = 現在の設定を引き継いで新しいTemplateへ分岐");
        relation.setTextColor(Color.DKGRAY);
        body.addView(relation);

        body.addView(header("3. EASY SETTINGS"));
        selectedTitle = tv("部品を選んでください",17);
        selectedTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        body.addView(selectedTitle);

        editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        body.addView(editor);

        body.addView(header("4. 自動変換されたMORE/LESS"));
        compiled = card("内部表現","");
        compiled.setTypeface(Typeface.MONOSPACE);
        body.addView(compiled);

        Button advancedButton = btn("詳細設定を表示 / 隠す");
        advancedButton.setOnClickListener(v -> {
            advanced = !advanced;
            refreshEditor();
        });
        body.addView(advancedButton);

        body.addView(header("5. 保存と再利用"));
        LinearLayout persist = new LinearLayout(this);
        Button save = btn("ワークスペース保存");
        Button load = btn("読込");
        Button clear = btn("新規");
        save.setOnClickListener(v -> saveWorkspace());
        load.setOnClickListener(v -> loadWorkspace());
        clear.setOnClickListener(v -> {
            engine.blocks.clear();
            engine.selected = -1;
            refreshAll();
        });
        persist.addView(save,new LinearLayout.LayoutParams(0,dp(50),1));
        persist.addView(load,new LinearLayout.LayoutParams(0,dp(50),1));
        persist.addView(clear,new LinearLayout.LayoutParams(0,dp(50),1));
        body.addView(persist);

        body.addView(card("コネクトーム規模へ伸ばすとき",
                "Neuronを1個ずつ設定するのではなく、Template → Instance → Moduleの順で再利用します。\n" +
                "将来BANC/888を読込むときも、cell type / motif / module単位でTemplateを割り当て、例外だけ個別調整する設計です。"));

        setContentView(root);
    }

    private void addLibButton(LinearLayout row, String label, String templateId) {
        Button b = btn("+ " + label);
        b.setOnClickListener(v -> {
            engine.addInstance(templateId);
            refreshAll();
        });
        row.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));
    }

    private void refreshAll() {
        refreshCanvas();
        refreshEditor();
        refreshCompiled();
    }

    private void refreshCanvas() {
        canvasList.removeAllViews();
        summary.setText("ワークスペース\n" + engine.workspaceSummary());
        if (engine.blocks.isEmpty()) {
            TextView empty = card("まだ空です","上のLIBRARYから部品を1つ押してください。");
            empty.setTextColor(Color.DKGRAY);
            canvasList.addView(empty);
            return;
        }

        for (int i=0;i<engine.blocks.size();i++) {
            BuilderEngine.Block b = engine.blocks.get(i);
            Button item = btn((i==engine.selected?"● ":"○ ") + b.name +
                    "\n" + b.relation + (b.linked?" · linked":" · detached"));
            final int index=i;
            item.setOnClickListener(v -> {
                engine.select(index);
                refreshAll();
            });
            canvasList.addView(item,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,dp(64)));
        }
    }

    private void refreshEditor() {
        editor.removeAllViews();
        BuilderEngine.Block b = engine.selectedBlock();
        if (b == null) {
            selectedTitle.setText("部品を選んでください");
            editor.addView(card("EASY SETTINGS","CANVAS上の部品をタップしてください。"));
            return;
        }

        selectedTitle.setText("選択中: " + b.name + "   [" + b.relation + "]");

        editor.addView(tv("必要入力 — いつONになるか",14));
        LinearLayout need = new LinearLayout(this);
        addNeedButton(need, BuilderEngine.RequiredInput.ANY);
        addNeedButton(need, BuilderEngine.RequiredInput.HALF);
        addNeedButton(need, BuilderEngine.RequiredInput.MOST);
        addNeedButton(need, BuilderEngine.RequiredInput.ALL);
        editor.addView(need);

        editor.addView(tv("感度 — 入力をどれだけ強く受けるか",14));
        SeekBar sensitivity = new SeekBar(this);
        sensitivity.setMax(100);
        sensitivity.setProgress((int)Math.round((b.gain - 0.50) / 1.50 * 100));
        sensitivity.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s,int p,boolean from) {
                if(from){engine.setSensitivity(p/100.0);refreshCompiled();}
            }
        });
        editor.addView(sensitivity);
        editor.addView(tv(String.format(Locale.US,"低い ←  %.0f%%  → 高い",(b.gain-0.50)/1.50*100),12));

        editor.addView(tv("抑制 — 他の候補をどれだけ抑えるか",14));
        SeekBar inhibition = new SeekBar(this);
        inhibition.setMax(150);
        inhibition.setProgress((int)Math.round(b.inhibition*100));
        inhibition.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s,int p,boolean from) {
                if(from){engine.setInhibition(p/100.0);refreshCompiled();}
            }
        });
        editor.addView(inhibition);
        editor.addView(tv(String.format(Locale.US,"弱い ←  %.2f  → 強い",b.inhibition),12));

        editor.addView(tv("記憶 — 前の状態をどれだけ残すか",14));
        SeekBar memory = new SeekBar(this);
        memory.setMax(95);
        memory.setProgress((int)Math.round(b.decay*100));
        memory.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s,int p,boolean from) {
                if(from){engine.setMemory(p/100.0);refreshCompiled();}
            }
        });
        editor.addView(memory);
        editor.addView(tv(String.format(Locale.US,"短い ←  %.2f  → 長い",b.decay),12));

        if (advanced) {
            editor.addView(header("ADVANCED"));
            Button comparator = btn("Comparator: " + (b.more?"MORE ≥":"LESS <"));
            comparator.setOnClickListener(v -> {engine.toggleComparator();refreshAll();});
            editor.addView(comparator);

            editor.addView(tv("Threshold ratio",13));
            SeekBar threshold = new SeekBar(this);
            threshold.setMax(100);
            threshold.setProgress((int)Math.round(b.thresholdRatio*100));
            threshold.setOnSeekBarChangeListener(new SimpleSeek() {
                public void onProgressChanged(SeekBar s,int p,boolean from) {
                    if(from){engine.setThresholdRatio(p/100.0);refreshCompiled();}
                }
            });
            editor.addView(threshold);

            editor.addView(tv("Bias (-1 ... +1)",13));
            SeekBar bias = new SeekBar(this);
            bias.setMax(200);
            bias.setProgress((int)Math.round((b.bias+1)*100));
            bias.setOnSeekBarChangeListener(new SimpleSeek() {
                public void onProgressChanged(SeekBar s,int p,boolean from) {
                    if(from){engine.setBias(p/100.0-1.0);refreshCompiled();}
                }
            });
            editor.addView(bias);
        }
    }

    private void addNeedButton(LinearLayout row, BuilderEngine.RequiredInput mode) {
        BuilderEngine.Block b = engine.selectedBlock();
        String mark = b != null && b.requiredInput == mode ? "● " : "";
        Button x = btn(mark + mode.label);
        x.setOnClickListener(v -> {
            engine.setRequiredInput(mode);
            refreshAll();
        });
        row.addView(x,new LinearLayout.LayoutParams(0,dp(52),1));
    }

    private void refreshCompiled() {
        BuilderEngine.Block b = engine.selectedBlock();
        if (b == null) {
            compiled.setText("内部表現\n部品を選ぶと、EASY SETTINGSがMORE/LESSへどう変換されたか表示します。");
            return;
        }
        compiled.setText("内部表現\n" + engine.compileSelected(8));
    }

    private void saveWorkspace() {
        SharedPreferences p = getSharedPreferences(PREF,MODE_PRIVATE);
        p.edit().putString(KEY,engine.serialize()).apply();
        summary.setText("ワークスペース\n保存しました。\n" + engine.workspaceSummary());
    }

    private void loadWorkspace() {
        SharedPreferences p = getSharedPreferences(PREF,MODE_PRIVATE);
        engine.deserialize(p.getString(KEY,""));
        refreshAll();
    }

    abstract class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) { refreshEditor(); }
    }
}
