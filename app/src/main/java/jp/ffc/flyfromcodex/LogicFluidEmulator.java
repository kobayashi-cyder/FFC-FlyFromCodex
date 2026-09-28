package jp.ffc.flyfromcodex;

import java.io.IOException;
import java.util.*;

/**
 * Event-driven logical connectome emulator over BANC v888.
 * Base BANC topology/counts stay immutable. Learning is a sparse overlay.
 */
final class LogicFluidEmulator {
    static final double BASE_BRAIN_K_MM = 5.0;
    static final double HEMOLYMPH_K_MM = 40.0;

    static final class Snapshot {
        int tick;
        int activeNeurons;
        int evaluatedEdges;
        int eligibilityEdges;
        int learnedEdges;
        double meanActivity;
        double microdomainK;
        double interstitialK;
        double extracellularVolume;
        double transmitter;
        double glialKLoad;
        double excitabilityGain;
        double bbbPermeability;
        double cumulativeReward;
        double lastReward;
        double meanAbsPlasticity;
        long[] topIds;
        float[] topActivities;
    }

    private static final class Eligibility {
        float value;
        Eligibility(float value){this.value=value;}
    }

    private final BancDataset data;
    private final PlasticityStore plasticity;
    private Map<Long,Float> activity=new HashMap<>();
    private final HashMap<PlasticityStore.EdgeKey,Eligibility> eligibility=new HashMap<>();

    private int tick=0;
    private int minSynapseCount=1;
    private int maxActive=5000;
    private int evaluatedEdges=0;
    private double cumulativeReward=0.0;
    private double lastReward=0.0;

    private double microdomainK=BASE_BRAIN_K_MM;
    private double interstitialK=BASE_BRAIN_K_MM;
    private double extracellularVolume=1.0;
    private double transmitter=0.0;
    private double glialKLoad=0.0;
    private double bbbPermeability=0.00002;

    LogicFluidEmulator(BancDataset data,PlasticityStore plasticity){
        this.data=data;
        this.plasticity=plasticity;
    }

    synchronized void setMinSynapseCount(int value){minSynapseCount=Math.max(1,value);}
    synchronized int getMinSynapseCount(){return minSynapseCount;}
    synchronized void setMaxActive(int value){maxActive=Math.max(100,Math.min(20000,value));}
    synchronized void setBarrierStress(boolean stressed){bbbPermeability=stressed?0.00020:0.00002;}
    synchronized boolean isBarrierStressed(){return bbbPermeability>0.00005;}

    synchronized void reset(long seedId){
        activity=new HashMap<>();
        if(seedId>0)activity.put(seedId,1.0f);
        eligibility.clear();
        tick=0;evaluatedEdges=0;lastReward=0;
        microdomainK=BASE_BRAIN_K_MM;interstitialK=BASE_BRAIN_K_MM;extracellularVolume=1.0;
        transmitter=0;glialKLoad=0;bbbPermeability=0.00002;
    }

    synchronized void stimulate(long neuronId,float strength){
        if(neuronId<=0)return;
        float s=clamp01(strength);
        activity.put(neuronId,Math.max(activity.getOrDefault(neuronId,0f),s));
    }

    synchronized boolean isActive(long neuronId){
        return activity.getOrDefault(neuronId,0f)>0.02f;
    }

    synchronized float activityOf(long neuronId){return activity.getOrDefault(neuronId,0f);}

    synchronized int learnedEdgeCount(){return plasticity.size();}

    synchronized void clearTraining(){
        plasticity.clear();
        eligibility.clear();
        cumulativeReward=0;
        lastReward=0;
    }

    synchronized int reinforce(float reward,float learningRate){
        reward=Math.max(-1f,Math.min(1f,reward));
        learningRate=Math.max(0.001f,Math.min(0.5f,learningRate));
        int changed=0;
        for(Map.Entry<PlasticityStore.EdgeKey,Eligibility> e:eligibility.entrySet()){
            float elig=e.getValue().value;
            if(elig<0.002f)continue;
            float amount=learningRate*reward*elig;
            if(Math.abs(amount)>=0.0001f){
                plasticity.add(e.getKey().pre,e.getKey().post,amount);
                changed++;
            }
        }
        plasticity.save();
        cumulativeReward+=reward;
        lastReward=reward;
        return changed;
    }

    synchronized Snapshot step() throws IOException {
        decayEligibility();

        HashMap<Long,Double> drive=new HashMap<>();
        evaluatedEdges=0;
        double outgoingActivity=0.0;

        for(Map.Entry<Long,Float> src:activity.entrySet()){
            float a=src.getValue();
            if(a<=0.002f)continue;
            outgoingActivity+=a;
            List<BancDataset.Link> links=data.outputs(src.getKey(),minSynapseCount,0);
            evaluatedEdges+=links.size();

            for(BancDataset.Link link:links){
                double base=Math.log1p(link.count)/Math.log(1104.0);
                double learned=plasticity.multiplier(src.getKey(),link.partner);
                double effective=base*learned;
                double contribution=a*(0.018+0.24*effective);
                drive.merge(link.partner,contribution,Double::sum);

                float trace=(float)Math.min(1.0,a*(0.08+0.92*base));
                if(trace>=0.02f)addEligibility(src.getKey(),link.partner,trace);
            }
        }

        for(Map.Entry<Long,Float> prev:activity.entrySet()){
            drive.merge(prev.getKey(),prev.getValue()*0.045,Double::sum);
        }

        double threshold=0.115/excitabilityGain();
        ArrayList<Map.Entry<Long,Double>> candidates=new ArrayList<>(drive.entrySet());
        candidates.removeIf(e->e.getValue()<threshold);
        candidates.sort((a,b)->Double.compare(b.getValue(),a.getValue()));
        if(candidates.size()>maxActive)candidates=new ArrayList<>(candidates.subList(0,maxActive));

        HashMap<Long,Float> next=new HashMap<>();
        for(Map.Entry<Long,Double> e:candidates){
            next.put(e.getKey(),(float)Math.min(1.0,e.getValue()));
        }
        activity=next;

        updateFluid(outgoingActivity);
        tick++;
        return snapshot();
    }

    synchronized Snapshot snapshot(){
        Snapshot s=new Snapshot();
        s.tick=tick;s.activeNeurons=activity.size();s.evaluatedEdges=evaluatedEdges;
        s.eligibilityEdges=eligibility.size();s.learnedEdges=plasticity.size();
        double sum=0;for(float v:activity.values())sum+=v;
        s.meanActivity=activity.isEmpty()?0:sum/activity.size();
        s.microdomainK=microdomainK;s.interstitialK=interstitialK;s.extracellularVolume=extracellularVolume;
        s.transmitter=transmitter;s.glialKLoad=glialKLoad;s.excitabilityGain=excitabilityGain();s.bbbPermeability=bbbPermeability;
        s.cumulativeReward=cumulativeReward;s.lastReward=lastReward;s.meanAbsPlasticity=plasticity.meanAbsDelta();

        ArrayList<Map.Entry<Long,Float>> top=new ArrayList<>(activity.entrySet());
        top.sort((a,b)->Float.compare(b.getValue(),a.getValue()));
        int n=Math.min(20,top.size());s.topIds=new long[n];s.topActivities=new float[n];
        for(int i=0;i<n;i++){s.topIds[i]=top.get(i).getKey();s.topActivities[i]=top.get(i).getValue();}
        return s;
    }

    private void addEligibility(long pre,long post,float trace){
        PlasticityStore.EdgeKey k=new PlasticityStore.EdgeKey(pre,post);
        Eligibility old=eligibility.get(k);
        if(old!=null){old.value=Math.min(1f,old.value+trace*0.45f);return;}
        if(eligibility.size()<120_000)eligibility.put(k,new Eligibility(trace));
    }

    private void decayEligibility(){
        Iterator<Map.Entry<PlasticityStore.EdgeKey,Eligibility>> it=eligibility.entrySet().iterator();
        while(it.hasNext()){
            Eligibility e=it.next().getValue();
            e.value*=0.72f;
            if(e.value<0.004f)it.remove();
        }
    }

    private void updateFluid(double totalActiveDrive){
        double load=Math.min(1.0,Math.log1p(Math.max(0,totalActiveDrive))/Math.log(5001.0));
        microdomainK+=0.38*load;transmitter+=0.65*load;
        double diffusion=0.20*(microdomainK-interstitialK);
        microdomainK-=diffusion;interstitialK+=0.35*diffusion;
        double uptakeMicro=0.08*Math.max(0,microdomainK-BASE_BRAIN_K_MM);
        double uptakeIsf=0.11*Math.max(0,interstitialK-BASE_BRAIN_K_MM);
        microdomainK-=uptakeMicro;interstitialK-=uptakeIsf;glialKLoad+=uptakeMicro+uptakeIsf;
        interstitialK+=bbbPermeability*(HEMOLYMPH_K_MM-interstitialK);
        interstitialK+=0.035*(BASE_BRAIN_K_MM-interstitialK);
        microdomainK+=0.055*(interstitialK-microdomainK);
        glialKLoad*=0.94;transmitter*=0.74;
        extracellularVolume+=0.028*Math.max(0,interstitialK-BASE_BRAIN_K_MM);
        extracellularVolume+=0.045*(1.0-extracellularVolume);
        microdomainK=clamp(microdomainK,2,20);interstitialK=clamp(interstitialK,2,20);
        extracellularVolume=clamp(extracellularVolume,.75,2);transmitter=clamp(transmitter,0,3);glialKLoad=clamp(glialKLoad,0,8);
    }

    private double excitabilityGain(){return clamp(1.0+0.085*(interstitialK-BASE_BRAIN_K_MM),0.65,1.8);}
    private static float clamp01(float x){return Math.max(0f,Math.min(1f,x));}
    private static double clamp(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
}
