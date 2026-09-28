package dev.ffc.ce3;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
public class BioSystemActivity extends Activity {
    private final ConnectomeEngine engine = new ConnectomeEngine();
    private NetworkView networkView;
    private TextView sensorText, resultText, weightText, provenanceText, testText;
    private Button gateA, gateV, gateH, learnToggle, supervisorButton;
    private SeekBar targetBar, threatBar, contextBar;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        refresh();
    }

    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String text, int sp) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(23, 32, 51));
        v.setPadding(dp(6), dp(6), dp(6), dp(6));
        return v;
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        return b;
    }

    private TextView section(String text) {
        TextView h = tv(text, 17);
        h.setTypeface(null, android.graphics.Typeface.BOLD);
        h.setPadding(dp(2), dp(14), dp(2), dp(6));
        return h;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout top = new LinearLayout(this);
        Button back = btn("← 戻る");
        back.setOnClickListener(v -> finish());
        TextView title = tv("F · Ce3  Connectome Lab", 20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(back, new LinearLayout.LayoutParams(dp(92), dp(52)));
        top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(top);

        TextView sub = tv("①場面を選ぶ → ②行動を見る → ③ゲートで止める → ④STEPで記憶 → ⑤REWARDで学習", 13);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(dp(14), 0, dp(14), dp(8));
        root.addView(sub);

        networkView = new NetworkView();
        root.addView(networkView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(4), dp(14), dp(24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView quick = tv(
                "おすすめ体験\n" +
                "FORAGE（探索）を押す → APPROACH（接近）を確認 → APPROACHゲートをBLOCK → HOLDへ変化 → STEP ×8 → REWARD +。\n" +
                "同じ変化を、ネットワーク図・数値・WHY説明の3つで確認できます。",
                14);
        quick.setTypeface(null, android.graphics.Typeface.BOLD);
        quick.setBackgroundColor(Color.WHITE);
        quick.setPadding(dp(12), dp(12), dp(12), dp(12));
        body.addView(quick);

        body.addView(section("0. 起動前セルフテスト"));
        Button selfTest = btn("RUN SELF TEST");
        testText = tv("", 13);
        testText.setTypeface(android.graphics.Typeface.MONOSPACE);
        testText.setBackgroundColor(Color.WHITE);
        testText.setPadding(dp(12), dp(12), dp(12), dp(12));
        selfTest.setOnClickListener(v -> {
            ConnectomeEngine.SelfTestReport report = ConnectomeEngine.runSelfTest();
            testText.setText((report.allPassed() ? "ALL PASS  " : "CHECK FAILED  ") +
                    report.passed + "/" + report.total + "\n" + report.details);
        });
        body.addView(selfTest);
        body.addView(testText);

        body.addView(section("1. 場面を選ぶ — まずここ"));
        sensorText = tv("", 14);
        body.addView(sensorText);
        targetBar = addSlider(body, "TARGET", 55);
        threatBar = addSlider(body, "THREAT", 25);
        contextBar = addSlider(body, "CONTEXT", 50);
        targetBar.setOnSeekBarChangeListener(listener(0));
        threatBar.setOnSeekBarChangeListener(listener(1));
        contextBar.setOnSeekBarChangeListener(listener(2));

        LinearLayout presets = new LinearLayout(this);
        String[] names = {"FORAGE\n探索", "THREAT\n危険", "AMBIG\n曖昧"};
        for (String name : names) {
            Button b = btn(name);
            b.setOnClickListener(v -> {
                String n = ((Button)v).getText().toString();
                if (n.contains("FORAGE")) setSensors(85, 10, 65);
                else if (n.contains("THREAT")) setSensors(20, 95, 45);
                else setSensors(55, 55, 50);
            });
            presets.addView(b, new LinearLayout.LayoutParams(0, dp(50), 1));
        }
        body.addView(presets);

        body.addView(section("2. 行動候補を止める / 通す — GATE"));
        LinearLayout gates = new LinearLayout(this);
        gateA = btn("");
        gateV = btn("");
        gateH = btn("");
        gateA.setOnClickListener(v -> { engine.gate[0] = !engine.gate[0]; refresh(); });
        gateV.setOnClickListener(v -> { engine.gate[1] = !engine.gate[1]; refresh(); });
        gateH.setOnClickListener(v -> { engine.gate[2] = !engine.gate[2]; refresh(); });
        gates.addView(gateA, new LinearLayout.LayoutParams(0, dp(52), 1));
        gates.addView(gateV, new LinearLayout.LayoutParams(0, dp(52), 1));
        gates.addView(gateH, new LinearLayout.LayoutParams(0, dp(52), 1));
        body.addView(gates);

        body.addView(section("3. 上位の方針を変える — SUPERVISOR"));
        supervisorButton = btn("");
        supervisorButton.setOnClickListener(v -> {
            engine.supervisor = (engine.supervisor + 1) % 3;
            refresh();
        });
        body.addView(supervisorButton);

        body.addView(section("4. 時間を進めて記憶を見る — STATE"));
        LinearLayout stepRow = new LinearLayout(this);
        Button step = btn("STEP\n1回");
        Button run8 = btn("STEP ×8\n連続");
        Button reset = btn("RESET\n記憶を消す");
        step.setOnClickListener(v -> { engine.step(); refresh(); });
        run8.setOnClickListener(v -> {
            for (int i = 0; i < 8; i++) engine.step();
            refresh();
        });
        reset.setOnClickListener(v -> {
            engine.resetState();
            refresh();
        });
        stepRow.addView(step, new LinearLayout.LayoutParams(0, dp(52), 1));
        stepRow.addView(run8, new LinearLayout.LayoutParams(0, dp(52), 1));
        stepRow.addView(reset, new LinearLayout.LayoutParams(0, dp(52), 1));
        body.addView(stepRow);

        resultText = tv("", 15);
        resultText.setTypeface(null, android.graphics.Typeface.BOLD);
        resultText.setBackgroundColor(Color.WHITE);
        resultText.setPadding(dp(12), dp(12), dp(12), dp(12));
        body.addView(resultText);

        body.addView(section("5. 結果を評価して学習 — REWARD"));
        LinearLayout learnRow = new LinearLayout(this);
        Button positive = btn("REWARD +\n良かった");
        Button negative = btn("REWARD -\n悪かった");
        learnToggle = btn("");
        positive.setOnClickListener(v -> { engine.learn(+1.0); refresh(); });
        negative.setOnClickListener(v -> { engine.learn(-1.0); refresh(); });
        learnToggle.setOnClickListener(v -> { engine.learningEnabled = !engine.learningEnabled; refresh(); });
        learnRow.addView(positive, new LinearLayout.LayoutParams(0, dp(52), 1));
        learnRow.addView(negative, new LinearLayout.LayoutParams(0, dp(52), 1));
        learnRow.addView(learnToggle, new LinearLayout.LayoutParams(0, dp(52), 1));
        body.addView(learnRow);

        weightText = tv("", 13);
        weightText.setTypeface(android.graphics.Typeface.MONOSPACE);
        weightText.setBackgroundColor(Color.WHITE);
        weightText.setPadding(dp(12), dp(12), dp(12), dp(12));
        body.addView(weightText);

        body.addView(section("6. 何をハエから借り、何をCe3が抽象化したか"));
        provenanceText = tv(
                "生物から着想: 重み付き統合、興奮/抑制、閾値、局所フィードバック、ゲーティング、再帰、競合、モジュール間調整。\n\n" +
                "Ce3の工学抽象: MORE/LESS表現、3モジュールWTA、数値バイアス、単純な報酬変調重み更新。\n\n" +
                "この画面はハエ脳の再現ではなく、connectomeから転用しやすい計算原理を一つの実行系にまとめたプロトタイプです。",
                13);
        provenanceText.setTextColor(Color.DKGRAY);
        provenanceText.setBackgroundColor(Color.WHITE);
        provenanceText.setPadding(dp(12), dp(12), dp(12), dp(12));
        body.addView(provenanceText);

        setContentView(root);
    }

    private SeekBar addSlider(LinearLayout parent, String label, int initial) {
        TextView l = tv(label, 13);
        l.setTextColor(Color.DKGRAY);
        parent.addView(l);
        SeekBar b = new SeekBar(this);
        b.setMax(100);
        b.setProgress(initial);
        parent.addView(b);
        return b;
    }

    private SeekBar.OnSeekBarChangeListener listener(final int idx) {
        return new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                engine.sensor[idx] = p / 100.0;
                refresh();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        };
    }

    private void setSensors(int target, int threat, int context) {
        targetBar.setProgress(target);
        threatBar.setProgress(threat);
        contextBar.setProgress(context);
        engine.sensor[0] = target / 100.0;
        engine.sensor[1] = threat / 100.0;
        engine.sensor[2] = context / 100.0;
        refresh();
    }

    private void refresh() {
        if (networkView == null) return;
        engine.preview();

        sensorText.setText(String.format(Locale.US,
                "TARGET %.2f    THREAT %.2f    CONTEXT %.2f",
                engine.sensor[0], engine.sensor[1], engine.sensor[2]));

        gateA.setText("接近 APPROACH\n" + (engine.gate[0] ? "EN / 通す" : "BLOCK / 止める"));
        gateV.setText("回避 AVOID\n" + (engine.gate[1] ? "EN / 通す" : "BLOCK / 止める"));
        gateH.setText("待機 HOLD\n" + (engine.gate[2] ? "EN / 通す" : "BLOCK / 止める"));

        String[] modes = {"NEUTRAL", "SEEK bias", "AVOID bias"};
        supervisorButton.setText("上位方針 SUPERVISOR: " + modes[engine.supervisor] + "\n押すと切替");
        learnToggle.setText(engine.learningEnabled ? "LEARN ON" : "LEARN OFF");

        resultText.setText(String.format(Locale.US,
                "OUTPUT = %s  (%s)\n" +
                "scores: APPROACH %.3f | AVOID %.3f | HOLD %.3f\n" +
                "memory: A %.3f | V %.3f | H %.3f\n" +
                "threshold %.2f | step %d | last reward %+.1f\n" +
                "WHY: %s",
                engine.output, outputJa(engine.output),
                engine.score[0], engine.score[1], engine.score[2],
                engine.memory[0], engine.memory[1], engine.memory[2],
                engine.threshold, engine.steps, engine.lastReward,
                engine.explanation()));

        weightText.setText(String.format(Locale.US,
                "WEIGHT MATRIX  [target, threat, context]\n" +
                "APPROACH [%+.2f, %+.2f, %+.2f]\n" +
                "AVOID    [%+.2f, %+.2f, %+.2f]\n" +
                "HOLD     [%+.2f, %+.2f, %+.2f]\n\n" +
                "更新: w <- clamp(w + lr × reward × sensor)\n" +
                "winnerの行だけ更新 / lr=%.2f",
                engine.w[0][0], engine.w[0][1], engine.w[0][2],
                engine.w[1][0], engine.w[1][1], engine.w[1][2],
                engine.w[2][0], engine.w[2][1], engine.w[2][2],
                engine.lr));

        networkView.invalidate();
    }

    class NetworkView extends View {
        private final Paint p = new Paint(1);

        NetworkView() {
            super(BioSystemActivity.this);
            p.setTypeface(android.graphics.Typeface.DEFAULT);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(Color.WHITE);
            float w = getWidth(), h = getHeight();

            float sx = w * 0.12f;
            float mx = w * 0.47f;
            float wx = w * 0.76f;
            float ox = w * 0.90f;

            float y0 = h * 0.24f, y1 = h * 0.50f, y2 = h * 0.76f;

            drawNode(c, sx, y0, "TARGET", fmt(engine.sensor[0]), true, 0);
            drawNode(c, sx, y1, "THREAT", fmt(engine.sensor[1]), true, 0);
            drawNode(c, sx, y2, "CONTEXT", fmt(engine.sensor[2]), true, 0);

            for (int m = 0; m < 3; m++) {
                float my = m == 0 ? y0 : m == 1 ? y1 : y2;
                for (int i = 0; i < 3; i++) {
                    float sy = i == 0 ? y0 : i == 1 ? y1 : y2;
                    drawEdge(c, sx + dp(34), sy, mx - dp(38), my,
                            engine.w[m][i], Math.abs(engine.w[m][i]) > 0.01);
                }
            }

            drawNode(c, mx, y0, "APP", fmt(engine.score[0]), engine.score[0] > 0, 1);
            drawNode(c, mx, y1, "AVOID", fmt(engine.score[1]), engine.score[1] > 0, 2);
            drawNode(c, mx, y2, "HOLD", fmt(engine.score[2]), engine.score[2] > 0, 3);

            drawEdge(c, mx + dp(36), y0, wx - dp(36), y1, +1, engine.gate[0]);
            drawEdge(c, mx + dp(36), y1, wx - dp(36), y1, +1, engine.gate[1]);
            drawEdge(c, mx + dp(36), y2, wx - dp(36), y1, +1, engine.gate[2]);

            drawNode(c, wx, y1, "WTA", "winner", engine.winner >= 0, 4);
            drawEdge(c, wx + dp(36), y1, ox - dp(32), y1, +1, engine.winner >= 0);
            drawNode(c, ox, y1, "OUT", shortOut(engine.output), engine.winner >= 0, 5);

            p.setColor(Color.rgb(42, 54, 77));
            p.setTextSize(dp(12));
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("LOCAL MOTIFS", mx, dp(22), p);
            c.drawText("COMPETE", wx, dp(22), p);
            c.drawText("BEHAVIOR", ox, dp(22), p);
            c.drawText("SUPERVISOR: " + (engine.supervisor == 0 ? "neutral" : engine.supervisor == 1 ? "seek" : "avoid"),
                    w * 0.62f, h - dp(10), p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private String fmt(double x) {
            return String.format(Locale.US, "%.2f", x);
        }

        private String shortOut(String s) {
            if ("APPROACH".equals(s)) return "APP";
            if ("AVOID".equals(s)) return "AVD";
            if ("HOLD".equals(s)) return "HLD";
            return "IDLE";
        }

        private void drawEdge(Canvas c, float x1, float y1, float x2, float y2, double weight, boolean on) {
            if (!on) {
                p.setColor(Color.rgb(205, 211, 221));
                p.setStrokeWidth(dp(1));
            } else if (weight >= 0) {
                p.setColor(Color.rgb(65, 115, 215));
                p.setStrokeWidth(dp(2));
            } else {
                p.setColor(Color.rgb(205, 75, 75));
                p.setStrokeWidth(dp(2));
            }
            c.drawLine(x1, y1, x2, y2, p);
        }

        private void drawNode(Canvas c, float x, float y, String top, String bottom, boolean on, int kind) {
            int fill;
            if (!on) fill = Color.rgb(228, 232, 239);
            else if (kind == 1) fill = Color.rgb(77, 139, 219);
            else if (kind == 2) fill = Color.rgb(216, 100, 88);
            else if (kind == 3) fill = Color.rgb(119, 155, 97);
            else if (kind == 4) fill = Color.rgb(133, 102, 190);
            else if (kind == 5) fill = Color.rgb(52, 56, 73);
            else fill = Color.rgb(95, 111, 139);

            p.setColor(fill);
            c.drawCircle(x, y, dp(31), p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(on ? Color.WHITE : Color.rgb(40, 46, 58));
            p.setTextSize(dp(11));
            c.drawText(top, x, y - dp(2), p);
            p.setTextSize(dp(10));
            c.drawText(bottom, x, y + dp(14), p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }
}
