package jp.tapmarker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Called in place of the original named-control classifier on the same fresh app UI scan. */
final class KaitakuVideoNamedControls {
  static void apply(PhaseEngine e, String context, List<KaitakuNamedControls.Control> controls,
                    Map<String,float[]> out, int w, int h) {
    List<KaitakuVideoModalPolicy.Hit> hits=new ArrayList<>();
    if(controls!=null)for(KaitakuNamedControls.Control c:controls)
      if(c!=null)hits.add(new KaitakuVideoModalPolicy.Hit(c.label,c.bounds));
    KaitakuVideoModalPolicy.Decision d=KaitakuVideoModalPolicy.decide(context,hits);
    if(d.result==KaitakuVideoModalPolicy.Result.NONE) {
      KaitakuNamedControls.apply(e,context,controls,out,w,h);return;
    }
    // Freeze ordinary-home and mission actions while a modal is present.
    KaitakuNamedControls.clear(out);
    if(d.result==KaitakuVideoModalPolicy.Result.DISMISS) {
      // Existing close transition re-observes game before any further input.
      KaitakuActionEvidence.put(out,"close",d.bounds);
    }
    // HOLD never fabricates coordinates or taps the rewarded-ad action.
  }
  private KaitakuVideoNamedControls() {}
}
