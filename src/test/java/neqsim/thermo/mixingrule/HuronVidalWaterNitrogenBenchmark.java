package neqsim.thermo.mixingrule;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Measures one EOS, model and scenario per JVM without switching HotSpot type profiles.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class HuronVidalWaterNitrogenBenchmark {
  /** Prevents benchmark instantiation. */
  private HuronVidalWaterNitrogenBenchmark() {
  }

  /**
   * Writes warmed timing and untimed topology/residual evidence for a single selection.
   *
   * @param args model0/1/2, rule2/4, init1/init3/flash, output path
   * @throws IOException if output cannot be written
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 4) {
      throw new IllegalArgumentException("Expected model, rule, scenario, output path");
    }
    int model = Integer.parseInt(args[0]);
    int rule = Integer.parseInt(args[1]);
    String scenario = args[2];
    if (model < 0 || model > 2 || (rule != 2 && rule != 4)
        || !("init1".equals(scenario) || "init3".equals(scenario) || "flash".equals(scenario))
        || (model == 2 && rule != 4)) {
      throw new IllegalArgumentException("Select model0/1/2, rule2/4 and init1/init3/flash; staged model requires4");
    }
    SystemInterface fluid = HuronVidalWaterNitrogenAssessmentTest.fluid(353.15, 50.0, model, false);
    if (rule == 2) {
      fluid.setMixingRule(2);
    }
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.init(3);
    for (int i = 0; i < 100; i++) {
      operation(fluid, operations, scenario, i);
    }
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[3]), StandardCharsets.UTF_8))) {
      out.println(
          "model,rule,scenario,round,operations,elapsed_ns,phases,balance,normalization,log_fugacity,gas_water,aqueous_nitrogen");
      for (int round = 0; round < 5; round++) {
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
          operation(fluid, operations, scenario, i);
        }
        long elapsed = System.nanoTime() - start;
        fluid.init(3);
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
        if (!Double.isFinite(balance) || !Double.isFinite(normalization) || !Double.isFinite(fugacity)
            || fluid.getNumberOfPhases() != 2 || balance > 1e-10 || normalization > 1e-12 || fugacity > 1e-8) {
          throw new IllegalStateException("Benchmark equilibrium contract failed");
        }
        out.printf(Locale.ROOT, "%d,%d,%s,%d,100,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g%n", model, rule, scenario, round,
            elapsed, fluid.getNumberOfPhases(), balance, normalization, fugacity,
            fluid.getPhase("gas").getComponent("water").getx(),
            fluid.getPhase("aqueous").getComponent("nitrogen").getx());
      }
      if (out.checkError()) {
        throw new IOException("Benchmark report failed");
      }
    }
  }

  /**
   * Performs a selected initialization or changed-temperature complete flash.
   *
   * @param fluid independently configured worker system
   * @param operations worker flash operations
   * @param scenario selected operation
   * @param iteration iteration controlling the nearby temperature
   */
  private static void operation(SystemInterface fluid, ThermodynamicOperations operations, String scenario,
      int iteration) {
    if ("flash".equals(scenario)) {
      fluid.setTemperature(353.15 + 0.2 * (iteration % 2));
      operations.TPflash();
      fluid.init(3);
    } else {
      fluid.init("init1".equals(scenario) ? 1 : 3);
    }
  }
}
