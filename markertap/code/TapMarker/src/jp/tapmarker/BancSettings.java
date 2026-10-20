package jp.tapmarker;
import android.app.*;import android.view.*;import android.widget.*;

final class BancSettings {
 static void show(AutoPilot pilot){
  TapService s=pilot.s;s.stop("BANC自律設定を開いた");LinearLayout body=new LinearLayout(s);body.setOrientation(1);body.setPadding(24,16,24,16);
  CheckBox enabled=new UiStrings.DisplayCheck(s);enabled.setText("BANCによる自律復帰・新しい手順の学習");enabled.setChecked(s.getSharedPreferences("bancAutonomy",0).getBoolean("enabled",true));body.addView(enabled);CheckBox shared=new UiStrings.DisplayCheck(s);shared.setText("他ゲームの成功・失敗と待機時間を活用する");shared.setChecked(s.getSharedPreferences("bancAutonomy",0).getBoolean("shareKnowHow",true));body.addView(shared);
  TextView help=new UiStrings.DisplayText(s);help.setText("登録した画面の独立した目印で状態を合わせ直します。未登録画面では、対象アプリの読み取れたボタンを使い、操作結果を確認します。他ゲームで確認できた操作方針と待機時間を活用しますが、画面と座標は実行先で確認します。成功2回以上・成功率66%以上の手順を再利用します。\n\nボタンや結果を読めないゲーム画面は、学習開始から実演するか、録画から生成してください。録画の未確認操作やデイリー完了を推測で通過しません。\n\n設定を閉じた後は「自動開始」で再開してください。");body.addView(help);
  TextView report=new UiStrings.DisplayText(s);String text=pilot.banc==null?"現在のアプリで自動開始すると学習の状況を確認できます。":pilot.banc.report();if(pilot.splitPilot.slots!=null)for(SplitPilot.Slot slot:pilot.splitPilot.slots)if(slot.banc!=null)text+="\n\n"+s.appLabel(slot.pkg)+"\n"+slot.banc.report();report.setText(text);report.setTextIsSelectable(true);report.setPadding(0,24,0,0);body.addView(report);ScrollView scroll=new ScrollView(s);scroll.addView(body);
  AlertDialog dialog=new UiStrings.DialogBuilder(s).setTitle("BANC自律").setView(scroll).setPositiveButton("保存",(d,n)->s.getSharedPreferences("bancAutonomy",0).edit().putBoolean("enabled",enabled.isChecked()).putBoolean("shareKnowHow",shared.isChecked()).apply()).setNegativeButton("閉じる",null).create();dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();
 }
}
