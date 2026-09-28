package dev.ffc.ce3;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

@SuppressLint("SetTextI18n")
public class ModuleBuilderActivity extends Activity {
    private final ModuleCore.Library lib = new ModuleCore.Library();
    private long selectedId = -1;
    private LinearLayout moduleList;
    private LinearLayout editor;
    private TextView selectedSummary;
    private TextView advanced;
    private TextView demo;
    private TextView canvasText;
    private SeekBar inputA, inputB;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ce3_module_builder", MODE_PRIVATE);
        load();
        if (lib.items.isEmpty()) {
            ModuleCore.Spec s = lib.create("THRESHOLD");
            s.name = "My First Module";
            selectedId = s.id;
        } else {
            selectedId = lib.items.get(0).id;
        }
        buildUi();
        refreshAll();
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView tv(String s,int sp){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.rgb(23,32,51));v.setPadding(dp(6),dp(6),dp(6),dp(6));return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setMinHeight(dp(48));return b;}
    private TextView header(String s){TextView v=tv(s,18);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(dp(2),dp(14),dp(2),dp(6));return v;}
    private TextView card(String title,String body){
        TextView v=tv(title+"\n"+body,14);
        v.setBackgroundColor(Color.WHITE);
        v.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(10));v.setLayoutParams(lp);return v;
    }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,251));

        LinearLayout top=new LinearLayout(this);
        Button back=btn("← 戻る"); back.setOnClickListener(v->finish());
        TextView title=tv("Ce3 Module Builder",22); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(back,new LinearLayout.LayoutParams(dp(90),dp(52)));
        top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(top);

        TextView sub=tv("作る → 保存 → COPY / INSTANCE / FORK → 組む → 試す",13);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(dp(14),0,dp(14),dp(8));
        root.addView(sub);

        ScrollView sv=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(4),dp(14),dp(28));
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        body.addView(card("考え方",
                "MORE/LESSの数値を毎回直接調整する代わりに、感度・必要入力・抑制・記憶という意味で設定します。\n"+
                "詳細値は下のADVANCEDに自動変換されます。"));

        body.addView(header("1. LIBRARYから部品を作る"));
        LinearLayout p1=new LinearLayout(this);
        addTemplateButton(p1,"THRESHOLD","THRESHOLD");
        addTemplateButton(p1,"LESS","LESS");
        addTemplateButton(p1,"GATE","GATE");
        body.addView(p1);
        LinearLayout p2=new LinearLayout(this);
        addTemplateButton(p2,"MEMORY","MEMORY");
        addTemplateButton(p2,"INTEGRATOR","INTEGRATOR");
        body.addView(p2);

        body.addView(header("2. MY MODULES"));
        moduleList=new LinearLayout(this);
        moduleList.setOrientation(LinearLayout.VERTICAL);
        body.addView(moduleList);

        selectedSummary=card("選択中","");
        body.addView(selectedSummary);

        LinearLayout actions1=new LinearLayout(this);
        Button copy=btn("COPY");
        Button inst=btn("INSTANCE");
        Button fork=btn("FORK");
        copy.setOnClickListener(v->{ModuleCore.Spec s=lib.copy(selectedId);selectNew(s);});
        inst.setOnClickListener(v->{ModuleCore.Spec s=lib.instance(selectedId);selectNew(s);});
        fork.setOnClickListener(v->{ModuleCore.Spec s=lib.fork(selectedId);selectNew(s);});
        actions1.addView(copy,new LinearLayout.LayoutParams(0,dp(52),1));
        actions1.addView(inst,new LinearLayout.LayoutParams(0,dp(52),1));
        actions1.addView(fork,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(actions1);

        LinearLayout actions2=new LinearLayout(this);
        Button templ=btn("SAVE TEMPLATE");
        Button del=btn("DELETE");
        Button source=btn("SOURCE");
        templ.setOnClickListener(v->{ModuleCore.Spec s=lib.template(selectedId);selectNew(s);});
        del.setOnClickListener(v->{deleteSelected();});
        source.setOnClickListener(v->{goSource();});
        actions2.addView(templ,new LinearLayout.LayoutParams(0,dp(52),1));
        actions2.addView(source,new LinearLayout.LayoutParams(0,dp(52),1));
        actions2.addView(del,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(actions2);

        body.addView(card("COPY / INSTANCE / FORK",
                "COPY: 独立コピー。元を変えても変わりません。\n"+
                "INSTANCE: 元の設定を参照。元を変えると反映します。\n"+
                "FORK: 現在値から分岐し、その後は独立します。"));

        body.addView(header("3. EASY SETTINGS"));
        editor=new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        body.addView(editor);

        advanced=card("ADVANCED","");
        advanced.setTypeface(Typeface.MONOSPACE);
        body.addView(advanced);

        body.addView(header("4. この部品を試す"));
        inputA=sliderWithLabel(body,"INPUT A",60);
        inputB=sliderWithLabel(body,"INPUT B",40);
        SeekBar.OnSeekBarChangeListener inListener=new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){refreshDemo(false);}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        };
        inputA.setOnSeekBarChangeListener(inListener);
        inputB.setOnSeekBarChangeListener(inListener);

        LinearLayout demoBtns=new LinearLayout(this);
        Button preview=btn("PREVIEW");
        Button step=btn("STEP");
        Button reset=btn("RESET STATE");
        preview.setOnClickListener(v->refreshDemo(false));
        step.setOnClickListener(v->{refreshDemo(true);save();});
        reset.setOnClickListener(v->{ModuleCore.Spec s=lib.find(selectedId);if(s!=null)s.state=0;refreshDemo(false);save();});
        demoBtns.addView(preview,new LinearLayout.LayoutParams(0,dp(52),1));
        demoBtns.addView(step,new LinearLayout.LayoutParams(0,dp(52),1));
        demoBtns.addView(reset,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(demoBtns);

        demo=card("RESULT","");
        demo.setTypeface(Typeface.MONOSPACE);
        body.addView(demo);

        body.addView(header("5. CANVASで再利用"));
        LinearLayout canvasBtns=new LinearLayout(this);
        Button add=btn("ADD");
        Button run=btn("RUN CHAIN");
        Button clear=btn("CLEAR");
        add.setOnClickListener(v->{if(lib.find(selectedId)!=null)lib.canvas.add(selectedId);save();refreshCanvas();});
        run.setOnClickListener(v->{runCanvas();});
        clear.setOnClickListener(v->{lib.canvas.clear();save();refreshCanvas();});
        canvasBtns.addView(add,new LinearLayout.LayoutParams(0,dp(52),1));
        canvasBtns.addView(run,new LinearLayout.LayoutParams(0,dp(52),1));
        canvasBtns.addView(clear,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(canvasBtns);
        canvasText=card("CANVAS","");
        canvasText.setTypeface(Typeface.MONOSPACE);
        body.addView(canvasText);

        body.addView(header("6. RECIPEをコピーして再利用"));
        LinearLayout recipeBtns=new LinearLayout(this);
        Button export=btn("COPY RECIPE");
        Button imp=btn("IMPORT");
        export.setOnClickListener(v->copyRecipe());
        imp.setOnClickListener(v->importRecipe());
        recipeBtns.addView(export,new LinearLayout.LayoutParams(0,dp(52),1));
        recipeBtns.addView(imp,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(recipeBtns);
        body.addView(card("Recipe",
                "選択した部品をJSONとしてクリップボードへコピーできます。別端末や別プロジェクトではIMPORTで再生成できます。"));

        setContentView(root);
    }

    private void addTemplateButton(LinearLayout row,String label,String kind){
        Button b=btn(label);
        b.setOnClickListener(v->{ModuleCore.Spec s=lib.create(kind);selectNew(s);});
        row.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));
    }

    private SeekBar sliderWithLabel(LinearLayout parent,String name,int initial){
        TextView l=tv(name+" = "+initial+"%",13); l.setTextColor(Color.DKGRAY); parent.addView(l);
        SeekBar b=new SeekBar(this);b.setMax(100);b.setProgress(initial);parent.addView(b);
        b.setTag(l);
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){((TextView)s.getTag()).setText(name+" = "+p+"%");}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });
        return b;
    }

    private void refreshAll(){
        refreshModuleList();
        refreshEditor();
        refreshDemo(false);
        refreshCanvas();
    }

    private void refreshModuleList(){
        moduleList.removeAllViews();
        for(ModuleCore.Spec s:lib.items){
            String prefix=s.id==selectedId?"▶ ":"";
            String rel=s.linked?" [INSTANCE→"+s.sourceId+"]":s.template?" [TEMPLATE]":"";
            Button b=btn(prefix+s.id+"  "+s.name+rel);
            b.setOnClickListener(v->{selectedId=s.id;refreshAll();});
            moduleList.addView(b);
        }
        if(lib.items.isEmpty())moduleList.addView(tv("まだ部品がありません。LIBRARYから作成してください。",14));
    }

    private void refreshEditor(){
        editor.removeAllViews();
        ModuleCore.Spec local=lib.find(selectedId);
        ModuleCore.Spec p=lib.effective(local);
        if(local==null||p==null){selectedSummary.setText("選択中\nなし");return;}

        selectedSummary.setText("選択中\n"+local.id+"  "+local.name+"\n"+
                (local.linked?"INSTANCE: 設定は source #"+local.sourceId+" を参照":
                 local.template?"TEMPLATE: 再利用用の設計図":"独立モジュール"));

        if(local.linked){
            editor.addView(card("INSTANCEはリンク中",
                    "EASY SETTINGSは元SOURCEで変更します。SOURCEボタンを押すと元へ移動します。"+
                    "このINSTANCE自身はSTATEだけ独立しています。"));
            updateAdvanced(local,p);
            return;
        }

        addQuickRequired();
        addEasySlider("感度",p.sensitivity, value->{p.sensitivity=value;});
        addEasySlider("必要入力",p.required, value->{p.required=value;});
        addEasySlider("抑制",p.inhibition, value->{p.inhibition=value;});
        addEasySlider("記憶",p.memory, value->{p.memory=value;});

        LinearLayout cmp=new LinearLayout(this);
        Button more=btn("以上でON (MORE)");
        Button less=btn("未満でON (LESS)");
        more.setOnClickListener(v->{p.more=true;save();refreshAll();});
        less.setOnClickListener(v->{p.more=false;save();refreshAll();});
        cmp.addView(more,new LinearLayout.LayoutParams(0,dp(52),1));
        cmp.addView(less,new LinearLayout.LayoutParams(0,dp(52),1));
        editor.addView(cmp);

        updateAdvanced(local,p);
    }

    private interface IntSetter{void set(int value);}

    private void addEasySlider(String label,int initial,IntSetter setter){
        TextView l=tv(label+" = "+initial+"%",13);
        l.setTextColor(Color.DKGRAY);
        editor.addView(l);
        SeekBar b=new SeekBar(this);b.setMax(100);b.setProgress(initial);
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){
                l.setText(label+" = "+p+"%");
                if(from){setter.set(p);save();updateAdvanced(lib.find(selectedId),lib.effective(lib.find(selectedId)));refreshDemo(false);}
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){refreshModuleList();}
        });
        editor.addView(b);
    }

    private void addQuickRequired(){
        TextView h=tv("必要入力のプリセット",13);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);editor.addView(h);
        LinearLayout row=new LinearLayout(this);
        int[] vals={25,50,75,100};
        String[] names={"少し","半分","かなり","全部"};
        for(int i=0;i<vals.length;i++){
            final int value=vals[i];
            Button b=btn(names[i]);
            b.setOnClickListener(v->{ModuleCore.Spec p=lib.effective(lib.find(selectedId));if(p!=null&&!lib.find(selectedId).linked){p.required=value;save();refreshAll();}});
            row.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        editor.addView(row);
    }

    private void updateAdvanced(ModuleCore.Spec local,ModuleCore.Spec p){
        if(advanced==null||local==null||p==null)return;
        double gain=0.5+1.5*(p.sensitivity/100.0);
        double bias=-(p.inhibition/100.0);
        double decay=0.95*(p.memory/100.0);
        advanced.setText(String.format(Locale.US,
                "ADVANCED\nkind=%s\ncomparator=%s\nthreshold=%.2f\ngain=%.2f\nbias=%.2f\nrecurrence=%.2f\nstate=%.2f\nsource=%s",
                p.kind,p.more?"MORE ≥":"LESS <",p.required/100.0,gain,bias,decay,local.state,
                local.linked?"#"+local.sourceId:"self"));
    }

    private void refreshDemo(boolean step){
        if(demo==null||inputA==null)return;
        ModuleCore.Spec s=lib.find(selectedId);
        if(s==null){demo.setText("RESULT\nNo module");return;}
        double a=inputA.getProgress()/100.0,b=inputB.getProgress()/100.0;
        ModuleCore.Result r=lib.evaluate(s,a,b,step);
        demo.setText(String.format(Locale.US,
                "RESULT\nA=%.2f  B=%.2f\n%s\nOUTPUT=%s",
                a,b,r.text,r.on?"ON":"OFF"));
        updateAdvanced(s,lib.effective(s));
    }

    private void refreshCanvas(){
        if(canvasText==null)return;
        StringBuilder sb=new StringBuilder("CANVAS\n");
        if(lib.canvas.isEmpty())sb.append("(empty)  ADDで選択部品を追加");
        for(int i=0;i<lib.canvas.size();i++){
            ModuleCore.Spec s=lib.find(lib.canvas.get(i));
            if(i>0)sb.append("\n   ↓\n");
            sb.append(i+1).append(". ").append(s==null?"missing":s.name);
        }
        canvasText.setText(sb.toString());
    }

    private void runCanvas(){
        double a=inputA.getProgress()/100.0,b=inputB.getProgress()/100.0;
        ModuleCore.Result r=lib.runCanvas(a,b,true);
        canvasText.setText(canvasText.getText()+"\n\nRUN RESULT: "+(r.on?"ON":"OFF")+"\n"+r.text);
        save();
    }

    private void selectNew(ModuleCore.Spec s){
        if(s!=null){selectedId=s.id;save();refreshAll();}
    }

    private void goSource(){
        ModuleCore.Spec s=lib.find(selectedId);
        if(s!=null&&s.linked&&lib.find(s.sourceId)!=null){selectedId=s.sourceId;refreshAll();}
    }

    private void deleteSelected(){
        if(selectedId<0)return;
        lib.delete(selectedId);
        selectedId=lib.items.isEmpty()?-1:lib.items.get(0).id;
        save();refreshAll();
    }

    private JSONObject toJson(ModuleCore.Spec s)throws Exception{
        ModuleCore.Spec p=lib.effective(s);
        JSONObject o=new JSONObject();
        o.put("name",p.name);o.put("kind",p.kind);
        o.put("sensitivity",p.sensitivity);o.put("inhibition",p.inhibition);
        o.put("memory",p.memory);o.put("required",p.required);o.put("more",p.more);
        return o;
    }

    private void copyRecipe(){
        try{
            ModuleCore.Spec s=lib.find(selectedId);if(s==null)return;
            String json=toJson(s).toString();
            ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Ce3 Recipe",json));
            selectedSummary.setText(selectedSummary.getText()+"\nRecipeをクリップボードへコピーしました。");
        }catch(Exception e){
            selectedSummary.setText("Recipe copy error: "+e.getMessage());
        }
    }

    private void importRecipe(){
        try{
            ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(!cm.hasPrimaryClip())return;
            CharSequence cs=cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            JSONObject o=new JSONObject(cs.toString());
            ModuleCore.Spec s=lib.create(o.optString("kind","THRESHOLD"));
            s.name=o.optString("name","Imported")+" Imported";
            s.sensitivity=o.optInt("sensitivity",50);
            s.inhibition=o.optInt("inhibition",0);
            s.memory=o.optInt("memory",0);
            s.required=o.optInt("required",50);
            s.more=o.optBoolean("more",true);
            selectNew(s);
        }catch(Exception e){
            selectedSummary.setText("IMPORT失敗: "+e.getMessage());
        }
    }

    private void save(){
        try{
            JSONObject root=new JSONObject();
            root.put("nextId",lib.nextId);
            JSONArray arr=new JSONArray();
            for(ModuleCore.Spec s:lib.items){
                JSONObject o=new JSONObject();
                o.put("id",s.id);o.put("name",s.name);o.put("kind",s.kind);
                o.put("template",s.template);o.put("linked",s.linked);o.put("sourceId",s.sourceId);
                o.put("sensitivity",s.sensitivity);o.put("inhibition",s.inhibition);
                o.put("memory",s.memory);o.put("required",s.required);o.put("more",s.more);o.put("state",s.state);
                arr.put(o);
            }
            root.put("items",arr);
            JSONArray canvas=new JSONArray();for(Long id:lib.canvas)canvas.put(id);
            root.put("canvas",canvas);
            prefs.edit().putString("library",root.toString()).apply();
        }catch(Exception ignored){}
    }

    private void load(){
        String raw=prefs.getString("library",null);if(raw==null)return;
        try{
            JSONObject root=new JSONObject(raw);
            lib.nextId=root.optLong("nextId",1);
            JSONArray arr=root.optJSONArray("items");
            if(arr!=null)for(int i=0;i<arr.length();i++){
                JSONObject o=arr.getJSONObject(i);
                ModuleCore.Spec s=new ModuleCore.Spec(o.getLong("id"),o.optString("name","Module"),o.optString("kind","THRESHOLD"));
                s.template=o.optBoolean("template",false);s.linked=o.optBoolean("linked",false);s.sourceId=o.optLong("sourceId",-1);
                s.sensitivity=o.optInt("sensitivity",50);s.inhibition=o.optInt("inhibition",0);
                s.memory=o.optInt("memory",0);s.required=o.optInt("required",50);s.more=o.optBoolean("more",true);s.state=o.optDouble("state",0);
                lib.items.add(s);
            }
            JSONArray canvas=root.optJSONArray("canvas");
            if(canvas!=null)for(int i=0;i<canvas.length();i++)lib.canvas.add(canvas.getLong(i));
        }catch(Exception ignored){}
    }
}
