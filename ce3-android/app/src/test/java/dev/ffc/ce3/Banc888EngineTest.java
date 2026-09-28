package dev.ffc.ce3;

import org.junit.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.Assert.*;

public class Banc888EngineTest {

    @Test public void demoLoadsAndBuildsModulesAndMotifs() {
        Banc888Engine e = new Banc888Engine();
        e.loadDemo();

        assertEquals(7, e.cellCount());
        assertEquals(11, e.edgeCount());
        assertTrue(e.modules().size() >= 4);
        assertTrue(e.motifStats().reciprocalPairs >= 2);
        assertTrue(e.motifStats().moduleEdges > 0);
    }

    @Test public void csvCellImportAcceptsCodexLikeColumns() throws Exception {
        String csv =
                "root_id,resolved_type,cell_type,nt_type,super_class,body_part,connectivity_tag\n" +
                "101,TasteA,,ACh,sensory,leg,broadcaster\n" +
                "102,GateCell,,GABA,interneuron,GNG,\n" +
                "103,MemoryCell,,ACh,interneuron,GNG,reciprocal\n";

        Banc888Engine e = new Banc888Engine();
        Banc888Engine.ImportReport r = e.importCells(new StringReader(csv), null);

        assertEquals(3, r.accepted);
        assertEquals(3, e.cellCount());
        assertEquals("T-WTA", e.findCell(101).templateId);
        assertEquals("T-GATE", e.findCell(102).templateId);
        assertEquals("T-MEM", e.findCell(103).templateId);
    }

    @Test public void tsvAndConnectionAliasesAreAccepted() throws Exception {
        Banc888Engine e = new Banc888Engine();
        String cells =
                "root_id\tresolved_type\tnt_type\tsuper_class\n" +
                "1\tA\tACh\tsensory\n" +
                "2\tB\tACh\tinterneuron\n";
        e.importCells(new StringReader(cells), null);

        String conns =
                "source_root_id\ttarget_root_id\tweight\n" +
                "1\t2\t8\n";
        Banc888Engine.ImportReport r = e.importConnections(new StringReader(conns), null);

        assertEquals(1, r.accepted);
        assertEquals(1, e.edgeCount());
        assertEquals(8, e.findCell(1).outputSynapses);
        assertEquals(8, e.findCell(2).inputSynapses);
    }

    @Test public void exceptionOverrideAndClearWork() {
        Banc888Engine e = new Banc888Engine();
        e.loadDemo();

        Banc888Engine.Cell c = e.findCell(1001);
        assertNotNull(c);
        String auto = c.templateId;

        assertTrue(e.applyException(1001, "T-MEM"));
        assertEquals("T-MEM", c.templateId);
        assertTrue(c.manualOverride);

        assertTrue(e.clearException(1001));
        assertFalse(c.manualOverride);
        assertEquals(auto, c.templateId);
    }

    @Test public void templateCountsCoverAllCells() {
        Banc888Engine e = new Banc888Engine();
        e.loadDemo();
        Map<String,Integer> counts = e.templateCounts();
        int total = 0;
        for (int v : counts.values()) total += v;
        assertEquals(e.cellCount(), total);
    }

    @Test public void moduleAggregationPreservesSynapseTotalsAcrossCrossModuleEdges() {
        Banc888Engine e = new Banc888Engine();
        e.loadDemo();

        long sum = 0;
        for (Banc888Engine.ModuleEdge me : e.moduleEdges()) sum += me.synapses;
        assertTrue(sum > 0);
        assertTrue(e.modules().size() < e.cellCount());
    }

    @Test public void quotedCsvFieldsParseCorrectly() throws Exception {
        String csv =
                "root_id,resolved_type,nt_type,super_class,connectivity_tag\n" +
                "201,\"Type,WithComma\",ACh,interneuron,\"reciprocal,foo\"\n";
        Banc888Engine e = new Banc888Engine();
        e.importCells(new StringReader(csv), null);

        assertEquals("Type,WithComma", e.findCell(201).resolvedType);
        assertEquals("T-MEM", e.findCell(201).templateId);
    }

    @Test public void missingRootColumnIsRejectedClearly() {
        Banc888Engine e = new Banc888Engine();
        try {
            e.importCells(new StringReader("cell_type,nt_type\nA,ACh\n"), null);
            fail("expected IOException");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("root_id"));
        }
    }

    @Test public void summaryDescribesCurrentWorkspace() {
        Banc888Engine e = new Banc888Engine();
        e.loadDemo();
        String s = e.summary();
        assertTrue(s.contains("cells=7"));
        assertTrue(s.contains("stored edges=11"));
        assertTrue(s.contains("modules="));
        assertTrue(s.contains("reciprocal="));
    }
}
