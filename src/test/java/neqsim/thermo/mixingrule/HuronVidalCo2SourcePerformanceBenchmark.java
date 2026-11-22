package neqsim.thermo.mixingrule;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Arrays;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Measures declared binary source configurations with one EOS, rule and scenario per JVM. Physical accuracy and
 * different rule2/rule4 solver work remain separate from timing.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class HuronVidalCo2SourcePerformanceBenchmark {
  /** Prevents construction of this manual benchmark utility. */
  private HuronVidalCo2SourcePerformanceBenchmark() {
  }

  /**
   * Writes warmed rounds and untimed endpoint evidence without a timing acceptance assertion.
   *
   * @param args profile0/1/2, rule2/4, init1/init3/flash, output path
   * @throws IOException if results cannot be written
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 4) {
      throw new IllegalArgumentException("Expected profile, rule, scenario and output path");
    }
    int profile = Integer.parseInt(args[0]);
    int rule = Integer.parseInt(args[1]);
    String scenario = args[2];
    if (profile < 0 || profile > 2 || (rule != 2 && rule != 4)
        || !("init1".equals(scenario) || "init3".equals(scenario) || "flash".equals(scenario))) {
      throw new IllegalArgumentException("Invalid profile, rule or scenario");
    }
    SystemInterface fluid = HuronVidalCo2SourceDiagnosticTest.fluid(353.15, 50.0, profile, false);
    double[] originalPure = pureInputs(fluid);
    if (rule == 2) {
      fluid.setMixingRule(2);
    }
    if (!Arrays.equals(originalPure, pureInputs(fluid))) {
      throw new IllegalStateException("Mixing-rule control changed pure inputs");
    }
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.init(3);
    int warmup = "flash".equals(scenario) ? 3000 : 100000;
    int repetitions = "flash".equals(scenario) ? 100 : 10000;
    for (int i = 0; i < warmup; i++) {
      operation(fluid, operations, scenario, i);
    }
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[3]), StandardCharsets.UTF_8))) {
      out.println(
          "profile,rule,scenario,round,operations,elapsed_ns,T_K,P_bara,topology,beta0,balance,normalization,log_fugacity,rich_water,aqueous_CO2");
      for (int round = 0; round < 10; round++) {
        long start = System.nanoTime();
        for (int i = 0; i < repetitions; i++) {
          operation(fluid, operations, scenario, i);
        }
        long elapsed = System.nanoTime() - start;
        fluid.init(3);
        double[] errors = residuals(fluid);
        int rich = fluid.getPhase(0).getComponent("CO2").getx() > fluid.getPhase(1).getComponent("CO2").getx() ? 0 : 1;
        out.printf(Locale.ROOT, "%d,%d,%s,%d,%d,%d,%.17g,%.17g,%s/%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g%n", profile,
            rule, scenario, round, repetitions, elapsed, fluid.getTemperature(), fluid.getPressure(),
            fluid.getPhase(0).getType(), fluid.getPhase(1).getType(), fluid.getBeta(0), errors[0], errors[1], errors[2],
            fluid.getPhase(rich).getComponent("water").getx(), fluid.getPhase(1 - rich).getComponent("CO2").getx());
      }
      if (out.checkError()) {
        throw new IOException("Benchmark result write failed");
      }
    }
  }

  /**
   * Captures pure critical, cubic and alpha inputs for every allocated phase before initialization.
   *
   * @param fluid configured worker
   * @return pure-property fingerprint at the matched state
   */
  private static double[] pureInputs(SystemInterface fluid) {
    double[] result = new double[fluid.getMaxNumberOfPhases() * 2 * 6];
    int index = 0;
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      for (int component = 0; component < 2; component++) {
        ComponentInterface item = fluid.getPhase(phase).getComponent(component);
        result[index++] = item.getTC();
        result[index++] = item.getPC();
        result[index++] = item.getAcentricFactor();
        result[index++] = ((ComponentEosInterface) item).geta();
        result[index++] = ((ComponentEosInterface) item).getb();
        result[index++] = item.getAttractiveTerm().alpha(fluid.getTemperature());
      }
    }
    return result;
  }

  /**
   * Calculates untimed binary equilibrium residuals using the fixed equal-mole feed.
   *
   * @param fluid flashed binary system
   * @return maximum balance, normalization and log-fugacity errors
   */
  private static double[] residuals(SystemInterface fluid) {
    if (fluid.getNumberOfPhases() != 2) {
      throw new IllegalStateException("Expected two phases");
    }
    double balance = 0.0;
    double normalization = Math.abs(fluid.getBeta(0) + fluid.getBeta(1) - 1.0);
    double fugacity = 0.0;
    for (int component = 0; component < 2; component++) {
      double recovered = 0.0;
      double[] log = new double[2];
      for (int phase = 0; phase < 2; phase++) {
        PhaseInterface state = fluid.getPhase(phase);
        double x = state.getComponent(component).getx();
        recovered += fluid.getBeta(phase) * x;
        normalization = Math.max(normalization,
            Math.abs(state.getComponent(0).getx() + state.getComponent(1).getx() - 1.0));
        log[phase] = Math.log(x) + state.getComponent(component).getLogFugacityCoefficient();
      }
      balance = Math.max(balance, Math.abs(recovered - 0.5));
      fugacity = Math.max(fugacity, Math.abs(log[0] - log[1]));
    }
    if (!Double.isFinite(balance) || !Double.isFinite(normalization) || !Double.isFinite(fugacity) || balance > 1e-10
        || normalization > 1e-12 || fugacity > 1e-8) {
      throw new IllegalStateException("Equilibrium acceptance failed");
    }
    return new double[] {balance, normalization, fugacity};
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
}
