package neqsim.process.equipment.distillation;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Speed and robustness profiling for representative distillation columns.
 *
 * <p>
 * Results are written to {@code target/distillation_solver_profile.tsv} and the legacy
 * {@code target/solver_benchmark.txt} path for quick comparison between solver branches.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
@Tag("slow")
public class SolverSpeedBenchmark {

  /** Solver labels reported in the profile. */
  private static final String[] SOLVER_LABELS =
      {"DIRECT", "INSIDE_OUT", "MATRIX_IO", "NEWTON", "NAPHTALI", "AUTO"};

  /** Solver types matching {@link #SOLVER_LABELS}. */
  private static final DistillationColumn.SolverType[] SOLVER_TYPES = {
      DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
      DistillationColumn.SolverType.INSIDE_OUT,
      DistillationColumn.SolverType.MATRIX_INSIDE_OUT, DistillationColumn.SolverType.NEWTON,
      DistillationColumn.SolverType.NAPHTALI_SANDHOLM, DistillationColumn.SolverType.AUTO};

  /**
   * Create a standard deethanizer feed.
   *
   * @return configured feed fluid
   */
  private SystemInterface createDeethanizerFeed() {
    SystemInterface gas = new SystemSrkEos(216, 30.00);
    gas.addComponent("nitrogen", 1.67366E-3);
    gas.addComponent("CO2", 1.06819E-4);
    gas.addComponent("methane", 5.14168E-1);
    gas.addComponent("ethane", 1.92528E-1);
    gas.addComponent("propane", 1.70001E-1);
    gas.addComponent("i-butane", 3.14561E-2);
    gas.addComponent("n-butane", 5.58678E-2);
    gas.addComponent("i-pentane", 1.29573E-2);
    gas.addComponent("n-pentane", 1.23719E-2);
    gas.addComponent("n-hexane", 5.12878E-3);
    gas.addComponent("n-heptane", 1.0E-2);
    gas.setMixingRule("classic");
    return gas;
  }

  /**
   * Run the solver profile across representative distillation cases.
   *
   * @throws IOException if writing the TSV report fails
   */
  @Test
  public void benchmarkAllSolvers() throws IOException {
    StringBuilder report = new StringBuilder();
    appendHeader(report);

    String[] caseNames = {"deethanizer_5", "deethanizer_10", "depropanizer",
        "debutanizer", "lean_demethanizer"};
    for (int caseIndex = 0; caseIndex < caseNames.length; caseIndex++) {
      for (int solverIndex = 0; solverIndex < SOLVER_LABELS.length; solverIndex++) {
        DistillationColumn column = createBenchmarkColumn(caseNames[caseIndex],
            SOLVER_TYPES[solverIndex], SOLVER_LABELS[solverIndex]);
        long startTime = System.nanoTime();
        column.run();
        double wallTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;
        appendProfileRow(report, caseNames[caseIndex], SOLVER_LABELS[solverIndex], column,
            wallTimeSeconds);
      }
    }

    String profile = report.toString();
    System.out.println(profile);
    writeReport("target/distillation_solver_profile.tsv", profile);
    writeReport("target/solver_benchmark.txt", profile);
  }

  /**
   * Append the TSV header row.
   *
   * @param report profile report builder
   */
  private void appendHeader(StringBuilder report) {
    report.append("case\tsolver\ttrays\titers\twallTime_s\treportedTime_s\tsolved\tstatus")
        .append("\tsolverUsed\tgas_kg_hr\tliquid_kg_hr\tmassResidual\tenergyResidual")
        .append("\ttemperatureResidual_K\tinsideOutSweeps\tinsideOutInnerIterations")
        .append("\tkResidual\tsurrogateResidual\tsurrogateResets\tmatrixWarmStartUsed")
        .append("\tmatrixWarmStartBypassed\tmatrixTime_s\tnaphtaliAnalyticColumns")
        .append("\tnaphtaliFiniteDifferenceColumns\tnaphtaliThermoEvaluations")
        .append("\tnaphtaliThermoCacheHits\tnaphtaliJacobianBuild_s")
        .append("\tnaphtaliBlockSolves\tnaphtaliDenseFallbacks\tnaphtaliLinearSolve_s\n");
  }

  /**
   * Append one profiling row.
   *
   * @param report profile report builder
   * @param caseName benchmark case name
   * @param solverLabel configured solver label
   * @param column solved column
   * @param wallTimeSeconds external wall time in seconds
   */
  private void appendProfileRow(StringBuilder report, String caseName, String solverLabel,
      DistillationColumn column, double wallTimeSeconds) {
    report.append(String.format(Locale.ROOT,
        "%s\t%s\t%d\t%d\t%.6f\t%.6f\t%s\t%s\t%s\t%.6f\t%.6f\t%.6e\t%.6e\t%.6e"
            + "\t%d\t%d\t%.6e\t%.6e\t%d\t%s\t%s\t%.6f\t%d\t%d\t%d\t%d\t%.6f"
            + "\t%d\t%d\t%.6f%n",
        caseName, solverLabel, column.getNumberOfTrays(), column.getLastIterationCount(),
        wallTimeSeconds, column.getLastSolveTimeSeconds(), column.solved() ? "YES" : "NO",
        statusName(column.getLastSolveStatus()), solverName(column.getLastSolverTypeUsed()),
        column.getGasOutStream().getFlowRate("kg/hr"),
        column.getLiquidOutStream().getFlowRate("kg/hr"), column.getLastMassResidual(),
        column.getLastEnergyResidual(), column.getLastTemperatureResidual(),
        column.getLastInsideOutOuterFlashSweeps(), column.getLastInsideOutInnerLoopIterations(),
        column.getLastInsideOutKValueResidual(), column.getLastInsideOutSurrogateResidual(),
        column.getLastInsideOutSurrogateResetCount(),
        column.wasMatrixInsideOutWarmStartUsed() ? "YES" : "NO",
        column.wasMatrixInsideOutWarmStartBypassed() ? "YES" : "NO",
        column.getLastMatrixInsideOutSolveTimeSeconds(),
        column.getLastNaphtaliAnalyticJacobianColumns(),
        column.getLastNaphtaliFiniteDifferenceJacobianColumns(),
        column.getLastNaphtaliThermoEvaluationCount(),
        column.getLastNaphtaliThermoCacheHitCount(),
        column.getLastNaphtaliJacobianBuildTimeSeconds(),
        column.getLastNaphtaliBlockLinearSolveCount(),
        column.getLastNaphtaliDenseLinearSolveCount(),
        column.getLastNaphtaliLinearSolveTimeSeconds()));
  }

  /**
   * Create a configured benchmark column.
   *
   * @param caseName benchmark case name
   * @param solverType solver type to configure
   * @param solverLabel solver label used in stream and column names
   * @return configured column
   */
  private DistillationColumn createBenchmarkColumn(String caseName,
      DistillationColumn.SolverType solverType, String solverLabel) {
    if ("deethanizer_5".equals(caseName)) {
      return createDeethanizerColumn(caseName, solverType, solverLabel, 5);
    }
    if ("deethanizer_10".equals(caseName)) {
      return createDeethanizerColumn(caseName, solverType, solverLabel, 10);
    }
    if ("depropanizer".equals(caseName)) {
      return createThreeComponentFractionator(caseName, solverType, solverLabel, "propane",
          "n-butane", "n-pentane", 10.0, 318.15, 303.15, 363.15);
    }
    if ("debutanizer".equals(caseName)) {
      return createThreeComponentFractionator(caseName, solverType, solverLabel, "n-butane",
          "n-pentane", "n-hexane", 6.0, 353.15, 318.15, 403.15);
    }
    return createLeanDemethanizer(caseName, solverType, solverLabel);
  }

  /**
   * Create a deethanizer benchmark case.
   *
   * @param caseName benchmark case name
   * @param solverType solver type to configure
   * @param solverLabel solver label used in stream and column names
   * @param trayCount number of simple trays
   * @return configured column
   */
  private DistillationColumn createDeethanizerColumn(String caseName,
      DistillationColumn.SolverType solverType, String solverLabel, int trayCount) {
    Stream feed = new Stream("feed_" + caseName + "_" + solverLabel,
        createDeethanizerFeed().clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("bench_" + caseName + "_" + solverLabel,
        trayCount, true, false);
    column.addFeedStream(feed, trayCount);
    column.getReboiler().setOutTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(trayCount <= 5 ? 50 : 80);
    configureSolver(column, solverType);
    return column;
  }

  /**
   * Create a three-component fractionator benchmark case.
   *
   * @param caseName benchmark case name
   * @param solverType solver type to configure
   * @param solverLabel solver label used in stream and column names
   * @param lightComponent light component name
   * @param middleComponent middle component name
   * @param heavyComponent heavy component name
   * @param pressure pressure in bara
   * @param feedTemperature feed temperature in Kelvin
   * @param condenserTemperature condenser temperature in Kelvin
   * @param reboilerTemperature reboiler temperature in Kelvin
   * @return configured column
   */
  private DistillationColumn createThreeComponentFractionator(String caseName,
      DistillationColumn.SolverType solverType, String solverLabel, String lightComponent,
      String middleComponent, String heavyComponent, double pressure, double feedTemperature,
      double condenserTemperature, double reboilerTemperature) {
    SystemInterface fluid = new SystemSrkEos(feedTemperature, pressure);
    fluid.addComponent(lightComponent, 0.35);
    fluid.addComponent(middleComponent, 0.45);
    fluid.addComponent(heavyComponent, 0.20);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed_" + caseName + "_" + solverLabel, fluid);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();

    DistillationColumn column =
        new DistillationColumn("bench_" + caseName + "_" + solverLabel, 6, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(pressure);
    column.setBottomPressure(pressure + 0.2);
    column.getCondenser().setOutTemperature(condenserTemperature);
    column.getReboiler().setOutTemperature(reboilerTemperature);
    column.setCondenserRefluxRatio(1.8);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    configureSolver(column, solverType);
    return column;
  }

  /**
   * Create a lean-gas demethanizer benchmark case.
   *
   * @param caseName benchmark case name
   * @param solverType solver type to configure
   * @param solverLabel solver label used in stream and column names
   * @return configured column
   */
  private DistillationColumn createLeanDemethanizer(String caseName,
      DistillationColumn.SolverType solverType, String solverLabel) {
    SystemInterface fluid = new SystemSrkEos(216.0, 30.0);
    fluid.addComponent("methane", 0.55);
    fluid.addComponent("ethane", 0.20);
    fluid.addComponent("propane", 0.15);
    fluid.addComponent("n-butane", 0.07);
    fluid.addComponent("n-pentane", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed_" + caseName + "_" + solverLabel, fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column =
        new DistillationColumn("bench_" + caseName + "_" + solverLabel, 7, true, false);
    column.addFeedStream(feed, 4);
    column.setTopPressure(30.0);
    column.setBottomPressure(31.0);
    column.getReboiler().setOutTemperature(273.15 + 100.0);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    configureSolver(column, solverType);
    return column;
  }

  /**
   * Configure solver options shared by benchmark cases.
   *
   * @param column column to configure
   * @param solverType solver type to configure
   */
  private void configureSolver(DistillationColumn column, DistillationColumn.SolverType solverType) {
    column.setSolverType(solverType);
    if (solverType == DistillationColumn.SolverType.INSIDE_OUT
        || solverType == DistillationColumn.SolverType.MATRIX_INSIDE_OUT) {
      column.setInnerLoopSteps(2);
    }
  }

  /**
   * Get a safe solve status name.
   *
   * @param status solve status
   * @return status name or {@code null}
   */
  private String statusName(DistillationColumn.SolveStatus status) {
    return status == null ? "null" : status.name();
  }

  /**
   * Get a safe solver type name.
   *
   * @param solverType solver type
   * @return solver type name or {@code null}
   */
  private String solverName(DistillationColumn.SolverType solverType) {
    return solverType == null ? "null" : solverType.name();
  }

  /**
   * Write a profile report file.
   *
   * @param path output path
   * @param report report content
   * @throws IOException if writing fails
   */
  private void writeReport(String path, String report) throws IOException {
    try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
      writer.print(report);
    }
  }
}
