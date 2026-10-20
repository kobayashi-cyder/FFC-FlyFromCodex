package jp.tapmarker;
/** A failed display must not cancel the remaining display initializations. */
final class VirtualPreparation {
 static final int NEW=0,CREATING=1,WAITING=2,CONFIRMED=3,FAILED=4;
 static final int IN_PROGRESS=0,ALL_READY=1,FINISHED_WITH_ERRORS=2,CANCELLED=3;
 private final int[] states;private boolean cancelled,runRequested;
 VirtualPreparation(int count){this(count,true);}
 VirtualPreparation(int count,boolean run){if(count<1)throw new IllegalArgumentException("empty display set");states=new int[count];runRequested=run;}
 synchronized boolean requestRun(){if(cancelled)return false;runRequested=true;return true;}
 synchronized boolean shouldRun(){return runRequested&&result()==ALL_READY;}
 synchronized boolean begin(int index){if(cancelled||states[index]!=NEW)return false;states[index]=CREATING;return true;}
 synchronized boolean launched(int index){if(cancelled||states[index]!=CREATING)return false;states[index]=WAITING;return true;}
 synchronized void confirm(int index){if(!cancelled&&states[index]==WAITING)states[index]=CONFIRMED;}
 synchronized void fail(int index){if(!cancelled&&states[index]!=CONFIRMED)states[index]=FAILED;}
 synchronized int state(int index){return states[index];}
 synchronized int count(int state){int count=0;for(int value:states)if(value==state)count++;return count;}
 synchronized int result(){if(cancelled)return CANCELLED;boolean failed=false;for(int state:states){if(state==NEW||state==CREATING||state==WAITING)return IN_PROGRESS;failed|=state==FAILED;}return failed?FINISHED_WITH_ERRORS:ALL_READY;}
 synchronized void cancel(){cancelled=true;runRequested=false;}
}
