package jp.tapmarker.weightspace;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
public final class MainActivity extends Activity {
  private final WeightSpacePolicy model=new WeightSpacePolicy(true);
  private final CheckBox[] inputs=new CheckBox[8];
  private TextView result;private Button patch;
  private static final String[] LABELS={"ゲームが前面","ミッション画面を確認","受取ボタンを確認","広告画面を確認","古い復帰待ちフラグ","ゲーム主画面を確認","不明な重なり表示","他のアプリが前面"};
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    ScrollView scroll=new ScrollView(this);
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
    int pad=(int)(16*getResources().getDisplayMetrics().density);
    layout.setPadding(pad,pad,pad,pad);scroll.addView(layout);
    TextView title=new TextView(this);title.setText("重み空間プログラミング / Weight-Space Lab");title.setTextSize(21);title.setTypeface(null,Typeface.BOLD);layout.addView(title);
    TextView context=new TextView(this);context.setText("MarkerTap向けの小型判断モデルを端末内で実行します。合成入力のみ・外部アプリの操作なし・広告操作なし。\nW2[CLAIM, stale_recovery_evidence] を直接編集します。");context.setTextSize(13);layout.addView(context);
    for(int i=0;i<inputs.length;i++){CheckBox b=new CheckBox(this);b.setText(LABELS[i]);inputs[i]=b;layout.addView(b);b.setOnCheckedChangeListener((button,checked)->refresh());}
    inputs[0].setChecked(true);inputs[1].setChecked(true);inputs[2].setChecked(true);inputs[4].setChecked(true);
    patch=new Button(this);layout.addView(patch);patch.setOnClickListener(v->{model.setPatched(!model.isPatched());refresh();});
    Button reset=new Button(this);reset.setText("入力を初期化");layout.addView(reset);
    reset.setOnClickListener(v->{for(int i=0;i<inputs.length;i++)inputs[i].setChecked(i==0||i==1||i==2||i==4);refresh();});
    result=new TextView(this);result.setTextSize(15);result.setTextIsSelectable(true);layout.addView(result);
    setContentView(scroll);refresh();
  }
  private void refresh(){
    if(result==null||patch==null)return;
    boolean[] x=new boolean[8];for(int i=0;i<8;i++)x[i]=inputs[i].isChecked();
    WeightSpacePolicy.Result out=model.evaluate(x);
    patch.setText(model.isPatched()?"修正前の重みへ戻す (-12)":"重みを直接修正する (0)");
    StringBuilder b=new StringBuilder();
    b.append("出力: ").append(out.action).append("（操作を実行せず判断のみ表示）\n");
    b.append("編集対象 W2[CLAIM,stale_recovery] = ").append(model.editedWeight()).append("\n\n隠れ層:\n");
    for(int i=0;i<5;i++)b.append(WeightSpacePolicy.HIDDEN[i]).append(" = ").append(String.format(Locale.US,"%.2f",out.hidden[i])).append("\n");
    b.append("\n出力スコア:\n");
    for(int i=0;i<4;i++)b.append(WeightSpacePolicy.ACTIONS[i]).append(" = ").append(String.format(Locale.US,"%.2f",out.scores[i])).append("\n");
    b.append("\nオフライン16事例・全256二値入力のテストはソースで実施。実ゲーム認識・タップは未検証です。");
    result.setText(b.toString());
  }
}
