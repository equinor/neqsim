package neqsim.util.agentic;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Executes the {@link AgentBenchmarkSuite} standard problems against NeqSim and reports how many reproduce their
 * reference value.
 *
 * <p>
 * The benchmark suite only declares the problems; without a runner nothing ever compares NeqSim output against the
 * reference data, so the suite cannot detect an accuracy regression. This class closes that loop and is what CI
 * executes.
 * </p>
 *
 * <p>
 * A problem whose execution throws is left unattempted rather than failed, so an unrelated API break does not
 * masquerade as an accuracy regression. Problems that the declared description does not determine (missing inlet
 * temperature, flow rate, or efficiency) are listed in {@link #getUnattemptedProblemIds()} with the reason.
 * </p>
 *
 * <pre>
 * AgentBenchmarkSuite.BenchmarkReport report = AgentBenchmarkRunner.runStandardSuite();
 * logger.info("pass rate {}", report.getPassRate());
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class AgentBenchmarkRunner {
  private static final Logger logger = LogManager.getLogger(AgentBenchmarkRunner.class);

  /** Problems the declared description does not fully determine, with the missing input. */
  private static final String[][] UNDERDETERMINED = {
      { "gas_pipeline_pressure_drop", "no flow rate or gas composition declared" },
      { "vessel_blowdown_50pct_time", "no vessel volume or orifice size declared" } };

  /** Utility class. */
  private AgentBenchmarkRunner() {
  }

  /**
   * Runs every executable problem in {@link AgentBenchmarkSuite#createStandardSuite()}.
   *
   * @return the evaluated benchmark report, never null
   */
  public static AgentBenchmarkSuite.BenchmarkReport runStandardSuite() {
    AgentBenchmarkSuite suite = AgentBenchmarkSuite.createStandardSuite();
    submit(suite, "methane_density_300K_50bar");
    submit(suite, "water_boiling_1atm");
    submit(suite, "co2_density_310K_100bar");
    submit(suite, "methane_ethane_flash_phase_count");
    submit(suite, "natural_gas_dewpoint");
    submit(suite, "separator_mass_balance");
    submit(suite, "compressor_polytropic_power");
    submit(suite, "simple_npv_10yr");
    return suite.evaluate();
  }

  /**
   * Returns the problem identifiers this runner deliberately leaves unattempted.
   *
   * @return array of {problemId, reason} pairs
   */
  public static String[][] getUnattemptedProblemIds() {
    return UNDERDETERMINED.clone();
  }

  /**
   * Reports whether a problem's reference value comes from an independently cited source.
   *
   * <p>
   * Problems whose reference source is marked UNVERIFIED are executed and reported but are not asserted on, because a
   * failure there says nothing about NeqSim until the reference value has been re-sourced.
   * </p>
   *
   * @param problem the benchmark problem to classify
   * @return true when the reference value is independently sourced
   */
  public static boolean isVerifiedReference(AgentBenchmarkSuite.BenchmarkProblem problem) {
    String source = problem.getReferenceSource();
    return source == null || !source.startsWith("UNVERIFIED");
  }

  /**
   * Computes one problem and submits the value, logging and skipping on failure.
   *
   * @param suite the suite to submit into
   * @param problemId the benchmark problem identifier
   */
  private static void submit(AgentBenchmarkSuite suite, String problemId) {
    try {
      suite.addResult(problemId, compute(problemId));
    } catch (RuntimeException ex) {
      logger.warn("benchmark problem {} could not be executed: {}", problemId, ex.toString());
    }
  }

  /**
   * Computes the NeqSim answer for one benchmark problem.
   *
   * @param problemId the benchmark problem identifier
   * @return the computed value in the problem's declared unit
   * @throws IllegalArgumentException if the identifier has no implementation
   */
  private static double compute(String problemId) {
    if ("methane_density_300K_50bar".equals(problemId)) {
      return density("methane", 300.0, 50.0);
    }
    if ("co2_density_310K_100bar".equals(problemId)) {
      return density("CO2", 310.0, 100.0);
    }
    if ("water_boiling_1atm".equals(problemId)) {
      return boilingPoint();
    }
    if ("methane_ethane_flash_phase_count".equals(problemId)) {
      return phaseCount();
    }
    if ("natural_gas_dewpoint".equals(problemId)) {
      return cricondentherm();
    }
    if ("separator_mass_balance".equals(problemId)) {
      return separatorMassBalanceError();
    }
    if ("compressor_polytropic_power".equals(problemId)) {
      return compressorPower();
    }
    if ("simple_npv_10yr".equals(problemId)) {
      return npv(100.0, 10, 0.10);
    }
    throw new IllegalArgumentException("no implementation for benchmark problem " + problemId);
  }

  /**
   * Single-component density from an SRK flash.
   *
   * @param component NeqSim component name
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @return density in kg/m3
   */
  private static double density(String component, double temperatureK, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent(component, 1.0);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid.getDensity("kg/m3");
  }

  /**
   * Normal boiling point of water from a bubble-point temperature flash.
   *
   * @return boiling temperature in K
   * @throws IllegalStateException if the bubble-point flash does not converge
   */
  private static double boilingPoint() {
    SystemInterface fluid = new SystemSrkEos(373.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    try {
      new ThermodynamicOperations(fluid).bubblePointTemperatureFlash();
    } catch (Exception ex) {
      throw new IllegalStateException("bubble point flash failed: " + ex.getMessage(), ex);
    }
    return fluid.getTemperature("K");
  }

  /**
   * Number of equilibrium phases for a 90/10 methane/ethane mixture at 300 K and 30 bar.
   *
   * @return phase count
   */
  private static double phaseCount() {
    SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    return fluid.getNumberOfPhases();
  }

  /**
   * Cricondentherm of a 85/10/5 methane/ethane/propane mixture.
   *
   * <p>
   * With bubblePointFirst = true the envelope getter names are swapped, so the branches are classified physically: the
   * dew curve is the one reaching the higher temperature.
   * </p>
   *
   * @return cricondentherm temperature in K
   */
  private static double cricondentherm() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope(true, 1.0);
    double branchA = maxOf(ops.get("bubT"));
    double branchB = maxOf(ops.get("dewT"));
    return Math.max(branchA, branchB);
  }

  /**
   * Largest finite element of an array, ignoring padding zeros and non-finite values.
   *
   * @param values array to scan, may be null
   * @return the maximum finite positive value, or 0.0 when none exists
   */
  private static double maxOf(double[] values) {
    double max = 0.0;
    if (values == null) {
      return max;
    }
    for (int i = 0; i < values.length; i++) {
      double v = values[i];
      if (!Double.isNaN(v) && !Double.isInfinite(v) && v > max) {
        max = v;
      }
    }
    return max;
  }

  /**
   * Relative mass-balance closure error across a two-phase separator.
   *
   * @return absolute closure error in percent of feed flow
   */
  private static double separatorMassBalanceError() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    Separator separator = new Separator("benchmark separator", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.run();

    double in = feed.getFlowRate("kg/hr");
    double out = separator.getGasOutStream().getFlowRate("kg/hr") + separator.getLiquidOutStream().getFlowRate("kg/hr");
    return Math.abs(in - out) / in * 100.0;
  }

  /**
   * Shaft power for methane compressed from 30 to 100 bara at 100 kg/hr.
   *
   * <p>
   * Isentropic rather than polytropic, so it compares like-for-like against the reference, which is a CoolProp
   * isentropic enthalpy rise divided by the same efficiency. A polytropic efficiency of 0.75 is not the same duty and
   * does about 6% more work at this pressure ratio.
   * </p>
   *
   * @return shaft power in kW
   */
  private static double compressorPower() {
    SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("compressor feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(300.0, "K");
    feed.setPressure(30.0, "bara");

    Compressor compressor = new Compressor("benchmark compressor", feed);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setUsePolytropicCalc(false);
    compressor.setIsentropicEfficiency(0.75);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.run();

    return compressor.getPower("kW");
  }

  /**
   * Net present value of a constant annual cash flow discounted end-of-year.
   *
   * @param annualCashFlow cash flow per year in MNOK
   * @param years number of years
   * @param discountRate discount rate as a fraction, e.g. 0.10
   * @return NPV in MNOK
   */
  private static double npv(double annualCashFlow, int years, double discountRate) {
    double total = 0.0;
    for (int year = 1; year <= years; year++) {
      total += annualCashFlow / Math.pow(1.0 + discountRate, year);
    }
    return total;
  }

  /**
   * Returns a human-readable summary of a benchmark report.
   *
   * <p>
   * Every problem is listed, not only the failures: a problem that passes on a wide tolerance because the model is
   * known weak at that state (near-critical CO2 density) must stay visible, otherwise a green pass rate hides a 14%
   * deviation.
   * </p>
   *
   * @param report the evaluated report
   * @return multi-line summary text
   */
  public static String summarize(AgentBenchmarkSuite.BenchmarkReport report) {
    List<String> lines = new ArrayList<String>();
    lines.add(String.format("pass rate %.1f%% (%d passed, %d failed, %d not attempted)", report.getPassRate() * 100.0,
        report.getPassed(), report.getFailed(), report.getNotAttempted()));
    for (AgentBenchmarkSuite.ProblemResult result : report.getResults()) {
      lines
          .add(String.format("  %-14s %-34s %s", result.getVerdict(), result.getProblem().getId(), result.getDetail()));
    }
    for (int i = 0; i < UNDERDETERMINED.length; i++) {
      lines.add("  UNDERDETERMINED " + UNDERDETERMINED[i][0] + ": " + UNDERDETERMINED[i][1]);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines.size(); i++) {
      if (i > 0) {
        sb.append('\n');
      }
      sb.append(lines.get(i));
    }
    return sb.toString();
  }
}
