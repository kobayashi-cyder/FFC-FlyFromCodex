package jp.tapmarker;

import java.text.Normalizer;
import java.util.List;

/**
 * Strict, opt-out-only continuation for two known Kaitaku modal screens.
 * A modal title can HOLD ordinary play, but cannot authorize a tap without
 * an actual named control and a finite, safe rectangle.
 */
final class KaitakuVideoModalPolicy {
  enum Result { NONE, HOLD, DISMISS }
  static final class Hit {
    final String label;
    final float[] bounds;
    Hit(String label, float[] bounds) { this.label=label; this.bounds=bounds; }
  }
  static final class Decision {
    final Result result;
    final String kind;
    final float[] bounds;
    Decision(Result r, String k, float[] b) { result=r; kind=k; bounds=b==null?null:b.clone(); }
  }
  private static final Decision NONE=new Decision(Result.NONE,"",null);
  static String normalize(String s) {
    return s==null?"":Normalizer.normalize(s,Normalizer.Form.NFKC).replaceAll("[\\s\\u3000]+","").replace("このまま始める","このままはじめる");
  }
  static boolean inViewport(float[] b) {
    return b!=null&&b.length==4&&Float.isFinite(b[0])&&Float.isFinite(b[1])&&
      Float.isFinite(b[2])&&Float.isFinite(b[3])&&b[0]>=0&&b[1]>=.30f&&
      b[2]<=1&&b[3]<=.85f&&b[2]-b[0]>=.05f&&b[3]-b[1]>=.01f;
  }
  private static float[] find(List<Hit> hits, String label) {
    if(hits==null)return null;
    for(Hit hit:hits)if(hit!=null&&label.equals(normalize(hit.label))&&inViewport(hit.bounds))return hit.bounds;
    return null;
  }
  static Decision decide(String page, List<Hit> hits) {
    String text=normalize(page);
    // Underlying mission text may remain in the accessibility tree while the popup is open.
    // The unique coin title plus the bounded, named opt-out is sufficient; its body may be missing.
    if(text.contains("コインアップチャンス")) {
      float[] b=find(hits,"このままはじめる");
      return new Decision(b==null?Result.HOLD:Result.DISMISS,"coin-chance",b);
    }
    // A mission row alone cannot satisfy the independent explanatory sentence.
    if(text.contains("タップ2倍")&&text.contains("1タップで進む量が2倍")) {
      float[] b=find(hits,"キャンセル");
      return new Decision(b==null?Result.HOLD:Result.DISMISS,"tap-boost",b);
    }
    return NONE;
  }
  private KaitakuVideoModalPolicy() {}
}
