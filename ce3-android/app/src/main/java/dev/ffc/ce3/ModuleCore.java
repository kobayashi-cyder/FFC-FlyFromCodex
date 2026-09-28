package dev.ffc.ce3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModuleCore {
    private ModuleCore() {}

    public static final class Spec {
        public long id;
        public String name;
        public String kind;
        public boolean template;
        public boolean linked;
        public long sourceId = -1;

        public int sensitivity = 50;   // 0..100 -> gain 0.5..2.0
        public int inhibition = 0;     // 0..100 -> bias 0..-1
        public int memory = 0;         // 0..100 -> recurrence 0..0.95
        public int required = 50;      // 0..100 -> threshold 0..1
        public boolean more = true;

        public double state = 0.0;

        public Spec(long id, String name, String kind) {
            this.id = id;
            this.name = name;
            this.kind = kind;
        }

        public Spec snapshot(long newId, String newName) {
            Spec s = new Spec(newId, newName, kind);
            s.sensitivity = sensitivity;
            s.inhibition = inhibition;
            s.memory = memory;
            s.required = required;
            s.more = more;
            s.template = false;
            s.linked = false;
            s.sourceId = -1;
            s.state = 0;
            return s;
        }
    }

    public static final class Result {
        public final double raw;
        public final double weighted;
        public final double threshold;
        public final boolean on;
        public final String text;

        public Result(double raw, double weighted, double threshold, boolean on, String text) {
            this.raw = raw;
            this.weighted = weighted;
            this.threshold = threshold;
            this.on = on;
            this.text = text;
        }
    }

    public static final class Library {
        public final ArrayList<Spec> items = new ArrayList<>();
        public final ArrayList<Long> canvas = new ArrayList<>();
        public long nextId = 1;

        public Spec create(String kind) {
            String label;
            Spec s;
            if ("GATE".equals(kind)) {
                label = "Gate";
                s = new Spec(nextId++, label + " " + nextId, kind);
                s.required = 50;
                s.sensitivity = 65;
            } else if ("MEMORY".equals(kind)) {
                label = "Memory";
                s = new Spec(nextId++, label + " " + nextId, kind);
                s.required = 50;
                s.memory = 80;
            } else if ("INTEGRATOR".equals(kind)) {
                label = "Integrator";
                s = new Spec(nextId++, label + " " + nextId, kind);
                s.required = 60;
                s.sensitivity = 55;
            } else if ("LESS".equals(kind)) {
                label = "Less";
                s = new Spec(nextId++, label + " " + nextId, kind);
                s.required = 50;
                s.more = false;
            } else {
                label = "Threshold";
                s = new Spec(nextId++, label + " " + nextId, "THRESHOLD");
                s.required = 50;
            }
            items.add(s);
            return s;
        }

        public Spec find(long id) {
            for (Spec s : items) if (s.id == id) return s;
            return null;
        }

        public Spec effective(Spec s) {
            if (s == null) return null;
            Spec cur = s;
            int guard = 0;
            while (cur.linked && cur.sourceId >= 0 && guard++ < 32) {
                Spec source = find(cur.sourceId);
                if (source == null || source.id == cur.id) break;
                cur = source;
            }
            return cur;
        }

        public Spec copy(long id) {
            Spec base = effective(find(id));
            if (base == null) return null;
            Spec out = base.snapshot(nextId++, base.name + " Copy");
            items.add(out);
            return out;
        }

        public Spec fork(long id) {
            Spec base = effective(find(id));
            if (base == null) return null;
            Spec out = base.snapshot(nextId++, base.name + " Fork");
            items.add(out);
            return out;
        }

        public Spec instance(long id) {
            Spec base = find(id);
            if (base == null) return null;
            Spec out = new Spec(nextId++, base.name + " Instance", base.kind);
            out.linked = true;
            out.sourceId = base.id;
            items.add(out);
            return out;
        }

        public Spec template(long id) {
            Spec base = effective(find(id));
            if (base == null) return null;
            Spec out = base.snapshot(nextId++, base.name + " Template");
            out.template = true;
            items.add(out);
            return out;
        }

        public void delete(long id) {
            items.removeIf(s -> s.id == id);
            canvas.removeIf(x -> x == id);
            for (Spec s : items) {
                if (s.linked && s.sourceId == id) {
                    s.linked = false;
                    s.sourceId = -1;
                }
            }
        }

        public Result evaluate(Spec target, double a, double b, boolean step) {
            if (target == null) return new Result(0,0,0,false,"No module");
            Spec p = effective(target);
            a = clamp(a, 0, 1);
            b = clamp(b, 0, 1);

            double raw;
            if ("GATE".equals(p.kind)) {
                raw = b >= 0.5 ? a : 0.0;
            } else if ("MEMORY".equals(p.kind)) {
                raw = a;
            } else {
                raw = (a + b) / 2.0;
            }

            double gain = 0.5 + 1.5 * (p.sensitivity / 100.0);
            double inhibit = p.inhibition / 100.0;
            double recur = 0.95 * (p.memory / 100.0);
            double threshold = p.required / 100.0;
            double weighted = gain * raw - inhibit + 0.35 * target.state;
            boolean on = p.more ? weighted >= threshold : weighted < threshold;

            if (step) {
                double drive = on ? 1.0 : raw;
                target.state = clamp(recur * target.state + (1.0 - recur) * drive, 0, 1);
                weighted = gain * raw - inhibit + 0.35 * target.state;
                on = p.more ? weighted >= threshold : weighted < threshold;
            }

            String text = String.format(Locale.US,
                    "%s | raw %.2f × gain %.2f - inhibit %.2f + memory %.2f = %.2f | %s %.2f → %s",
                    p.kind, raw, gain, inhibit, target.state,
                    weighted, p.more ? "MORE ≥" : "LESS <", threshold, on ? "ON" : "OFF");
            return new Result(raw, weighted, threshold, on, text);
        }

        public Result runCanvas(double inputA, double inputB, boolean step) {
            double signal = clamp(inputA,0,1);
            Result last = new Result(signal,signal,0,signal>=0.5,"Canvas empty");
            for (Long id : canvas) {
                Spec s = find(id);
                if (s == null) continue;
                last = evaluate(s, signal, inputB, step);
                signal = last.on ? 1.0 : 0.0;
            }
            return last;
        }

        public static double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }
}
