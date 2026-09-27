package jp.ffc.flyfromcodex;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    static final class Neuron {
        long id;
        String type, superClass, bodyPart, nt;
        double neuriteUm, gliaPct;
        Neuron(long id, String type, String superClass, String bodyPart, String nt, double neuriteUm, double gliaPct) {
            this.id=id; this.type=type; this.superClass=superClass; this.bodyPart=bodyPart; this.nt=nt;
            this.neuriteUm=neuriteUm; this.gliaPct=gliaPct;
        }
    }

    static final class Edge {
        long pre, post;
        int syn;
        String nt, neuropil, effect, confidence;
        Edge(long pre, long post, int syn, String nt, String neuropil, String effect, String confidence) {
            this.pre=pre; this.post=post; this.syn=syn; this.nt=nt; this.neuropil=neuropil;
            this.effect=effect; this.confidence=confidence;
        }
    }

    private final ArrayList<Neuron> neurons = new ArrayList<>();
    private final ArrayList<Edge> edges = new ArrayList<>();
    private LinearLayout content;
    private long selectedId = 1001;
    private static final int REQ_NODES = 501;
    private static final int REQ_EDGES = 502;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadDemoData();
        buildShell();
        showOverview();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        TextView title = text("FFC Connectome Explorer", 24, true);
        root.addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(navButton("概要", this::showOverview));
        nav.addView(navButton("検索", this::showSearch));
        nav.addView(navButton("細胞", this::showCell));
        nav.addView(navButton("Hops", this::showHops));
        nav.addView(navButton("Graph", this::showGraph));
        nav.addView(navButton("学習", this::showLearn));
        nav.addView(navButton("取込", this::showImport));
        scroll.addView(nav);
        root.addView(scroll);

        ScrollView page = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(4, 10, 4, 80);
        page.addView(content);
        root.addView(page, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(25,25,30));
        v.setPadding(10,10,10,10);
        if (bold) v.setTypeface(null, 1);
        return v;
    }

    private Button navButton(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void clear() { content.removeAllViews(); }

    private void showOverview() {
        clear();
        content.addView(text("理解の順序", 21, true));
        content.addView(text(
                "EM image → segmentation → neuron/root → mesh/skeleton → synapse → connectivity graph → annotation → cell type / function / body part",
                16, false));

        content.addView(text("このAPKで見るもの", 20, true));
        content.addView(text(
                "・ニューロン属性\n" +
                "・直接接続（1-hop）\n" +
                "・Exact 2〜6-hop\n" +
                "・長さkの全path集約\n" +
                "・synapse count / NT / neuropil\n" +
                "・effect / confidence\n" +
                "・neurite length / glial coverage 派生指標\n" +
                "・ネイティブ接続グラフ\n" +
                "・CSV差し替え",
                16, false));

        content.addView(text("データ状態", 19, true));
        content.addView(text(
                "現在の同梱データはUI・アルゴリズム確認用デモです。BANC実IDではありません。\n" +
                "neurons=" + neurons.size() + " / edges=" + edges.size(),
                15, false));
    }

    private void showSearch() {
        clear();
        content.addView(text("ニューロン検索", 21, true));

        EditText query = new EditText(this);
        query.setHint("ID / cell type / super class / body part / NT");
        content.addView(query);

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);

        content.addView(navButton("検索", () -> {
            results.removeAllViews();
            String q = query.getText().toString().trim().toLowerCase(Locale.ROOT);
            int count = 0;
            for (Neuron n : neurons) {
                String hay = (n.id + " " + n.type + " " + n.superClass + " " + n.bodyPart + " " + n.nt)
                        .toLowerCase(Locale.ROOT);
                if (q.isEmpty() || hay.contains(q)) {
                    Button row = navButton(
                            n.type + " · " + n.id + " | " + n.superClass + " | " + n.nt,
                            () -> { selectedId = n.id; showCell(); });
                    results.addView(row);
                    count++;
                }
            }
            if (count == 0) results.addView(text("該当なし", 15, false));
        }));

        content.addView(results);
    }

    private Neuron neuron(long id) {
        for (Neuron n : neurons) if (n.id == id) return n;
        return null;
    }

    private void showCell() {
        clear();
        Neuron n = neuron(selectedId);
        if (n == null) {
            content.addView(text("細胞が見つかりません。", 18, true));
            return;
        }

        content.addView(text("Cell " + n.id, 22, true));
        content.addView(text(
                "cell type: " + n.type + "\n" +
                "super class: " + n.superClass + "\n" +
                "body part: " + n.bodyPart + "\n" +
                "neurotransmitter: " + n.nt + "\n" +
                "neurite length: " + n.neuriteUm + " µm\n" +
                "glial coverage: " + n.gliaPct + " %",
                16, false));

        content.addView(text("Outputs", 19, true));
        boolean any = false;
        for (Edge e : edges) {
            if (e.pre == n.id) {
                content.addView(text(
                        "→ " + e.post +
                        " | syn=" + e.syn +
                        " | NT=" + e.nt +
                        " | region=" + e.neuropil +
                        " | effect=" + e.effect +
                        " | confidence=" + e.confidence,
                        14, false));
                any = true;
            }
        }
        if (!any) content.addView(text("なし", 14, false));

        content.addView(text("Inputs", 19, true));
        any = false;
        for (Edge e : edges) {
            if (e.post == n.id) {
                content.addView(text(
                        "← " + e.pre +
                        " | syn=" + e.syn +
                        " | NT=" + e.nt +
                        " | region=" + e.neuropil +
                        " | effect=" + e.effect +
                        " | confidence=" + e.confidence,
                        14, false));
                any = true;
            }
        }
        if (!any) content.addView(text("なし", 14, false));
    }

    private void showHops() {
        clear();
        content.addView(text("Exact-hop / k-path 解析", 21, true));

        EditText source = new EditText(this);
        source.setInputType(2);
        source.setText(Long.toString(selectedId));
        content.addView(source);

        Spinner hop = new Spinner(this);
        hop.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"1","2","3","4","5","6"}));
        hop.setSelection(1);
        content.addView(hop);

        EditText minSyn = new EditText(this);
        minSyn.setInputType(2);
        minSyn.setHint("最小synapse数");
        minSyn.setText("1");
        content.addView(minSyn);

        LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);

        content.addView(navButton("計算", () -> {
            out.removeAllViews();
            long src = parseLong(source.getText().toString(), selectedId);
            int k = Integer.parseInt((String) hop.getSelectedItem());
            int min = (int) parseLong(minSyn.getText().toString(), 1);

            Map<Long,Integer> distances = shortestDistances(src, k, min);
            Map<Long,long[]> allKPaths = aggregateKPaths(src, k, min);

            int exactCount = 0;
            for (Map.Entry<Long,long[]> entry : allKPaths.entrySet()) {
                long target = entry.getKey();
                if (distances.getOrDefault(target, Integer.MAX_VALUE) == k) {
                    Neuron t = neuron(target);
                    long[] a = entry.getValue();
                    out.addView(text(
                            target + " " + (t == null ? "" : t.type) +
                            " | paths=" + a[0] +
                            " | weightedSyn=" + a[1],
                            14, false));
                    exactCount++;
                }
            }

            out.addView(text(
                    "Exact " + k + "-hop = " + exactCount + " cells\n" +
                    "Exact-k = 最短距離がちょうどkの細胞集合。\n" +
                    "k-path = 長さkの全経路集合。両者は別物です。",
                    14, true));
        }));

        content.addView(out);
    }

    private Map<Long,Integer> shortestDistances(long src, int maxHop, int minSyn) {
        Map<Long,Integer> dist = new HashMap<>();
        ArrayDeque<Long> q = new ArrayDeque<>();
        dist.put(src, 0);
        q.add(src);

        while (!q.isEmpty()) {
            long u = q.remove();
            int du = dist.get(u);
            if (du >= maxHop) continue;

            for (Edge e : edges) {
                if (e.pre == u && e.syn >= minSyn && !dist.containsKey(e.post)) {
                    dist.put(e.post, du + 1);
                    q.add(e.post);
                }
            }
        }
        return dist;
    }

    private Map<Long,long[]> aggregateKPaths(long src, int k, int minSyn) {
        Map<Long,long[]> frontier = new HashMap<>();
        frontier.put(src, new long[]{1, 0});

        for (int step=0; step<k; step++) {
            Map<Long,long[]> next = new HashMap<>();
            for (Map.Entry<Long,long[]> cur : frontier.entrySet()) {
                for (Edge e : edges) {
                    if (e.pre == cur.getKey() && e.syn >= minSyn) {
                        long[] agg = next.get(e.post);
                        if (agg == null) {
                            agg = new long[]{0,0};
                            next.put(e.post, agg);
                        }
                        long pathCount = cur.getValue()[0];
                        agg[0] += pathCount;
                        agg[1] += pathCount * e.syn;
                    }
                }
            }
            frontier = next;
        }
        return frontier;
    }

    private void showGraph() {
        clear();
        content.addView(text("ネイティブ接続グラフ", 21, true));
        content.addView(text(
                "ノード=細胞、線=接続。線幅はsynapse数の概算強度。ピンチ拡大・ドラッグ対応。",
                14, false));

        GraphView graph = new GraphView();
        content.addView(graph, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1100));
    }

    private void showLearn() {
        clear();
        content.addView(text("BANC / Codex 学習メモ", 21, true));
        String[] notes = new String[] {
                "root_id + materialization: ニューロンIDは版情報と組で扱う。",
                "synapse: 個々の接点。edge: neuron pairへ集約した接続。",
                "1-hop: 直接接続。",
                "2-hop: A→M→B。中継Mのまとまり・収束・発散を見る。",
                "3-hop: A→M1→M2→B。再収束・ボトルネックを見る。",
                "NT: 効果の手掛かり。受容体情報なしに興奮/抑制を断定しない。",
                "effect confidence: 構造接続と機能効果の確度を分ける。",
                "Influence: multi-hop累積指標。実験的因果そのものではない。",
                "neurite length: 完成形の静的形態指標。発生時の伸長速度ではない。",
                "glial coverage: 静的EMから作る派生評価枠。BANC標準の動的伸長値ではない。"
        };
        for (String n : notes) content.addView(text("・" + n, 15, false));
    }

    private void showImport() {
        clear();
        content.addView(text("BANC由来データ取込", 21, true));
        content.addView(text(
                "nodes.csv\n" +
                "id,cell_type,super_class,body_part,nt,neurite_um,glia_coverage_pct\n\n" +
                "edges.csv\n" +
                "pre_id,post_id,synapse_count,nt,neuropil,effect,effect_confidence",
                14, false));

        content.addView(navButton("nodes.csv を選択", () -> pickCsv(REQ_NODES)));
        content.addView(navButton("edges.csv を選択", () -> pickCsv(REQ_EDGES)));
        content.addView(navButton("デモデータに戻す", () -> {
            loadDemoData();
            showOverview();
        }));
    }

    private void pickCsv(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, requestCode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            int rows = requestCode == REQ_NODES ? importNodes(in) : importEdges(in);
            Toast.makeText(this, rows + " rows imported", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Import error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int importNodes(InputStream in) throws Exception {
        List<String[]> rows = readCsv(in);
        if (rows.size() < 2) return 0;
        String[] h = rows.get(0);
        neurons.clear();

        for (int i=1; i<rows.size(); i++) {
            String[] r = rows.get(i);
            neurons.add(new Neuron(
                    parseLong(col(h,r,"id"),0),
                    col(h,r,"cell_type"),
                    col(h,r,"super_class"),
                    col(h,r,"body_part"),
                    col(h,r,"nt"),
                    parseDouble(col(h,r,"neurite_um"),0),
                    parseDouble(col(h,r,"glia_coverage_pct"),0)
            ));
        }
        return neurons.size();
    }

    private int importEdges(InputStream in) throws Exception {
        List<String[]> rows = readCsv(in);
        if (rows.size() < 2) return 0;
        String[] h = rows.get(0);
        edges.clear();

        for (int i=1; i<rows.size(); i++) {
            String[] r = rows.get(i);
            edges.add(new Edge(
                    parseLong(col(h,r,"pre_id"),0),
                    parseLong(col(h,r,"post_id"),0),
                    (int) parseLong(col(h,r,"synapse_count"),0),
                    col(h,r,"nt"),
                    col(h,r,"neuropil"),
                    col(h,r,"effect"),
                    col(h,r,"effect_confidence")
            ));
        }
        return edges.size();
    }

    private List<String[]> readCsv(InputStream in) throws Exception {
        ArrayList<String[]> rows = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.trim().isEmpty()) rows.add(line.split(",", -1));
        }
        return rows;
    }

    private String col(String[] h, String[] r, String key) {
        for (int i=0; i<h.length; i++) {
            if (h[i].trim().equalsIgnoreCase(key)) return i < r.length ? r[i].trim() : "";
        }
        return "";
    }

    private long parseLong(String s, long fallback) {
        try { return Long.parseLong(s.trim()); }
        catch (Exception e) { return fallback; }
    }

    private double parseDouble(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return fallback; }
    }

    private void loadDemoData() {
        neurons.clear();
        edges.clear();

        neurons.add(new Neuron(1001,"Sensory-A","sensory","antenna","ACh",680,21));
        neurons.add(new Neuron(1002,"Local-B","interneuron","brain","ACh",920,32));
        neurons.add(new Neuron(1003,"Local-C","interneuron","brain","GABA",740,35));
        neurons.add(new Neuron(1004,"Projection-D","projection","brain→VNC","ACh",1880,44));
        neurons.add(new Neuron(1005,"Descending-E","descending","brain→VNC","ACh",2450,61));
        neurons.add(new Neuron(1006,"Premotor-F","premotor","VNC","Glu",1120,54));
        neurons.add(new Neuron(1007,"Motor-G","motor","leg","ACh",1640,70));
        neurons.add(new Neuron(1008,"Modulator-H","modulatory","brain","Dopamine",1310,29));
        neurons.add(new Neuron(1009,"Local-I","interneuron","VNC","GABA",860,47));
        neurons.add(new Neuron(1010,"Motor-J","motor","wing","ACh",1710,66));

        edges.add(new Edge(1001,1002,18,"ACh","AL","excitatory?","medium"));
        edges.add(new Edge(1001,1003,9,"ACh","AL","excitatory?","medium"));
        edges.add(new Edge(1002,1004,15,"ACh","SMP","excitatory?","medium"));
        edges.add(new Edge(1003,1004,12,"GABA","SMP","inhibitory?","medium"));
        edges.add(new Edge(1002,1008,6,"ACh","SMP","modulatory path","low"));
        edges.add(new Edge(1008,1005,8,"Dopamine","SMP","modulatory","medium"));
        edges.add(new Edge(1004,1005,21,"ACh","neck","excitatory?","medium"));
        edges.add(new Edge(1005,1006,26,"ACh","VNC","excitatory?","medium"));
        edges.add(new Edge(1005,1009,11,"ACh","VNC","excitatory?","medium"));
        edges.add(new Edge(1009,1006,13,"GABA","VNC","inhibitory?","medium"));
        edges.add(new Edge(1006,1007,30,"Glu","leg","unknown","low"));
        edges.add(new Edge(1006,1010,17,"Glu","wing","unknown","low"));
    }

    private final class GraphView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<Long, PointF> positions = new HashMap<>();
        private final ScaleGestureDetector scaler;

        private float scale = 1f;
        private float dx = 0f, dy = 0f;
        private float lastX, lastY;

        GraphView() {
            super(MainActivity.this);
            setBackgroundColor(Color.rgb(248,248,250));
            labelPaint.setColor(Color.DKGRAY);
            labelPaint.setTextSize(22);

            scaler = new ScaleGestureDetector(MainActivity.this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override public boolean onScale(ScaleGestureDetector detector) {
                            scale = Math.max(0.45f, Math.min(4.0f,
                                    scale * detector.getScaleFactor()));
                            invalidate();
                            return true;
                        }
                    });

            int count = Math.max(1, neurons.size());
            for (int i=0; i<neurons.size(); i++) {
                double angle = 2.0 * Math.PI * i / count;
                positions.put(neurons.get(i).id,
                        new PointF((float)(360*Math.cos(angle)), (float)(360*Math.sin(angle))));
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.translate(getWidth()/2f + dx, getHeight()/2f + dy);
            canvas.scale(scale, scale);

            linePaint.setColor(Color.LTGRAY);
            for (Edge e : edges) {
                PointF a = positions.get(e.pre);
                PointF b = positions.get(e.post);
                if (a == null || b == null) continue;
                linePaint.setStrokeWidth(Math.max(2f, Math.min(12f, e.syn/2f)) / scale);
                canvas.drawLine(a.x, a.y, b.x, b.y, linePaint);
            }

            for (Neuron n : neurons) {
                PointF p = positions.get(n.id);
                if (p == null) continue;
                nodePaint.setColor(n.id == selectedId
                        ? Color.rgb(210,120,40)
                        : Color.rgb(70,100,180));
                canvas.drawCircle(p.x, p.y, 32f, nodePaint);
                labelPaint.setTextSize(20f / scale);
                canvas.drawText(Long.toString(n.id), p.x + 38f, p.y + 6f, labelPaint);
            }

            canvas.restore();
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            scaler.onTouchEvent(e);
            if (!scaler.isInProgress()) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    lastX = e.getX();
                    lastY = e.getY();
                    return true;
                }
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    dx += e.getX() - lastX;
                    dy += e.getY() - lastY;
                    lastX = e.getX();
                    lastY = e.getY();
                    invalidate();
                    return true;
                }
            }
            return true;
        }
    }
}
