package neqsim.thermodynamicoperations.flashops;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Manual matched single-phase VU benchmark; use one EOS/rule/scenario per independent JVM.
 *
 * <p>
 * Arguments: label, CSV path, SRK/PR, mixing rule 2/4, gas/oil/aqueous/large, warmup batches, measured batches. Each
 * batch constructs one independent fixture and solves twenty alternating reference targets. Each target permits up to
 * 32 public VU refinements with caloric initialization until volume/energy errors meet the same 1e-6/1e-5 relative
 * bounds for both versions. Reference targets are prepared outside timing. Baseline/candidate share all classes except
 * OptimizedVUflash; alternate process order and retain raw samples. Elapsed time includes fixture construction and
 * validation. Allocation is thread-local HotSpot evidence when supported, otherwise minus one. Timing is not a CI
 * assertion or a model-accuracy qualification.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class VUSinglePhasePerformanceBenchmark {
  /** Prevents construction. */
  private VUSinglePhasePerformanceBenchmark() {
  }

  /**
   * Runs one frozen workload and appends raw measurements.
   *
   * @param args benchmark arguments described in the class documentation
   * @throws Exception for invalid selection, failed physical closure or output failure
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 7) {
      throw new IllegalArgumentException("label output.csv SRK|PR 2|4 gas|oil|aqueous|large warmup measured");
    }
    boolean pr = "PR".equals(args[2]);
    if (!pr && !"SRK".equals(args[2])) {
      throw new IllegalArgumentException("EOS must be SRK or PR");
    }
    int rule = Integer.parseInt(args[3]);
    if (rule != 2 && rule != 4) {
      throw new IllegalArgumentException("mixing rule must be 2 or 4");
    }
    String state = args[4];
    double temperature = "gas".equals(state) ? 350.0
        : "oil".equals(state) ? 300.0 : "aqueous".equals(state) ? 330.0 : "large".equals(state) ? 450.0 : Double.NaN;
    double pressure = "gas".equals(state) ? 40.0 : "oil".equals(state) ? 20.0 : "aqueous".equals(state) ? 50.0 : 5.0;
    if (!Double.isFinite(temperature)) {
      throw new IllegalArgumentException("unknown scenario");
    }
    int warmup = Integer.parseInt(args[5]);
    int measured = Integer.parseInt(args[6]);
    if (warmup < 1 || measured < 1) {
      throw new IllegalArgumentException("positive batch counts required");
    }
    double[] volumes = new double[2];
    double[] energies = new double[2];
    for (int target = 0; target < 2; target++) {
      SystemInterface reference = fluid(pr, rule, state, temperature + target * 0.02, pressure);
      volumes[target] = reference.getVolume();
      energies[target] = reference.getInternalEnergy();
    }
    java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
    com.sun.management.ThreadMXBean allocationBean = baseBean instanceof com.sun.management.ThreadMXBean
        ? (com.sun.management.ThreadMXBean) baseBean
        : null;
    if (allocationBean != null && allocationBean.isThreadAllocatedMemorySupported()) {
      allocationBean.setThreadAllocatedMemoryEnabled(true);
    } else {
      allocationBean = null;
    }
    long thread = Thread.currentThread().getId();
    File output = new File(args[1]);
    boolean header = !output.exists() || output.length() == 0;
    try (PrintWriter writer = new PrintWriter(new FileWriter(output, true))) {
      if (header) {
        writer.println("sample,eos,rule,state,elapsed_ns,allocated_bytes,iterations,solves,checksum,max_volume_error,"
            + "max_energy_error,max_returned_enthalpy_drift,max_pressure_error,max_balance,max_normalization");
      }
      for (int round = -warmup; round < measured; round++) {
        long allocated = allocationBean == null ? 0 : allocationBean.getThreadAllocatedBytes(thread);
        long started = System.nanoTime();
        SystemInterface working = fluid(pr, rule, state, temperature + 0.01, pressure);
        int iterations = 0;
        int solves = 0;
        double checksum = 0.0;
        double volumeError = 0.0;
        double energyError = 0.0;
        double caloricDrift = 0.0;
        double pressureError = 0.0;
        double balance = 0.0;
        double normalization = 0.0;
        for (int step = 0; step < 20; step++) {
          int target = step % 2;
          double energyScale = Math.max(1.0, Math.abs(energies[target]));
          for (int refinement = 0; refinement < 32; refinement++) {
            ThermodynamicOperations operations = new ThermodynamicOperations(working);
            operations.VUflash(volumes[target], energies[target]);
            solves++;
            OptimizedVUflash flash = (OptimizedVUflash) operations.getOperation();
            iterations += flash.getLastIterationCount();
            if (!flash.isLastRunConverged()) {
              throw new IllegalStateException("VU solve rejected");
            }
            double returnedH = working.getEnthalpy();
            working.init(2);
            caloricDrift = Math.max(caloricDrift, Math.abs(working.getEnthalpy() - returnedH) / energyScale);
            if (Math.abs(working.getVolume() / volumes[target] - 1.0) <= 1.0e-6
                && Math.abs(working.getInternalEnergy() - energies[target]) / energyScale <= 1.0e-5) {
              break;
            }
          }
          if (working.getNumberOfPhases() != 1) {
            throw new IllegalStateException("single-phase benchmark changed topology");
          }
          volumeError = Math.max(volumeError, Math.abs(working.getVolume() / volumes[target] - 1.0));
          energyError = Math.max(energyError, Math.abs(working.getInternalEnergy() - energies[target]) / energyScale);
          pressureError = Math.max(pressureError,
              Math.abs(((PhaseEos) working.getPhase(0)).calcPressure() - working.getPressure())
                  / Math.max(1.0, working.getPressure()));
          double sum = 0.0;
          for (int component = 0; component < working.getNumberOfComponents(); component++) {
            double x = working.getPhase(0).getComponent(component).getx();
            sum += x;
            balance = Math.max(balance, Math.abs(x - working.getComponent(component).getz()));
            checksum += working.getPhase(0).getComponent(component).getLogFugacityCoefficient();
          }
          normalization = Math.max(normalization, Math.abs(sum - 1.0));
          checksum += working.getTemperature() + working.getPressure();
        }
        if (!Double.isFinite(volumeError) || !Double.isFinite(energyError) || !Double.isFinite(pressureError)
            || !Double.isFinite(balance) || !Double.isFinite(normalization) || !Double.isFinite(checksum)
            || !Double.isFinite(caloricDrift) || volumeError > 1.0e-6 || energyError > 1.0e-5 || pressureError > 1.0e-8
            || balance > 1.0e-10 || normalization > 1.0e-12) {
          throw new IllegalStateException("benchmark physical closure failed: V=" + volumeError + " U=" + energyError
              + " P=" + pressureError + " balance=" + balance + " norm=" + normalization);
        }
        long elapsed = System.nanoTime() - started;
        allocated = allocationBean == null ? -1 : allocationBean.getThreadAllocatedBytes(thread) - allocated;
        if (round >= 0) {
          writer.println(args[0] + "_" + round + "," + args[2] + "," + rule + "," + state + "," + elapsed + ","
              + allocated + "," + iterations + "," + solves + "," + checksum + "," + volumeError + "," + energyError
              + "," + caloricDrift + "," + pressureError + "," + balance + "," + normalization);
        }
      }
    }
  }

  /**
   * Builds an independent fluid for one frozen scenario.
   *
   * @param pr whether to use PR
   * @param rule mixing rule
   * @param state scenario name
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return initialized single-phase fluid
   */
  private static SystemInterface fluid(boolean pr, int rule, String state, double temperature, double pressure) {
    SystemInterface fluid = pr ? new SystemPrEos(temperature, pressure) : new SystemSrkEos(temperature, pressure);
    if ("gas".equals(state)) {
      fluid.addComponent("methane", 0.8);
      fluid.addComponent("ethane", 0.1);
      fluid.addComponent("propane", 0.1);
    } else if ("oil".equals(state)) {
      fluid.addComponent("propane", 0.8);
      fluid.addComponent("n-butane", 0.2);
    } else if ("aqueous".equals(state)) {
      fluid.addComponent("methanol", 0.3);
      fluid.addComponent("water", 0.7);
    } else {
      String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-hexane", "n-heptane", "n-octane",
          "n-nonane", "n-decane", "nC11", "nC12", "nC13", "nC14", "nC15", "nC16", "nC17", "nC18", "nC19", "nC20",
          "nitrogen", "CO2", "water", "MEG"};
      for (int component = 0; component < names.length; component++) {
        fluid.addComponent(names[component], component == 0 ? 0.99 : component < 20 ? 0.0001 : 0.00001);
      }
    }
    fluid.setMixingRule(rule);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    if (fluid.getNumberOfPhases() != 1) {
      throw new IllegalStateException("fixture must have one phase");
    }
    return fluid;
  }
}
