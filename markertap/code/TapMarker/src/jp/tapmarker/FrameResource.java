package jp.tapmarker;
/** Small reference-counted owner for an immutable native image or its reader. */
final class FrameResource {
 private int references=1;private final Runnable dispose;private volatile boolean closed;
 FrameResource(Runnable dispose){if(dispose==null)throw new IllegalArgumentException("dispose");this.dispose=dispose;}
 synchronized void retain(){if(closed)throw new IllegalStateException("resource is closed");references++;}
 void release(){boolean last;synchronized(this){if(references<=0)throw new IllegalStateException("unbalanced frame release");last=--references==0;if(last)closed=true;}if(last)dispose.run();}
 void check(){if(closed)throw new IllegalStateException("resource is closed");}
 synchronized int references(){return references;}
}
