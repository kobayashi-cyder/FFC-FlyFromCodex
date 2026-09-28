package dev.ffc.ce3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BuilderEngine {

    public enum RequiredInput {
        ANY("1つでも", 0.25),
        HALF("半分以上", 0.50),
        MOST("かなり", 0.75),
        ALL("全部", 1.00);

        public final String label;
        public final double ratio;
        RequiredInput(String label, double ratio) {
            this.label = label;
            this.ratio = ratio;
        }
    }

    public static class Template {
        public final String id;
        public String name;
        public String kind;
        public boolean more = true;
        public double thresholdRatio = 0.50;
        public double gain = 1.0;
        public double inhibition = 0.0;
        public double decay = 0.25;
        public double bias = 0.0;
        public RequiredInput requiredInput = RequiredInput.HALF;

        Template(String id, String name, String kind) {
            this.id = id;
            this.name = name;
            this.kind = kind;
        }

        Template copyAs(String newId, String newName) {
            Template t = new Template(newId, newName, kind);
            t.more = more;
            t.thresholdRatio = thresholdRatio;
            t.gain = gain;
            t.inhibition = inhibition;
            t.decay = decay;
            t.bias = bias;
            t.requiredInput = requiredInput;
            return t;
        }
    }

    public static class Block {
        public final String id;
        public String name;
        public String templateId;
        public String relation; // INSTANCE / COPY / FORK
        public boolean linked;

        public boolean more;
        public double thresholdRatio;
        public double gain;
        public double inhibition;
        public double decay;
        public double bias;
        public RequiredInput requiredInput;

        Block(String id, String name, Template t, String relation, boolean linked) {
            this.id = id;
            this.name = name;
            this.templateId = t.id;
            this.relation = relation;
            this.linked = linked;
            pullFrom(t);
        }

        void pullFrom(Template t) {
            this.more = t.more;
            this.thresholdRatio = t.thresholdRatio;
            this.gain = t.gain;
            this.inhibition = t.inhibition;
            this.decay = t.decay;
            this.bias = t.bias;
            this.requiredInput = t.requiredInput;
        }
    }

    public final List<Template> templates = new ArrayList<>();
    public final List<Block> blocks = new ArrayList<>();
    public int selected = -1;

    private int nextTemplate = 100;
    private int nextBlock = 1;

    public BuilderEngine() {
        seedTemplates();
    }

    private void seedTemplates() {
        Template and = new Template("T-AND", "AND-like", "LOGIC");
        and.more = true;
        and.requiredInput = RequiredInput.ALL;
        and.thresholdRatio = 1.0;
        templates.add(and);

        Template gate = new Template("T-GATE", "Gate", "GATING");
        gate.more = true;
        gate.requiredInput = RequiredInput.HALF;
        gate.thresholdRatio = 0.50;
        gate.gain = 1.0;
        templates.add(gate);

        Template wta = new Template("T-WTA", "Winner-Take-All", "COMPETITION");
        wta.more = true;
        wta.requiredInput = RequiredInput.HALF;
        wta.thresholdRatio = 0.50;
        wta.inhibition = 0.80;
        templates.add(wta);

        Template memory = new Template("T-MEM", "Memory", "RECURRENT");
        memory.more = true;
        memory.requiredInput = RequiredInput.HALF;
        memory.thresholdRatio = 0.50;
        memory.decay = 0.80;
        templates.add(memory);

        Template custom = new Template("T-CUSTOM", "Custom Cell", "CUSTOM");
        templates.add(custom);
    }

    public Template findTemplate(String id) {
        for (Template t : templates) if (t.id.equals(id)) return t;
        return null;
    }

    public Block selectedBlock() {
        return selected >= 0 && selected < blocks.size() ? blocks.get(selected) : null;
    }

    public Block addInstance(String templateId) {
        Template t = findTemplate(templateId);
        if (t == null) throw new IllegalArgumentException("Unknown template " + templateId);
        Block b = new Block(nextBlockId(), t.name + " " + (blocks.size() + 1), t, "INSTANCE", true);
        blocks.add(b);
        selected = blocks.size() - 1;
        return b;
    }

    public Block copySelected() {
        Block src = requireSelected();
        Template temp = new Template("DETACHED-" + nextTemplate++, src.name + " copy", src.relation);
        writeBlockToTemplate(src, temp);
        Block b = new Block(nextBlockId(), src.name + " copy", temp, "COPY", false);
        b.templateId = src.templateId;
        blocks.add(b);
        selected = blocks.size() - 1;
        return b;
    }

    public Block instanceSelected() {
        Block src = requireSelected();
        Template t = findTemplate(src.templateId);
        if (t == null || !src.linked) {
            Template base = new Template("T-" + nextTemplate++, src.name + " template", "CUSTOM");
            writeBlockToTemplate(src, base);
            templates.add(base);
            src.templateId = base.id;
            src.linked = true;
            src.relation = "INSTANCE";
            t = base;
        }
        Block b = new Block(nextBlockId(), t.name + " instance", t, "INSTANCE", true);
        blocks.add(b);
        selected = blocks.size() - 1;
        return b;
    }

    public Block forkSelected() {
        Block src = requireSelected();
        Template fork = new Template("T-" + nextTemplate++, src.name + " fork", "FORK");
        writeBlockToTemplate(src, fork);
        templates.add(fork);
        Block b = new Block(nextBlockId(), fork.name, fork, "FORK", true);
        blocks.add(b);
        selected = blocks.size() - 1;
        return b;
    }

    public void deleteSelected() {
        requireSelected();
        blocks.remove(selected);
        if (blocks.isEmpty()) selected = -1;
        else selected = Math.min(selected, blocks.size() - 1);
    }

    public void select(int index) {
        if (index < 0 || index >= blocks.size()) throw new IndexOutOfBoundsException();
        selected = index;
    }

    public void setRequiredInput(RequiredInput mode) {
        Block b = requireSelected();
        b.requiredInput = mode;
        b.thresholdRatio = mode.ratio;
        propagateIfLinked(b);
    }

    public void setSensitivity(double value) {
        Block b = requireSelected();
        value = clamp(value, 0, 1);
        b.gain = 0.50 + 1.50 * value;
        propagateIfLinked(b);
    }

    public void setInhibition(double value) {
        Block b = requireSelected();
        b.inhibition = clamp(value, 0, 1.50);
        propagateIfLinked(b);
    }

    public void setMemory(double value) {
        Block b = requireSelected();
        b.decay = clamp(value, 0, 0.95);
        propagateIfLinked(b);
    }

    public void toggleComparator() {
        Block b = requireSelected();
        b.more = !b.more;
        propagateIfLinked(b);
    }

    public void setBias(double value) {
        Block b = requireSelected();
        b.bias = clamp(value, -1, 1);
        propagateIfLinked(b);
    }

    public void setThresholdRatio(double value) {
        Block b = requireSelected();
        b.thresholdRatio = clamp(value, 0, 1);
        propagateIfLinked(b);
    }

    public String compileSelected(int inputCount) {
        Block b = requireSelected();
        int n = Math.max(1, inputCount);
        double threshold = b.thresholdRatio * n;
        return String.format(Locale.US,
                "%s(%s)\nSUM = Σ(input × %.2f) - inhibition %.2f + bias %+.2f\n%s threshold %.2f of %d inputs\nMEMORY decay %.2f\nRELATION %s%s",
                b.name, b.id, b.gain, b.inhibition, b.bias,
                b.more ? "MORE >=" : "LESS <",
                threshold, n, b.decay, b.relation,
                b.linked ? " / linked " + b.templateId : " / detached");
    }

    public String workspaceSummary() {
        return "templates=" + templates.size() + ", blocks=" + blocks.size() + ", selected=" + selected;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(escape(b.name)).append("|")
              .append(escape(b.templateId)).append("|")
              .append(b.relation).append("|")
              .append(b.linked ? "1" : "0").append("|")
              .append(b.more ? "1" : "0").append("|")
              .append(fmt(b.thresholdRatio)).append("|")
              .append(fmt(b.gain)).append("|")
              .append(fmt(b.inhibition)).append("|")
              .append(fmt(b.decay)).append("|")
              .append(fmt(b.bias)).append("|")
              .append(b.requiredInput.name());
        }
        return sb.toString();
    }

    public void deserialize(String data) {
        blocks.clear();
        selected = -1;
        if (data == null || data.trim().isEmpty()) return;
        String[] lines = data.split("\n");
        for (String line : lines) {
            String[] p = line.split("\\|", -1);
            if (p.length != 11) continue;
            Template base = findTemplate(unescape(p[1]));
            if (base == null) {
                base = new Template("T-" + nextTemplate++, unescape(p[0]) + " restored", "RESTORED");
                templates.add(base);
            }
            Block b = new Block(nextBlockId(), unescape(p[0]), base, p[2], "1".equals(p[3]));
            b.templateId = unescape(p[1]);
            b.more = "1".equals(p[4]);
            b.thresholdRatio = parse(p[5], 0.5);
            b.gain = parse(p[6], 1.0);
            b.inhibition = parse(p[7], 0.0);
            b.decay = parse(p[8], 0.25);
            b.bias = parse(p[9], 0.0);
            try { b.requiredInput = RequiredInput.valueOf(p[10]); }
            catch (Exception ignored) { b.requiredInput = RequiredInput.HALF; }
            blocks.add(b);
        }
        if (!blocks.isEmpty()) selected = 0;
    }

    private void propagateIfLinked(Block b) {
        if (!b.linked) return;
        Template t = findTemplate(b.templateId);
        if (t == null) return;
        writeBlockToTemplate(b, t);
        for (Block other : blocks) {
            if (other != b && other.linked && other.templateId.equals(t.id)) other.pullFrom(t);
        }
    }

    private Block requireSelected() {
        Block b = selectedBlock();
        if (b == null) throw new IllegalStateException("No block selected");
        return b;
    }

    private void writeBlockToTemplate(Block b, Template t) {
        t.more = b.more;
        t.thresholdRatio = b.thresholdRatio;
        t.gain = b.gain;
        t.inhibition = b.inhibition;
        t.decay = b.decay;
        t.bias = b.bias;
        t.requiredInput = b.requiredInput;
    }

    private String nextBlockId() { return "B-" + nextBlock++; }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    private static double parse(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }

    private static String escape(String s) { return s.replace("|", "/").replace("\n", " "); }
    private static String unescape(String s) { return s; }
}
