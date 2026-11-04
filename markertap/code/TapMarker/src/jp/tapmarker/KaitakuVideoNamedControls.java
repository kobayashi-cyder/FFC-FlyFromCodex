package jp.tapmarker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Called in place of the original named-control classifier on the same fresh app UI scan. */
final class KaitakuVideoNamedControls {
  static void apply(PhaseEngine e, String context, List<KaitakuNamedControls.Control> controls,
                    Map<String,float[]> out, int w, int h) {
    // Ordinary game frames dominate scanning. Avoid NFKC of the full
    // accessibility tree and per-control allocations when no modal keyword
    // could be present. Half-width variants still enter the full classifier.
    if(context==null||!(context.contains("コイン")||context.contains("ｺｲﾝ")||
       context.contains("タップ")||context.contains("ﾀｯﾌﾟ"))) {
      KaitakuActionPacer.reset(e);
      if(!KaitakuRewardNamedControls.tryApply(e,context,controls,out,w,h))
        KaitakuNamedControls.apply(e,context,controls,out,w,h);
      return;
    }
    List<KaitakuVideoModalPolicy.Hit> hits=new ArrayList<>();
    if(controls!=null)for(KaitakuNamedControls.Control c:controls)
      if(c!=null)hits.add(new KaitakuVideoModalPolicy.Hit(c.label,c.bounds));
    KaitakuVideoModalPolicy.Decision d=KaitakuVideoModalPolicy.decide(context,hits);
    if(d.result==KaitakuVideoModalPolicy.Result.NONE) {
      // A genuinely changed screen must make the next opt-out immediately tappable.
      KaitakuActionPacer.reset(e);
      if(!KaitakuRewardNamedControls.tryApply(e,context,controls,out,w,h))
        KaitakuNamedControls.apply(e,context,controls,out,w,h);return;
    }
    // Freeze ordinary-home and mission actions while a modal is present.
    KaitakuNamedControls.clear(out);
    if(d.result==KaitakuVideoModalPolicy.Result.DISMISS &&
       KaitakuActionPacer.allow(e,d.kind,d.bounds,System.nanoTime()/1000000L)) {
      // First opt-out is immediate; unchanged duplicates are backed off.
      // Candidate pacing does not replace action-result/frame verification.
      KaitakuActionEvidence.put(out,"close",d.bounds);
    }
    // HOLD never fabricates coordinates or taps the rewarded-ad action.
  }
  private KaitakuVideoNamedControls() {}
}
