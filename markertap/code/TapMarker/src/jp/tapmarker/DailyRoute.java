package jp.tapmarker;
import java.util.*;

/** A finite, cancellable daily run. Completion never comes from a retry timeout. */
final class DailyRoute {
 private String[] apps=new String[0];private int index;private boolean active;private long generation;
 long start(String[] targets){LinkedHashSet<String> unique=new LinkedHashSet<>();for(String app:targets)if(app!=null&&!app.trim().isEmpty())unique.add(app);if(unique.isEmpty())throw new IllegalArgumentException("巡回するアプリを選択してください");apps=unique.toArray(new String[unique.size()]);index=0;active=true;return ++generation;}
 void stop(){active=false;generation++;}
 boolean active(){return active;}long generation(){return generation;}
 String current(){return active?apps[index]:"";}int position(){return index+1;}int size(){return apps.length;}
 boolean complete(String app,long run){if(!active||run!=generation||!current().equals(app))return false;index++;if(index==apps.length)active=false;return true;}
}
