package jp.tapmarker;
/** AOSP flags; removing a display must never reparent its game onto display 0. */
final class VirtualDisplayPolicy {
 static final int DESTROY_ON_REMOVAL=1<<8,TRUSTED=1<<10,OWN_FOCUS=1<<14,NO_STEAL_FOCUS=1<<16;
 static int flags(int sdk,boolean shell){int flags=1|2|8|64|DESTROY_ON_REMOVAL;if(shell&&sdk>=33)flags|=TRUSTED;if(shell&&sdk>=34)flags|=OWN_FOCUS|NO_STEAL_FOCUS;return flags;}
}
