package jp.tapmarker;

/** A preview touch suspends one runner; other displays keep their input epochs. */
final class ManualIntervention {
 private long until,touchUntil;private int revision;
 int begin(long now){revision++;touchUntil=now+10000;until=now+1500;return revision;}
 boolean holding(long now){return now<Math.max(until,touchUntil);}
 int release(long now,boolean cancelled){touchUntil=0;until=now+(cancelled?800:1500);return revision;}
 boolean current(int ticket){return ticket==revision;}
 void completed(int ticket,long now){if(current(ticket)){touchUntil=0;until=now+800;}}
 void reset(){revision++;until=touchUntil=0;}
}
