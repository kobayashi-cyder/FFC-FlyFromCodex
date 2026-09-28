package dev.ffc.ce3;

public class ConnectomeEngine {
    public final double[] sensor = {0.55, 0.25, 0.50};
    public final boolean[] gate = {true, true, true};
    public final double[][] w = {
            {+1.20, -1.10, +0.35},
            {-0.25, +1.45, +0.15},
            {+0.10, +0.20, +0.75}
    };
    public final double[] memory = {0, 0, 0};
    public final double[] score = {0, 0, 0};
    public final double[] bias = {0, 0, 0};
    public final double threshold = 0.35;
    public final double lr = 0.08;

    public int supervisor = 0;
    public int steps = 0;
    public int winner = -1;
    public String output = "IDLE";
    public double lastReward = 0;
    public boolean learningEnabled = true;

    public void setSensors(double target, double threat, double context) {
        sensor[0] = clamp(target, 0, 1);
        sensor[1] = clamp(threat, 0, 1);
        sensor[2] = clamp(context, 0, 1);
    }

    public void setBias() {
        bias[0] = bias[1] = bias[2] = 0;
        if (supervisor == 1) {
            bias[0] = +0.25;
            bias[1] = -0.10;
        } else if (supervisor == 2) {
            bias[0] = -0.15;
            bias[1] = +0.30;
        }
    }

    public void preview() {
        setBias();
        for (int m = 0; m < 3; m++) {
            double sum = bias[m] + 0.35 * memory[m];
            for (int i = 0; i < 3; i++) sum += w[m][i] * sensor[i];
            score[m] = gate[m] && sum >= threshold ? sum : 0;
        }
        winner = -1;
        double best = 0;
        for (int m = 0; m < 3; m++) {
            if (score[m] > best) {
                best = score[m];
                winner = m;
            }
        }
        output = winner == 0 ? "APPROACH" :
                 winner == 1 ? "AVOID" :
                 winner == 2 ? "HOLD" : "IDLE";
    }

    public void step() {
        preview();
        for (int m = 0; m < 3; m++) {
            double drive = (winner == m) ? 1.0 : 0.0;
            memory[m] = 0.72 * memory[m] + 0.28 * drive;
        }
        steps++;
        preview();
    }

    public void learn(double reward) {
        lastReward = reward;
        preview();
        if (!learningEnabled || winner < 0) return;
        for (int i = 0; i < 3; i++) {
            w[winner][i] = clamp(w[winner][i] + lr * reward * sensor[i], -2.0, 2.0);
        }
        preview();
    }

    public void resetState() {
        for (int i = 0; i < 3; i++) memory[i] = 0;
        steps = 0;
        lastReward = 0;
        preview();
    }

    public void resetAll() {
        setSensors(0.55, 0.25, 0.50);
        gate[0] = gate[1] = gate[2] = true;
        supervisor = 0;
        steps = 0;
        winner = -1;
        output = "IDLE";
        lastReward = 0;
        learningEnabled = true;
        memory[0] = memory[1] = memory[2] = 0;

        w[0][0] = +1.20; w[0][1] = -1.10; w[0][2] = +0.35;
        w[1][0] = -0.25; w[1][1] = +1.45; w[1][2] = +0.15;
        w[2][0] = +0.10; w[2][1] = +0.20; w[2][2] = +0.75;
        preview();
    }

    public String explanation() {
        preview();
        if (winner < 0) return "閾値を超えた有効モジュールがないためIDLEです。";
        int topInput = 0;
        double bestAbs = -1;
        double bestContribution = 0;
        for (int i = 0; i < 3; i++) {
            double c = w[winner][i] * sensor[i];
            if (Math.abs(c) > bestAbs) {
                bestAbs = Math.abs(c);
                bestContribution = c;
                topInput = i;
            }
        }
        String[] inputNames = {"TARGET", "THREAT", "CONTEXT"};
        String[] moduleNames = {"APPROACH", "AVOID", "HOLD"};
        return moduleNames[winner] + "がWTAで勝利。最大寄与は" +
                inputNames[topInput] + " " + formatSigned(bestContribution) +
                "、再帰memory " + String.format(java.util.Locale.US, "%.3f", memory[winner]) +
                "、supervisor bias " + formatSigned(bias[winner]) + "です。";
    }

    private static String formatSigned(double v) {
        return String.format(java.util.Locale.US, "%+.3f", v);
    }

    public static SelfTestReport runSelfTest() {
        StringBuilder details = new StringBuilder();
        int pass = 0, total = 0;

        total++;
        ConnectomeEngine e1 = new ConnectomeEngine();
        e1.setSensors(0.85, 0.10, 0.65);
        e1.preview();
        if ("APPROACH".equals(e1.output)) { pass++; details.append("PASS forage→APPROACH\n"); }
        else details.append("FAIL forage expected APPROACH got ").append(e1.output).append("\n");

        total++;
        ConnectomeEngine e2 = new ConnectomeEngine();
        e2.setSensors(0.20, 0.95, 0.45);
        e2.preview();
        if ("AVOID".equals(e2.output)) { pass++; details.append("PASS threat→AVOID\n"); }
        else details.append("FAIL threat expected AVOID got ").append(e2.output).append("\n");

        total++;
        ConnectomeEngine e3 = new ConnectomeEngine();
        e3.setSensors(0.85, 0.10, 0.65);
        e3.gate[0] = false;
        e3.preview();
        if ("HOLD".equals(e3.output)) { pass++; details.append("PASS gate blocks APPROACH→HOLD\n"); }
        else details.append("FAIL gate expected HOLD got ").append(e3.output).append("\n");

        total++;
        ConnectomeEngine e4 = new ConnectomeEngine();
        e4.setSensors(0.85, 0.10, 0.65);
        e4.step();
        if (e4.memory[0] > 0 && e4.steps == 1) { pass++; details.append("PASS recurrence stores winner\n"); }
        else details.append("FAIL recurrence memory/step\n");

        total++;
        ConnectomeEngine e5 = new ConnectomeEngine();
        e5.setSensors(0.85, 0.10, 0.65);
        e5.preview();
        double before = e5.w[0][0];
        e5.learn(+1);
        if (e5.w[0][0] > before) { pass++; details.append("PASS reward updates winning weights\n"); }
        else details.append("FAIL reward did not increase winning target weight\n");

        total++;
        ConnectomeEngine e6 = new ConnectomeEngine();
        e6.setSensors(0.85, 0.10, 0.65);
        e6.step();
        e6.resetState();
        if (e6.steps == 0 && Math.abs(e6.memory[0]) < 1e-9 &&
                Math.abs(e6.memory[1]) < 1e-9 && Math.abs(e6.memory[2]) < 1e-9) {
            pass++; details.append("PASS reset clears temporal state\n");
        } else details.append("FAIL reset temporal state\n");

        total++;
        ConnectomeEngine e7 = new ConnectomeEngine();
        e7.setSensors(0, 0, 0);
        e7.gate[0] = e7.gate[1] = e7.gate[2] = false;
        e7.preview();
        if ("IDLE".equals(e7.output) && e7.winner == -1) {
            pass++; details.append("PASS all gates blocked→IDLE\n");
        } else details.append("FAIL blocked system expected IDLE\n");

        return new SelfTestReport(pass, total, details.toString());
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static final class SelfTestReport {
        public final int passed;
        public final int total;
        public final String details;

        public SelfTestReport(int passed, int total, String details) {
            this.passed = passed;
            this.total = total;
            this.details = details;
        }

        public boolean allPassed() {
            return passed == total;
        }
    }
}
