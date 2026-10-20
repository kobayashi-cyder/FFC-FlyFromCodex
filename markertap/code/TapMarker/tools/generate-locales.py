#!/usr/bin/env python3
"""Generate Android resources and the display-only catalog; never rewrite stored data."""
from pathlib import Path
from xml.sax.saxutils import escape
import hashlib,json,re

ROOT=Path(__file__).resolve().parent.parent
rows=[]
for line in (ROOT/'tools/ui-translations.tsv').read_text().splitlines():
    if not line or line.startswith('#'):continue
    ja,en=line.split('\t',1)
    rows.append((ja.replace('\\n','\n'),en.replace('\\n','\n')))
extra=ROOT/'tools/ui-translations-long.json'
if extra.exists():
    for row in json.loads(extra.read_text()):
        source=(ROOT/row['file']).read_text()
        candidates=[json.loads(x.group()) for x in re.finditer(r'"(?:\\.|[^"\\])*"',source)]
        matches=[x for x in candidates if x.startswith(row['starts_with'])]
        assert len(set(matches))==1,(row['starts_with'],len(set(matches)))
        rows.append((matches[0],row['en']))
entries={}
for ja,en in rows:
    if ja in entries:assert entries[ja]==en,ja
    entries[ja]=en

def xmltext(text):
    return escape(text).replace('\\','\\\\').replace('\n','\\n').replace("'","\\'").replace('"','\\"')

fixed={
    'startup_width_value':('幅 %1$dpx','Width: %1$dpx'),
    'startup_geometry_value':('%1$d × %2$dpx / %3$d dpi','%1$d × %2$dpx / %3$d dpi'),
    'startup_next_geometry':('次回の予定：%1$s','Next startup: %1$s'),
    'locale_id':('ja','en'),
    'app_name':('マーカー連打 軽量版','MarkerTap Lite'),
    'service_description':('マーカー連打と、教えた画面でのタップ・スワイプ・戻る操作を行います。自律モードは画面画像と対象アプリ名を端末内で処理します。端末内接続では自分の端末のADBに接続します。操作パネルで停止できます。','Repeats marker taps and performs taught taps, swipes, and Back actions. Automation processes screen images and target app names on this device. On-device connection uses ADB on this device. Stop automation from the control panel.'),
    'virtual_limit_exceeded':('仮想画面は%1$dつまでです。稼働数か対象アプリを選び直してください。','Use up to %1$d virtual displays. Change the concurrency setting or app selection.'),
    'virtual_app_selection_title':('対象アプリを選択（仮想%1$d画面まで）','Choose apps (up to %1$d virtual displays)'),
    'virtual_concurrency_value':('同時稼働数：%1$dアプリ','Concurrent apps: %1$d'),
    'virtual_count':('仮想%1$s画面','%1$s virtual displays'),
    'display_count':('%1$s画面','%1$s displays'),
    'sent_count':('送信 %1$s回','Sent: %1$s'),
    'times_count':('%1$s回','%1$s times'),
    'stop_reason_value':('停止理由：%1$s','Stop reason: %1$s'),
    'target_input_rechecking':('入力を再確認中（詳しい状況を参照）','Checking input (see Details)'),
    'count_of_ten':('確認中：%1$s / 10','Testing: %1$s / 10'),
    'tap_received':('受信：押す %1$s・離す %2$s','Received: press %1$s / release %2$s'),
    'locale_settings_unavailable':('言語設定を開けませんでした。Androidの設定から変更してください。','Could not open language settings. Change the language in Android Settings.'),
}
ids={ja:'ui_'+hashlib.sha256(ja.encode()).hexdigest()[:12] for ja in entries}
for locale,column in [('values',1),('values-en',1),('values-ja',0)]:
    target=ROOT/'res'/locale/'strings.xml';target.parent.mkdir(parents=True,exist_ok=True)
    lines=['<?xml version="1.0" encoding="utf-8"?>','<resources>']
    for name,pair in fixed.items():lines.append('  <string name="'+name+'">'+xmltext(pair[column])+'</string>')
    for ja,en in entries.items():lines.append('  <string name="'+ids[ja]+'" formatted="false">'+xmltext(ja if column==0 else en)+'</string>')
    lines.append('</resources>');target.write_text('\n'.join(lines)+'\n')

java=['package jp.tapmarker;','import java.util.*;','import java.util.function.IntFunction;','import java.util.regex.*;',
      '/** Generated display catalog. Canonical automaton strings are never modified. */',
      'final class UiCatalog {',' static final Map<String,Integer> IDS=new HashMap<>();',' static {']
for ja in entries:java.append('  IDS.put('+json.dumps(ja,ensure_ascii=False)+',R.string.'+ids[ja]+');')
java+=[' }',' static String exact(String text,IntFunction<String> resolve){Integer id=IDS.get(text);return id==null?text:resolve.apply(id);}',r'''
 static final Pattern VIRTUAL=Pattern.compile("^仮想([0-9]+)画面$"),DISPLAYS=Pattern.compile("^([0-9]+)画面$"),TIMES=Pattern.compile("^([0-9]+)回$");
 static String render(String raw,IntFunction<String> resolve){
  if(raw==null)return "";String exact=exact(raw,resolve);if(!exact.equals(raw))return exact;
  String[] lines=raw.split("\\n",-1);for(int i=0;i<lines.length;i++)lines[i]=line(lines[i],resolve,0);return join(lines,"\n");
 }
 static String line(String raw,IntFunction<String> resolve,int depth){
  String exact=exact(raw,resolve);if(!exact.equals(raw)||depth>4)return exact;
  if(raw.contains(" / ")){String[] pieces=raw.split(" / ",-1);for(int i=0;i<pieces.length;i++)pieces[i]=line(pieces[i],resolve,depth+1);return join(pieces," / ");}
  int colon=raw.indexOf('：');if(colon>0){String left=raw.substring(0,colon),right=raw.substring(colon+1),a=exact(left,resolve),b=line(right,resolve,depth+1);if(!a.equals(left)||!b.equals(right))return a+": "+b;}
  Matcher count=VIRTUAL.matcher(raw);if(count.matches())return String.format(Locale.ROOT,resolve.apply(R.string.virtual_count),count.group(1));
  count=DISPLAYS.matcher(raw);if(count.matches())return String.format(Locale.ROOT,resolve.apply(R.string.display_count),count.group(1));
  count=TIMES.matcher(raw);if(count.matches())return String.format(Locale.ROOT,resolve.apply(R.string.times_count),count.group(1));
  return raw;
 }
 static String join(String[] values,String separator){StringBuilder text=new StringBuilder();for(int i=0;i<values.length;i++){if(i>0)text.append(separator);text.append(values[i]);}return text.toString();}
''',' private UiCatalog(){}','}']
(ROOT/'src/jp/tapmarker/UiCatalog.java').write_text('\n'.join(java)+'\n')
(ROOT/'tools/ui-catalog-index.json').write_text(json.dumps({'entries':len(entries),'ids':ids},ensure_ascii=False,indent=2)+'\n')
print('Generated',len(entries),'Japanese/English display entries and',len(fixed),'named resources.')
