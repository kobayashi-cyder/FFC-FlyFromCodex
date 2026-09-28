package dev.ffc.ce3;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

@SuppressLint("SetTextI18n")
public class QuickStartActivity extends Activity {
    private int a=0,b=0;
    private boolean enable=true;
    private boolean more=true;
    private double threshold=1.0;
    private String state="IDLE";
    private String preset="OR";
    private TextView live, stepHint;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,251));

        LinearLayout top=new LinearLayout(this);
        Button back=btn("← 戻る");
        back.setOnClickListener(v->finish());
        TextView title=tv("この画面の使い方",22);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(back,new LinearLayout.LayoutParams(dp(92),dp(52)));
        top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(top);

        ScrollView sv=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(4),dp(14),dp(28));
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        body.addView(card("まず覚えること",
                "この画面には2つの層があります。\n\n" +
                "① 論理判定：A/B → SUM → MORE/LESS\n" +
                "② 状態記憶：ENABLE → STEP → STATE\n\n" +
                "判定を見るだけならSTEPは不要です。STEPは『結果を状態として記憶させる』操作です。"));

        body.addView(header("最短30秒コース"));
        body.addView(card("① ルールを選ぶ",
                "OR / AND / NOR / NAND / NOT A のどれかを押します。最初はORがおすすめです。\n" +
                "ORなら MORE、Threshold=1 が自動設定されます。"));

        LinearLayout presets=new LinearLayout(this);
        String[] ps={"OR","AND","NOR","NAND"};
        for(String p:ps){
            Button x=btn(p);
            x.setOnClickListener(v->{setPreset(((Button)v).getText().toString());refresh();});
            presets.addView(x,new LinearLayout.LayoutParams(0,dp(50),1));
        }
        body.addView(presets);

        body.addView(card("② A / B を押して入力を変える",
                "AとBは0/1の入力です。ボタンを押すたびに0↔1が切り替わります。"));

        LinearLayout inputs=new LinearLayout(this);
        Button aBtn=btn("Aを切替");
        Button bBtn=btn("Bを切替");
        Button enBtn=btn("ENABLE切替");
        aBtn.setOnClickListener(v->{a=1-a;refresh();});
        bBtn.setOnClickListener(v->{b=1-b;refresh();});
        enBtn.setOnClickListener(v->{enable=!enable;refresh();});
        inputs.addView(aBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        inputs.addView(bBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        inputs.addView(enBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(inputs);

        body.addView(card("③ 中央のMORE/LESSを見る",
                "ここが論理回路の出力です。\n" +
                "TRUE = 条件成立、FALSE = 条件不成立。\n" +
                "例えばANDならA=B=1のときだけTRUEです。"));

        live=card("ライブ結果","");
        live.setTypeface(Typeface.MONOSPACE);
        body.addView(live);

        body.addView(card("④ STEPは『記憶させる』ボタン",
                "TRUE/FALSEを確認するだけならSTEPは押さなくて構いません。\n" +
                "STEPを押すと、現在の判定を使ってSTATEを進めます。"));

        LinearLayout stateRow=new LinearLayout(this);
        Button step=btn("STEP");
        Button reset=btn("RESET");
        step.setOnClickListener(v->{step();refresh();});
        reset.setOnClickListener(v->{state="IDLE";refresh();});
        stateRow.addView(step,new LinearLayout.LayoutParams(0,dp(52),1));
        stateRow.addView(reset,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(stateRow);

        stepHint=card("STATEの意味","");
        body.addView(stepHint);

        body.addView(card("⑤ ENABLEは『通行許可』",
                "ENABLE=OFFでは、MORE/LESSがTRUEでもSTATEは変わりません。\n" +
                "論理判定そのものは見えますが、状態遷移だけを止めます。"));

        body.addView(card("画面を読む順番",
                "上から読む必要はありません。おすすめは\n\n" +
                "プリセット → A/B → 中央のMORE/LESS → 必要ならSTEP → STATE\n\n" +
                "です。ThresholdやMORE/LESSを直接触るのは、仕組みが分かってからで十分です。"));

        body.addView(card("次に進むなら",
                "この画面の使い方が分かったら『回路チュートリアル』で、MORE/LESSからAND/NAND、加算器、ALU、CPUへ進みます。"));

        setContentView(root);
        refresh();
    }

    private void setPreset(String p){
        preset=p;
        if("OR".equals(p)){more=true;threshold=1;}
        else if("AND".equals(p)){more=true;threshold=2;}
        else if("NOR".equals(p)){more=false;threshold=1;}
        else if("NAND".equals(p)){more=false;threshold=2;}
    }

    private boolean result(){
        double s=a+b;
        return more ? s>=threshold : s<threshold;
    }

    private void step(){
        if(!enable)return;
        boolean r=result();
        if(r && "IDLE".equals(state)) state="ACTIVE";
        else if(r && "ACTIVE".equals(state)) state="LOCKED";
        else if(!r && "LOCKED".equals(state)) state="ACTIVE";
        else if(!r && "ACTIVE".equals(state)) state="IDLE";
    }

    private void refresh(){
        if(live==null)return;
        double s=a+b;
        live.setText(String.format(Locale.US,
                "ライブ結果\nPRESET = %s\nA=%d  B=%d  SUM=%.0f\n%s  T=%.0f\nRESULT = %s\nENABLE = %s",
                preset,a,b,s,more?"MORE ≥":"LESS <",threshold,result()?"TRUE":"FALSE",enable?"ON":"OFF"));
        stepHint.setText("STATEの意味\n現在: "+state+"\n"+
                ("IDLE".equals(state)?"まだ状態を記憶していません。":
                 "ACTIVE".equals(state)?"条件が成立した履歴を1段階保持しています。":
                 "条件成立が続いた履歴を保持しています。"));
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setMinHeight(dp(48));return b;}
    private TextView tv(String s,int sp){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.rgb(23,32,51));v.setPadding(dp(6),dp(6),dp(6),dp(6));return v;}
    private TextView header(String s){TextView v=tv(s,18);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(dp(2),dp(14),dp(2),dp(6));return v;}
    private TextView card(String title,String text){
        TextView v=tv(title+"\n"+text,14);
        v.setBackgroundColor(Color.WHITE);
        v.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(10));v.setLayoutParams(lp);return v;
    }
}
