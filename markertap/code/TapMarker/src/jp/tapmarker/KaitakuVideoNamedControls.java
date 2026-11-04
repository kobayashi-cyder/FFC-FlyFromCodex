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
      if(!KaitakuRewardNamedControls.tryApply(e,context,controls,out,w,h)&&
         !(e.current.equals(KaitakuPreset.P+"missions")&&KaitakuActionEvidence.present(out,"get")))
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
      if(!KaitakuRewardNamedControls.tryApply(e,context,controls,out,w,h)&&
         !(e.current.equals(KaitakuPreset.P+"missions")&&KaitakuActionEvidence.present(out,"get")))
        KaitakuNamedControls.apply(e,context,controls,out,w,h);return;
    }
    // A known opt-out can be proved by the current screenshot even if
    // Canvas accessibility exposes the title but not its button. Do not
    // downgrade this independent visual evidence to a HOLD.
    if(d.result==KaitakuVideoModalPolicy.Result.HOLD&&
       (KaitakuActionEvidence.present(out,"close")||
        KaitakuActionEvidence.present(out,"tap-ad-start")))return;
    // Freeze ordinary-home and mission actions while a modal is present.
    KaitakuNamedControls.clear(out);
    if(d.result==KaitakuVideoModalPolicy.Result.DISMISS &&
       KaitakuActionPacer.allow(e,d.kind,d.bounds,System.nanoTime()/1000000L)) {
      // First opt-out is immediate; unchanged duplicates are backed off.
      // Candidate pacing does not replace action-result/frame verification.
      KaitakuActionEvidence.put(out,"close",d.bounds);
    }
    if(d.result==KaitakuVideoModalPolicy.Result.START_AD &&
       KaitakuActionPacer.allow(e,d.kind,d.bounds,System.nanoTime()/1000000L)) {
      KaitakuActionEvidence.put(out,"tap-ad-start",d.bounds);
    }
    // HOLD never fabricates coordinates. A named CTA or strictly verified
    // pixel geometry is required before any rewarded-ad start candidate.
  }
  private KaitakuVideoNamedControls() {}
}
