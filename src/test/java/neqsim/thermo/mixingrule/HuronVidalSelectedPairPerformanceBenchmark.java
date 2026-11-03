package neqsim.thermo.mixingrule;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Arrays;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Measures declared CO2/water selection in synthetic five- and fifteen-component mixtures with one EOS, rule and
 * scenario per JVM. Physical accuracy and different rule2/rule4 solver work remain separate from timing.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class HuronVidalSelectedPairPerformanceBenchmark {
  /** Optional allocation accessor, invoked outside timed intervals. */
  private static Method allocationReader;
  /** Platform-specific allocation bean; null explicitly means unavailable. */
  private static final Object ALLOCATIONS = allocationCounter();

  /** Prevents construction of this manual benchmark utility. */
  private HuronVidalSelectedPairPerformanceBenchmark() {
  }

  /**
   * Writes warmed rounds and untimed endpoint evidence without a timing acceptance assertion.
   *
   * @param args profile0/1/2, rule2/4, init1/init3/flash, output path, components5/15
   * @throws IOException if results cannot be written
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 5) {
      throw new IllegalArgumentException("Expected profile, rule, scenario, output path and components5/15");
    }
    int profile = Integer.parseInt(args[0]);
    int rule = Integer.parseInt(args[1]);
    String scenario = args[2];
    if (profile < 0 || profile > 2 || (rule != 2 && rule != 4)
        || !("init1".equals(scenario) || "init3".equals(scenario) || "flash".equals(scenario))) {
      throw new IllegalArgumentException("Invalid profile, rule or scenario");
    }
    int size = Integer.parseInt(args[4]);
    SystemInterface fluid = fluid(profile, size, false);
    double[] originalInputs = modelInputs(fluid);
    if (rule == 2) {
      fluid.setMixingRule(2);
    }
    if (!Arrays.equals(originalInputs, modelInputs(fluid))) {
      throw new IllegalStateException("Mixing-rule control changed pure inputs or ordinary kij");
    }
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.init(3);
    int warmup = "flash".equals(scenario) ? 1000 : 20000;
    int repetitions = "flash".equals(scenario) ? 50 : 2000;
    for (int i = 0; i < warmup; i++) {
      operation(fluid, operations, scenario, i);
    }
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[3]), StandardCharsets.UTF_8));
        PrintWriter states = new PrintWriter(
            Files.newBufferedWriter(Paths.get(args[3] + ".states.csv"), StandardCharsets.UTF_8))) {
      states.println("round,phase,type,beta,component,z,x,logphi");
      out.println(
          "profile,rule,scenario,components,round,operations,elapsed_ns,allocated_bytes,T_K,P_bara,topology,beta0,balance,normalization,log_fugacity,rich_water,aqueous_CO2");
      for (int round = 0; round < 10; round++) {
        long allocated = allocatedBytes();
        long start = System.nanoTime();
        for (int i = 0; i < repetitions; i++) {
          operation(fluid, operations, scenario, i);
        }
        long elapsed = System.nanoTime() - start;
        long after = allocatedBytes();
        long bytes = allocated < 0 || after < 0 ? -1 : after - allocated;
        fluid.init(3);
        double[] errors = residuals(fluid);
        int rich = 0;
        int aqueous = -1;
        StringBuilder topology = new StringBuilder();
        for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
          PhaseInterface state = fluid.getPhase(phase);
          if (state.getComponent("CO2").getx() > fluid.getPhase(rich).getComponent("CO2").getx()) {
            rich = phase;
          }
          if (aqueous < 0
              || state.getComponent("water").getx() > fluid.getPhase(aqueous).getComponent("water").getx()) {
            aqueous = phase;
          }
          if (phase > 0) {
            topology.append('/');
          }
          topology.append(state.getType());
          for (int i = 0; i < size; i++) {
            states.printf(Locale.ROOT, "%d,%d,%s,%.17g,%s,%.17g,%.17g,%.17g%n", round, phase, state.getType(),
                fluid.getBeta(phase), state.getComponent(i).getComponentName(), state.getComponent(i).getz(),
                state.getComponent(i).getx(), state.getComponent(i).getLogFugacityCoefficient());
          }
        }
        out.printf(Locale.ROOT, "%d,%d,%s,%d,%d,%d,%d,%d,%.17g,%.17g,%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g%n", profile,
            rule, scenario, size, round, repetitions, elapsed, bytes, fluid.getTemperature(), fluid.getPressure(),
            topology, fluid.getBeta(0), errors[0], errors[1], errors[2],
            fluid.getPhase(rich).getComponent("water").getx(), fluid.getPhase(aqueous).getComponent("CO2").getx());
      }
      if (out.checkError() || states.checkError()) {
        throw new IOException("Benchmark result write failed");
      }
    }
  }

  /**
   * Captures pure critical, cubic, alpha and ordinary kij inputs for every allocated phase before initialization.
   *
   * @param fluid configured worker
   * @return input fingerprint at the matched state
   */
  private static double[] modelInputs(SystemInterface fluid) {
    int count = fluid.getNumberOfComponents();
    double[] result = new double[fluid.getMaxNumberOfPhases() * count * (6 + count)];
    int index = 0;
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        ComponentInterface item = fluid.getPhase(phase).getComponent(component);
        result[index++] = item.getTC();
        result[index++] = item.getPC();
        result[index++] = item.getAcentricFactor();
        result[index++] = ((ComponentEosInterface) item).geta();
        result[index++] = ((ComponentEosInterface) item).getb();
        result[index++] = item.getAttractiveTerm().alpha(fluid.getTemperature());
      }
      EosMixingRulesInterface rule = (EosMixingRulesInterface) fluid.getPhase(phase).getMixingRule();
      for (int i = 0; i < count; i++) {
        for (int j = 0; j < count; j++) {
          result[index++] = rule.getBinaryInteractionParameter(i, j);
        }
      }
    }
    return result;
  }

  /**
   * Calculates untimed equilibrium residuals against the configured molar feed.
   *
   * @param fluid flashed synthetic mixture
   * @return maximum balance, normalization and log-fugacity errors
   */
  static double[] residuals(SystemInterface fluid) {
    if (fluid.getNumberOfPhases() < 2) {
      throw new IllegalStateException("Expected at least two phases");
    }
    double beta = 0.0;
    double balance = 0.0;
    double normalization = 0.0;
    double fugacity = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      beta += fluid.getBeta(phase);
      double sum = 0.0;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        double x = fluid.getPhase(phase).getComponent(i).getx();
        if (!Double.isFinite(x) || x <= 0.0) {
          throw new IllegalStateException("Invalid phase composition");
        }
        sum += x;
      }
      normalization = Math.max(normalization, Math.abs(sum - 1.0));
    }
    normalization = Math.max(normalization, Math.abs(beta - 1.0));
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double recovered = 0.0;
      double reference = Math.log(fluid.getPhase(0).getComponent(i).getx())
          + fluid.getPhase(0).getComponent(i).getLogFugacityCoefficient();
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        PhaseInterface state = fluid.getPhase(phase);
        recovered += fluid.getBeta(phase) * state.getComponent(i).getx();
        double log = Math.log(state.getComponent(i).getx()) + state.getComponent(i).getLogFugacityCoefficient();
        fugacity = Math.max(fugacity, Math.abs(reference - log));
      }
      balance = Math.max(balance, Math.abs(recovered - fluid.getComponent(i).getz()));
    }
    if (!Double.isFinite(balance) || !Double.isFinite(normalization) || !Double.isFinite(fugacity) || balance > 1e-10
        || normalization > 1e-12 || fugacity > 1e-8) {
      throw new IllegalStateException(
          "Equilibrium acceptance failed: " + balance + "/" + normalization + "/" + fugacity);
    }
    return new double[] {balance, normalization, fugacity};
  }

  /**
   * Creates an independent synthetic feed with only CO2/water selected as HV.
   *
   * @param profile declared source configuration
   * @param size five or fifteen components
   * @param reverse whether to reverse insertion order
   * @return fully configured unflashed fluid
   */
  static SystemInterface fluid(int profile, int size, boolean reverse) {
    if (profile < 0 || profile > 2 || !(size == 5 || size == 15)) {
      throw new IllegalArgumentException("Invalid profile or size");
    }
    SystemInterface fluid = profile == 1 ? new SystemSrkEos(353.15, 50.0) : new SystemPrEos(353.15, 50.0);
    String[] names = {"water", "CO2", "methane", "methanol", "MEG", "ethane", "propane", "i-butane", "n-butane",
        "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "nitrogen"};
    double[] small = {.3, .2, .4, .05, .05};
    double[] large = {.3, .1, .3, .03, .02, .025, .025, .025, .025, .025, .025, .025, .025, .025, .025};
    for (int i = 0; i < size; i++) {
      int index = reverse ? size - 1 - i : i;
      fluid.addComponent(names[index], size == 5 ? small[index] : large[index]);
    }
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    HuronVidalCo2SourceDiagnosticTest.configureProfile(fluid, profile, true);
    return fluid;
  }

  /**
   * Executes a selected initialization or nearby-temperature complete flash without diagnostics.
   *
   * @param fluid independently configured fluid
   * @param operations corresponding flash operations
   * @param scenario init1, init3 or flash
   * @param iteration alternating nearby-temperature index
   */
  private static void operation(SystemInterface fluid, ThermodynamicOperations operations, String scenario,
      int iteration) {
    if ("flash".equals(scenario)) {
      fluid.setTemperature(353.15 + .2 * (iteration % 2));
      operations.TPflash();
      fluid.init(3);
    } else {
      fluid.init("init1".equals(scenario) ? 1 : 3);
    }
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
