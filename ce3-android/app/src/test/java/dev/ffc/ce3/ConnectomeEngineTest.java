package dev.ffc.ce3;

import org.junit.Test;

import static org.junit.Assert.*;

public class ConnectomeEngineTest {
    private static final double EPS = 1e-9;

    @Test public void selfTestPasses() {
        ConnectomeEngine.SelfTestReport report = ConnectomeEngine.runSelfTest();
        assertTrue(report.details, report.allPassed());
        assertEquals(7, report.total);
    }

    @Test public void forageSelectsApproach() {
        ConnectomeEngine e = new ConnectomeEngine();
        e.setSensors(0.85, 0.10, 0.65);
        e.preview();
        assertEquals("APPROACH", e.output);
        assertEquals(0, e.winner);
    }

    @Test public void threatSelectsAvoid() {
        ConnectomeEngine e = new ConnectomeEngine();
        e.setSensors(0.20, 0.95, 0.45);
        e.preview();
        assertEquals("AVOID", e.output);
        assertEquals(1, e.winner);
    }

    @Test public void gateActuallyBlocksModule() {
        ConnectomeEngine e = new ConnectomeEngine();
        e.setSensors(0.85, 0.10, 0.65);
        e.gate[0] = false;
        e.preview();
        assertEquals(0.0, e.score[0], EPS);
        assertNotEquals("APPROACH", e.output);
        assertEquals("HOLD", e.output);
    }

    @Test public void recurrenceStoresAndResetClearsState() {
        ConnectomeEngine e = new ConnectomeEngine();
        e.setSensors(0.85, 0.10, 0.65);
        e.step();
        assertEquals(1, e.steps);
        assertTrue(e.memory[0] > 0.0);
        e.resetState();
        assertEquals(0, e.steps);
        assertEquals(0.0, e.memory[0], EPS);
        assertEquals(0.0, e.memory[1], EPS);
        assertEquals(0.0, e.memory[2], EPS);
    }

    @Test public void positiveRewardChangesOnlyWinningRow() {
        ConnectomeEngine e = new ConnectomeEngine();
        e.setSensors(0.85, 0.10, 0.65);
        e.preview();
        assertEquals(0, e.winner);

        double[][] before = copy(e.w);
        e.learn(+1.0);

        for (int i = 0; i < 3; i++) assertTrue(e.w[0][i] > before[0][i]);
        for (int row = 1; row < 3; row++)
            for (int i = 0; i < 3; i++)
                assertEquals(before[row][i], e.w[row][i], EPS);
    }

    @Test public void learningOffLeavesWeightsUnchanged() {
        ConnectomeEngine e = new ConnectomeEngine();
        e.setSensors(0.85, 0.10, 0.65);
        e.learningEnabled = false;
        double[][] before = copy(e.w);
        e.learn(+1.0);
        for (int row = 0; row < 3; row++)
            for (int i = 0; i < 3; i++)
                assertEquals(before[row][i], e.w[row][i], EPS);
    }

    @Test public void supervisorBiasChangesScoresPredictably() {
        ConnectomeEngine e = new ConnectomeEngine();
        e.setSensors(0.55, 0.55, 0.50);
        e.supervisor = 0;
        e.preview();
        double neutralApproach = e.score[0];
        double neutralAvoid = e.score[1];

        e.supervisor = 1;
        e.preview();
        assertTrue(e.score[0] >= neutralApproach);
        assertTrue(e.score[1] <= neutralAvoid);

        e.supervisor = 2;
        e.preview();
        assertTrue(e.score[1] >= neutralAvoid);
    }

    @Test public void allGatesBlockedProducesIdle() {
        ConnectomeEngine e = new ConnectomeEngine();
        e.gate[0] = e.gate[1] = e.gate[2] = false;
        e.preview();
        assertEquals(-1, e.winner);
        assertEquals("IDLE", e.output);
    }

    @Test public void exhaustiveSensorGridMaintainsCoreInvariants() {
        ConnectomeEngine e = new ConnectomeEngine();
        for (int ti = 0; ti <= 10; ti++) {
            for (int hi = 0; hi <= 10; hi++) {
                for (int ci = 0; ci <= 10; ci++) {
                    e.resetAll();
                    e.setSensors(ti / 10.0, hi / 10.0, ci / 10.0);
                    e.preview();

                    for (int m = 0; m < 3; m++) {
                        assertTrue(Double.isFinite(e.score[m]));
                        assertTrue(e.score[m] == 0.0 || e.score[m] >= e.threshold);
                    }

                    if (e.winner < 0) {
                        assertEquals("IDLE", e.output);
                    } else {
                        double max = e.score[e.winner];
                        assertTrue(max > 0.0);
                        for (int m = 0; m < 3; m++) assertTrue(max + EPS >= e.score[m]);
                        assertEquals(nameFor(e.winner), e.output);
                    }
                }
            }
        }
    }

    @Test public void sensorInputsAreClamped() {
        ConnectomeEngine e = new ConnectomeEngine();
        e.setSensors(-2.0, 3.0, 0.5);
        assertEquals(0.0, e.sensor[0], EPS);
        assertEquals(1.0, e.sensor[1], EPS);
        assertEquals(0.5, e.sensor[2], EPS);
    }

    private static double[][] copy(double[][] src) {
        double[][] dst = new double[src.length][];
        for (int i = 0; i < src.length; i++) dst[i] = src[i].clone();
        return dst;
    }

    private static String nameFor(int winner) {
        return winner == 0 ? "APPROACH" : winner == 1 ? "AVOID" : "HOLD";
    }
}
