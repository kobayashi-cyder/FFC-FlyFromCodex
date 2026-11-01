package jp.tapmarker;

import java.text.Normalizer;
import java.util.List;

/**
 * Video-derived, opt-out-only popup policy. Never authorizes a rewarded ad.
 * It requires an exact visible action AND a matching dialog-specific sentence.
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
    return s==null?"":Normalizer.normalize(s,Normalizer.Form.NFKC).replaceAll("[\\s\\u3000]+","");
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
    // A mission row says "タップ2倍を3回使用する"; it is NOT the boost modal.
    if(text.contains("デイリーミッション"))return NONE;
    if(text.contains("コインアップチャンス")&&text.contains("ステージクリア時のコイン")) {
      float[] b=find(hits,"このままはじめる");
      return new Decision(b==null?Result.HOLD:Result.DISMISS,"coin-chance",b);
    }
    if(text.contains("タップ2倍")&&text.contains("1タップで進む量が2倍")) {
      float[] b=find(hits,"キャンセル");
      return new Decision(b==null?Result.HOLD:Result.DISMISS,"tap-boost",b);
    }
    return NONE;
  }
  private KaitakuVideoModalPolicy() {}
}
