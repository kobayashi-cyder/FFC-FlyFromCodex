package dev.ffc.ce3;

import org.junit.Test;

import static org.junit.Assert.*;

public class BuilderEngineTest {

    @Test public void librarySeedsExpectedTemplates() {
        BuilderEngine e = new BuilderEngine();
        assertNotNull(e.findTemplate("T-AND"));
        assertNotNull(e.findTemplate("T-GATE"));
        assertNotNull(e.findTemplate("T-WTA"));
        assertNotNull(e.findTemplate("T-MEM"));
        assertNotNull(e.findTemplate("T-CUSTOM"));
    }

    @Test public void addInstanceSelectsNewBlock() {
        BuilderEngine e = new BuilderEngine();
        BuilderEngine.Block b = e.addInstance("T-WTA");
        assertEquals(1, e.blocks.size());
        assertSame(b, e.selectedBlock());
        assertTrue(b.linked);
        assertEquals("INSTANCE", b.relation);
        assertEquals("T-WTA", b.templateId);
    }

    @Test public void instanceSharesTemplateUpdates() {
        BuilderEngine e = new BuilderEngine();
        BuilderEngine.Block first = e.addInstance("T-WTA");
        BuilderEngine.Block second = e.instanceSelected();

        e.select(0);
        e.setInhibition(1.25);

        assertEquals(1.25, first.inhibition, 1e-9);
        assertEquals(1.25, second.inhibition, 1e-9);
        assertEquals(first.templateId, second.templateId);
    }

    @Test public void copyDetachesFromTemplate() {
        BuilderEngine e = new BuilderEngine();
        e.addInstance("T-MEM");
        BuilderEngine.Block copy = e.copySelected();
        assertFalse(copy.linked);
        assertEquals("COPY", copy.relation);

        double originalDecay = e.blocks.get(0).decay;
        e.setMemory(0.15);
        assertEquals(originalDecay, e.blocks.get(0).decay, 1e-9);
        assertEquals(0.15, copy.decay, 1e-9);
    }

    @Test public void forkCreatesIndependentTemplate() {
        BuilderEngine e = new BuilderEngine();
        BuilderEngine.Block original = e.addInstance("T-GATE");
        String oldTemplate = original.templateId;
        BuilderEngine.Block fork = e.forkSelected();

        assertEquals("FORK", fork.relation);
        assertTrue(fork.linked);
        assertNotEquals(oldTemplate, fork.templateId);

        e.setRequiredInput(BuilderEngine.RequiredInput.ALL);
        assertEquals(BuilderEngine.RequiredInput.HALF, original.requiredInput);
        assertEquals(BuilderEngine.RequiredInput.ALL, fork.requiredInput);
    }

    @Test public void easySettingsCompileToMoreLess() {
        BuilderEngine e = new BuilderEngine();
        e.addInstance("T-CUSTOM");
        e.setRequiredInput(BuilderEngine.RequiredInput.MOST);
        e.setSensitivity(1.0);
        e.setInhibition(0.6);
        e.setMemory(0.8);

        String compiled = e.compileSelected(8);
        assertTrue(compiled.contains("MORE >="));
        assertTrue(compiled.contains("threshold 6.00 of 8 inputs"));
        assertTrue(compiled.contains("input × 2.00"));
        assertTrue(compiled.contains("inhibition 0.60"));
        assertTrue(compiled.contains("MEMORY decay 0.80"));
    }

    @Test public void comparatorCanSwitchToLess() {
        BuilderEngine e = new BuilderEngine();
        e.addInstance("T-CUSTOM");
        e.toggleComparator();
        assertFalse(e.selectedBlock().more);
        assertTrue(e.compileSelected(8).contains("LESS <"));
    }

    @Test public void serializeRoundTripKeepsWorkspace() {
        BuilderEngine e = new BuilderEngine();
        e.addInstance("T-WTA");
        e.setInhibition(1.10);
        e.copySelected();
        e.setMemory(0.70);

        String data = e.serialize();

        BuilderEngine restored = new BuilderEngine();
        restored.deserialize(data);

        assertEquals(2, restored.blocks.size());
        assertEquals("INSTANCE", restored.blocks.get(0).relation);
        assertEquals("COPY", restored.blocks.get(1).relation);
        assertEquals(1.10, restored.blocks.get(0).inhibition, 1e-9);
        assertEquals(0.70, restored.blocks.get(1).decay, 1e-9);
    }

    @Test public void noSelectedBlockOperationsFailFast() {
        BuilderEngine e = new BuilderEngine();
        try {
            e.copySelected();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("No block"));
        }
    }
}
