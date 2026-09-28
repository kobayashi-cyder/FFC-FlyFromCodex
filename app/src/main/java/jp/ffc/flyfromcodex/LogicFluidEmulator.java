package jp.ffc.flyfromcodex;

import java.io.IOException;
import java.util.*;

/**
 * Event-driven logical connectome emulator over the full BANC v888 graph.
 *
 * BANC supplies topology and synapse-pair counts.
 * Fluid/ion state is an explicit MODEL layer, not a BANC measurement.
 */
final class LogicFluidEmulator {
    static final double BASE_BRAIN_K_MM = 5.0;
    static final double HEMOLYMPH_K_MM = 40.0;

    static final class Snapshot {
        int tick;
        int activeNeurons;
        int evaluatedEdges;
        double meanActivity;
        double microdomainK;
        double interstitialK;
        double extracellularVolume;
        double transmitter;
        double glialKLoad;
        double excitabilityGain;
        double bbbPermeability;
        long[] topIds;
        float[] topActivities;
    }

    private final BancDataset data;
    private Map<Long, Float> activity = new HashMap<>();
    private int tick = 0;
    private int minSynapseCount = 1;
    private int maxActive = 5000;
    private int evaluatedEdges = 0;

    // Fluid compartments. K values are in mM; other fields are model proxies.
    private double microdomainK = BASE_BRAIN_K_MM;
    private double interstitialK = BASE_BRAIN_K_MM;
    private double extracellularVolume = 1.0;
    private double transmitter = 0.0;
    private double glialKLoad = 0.0;
    private double bbbPermeability = 0.00002;

    LogicFluidEmulator(BancDataset data) {
        this.data = data;
    }

    synchronized void setMinSynapseCount(int value) {
        minSynapseCount = Math.max(1, value);
    }

    synchronized int getMinSynapseCount() { return minSynapseCount; }

    synchronized void setMaxActive(int value) {
        maxActive = Math.max(100, Math.min(20000, value));
    }

    synchronized void setBarrierStress(boolean stressed) {
        bbbPermeability = stressed ? 0.00020 : 0.00002;
    }

    synchronized boolean isBarrierStressed() {
        return bbbPermeability > 0.00005;
    }

    synchronized void reset(long seedId) {
        activity = new HashMap<>();
        if (seedId > 0) activity.put(seedId, 1.0f);
        tick = 0;
        evaluatedEdges = 0;
        microdomainK = BASE_BRAIN_K_MM;
        interstitialK = BASE_BRAIN_K_MM;
        extracellularVolume = 1.0;
        transmitter = 0.0;
        glialKLoad = 0.0;
        bbbPermeability = 0.00002;
    }

    synchronized void stimulate(long neuronId, float strength) {
        if (neuronId <= 0) return;
        float s = clamp01(strength);
        float old = activity.containsKey(neuronId) ? activity.get(neuronId) : 0f;
        activity.put(neuronId, Math.max(old, s));
    }

    synchronized Snapshot step() throws IOException {
        HashMap<Long, Double> drive = new HashMap<>();
        evaluatedEdges = 0;
        double outgoingActivity = 0.0;

        // Event-driven: only currently active neurons traverse their outgoing BANC edges.
        for (Map.Entry<Long, Float> src : activity.entrySet()) {
            float a = src.getValue();
            if (a <= 0.002f) continue;
            outgoingActivity += a;
            List<BancDataset.Link> links = data.outputs(src.getKey(), minSynapseCount, 0);
            evaluatedEdges += links.size();

            for (BancDataset.Link link : links) {
                // Preserve count=1. Weight is compressed but monotonic in actual BANC pair count.
                double w = Math.log1p(link.count) / Math.log(1104.0);
                double contribution = a * (0.018 + 0.24 * w);
                drive.merge(link.partner, contribution, Double::sum);
            }
        }

        // A little state persistence; not a biological membrane equation.
        for (Map.Entry<Long, Float> prev : activity.entrySet()) {
            drive.merge(prev.getKey(), prev.getValue() * 0.045, Double::sum);
        }

        double gain = excitabilityGain();
        double threshold = 0.115 / gain;
        ArrayList<Map.Entry<Long, Double>> candidates = new ArrayList<>(drive.entrySet());
        candidates.removeIf(e -> e.getValue() < threshold);
        candidates.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));
        if (candidates.size() > maxActive) candidates = new ArrayList<>(candidates.subList(0, maxActive));

        HashMap<Long, Float> next = new HashMap<>();
        for (Map.Entry<Long, Double> e : candidates) {
            next.put(e.getKey(), (float)Math.min(1.0, e.getValue()));
        }
        activity = next;

        updateFluid(outgoingActivity);
        tick++;
        return snapshot();
    }

    synchronized Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.tick = tick;
        s.activeNeurons = activity.size();
        s.evaluatedEdges = evaluatedEdges;
        double sum = 0.0;
        for(float v : activity.values()) sum += v;
        s.meanActivity = activity.isEmpty() ? 0.0 : sum / activity.size();
        s.microdomainK = microdomainK;
        s.interstitialK = interstitialK;
        s.extracellularVolume = extracellularVolume;
        s.transmitter = transmitter;
        s.glialKLoad = glialKLoad;
        s.excitabilityGain = excitabilityGain();
        s.bbbPermeability = bbbPermeability;

        ArrayList<Map.Entry<Long, Float>> top = new ArrayList<>(activity.entrySet());
        top.sort((a,b)->Float.compare(b.getValue(),a.getValue()));
        int n = Math.min(20, top.size());
        s.topIds = new long[n];
        s.topActivities = new float[n];
        for(int i=0;i<n;i++){
            s.topIds[i] = top.get(i).getKey();
            s.topActivities[i] = top.get(i).getValue();
        }
        return s;
    }

    private void updateFluid(double totalActiveDrive) {
        // Convert population activity to a bounded load. MODEL coefficient, not measured concentration.
        double load = Math.min(1.0, Math.log1p(Math.max(0.0, totalActiveDrive)) / Math.log(5001.0));

        // Firing raises local extracellular K and transmitter in a perisynaptic microdomain.
        microdomainK += 0.38 * load;
        transmitter += 0.65 * load;

        // Microdomain -> interstitial extracellular fluid diffusion.
        double diffusion = 0.20 * (microdomainK - interstitialK);
        microdomainK -= diffusion;
        interstitialK += 0.35 * diffusion;

        // Glial K buffering. Stronger when extracellular K rises above the brain-side baseline.
        double uptakeMicro = 0.08 * Math.max(0.0, microdomainK - BASE_BRAIN_K_MM);
        double uptakeIsf = 0.11 * Math.max(0.0, interstitialK - BASE_BRAIN_K_MM);
        microdomainK -= uptakeMicro;
        interstitialK -= uptakeIsf;
        glialKLoad += uptakeMicro + uptakeIsf;

        // BBB boundary: hemolymph is high-K but normal permeability is deliberately tiny.
        double barrierFlux = bbbPermeability * (HEMOLYMPH_K_MM - interstitialK);
        interstitialK += barrierFlux;

        // Slow homeostatic return and glial unloading.
        interstitialK += 0.035 * (BASE_BRAIN_K_MM - interstitialK);
        microdomainK += 0.055 * (interstitialK - microdomainK);
        glialKLoad *= 0.94;

        // Transmitter clearance / uptake proxy.
        transmitter *= 0.74;

        // K/water coupling proxy: high ISF K expands ECF volume; recovery returns to 1.
        extracellularVolume += 0.028 * Math.max(0.0, interstitialK - BASE_BRAIN_K_MM);
        extracellularVolume += 0.045 * (1.0 - extracellularVolume);

        microdomainK = clamp(microdomainK, 2.0, 20.0);
        interstitialK = clamp(interstitialK, 2.0, 20.0);
        extracellularVolume = clamp(extracellularVolume, 0.75, 2.0);
        transmitter = clamp(transmitter, 0.0, 3.0);
        glialKLoad = clamp(glialKLoad, 0.0, 8.0);
    }

    private double excitabilityGain() {
        // Higher extracellular K lowers the simplified logical firing threshold.
        return clamp(1.0 + 0.085 * (interstitialK - BASE_BRAIN_K_MM), 0.65, 1.8);
    }

    private static float clamp01(float x) {
        return Math.max(0f, Math.min(1f, x));
    }
    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
