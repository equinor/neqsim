package neqsim.thermo.mixingrule;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentGENRTLmodifiedHV;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Manual benchmark for modified-NRTL evaluations and complete Huron-Vidal three-phase flashes.
 *
 * <p>
 * Arguments are a sample label, output CSV path, optional {@code SRK}/{@code PR} filter, optional scenario filter
 * ({@code kernel_init1}, {@code kernel_init3}, {@code phase_init1}, {@code phase_init3}, {@code flash_init1},
 * {@code flash_init3}), optional warmup-round count, optional measurement-round count and optional mixing rule
 * ({@code 2} for classical or {@code 4} for temperature-dependent Huron-Vidal; default four). Use a fresh JVM for each
 * EOS/scenario combination to avoid type-profile recompilation during measurement. Run paired baseline/candidate
 * samples in alternating order on an otherwise idle host; timing is deliberately not a CI assertion. The CSV includes a
 * numerical checksum so timing cannot silently discard evaluated work.
 * </p>
 *
 * <p>
 * Each kernel call constructs and initializes one four-component fixture before evaluating 20,000 activity
 * coefficients. Each complete-flash call constructs one fixture, performs an initial TP flash, then performs 60 TP
 * flashes with alternating temperature and the requested property initialization level. Default warmup is 50 kernel
 * calls or three complete-flash calls. The iteration counts and fixture construction are included in the measured
 * workload. Phase-initialization scenarios construct a forced gas at 310 K and 20 bara with methane/decane/water/MEG
 * mole fractions 0.65/0.20/0.12/0.03, then repeat initialization 2,000 times; their default warmup is five calls. The
 * prescribed GE kernels do not use the selected EOS mixing rule and have no direct classical counterpart. These
 * prescribed/synthetic cases do not qualify experimental parameter accuracy.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class HuronVidalPerformanceBenchmark {
  private static final Logger logger = LogManager.getLogger(HuronVidalPerformanceBenchmark.class);
  private static volatile double checksumSink;

  /** Prevents construction of the manual benchmark utility. */
  private HuronVidalPerformanceBenchmark() {
  }

  /**
   * Runs selected scenarios and appends elapsed nanoseconds and checksums to a CSV file.
   *
   * @param args sample label, CSV path, optional EOS, scenario, warmup rounds, measurement rounds and mixing rule
   * @throws IOException if the output CSV cannot be opened
   * @throws IllegalArgumentException if the argument count, selection or warmup count is invalid
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 2 || args.length > 7) {
      throw new IllegalArgumentException(
          "Usage: sample-label output.csv [SRK|PR] [scenario] [warmup-rounds] [measurement-rounds] [2|4]");
    }
    if (args.length > 2 && !"SRK".equals(args[2]) && !"PR".equals(args[2])) {
      throw new IllegalArgumentException("EOS filter must be SRK or PR");
    }
    String[] scenarios = {"kernel_init1", "kernel_init3", "phase_init1", "phase_init3", "flash_init1", "flash_init3"};
    boolean validScenario = args.length < 4;
    for (String scenario : scenarios) {
      validScenario |= args.length > 3 && scenario.equals(args[3]);
    }
    if (!validScenario) {
      throw new IllegalArgumentException("Unknown benchmark scenario: " + args[3]);
    }
    int warmupOverride = args.length > 4 ? Integer.parseInt(args[4]) : 0;
    if (args.length > 4 && warmupOverride < 1) {
      throw new IllegalArgumentException("Warmup rounds must be positive");
    }
    int measurementRounds = args.length > 5 ? Integer.parseInt(args[5]) : 1;
    if (measurementRounds < 1) {
      throw new IllegalArgumentException("Measurement rounds must be positive");
    }
    int mixingRule = args.length > 6 ? Integer.parseInt(args[6]) : 4;
    if (mixingRule != 2 && mixingRule != 4) {
      throw new IllegalArgumentException("Mixing rule must be classical (2) or temperature-dependent Huron-Vidal (4)");
    }
    File output = new File(args[1]);
    boolean header = !output.exists() || output.length() == 0L;
    try (PrintWriter writer = new PrintWriter(new FileWriter(output, true))) {
      if (header) {
        writer.println("sample,eos,scenario,mixing_rule,elapsed_ns,checksum");
      }
      for (boolean pengRobinson : new boolean[] {false, true}) {
        String eos = pengRobinson ? "PR" : "SRK";
        if (args.length > 2 && !eos.equals(args[2])) {
          continue;
        }
        for (String scenario : scenarios) {
          if (args.length > 3 && !scenario.equals(args[3])) {
            continue;
          }
          boolean kernel = scenario.startsWith("kernel");
          boolean phase = scenario.startsWith("phase");
          int init = scenario.endsWith("1") ? 1 : 3;
          int warmupRounds = warmupOverride > 0 ? warmupOverride : kernel ? 50 : phase ? 5 : 3;
          for (int round = 0; round < warmupRounds; round++) {
            if (kernel) {
              kernel(pengRobinson, init, 5000);
            } else if (phase) {
              phaseInitialization(pengRobinson, init, 2000, mixingRule);
            } else {
              completeFlash(pengRobinson, init, 60, mixingRule);
            }
          }
          for (int round = 0; round < measurementRounds; round++) {
            long start = System.nanoTime();
            double checksum;
            if (kernel) {
              checksum = kernel(pengRobinson, init, 5000);
            } else if (phase) {
              checksum = phaseInitialization(pengRobinson, init, 2000, mixingRule);
            } else {
              checksum = completeFlash(pengRobinson, init, 60, mixingRule);
            }
            long elapsed = System.nanoTime() - start;
            String rule = kernel ? "prescribed" : Integer.toString(mixingRule);
            writer.println(
                args[0] + "_" + round + "," + eos + "," + scenario + "," + rule + "," + elapsed + "," + checksum);
            logger.info("{} {} {} sample {}: {} ns, checksum {}", args[0], eos, scenario, round, elapsed, checksum);
          }
        }
      }
    }
    logger.debug("Last evaluated checksum {}", checksumSink);
  }

  /**
   * Evaluates prescribed modified-NRTL parameters with both fitted HV pairs and generated classic-pair interactions.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param init property initialization level, one or three
   * @param repetitions number of four-component activity evaluations
   * @return sum of activity coefficients and, at level three, first temperature derivatives
   */
  private static double kernel(boolean pengRobinson, int init, int repetitions) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(310.0, 60.0) : new SystemSrkEos(310.0, 60.0);
    String[] names = {"methane", "n-decane", "water", "MEG"};
    double[] amounts = {0.65, 0.20, 0.12, 0.03};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], amounts[i]);
    }
    fluid.setMixingRule(2);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(3);
    PhaseInterface phase = fluid.getPhase(0);
    phase.setInitType(init);
    double[][] alpha = new double[4][4];
    double[][] energy = new double[4][4];
    double[][] slope = new double[4][4];
    double[][] binary = new double[4][4];
    String[][] rules = new String[4][4];
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        rules[i][j] = i == j || i < 2 && j < 2 ? "Classic" : "HV";
        alpha[i][j] = 0.3;
        energy[i][j] = i == j ? 0.0 : 200.0 * (i - j);
        slope[i][j] = i == j ? 0.0 : 0.2 * (j - i);
      }
    }
    ComponentGENRTLmodifiedHV[] components = new ComponentGENRTLmodifiedHV[4];
    for (int i = 0; i < 4; i++) {
      components[i] = new ComponentGENRTLmodifiedHV(names[i], amounts[i], amounts[i], i);
    }
    double checksum = 0.0;
    for (int repetition = 0; repetition < repetitions; repetition++) {
      for (int i = 0; i < 4; i++) {
        checksum += components[i].getGamma(phase, 4, 310.0, 60.0, PhaseType.GAS, alpha, energy, slope, binary, rules);
        if (init == 3) {
          checksum += components[i].getLnGammadt();
        }
      }
    }
    checksumSink = checksum;
    return checksum;
  }

  /**
   * Initializes the same valid forced gas root repeatedly with either classical or Huron-Vidal mixing.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param init property initialization level, one or three
   * @param repetitions number of complete fluid initialization calls
   * @param mixingRule classical rule two or Huron-Vidal rule four
   * @return accumulated log fugacity coefficients and, at level three, scaled enthalpy and heat capacity
   */
  private static double phaseInitialization(boolean pengRobinson, int init, int repetitions, int mixingRule) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(310.0, 20.0) : new SystemSrkEos(310.0, 20.0);
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("n-decane", 0.20);
    fluid.addComponent("water", 0.12);
    fluid.addComponent("MEG", 0.03);
    fluid.setMixingRule(mixingRule);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(3);
    double checksum = 0.0;
    for (int repetition = 0; repetition < repetitions; repetition++) {
      fluid.init(init);
      for (int component = 0; component < 4; component++) {
        checksum += fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      }
      if (init == 3) {
        checksum += fluid.getEnthalpy() * 1.0e-4 + fluid.getCp() * 1.0e-3;
      }
    }
    checksumSink = checksum;
    return checksum;
  }

  /**
   * Runs the public TP-flash operation on a synthetic methane/decane/water/MEG three-phase fluid.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param init property initialization level after each flash
   * @param repetitions number of temperature-change and flash iterations after the initial flash
   * @param mixingRule classical rule two or Huron-Vidal rule four
   * @return sum of gas phase fraction, aqueous water fraction and gas MEG fraction
   */
  private static double completeFlash(boolean pengRobinson, int init, int repetitions, int mixingRule) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(298.15, 60.0) : new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("n-decane", 0.20);
    fluid.addComponent("water", 0.15);
    fluid.addComponent("MEG", 0.03);
    fluid.setMixingRule(mixingRule);
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    double checksum = 0.0;
    for (int repetition = 0; repetition < repetitions; repetition++) {
      fluid.setTemperature(298.15 + 0.2 * (repetition % 2));
      operations.TPflash();
      fluid.init(init);
      checksum += fluid.getBeta(0) + fluid.getPhase(PhaseType.AQUEOUS).getComponent("water").getx()
          + fluid.getPhase(PhaseType.GAS).getComponent("MEG").getx();
    }
    checksumSink = checksum;
    return checksum;
  }
}
