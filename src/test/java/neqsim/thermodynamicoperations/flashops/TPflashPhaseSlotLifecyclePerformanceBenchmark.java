package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.BufferedWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Matched full-flash timing, allocation, and numerical qualification for the phase-slot lifecycle repair.
 *
 * <p>
 * This benchmark is intended for separate JVM runs of the exact pull-request base and final feature head. It reports
 * wall time and main-thread allocation only when every measured endpoint is checked for phase-fraction, composition,
 * material-balance, and interphase fugacity closure. Results from invalid endpoints are never accepted as performance
 * evidence.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
@Tag("benchmark")
class TPflashPhaseSlotLifecyclePerformanceBenchmark {
  /** Pressure path that repeatedly crosses the hydrocarbon-scrubber phase boundary. */
  private static final double[] SCRUBBER_PRESSURES = {151.0, 106.0, 105.0, 101.0, 81.0, 61.0, 41.0, 31.0, 41.0, 61.0,
      81.0, 101.0, 105.0, 106.0, 151.0};

  /** Reusable operation that executes one complete flash sequence. */
  private interface FlashSequence {
    /**
     * Executes the complete sequence and returns its numerical evidence.
     *
     * @return sequence evidence
     */
    SequenceEvidence run();
  }

  /** Numerical evidence accumulated over one complete sequence. */
  private static final class SequenceEvidence {
    private long flashes;
    private long invalidEndpoints;
    private long topologyHash = 17L;
    private double maximumMaterialResidual;
    private double maximumFugacityResidual;
    private double maximumBetaResidual;
    private double maximumCompositionResidual;

    /** Adds one endpoint to this sequence. */
    private void add(SystemInterface fluid) {
      fluid.init(1);
      flashes++;
      topologyHash = 31L * topologyHash + topologySignature(fluid);
      double betaSum = 0.0;
      double endpointCompositionResidual = 0.0;
      boolean finite = true;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        double beta = fluid.getBeta(phase);
        finite &= Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0;
        betaSum += beta;
        double compositionSum = 0.0;
        for (int component = 0; component < fluid.getPhase(phase).getNumberOfComponents(); component++) {
          double moleFraction = fluid.getPhase(phase).getComponent(component).getx();
          finite &= Double.isFinite(moleFraction) && moleFraction >= 0.0;
          compositionSum += moleFraction;
        }
        endpointCompositionResidual = Math.max(endpointCompositionResidual, Math.abs(1.0 - compositionSum));
      }
      double endpointBetaResidual = Math.abs(1.0 - betaSum);
      double endpointMaterialResidual = 0.0;
      double endpointFugacityResidual = 0.0;
      int fugacityComparisons = 0;
      int components = fluid.getPhase(0).getNumberOfComponents();
      for (int component = 0; component < components; component++) {
        double recovered = 0.0;
        for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
          recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
        }
        endpointMaterialResidual = Math.max(endpointMaterialResidual,
            Math.abs(fluid.getPhase(0).getComponent(component).getz() - recovered));
        for (int phase = 1; phase < fluid.getNumberOfPhases(); phase++) {
          double referenceX = fluid.getPhase(0).getComponent(component).getx();
          double otherX = fluid.getPhase(phase).getComponent(component).getx();
          double referencePhi = fluid.getPhase(0).getComponent(component).getFugacityCoefficient();
          double otherPhi = fluid.getPhase(phase).getComponent(component).getFugacityCoefficient();
          if (referenceX > 1.0e-12 && otherX > 1.0e-12 && Double.isFinite(referencePhi) && referencePhi > 0.0
              && Double.isFinite(otherPhi) && otherPhi > 0.0) {
            endpointFugacityResidual = Math.max(endpointFugacityResidual,
                Math.abs(Math.log(referenceX * referencePhi) - Math.log(otherX * otherPhi)));
            fugacityComparisons++;
          }
        }
      }
      maximumMaterialResidual = Math.max(maximumMaterialResidual, endpointMaterialResidual);
      maximumFugacityResidual = Math.max(maximumFugacityResidual, endpointFugacityResidual);
      maximumBetaResidual = Math.max(maximumBetaResidual, endpointBetaResidual);
      maximumCompositionResidual = Math.max(maximumCompositionResidual, endpointCompositionResidual);
      boolean fugacityClosed = fluid.getNumberOfPhases() == 1
          || fugacityComparisons > 0 && endpointFugacityResidual <= 1.0e-8;
      if (!finite || endpointBetaResidual > 1.0e-12 || endpointCompositionResidual > 1.0e-9
          || endpointMaterialResidual > 1.0e-10 || !fugacityClosed) {
        invalidEndpoints++;
      }
    }
  }

  /** Aggregated timing, allocation, and numerical evidence for one case. */
  private static final class Measurement {
    private final String name;
    private final long flashes;
    private final long invalidEndpoints;
    private final long topologyMismatches;
    private final long elapsedNanoseconds;
    private final long allocatedBytes;
    private final double maximumMaterialResidual;
    private final double maximumFugacityResidual;
    private final double maximumBetaResidual;
    private final double maximumCompositionResidual;

    /** Creates an immutable measurement. */
    private Measurement(String name, long flashes, long invalidEndpoints, long topologyMismatches,
        long elapsedNanoseconds, long allocatedBytes, double maximumMaterialResidual, double maximumFugacityResidual,
        double maximumBetaResidual, double maximumCompositionResidual) {
      this.name = name;
      this.flashes = flashes;
      this.invalidEndpoints = invalidEndpoints;
      this.topologyMismatches = topologyMismatches;
      this.elapsedNanoseconds = elapsedNanoseconds;
      this.allocatedBytes = allocatedBytes;
      this.maximumMaterialResidual = maximumMaterialResidual;
      this.maximumFugacityResidual = maximumFugacityResidual;
      this.maximumBetaResidual = maximumBetaResidual;
      this.maximumCompositionResidual = maximumCompositionResidual;
    }
  }

  /** Runs all matched benchmark cases and writes one CSV row per case. */
  @Test
  void writeMatchedEvidence() throws Exception {
    int warmupSequences = Integer.getInteger("tpflash.benchmark.warmup", 6);
    int measuredSequences = Integer.getInteger("tpflash.benchmark.sequences", 12);
    boolean requireValid = Boolean.getBoolean("tpflash.benchmark.requireValid");
    SystemInterface waterBearing = createWaterBearingPrSystem();
    SystemInterface methaneHeptane = createMethaneHeptaneSystem();
    SystemInterface scrubberGas = runScrubberProcess().getGasOutStream().getFluid().clone();

    Measurement[] measurements = {
        measure("pr-water-changed-state", warmupSequences, measuredSequences,
            () -> runWaterBearingSequence(waterBearing)),
        measure("pr-methane-heptane-map", warmupSequences, measuredSequences,
            () -> runMethaneHeptaneSequence(methaneHeptane)),
        measure("umr-scrubber-ordinary", warmupSequences, measuredSequences,
            () -> runScrubberSequence(scrubberGas, false)),
        measure("umr-scrubber-enhanced", warmupSequences, measuredSequences,
            () -> runScrubberSequence(scrubberGas, true))};

    if (requireValid) {
      for (Measurement measurement : measurements) {
        assertEquals(0L, measurement.invalidEndpoints, measurement.name + " invalid endpoints");
        assertEquals(0L, measurement.topologyMismatches, measurement.name + " topology mismatches");
      }
    }
    writeCsv(measurements, warmupSequences, measuredSequences);
  }

  /** Measures one sequence in the current JVM after deterministic warmup. */
  private static Measurement measure(String name, int warmupSequences, int measuredSequences, FlashSequence sequence) {
    for (int warmup = 0; warmup < warmupSequences; warmup++) {
      sequence.run();
    }
    long allocatedBefore = allocatedBytes();
    long started = System.nanoTime();
    long flashes = 0L;
    long invalidEndpoints = 0L;
    long topologyMismatches = 0L;
    long referenceTopology = Long.MIN_VALUE;
    double maximumMaterialResidual = 0.0;
    double maximumFugacityResidual = 0.0;
    double maximumBetaResidual = 0.0;
    double maximumCompositionResidual = 0.0;
    for (int repetition = 0; repetition < measuredSequences; repetition++) {
      SequenceEvidence evidence = sequence.run();
      flashes += evidence.flashes;
      invalidEndpoints += evidence.invalidEndpoints;
      if (referenceTopology == Long.MIN_VALUE) {
        referenceTopology = evidence.topologyHash;
      } else if (referenceTopology != evidence.topologyHash) {
        topologyMismatches++;
      }
      maximumMaterialResidual = Math.max(maximumMaterialResidual, evidence.maximumMaterialResidual);
      maximumFugacityResidual = Math.max(maximumFugacityResidual, evidence.maximumFugacityResidual);
      maximumBetaResidual = Math.max(maximumBetaResidual, evidence.maximumBetaResidual);
      maximumCompositionResidual = Math.max(maximumCompositionResidual, evidence.maximumCompositionResidual);
    }
    long elapsedNanoseconds = System.nanoTime() - started;
    long allocatedAfter = allocatedBytes();
    return new Measurement(name, flashes, invalidEndpoints, topologyMismatches, elapsedNanoseconds,
        allocationDelta(allocatedBefore, allocatedAfter), maximumMaterialResidual, maximumFugacityResidual,
        maximumBetaResidual, maximumCompositionResidual);
  }

  /** Runs the water-bearing PR changed-state lifecycle. */
  private static SequenceEvidence runWaterBearingSequence(SystemInterface template) {
    SystemInterface fluid = template.clone();
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    double[][] states = {{298.15, 9.9}, {298.15, 10.0}, {298.15, 10.1}, {288.15, 499.0}, {288.15, 500.0},
        {288.15, 501.0}, {298.15, 10.0}};
    SequenceEvidence evidence = new SequenceEvidence();
    for (double[] state : states) {
      fluid.setTemperature(state[0], "K");
      fluid.setPressure(state[1], "bara");
      operations.TPflash();
      evidence.add(fluid);
    }
    return evidence;
  }

  /** Runs cold dense-split, collapse, phase-boundary, and return states on one PR fluid. */
  private static SequenceEvidence runMethaneHeptaneSequence(SystemInterface template) {
    SystemInterface fluid = template.clone();
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    double[][] states = {{194.0, 78.5}, {194.0, 81.0}, {175.0, 80.0}, {180.0, 90.0}, {182.0, 95.0}, {360.0, 120.0},
        {400.0, 170.0}, {424.0, 186.0}, {418.0, 191.0}, {450.0, 150.0}, {194.0, 78.5}};
    SequenceEvidence evidence = new SequenceEvidence();
    for (double[] state : states) {
      fluid.setTemperature(state[0], "K");
      fluid.setPressure(state[1], "bara");
      operations.TPflash();
      evidence.add(fluid);
    }
    return evidence;
  }

  /** Runs the qualified scrubber pressure sweep on one reused UMR-PRU fluid. */
  private static SequenceEvidence runScrubberSequence(SystemInterface template, boolean enhanced) {
    SystemInterface fluid = template.clone();
    fluid.setTemperature(0.0, "C");
    fluid.setMultiPhaseCheck(true);
    fluid.setEnhancedMultiPhaseCheck(enhanced);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    SequenceEvidence evidence = new SequenceEvidence();
    for (double pressure : SCRUBBER_PRESSURES) {
      fluid.setPressure(pressure, "bara");
      operations.TPflash();
      evidence.add(fluid);
    }
    return evidence;
  }

  /** Creates the water-bearing PR lifecycle fixture. */
  private static SystemInterface createWaterBearingPrSystem() {
    SystemInterface fluid = new SystemPrEos(243.15, 300.0);
    String[] names = {"nitrogen", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane",
        "n-hexane", "nC10", "water"};
    double[] amounts = {1.0, 90.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0};
    for (int component = 0; component < names.length; component++) {
      fluid.addComponent(names[component], amounts[component]);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Creates the methane/n-heptane PR phase-map fixture. */
  private static SystemInterface createMethaneHeptaneSystem() {
    SystemInterface fluid = new SystemPrEos(400.0, 170.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("n-heptane", 30.0);
    fluid.setMixingRule("classic");
    for (int phase = 0; phase < 2; phase++) {
      ((EosMixingRulesInterface) fluid.getPhase(phase).getMixingRule()).setBinaryInteractionParameter(0, 1, 0.05);
    }
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /** Builds the public hydrocarbon-scrubber export-gas fixture. */
  private static ThreePhaseSeparator runScrubberProcess() {
    SystemInterface fluid = new SystemUMRPRUMCEos(280.0, 10.0);
    String[] names = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "2-m-C5", "3-m-C5", "n-hexane", "c-hexane", "n-heptane", "benzene", "n-octane", "c-C7", "toluene",
        "n-nonane", "c-C8", "m-Xylene", "nC10", "nC11", "nC12"};
    double[] amounts = {0.01, 0.01, 0.9, 0.1, 0.03, 0.01, 0.01, 0.01, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 0.0001,
        0.0001, 0.0001, 0.0001, 0.0001, 0.00001, 0.00001, 3.0e-12, 3.0e-12, 3.0e-12};
    for (int component = 0; component < names.length; component++) {
      fluid.addComponent(names[component], amounts[component]);
    }
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    Stream feed = new Stream("feed gas", fluid);
    feed.setFlowRate(25.0, "MSm3/day");
    feed.setTemperature(22.5, "C");
    feed.setPressure(81.0, "bara");
    Separator upstreamSeparator = new Separator("upstream separator", feed);
    Heater cooler = new Heater("cooler", upstreamSeparator.getGasOutStream());
    cooler.setOutPressure(78.0, "bara");
    cooler.setOutTemperature(15.0, "C");
    ThreePhaseSeparator scrubber = new ThreePhaseSeparator("dewpoint scrubber", cooler.getOutStream());
    scrubber.setEntrainment(0.5, "volume", "feed", "oil", "gas");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(upstreamSeparator);
    process.add(cooler);
    process.add(scrubber);
    process.run();
    return scrubber;
  }

  /** Returns a deterministic phase-count/type signature. */
  private static long topologySignature(SystemInterface fluid) {
    long signature = fluid.getNumberOfPhases();
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseType type = fluid.getPhase(phase).getType();
      signature = 31L * signature + type.ordinal();
    }
    return signature;
  }

  /** Returns main-thread allocated bytes when supported by the JVM. */
  private static long allocatedBytes() {
    java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    if (bean instanceof com.sun.management.ThreadMXBean) {
      com.sun.management.ThreadMXBean allocationBean = (com.sun.management.ThreadMXBean) bean;
      if (allocationBean.isThreadAllocatedMemorySupported() && !allocationBean.isThreadAllocatedMemoryEnabled()) {
        allocationBean.setThreadAllocatedMemoryEnabled(true);
      }
      if (allocationBean.isThreadAllocatedMemorySupported() && allocationBean.isThreadAllocatedMemoryEnabled()) {
        return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
      }
    }
    return -1L;
  }

  /** Returns an allocation delta or -1 when unavailable. */
  private static long allocationDelta(long before, long after) {
    return before < 0L || after < 0L ? -1L : after - before;
  }

  /** Writes exact environment and one measurement per CSV row. */
  private static void writeCsv(Measurement[] measurements, int warmupSequences, int measuredSequences)
      throws Exception {
    Path output = Paths
        .get(System.getProperty("tpflash.benchmark.output", "target/tpflash-phase-slot-performance.csv"));
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      writer.write("commit,case,warmup_sequences,measured_sequences,flashes,invalid_endpoints,topology_mismatches,");
      writer.write("elapsed_ns,ns_per_flash,allocated_bytes,bytes_per_flash,max_material_residual,");
      writer.write("max_log_fugacity_residual,max_beta_residual,max_composition_residual,java_version,os_name\n");
      for (Measurement measurement : measurements) {
        writer.write(System.getProperty("tpflash.benchmark.commit", "unknown"));
        writer.write("," + measurement.name + "," + warmupSequences + "," + measuredSequences + ","
            + measurement.flashes + "," + measurement.invalidEndpoints + "," + measurement.topologyMismatches + ","
            + measurement.elapsedNanoseconds + "," + (measurement.elapsedNanoseconds / (double) measurement.flashes)
            + "," + measurement.allocatedBytes + "," + (measurement.allocatedBytes / (double) measurement.flashes) + ","
            + measurement.maximumMaterialResidual + "," + measurement.maximumFugacityResidual + ","
            + measurement.maximumBetaResidual + "," + measurement.maximumCompositionResidual + ","
            + System.getProperty("java.version") + "," + System.getProperty("os.name") + "\n");
      }
    }
  }
}
