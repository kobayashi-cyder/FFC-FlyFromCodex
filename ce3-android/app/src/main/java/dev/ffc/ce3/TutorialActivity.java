package dev.ffc.ce3;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TutorialActivity extends Activity {
    private int lesson = 0;
    private int a = 0, b = 0, cin = 0;
    private int x2 = 1, y2 = 1;
    private LinearLayout body;
    private TextView header, title, concept, circuit, formula, truth, live;
    private Button aBtn, bBtn, cBtn, prevBtn, nextBtn;

    private static class Lesson {
        final String title, concept, circuit, formula, truth;
        final int inputMode; // 0=bits A/B, 1=bits A/B/Cin, 2=2-bit numbers
        Lesson(String title, String concept, String circuit, String formula, String truth, int inputMode) {
            this.title=title; this.concept=concept; this.circuit=circuit; this.formula=formula; this.truth=truth; this.inputMode=inputMode;
        }
    }

    private final Lesson[] lessons = new Lesson[]{
        new Lesson(
            "0. MORE / LESS の基本",
            "規約を固定します。入力は0/1、各入力の重みは+1。Sは入力の加算値です。\n\nMORE(T): S >= T なら1\nLESS(T): S < T なら1\n\n等号をMORE側に固定すると、同じ閾値でMOREとLESSが同時に1になりません。",
            "A ----+\n      +--> SUM S --> MORE(T)\nB ----+            \\-> LESS(T)",
            "S = A + B\nMORE_T(S) = [S >= T]\nLESS_T(S) = [S < T]",
            "S | MORE(1) LESS(1) MORE(2) LESS(2)\n0 |    0       1       0       1\n1 |    1       0       0       1\n2 |    1       0       1       0",
            0
        ),
        new Lesson(
            "1. NOT",
            "NOTはLESSだけで作れます。A=0のときだけLESS(1)が成立します。",
            "A --> SUM --> LESS(1) --> OUT",
            "OUT = LESS_1(A)",
            "A | NOT A\n0 |   1\n1 |   0",
            0
        ),
        new Lesson(
            "2. OR",
            "A+Bが1以上ならON。つまりMORE(1)です。",
            "A --+\n    +--> SUM --> MORE(1) --> OR\nB --+",
            "S=A+B\nOR = MORE_1(S)",
            "A B | OR\n0 0 | 0\n0 1 | 1\n1 0 | 1\n1 1 | 1",
            0
        ),
        new Lesson(
            "3. NOR",
            "A+Bが1未満、つまり両方0のときだけON。LESS(1)だけで完成します。",
            "A --+\n    +--> SUM --> LESS(1) --> NOR\nB --+",
            "S=A+B\nNOR = LESS_1(S)",
            "A B | NOR\n0 0 |  1\n0 1 |  0\n1 0 |  0\n1 1 |  0",
            0
        ),
        new Lesson(
            "4. AND",
            "A+Bが2以上になるのはA=B=1だけ。MORE(2)です。",
            "A --+\n    +--> SUM --> MORE(2) --> AND\nB --+",
            "S=A+B\nAND = MORE_2(S)",
            "A B | AND\n0 0 |  0\n0 1 |  0\n1 0 |  0\n1 1 |  1",
            0
        ),
        new Lesson(
            "5. NAND",
            "A+Bが2未満ならON。LESS(2)だけでNANDになります。NANDは万能ゲートなので、ここから他の論理も再構成できます。",
            "A --+\n    +--> SUM --> LESS(2) --> NAND\nB --+",
            "S=A+B\nNAND = LESS_2(S)",
            "A B | NAND\n0 0 |   1\n0 1 |   1\n1 0 |   1\n1 1 |   0",
            0
        ),
        new Lesson(
            "6. XOR — ちょうど1個だけON",
            "XORは単一のMORE/LESS一個では作れません。S=1という範囲を、MORE(1)とLESS(2)の両方が成立する条件として作ります。",
            "S=A+B\nM=MORE(1,S)\nL=LESS(2,S)\nM,L --> SUM --> MORE(2) --> XOR",
            "M = MORE_1(S)\nL = LESS_2(S)\nXOR = MORE_2(M+L)",
            "A B | XOR\n0 0 |  0\n0 1 |  1\n1 0 |  1\n1 1 |  0",
            0
        ),
        new Lesson(
            "7. 半加算器",
            "2つの1bitを加算します。下位bitはXOR、桁上がりはANDです。つまりMORE/LESSの組合せだけで加算器になります。",
            "A,B --> XOR ------> SUM bit\n  \\--> MORE(2) ---> CARRY",
            "SUM = XOR(A,B)\nCARRY = MORE_2(A+B)",
            "A B | SUM CARRY\n0 0 |  0    0\n0 1 |  1    0\n1 0 |  1    0\n1 1 |  0    1",
            0
        ),
        new Lesson(
            "8. 全加算器",
            "A,Bに前の桁からのCinを加えます。SUMは3入力の奇数パリティ、CARRYは3入力のうち2個以上が1かどうかです。",
            "A,B --> XOR --> X --+--> XOR --> SUM\nCin ---------------+\nA+B+Cin --> MORE(2) --> COUT",
            "X = XOR(A,B)\nSUM = XOR(X,Cin)\nCOUT = MORE_2(A+B+Cin)",
            "A B C | SUM COUT\n0 0 0 | 0 0\n0 0 1 | 1 0\n0 1 0 | 1 0\n0 1 1 | 0 1\n1 0 0 | 1 0\n1 0 1 | 0 1\n1 1 0 | 0 1\n1 1 1 | 1 1",
            1
        ),
        new Lesson(
            "9. 半減算器",
            "A-Bを1bitで行います。DIFFはXOR。BORROWはAよりBが大きいときだけ1です。Ce3では負の重みを許せば、A-B < 0 をLESS(0)で直接判定できます。",
            "A ----(+1)---+\n              +--> S=A-B --> LESS(0) --> BORROW\nB ----(-1)---+\nA,B --> XOR --------------------------> DIFF",
            "D = A - B\nDIFF = XOR(A,B)\nBORROW = LESS_0(D)",
            "A B | DIFF BORROW\n0 0 |  0     0\n0 1 |  1     1\n1 0 |  1     0\n1 1 |  0     0",
            0
        ),
        new Lesson(
            "10. 全減算器",
            "A-B-Binを行います。借りは重み付き和 A-B-Bin が0未満かどうかで直接決められます。差bitはXORを2段にします。",
            "A(+1), B(-1), Bin(-1)\n       \\--> S=A-B-Bin --> LESS(0) --> BOUT\nA,B --> XOR --> X;  X,Cin --> XOR --> DIFF",
            "D = A - B - Bin\nDIFF = XOR(XOR(A,B),Bin)\nBOUT = LESS_0(D)",
            "A B Bin | DIFF BOUT\n0 0 0 | 0 0\n0 0 1 | 1 1\n0 1 0 | 1 1\n0 1 1 | 0 1\n1 0 0 | 1 0\n1 0 1 | 0 0\n1 1 0 | 0 0\n1 1 1 | 1 1",
            1
        ),
        new Lesson(
            "11. 1bit掛け算",
            "1bit同士の掛け算はANDそのものです。1×1のときだけ1なのでMORE(2)で作れます。",
            "A --+\n    +--> SUM --> MORE(2) --> PRODUCT\nB --+",
            "PRODUCT = MORE_2(A+B)",
            "A B | A×B\n0 0 |  0\n0 1 |  0\n1 0 |  0\n1 1 |  1",
            0
        ),
        new Lesson(
            "12. 2bit掛け算",
            "2bit掛け算は『部分積AND + 加算器』です。各bit同士をANDして部分積を作り、桁をずらして加算します。これは一般の二進乗算の基本形です。",
            "A=A1A0, B=B1B0\nP00=A0&B0 -> R0\nP01=A0&B1 --+\nP10=A1&B0 --+--> HALF ADDER -> R1,C1\nP11=A1&B1 --+\nC1 ----------+--> HALF ADDER -> R2,R3",
            "p00=AND(A0,B0)\np01=AND(A0,B1)\np10=AND(A1,B0)\np11=AND(A1,B1)\nR0=p00\nR1=XOR(p01,p10)\nc1=AND(p01,p10)\nR2=XOR(p11,c1)\nR3=AND(p11,c1)",
            "例: 3(11) × 3(11) = 9(1001)\n例: 2(10) × 3(11) = 6(0110)",
            2
        ),
        new Lesson(
            "13. n bit掛け算への拡張",
            "n bitでは、各Bのbitが1ならAをその桁だけ左シフトした部分積を生成し、それらを加算器で足します。つまり掛け算器の本体は『AND配列 + シフト + 加算器ツリー』です。",
            "B0 -> A & B0 << 0 --+\nB1 -> A & B1 << 1 --+\nB2 -> A & B2 << 2 --+--> ADDER TREE --> PRODUCT\n...                    +",
            "partial_j = A * Bj shifted by j\nPRODUCT = Σ partial_j",
            "Ce3部品対応:\nAND = MORE(2)\n加算 = Half/Full Adder\nシフト = 配線位置の変更\nENABLE = 部分積のON/OFF",
            2
        ),
        new Lesson(
            "14. ENABLEと算術回路",
            "ENABLEは計算を変えるのではなく、回路を通すか止めるかを制御します。例えば乗算器の部分積をBのbitでENABLEすれば、1ならAを通し、0なら0にできます。",
            "DATA ----> [ ENABLE ] ----> OUT\n               ^\n             CONTROL",
            "OUT = DATA AND ENABLE\nANDはMORE(2)で実装可能",
            "EN DATA | OUT\n0   0   | 0\n0   1   | 0\n1   0   | 0\n1   1   | 1",
            0
        )
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,251));

        LinearLayout top = new LinearLayout(this);
        Button back=btn("← 戻る");
        back.setOnClickListener(v -> finish());
        header=tv("",14); header.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(back,new LinearLayout.LayoutParams(dp(90),dp(48)));
        top.addView(header,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(top);

        ScrollView sv=new ScrollView(this);
        body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(8),dp(14),dp(24));
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout nav=new LinearLayout(this);
        prevBtn=btn("← 前");
        nextBtn=btn("次 →");
        prevBtn.setOnClickListener(v->{ if(lesson>0){lesson--;render();}});
        nextBtn.setOnClickListener(v->{ if(lesson<lessons.length-1){lesson++;render();}});
        nav.addView(prevBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        nav.addView(nextBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(nav);

        setContentView(root);
        render();
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView tv(String s,int sp){
        TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(23,32,51)); v.setPadding(dp(6),dp(6),dp(6),dp(6)); return v;
    }
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private TextView block(String label,String text){
        TextView v=tv(label+"\n"+text,14); v.setBackgroundColor(Color.WHITE); v.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,0,0,dp(10)); v.setLayoutParams(lp); return v;
    }

    private void render(){
        body.removeAllViews();
        Lesson L=lessons[lesson];
        header.setText("MORE / LESS Tutorial  "+(lesson+1)+"/"+lessons.length);
        title=tv(L.title,22); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); body.addView(title);
        concept=block("考え方",L.concept); body.addView(concept);
        circuit=block("回路構成",L.circuit); circuit.setTypeface(Typeface.MONOSPACE); body.addView(circuit);
        formula=block("式",L.formula); formula.setTypeface(Typeface.MONOSPACE); body.addView(formula);

        TextView tryTitle=tv("動かして確認",17); tryTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); body.addView(tryTitle);
        LinearLayout inputs=new LinearLayout(this);
        aBtn=btn(""); bBtn=btn(""); cBtn=btn("");
        inputs.addView(aBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        inputs.addView(bBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        inputs.addView(cBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(inputs);
        aBtn.setOnClickListener(v->{ if(L.inputMode==2)x2=(x2+1)&3; else a=1-a; refreshLive();});
        bBtn.setOnClickListener(v->{ if(L.inputMode==2)y2=(y2+1)&3; else b=1-b; refreshLive();});
        cBtn.setOnClickListener(v->{cin=1-cin;refreshLive();});

        live=block("ライブ結果",""); live.setTypeface(Typeface.MONOSPACE); body.addView(live);
        truth=block("真理値表 / 例",L.truth); truth.setTypeface(Typeface.MONOSPACE); body.addView(truth);

        prevBtn.setEnabled(lesson>0); nextBtn.setEnabled(lesson<lessons.length-1);
        refreshLive();
    }

    private int more(int s,int t){return s>=t?1:0;}
    private int less(int s,int t){return s<t?1:0;}
    private int and(int x,int y){return more(x+y,2);}
    private int or(int x,int y){return more(x+y,1);}
    private int not(int x){return less(x,1);}
    private int nand(int x,int y){return less(x+y,2);}
    private int nor(int x,int y){return less(x+y,1);}
    private int xor(int x,int y){
        int s=x+y;
        int m=more(s,1), l=less(s,2);
        return more(m+l,2);
    }

    private void refreshLive(){
        Lesson L=lessons[lesson];
        if(L.inputMode==2){
            aBtn.setText("X = "+x2+" ("+bits2(x2)+")");
            bBtn.setText("Y = "+y2+" ("+bits2(y2)+")");
            cBtn.setVisibility(View.GONE);
        }else{
            aBtn.setText("A = "+a);
            bBtn.setText("B = "+b);
            bBtn.setVisibility(lesson==1?View.INVISIBLE:View.VISIBLE);
            cBtn.setVisibility(L.inputMode==1?View.VISIBLE:View.GONE);
            cBtn.setText("Bin/Cin = "+cin);
        }

        String s;
        switch(lesson){
            case 0: {
                int sum=a+b;
                s="A="+a+" B="+b+"\nS="+sum+"\nMORE(1)="+more(sum,1)+"  LESS(1)="+less(sum,1)+"\nMORE(2)="+more(sum,2)+"  LESS(2)="+less(sum,2);
                break;
            }
            case 1: s="A="+a+"\nLESS(1,A)="+not(a)+"\nNOT="+not(a); break;
            case 2: s="S="+(a+b)+"\nMORE(1)="+or(a,b)+"\nOR="+or(a,b); break;
            case 3: s="S="+(a+b)+"\nLESS(1)="+nor(a,b)+"\nNOR="+nor(a,b); break;
            case 4: s="S="+(a+b)+"\nMORE(2)="+and(a,b)+"\nAND="+and(a,b); break;
            case 5: s="S="+(a+b)+"\nLESS(2)="+nand(a,b)+"\nNAND="+nand(a,b); break;
            case 6: {
                int sum=a+b,m=more(sum,1),l=less(sum,2),x=xor(a,b);
                s="S="+sum+"\nM=MORE(1)="+m+"\nL=LESS(2)="+l+"\nMORE(2,M+L)="+x+"\nXOR="+x; break;
            }
            case 7: {
                int sum=xor(a,b),carry=and(a,b);
                s=a+" + "+b+" = "+(a+b)+"\nSUM="+sum+"  CARRY="+carry+"\n2bit result="+carry+""+sum; break;
            }
            case 8: {
                int x=xor(a,b),sum=xor(x,cin),cout=more(a+b+cin,2);
                s="A+B+Cin="+(a+b+cin)+"\nX=XOR(A,B)="+x+"\nSUM=XOR(X,Cin)="+sum+"\nCOUT=MORE(2,total)="+cout+"\nresult="+cout+""+sum; break;
            }
            case 9: {
                int d=a-b,diff=xor(a,b),borrow=less(d,0);
                s="D=A-B="+d+"\nDIFF="+diff+"\nBORROW=LESS(0,D)="+borrow; break;
            }
            case 10: {
                int d=a-b-cin,diff=xor(xor(a,b),cin),bout=less(d,0);
                s="D=A-B-Bin="+d+"\nDIFF="+diff+"\nBOUT=LESS(0,D)="+bout; break;
            }
            case 11: s=a+" × "+b+" = "+and(a,b)+"\nPRODUCT=MORE(2,A+B)="+and(a,b); break;
            case 12: {
                int a0=x2&1,a1=(x2>>1)&1,b0=y2&1,b1=(y2>>1)&1;
                int p00=and(a0,b0),p01=and(a0,b1),p10=and(a1,b0),p11=and(a1,b1);
                int r0=p00,r1=xor(p01,p10),c1=and(p01,p10),r2=xor(p11,c1),r3=and(p11,c1);
                int product=r0+(r1<<1)+(r2<<2)+(r3<<3);
                s="p00="+p00+" p01="+p01+" p10="+p10+" p11="+p11+"\n"+
                  "R0="+r0+"\nR1=XOR(p01,p10)="+r1+"  c1="+c1+"\n"+
                  "R2=XOR(p11,c1)="+r2+"  R3="+r3+"\n"+
                  bits2(x2)+" × "+bits2(y2)+" = "+bits4(product)+" = "+product; break;
            }
            case 13: {
                int product=x2*y2;
                s="X="+bits2(x2)+"  Y="+bits2(y2)+"\n"+
                  "partial(B0)="+bits4((y2&1)!=0?x2:0)+"\n"+
                  "partial(B1)="+bits4((y2&2)!=0?(x2<<1):0)+"\n"+
                  "SUM="+bits4(product)+" = "+product; break;
            }
            default: {
                int en=b,out=and(a,en);
                s="DATA(A)="+a+"  ENABLE(B)="+en+"\nOUT=AND(DATA,ENABLE)="+out; break;
            }
        }
        live.setText("ライブ結果\n"+s);
    }

    private String bits2(int x){return ""+((x>>1)&1)+(x&1);}
    private String bits4(int x){return ""+((x>>3)&1)+((x>>2)&1)+((x>>1)&1)+(x&1);}
}
