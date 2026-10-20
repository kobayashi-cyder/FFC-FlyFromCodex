package jp.tapmarker;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.*;

/** Shared sizes and contrast for the activity and floating controls. */
final class UiKit {
 static final int BG=0xfff3f5f7,INK=0xff18252b,MUTED=0xff53656c,GREEN=0xff14745b,LINE=0xffdce4e6;
 static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
 static TextView text(Context c,String value,int size,int color,boolean bold){TextView t=new UiStrings.DisplayText(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(c,3),1);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
 static LinearLayout card(Context c,LinearLayout parent,String title,String description){LinearLayout box=column(c);box.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,16));box.setBackground(shape(c,0xffffffff,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(c,12);parent.addView(box,p);if(!title.isEmpty())box.addView(text(c,title,18,INK,true));if(!description.isEmpty()){TextView help=text(c,description,14,MUTED,false);help.setPadding(0,dp(c,6),0,dp(c,8));box.addView(help);}return box;}
 static Button button(Context c,String label,boolean primary,Runnable action){Button b=new UiStrings.DisplayButton(c);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setGravity(Gravity.CENTER);b.setTextColor(primary?0xffffffff:INK);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,10),dp(c,8),dp(c,10),dp(c,8));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),shape(c,primary?GREEN:0xffeaf0f1,10),null));b.setOnClickListener(v->action.run());return b;}
 static void full(Context c,LinearLayout parent,Button button){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,8);parent.addView(button,p);}
 static LinearLayout row(Context c,LinearLayout parent){LinearLayout row=new LinearLayout(c);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,8);parent.addView(row,p);return row;}
 static void cell(Context c,LinearLayout row,Button button){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(row.getChildCount()>0)p.leftMargin=dp(c,8);row.addView(button,p);}
 static String concise(String state){if(state==null||state.isEmpty())return "待機中";String value=state.replaceFirst("^(主画面確認済み：|主画面の認識待ち：|実行：)","");int split=value.indexOf(" / ");if(split>=0)value=value.substring(0,split);return value;}
 private UiKit(){}
}
