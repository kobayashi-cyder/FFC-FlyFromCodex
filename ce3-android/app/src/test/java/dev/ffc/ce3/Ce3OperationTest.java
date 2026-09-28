package dev.ffc.ce3;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Ce3OperationTest {

    @Test public void mainScreenRoutesToConnectomeSystem() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Button connectome = buttonContains(activity.getWindow().getDecorView(), "CONNECTOME SYSTEM");
        assertNotNull(connectome);
        connectome.performClick();

        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertNotNull(next.getComponent());
        assertEquals(BioSystemActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test public void guidedHomeExplainsFourStepFlow() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();
        assertNotNull(textContains(root, "①回路を選ぶ"));
        assertNotNull(textContains(root, "論理出力"));
        assertNotNull(buttonContains(root, "OR"));
        assertNotNull(buttonContains(root, "入力A"));
        assertNotNull(buttonContains(root, "STEP"));
    }

    @Test public void andPresetAndInputsProduceOnResult() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();

        Button and = buttonContains(root, "AND");
        assertNotNull(and);
        and.performClick();

        root = activity.getWindow().getDecorView();
        buttonContains(root, "入力A").performClick();
        buttonContains(root, "入力B").performClick();

        TextView result = textContains(root, "論理出力: ON = 1");
        assertNotNull("AND with A=B=1 should show ON", result);
        assertTrue(result.getText().toString().contains("現在の回路: AND"));
    }

    @Test public void stepBuildsStateMemoryAndBlockHoldsIt() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();

        buttonContains(root, "OR").performClick();
        buttonContains(root, "入力A").performClick();
        buttonContains(root, "STEP").performClick();
        assertNotNull(textContains(root, "状態メモリ: ACTIVE"));

        buttonContains(root, "遷移許可").performClick();
        buttonContains(root, "STEP").performClick();
        assertNotNull(textContains(root, "BLOCK: 状態はそのまま"));
    }

    @Test public void inAppSelfTestIsVisibleAndPasses() {
        BioSystemActivity activity = Robolectric.buildActivity(BioSystemActivity.class).setup().get();
        Button selfTest = buttonExact(activity.getWindow().getDecorView(), "RUN SELF TEST");
        assertNotNull(selfTest);
        selfTest.performClick();

        assertNotNull(textContains(activity.getWindow().getDecorView(), "ALL PASS"));
        assertNotNull(textContains(activity.getWindow().getDecorView(), "7/7"));
    }

    @Test public void forageAndThreatPresetsProduceExpectedBehavior() {
        BioSystemActivity activity = Robolectric.buildActivity(BioSystemActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();

        buttonContains(root, "FORAGE").performClick();
        TextView forage = textContains(root, "OUTPUT = APPROACH");
        assertNotNull("FORAGE should select APPROACH", forage);
        assertTrue(forage.getText().toString().contains("WHY:"));

        buttonContains(root, "THREAT").performClick();
        assertNotNull("THREAT should select AVOID", textContains(root, "OUTPUT = AVOID"));
    }

    @Test public void approachGateChangesForageDecisionToHold() {
        BioSystemActivity activity = Robolectric.buildActivity(BioSystemActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();

        buttonExact(root, "FORAGE").performClick();
        assertNotNull(textContains(root, "OUTPUT = APPROACH"));

        Button approachGate = buttonContains(root, "APPROACH");
        assertNotNull(approachGate);
        assertTrue(approachGate.getText().toString().contains("EN"));
        approachGate.performClick();

        assertNotNull("Blocking approach should expose HOLD as winner",
                textContains(root, "OUTPUT = HOLD"));
        assertTrue(approachGate.getText().toString().contains("BLOCK"));
    }

    @Test public void rewardButtonChangesDisplayedWeightsWhenLearningIsOn() {
        BioSystemActivity activity = Robolectric.buildActivity(BioSystemActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();

        buttonExact(root, "FORAGE").performClick();
        TextView weights = textContains(root, "WEIGHT MATRIX");
        assertNotNull(weights);
        String before = weights.getText().toString();

        Button reward = buttonContains(root, "REWARD +");
        assertNotNull(reward);
        reward.performClick();

        String after = weights.getText().toString();
        assertNotEquals("Reward should change the winning module's displayed weights", before, after);
        assertTrue(after.contains("APPROACH"));
    }

    @Test public void learnOffPreventsWeightChangeThroughUi() {
        BioSystemActivity activity = Robolectric.buildActivity(BioSystemActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();

        buttonExact(root, "FORAGE").performClick();
        TextView weights = textContains(root, "WEIGHT MATRIX");
        Button toggle = buttonExact(root, "LEARN ON");
        assertNotNull(toggle);
        toggle.performClick();
        assertEquals("LEARN OFF", toggle.getText().toString());

        String before = weights.getText().toString();
        buttonContains(root, "REWARD +").performClick();
        String after = weights.getText().toString();
        assertEquals(before, after);
    }

    private static Button buttonExact(View root, String text) {
        View v = find(root, text, true, true);
        return v instanceof Button ? (Button) v : null;
    }

    private static Button buttonContains(View root, String text) {
        View v = find(root, text, false, true);
        return v instanceof Button ? (Button) v : null;
    }

    private static TextView textContains(View root, String text) {
        View v = find(root, text, false, false);
        return v instanceof TextView ? (TextView) v : null;
    }

    private static View find(View root, String text, boolean exact, boolean buttonOnly) {
        if (root instanceof TextView) {
            String value = ((TextView) root).getText().toString();
            boolean match = exact ? value.equals(text) : value.contains(text);
            if (match && (!buttonOnly || root instanceof Button)) return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text, exact, buttonOnly);
                if (found != null) return found;
            }
        }
        return null;
    }
}
