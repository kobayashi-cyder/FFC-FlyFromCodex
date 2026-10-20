package jp.tapmarker;
import java.util.Locale;
public final class DialogSafety {
 public static int classify(String text){String t=text==null?"":text.toLowerCase(Locale.ROOT).replace(" ","").replace("　","");for(String word:new String[]{"ログアウト","サインアウト","アカウント削除","アカウントを削除","退会","データを削除","データ削除","deleteaccount","logout","signout"})if(t.contains(word))return 2;for(String word:new String[]{"エラーが発生","問題が発生","通信エラー","通信に失敗","接続に失敗","予期しないエラー","異常が発生","再度お試し","erroroccurred","connectionfailed"})if(t.contains(word))return 1;return 0;}
}
