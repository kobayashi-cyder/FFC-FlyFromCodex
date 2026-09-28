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
        Button connectome = buttonExact(activity.getWindow().getDecorView(), "コネクトーム");
        assertNotNull(connectome);
        connectome.performClick();

        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertNotNull(next.getComponent());
        assertEquals(BioSystemActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test public void quickStartTutorialExplainsTheBasicScreen() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Button guide = buttonExact(activity.getWindow().getDecorView(), "▶ この画面の使い方（30秒）");
        assertNotNull(guide);
        guide.performClick();

        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertNotNull(next.getComponent());
        assertEquals(QuickStartActivity.class.getName(), next.getComponent().getClassName());

        QuickStartActivity guideActivity = Robolectric.buildActivity(QuickStartActivity.class).setup().get();
        View guideRoot = guideActivity.getWindow().getDecorView();
        assertNotNull(textContains(guideRoot, "判定を見るだけならSTEPは不要です"));
        assertNotNull(textContains(guideRoot, "ENABLEは『通行許可』"));
    }

    @Test public void mainScreenRoutesToReusableBuilder() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Button builder = buttonExact(activity.getWindow().getDecorView(),
                "＋ 回路ビルダー（保存・コピー・Instance・Fork）");
        assertNotNull(builder);
        builder.performClick();

        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertNotNull(next.getComponent());
        assertEquals(BuilderActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test public void builderCanPlaceCopyAndCompileWtaBlock() {
        BuilderActivity activity = Robolectric.buildActivity(BuilderActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();

        Button wta = buttonExact(root, "+ WTA");
        assertNotNull(wta);
        wta.performClick();

        assertNotNull(textContains(root, "Winner-Take-All 1"));
        assertNotNull(textContains(root, "MORE >="));

        Button copy = buttonExact(root, "COPY");
        assertNotNull(copy);
        copy.performClick();

        assertNotNull(textContains(root, "blocks=2"));
        assertNotNull(textContains(root, "[COPY]"));
    }

    @Test public void builderEasySettingsExposeMeaningBeforeNumbers() {
        BuilderActivity activity = Robolectric.buildActivity(BuilderActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();

        buttonExact(root, "+ Custom").performClick();
        assertNotNull(textContains(root, "必要入力 — いつONになるか"));
        assertNotNull(buttonContains(root, "半分以上"));
        assertNotNull(textContains(root, "感度 — 入力をどれだけ強く受けるか"));
        assertNotNull(textContains(root, "抑制 — 他の候補をどれだけ抑えるか"));
        assertNotNull(textContains(root, "記憶 — 前の状態をどれだけ残すか"));
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

        buttonExact(root, "FORAGE").performClick();
        TextView forage = textContains(root, "OUTPUT = APPROACH");
        assertNotNull("FORAGE should select APPROACH", forage);
        assertTrue(forage.getText().toString().contains("WHY:"));

        buttonExact(root, "THREAT").performClick();
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

        Button reward = buttonExact(root, "REWARD +");
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
        buttonExact(root, "REWARD +").performClick();
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
