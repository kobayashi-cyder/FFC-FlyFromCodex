package dev.ffc.ce3;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

@SuppressLint("SetTextI18n")
public class BancTutorialActivity extends Activity {

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,251));

        LinearLayout top = new LinearLayout(this);
        Button back = btn("← 戻る");
        back.setOnClickListener(v -> finish());
        TextView title = tv("BANC v888 Lab チュートリアル",22);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(back,new LinearLayout.LayoutParams(dp(92),dp(52)));
        top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(top);

        TextView sub = tv("3分で「読込 → 圧縮 → Motif → 例外調整 → Builder」まで",13);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(dp(14),0,dp(14),dp(8));
        root.addView(sub);

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(2),dp(14),dp(28));
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        body.addView(card("最初に覚えること",
                "このLabは、15万個級のNeuronを1個ずつ調整する画面ではありません。\n\n" +
                "Cellを属性でまとめる → Ce3 Templateを仮割当 → Moduleへ圧縮 → Motifを見る → 必要なCellだけ例外調整、という流れです。"));

        body.addView(step("STEP 1 — まずDEMOを読込",
                "最初は実データを用意しなくて構いません。\n" +
                "BANC v888 Labへ戻り「DEMOを読込」を押してください。\n\n" +
                "確認する場所:\n" +
                "・cells / stored edges\n" +
                "・modules / module edges\n" +
                "・Gate / Memory / AND-like / WTA / Custom\n" +
                "・reciprocal / 3-cycle / feed-forward"));

        body.addView(step("STEP 2 — 本物のデータは2段階で読込",
                "① CELLS CSV/TSV\n" +
                "② CONNECTIONS CSV/TSV\n\n" +
                "Cell側にはroot_idと属性、Connection側にはpre/post root_idとsynapse_countを使います。\n" +
                "CellsだけでもTemplate仮割当はできますが、Motif解析にはConnectionが必要です。"));

        body.addView(step("STEP 3 — AUTO ASSIGNは『仮説の初期値』",
                "AUTO ASSIGNは、生物学的な真理を確定する機能ではありません。\n" +
                "NT、super_class、connectivity tagなどを見て、Ce3で扱いやすいGate / Memory / AND-like / WTA / Customへ初期配置します。\n\n" +
                "判断根拠が弱いCellはCustomへ残す設計です。"));

        body.addView(step("STEP 4 — Module化で複雑さを畳む",
                "同じcell type + TemplateのCellをModuleへまとめます。\n\n" +
                "例:\n" +
                "TasteSensory × 80 → 1つのSensory Module\n" +
                "Gate系 × 120 → 1つのGate Module\n\n" +
                "画面上ではModuleを見て、必要なときだけ個々のCellへ降ります。"));

        body.addView(step("STEP 5 — Motifは『候補』として読む",
                "reciprocal = 双方向接続のModule対\n" +
                "3-cycle = A→B→C→A\n" +
                "feed-forward = A→B、A→C、B→C\n\n" +
                "これらは回路構造の候補です。Motifがあるだけで、その機能が確定するわけではありません。"));

        body.addView(step("STEP 6 — 例外Cellだけ個別調整",
                "root_idを入力 → 確認 → Gate / Memory / AND-like / WTA / Customを選択 → 適用。\n\n" +
                "AUTOへ戻すを押せば、自動割当に戻せます。\n" +
                "大量のCellを手修正せず、例外だけ扱うのが基本です。"));

        body.addView(step("STEP 7 — Builderへ持っていく",
                "BANC Labで『構造』を圧縮し、回路ビルダーで『動作』を与えます。\n\n" +
                "BANC Lab: どのCellがどう繋がるか\n" +
                "Builder: 感度 / 抑制 / 記憶 / MORE/LESSをどう動かすか\n\n" +
                "この分離により、connectomeを壊さずCe3 dynamicsを試せます。"));

        body.addView(card("やってはいけない読み方",
                "・GABAだから必ずGate、と断定しない\n" +
                "・reciprocalだからMemory、と断定しない\n" +
                "・synapse_countをそのまま実効weightとみなさない\n" +
                "・connectomeだけで時間特性や学習則まで確定したと考えない\n\n" +
                "BANC Labは『候補を絞る装置』として使うのが正しいです。"));

        LinearLayout actions = new LinearLayout(this);
        Button openLab = btn("BANC v888 Labへ");
        Button openBuilder = btn("回路ビルダーへ");
        openLab.setOnClickListener(v -> {
            startActivity(new Intent(this,Banc888Activity.class));
            finish();
        });
        openBuilder.setOnClickListener(v ->
                startActivity(new Intent(this,BuilderActivity.class)));
        actions.addView(openLab,new LinearLayout.LayoutParams(0,dp(54),1));
        actions.addView(openBuilder,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(actions);

        setContentView(root);
    }

    private TextView step(String title,String body) {
        return card(title,body);
    }

    private int dp(int n) {
        return (int)(n*getResources().getDisplayMetrics().density+0.5f);
    }

    private TextView tv(String s,int sp) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(23,32,51));
        v.setPadding(dp(6),dp(6),dp(6),dp(6));
        return v;
    }

    private TextView card(String title,String body) {
        TextView v = tv(title+"\n"+body,14);
        v.setBackgroundColor(Color.WHITE);
        v.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(10));
        v.setLayoutParams(lp);
        return v;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        return b;
    }
}
