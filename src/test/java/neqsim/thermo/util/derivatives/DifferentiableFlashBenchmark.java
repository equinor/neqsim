package neqsim.thermo.util.derivatives;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Manual paired benchmark for scalar and batched equilibrium property gradients; never a wall-clock CI gate.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DifferentiableFlashBenchmark {
  /** Benchmark logger. */
  private static final Logger logger = LogManager.getLogger(DifferentiableFlashBenchmark.class);
  /** Observable sink keeping calculations live. */
  private static volatile double sink;
  /** Common observable workload. */
  private static final String[] NAMES = {"density", "enthalpy", "entropy", "Cp", "compressibility"};

  /** Prevents construction. */
  private DifferentiableFlashBenchmark() {
  }

  /**
   * Constructs a fixed public benchmark case.
   *
   * @param model SRK, PR or CPA
   * @return converged equilibrium
   */
  private static SystemInterface fluid(int model) {
    SystemInterface f;
    if (model == 2) {
      f = new SystemSrkCPAstatoil(298.15, 50.0);
      f.addComponent("methane", 0.98);
      f.addComponent("water", 0.02);
      f.setMixingRule(10);
    } else {
      f = model == 0 ? new SystemSrkEos(250.0, 30.0) : new SystemPrEos(250.0, 30.0);
      f.addComponent("methane", 0.55);
      f.addComponent("ethane", 0.2);
      f.addComponent("propane", 0.15);
      f.addComponent("n-heptane", 0.1);
      f.setMixingRule(2);
    }
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    return f;
  }

  /**
   * Computes the same requested observables, consuming every returned derivative.
   *
   * @param f equilibrium input
   * @param batch true to share local states
   * @return checksum
   */
  private static double evaluate(SystemInterface f, boolean batch) {
    DifferentiableFlash derivative = new DifferentiableFlash(f);
    PropertyGradient[] values;
    if (batch) {
      values = derivative.computePropertyGradients(NAMES);
    } else {
      values = new PropertyGradient[NAMES.length];
      for (int i = 0; i < NAMES.length; i++) {
        values[i] = derivative.computePropertyGradient(NAMES[i]);
      }
    }
    double total = 0.0;
    for (PropertyGradient value : values) {
      total += value.getValue();
      for (double component : value.toArray()) {
        total += component;
      }
    }
    return total;
  }

  /**
   * Runs a warmed timed workload excluding fixture construction.
   *
   * @param f equilibrium case
   * @param batch batch mode
   * @param count repetition count
   * @return nanoseconds per complete five-property workload
   */
  private static double time(SystemInterface f, boolean batch, int count) {
    long start = System.nanoTime();
    double checksum = 0.0;
    for (int i = 0; i < count; i++) {
      checksum += evaluate(f, batch);
    }
    sink = checksum;
    return (System.nanoTime() - start) / (double) count;
  }

  /**
   * Emits per-round paired results with alternating execution order.
   *
   * @param args optional repetitions per round and round count
   */
  public static void main(String[] args) {
    int count = args.length == 0 ? 100 : Integer.parseInt(args[0]);
    int rounds = args.length < 2 ? 5 : Integer.parseInt(args[1]);
    boolean legacy = Boolean.getBoolean("benchmark.legacy");
    for (int model = 0; model < 3; model++) {
      SystemInterface f = fluid(model);
      for (int i = 0; i < 40; i++) {
        sink = evaluate(f, false);
        if (!legacy) {
          sink = evaluate(f, true);
        }
      }
      for (int round = 0; round < rounds; round++) {
        double scalar;
        double batch = Double.NaN;
        if (!legacy && round % 2 == 1) {
          batch = time(f, true, count);
          scalar = time(f, false, count);
        } else {
          scalar = time(f, false, count);
          if (!legacy) {
            batch = time(f, true, count);
          }
        }
        logger.info("gradient-benchmark model={} round={} scalar_ns={} batch_ns={} checksum={}", model, round, scalar,
            batch, sink);
      }
    }
  }
}
