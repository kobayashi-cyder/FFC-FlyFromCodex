package jp.tapmarker;

import android.app.AlertDialog;
import android.content.*;
import android.content.res.Configuration;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;

/** Localization at display boundaries. Never feeds translated text into matching or storage. */
final class UiStrings {
 static String language(Context context){return context.getString(R.string.locale_id);}
 static String text(Context context,CharSequence value){
  if(value==null)return "";String raw=value.toString();
  if(context==null||"ja".equals(language(context)))return raw;
  return UiCatalog.exact(raw,id->context.getString(id));
 }
 static String status(Context context,CharSequence value){
  if(value==null)return "";String raw=value.toString();
  if(context==null||"ja".equals(language(context)))return raw;
  return UiCatalog.render(raw,id->context.getString(id));
 }
 static void hint(TextView view,CharSequence text){view.setHint(status(view.getContext(),text));}
 static Toast toast(Context context,CharSequence text,int duration){return Toast.makeText(context,status(context,text),duration);}
 static CharSequence[] array(Context context,CharSequence[] values){CharSequence[] copy=new CharSequence[values.length];for(int i=0;i<values.length;i++)copy[i]=status(context,values[i]);return copy;}
 interface Refreshable {void refreshLocale();}
 static void refresh(View root){if(root instanceof Refreshable)((Refreshable)root).refreshLocale();if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)refresh(group.getChildAt(i));}}
 static boolean languageOnly(Configuration before,Configuration after){
  if(before==null||after==null||before.getLocales().equals(after.getLocales()))return false;
  return before.orientation==after.orientation&&before.screenWidthDp==after.screenWidthDp&&before.screenHeightDp==after.screenHeightDp&&before.smallestScreenWidthDp==after.smallestScreenWidthDp&&before.densityDpi==after.densityDpi&&before.fontScale==after.fontScale;
 }
 static class DisplayText extends TextView implements Refreshable {
  private CharSequence original;
  DisplayText(Context c){super(c);}
  @Override public void setText(CharSequence text,BufferType type){original=text;super.setText(status(getContext(),text),type);}
  public void refreshLocale(){super.setText(status(getContext(),original),BufferType.NORMAL);}
 }
 static class DisplayButton extends Button implements Refreshable {
  private CharSequence original;
  DisplayButton(Context c){super(c);setAllCaps(false);}
  @Override public void setText(CharSequence text,BufferType type){original=text;super.setText(status(getContext(),text),type);}
  public void refreshLocale(){super.setText(status(getContext(),original),BufferType.NORMAL);}
 }
 static class DisplayCheck extends CheckBox implements Refreshable {
  private CharSequence original;
  DisplayCheck(Context c){super(c);}
  @Override public void setText(CharSequence text,BufferType type){original=text;super.setText(status(getContext(),text),type);}
  public void refreshLocale(){super.setText(status(getContext(),original),BufferType.NORMAL);}
 }
 static class StringAdapter extends ArrayAdapter<String> {
  StringAdapter(Context c,int resource,String[] values){super(c,resource,values);}
  StringAdapter(Context c,int resource,List<String> values){super(c,resource,values);}
  @Override public View getView(int position,View view,ViewGroup parent){return translated(super.getView(position,view,parent),position);}
  @Override public View getDropDownView(int position,View view,ViewGroup parent){return translated(super.getDropDownView(position,view,parent),position);}
  private View translated(View view,int position){TextView text=view instanceof TextView?(TextView)view:(TextView)view.findViewById(android.R.id.text1);if(text!=null)text.setText(status(getContext(),getItem(position)));return view;}
 }
 static class DialogBuilder extends AlertDialog.Builder {
  final Context context;
  DialogBuilder(Context c){super(c);context=c;}
  @Override public AlertDialog.Builder setTitle(CharSequence s){return super.setTitle(status(context,s));}
  @Override public AlertDialog.Builder setMessage(CharSequence s){return super.setMessage(status(context,s));}
  @Override public AlertDialog.Builder setPositiveButton(CharSequence s,DialogInterface.OnClickListener listener){return super.setPositiveButton(text(context,s),listener);}
  @Override public AlertDialog.Builder setNegativeButton(CharSequence s,DialogInterface.OnClickListener listener){return super.setNegativeButton(text(context,s),listener);}
  @Override public AlertDialog.Builder setNeutralButton(CharSequence s,DialogInterface.OnClickListener listener){return super.setNeutralButton(text(context,s),listener);}
  @Override public AlertDialog.Builder setItems(CharSequence[] values,DialogInterface.OnClickListener listener){return super.setItems(array(context,values),listener);}
  @Override public AlertDialog.Builder setSingleChoiceItems(CharSequence[] values,int selected,DialogInterface.OnClickListener listener){return super.setSingleChoiceItems(array(context,values),selected,listener);}
  @Override public AlertDialog.Builder setMultiChoiceItems(CharSequence[] values,boolean[] selected,DialogInterface.OnMultiChoiceClickListener listener){return super.setMultiChoiceItems(array(context,values),selected,listener);}
 }
 private UiStrings(){}
}
