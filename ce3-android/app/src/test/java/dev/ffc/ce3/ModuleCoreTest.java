package dev.ffc.ce3;

import org.junit.Test;

import static org.junit.Assert.*;

public class ModuleCoreTest {
    private static final double EPS=1e-9;

    @Test public void copyIsIndependent() {
        ModuleCore.Library lib=new ModuleCore.Library();
        ModuleCore.Spec src=lib.create("THRESHOLD");
        src.required=50;
        ModuleCore.Spec copy=lib.copy(src.id);
        assertNotNull(copy);
        src.required=90;
        assertEquals(50,copy.required);
        assertFalse(copy.linked);
    }

    @Test public void instanceTracksSourceParametersButKeepsState() {
        ModuleCore.Library lib=new ModuleCore.Library();
        ModuleCore.Spec src=lib.create("THRESHOLD");
        src.required=40;
        ModuleCore.Spec inst=lib.instance(src.id);
        assertNotNull(inst);
        assertTrue(inst.linked);
        assertEquals(40,lib.effective(inst).required);

        src.required=80;
        assertEquals(80,lib.effective(inst).required);

        lib.evaluate(inst,1.0,1.0,true);
        assertTrue(inst.state>0);
        assertEquals(0.0,src.state,EPS);
    }

    @Test public void forkStartsEqualThenDiverges() {
        ModuleCore.Library lib=new ModuleCore.Library();
        ModuleCore.Spec src=lib.create("MEMORY");
        src.memory=77;
        ModuleCore.Spec fork=lib.fork(src.id);
        assertNotNull(fork);
        assertEquals(77,fork.memory);
        src.memory=10;
        assertEquals(77,fork.memory);
        assertFalse(fork.linked);
    }

    @Test public void semanticSettingsMapToExpectedAdvancedValues() {
        ModuleCore.Library lib=new ModuleCore.Library();
        ModuleCore.Spec s=lib.create("THRESHOLD");
        s.sensitivity=100;
        s.inhibition=25;
        s.required=75;
        s.memory=100;

        ModuleCore.Result r=lib.evaluate(s,1.0,1.0,false);
        assertEquals(0.75,r.threshold,EPS);
        assertTrue(r.weighted>1.0);
        assertTrue(r.on);
    }

    @Test public void moreAndLessAreComplementaryAtSameThreshold() {
        ModuleCore.Library lib=new ModuleCore.Library();
        ModuleCore.Spec more=lib.create("THRESHOLD");
        more.required=50;
        more.sensitivity=50;
        more.inhibition=0;
        more.more=true;

        ModuleCore.Spec less=lib.copy(more.id);
        less.more=false;

        ModuleCore.Result rm=lib.evaluate(more,0.0,0.0,false);
        ModuleCore.Result rl=lib.evaluate(less,0.0,0.0,false);
        assertNotEquals(rm.on,rl.on);

        rm=lib.evaluate(more,1.0,1.0,false);
        rl=lib.evaluate(less,1.0,1.0,false);
        assertNotEquals(rm.on,rl.on);
    }

    @Test public void gateUsesBAsEnable() {
        ModuleCore.Library lib=new ModuleCore.Library();
        ModuleCore.Spec gate=lib.create("GATE");
        gate.required=30;
        gate.sensitivity=100;

        ModuleCore.Result off=lib.evaluate(gate,1.0,0.0,false);
        ModuleCore.Result on=lib.evaluate(gate,1.0,1.0,false);
        assertFalse(off.on);
        assertTrue(on.on);
    }

    @Test public void canvasReusesSavedModulesAsChain() {
        ModuleCore.Library lib=new ModuleCore.Library();
        ModuleCore.Spec a=lib.create("THRESHOLD");
        a.required=30;
        a.sensitivity=100;
        ModuleCore.Spec b=lib.copy(a.id);
        lib.canvas.add(a.id);
        lib.canvas.add(b.id);

        ModuleCore.Result r=lib.runCanvas(1.0,1.0,true);
        assertTrue(r.on);
        assertTrue(a.state>0);
        assertTrue(b.state>0);
    }

    @Test public void deletingSourceUnlinksInstancesSafely() {
        ModuleCore.Library lib=new ModuleCore.Library();
        ModuleCore.Spec src=lib.create("THRESHOLD");
        ModuleCore.Spec inst=lib.instance(src.id);
        lib.delete(src.id);
        assertFalse(inst.linked);
        assertEquals(-1,inst.sourceId);
        assertSame(inst,lib.effective(inst));
    }
}
