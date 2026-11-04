package jp.tapmarker.weightspace;

/** Interpretable 8->5->4 ReLU model; no training loop or Android dependencies. */
public final class WeightSpacePolicy {
    public static final String[] FEATURES = {"game_foreground","mission_visible","claim_visible","ad_visible","recovery_pending","home_visible","unknown_overlay","external_app"};
    public static final String[] HIDDEN = {"claim_evidence","ad_or_external","home_evidence","uncertain_evidence","stale_recovery_evidence"};
    public static final String[] ACTIONS = {"WAIT","CLAIM","RECOVER","RESUME"};
    private static final double[][] W1 = {
      {1,1,1,0,0,0,0,0},
      {0,0,0,1,0,0,0,1},
      {1,0,0,0,0,1,0,0},
      {0,0,0,0,0,0,1,0},
      {0,0,0,0,1,0,0,0}
    };
    private static final double[] B1 = {-2.5,-0.5,-1.5,-0.5,-0.5};
    private static final double[][] FIXED_W2 = {
      {0,0,0,0,0},
      {10,-10,0,-30,0},
      {-8,6,-8,-30,0},
      {-8,-8,6,-30,0}
    };
    private static final double[] B2 = {0.05,0,0,0};
    private final double[][] w2 = new double[4][5];
    private boolean patched;
    public WeightSpacePolicy(boolean applyPatch) {
      for(int i=0;i<4;i++) System.arraycopy(FIXED_W2[i],0,w2[i],0,5);
      setPatched(applyPatch);
    }
    /** Directly edit exactly one output weight; no gradient, optimizer, or retraining. */
    public void setPatched(boolean value) { w2[1][4] = value ? 0.0 : -12.0; patched = value; }
    public boolean isPatched() { return patched; }
    public double editedWeight() { return w2[1][4]; }
    public static final class Result {
      public final String action;
      public final double[] hidden;
      public final double[] scores;
      Result(String action,double[] hidden,double[] scores) {this.action=action;this.hidden=hidden;this.scores=scores;}
    }
    public Result evaluate(boolean[] inputs) {
      if(inputs==null||inputs.length!=FEATURES.length) throw new IllegalArgumentException("8 input features required");
      double[] h = new double[5];
      for(int i=0;i<5;i++) {
        double s=B1[i];
        for(int j=0;j<8;j++) if(inputs[j]) s+=W1[i][j];
        h[i]=Math.max(0,s);
      }
      double[] scores = new double[4];
      int best=0;
      for(int i=0;i<4;i++) {
        double s=B2[i];
        for(int j=0;j<5;j++) s+=w2[i][j]*h[j];
        scores[i]=s;
        if(scores[i]>scores[best]) best=i;
      }
      return new Result(ACTIONS[best],h,scores);
    }
}
