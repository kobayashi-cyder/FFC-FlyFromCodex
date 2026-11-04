package jp.tapmarker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Candidate only; requires a fresh app-scoped accessibility probe and original
 * screenshot/input preflight in the installed DEX for an actual gesture.
 */
final class KaitakuRewardNamedControls {
  static void apply(PhaseEngine engine,String context,List<KaitakuNamedControls.Control> controls,
                    Map<String,float[]> out,int width,int height){
    if(!tryApply(engine,context,controls,out,width,height))
      KaitakuVideoNamedControls.apply(engine,context,controls,out,width,height);
  }
  /** Called only when the video modal classifier has no active modal. */
  static boolean tryApply(PhaseEngine engine,String context,List<KaitakuNamedControls.Control> controls,
                    Map<String,float[]> out,int width,int height){
    if(engine==null||out==null||engine.current==null||!engine.current.startsWith("kaitaku-default-"))
      return false;
    List<KaitakuRewardClaimPolicy.Hit> hits=new ArrayList<>();
    if(controls!=null)for(KaitakuNamedControls.Control c:controls)
      if(c!=null)hits.add(new KaitakuRewardClaimPolicy.Hit(c.label,c.bounds));
    KaitakuRewardClaimPolicy.Decision d=KaitakuRewardClaimPolicy.decide(engine.current,context,hits);
    if(d.kind==KaitakuRewardClaimPolicy.Kind.NONE)return false;
    KaitakuActionPacer.reset(engine);
    KaitakuNamedControls.clear(out);
    if(d.kind==KaitakuRewardClaimPolicy.Kind.RECEIPT_CLOSE){
      KaitakuRewardClaimGate.receipt(engine);
      KaitakuActionEvidence.put(out,"close",d.rect);
      // Distinct receipt evidence; opt-out/ad-close never sets this flag.
      out.put("kaitaku-default-receipt-proof",new float[]{1});return true;
    }
    if(d.kind==KaitakuRewardClaimPolicy.Kind.HOLD)return true;
    KaitakuActionEvidence.put(out,"mission-page",null);
    if(engine.current.equals("kaitaku-default-missions")){
      if(d.kind==KaitakuRewardClaimPolicy.Kind.CLAIM&&KaitakuRewardClaimGate.allow(engine,d.rect))
        KaitakuActionEvidence.put(out,"get",d.rect);
      else if(d.kind==KaitakuRewardClaimPolicy.Kind.BACK)
        KaitakuActionEvidence.put(out,"back",d.rect);
    }
    return true;
  }
  private KaitakuRewardNamedControls(){}
}
