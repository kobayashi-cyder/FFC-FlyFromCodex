package dev.ffc.ce3;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

@SuppressLint("SetTextI18n")
public class Banc888Activity extends Activity {
    private static final int REQ_CELLS = 301;
    private static final int REQ_CONNECTIONS = 302;

    private final Banc888Engine engine = new Banc888Engine();
    private TextView status;
    private TextView modules;
    private TextView exceptionInfo;
    private EditText rootIdInput;
    private String pendingTemplate = "T-CUSTOM";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        refresh();
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView tv(String s,int sp) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(23,32,51));
        v.setPadding(dp(6),dp(6),dp(6),dp(6));
        return v;
    }

    private TextView card(String title,String body) {
        TextView v = tv(title + "\n" + body,14);
        v.setBackgroundColor(Color.WHITE);
        v.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(10));
        v.setLayoutParams(lp);
        return v;
    }

    private TextView header(String s) {
        TextView v = tv(s,18);
        v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
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
        TextView title = tv("BANC v888 Lab",22);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(back,new LinearLayout.LayoutParams(dp(92),dp(52)));
        top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(top);

        TextView sub = tv("CSV/TSV → Template自動割当 → Module化 → Motif解析 → 例外調整",13);
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

        body.addView(card("目的",
                "BANC/888の全Neuronを1個ずつ設定しません。\n" +
                "cell type / NT / connectivity tagからCe3 Templateを自動割当し、\n" +
                "同種CellをModuleへ畳み、例外だけroot_id単位で調整します。\n\n" +
                "この割当はCe3の工学的ヒューリスティックであり、生物学的同一性の主張ではありません。"));

        body.addView(header("1. データを読み込む"));
        LinearLayout import1 = new LinearLayout(this);
        Button demo = btn("DEMOを読込");
        Button cells = btn("CELLS CSV/TSV");
        Button conns = btn("CONNECTIONS CSV/TSV");
        demo.setOnClickListener(v -> {
            engine.loadDemo();
            refresh();
        });
        cells.setOnClickListener(v -> openDocument(REQ_CELLS));
        conns.setOnClickListener(v -> openDocument(REQ_CONNECTIONS));
        import1.addView(demo,new LinearLayout.LayoutParams(0,dp(52),1));
        import1.addView(cells,new LinearLayout.LayoutParams(0,dp(52),1));
        import1.addView(conns,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(import1);

        body.addView(card("対応する主な列名",
                "Cells: root_id / resolved_type / cell_type / nt_type / super_class / body_part / connectivity_tag\n" +
                "Connections: pre_root_id / post_root_id / synapse_count\n" +
                "列名には一般的な別名も許容します。CodexからCSVを書き出した後、そのまま端末で選べる設計です。"));

        status = card("STATUS","まだデータなし");
        status.setTypeface(Typeface.MONOSPACE);
        body.addView(status);

        body.addView(header("2. 自動割当とModule化"));
        LinearLayout analyzeRow = new LinearLayout(this);
        Button auto = btn("AUTO ASSIGN");
        Button motif = btn("MOTIF解析");
        Button clear = btn("CLEAR");
        auto.setOnClickListener(v -> {
            int changed = engine.autoAssignAll();
            status.setText("STATUS\nAUTO ASSIGN changed=" + changed + "\n" + engine.summary());
            modules.setText(engine.modulesText(30));
        });
        motif.setOnClickListener(v -> {
            engine.buildModules();
            engine.analyzeMotifs();
            refresh();
        });
        clear.setOnClickListener(v -> {
            engine.clearAll();
            refresh();
        });
        analyzeRow.addView(auto,new LinearLayout.LayoutParams(0,dp(50),1));
        analyzeRow.addView(motif,new LinearLayout.LayoutParams(0,dp(50),1));
        analyzeRow.addView(clear,new LinearLayout.LayoutParams(0,dp(50),1));
        body.addView(analyzeRow);

        body.addView(card("自動割当の考え方",
                "例: 抑制性候補→Gate、attractor/reciprocal系tag→Memory、integrator→AND-like、broadcaster→WTA。\n" +
                "根拠の強さが足りないCellはCustomへ残します。後から例外指定できます。"));

        body.addView(header("3. MODULES"));
        modules = tv("まだModuleなし",13);
        modules.setTypeface(Typeface.MONOSPACE);
        modules.setBackgroundColor(Color.WHITE);
        modules.setPadding(dp(12),dp(12),dp(12),dp(12));
        body.addView(modules);

        body.addView(header("4. 例外Cellだけ個別調整"));
        rootIdInput = new EditText(this);
        rootIdInput.setHint("root_id を入力");
        rootIdInput.setSingleLine(true);
        rootIdInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        body.addView(rootIdInput,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        LinearLayout exceptionTemplates = new LinearLayout(this);
        addTemplateButton(exceptionTemplates,"Gate","T-GATE");
        addTemplateButton(exceptionTemplates,"Memory","T-MEM");
        addTemplateButton(exceptionTemplates,"AND-like","T-AND");
        body.addView(exceptionTemplates);
        LinearLayout exceptionTemplates2 = new LinearLayout(this);
        addTemplateButton(exceptionTemplates2,"WTA","T-WTA");
        addTemplateButton(exceptionTemplates2,"Custom","T-CUSTOM");
        body.addView(exceptionTemplates2);

        LinearLayout exceptionActions = new LinearLayout(this);
        Button inspect = btn("確認");
        Button apply = btn("適用");
        Button reset = btn("AUTOへ戻す");
        inspect.setOnClickListener(v -> inspectRoot());
        apply.setOnClickListener(v -> applyException());
        reset.setOnClickListener(v -> clearException());
        exceptionActions.addView(inspect,new LinearLayout.LayoutParams(0,dp(50),1));
        exceptionActions.addView(apply,new LinearLayout.LayoutParams(0,dp(50),1));
        exceptionActions.addView(reset,new LinearLayout.LayoutParams(0,dp(50),1));
        body.addView(exceptionActions);

        exceptionInfo = card("選択Cell","root_idを入力してください。");
        exceptionInfo.setTypeface(Typeface.MONOSPACE);
        body.addView(exceptionInfo);

        body.addView(header("5. 大規模データでの扱い"));
        body.addView(card("メモリ節約",
                "Neuron属性は最小限で保持し、Connectionはprimitive配列で最大4,000,000行まで保存します。\n" +
                "Cell同士の全表示ではなく、type+Template単位のModuleグラフへ畳んでMotifを解析します。\n" +
                "Module Motifは reciprocal / 3-cycle / feed-forward を数えます。"));

        body.addView(card("次の工程",
                "このLabで得たModuleを回路ビルダーのTemplate/Instanceへ変換すれば、\n" +
                "BANC構造を保持したまま感度・抑制・記憶などCe3 dynamicsを載せられます。"));

        setContentView(root);
    }

    private void addTemplateButton(LinearLayout row,String label,String id) {
        Button b = btn(label);
        b.setOnClickListener(v -> {
            pendingTemplate = id;
            exceptionInfo.setText("選択Cell\n適用予定 Template = " + id +
                    "\nroot_idを入力して「適用」を押してください。");
        });
        row.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));
    }

    private void openDocument(int requestCode) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
                "text/csv","text/tab-separated-values","text/plain","application/octet-stream"
        });
        startActivityForResult(i,requestCode);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        status.setText("STATUS\n読込中…");
        new Thread(() -> importUri(requestCode,uri)).start();
    }

    private void importUri(int requestCode,Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            Banc888Engine.Progress progress = (message,rows) ->
                    runOnUiThread(() -> status.setText("STATUS\n" + message + " rows"));
            Banc888Engine.ImportReport report;
            if (requestCode == REQ_CELLS) report = engine.importCells(reader,progress);
            else report = engine.importConnections(reader,progress);
            runOnUiThread(() -> {
                status.setText("STATUS\n" + report.toString() + "\n" + engine.summary());
                modules.setText(engine.modulesText(30));
            });
        } catch (Exception e) {
            runOnUiThread(() -> status.setText("STATUS\nIMPORT ERROR\n" + e.getMessage()));
        }
    }

    private Long rootId() {
        try { return Long.parseLong(rootIdInput.getText().toString().trim()); }
        catch (Exception e) { return null; }
    }

    private void inspectRoot() {
        Long id = rootId();
        exceptionInfo.setText("選択Cell\n" + (id == null ? "root_idが不正です" : engine.exceptionInfo(id)));
    }

    private void applyException() {
        Long id = rootId();
        if (id == null) {
            exceptionInfo.setText("選択Cell\nroot_idが不正です");
            return;
        }
        boolean ok = engine.applyException(id,pendingTemplate);
        exceptionInfo.setText("選択Cell\n" + (ok ? "適用しました\n" + engine.exceptionInfo(id) : "root_id not found"));
        refresh();
    }

    private void clearException() {
        Long id = rootId();
        if (id == null) {
            exceptionInfo.setText("選択Cell\nroot_idが不正です");
            return;
        }
        boolean ok = engine.clearException(id);
        exceptionInfo.setText("選択Cell\n" + (ok ? "AUTOへ戻しました\n" + engine.exceptionInfo(id) : "root_id not found"));
        refresh();
    }

    private void refresh() {
        status.setText("STATUS\n" + engine.summary());
        modules.setText(engine.modules().isEmpty() ? "まだModuleなし" : engine.modulesText(30));
    }
}
