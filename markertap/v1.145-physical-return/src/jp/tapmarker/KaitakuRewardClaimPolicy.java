package jp.tapmarker;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/** Conservative UI-scoped Kaitaku mission reward detector.
 * It cannot establish that coins actually reached the account.
 */
final class KaitakuRewardClaimPolicy {
  enum Kind { NONE, PAGE, CLAIM, BACK, RECEIPT_CLOSE, HOLD }
  static final class Hit {
    final String label; final float[] rect;
    Hit(String label,float[] rect){this.label=label;this.rect=rect;}
  }
  static final class Decision {
    final Kind kind;final float[] rect;
    Decision(Kind kind,float[] rect){this.kind=kind;this.rect=rect==null?null:rect.clone();}
  }
  private static final Pattern SPACE=Pattern.compile("[\\s\\u3000]+");
  private static final Decision NONE=new Decision(Kind.NONE,null);
  private static String norm(String text){
    return text==null?"":SPACE.matcher(Normalizer.normalize(text,Normalizer.Form.NFKC)).replaceAll("");
  }
  static boolean safe(float[] r){
    return r!=null&&r.length==4&&Float.isFinite(r[0])&&Float.isFinite(r[1])&&
      Float.isFinite(r[2])&&Float.isFinite(r[3])&&r[0]>=0&&r[1]>=.08f&&
      r[2]<=1&&r[3]<=.93f&&r[2]-r[0]>=.04f&&r[3]-r[1]>=.012f;
  }
  private static boolean equalsAny(String x,String...names){
    for(String n:names)if(n.equals(x))return true;return false;
  }
  private static float[] find(List<Hit> hits,String... names){
    if(hits==null)return null;
    float[] selected=null;
    for(Hit hit:hits){
      if(hit==null||!equalsAny(norm(hit.label),names)||!safe(hit.rect))continue;
      if(selected==null||hit.rect[1]<selected[1])selected=hit.rect;
    }
    return selected;
  }
  private static boolean labeled(List<Hit> hits,String... names){
    if(hits==null)return false;
    for(Hit hit:hits)if(hit!=null&&equalsAny(norm(hit.label),names))return true;
    return false;
  }
  static Decision decide(String phase,String page,List<Hit> hits){
    if(phase==null||!phase.startsWith("kaitaku-default-"))return NONE;
    String text=norm(page);
    boolean receipt=text.contains("報酬獲得")||text.contains("報酬を獲得")||
      text.contains("獲得しました")||text.contains("コインゲット");
    if(receipt){
      float[] close=find(hits,"閉じる","とじる","OK","確認");
      return new Decision(close==null?Kind.HOLD:Kind.RECEIPT_CLOSE,close);
    }
    if(text.contains("コインアップチャンス")||
       (text.contains("タップ2倍")&&text.contains("1タップで進む量が2倍")))return NONE;
    boolean mission=text.contains("デイリーミッション")||text.contains("ミッション一覧");
    if(!mission||phase.equals("kaitaku-default-mini"))return NONE;
    float[] claim=find(hits,"ゲット","GET","受け取る","報酬を受け取る","一括受取","一括で受け取る");
    if(claim!=null)return new Decision(Kind.CLAIM,claim);
    if(labeled(hits,"ゲット","GET","受け取る","報酬を受け取る","一括受取","一括で受け取る"))
      return new Decision(Kind.HOLD,null);
    // "Play" is a mission action, not a reward claim. Delegate to the
    // established mission automaton instead of freezing on PAGE.
    if(find(hits,"プレイ","ゲームへ戻る")!=null)return NONE;
    float[] back=find(hits,"戻る","閉じる");
    return new Decision(back==null?Kind.PAGE:Kind.BACK,back);
  }
  static String target(float[] b){
    if(!safe(b))throw new IllegalArgumentException("unsafe");
    return Math.round(b[0]*100f)+":"+Math.round(b[1]*100f)+":"+
      Math.round(b[2]*100f)+":"+Math.round(b[3]*100f);
  }
  private KaitakuRewardClaimPolicy(){}
}
