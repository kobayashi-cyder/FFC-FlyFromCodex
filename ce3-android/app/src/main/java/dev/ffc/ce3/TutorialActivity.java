package dev.ffc.ce3;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TutorialActivity extends Activity {
    private int lesson=0;
    private int a=0,b=0,cin=0;
    private int x2=1,y2=1;
    private int reg2=0,counter2=0,fsmState=0,cpuAcc=0,aluOp=0;
    private boolean latchQ=false,shiftLeft=true;
    private LinearLayout body;
    private TextView header,live;
    private Button aBtn,bBtn,cBtn,prevBtn,nextBtn;

    private static class Lesson {
        final String title,concept,circuit,formula,truth;
        final int inputMode; // 0=bit, 1=3bit, 2=2bit number, 3=stateful/custom
        Lesson(String t,String c,String r,String f,String q,int m){
            title=t;concept=c;circuit=r;formula=f;truth=q;inputMode=m;
        }
    }

    private final Lesson[] lessons=new Lesson[]{
        new Lesson("0. MORE / LESS の基本",
            "入力は0/1、重み付き和をSとします。MORE(T)はS>=T、LESS(T)はS<T。等号をMORE側に固定すると境界が一意になります。",
            "inputs --weights--> SUM S --+--> MORE(T)\n                           \\--> LESS(T)",
            "MORE_T(S)=[S>=T]\nLESS_T(S)=[S<T]",
            "S | MORE1 LESS1 MORE2 LESS2\n0 |   0     1     0     1\n1 |   1     0     0     1\n2 |   1     0     1     0",0),
        new Lesson("1. NOT",
            "Aが0のときだけONにしたいのでLESS(1)を使います。",
            "A --> LESS(1) --> NOT A",
            "NOT(A)=LESS_1(A)",
            "A | OUT\n0 | 1\n1 | 0",0),
        new Lesson("2. OR",
            "A+Bが1以上ならON。MORE(1)だけでORになります。",
            "A --+\n    +--> SUM --> MORE(1) --> OR\nB --+",
            "OR=MORE_1(A+B)",
            "00->0  01->1  10->1  11->1",0),
        new Lesson("3. NOR",
            "A+Bが1未満、つまり両方0だけON。LESS(1)です。",
            "A --+\n    +--> SUM --> LESS(1) --> NOR\nB --+",
            "NOR=LESS_1(A+B)",
            "00->1  01->0  10->0  11->0",0),
        new Lesson("4. AND",
            "A+Bが2以上になるのは11だけ。MORE(2)です。",
            "A --+\n    +--> SUM --> MORE(2) --> AND\nB --+",
            "AND=MORE_2(A+B)",
            "00->0  01->0  10->0  11->1",0),
        new Lesson("5. NAND",
            "A+Bが2未満ならON。LESS(2)だけでNANDになります。",
            "A --+\n    +--> SUM --> LESS(2) --> NAND\nB --+",
            "NAND=LESS_2(A+B)",
            "00->1  01->1  10->1  11->0",0),
        new Lesson("6. XOR — 範囲を作る",
            "XORはS=1だけON。MORE(1)とLESS(2)を同時に満たす『窓』を作り、最後にAND相当のMORE(2)でまとめます。",
            "S=A+B\nM=MORE(1,S) --+\n                +--> MORE(2) --> XOR\nL=LESS(2,S) --+",
            "XOR=MORE_2(MORE_1(S)+LESS_2(S))",
            "S=0->0  S=1->1  S=2->0",0),
        new Lesson("7. 半加算器",
            "1bit+1bit。和bitはXOR、桁上がりはAND=MORE(2)。",
            "A,B --> XOR ------> SUM\n  \\--> MORE(2) ---> CARRY",
            "SUM=XOR(A,B)\nCARRY=MORE_2(A+B)",
            "00->00  01->01  10->01  11->10",0),
        new Lesson("8. 全加算器",
            "A+B+Cin。COUTは3入力のうち2個以上が1ならよいのでMORE(2)で直接作れます。",
            "A,B --> XOR --> X --+--> XOR --> SUM\nCin ----------------+\nA+B+Cin --> MORE(2) --> COUT",
            "SUM=XOR(XOR(A,B),Cin)\nCOUT=MORE_2(A+B+Cin)",
            "111 -> SUM=1,COUT=1\n011 -> 0,1\n001 -> 1,0",1),
        new Lesson("9. 半減算器",
            "Ce3では負の重みを許します。D=A-Bを作り、D<0なら借りが必要なのでLESS(0)です。",
            "A(+1) --+\n         +--> D=A-B --> LESS(0) --> BORROW\nB(-1) --+\nA,B --> XOR --------------------> DIFF",
            "DIFF=XOR(A,B)\nBORROW=LESS_0(A-B)",
            "0-1: D=-1 -> borrow=1\n1-0: D=+1 -> borrow=0",0),
        new Lesson("10. 全減算器",
            "A-B-Bin。借りは重み付き和が負かどうかで直接判断できます。",
            "A(+1),B(-1),Bin(-1) --> D --> LESS(0) --> BOUT\nA,B,Bin --> XOR chain -----------------> DIFF",
            "DIFF=XOR(XOR(A,B),Bin)\nBOUT=LESS_0(A-B-Bin)",
            "0-1-0 -> D=-1, diff=1, borrow=1\n1-1-1 -> D=-1, diff=1, borrow=1",1),
        new Lesson("11. 1bit掛け算",
            "1bit同士の掛け算はANDそのものです。",
            "A,B --> MORE(2) --> PRODUCT",
            "PRODUCT=MORE_2(A+B)",
            "1x1=1、それ以外=0",0),
        new Lesson("12. 2bit掛け算",
            "掛け算は『部分積AND + 加算器』。各bit同士をANDし、桁位置をずらして足します。",
            "p00=A0&B0 -> R0\np01=A0&B1 --+\np10=A1&B0 --+--> HALF ADDER -> R1,c1\np11=A1&B1 --+\nc1 ----------+--> HALF ADDER -> R2,R3",
            "pij=AND(Ai,Bj)\nR0=p00\nR1=XOR(p01,p10)\nc1=AND(p01,p10)\nR2=XOR(p11,c1)\nR3=AND(p11,c1)",
            "11 x 11 = 1001 (9)\n10 x 11 = 0110 (6)",2),
        new Lesson("13. n bit掛け算",
            "各B[j]でAをENABLEし、j桁左へ配線して部分積を作り、加算器ツリーで合計します。",
            "A&B0 <<0 --+\nA&B1 <<1 --+\nA&B2 <<2 --+--> ADDER TREE --> PRODUCT\n...         +",
            "partial_j=(A AND replicated Bj)<<j\nPRODUCT=sum(partial_j)",
            "AND=MORE(2)\nSHIFT=配線位置\nADD=Half/Full Adder",2),
        new Lesson("14. ENABLE",
            "ENABLEは計算を通す/止める制御。DATAとENABLEのANDとして実装できます。",
            "DATA ----> [GATE] ----> OUT\n             ^\n           ENABLE",
            "OUT=AND(DATA,ENABLE)",
            "EN=0なら常に0、EN=1ならDATAを通す",0),

        new Lesson("15. 大小比較器",
            "比較はMORE/LESSが最も直接的に使える場所です。D=X-Yを作れば、符号だけでX<Y / X>=Yが分かります。整数ならD=0も窓判定で検出できます。",
            "X(+1),Y(-1) --> D=X-Y --+--> LESS(0): X<Y\n                           +--> MORE(0): X>=Y\n                           +--> [0<=D<1]: X==Y",
            "LT=LESS_0(D)\nGE=MORE_0(D)\nEQ=AND(MORE_0(D),LESS_1(D))",
            "X=2,Y=3 -> D=-1 -> LT=1\nX=3,Y=3 -> D=0 -> EQ=1",2),
        new Lesson("16. シフタ",
            "シフトは比較器ではなく『配線』です。左シフトはbitを上位側へ1本ずらし、右シフトは下位側へずらします。MORE/LESS回路から作ったbitを次段へ運ぶ基本操作です。",
            "X1 X0 --LEFT--> X1 X0 0\nX1 X0 --RIGHT--> 0 X1",
            "LEFT1(X)=X<<1\nRIGHT1(X)=X>>1",
            "2bit 11 左1 -> 0110\n2bit 11 右1 -> 0001",2),
        new Lesson("17. インクリメンタ",
            "X+1は加算器の簡略版。最下位bitを反転し、元のbitが1だったときだけcarryを次桁へ送ります。",
            "X0 --> NOT ------> R0\nX0 ----------> carry\nX1,carry --> XOR -> R1\nX1,carry --> AND -> overflow",
            "R0=NOT(X0)\nc=X0\nR1=XOR(X1,c)\nOV=AND(X1,c)",
            "00+1=001\n01+1=010\n10+1=011\n11+1=100",2),
        new Lesson("18. 2bit ALU",
            "ALUは計算器を複数並べ、OPでどの結果を出力へ通すか選ぶものです。ADD/SUB/AND/ORを同時に計算して、ENABLE/ROUTEで1本だけ選択します。",
            "X,Y --> ADD --+\n      --> SUB --+\n      --> AND --+--> ROUTE(OP) --> OUT\n      --> OR  --+",
            "OP0: X+Y\nOP1: X-Y\nOP2: X AND Y\nOP3: X OR Y",
            "ROUTEはswitch相当。ただし各枝の入口をENABLEで開閉しても同じ役割を作れます。",2),
        new Lesson("19. SRラッチ",
            "ここから『状態』です。NORを2個交差結合すると、入力が戻ってもQを保持できます。MORE/LESSから作ったNORを使うので基本素子は同じです。",
            "      +---- NOR <---- R\n      |      |\nS --> NOR ---+----> Q\n      |\n      +------------ feedback",
            "S=1,R=0 -> SET Q=1\nS=0,R=1 -> RESET Q=0\nS=0,R=0 -> HOLD\nS=1,R=1 -> INVALID",
            "状態保持がオートマトンの土台。現在の入力だけでなく過去の結果が次の計算へ残ります。",3),
        new Lesson("20. 2bitレジスタ",
            "ラッチをbit数だけ並べ、LOAD=1の瞬間に入力を保存します。LOAD=0なら前の値を保持します。",
            "D1 D0 --> [2bit REGISTER] --> Q1 Q0\n              ^\n             LOAD",
            "if LOAD: Q_next=D\nelse: Q_next=Q",
            "レジスタ=状態を持つbitの束。ALU結果を保存するとCPUのデータパスになります。",3),
        new Lesson("21. 2bitカウンタ",
            "レジスタの出力をインクリメンタへ戻すとカウンタになります。STEPごとにQ=Q+1、2bitなので3の次は0へ戻ります。",
            "Q --> INCREMENTER --> D\n^                  |\n+---- REGISTER <---+  (STEP)",
            "Q_next=(Q+1) mod 4",
            "00 -> 01 -> 10 -> 11 -> 00 ...",3),
        new Lesson("22. 除算器",
            "最小の除算は『繰り返し減算』です。R=Xから始め、D=R-Yが0以上(MORE(0))の間だけ減算を受理し、その回数をQとして数えます。",
            "R=X\nloop: D=R-Y\n      MORE(0,D)? --yes--> R=D, Q=Q+1\n                  \\no--> STOP",
            "Q=floor(X/Y)\nR=X-Q*Y",
            "3/2: 3-2=1 (MORE) -> Q=1\n1-2=-1 (LESS) -> stop\nresult Q=1,R=1",2),
        new Lesson("23. 有限オートマトン",
            "STATEを持ち、入力条件で次STATEへ遷移します。比較器が『条件判定』、ENABLEが『遷移許可』、STATEが『履歴』、ROUTEが『次状態の選択』です。",
            "STATE --input/COMPARE--> ROUTE --> NEXT STATE\n  ^                              |\n  +----------- REGISTER <--------+",
            "if ENABLE=0: hold\nif ENABLE=1 and B=0: IDLE->WORK->DONE->IDLE\nif B=1: reset to IDLE",
            "これはdecision treeではなく、feedbackを持つ有向グラフです。",3),
        new Lesson("24. 最小CPUデータパス",
            "レジスタ(ACC) + ALU + ROUTE + STEPをつなぐと、小型CPUの核になります。命令(OP)がALU機能を選び、STEPで結果をACCへ保存します。",
            "OPERAND --> ALU(OP) --> ACC register --+\n              ^                 |\n              +------ feedback -+\nSTEP: ALU result -> ACC",
            "OP0 ADD: ACC=ACC+X\nOP1 SUB: ACC=ACC-X\nOP2 AND: ACC=ACC AND X\nOP3 OR : ACC=ACC OR X",
            "MORE/LESS -> logic -> adder/subtractor -> ALU -> register -> automaton。ここまでが一本の構成です。",3)
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,251));

        LinearLayout top=new LinearLayout(this);
        Button back=btn("← 戻る"); back.setOnClickListener(v->finish());
        header=tv("",14); header.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(back,new LinearLayout.LayoutParams(dp(90),dp(48)));
        top.addView(header,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(top);

        ScrollView sv=new ScrollView(this);
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(14),dp(8),dp(14),dp(24));
        sv.addView(body); root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout nav=new LinearLayout(this);
        prevBtn=btn("← 前"); nextBtn=btn("次 →");
        prevBtn.setOnClickListener(v->{if(lesson>0){lesson--;render();}});
        nextBtn.setOnClickListener(v->{if(lesson<lessons.length-1){lesson++;render();}});
        nav.addView(prevBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        nav.addView(nextBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(nav);

        setContentView(root); render();
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView tv(String s,int sp){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.rgb(23,32,51));v.setPadding(dp(6),dp(6),dp(6),dp(6));return v;
    }
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private TextView block(String label,String text){
        TextView v=tv(label+"\n"+text,14);v.setBackgroundColor(Color.WHITE);v.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(10));v.setLayoutParams(lp);return v;
    }

    private void render(){
        body.removeAllViews();
        Lesson L=lessons[lesson];
        header.setText("MORE / LESS Tutorial  "+(lesson+1)+"/"+lessons.length);
        TextView title=tv(L.title,22);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(title);
        body.addView(block("考え方",L.concept));
        TextView c=block("回路構成",L.circuit);c.setTypeface(Typeface.MONOSPACE);body.addView(c);
        TextView f=block("式",L.formula);f.setTypeface(Typeface.MONOSPACE);body.addView(f);

        TextView h=tv("動かして確認",17);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(h);
        LinearLayout inputs=new LinearLayout(this);
        aBtn=btn("");bBtn=btn("");cBtn=btn("");
        inputs.addView(aBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        inputs.addView(bBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        inputs.addView(cBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(inputs);
        aBtn.setOnClickListener(v->{handleA();refreshLive();});
        bBtn.setOnClickListener(v->{handleB();refreshLive();});
        cBtn.setOnClickListener(v->{handleC();refreshLive();});

        live=block("ライブ結果","");live.setTypeface(Typeface.MONOSPACE);body.addView(live);
        TextView q=block("真理値表 / 例",L.truth);q.setTypeface(Typeface.MONOSPACE);body.addView(q);
        prevBtn.setEnabled(lesson>0);nextBtn.setEnabled(lesson<lessons.length-1);
        refreshLive();
    }

    private void handleA(){
        if(lesson==19){a=1-a;return;}
        if(lesson==20){x2=(x2+1)&3;return;}
        if(lesson==21){counter2=(counter2+1)&3;return;}
        if(lesson==22){x2=(x2+1)&3;return;}
        if(lesson==23){a=1-a;return;}
        if(lesson==24){x2=(x2+1)&3;return;}
        if(lessons[lesson].inputMode==2)x2=(x2+1)&3; else a=1-a;
    }
    private void handleB(){
        if(lesson==19){b=1-b;applyLatch();return;}
        if(lesson==20){reg2=0;return;}
        if(lesson==21){counter2=0;return;}
        if(lesson==22){y2=(y2%3)+1;return;}
        if(lesson==23){b=1-b;return;}
        if(lesson==24){aluOp=(aluOp+1)&3;return;}
        if(lessons[lesson].inputMode==2)y2=(y2+1)&3; else b=1-b;
    }
    private void handleC(){
        if(lesson==8||lesson==10){cin=1-cin;return;}
        if(lesson==16){shiftLeft=!shiftLeft;return;}
        if(lesson==18){aluOp=(aluOp+1)&3;return;}
        if(lesson==19){applyLatch();return;}
        if(lesson==20){reg2=x2;return;}
        if(lesson==23){fsmStep();return;}
        if(lesson==24){cpuExecute();return;}
    }

    private int more(int s,int t){return s>=t?1:0;}
    private int less(int s,int t){return s<t?1:0;}
    private int and(int x,int y){return more(x+y,2);}
    private int or(int x,int y){return more(x+y,1);}
    private int not(int x){return less(x,1);}
    private int nand(int x,int y){return less(x+y,2);}
    private int nor(int x,int y){return less(x+y,1);}
    private int xor(int x,int y){int s=x+y;return more(more(s,1)+less(s,2),2);}

    private void applyLatch(){
        if(a==1&&b==0)latchQ=true;
        else if(a==0&&b==1)latchQ=false;
    }
    private void fsmStep(){
        if(b==1){fsmState=0;return;}
        if(a==0)return;
        fsmState=(fsmState+1)%3;
    }
    private void cpuExecute(){
        int v;
        if(aluOp==0)v=cpuAcc+x2;
        else if(aluOp==1)v=cpuAcc-x2;
        else if(aluOp==2)v=cpuAcc&x2;
        else v=cpuAcc|x2;
        cpuAcc=v&3;
    }
    private String opName(){return new String[]{"ADD","SUB","AND","OR"}[aluOp];}
    private String fsmName(){return new String[]{"IDLE","WORK","DONE"}[fsmState];}

    private void setButtons(String A,String B,String C,boolean showB,boolean showC){
        aBtn.setText(A);bBtn.setText(B);cBtn.setText(C);
        bBtn.setVisibility(showB?View.VISIBLE:View.INVISIBLE);
        cBtn.setVisibility(showC?View.VISIBLE:View.GONE);
    }

    private void refreshLive(){
        String s="";
        if(lesson<=14){
            Lesson L=lessons[lesson];
            if(L.inputMode==2)setButtons("X="+x2+" ("+bits2(x2)+")","Y="+y2+" ("+bits2(y2)+")","",true,false);
            else setButtons("A="+a,"B="+b,"Cin="+cin,lesson!=1,L.inputMode==1);

            switch(lesson){
                case 0:{int z=a+b;s="S="+z+"\nMORE1="+more(z,1)+" LESS1="+less(z,1)+"\nMORE2="+more(z,2)+" LESS2="+less(z,2);break;}
                case 1:s="NOT="+not(a)+" = LESS(1,A)";break;
                case 2:s="OR="+or(a,b)+" = MORE(1,A+B)";break;
                case 3:s="NOR="+nor(a,b)+" = LESS(1,A+B)";break;
                case 4:s="AND="+and(a,b)+" = MORE(2,A+B)";break;
                case 5:s="NAND="+nand(a,b)+" = LESS(2,A+B)";break;
                case 6:{int z=a+b,m=more(z,1),l=less(z,2);s="S="+z+"\nM="+m+" L="+l+"\nXOR="+more(m+l,2);break;}
                case 7:{int sm=xor(a,b),co=and(a,b);s=a+"+"+b+" -> CARRY,SUM = "+co+sm;break;}
                case 8:{int x=xor(a,b),sm=xor(x,cin),co=more(a+b+cin,2);s="X="+x+"\nSUM="+sm+" COUT="+co+"\nresult="+co+sm;break;}
                case 9:{int d=a-b;s="D="+d+"\nDIFF="+xor(a,b)+"\nBORROW="+less(d,0);break;}
                case 10:{int d=a-b-cin;s="D="+d+"\nDIFF="+xor(xor(a,b),cin)+"\nBOUT="+less(d,0);break;}
                case 11:s=a+" x "+b+" = "+and(a,b);break;
                case 12:{
                    int a0=x2&1,a1=(x2>>1)&1,b0=y2&1,b1=(y2>>1)&1;
                    int p00=and(a0,b0),p01=and(a0,b1),p10=and(a1,b0),p11=and(a1,b1);
                    int r0=p00,r1=xor(p01,p10),c1=and(p01,p10),r2=xor(p11,c1),r3=and(p11,c1);
                    int p=r0+(r1<<1)+(r2<<2)+(r3<<3);
                    s="p00="+p00+" p01="+p01+" p10="+p10+" p11="+p11+"\nR="+bits4(p)+" = "+p;break;
                }
                case 13:s="partial0="+bits4((y2&1)!=0?x2:0)+"\npartial1="+bits4((y2&2)!=0?(x2<<1):0)+"\nPRODUCT="+bits4(x2*y2);break;
                default:s="DATA="+a+" ENABLE="+b+" -> OUT="+and(a,b);break;
            }
        }else if(lesson==15){
            setButtons("X="+x2,"Y="+y2,"",true,false);
            int d=x2-y2,lt=less(d,0),ge=more(d,0),eq=and(ge,less(d,1));
            s="D=X-Y="+d+"\nLT="+lt+"  GE="+ge+"  EQ="+eq+"\nGT="+more(d,1)+"  LE="+less(d,1);
        }else if(lesson==16){
            setButtons("X="+x2+" ("+bits2(x2)+")","Y unused",shiftLeft?"LEFT":"RIGHT",false,true);
            int r=shiftLeft?(x2<<1):(x2>>1);
            s=(shiftLeft?"LEFT1":"RIGHT1")+"\n"+bits2(x2)+" -> "+bits4(r)+" = "+r;
        }else if(lesson==17){
            setButtons("X="+x2+" ("+bits2(x2)+")","", "",false,false);
            int x0=x2&1,x1=(x2>>1)&1,r0=not(x0),c=x0,r1=xor(x1,c),ov=and(x1,c);
            int r=r0+(r1<<1)+(ov<<2);
            s="x0="+x0+" -> NOT="+r0+"\ncarry="+c+"\nr1=XOR(x1,carry)="+r1+"\noverflow="+ov+"\nRESULT="+bits3(r)+" = "+r;
        }else if(lesson==18){
            setButtons("X="+x2,"Y="+y2,"OP="+opName(),true,true);
            int out=aluResult(x2,y2,aluOp);
            s="OP="+opName()+"\nraw="+out+"\n2bit OUT="+bits2(out&3)+" ("+(out&3)+")";
        }else if(lesson==19){
            setButtons("S="+a,"R="+b,"APPLY",true,true);
            String valid=(a==1&&b==1)?"INVALID":"valid";
            s="S="+a+" R="+b+"\nQ="+(latchQ?1:0)+"\n"+valid+"\nS=R=0ならQを保持";
        }else if(lesson==20){
            setButtons("D="+x2+" ("+bits2(x2)+")","CLEAR","LOAD",true,true);
            s="INPUT D="+bits2(x2)+"\nREGISTER Q="+bits2(reg2)+"\nLOADでDを保存、CLEARで00";
        }else if(lesson==21){
            setButtons("STEP","RESET","",true,false);
            int next=(counter2+1)&3;
            s="Q="+bits2(counter2)+" ("+counter2+")\nnext via incrementer="+bits2(next)+"\nSTEPで保存";
        }else if(lesson==22){
            setButtons("X="+x2,"Y="+y2,"",true,false);
            if(y2==0){s="Y=0 は除算不可";}
            else{
                int r=x2,q=0;StringBuilder tr=new StringBuilder();
                while(true){
                    int d=r-y2;
                    tr.append("R=").append(r).append("  D=R-Y=").append(d)
                      .append("  ").append(d>=0?"MORE(0)=1":"LESS(0)=1").append("\n");
                    if(d<0)break;
                    r=d;q++;
                    if(q>8)break;
                }
                s=tr+"Q="+q+"  REM="+r;
            }
        }else if(lesson==23){
            setButtons("ENABLE="+a,"RESET="+b,"STEP",true,true);
            s="STATE="+fsmName()+"\nENABLE=0: hold\nRESET=1: STEPでIDLE\nENABLE=1: STEPで次状態";
        }else{
            setButtons("OPERAND="+x2,"OP="+opName(),"EXEC",true,true);
            int raw=aluResult(cpuAcc,x2,aluOp);
            s="ACC="+bits2(cpuAcc)+" ("+cpuAcc+")\nOP="+opName()+" operand="+x2+"\nALU raw="+raw+"\nEXEC -> ACC="+bits2(raw&3);
        }
        live.setText("ライブ結果\n"+s);
    }

    private int aluResult(int x,int y,int op){
        if(op==0)return x+y;
        if(op==1)return x-y;
        if(op==2)return x&y;
        return x|y;
    }
    private String bits2(int x){x&=3;return ""+((x>>1)&1)+(x&1);}
    private String bits3(int x){x&=7;return ""+((x>>2)&1)+((x>>1)&1)+(x&1);}
    private String bits4(int x){x&=15;return ""+((x>>3)&1)+((x>>2)&1)+((x>>1)&1)+(x&1);}
}
