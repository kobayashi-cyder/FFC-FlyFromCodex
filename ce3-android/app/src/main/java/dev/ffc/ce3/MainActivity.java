package dev.ffc.ce3;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

@SuppressLint("SetTextI18n")
public class MainActivity extends Activity {
    private final AutoCore auto = new AutoCore();
    private final MLP ml = new MLP();
    private CircuitView circuitView;
    private LinearLayout controls;
    private TextView status;
    private TextView detail;
    private boolean mlMode = false;
    private int mlTarget = 3;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        showShell();
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView tv(String s, int sp) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(23,32,51));
        v.setPadding(dp(4), dp(5), dp(4), dp(5));
        return v;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        return b;
    }

    private void showShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,251));

        TextView title = tv("FFC · Ce3", 22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(16),dp(14),dp(16),dp(6));
        root.addView(title);

        TextView sub = tv("入力 → 判定 → 状態 → 学習を、触りながら理解する実験環境", 13);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(dp(16),0,dp(16),dp(8));
        root.addView(sub);

        Button guide = btn("▶ この画面の使い方（30秒）");
        guide.setOnClickListener(v -> startActivity(new Intent(this, QuickStartActivity.class)));
        LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        guideLp.setMargins(dp(12),0,dp(12),dp(8));
        root.addView(guide, guideLp);

        Button builder = btn("＋ 回路ビルダー（保存・コピー・Instance・Fork）");
        builder.setOnClickListener(v -> startActivity(new Intent(this, BuilderActivity.class)));
        LinearLayout.LayoutParams builderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        builderLp.setMargins(dp(12),0,dp(12),dp(8));
        root.addView(builder, builderLp);

        Button banc = btn("BANC v888 Lab（読込・Module化・Motif）");
        banc.setOnClickListener(v -> startActivity(new Intent(this, Banc888Activity.class)));
        LinearLayout.LayoutParams bancLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        bancLp.setMargins(dp(12),0,dp(12),dp(8));
        root.addView(banc, bancLp);

        LinearLayout tabs1 = new LinearLayout(this);
        tabs1.setPadding(dp(12),0,dp(12),0);
        Button a = btn("基本回路");
        Button m = btn("学習回路");
        tabs1.addView(a, new LinearLayout.LayoutParams(0, dp(50), 1));
        tabs1.addView(m, new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(tabs1);

        LinearLayout tabs2 = new LinearLayout(this);
        tabs2.setPadding(dp(12),0,dp(12),dp(6));
        Button t = btn("回路チュートリアル");
        Button bio = btn("コネクトーム");
        tabs2.addView(t, new LinearLayout.LayoutParams(0, dp(50), 1));
        tabs2.addView(bio, new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(tabs2);

        a.setOnClickListener(v -> { mlMode=false; buildControls(); });
        m.setOnClickListener(v -> { mlMode=true; buildControls(); });
        t.setOnClickListener(v -> startActivity(new Intent(this, TutorialActivity.class)));
        bio.setOnClickListener(v -> startActivity(new Intent(this, BioSystemActivity.class)));

        circuitView = new CircuitView(this);
        circuitView.setMinimumHeight(dp(300));
        root.addView(circuitView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(310)));

        ScrollView sv = new ScrollView(this);
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(14),dp(6),dp(14),dp(24));
        sv.addView(controls);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        buildControls();
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private void addRowButtons(String[] labels, View.OnClickListener[] listeners) {
        LinearLayout r = row();
        for (int i=0;i<labels.length;i++) {
            Button b=btn(labels[i]);
            b.setOnClickListener(listeners[i]);
            r.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));
        }
        controls.addView(r);
    }

    private void buildControls() {
        controls.removeAllViews();
        if (mlMode) buildMl(); else buildAuto();
        circuitView.invalidate();
    }

    private void addHeader(String s) {
        TextView h=tv(s,17); h.setTypeface(null, android.graphics.Typeface.BOLD); h.setPadding(dp(2),dp(12),dp(2),dp(6)); controls.addView(h);
    }

    private void buildAuto() {
        TextView flow=tv("使い方  ①ルールを選ぶ → ②A/Bを押す → ③判定を見る → ④必要ならSTEP",14);
        flow.setBackgroundColor(Color.WHITE);
        flow.setPadding(dp(12),dp(10),dp(12),dp(10));
        controls.addView(flow);

        addHeader("1. まずルールを選ぶ");
        addRowButtons(new String[]{"OR","AND","NOR","NAND"}, new View.OnClickListener[]{
                v->{auto.setPreset("OR");buildControls();},
                v->{auto.setPreset("AND");buildControls();},
                v->{auto.setPreset("NOR");buildControls();},
                v->{auto.setPreset("NAND");buildControls();}
        });
        addRowButtons(new String[]{"NOT A"}, new View.OnClickListener[]{
                v->{auto.setPreset("NOT A");buildControls();}
        });

        TextView presetInfo=tv("選択中: "+auto.preset+"   "+auto.presetMeaning(),14);
        presetInfo.setTextColor(Color.DKGRAY);
        controls.addView(presetInfo);

        addHeader("2. 入力を変える");
        addRowButtons(new String[]{"A = "+auto.a,"B = "+auto.b,"ENABLE = "+(auto.enable?"ON":"OFF")},
                new View.OnClickListener[]{
                        v->{auto.a=1-auto.a;buildControls();},
                        v->{auto.b=1-auto.b;buildControls();},
                        v->{auto.enable=!auto.enable;buildControls();}
                });

        addHeader("3. 判定を見る");
        status=tv("",17);
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        status.setBackgroundColor(Color.WHITE);
        status.setPadding(dp(12),dp(12),dp(12),dp(12));
        controls.addView(status);

        TextView advanced=tv("詳細設定（仕組みを触りたい人向け）",14);
        advanced.setTypeface(null, android.graphics.Typeface.BOLD);
        controls.addView(advanced);
        addRowButtons(new String[]{"MORE  ≥ T","LESS  < T"}, new View.OnClickListener[]{
                v->{auto.more=true;auto.preset="CUSTOM";buildControls();},
                v->{auto.more=false;auto.preset="CUSTOM";buildControls();}
        });
        TextView th=tv(String.format(Locale.US,"Threshold T = %.2f",auto.threshold),14);
        controls.addView(th);
        SeekBar sb=new SeekBar(this); sb.setMax(250); sb.setProgress((int)(auto.threshold*100));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){
                auto.threshold=p/100f;
                if(from) auto.preset="CUSTOM";
                th.setText(String.format(Locale.US,"Threshold T = %.2f",auto.threshold));
                circuitView.invalidate();
                refreshAutoText();
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){buildControls();}
        });
        controls.addView(sb);

        addHeader("4. STATEに記憶させる（必要なときだけ）");
        TextView stateHelp=tv("MORE/LESSのTRUE/FALSEを見るだけならSTEP不要。STEPは現在の判定をSTATEへ反映します。",13);
        stateHelp.setTextColor(Color.DKGRAY);
        controls.addView(stateHelp);
        addRowButtons(new String[]{"STEP","STATE RESET"}, new View.OnClickListener[]{
                v->{auto.step(); refreshAutoText(); circuitView.invalidate();},
                v->{auto.reset(); refreshAutoText(); circuitView.invalidate();}
        });

        detail=tv("",13);
        detail.setTextColor(Color.DKGRAY);
        detail.setBackgroundColor(Color.WHITE);
        detail.setPadding(dp(12),dp(10),dp(12),dp(10));
        controls.addView(detail);
        refreshAutoText();
    }

    private void refreshAutoText() {
        if(status==null||mlMode)return;
        float s=auto.sum();
        boolean cmp=auto.more ? s>=auto.threshold : s<auto.threshold;
        status.setText(String.format(Locale.US,
                "%s 判定: %s\nA=%d + B=%d → SUM=%.0f\n%s %.2f → %s",
                auto.preset, cmp?"TRUE":"FALSE", auto.a, auto.b, s,
                auto.more?"MORE ≥":"LESS <", auto.threshold, cmp?"条件成立":"条件不成立"));
        detail.setText("STATE = "+auto.state+"\n"+
                "ENABLE = "+(auto.enable?"ON（STEPで状態変更可）":"OFF（STATEは固定）")+"\n"+
                "直前のSTEP: "+auto.last);
    }

    private void buildMl() {
        addHeader("1. 学習する論理");
        addRowButtons(new String[]{"AND","OR","NOR","XOR"}, new View.OnClickListener[]{
                v->{mlTarget=0;buildControls();}, v->{mlTarget=1;buildControls();},
                v->{mlTarget=2;buildControls();}, v->{mlTarget=3;buildControls();}
        });

        addHeader("2. 入力と判定");
        addRowButtons(new String[]{"A: "+ml.a,"B: "+ml.b,"COMPARE: "+(ml.more?"MORE":"LESS")},new View.OnClickListener[]{
                v->{ml.a=1-ml.a;buildControls();},v->{ml.b=1-ml.b;buildControls();},
                v->{ml.more=!ml.more;buildControls();}
        });

        addHeader("3. ML回路");
        addRowButtons(new String[]{"TRAIN ×2000","RESET WEIGHTS"}, new View.OnClickListener[]{
                v->{ for(int i=0;i<2000;i++) ml.trainEpoch(mlTarget); refreshMlText(); circuitView.invalidate(); },
                v->{ml.resetWeights();refreshMlText();circuitView.invalidate();}
        });
        addRowButtons(new String[]{"保存","読込"}, new View.OnClickListener[]{
                v->{saveMl();refreshMlText();}, v->{loadMl();refreshMlText();circuitView.invalidate();}
        });

        status=tv("",16);status.setTypeface(null, android.graphics.Typeface.BOLD);controls.addView(status);
        detail=tv("",13);detail.setTextColor(Color.DKGRAY);controls.addView(detail);
        refreshMlText();

        addHeader("ML拡張の意味");
        TextView note=tv("入力→2個の隠れセル→出力セルの 2–2–1 回路です。重みは学習で変化し、最後の真偽判定だけ MORE/LESS 0.5 を使います。XORは単一閾値では分離できないため、隠れ層が「オートマトン＋単純比較」からの拡張例になります。",13);
        note.setTextColor(Color.DKGRAY);controls.addView(note);
    }

    private String targetName(){ return new String[]{"AND","OR","NOR","XOR"}[mlTarget]; }

    private void refreshMlText() {
        if(status==null||!mlMode)return;
        double y=ml.forward(ml.a,ml.b);
        boolean out=ml.more ? y>=0.5 : y<0.5;
        status.setText(String.format(Locale.US,"%s   A=%d B=%d   y=%.4f   %s0.5 → %d",
                targetName(),ml.a,ml.b,y,ml.more?"MORE ":"LESS ",out?1:0));
        detail.setText(String.format(Locale.US,
                "Loss(all 4) = %.6f\nH1: [%.3f, %.3f | b %.3f]  H2: [%.3f, %.3f | b %.3f]\nOUT: [%.3f, %.3f | b %.3f]",
                ml.loss(mlTarget),ml.w1[0][0],ml.w1[0][1],ml.b1[0],ml.w1[1][0],ml.w1[1][1],ml.b1[1],ml.w2[0],ml.w2[1],ml.b2));
    }

    private void saveMl(){
        SharedPreferences p=getSharedPreferences("ce3",MODE_PRIVATE);
        p.edit().putString("weights",ml.pack()).putInt("target",mlTarget).apply();
    }
    private void loadMl(){
        SharedPreferences p=getSharedPreferences("ce3",MODE_PRIVATE);
        String s=p.getString("weights",null); if(s!=null) ml.unpack(s); mlTarget=p.getInt("target",mlTarget);
    }

    static class AutoCore {
        int a=0,b=0;
        boolean enable=true,more=true;
        float threshold=1f;
        String state="IDLE";
        String last="まだSTEPしていません";
        String preset="OR";

        float sum(){return a+b;}
        boolean result(){float s=sum();return more?s>=threshold:s<threshold;}

        void setPreset(String p){
            preset=p;
            if("OR".equals(p)){more=true;threshold=1f;}
            else if("AND".equals(p)){more=true;threshold=2f;}
            else if("NOR".equals(p)){more=false;threshold=1f;}
            else if("NAND".equals(p)){more=false;threshold=2f;}
            else if("NOT A".equals(p)){more=false;threshold=1f;b=0;}
        }

        String presetMeaning(){
            if("OR".equals(preset)) return "AかBのどちらか1でTRUE";
            if("AND".equals(preset)) return "AとBが両方1でTRUE";
            if("NOR".equals(preset)) return "AとBが両方0でTRUE";
            if("NAND".equals(preset)) return "AとBが両方1以外でTRUE";
            if("NOT A".equals(preset)) return "A=0ならTRUE";
            return "MORE/LESSと閾値を手動設定";
        }

        void reset(){state="IDLE";last="STATEをIDLEに戻しました";}

        void step(){
            if(!enable){last="ENABLE=OFFのためSTATEは変化しません";return;}
            boolean r=result();
            String before=state;
            if(r){
                if("IDLE".equals(state)) state="ACTIVE";
                else if("ACTIVE".equals(state)) state="LOCKED";
            }else{
                if("LOCKED".equals(state)) state="ACTIVE";
                else if("ACTIVE".equals(state)) state="IDLE";
            }
            last=before.equals(state)
                    ? "判定によりSTATEを保持"
                    : before+" → "+state;
        }
    }

    static class MLP {
        int a=0,b=0; boolean more=true;
        final double[][] w1=new double[2][2];
        final double[] b1=new double[2];
        final double[] w2=new double[2];
        double b2;
        final Random rnd=new Random(73);
        MLP(){resetWeights();}
        void resetWeights(){
            for(int h=0;h<2;h++){for(int i=0;i<2;i++)w1[h][i]=rnd.nextDouble()*1.2-0.6;b1[h]=rnd.nextDouble()*0.4-0.2;w2[h]=rnd.nextDouble()*1.2-0.6;}
            b2=rnd.nextDouble()*0.4-0.2;
        }
        double sig(double x){return 1.0/(1.0+Math.exp(-x));}
        double forward(double x0,double x1){
            double h0=sig(w1[0][0]*x0+w1[0][1]*x1+b1[0]);
            double h1=sig(w1[1][0]*x0+w1[1][1]*x1+b1[1]);
            return sig(w2[0]*h0+w2[1]*h1+b2);
        }
        int[] targets(int t){
            if(t==0)return new int[]{0,0,0,1};
            if(t==1)return new int[]{0,1,1,1};
            if(t==2)return new int[]{1,0,0,0};
            return new int[]{0,1,1,0};
        }
        void trainEpoch(int t){
            int[] yy=targets(t); double lr=0.8;
            for(int k=0;k<4;k++){
                double x0=(k>>1)&1, x1=k&1, y=yy[k];
                double h0=sig(w1[0][0]*x0+w1[0][1]*x1+b1[0]);
                double h1=sig(w1[1][0]*x0+w1[1][1]*x1+b1[1]);
                double o=sig(w2[0]*h0+w2[1]*h1+b2);
                double dO=(y-o)*o*(1-o);
                double oldW20=w2[0], oldW21=w2[1];
                w2[0]+=lr*dO*h0; w2[1]+=lr*dO*h1; b2+=lr*dO;
                double dH0=h0*(1-h0)*oldW20*dO;
                double dH1=h1*(1-h1)*oldW21*dO;
                w1[0][0]+=lr*dH0*x0;w1[0][1]+=lr*dH0*x1;b1[0]+=lr*dH0;
                w1[1][0]+=lr*dH1*x0;w1[1][1]+=lr*dH1*x1;b1[1]+=lr*dH1;
            }
        }
        double loss(int t){
            int[] yy=targets(t);double sum=0;
            for(int k=0;k<4;k++){double o=forward((k>>1)&1,k&1);double d=yy[k]-o;sum+=d*d;}
            return sum/4.0;
        }
        String pack(){
            return String.format(Locale.US,"%f,%f,%f,%f,%f,%f,%f,%f,%f",
                w1[0][0],w1[0][1],b1[0],w1[1][0],w1[1][1],b1[1],w2[0],w2[1],b2);
        }
        void unpack(String s){
            try{String[] q=s.split(",");if(q.length!=9)return;
                w1[0][0]=Double.parseDouble(q[0]);w1[0][1]=Double.parseDouble(q[1]);b1[0]=Double.parseDouble(q[2]);
                w1[1][0]=Double.parseDouble(q[3]);w1[1][1]=Double.parseDouble(q[4]);b1[1]=Double.parseDouble(q[5]);
                w2[0]=Double.parseDouble(q[6]);w2[1]=Double.parseDouble(q[7]);b2=Double.parseDouble(q[8]);
            }catch(Exception ignored){}
        }
    }

    class CircuitView extends View {
        Paint p=new Paint(1);
        CircuitView(Context c){super(c);p.setTypeface(android.graphics.Typeface.DEFAULT);}
        protected void onDraw(Canvas c){
            super.onDraw(c);
            c.drawColor(Color.WHITE);
            if(mlMode) drawMl(c); else drawAuto(c);
        }
        void line(Canvas c,float x1,float y1,float x2,float y2,String label){
            p.setColor(Color.rgb(92,105,126));p.setStrokeWidth(dp(2));c.drawLine(x1,y1,x2,y2,p);
            p.setColor(Color.DKGRAY);p.setTextSize(dp(11));c.drawText(label,(x1+x2)/2,(y1+y2)/2-dp(4),p);
        }
        void node(Canvas c,float x,float y,String top,String bottom,boolean on){
            p.setColor(on?Color.rgb(49,94,251):Color.rgb(230,234,242));c.drawCircle(x,y,dp(30),p);
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(12));p.setColor(on?Color.WHITE:Color.rgb(23,32,51));c.drawText(top,x,y-dp(2),p);
            p.setTextSize(dp(10));c.drawText(bottom,x,y+dp(14),p);p.setTextAlign(Paint.Align.LEFT);
        }
        void drawAuto(Canvas c){
            float w=getWidth(), h=getHeight(); float y1=h*.22f,y2=h*.52f,y3=h*.80f;
            node(c,w*.18f,y1,"A",""+auto.a,auto.a==1);node(c,w*.42f,y1,"B",""+auto.b,auto.b==1);
            line(c,w*.18f,y1+dp(31),w*.34f,y2-dp(31),"w=1");line(c,w*.42f,y1+dp(31),w*.34f,y2-dp(31),"w=1");
            node(c,w*.34f,y2,"SUM",String.format(Locale.US,"%.1f",auto.sum()),auto.sum()>0);
            line(c,w*.34f+dp(31),y2,w*.67f-dp(31),y2,auto.more?"≥ T":"< T");
            boolean cmp=auto.more?auto.sum()>=auto.threshold:auto.sum()<auto.threshold;
            node(c,w*.67f,y2,auto.more?"MORE":"LESS",String.format(Locale.US,"T %.1f",auto.threshold),cmp);
            line(c,w*.67f,y2+dp(31),w*.67f,y3-dp(31),auto.enable?"ENABLE":"BLOCK");
            node(c,w*.67f,y3,"STATE",auto.state,!auto.state.equals("IDLE"));
            node(c,w*.88f,y1,"EN",auto.enable?"1":"0",auto.enable);
        }
        void drawMl(Canvas c){
            float w=getWidth(),h=getHeight();
            double h0=ml.sig(ml.w1[0][0]*ml.a+ml.w1[0][1]*ml.b+ml.b1[0]);
            double h1=ml.sig(ml.w1[1][0]*ml.a+ml.w1[1][1]*ml.b+ml.b1[1]);
            double o=ml.forward(ml.a,ml.b);
            node(c,w*.12f,h*.30f,"A",""+ml.a,ml.a==1);node(c,w*.12f,h*.70f,"B",""+ml.b,ml.b==1);
            line(c,w*.12f+dp(31),h*.30f,w*.45f-dp(31),h*.30f,String.format(Locale.US,"%.2f",ml.w1[0][0]));
            line(c,w*.12f+dp(31),h*.30f,w*.45f-dp(31),h*.70f,String.format(Locale.US,"%.2f",ml.w1[1][0]));
            line(c,w*.12f+dp(31),h*.70f,w*.45f-dp(31),h*.30f,String.format(Locale.US,"%.2f",ml.w1[0][1]));
            line(c,w*.12f+dp(31),h*.70f,w*.45f-dp(31),h*.70f,String.format(Locale.US,"%.2f",ml.w1[1][1]));
            node(c,w*.45f,h*.30f,"H1",String.format(Locale.US,"%.2f",h0),h0>=.5);
            node(c,w*.45f,h*.70f,"H2",String.format(Locale.US,"%.2f",h1),h1>=.5);
            line(c,w*.45f+dp(31),h*.30f,w*.74f-dp(31),h*.50f,String.format(Locale.US,"%.2f",ml.w2[0]));
            line(c,w*.45f+dp(31),h*.70f,w*.74f-dp(31),h*.50f,String.format(Locale.US,"%.2f",ml.w2[1]));
            node(c,w*.74f,h*.50f,"OUT",String.format(Locale.US,"%.2f",o),o>=.5);
            boolean q=ml.more?o>=.5:o<.5;
            line(c,w*.74f+dp(31),h*.50f,w*.91f-dp(31),h*.50f,ml.more?"≥.5":"<.5");
            node(c,w*.91f,h*.50f,ml.more?"MORE":"LESS",q?"1":"0",q);
        }
    }
}
