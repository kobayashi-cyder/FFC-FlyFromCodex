package jp.tapmarker;
/** One outstanding operation per display; other displays remain independent. */
final class DisplayLane {
 private boolean busy;private long generation;
 synchronized long enter(){if(busy)return -1;busy=true;return ++generation;}
 synchronized boolean valid(long token){return busy&&token==generation;}
 synchronized void complete(long token){if(token==generation)busy=false;}
 synchronized void cancel(){generation++;busy=false;}
}
