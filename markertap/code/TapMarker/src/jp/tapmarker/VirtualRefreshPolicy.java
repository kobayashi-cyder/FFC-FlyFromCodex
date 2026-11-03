package jp.tapmarker;

import java.util.ArrayDeque;

/**
 * Per-display autonomous refresh decision engine. Pure Java 8, no Android I/O.
 * Instance must belong to ONE virtual display and use monotonic uptime.
 * Decisions authorize a single action; the Android adapter performs and verifies it.
 */
final class VirtualRefreshPolicy {
    static final long FRAME_STALE_MS = 8_000L;
    static final long FRAME_RETRY_MS = 8_000L;
    static final long REBUILD_VERIFY_MS = 25_000L;
    static final long REBUILD_WINDOW_MS = 10 * 60_000L;
    static final int MAX_REBUILDS = 2;

    enum Action { NONE, WAIT_NETWORK, WAIT_DNS, REQUEST_FRAME, RECREATE_DISPLAY, QUARANTINE }
    enum Fault { NONE, MANUAL, NETWORK, DNS, FRAME_STALE, DISPLAY_REMOVED }

    static final class Observation {
        final long now, frameAt;
        final boolean automationOn, manualInput, networkValidated, dnsFailure, displayRemoved, rebuilding;
        Observation(long now, long frameAt, boolean automationOn, boolean manualInput,
                    boolean networkValidated, boolean dnsFailure, boolean displayRemoved,
                    boolean rebuilding) {
            this.now=now;this.frameAt=frameAt;this.automationOn=automationOn;
            this.manualInput=manualInput;this.networkValidated=networkValidated;
            this.dnsFailure=dnsFailure;this.displayRemoved=displayRemoved;this.rebuilding=rebuilding;
        }
    }
    static final class Decision {
        final Fault fault;final Action action;final String reason;
        Decision(Fault fault,Action action,String reason){this.fault=fault;this.action=action;this.reason=reason;}
    }

    private final ArrayDeque<Long> rebuilds = new ArrayDeque<>();
    private long firstStaleAt = -1, frameRequestAt = -1, rebuildAt = -1, observedFrameAt = -1;
    private boolean quarantined;
    private int freshSamples;

    synchronized Decision observe(Observation o) {
        if(o.now < 0 || o.frameAt < 0 || o.frameAt > o.now) throw new IllegalArgumentException("invalid monotonic time");
        expireBudget(o.now);
        if(!o.automationOn || o.manualInput) return out(Fault.MANUAL,Action.NONE,"manual control");
        // Network symptoms must not trigger display destruction or application relaunch.
        if(!o.networkValidated) return out(Fault.NETWORK,Action.WAIT_NETWORK,"network unvalidated");
        if(o.dnsFailure) return out(Fault.DNS,Action.WAIT_DNS,"application DNS failure");
        if(o.rebuilding) return out(Fault.DISPLAY_REMOVED,Action.NONE,"rebuild in progress");
        if(quarantined) return out(Fault.FRAME_STALE,Action.QUARANTINE,"recovery budget exhausted");

        if(o.frameAt > observedFrameAt && o.frameAt > 0 && o.now-o.frameAt < FRAME_STALE_MS) {
            observedFrameAt=o.frameAt;
            if(++freshSamples >= 2) {
                firstStaleAt=frameRequestAt=rebuildAt=-1;
                // Recovery budget deliberately survives a few fresh frames;
                // flapping must not replenish a 10-minute limit.
            }
        } else if(o.frameAt==0 || o.now-o.frameAt>=FRAME_STALE_MS) {
            freshSamples=0;
        }
        if(!o.displayRemoved && o.frameAt>0 && o.now-o.frameAt<FRAME_STALE_MS)
            return out(Fault.NONE,Action.NONE,"fresh frame");

        Fault fault=o.displayRemoved?Fault.DISPLAY_REMOVED:Fault.FRAME_STALE;
        if(firstStaleAt<0) firstStaleAt=o.now;
        if(o.displayRemoved) {
            if(rebuildAt>=0 && o.now-rebuildAt<REBUILD_VERIFY_MS)
                return out(fault,Action.NONE,"display rebuild verification");
            return issueRebuild(o.now,fault,"one display removed");
        }
        if(frameRequestAt<0){frameRequestAt=o.now;return out(fault,Action.REQUEST_FRAME,"request fresh screenshot");}
        if(o.now-frameRequestAt<FRAME_RETRY_MS)
            return out(fault,Action.NONE,"waiting for screenshot");
        if(rebuildAt>=0 && o.now-rebuildAt<REBUILD_VERIFY_MS)
            return out(fault,Action.NONE,"waiting for rebuilt display");
        if(o.now-firstStaleAt<FRAME_STALE_MS+FRAME_RETRY_MS)
            return out(fault,Action.REQUEST_FRAME,"retry screenshot, not yet destructive");
        return issueRebuild(o.now,fault,"stale frames after screenshot retries");
    }
    private Decision issueRebuild(long now,Fault fault,String reason) {
        if(rebuilds.size()>=MAX_REBUILDS){quarantined=true;return out(fault,Action.QUARANTINE,"two rebuilds within 10 minutes");}
        rebuilds.addLast(now);rebuildAt=now;return out(fault,Action.RECREATE_DISPLAY,reason);
    }
    synchronized Decision manualRefresh(long now,boolean hard,boolean networkValidated,boolean dnsFailure) {
        if(now<0) throw new IllegalArgumentException("negative time");
        expireBudget(now);
        if(!networkValidated)return out(Fault.NETWORK,Action.WAIT_NETWORK,"network unvalidated");
        if(dnsFailure)return out(Fault.DNS,Action.WAIT_DNS,"application DNS failure");
        if(!hard){frameRequestAt=now;return out(Fault.FRAME_STALE,Action.REQUEST_FRAME,"manual screenshot refresh");}
        if(quarantined || rebuilds.size()>=MAX_REBUILDS){quarantined=true;return out(Fault.FRAME_STALE,Action.QUARANTINE,"rebuild budget exhausted");}
        return issueRebuild(now,Fault.FRAME_STALE,"manual display refresh");
    }
    synchronized void failRebuild() { // do not refund an attempted rebuild
        rebuildAt=-1;
    }
    synchronized void resetAfterExplicitUserStart() {
        rebuilds.clear();firstStaleAt=frameRequestAt=rebuildAt=observedFrameAt=-1;
        quarantined=false;freshSamples=0;
    }
    synchronized int rebuildCount(long now){expireBudget(now);return rebuilds.size();}
    synchronized boolean quarantined(){return quarantined;}
    private void expireBudget(long now){while(!rebuilds.isEmpty() && (now<rebuilds.peekFirst() || now-rebuilds.peekFirst()>=REBUILD_WINDOW_MS))rebuilds.removeFirst();}
    private static Decision out(Fault fault,Action action,String reason){return new Decision(fault,action,reason);}
}
