package neqsim.thermo.mixingrule;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Manual same-harness clone, initialization and TP-flash benchmark for classical and HV rules.
 *
 * <p>
 * Use a fresh JVM for each EOS, size, operation, initialization level and mixing rule. Alternate baseline/candidate JVM
 * order and retain every warmed round. Timing is not a CI assertion or experimental-accuracy qualification. Fixture
 * setup and numerical reporting are outside the measured batch; cloning is included in clone_flash. A separate CSV with
 * suffix {@code .states.csv} records the final phase types, fractions, compositions and log fugacity coefficients.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class HuronVidalClonePerformanceBenchmark {
  /** Prevents elimination of the evaluated work. */
  private static volatile double checksumSink;
  /** Retains the last calculated fluid for checks outside the timing interval. */
  private static SystemInterface last;
  /** Optional HotSpot allocation counter, independent of elapsed wall time. */
  private static java.lang.reflect.Method allocationReader;
  /** Allocation bean accessed reflectively so the harness compiles against Java 8 APIs. */
  private static final Object ALLOCATIONS = allocationCounter();

  /** Prevents construction. */
  private HuronVidalClonePerformanceBenchmark() {
  }

  /**
   * Runs one isolated configuration and appends raw rounds to a CSV.
   *
   * @param args label, CSV, EOS, size, operation, init, rule, warmups, rounds, iterations
   * @throws IOException if writing the report fails
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 10) {
      throw new IllegalArgumentException(
          "label CSV SRK|PR 4|20 clone|phase|flash|clone_flash init rule warmups rounds iterations");
    }
    String label = args[0];
    boolean pr = "PR".equals(args[2]);
    int size = Integer.parseInt(args[3]);
    String operation = args[4];
    int init = Integer.parseInt(args[5]);
    int rule = Integer.parseInt(args[6]);
    int warmups = Integer.parseInt(args[7]);
    int rounds = Integer.parseInt(args[8]);
    int iterations = Integer.parseInt(args[9]);
    if (!("SRK".equals(args[2]) || pr) || !(size == 4 || size == 20) || !(init == 1 || init == 3)
        || !(rule == 2 || rule == 4) || !("clone".equals(operation) || "phase".equals(operation)
            || "flash".equals(operation) || "clone_flash".equals(operation))
        || warmups < 1 || rounds < 2 || iterations < 1) {
      throw new IllegalArgumentException("Invalid benchmark configuration");
    }
    SystemInterface fluid = fluid(pr, size, rule, operation, init);
    for (int warmup = 0; warmup < warmups; warmup++) {
      batch(fluid, operation, init, iterations);
    }
    File output = new File(args[1]);
    boolean header = !output.exists() || output.length() == 0;
    try (PrintWriter out = new PrintWriter(new FileWriter(output, true))) {
      if (header) {
        out.println("sample,eos,components,operation,init,rule,round,iterations,total_ns,ns_per_op,"
            + "allocated_bytes_per_op,phases,T_K,P_bara,max_balance,max_logfugacity,checksum");
      }
      for (int round = 0; round < rounds; round++) {
        long allocated = allocatedBytes();
        long start = System.nanoTime();
        double checksum = batch(fluid, operation, init, iterations);
        long elapsed = System.nanoTime() - start;
        long after = allocatedBytes();
        double bytes = allocated < 0 || after < 0 ? Double.NaN : (after - allocated) / (double) iterations;
        double[] residuals = residuals(last);
        out.printf(Locale.ROOT, "%s,%s,%d,%s,%d,%d,%d,%d,%d,%.17g,%.17g,%d,%.17g,%.17g,%.17g,%.17g,%.17g%n", label,
            pr ? "PR" : "SRK", size, operation, init, rule, round, iterations, elapsed, elapsed / (double) iterations,
            bytes, last.getNumberOfPhases(), last.getTemperature(), last.getPressure(), residuals[0], residuals[1],
            checksum);
        out.flush();
      }
      if (out.checkError()) {
        throw new IOException("Could not write benchmark evidence");
      }
    }
    reportState(args);
  }

  /**
   * Records the final thermodynamic result outside every measured interval.
   *
   * @param args benchmark configuration
   * @throws IOException if the state report cannot be written
   */
  private static void reportState(String[] args) throws IOException {
    File output = new File(args[1] + ".states.csv");
    boolean header = !output.exists() || output.length() == 0;
    try (PrintWriter out = new PrintWriter(new FileWriter(output, true))) {
      if (header) {
        out.println("sample,eos,components,operation,init,rule,T_K,P_bara,phase,beta,component,x,logphi");
      }
      for (int phase = 0; phase < last.getNumberOfPhases(); phase++) {
        PhaseInterface state = last.getPhase(phase);
        for (int component = 0; component < last.getNumberOfComponents(); component++) {
          out.printf(Locale.ROOT, "%s,%s,%s,%s,%s,%s,%.17g,%.17g,%s,%.17g,%s,%.17g,%.17g%n", args[0], args[2], args[3],
              args[4], args[5], args[6], last.getTemperature(), last.getPressure(), state.getType(), state.getBeta(),
              state.getComponent(component).getComponentName(), state.getComponent(component).getx(),
              state.getComponent(component).getLogFugacityCoefficient());
        }
      }
      if (out.checkError()) {
        throw new IOException("Could not write final thermodynamic state");
      }
    }
  }

  /**
   * Performs the measured work while retaining numerical output.
   *
   * @param source independently configured fixture
   * @param operation selected workload
   * @param init property level
   * @param iterations operations per batch
   * @return numerical checksum
   */
  private static double batch(SystemInterface source, String operation, int init, int iterations) {
    double checksum = 0.0;
    ThermodynamicOperations operations = new ThermodynamicOperations(source);
    for (int iteration = 0; iteration < iterations; iteration++) {
      SystemInterface evaluated = source;
      if ("clone".equals(operation)) {
        evaluated = source.clone();
      } else if ("phase".equals(operation)) {
        source.init(init);
      } else if ("clone_flash".equals(operation)) {
        evaluated = source.clone();
        evaluated.setTemperature(298.15 + 0.1 * (iteration % 2));
        new ThermodynamicOperations(evaluated).TPflash();
        evaluated.init(init);
      } else {
        source.setTemperature(298.15 + 0.1 * (iteration % 2));
        operations.TPflash();
        source.init(init);
      }
      for (int phase = 0; phase < evaluated.getNumberOfPhases(); phase++) {
        PhaseInterface state = evaluated.getPhase(phase);
        checksum += state.getBeta();
        for (int component = 0; component < evaluated.getNumberOfComponents(); component++) {
          checksum += state.getComponent(component).getx() + state.getComponent(component).getLogFugacityCoefficient();
        }
      }
      last = evaluated;
    }
    checksumSink = checksum;
    return checksumSink;
  }

  /**
   * Configures one synthetic matched-composition fixture.
   *
   * @param pr whether to use PR
   * @param size number of components
   * @param rule classical or HV rule
   * @param operation selected workload
   * @param init derivative level retained before cloning
   * @return initialized fixture
   */
  private static SystemInterface fluid(boolean pr, int size, int rule, String operation, int init) {
    SystemInterface fluid = pr ? new SystemPrEos(298.15, 60.0) : new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("nC10", 0.20);
    fluid.addComponent("water", 0.12);
    fluid.addComponent("MEG", 0.03);
    if (size == 20) {
      String[] extra = {"ethane", "propane", "n-butane", "i-butane", "n-pentane", "i-pentane", "n-hexane", "n-heptane",
          "n-octane", "n-nonane", "nC11", "nC12", "nC13", "nC14", "nC15", "nitrogen"};
      for (String name : extra) {
        fluid.addComponent(name, 0.005);
      }
    }
    if ("phase".equals(operation)) {
      fluid.setTemperature(310.0);
      fluid.setPressure(20.0);
    }
    fluid.setMixingRule(rule);
    fluid.init(0);
    if ("phase".equals(operation)) {
      fluid.setForceSinglePhase(PhaseType.GAS);
    } else {
      fluid.setMultiPhaseCheck(true);
      new ThermodynamicOperations(fluid).TPflash();
    }
    fluid.init(init);
    residuals(fluid);
    return fluid;
  }

  /**
   * Checks results independently of the measured interval.
   *
   * @param fluid evaluated fixture
   * @return maximum balance and log-fugacity residuals
   */
  private static double[] residuals(SystemInterface fluid) {
    double balance = 0.0;
    double fugacity = 0.0;
    double beta = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface state = fluid.getPhase(phase);
      beta += state.getBeta();
      double sum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        sum += state.getComponent(component).getx();
      }
      if (!Double.isFinite(sum) || Math.abs(sum - 1.0) > 1e-12) {
        throw new IllegalStateException("Composition normalization failed");
      }
    }
    if (!Double.isFinite(beta) || Math.abs(beta - 1.0) > 1e-12) {
      throw new IllegalStateException("Phase normalization failed");
    }
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double inventory = 0.0;
      double reference = Math.log(fluid.getPhase(0).getComponent(component).getx())
          + fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        PhaseInterface state = fluid.getPhase(phase);
        inventory += state.getBeta() * state.getComponent(component).getx();
        fugacity = Math.max(fugacity, Math.abs(reference - Math.log(state.getComponent(component).getx())
            - state.getComponent(component).getLogFugacityCoefficient()));
      }
      balance = Math.max(balance, Math.abs(inventory - fluid.getComponent(component).getz()));
    }
    if (!Double.isFinite(balance) || !Double.isFinite(fugacity) || balance > 1e-10 || fugacity > 1e-8) {
      throw new IllegalStateException("Equilibrium residual gate failed: balance=" + balance + ", logphi=" + fugacity);
    }
    return new double[] {balance, fugacity};
  }

  /**
   * Obtains a supported allocation counter without making it a required platform feature.
   *
   * @return counter, or null when unavailable
   */
  private static Object allocationCounter() {
    Object bean = ManagementFactory.getThreadMXBean();
    try {
      Class<?> type = Class.forName("com.sun.management.ThreadMXBean");
      if (!type.isInstance(bean)
          || !((Boolean) type.getMethod("isThreadAllocatedMemorySupported").invoke(bean)).booleanValue()) {
        return null;
      }
      type.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(bean, true);
      allocationReader = type.getMethod("getThreadAllocatedBytes", long.class);
      return bean;
    } catch (ReflectiveOperationException | SecurityException exception) {
      return null;
    }
  }

  /**
   * Reads allocated bytes for the current thread outside the measured interval.
   *
   * @return byte count, or minus one when unsupported
   */
  private static long allocatedBytes() {
    if (ALLOCATIONS == null) {
      return -1;
    }
    try {
      return ((Long) allocationReader.invoke(ALLOCATIONS, Thread.currentThread().getId())).longValue();
    } catch (ReflectiveOperationException | SecurityException exception) {
      return -1;
    }
  }
}
