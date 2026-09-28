package dev.ffc.ce3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Banc888Engine {
    public static final int MAX_STORED_EDGES = 4_000_000;

    public interface Progress {
        void onProgress(String message, int rows);
    }

    public static class Cell {
        public long rootId;
        public String resolvedType = "";
        public String cellType = "";
        public String ntType = "";
        public String superClass = "";
        public String bodyPart = "";
        public String connectivityTag = "";
        public String templateId = "T-CUSTOM";
        public boolean manualOverride = false;
        public long inputSynapses = 0;
        public long outputSynapses = 0;

        Cell(long rootId) { this.rootId = rootId; }

        public String bestType() {
            if (!resolvedType.isEmpty()) return resolvedType;
            if (!cellType.isEmpty()) return cellType;
            if (!superClass.isEmpty()) return superClass;
            return "untyped";
        }
    }

    public static class Module {
        public int index;
        public String key;
        public String label;
        public String templateId;
        public int cellCount;
        public long inputSynapses;
        public long outputSynapses;

        Module(int index, String key, String label, String templateId) {
            this.index = index;
            this.key = key;
            this.label = label;
            this.templateId = templateId;
        }
    }

    public static class ModuleEdge {
        public int preModule;
        public int postModule;
        public long synapses;
        public int rows;

        ModuleEdge(int preModule, int postModule) {
            this.preModule = preModule;
            this.postModule = postModule;
        }
    }

    public static class ImportReport {
        public int rows;
        public int accepted;
        public int rejected;
        public int discardedEdges;
        public String message;

        @Override public String toString() {
            return message + " rows=" + rows + " accepted=" + accepted +
                    " rejected=" + rejected +
                    (discardedEdges > 0 ? " discardedEdges=" + discardedEdges : "");
        }
    }

    public static class MotifStats {
        public int moduleNodes;
        public int moduleEdges;
        public int reciprocalPairs;
        public int threeCycles;
        public int feedForwardLoops;

        @Override public String toString() {
            return "module nodes=" + moduleNodes +
                    ", edges=" + moduleEdges +
                    ", reciprocal=" + reciprocalPairs +
                    ", 3-cycles=" + threeCycles +
                    ", feed-forward=" + feedForwardLoops;
        }
    }

    private final ArrayList<Cell> cells = new ArrayList<>();
    private final HashMap<Long,Integer> cellIndex = new HashMap<>();

    private int[] pre = new int[4096];
    private int[] post = new int[4096];
    private int[] syn = new int[4096];
    private int edgeCount = 0;
    private int discardedEdges = 0;

    private final ArrayList<Module> modules = new ArrayList<>();
    private final ArrayList<ModuleEdge> moduleEdges = new ArrayList<>();
    private final HashMap<String,Integer> moduleIndexByKey = new HashMap<>();
    private MotifStats motifStats = new MotifStats();

    public int cellCount() { return cells.size(); }
    public int edgeCount() { return edgeCount; }
    public int discardedEdges() { return discardedEdges; }
    public List<Cell> cells() { return cells; }
    public List<Module> modules() { return modules; }
    public List<ModuleEdge> moduleEdges() { return moduleEdges; }
    public MotifStats motifStats() { return motifStats; }

    public void clearAll() {
        cells.clear();
        cellIndex.clear();
        edgeCount = 0;
        discardedEdges = 0;
        modules.clear();
        moduleEdges.clear();
        moduleIndexByKey.clear();
        motifStats = new MotifStats();
    }

    public Cell findCell(long rootId) {
        Integer idx = cellIndex.get(rootId);
        return idx == null ? null : cells.get(idx);
    }

    private Cell getOrCreate(long rootId) {
        Integer idx = cellIndex.get(rootId);
        if (idx != null) return cells.get(idx);
        Cell c = new Cell(rootId);
        int n = cells.size();
        cells.add(c);
        cellIndex.put(rootId, n);
        return c;
    }

    public ImportReport importCells(Reader reader, Progress progress) throws IOException {
        BufferedReader br = reader instanceof BufferedReader ? (BufferedReader)reader : new BufferedReader(reader);
        String headerLine = br.readLine();
        ImportReport report = new ImportReport();
        report.message = "cells";
        if (headerLine == null) return report;

        char delim = detectDelimiter(headerLine);
        List<String> header = parseLine(headerLine, delim);
        HeaderMap h = new HeaderMap(header);
        int idCol = h.first("root_id","rootid","id","cell_id");
        if (idCol < 0) throw new IOException("Cells CSV: root_id column not found");

        int resolvedCol = h.first("resolved_type","resolvedtype");
        int typeCol = h.first("cell_type","celltype","type");
        int ntCol = h.first("nt_type","neurotransmitter","nt");
        int superCol = h.first("super_class","superclass");
        int bodyCol = h.first("body_part","bodypart");
        int tagCol = h.first("connectivity_tag","connectivity_tags","tag");

        String line;
        while ((line = br.readLine()) != null) {
            report.rows++;
            List<String> row = parseLine(line, delim);
            long id = parseLong(get(row,idCol), Long.MIN_VALUE);
            if (id == Long.MIN_VALUE) {
                report.rejected++;
                continue;
            }
            Cell c = getOrCreate(id);
            if (resolvedCol >= 0) c.resolvedType = clean(get(row,resolvedCol));
            if (typeCol >= 0) c.cellType = clean(get(row,typeCol));
            if (ntCol >= 0) c.ntType = clean(get(row,ntCol));
            if (superCol >= 0) c.superClass = clean(get(row,superCol));
            if (bodyCol >= 0) c.bodyPart = clean(get(row,bodyCol));
            if (tagCol >= 0) c.connectivityTag = clean(get(row,tagCol));
            if (!c.manualOverride) c.templateId = autoTemplateFor(c);
            report.accepted++;
            if (progress != null && report.rows % 10000 == 0)
                progress.onProgress("cells " + report.rows, report.rows);
        }
        buildModules();
        return report;
    }

    public ImportReport importConnections(Reader reader, Progress progress) throws IOException {
        BufferedReader br = reader instanceof BufferedReader ? (BufferedReader)reader : new BufferedReader(reader);
        String headerLine = br.readLine();
        ImportReport report = new ImportReport();
        report.message = "connections";
        if (headerLine == null) return report;

        char delim = detectDelimiter(headerLine);
        List<String> header = parseLine(headerLine, delim);
        HeaderMap h = new HeaderMap(header);
        int preCol = h.first("pre_root_id","pre_root","pre","source_root_id","source","pre_pt_root_id");
        int postCol = h.first("post_root_id","post_root","post","target_root_id","target","post_pt_root_id");
        int synCol = h.first("synapse_count","syn_count","synapses","weight","n_synapses");
        if (preCol < 0 || postCol < 0)
            throw new IOException("Connections CSV: pre/post root columns not found");

        String line;
        while ((line = br.readLine()) != null) {
            report.rows++;
            List<String> row = parseLine(line, delim);
            long preId = parseLong(get(row,preCol), Long.MIN_VALUE);
            long postId = parseLong(get(row,postCol), Long.MIN_VALUE);
            int count = synCol >= 0 ? (int)Math.max(1, parseLong(get(row,synCol), 1)) : 1;
            if (preId == Long.MIN_VALUE || postId == Long.MIN_VALUE || preId == postId) {
                report.rejected++;
                continue;
            }

            Cell a = getOrCreate(preId);
            Cell b = getOrCreate(postId);
            a.outputSynapses += count;
            b.inputSynapses += count;

            if (edgeCount < MAX_STORED_EDGES) {
                ensureEdgeCapacity(edgeCount + 1);
                pre[edgeCount] = cellIndex.get(preId);
                post[edgeCount] = cellIndex.get(postId);
                syn[edgeCount] = count;
                edgeCount++;
                report.accepted++;
            } else {
                discardedEdges++;
                report.discardedEdges++;
            }

            if (progress != null && report.rows % 25000 == 0)
                progress.onProgress("connections " + report.rows, report.rows);
        }

        for (Cell c : cells) if (!c.manualOverride && "T-CUSTOM".equals(c.templateId))
            c.templateId = autoTemplateFor(c);
        buildModules();
        analyzeMotifs();
        return report;
    }

    public String autoTemplateFor(Cell c) {
        String nt = norm(c.ntType);
        String tag = norm(c.connectivityTag);
        String sc = norm(c.superClass);

        if (tag.contains("attractor") || tag.contains("reciprocal") || tag.contains("3-cycle"))
            return "T-MEM";
        if (nt.contains("gaba") || nt.contains("glutamate") && tag.contains("inhib"))
            return "T-GATE";
        if (tag.contains("integrator"))
            return "T-AND";
        if (tag.contains("broadcaster") || tag.contains("winner"))
            return "T-WTA";
        if (sc.contains("sensory") || sc.contains("motor"))
            return "T-GATE";
        return "T-CUSTOM";
    }

    public boolean applyException(long rootId, String templateId) {
        Cell c = findCell(rootId);
        if (c == null) return false;
        c.templateId = templateId;
        c.manualOverride = true;
        buildModules();
        analyzeMotifs();
        return true;
    }

    public boolean clearException(long rootId) {
        Cell c = findCell(rootId);
        if (c == null) return false;
        c.manualOverride = false;
        c.templateId = autoTemplateFor(c);
        buildModules();
        analyzeMotifs();
        return true;
    }

    public int autoAssignAll() {
        int changed = 0;
        for (Cell c : cells) {
            if (c.manualOverride) continue;
            String next = autoTemplateFor(c);
            if (!next.equals(c.templateId)) {
                c.templateId = next;
                changed++;
            }
        }
        buildModules();
        analyzeMotifs();
        return changed;
    }

    public void buildModules() {
        modules.clear();
        moduleEdges.clear();
        moduleIndexByKey.clear();

        int[] moduleOfCell = new int[cells.size()];
        Arrays.fill(moduleOfCell, -1);

        for (int i=0;i<cells.size();i++) {
            Cell c = cells.get(i);
            String label = c.bestType();
            String key = c.templateId + "|" + label;
            Integer mi = moduleIndexByKey.get(key);
            if (mi == null) {
                mi = modules.size();
                modules.add(new Module(mi,key,label,c.templateId));
                moduleIndexByKey.put(key,mi);
            }
            Module m = modules.get(mi);
            m.cellCount++;
            m.inputSynapses += c.inputSynapses;
            m.outputSynapses += c.outputSynapses;
            moduleOfCell[i] = mi;
        }

        LinkedHashMap<Long,ModuleEdge> agg = new LinkedHashMap<>();
        for (int e=0;e<edgeCount;e++) {
            int a = moduleOfCell[pre[e]];
            int b = moduleOfCell[post[e]];
            if (a < 0 || b < 0 || a == b) continue;
            long k = pairKey(a,b);
            ModuleEdge me = agg.get(k);
            if (me == null) {
                me = new ModuleEdge(a,b);
                agg.put(k,me);
            }
            me.synapses += syn[e];
            me.rows++;
        }
        moduleEdges.addAll(agg.values());
    }

    public MotifStats analyzeMotifs() {
        MotifStats s = new MotifStats();
        s.moduleNodes = modules.size();
        s.moduleEdges = moduleEdges.size();

        int n = modules.size();
        if (n == 0) {
            motifStats = s;
            return s;
        }

        boolean[][] adj;
        if (n <= 1200) {
            adj = new boolean[n][n];
            for (ModuleEdge e : moduleEdges) adj[e.preModule][e.postModule] = true;
        } else {
            // Avoid a huge dense matrix; motif scan falls back to reciprocal only through edge map.
            adj = null;
        }

        HashMap<Long,Boolean> edgeSet = new HashMap<>();
        for (ModuleEdge e : moduleEdges) edgeSet.put(pairKey(e.preModule,e.postModule), Boolean.TRUE);

        for (ModuleEdge e : moduleEdges) {
            if (e.preModule < e.postModule &&
                    edgeSet.containsKey(pairKey(e.postModule,e.preModule)))
                s.reciprocalPairs++;
        }

        if (adj != null) {
            // Count unique directed 3-cycles once by requiring a to be the smallest index.
            for (int a=0;a<n;a++) {
                for (int b=0;b<n;b++) if (a!=b && adj[a][b]) {
                    for (int c=0;c<n;c++) {
                        if (c==a || c==b) continue;
                        if (a < b && a < c && adj[b][c] && adj[c][a]) s.threeCycles++;
                        if (a < b && b < c && adj[a][c] && adj[b][c]) s.feedForwardLoops++;
                    }
                }
            }
        }

        motifStats = s;
        return s;
    }

    public Map<String,Integer> templateCounts() {
        LinkedHashMap<String,Integer> out = new LinkedHashMap<>();
        out.put("T-GATE",0);
        out.put("T-MEM",0);
        out.put("T-AND",0);
        out.put("T-WTA",0);
        out.put("T-CUSTOM",0);
        for (Cell c : cells) out.put(c.templateId, out.containsKey(c.templateId) ? out.get(c.templateId)+1 : 1);
        return out;
    }

    public String summary() {
        Map<String,Integer> c = templateCounts();
        return "BANC/888 workspace\n" +
                "cells=" + cells.size() +
                "  stored edges=" + edgeCount +
                (discardedEdges>0 ? "  discarded=" + discardedEdges : "") +
                "\nmodules=" + modules.size() +
                "  module edges=" + moduleEdges.size() +
                "\nGate=" + c.get("T-GATE") +
                "  Memory=" + c.get("T-MEM") +
                "  AND-like=" + c.get("T-AND") +
                "  WTA=" + c.get("T-WTA") +
                "  Custom=" + c.get("T-CUSTOM") +
                "\n" + motifStats.toString();
    }

    public String modulesText(int limit) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(limit, modules.size());
        for (int i=0;i<n;i++) {
            Module m = modules.get(i);
            sb.append(String.format(Locale.US,
                    "%d. %s  [%s]  cells=%d  in=%d  out=%d\n",
                    i+1,m.label,m.templateId,m.cellCount,m.inputSynapses,m.outputSynapses));
        }
        if (modules.size() > n) sb.append("… +").append(modules.size()-n).append(" modules");
        return sb.toString();
    }

    public String exceptionInfo(long rootId) {
        Cell c = findCell(rootId);
        if (c == null) return "root_id not found";
        return "root_id=" + c.rootId +
                "\ntype=" + c.bestType() +
                "\nNT=" + c.ntType +
                "\nsuper_class=" + c.superClass +
                "\ntag=" + c.connectivityTag +
                "\ntemplate=" + c.templateId +
                (c.manualOverride ? " (manual override)" : " (auto)");
    }

    public void loadDemo() {
        clearAll();
        String[] rows = {
                "1001,TasteA,sensory,ACh,leg,broadcaster",
                "1002,TasteB,sensory,ACh,leg,",
                "1003,GateGABA,interneuron,GABA,GNG,reciprocal",
                "1004,Integrator,interneuron,ACh,GNG,integrator",
                "1005,MemoryLoop,interneuron,ACh,GNG,attractor",
                "1006,MotorA,motor,ACh,leg,",
                "1007,MotorB,motor,ACh,leg,"
        };
        for (String r : rows) {
            String[] p = r.split(",",-1);
            Cell c = getOrCreate(Long.parseLong(p[0]));
            c.resolvedType=p[1];
            c.superClass=p[2];
            c.ntType=p[3];
            c.bodyPart=p[4];
            c.connectivityTag=p[5];
            c.templateId=autoTemplateFor(c);
        }
        long[][] es = {
                {1001,1004,12},{1002,1004,9},{1004,1003,7},{1003,1004,5},
                {1004,1005,11},{1005,1004,8},{1005,1006,10},{1004,1006,6},
                {1003,1007,9},{1007,1003,4},{1006,1007,3}
        };
        for (long[] e : es) addDemoEdge(e[0],e[1],(int)e[2]);
        buildModules();
        analyzeMotifs();
    }

    private void addDemoEdge(long preId,long postId,int count) {
        Cell a=getOrCreate(preId), b=getOrCreate(postId);
        a.outputSynapses+=count;
        b.inputSynapses+=count;
        ensureEdgeCapacity(edgeCount+1);
        pre[edgeCount]=cellIndex.get(preId);
        post[edgeCount]=cellIndex.get(postId);
        syn[edgeCount]=count;
        edgeCount++;
    }

    private void ensureEdgeCapacity(int needed) {
        if (needed <= pre.length) return;
        int next = Math.min(MAX_STORED_EDGES, Math.max(needed, pre.length * 2));
        pre = Arrays.copyOf(pre,next);
        post = Arrays.copyOf(post,next);
        syn = Arrays.copyOf(syn,next);
    }

    private static long pairKey(int a,int b) {
        return ((long)a << 32) ^ (b & 0xffffffffL);
    }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase(Locale.US); }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static String get(List<String> row,int col) { return col>=0 && col<row.size() ? row.get(col) : ""; }

    private static long parseLong(String s,long fallback) {
        try { return Long.parseLong(s.trim()); }
        catch (Exception e) {
            try { return (long)Double.parseDouble(s.trim()); }
            catch (Exception ignored) { return fallback; }
        }
    }

    private static char detectDelimiter(String line) {
        int tabs=0, commas=0;
        boolean quote=false;
        for (int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if (c=='"') quote=!quote;
            else if (!quote && c=='\t') tabs++;
            else if (!quote && c==',') commas++;
        }
        return tabs>commas ? '\t' : ',';
    }

    static List<String> parseLine(String line,char delimiter) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quote = false;
        for (int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if (c=='"') {
                if (quote && i+1<line.length() && line.charAt(i+1)=='"') {
                    sb.append('"');
                    i++;
                } else quote=!quote;
            } else if (c==delimiter && !quote) {
                out.add(sb.toString());
                sb.setLength(0);
            } else sb.append(c);
        }
        out.add(sb.toString());
        return out;
    }

    static class HeaderMap {
        final Map<String,Integer> map = new HashMap<>();
        HeaderMap(List<String> header) {
            for (int i=0;i<header.size();i++) {
                String k=header.get(i).trim().toLowerCase(Locale.US)
                        .replace(" ","_").replace("-","_");
                map.put(k,i);
            }
        }
        int first(String... names) {
            for (String n:names) {
                Integer i=map.get(n);
                if (i!=null) return i;
            }
            return -1;
        }
    }
}
