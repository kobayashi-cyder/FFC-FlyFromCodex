package jp.tapmarker;
public class DialogSafetyTest {
 public static void main(String[] args){String[] blocked={"ログアウトしますか？","アカウントを削除する","退会 確認","データ削除","Sign out","Delete account"};for(String t:blocked)if(DialogSafety.classify(t)!=2)throw new AssertionError(t);String[] errors={"通信エラー 再試行","予期しないエラーが発生しました","接続に失敗しました"};for(String t:errors)if(DialogSafety.classify(t)!=1)throw new AssertionError(t);for(String t:new String[]{"増やす","使用する","詳細を見る","アカウント設定",null})if(DialogSafety.classify(t)!=0)throw new AssertionError(t);System.out.println("PASS 14 sensitive/error/normal text checks; visual-only dialogs not covered");}
}
