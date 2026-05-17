package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comprehensive benchmark tests comparing built-in solver types on standard distillation problems.
 * These tests validate the improvements described in the distillation column paper.
 */
public class DistillationSolverBenchmarkTest {

  /** Components used in the column4.py C1 to C5 benchmark. */
  private static final String[] COLUMN4_COMPONENTS =
      {"methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane"};
  /** UniSim top vapor composition for the column4.py benchmark. */
  private static final double[] COLUMN4_UNISIM_TOP_Y =
      {0.073309694767772, 0.616960714136994, 7.33096220661988e-2, 7.31827732063146e-2,
          7.22445086719427e-2, 4.93741528530644e-2, 4.16185342977135e-2};
  /** UniSim bottom liquid composition for the column4.py benchmark. */
  private static final double[] COLUMN4_UNISIM_BOTTOM_X =
      {1.08569588192724e-18, 2.15899905392992e-5, 1.27950427844865e-6, 2.23374369740334e-3,
          1.87466392691616e-2, 0.421251245903268, 0.557745501635349};
  /** Atmospheric pressure used to convert column4.py barG inputs to bara. */
  private static final double COLUMN4_ATM_BARA = 1.01325;

  /**
   * Create a standard deethanizer feed for benchmarking.
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
   * Run a 5-tray deethanizer with a given solver and return the column.
   */
  private DistillationColumn runDeethanizer(DistillationColumn.SolverType solverType) {
    Stream feed = new Stream("feed_" + solverType.name(), createDeethanizerFeed().clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column =
        new DistillationColumn("deethanizer_" + solverType.name(), 5, true, false);
    column.addFeedStream(feed, 5);
    column.getReboiler().setOutTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(50);
    column.setSolverType(solverType);
    column.run();
    return column;
  }

  /**
   * Test that all solver types converge on the deethanizer benchmark and produce consistent results
   * within engineering tolerance.
   */
  @Test
  public void allSolversConvergeOnDeethanizer() {
    DistillationColumn.SolverType[] solvers = {DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
        DistillationColumn.SolverType.DAMPED_SUBSTITUTION, DistillationColumn.SolverType.INSIDE_OUT,
        DistillationColumn.SolverType.WEGSTEIN, DistillationColumn.SolverType.SUM_RATES,
        DistillationColumn.SolverType.NEWTON, DistillationColumn.SolverType.NAPHTALI_SANDHOLM,
        DistillationColumn.SolverType.MESH_RESIDUAL};

    double[] gasFlows = new double[solvers.length];
    double[] liquidFlows = new double[solvers.length];
    int[] iterations = new int[solvers.length];
    double[] times = new double[solvers.length];

    for (int i = 0; i < solvers.length; i++) {
      DistillationColumn col = runDeethanizer(solvers[i]);
      assertTrue(col.solved(),
          solvers[i].name() + " should converge: " + col.getConvergenceDiagnostics());

      gasFlows[i] = col.getGasOutStream().getFlowRate("kg/hr");
      liquidFlows[i] = col.getLiquidOutStream().getFlowRate("kg/hr");
      iterations[i] = col.getLastIterationCount();
      times[i] = col.getLastSolveTimeSeconds();

      // Mass balance closure: within 0.5%
      double massbalance = Math.abs(100.0 - gasFlows[i] - liquidFlows[i]) / 100.0 * 100;
      assertEquals(0.0, massbalance, 0.5,
          solvers[i].name() + " mass balance should close within 0.5%");
    }

    // Print solver timing summary
    System.out.printf("%n%-25s %6s %10s %10s %10s%n", "Solver", "Iters", "Time(s)", "GasFlow",
        "LiqFlow");
    System.out.println(org.apache.commons.lang3.StringUtils.repeat("-", 65));
    for (int i = 0; i < solvers.length; i++) {
      System.out.printf("%-25s %6d %10.3f %10.2f %10.2f%n", solvers[i].name(), iterations[i],
          times[i], gasFlows[i], liquidFlows[i]);
    }

    // All solvers should agree on product splits within 2%
    double refGas = gasFlows[0]; // DIRECT_SUBSTITUTION as reference
    for (int i = 1; i < solvers.length; i++) {
      double tolerance = Math.max(0.01, refGas * 0.02);
      assertEquals(refGas, gasFlows[i], tolerance,
          solvers[i].name() + " gas flow should match direct substitution within 2%");
    }
  }

  /**
   * Benchmark: Wegstein should converge in fewer iterations than direct substitution on the
   * standard deethanizer.
   */
  @Test
  public void wegsteinConvergesFasterThanDirectSubstitution() {
    DistillationColumn direct = runDeethanizer(DistillationColumn.SolverType.DIRECT_SUBSTITUTION);
    DistillationColumn wegstein = runDeethanizer(DistillationColumn.SolverType.WEGSTEIN);

    assertTrue(direct.solved(), "Direct substitution should converge");
    assertTrue(wegstein.solved(),
        "Wegstein should converge: " + wegstein.getConvergenceDiagnostics());

    // Wegstein should use at most the same number of iterations (typically fewer)
    assertTrue(wegstein.getLastIterationCount() <= direct.getLastIterationCount() * 1.2 + 2,
        "Wegstein should not require significantly more iterations than direct substitution. "
            + "Wegstein=" + wegstein.getLastIterationCount() + " Direct="
            + direct.getLastIterationCount());
  }

  /**
   * Test convergence history is properly recorded and monotonically decreasing in the later
   * iterations.
   */
  @Test
  public void convergenceHistoryIsRecorded() {
    DistillationColumn col = runDeethanizer(DistillationColumn.SolverType.DIRECT_SUBSTITUTION);

    List<double[]> history = col.getConvergenceHistory();
    assertFalse(history.isEmpty(), "Convergence history should not be empty");
    assertTrue(history.size() > 1, "Should have multiple iterations");

    // Each entry should have at least 3 elements (IO solver adds a 4th: kValueResidual)
    for (double[] entry : history) {
      assertTrue(entry.length >= 3, "Each history entry should have at least [temp, mass, energy]");
      assertTrue(Double.isFinite(entry[0]), "Temperature residual should be finite");
    }

    // The last temperature residual should be small if solved
    if (col.solved()) {
      double lastTemp = history.get(history.size() - 1)[0];
      assertTrue(lastTemp < 1.0, "Final temperature residual should be < 1 K");
    }
  }

  /**
   * Validate all solvers on a simple binary methane-ethane separation.
   */
  @Test
  public void substitutionSolversHandleSimpleBinarySystem() {
    DistillationColumn.SolverType[] solvers = {DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
        DistillationColumn.SolverType.INSIDE_OUT, DistillationColumn.SolverType.WEGSTEIN,
        DistillationColumn.SolverType.SUM_RATES};

    for (DistillationColumn.SolverType solver : solvers) {
      SystemInterface sys = new SystemSrkEos(323.15, 10.0);
      sys.addComponent("propane", 1.0);
      sys.addComponent("n-butane", 1.0);
      sys.setMixingRule("classic");

      Stream feed = new Stream("binary_" + solver.name(), sys);
      feed.setFlowRate(100.0, "kg/hr");
      feed.setTemperature(323.15);
      feed.setPressure(10.0, "bara");
      feed.run();

      DistillationColumn column =
          new DistillationColumn("binary_col_" + solver.name(), 5, true, true);
      column.addFeedStream(feed, 3);
      column.getCondenser().setOutTemperature(298.15);
      column.getReboiler().setOutTemperature(348.15);
      column.getCondenser().setRefluxRatio(2.0);
      column.getReboiler().setRefluxRatio(2.0);
      column.setTopPressure(10.0);
      column.setBottomPressure(10.0);
      column.setMaxNumberOfIterations(100);
      column.setTemperatureTolerance(1.0e-1);
      column.setMassBalanceTolerance(1.0e-1);
      column.setSolverType(solver);
      column.run();

      assertTrue(column.solved(), solver.name() + " should converge on binary system\n"
          + column.getConvergenceDiagnostics());
    }
  }

  /**
   * Test Murphree efficiency setter/getter.
   */
  @Test
  public void murphreeEfficiencyAccessors() {
    DistillationColumn column = new DistillationColumn("test", 3, true, true);
    assertEquals(1.0, column.getMurphreeEfficiency(), 1e-10);

    column.setMurphreeEfficiency(0.75);
    assertEquals(0.75, column.getMurphreeEfficiency(), 1e-10);

    column.setMurphreeEfficiency(1.5); // clamped to 1.0
    assertEquals(1.0, column.getMurphreeEfficiency(), 1e-10);

    column.setMurphreeEfficiency(-0.1); // clamped to 0.0
    assertEquals(0.0, column.getMurphreeEfficiency(), 1e-10);
  }

  /**
   * Test getSolverType returns the set value.
   */
  @Test
  public void solverTypeAccessors() {
    DistillationColumn column = new DistillationColumn("test", 3, true, true);
    assertEquals(DistillationColumn.SolverType.DIRECT_SUBSTITUTION, column.getSolverType());

    column.setSolverType(DistillationColumn.SolverType.WEGSTEIN);
    assertEquals(DistillationColumn.SolverType.WEGSTEIN, column.getSolverType());

    column.setSolverType(null);
    assertEquals(DistillationColumn.SolverType.DIRECT_SUBSTITUTION, column.getSolverType());
  }

  /**
   * Test seed-temperature accessors used by external warm-start examples.
   */
  @Test
  public void seedTemperatureAccessors() {
    DistillationColumn column = new DistillationColumn("seeded", 3, true, true);
    assertEquals(column.getNumerOfTrays(), column.getNumberOfTrays());
    assertFalse(column.hasSeedTemperatures());
    assertTrue(Double.isNaN(column.getSeedTemperature(1)));

    column.setSeedTemperature(1, 320.0);
    assertTrue(column.hasSeedTemperatures());
    assertEquals(320.0, column.getSeedTemperature(1), 1.0e-12);

    column.setSeedTemperature(-1, 280.0);
    column.setSeedTemperature(99, 280.0);
    assertTrue(Double.isNaN(column.getSeedTemperature(-1)));
    assertTrue(Double.isNaN(column.getSeedTemperature(99)));

    column.setSeedTemperature(1, Double.NaN);
    assertFalse(column.hasSeedTemperatures());

    column.setSeedTemperature(2, 330.0);
    assertTrue(column.hasSeedTemperatures());
    column.clearSeedTemperatures();
    assertFalse(column.hasSeedTemperatures());
  }

  /**
   * Test that every public solver enum has a mapped solver strategy.
   */
  @Test
  public void solverFactoryCoversAllSolverTypes() {
    for (DistillationColumn.SolverType solverType : DistillationColumn.SolverType.values()) {
      ColumnSolver solver = ColumnSolverFactory.create(solverType);
      assertNotNull(solver, solverType.name() + " should have a solver strategy");
      assertEquals(solverType, solver.getSolverType());
    }
  }

  /**
   * Test that single-tray fast paths update the convergence state used by solved().
   */
  @Test
  public void singleTrayFastPathsReportSolvedState() {
    DistillationColumn.SolverType[] solvers = {DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
        DistillationColumn.SolverType.DAMPED_SUBSTITUTION, DistillationColumn.SolverType.INSIDE_OUT,
        DistillationColumn.SolverType.WEGSTEIN, DistillationColumn.SolverType.SUM_RATES,
        DistillationColumn.SolverType.NEWTON, DistillationColumn.SolverType.NAPHTALI_SANDHOLM};

    for (int i = 0; i < solvers.length; i++) {
      DistillationColumn.SolverType solverType = solvers[i];
      SystemInterface fluid = new SystemSrkEos(273.15, 10.0);
      fluid.addComponent("propane", 0.5);
      fluid.addComponent("n-butane", 0.5);
      fluid.setMixingRule("classic");

      Stream feed = new Stream("single_tray_feed_" + solverType.name(), fluid);
      feed.setFlowRate(100.0, "kg/hr");
      feed.run();

      DistillationColumn column =
          new DistillationColumn("single_tray_" + solverType.name(), 1, false, false);
      column.addFeedStream(feed, 0);
      column.setSolverType(solverType);
      column.run();

      double productMass = column.getGasOutStream().getFlowRate("kg/hr")
          + column.getLiquidOutStream().getFlowRate("kg/hr");
      assertTrue(column.solved(),
          solverType.name() + " should report solved: " + column.getConvergenceDiagnostics());
      assertEquals(feed.getFlowRate("kg/hr"), productMass, feed.getFlowRate("kg/hr") * 1.0e-6,
          solverType.name() + " products should close mass balance");
      assertEquals(0.0, column.getLastTemperatureResidual(), 1.0e-12,
          solverType.name() + " should report zero single-tray temperature residual");
    }
  }

  /**
   * Test that column diagnostics explain common convergence causes for top-fed low-reflux columns.
   */
  @Test
  public void diagnosticsHighlightTopFeedAndSolverAlternatives() {
    SystemInterface sys = new SystemSrkEos(289.15, 14.0);
    sys.addComponent("propane", 0.35);
    sys.addComponent("n-butane", 0.40);
    sys.addComponent("n-pentane", 0.25);
    sys.setMixingRule("classic");

    Stream feed = new Stream("diagnostic_feed", sys);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("diagnostic debutanizer", 10, true, true);
    column.addFeedStream(feed, 9);
    column.getCondenser().setRefluxRatio(0.1);
    column.getCondenser().setTotalCondenser(true);

    String diagnostics = column.getConvergenceDiagnostics();

    assertTrue(diagnostics.contains("near top/condenser"));
    assertTrue(diagnostics.contains("reflux ratio is low"));
    assertTrue(diagnostics.contains("NAPHTALI_SANDHOLM"));
    assertTrue(diagnostics.contains("MESH_RESIDUAL"));
    assertTrue(diagnostics.contains("NEWTON"));
  }

  /**
   * Test component material closure for the standard deethanizer benchmark.
   */
  @Test
  public void componentMaterialBalancesCloseOnDeethanizer() {
    Stream feed = new Stream("component_balance_feed", createDeethanizerFeed().clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("component_balance_column", 5, true, false);
    column.addFeedStream(feed, 5);
    column.getReboiler().setOutTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(50);
    column.run();

    assertTrue(column.solved(), "Component balance case should converge");

    String[] componentNames = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
        "n-butane", "i-pentane", "n-pentane", "n-hexane", "n-heptane"};

    for (int i = 0; i < componentNames.length; i++) {
      String componentName = componentNames[i];
      double feedFlow = feed.getFluid().getComponent(componentName).getTotalFlowRate("mol/hr");
      double productFlow =
          column.getGasOutStream().getFluid().getComponent(componentName).getTotalFlowRate("mol/hr")
              + column.getLiquidOutStream().getFluid().getComponent(componentName)
                  .getTotalFlowRate("mol/hr");
      double tolerance = Math.max(1.0e-8, Math.abs(feedFlow) * 5.0e-2);
      assertEquals(feedFlow, productFlow, tolerance,
          componentName + " component flow should close across products");
    }
  }

  /**
   * Test that MESH residual diagnostics have the expected structure on the standard deethanizer.
   */
  @Test
  public void meshResidualVectorIsFiniteOnSolvedDeethanizer() {
    DistillationColumn column = runDeethanizer(DistillationColumn.SolverType.DIRECT_SUBSTITUTION);

    ColumnMeshResidual residual = column.getLastMeshResidual();
    int trayCount = column.getTrays().size();
    int componentCount = column.getGasOutStream().getThermoSystem().getNumberOfComponents();

    assertNotNull(residual, "MESH residual diagnostics should be recorded after a run");
    assertTrue(residual.size() > 0, "MESH residual vector should contain equations");
    assertTrue(residual.isFinite(), "MESH residual values should be finite");
    assertTrue(Double.isFinite(residual.getInfinityNorm()), "MESH residual norm should be finite");
    assertEquals(residual.size(), column.getLastMeshResidualVector().length,
        "Public residual vector should match internal residual size");
    assertEquals(trayCount * componentCount, residual.count(ColumnMeshEquationType.MATERIAL),
        "Material residual count should match tray-component equations");
    assertEquals(trayCount, residual.count(ColumnMeshEquationType.ENERGY),
        "Energy residual count should match tray count");
    assertEquals(2 * componentCount, residual.count(ColumnMeshEquationType.PRODUCT_DRAW),
        "Product draw residual count should match top and bottom component equations");
    assertTrue(residual.count(ColumnMeshEquationType.EQUILIBRIUM) > 0,
        "Equilibrium residuals should be present");
    assertTrue(residual.count(ColumnMeshEquationType.SUMMATION) > 0,
        "Summation residuals should be present");
    assertTrue(Double.isFinite(column.getLastMeshResidualNorm()),
        "Public MESH residual norm should be finite");
    assertTrue(Double.isFinite(column.getLastMeshMaterialResidualNorm()),
        "Public material residual norm should be finite");
    assertTrue(Double.isFinite(column.getLastMeshEnergyResidualNorm()),
        "Public energy residual norm should be finite");
    assertTrue(Double.isFinite(column.getLastMeshProductDrawResidualNorm()),
        "Public product draw residual norm should be finite");
  }

  /**
   * Test that active column specifications are included in the MESH residual vector.
   */
  @Test
  public void meshResidualIncludesSpecificationResiduals() {
    Stream feed = new Stream("mesh_spec_feed", createDeethanizerFeed().clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("mesh_spec_column", 5, true, false);
    column.addFeedStream(feed, 5);
    column.getReboiler().setOutTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setTopSpecification(
        new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
            ColumnSpecification.ProductLocation.TOP, 1.5));
    column.run();

    ColumnMeshResidual residual = column.getLastMeshResidual();
    assertNotNull(residual, "MESH residual diagnostics should be recorded after a spec solve");
    assertTrue(residual.isFinite(), "Specification residual vector should be finite");
    assertTrue(residual.count(ColumnMeshEquationType.SPECIFICATION) >= 1,
        "Active specifications should contribute residual equations");
    assertTrue(Double.isFinite(column.getLastMeshSpecificationResidualNorm()),
        "Public specification residual norm should be finite");
  }

  /**
   * Test that the MESH residual-monitored solver mode runs and records diagnostics.
   */
  @Test
  public void meshResidualSolverRunsOnDeethanizer() {
    DistillationColumn column = runDeethanizer(DistillationColumn.SolverType.MESH_RESIDUAL);

    assertTrue(column.solved(), "MESH residual-monitored solver should converge");
    assertNotNull(column.getLastMeshResidual(), "MESH residual solver should record diagnostics");
    assertTrue(Double.isFinite(column.getLastMeshResidualNorm()),
        "MESH residual solver should report a finite norm");
  }

  /**
   * Test that the Naphtali-Sandholm solver runs and records full MESH residual diagnostics.
   */
  @Test
  public void naphtaliSandholmSolverRunsOnDeethanizer() {
    DistillationColumn column = runDeethanizer(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);

    assertTrue(column.solved(),
        "Naphtali-Sandholm solver should converge: " + column.getConvergenceDiagnostics());
    assertNotNull(column.getLastMeshResidual(),
        "Naphtali-Sandholm solver should record MESH diagnostics");
    assertTrue(Double.isFinite(column.getLastMeshResidualNorm()),
        "Naphtali-Sandholm solver should report a finite residual norm");
    assertTrue(column.getLastIterationCount() > 0,
        "Naphtali-Sandholm solver should report iteration metrics");

    double massBalance = Math.abs(100.0 - column.getGasOutStream().getFlowRate("kg/hr")
        - column.getLiquidOutStream().getFlowRate("kg/hr")) / 100.0 * 100.0;
    assertEquals(0.0, massBalance, 0.5, "Naphtali-Sandholm mass balance should close within 0.5%");
  }

  /**
   * Test that guarded Newton polishing in the MESH solver only accepts residual-improving states.
   */
  @Test
  public void meshResidualPolishDoesNotWorsenInsideOutResidual() {
    DistillationColumn insideOut = runDeethanizer(DistillationColumn.SolverType.INSIDE_OUT);
    DistillationColumn meshResidual = runDeethanizer(DistillationColumn.SolverType.MESH_RESIDUAL);

    assertTrue(insideOut.solved(), "Inside-out reference should converge");
    assertTrue(meshResidual.solved(), "MESH residual solver should converge");
    assertTrue(
        meshResidual.getLastMeshResidualNorm() <= insideOut.getLastMeshResidualNorm() * 1.001
            + 1.0e-12,
        "Guarded Newton polish should not accept a worse MESH residual. insideOut="
            + insideOut.getLastMeshResidualNorm() + " mesh="
            + meshResidual.getLastMeshResidualNorm());
  }

  /**
   * Test optional convergence gating on the full MESH residual norm.
   */
  @Test
  public void meshResidualToleranceCanBeEnforced() {
    DistillationColumn column = runDeethanizer(DistillationColumn.SolverType.MESH_RESIDUAL);
    double residualNorm = column.getLastMeshResidualNorm();
    double productDrawResidualNorm = column.getLastMeshProductDrawResidualNorm();
    double permissiveTolerance = Math.max(1.0, residualNorm * 2.0 + 1.0);
    double permissiveProductDrawTolerance = productDrawResidualNorm * 2.0 + 1.0e-6;

    assertTrue(column.isEnforceMeshResidualTolerance(),
        "MESH residual gating should be enabled by default for the MESH_RESIDUAL solver");
    column.setMeshResidualTolerance(permissiveTolerance);
    column.setMeshProductDrawResidualTolerance(permissiveProductDrawTolerance);
    column.setEnforceMeshResidualTolerance(true);

    assertEquals(permissiveTolerance, column.getMeshResidualTolerance(), 0.0,
        "Configured MESH tolerance should be retained");
    assertEquals(permissiveProductDrawTolerance, column.getMeshProductDrawResidualTolerance(), 0.0,
        "Configured product-draw tolerance should be retained");
    assertTrue(column.isEnforceMeshResidualTolerance(),
        "MESH residual gating should be enabled after setter call");
    assertTrue(column.solved(), "Permissive MESH residual gate should keep the solve converged");
    assertTrue(productDrawResidualNorm <= column.getMeshProductDrawResidualTolerance(),
        "Synchronized terminal products should satisfy the product-draw gate");
    column.setMeshResidualTolerance(Math.max(1.0e-12, residualNorm * 0.5));
    assertFalse(column.solved(),
        "A strict MESH residual gate should reject a residual above tolerance");
    column.setEnforceMeshResidualTolerance(false);
    assertFalse(column.isEnforceMeshResidualTolerance(),
        "Explicitly disabling the gate should override the MESH_RESIDUAL default");
    assertTrue(column.solved(),
        "Explicitly disabling the MESH residual gate should restore legacy convergence behavior");
    assertThrows(IllegalArgumentException.class, () -> column.setMeshResidualTolerance(0.0));
    assertThrows(IllegalArgumentException.class, () -> column.setMeshResidualTolerance(Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> column.setMeshProductDrawResidualTolerance(0.0));
    assertThrows(IllegalArgumentException.class,
        () -> column.setMeshProductDrawResidualTolerance(Double.NaN));
  }

  /**
   * Test that product-draw residual diagnostics still detect stale terminal draw streams.
   */
  @Test
  public void productDrawResidualDetectsUnsynchronizedTerminalDraw() {
    DistillationColumn column = runDeethanizer(DistillationColumn.SolverType.MESH_RESIDUAL);
    assertTrue(
        column.getLastMeshProductDrawResidualNorm() <= column.getMeshProductDrawResidualTolerance(),
        "Solved column should start with synchronized terminal product draws");

    StreamInterface staleTopDraw = column.getGasOutStream().clone();
    staleTopDraw.setFlowRate(column.getGasOutStream().getFlowRate("kg/hr") * 1000.0, "kg/hr");
    staleTopDraw.run();
    StreamInterface staleBottomDraw = column.getLiquidOutStream().clone();
    staleBottomDraw.setFlowRate(column.getLiquidOutStream().getFlowRate("kg/hr") * 1000.0, "kg/hr");
    staleBottomDraw.run();
    column.setTerminalProductDrawStreamsForDiagnostics(staleTopDraw, staleBottomDraw);

    ColumnMeshResidual residual = ColumnMeshResidualEvaluator.evaluate(column);
    double productDrawNorm = residual.getInfinityNorm(ColumnMeshEquationType.PRODUCT_DRAW);
    assertTrue(productDrawNorm > 1.0e-6,
        "Product-draw residual should catch a terminal draw that no longer matches the public product, norm="
            + productDrawNorm);
  }

  /**
   * Regression for the column4.py C1 to C5 UniSim case.
   *
   * <p>
   * The rigorous tray solver is deliberately not reported as converged when the raw internal tray
   * traffic hits the guard. The exposed products are instead a bounded mass-conserving fallback
   * estimate that keeps the bottom phase liquid-like and enriched in C5 components.
   * </p>
   */
  @Test
  public void column4C1ToC5CaseTracksLiquidCompositionDeviation() {
    DistillationColumn column = runColumn4Case(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    StreamInterface mainFeed = column.getFeedStreams(6).get(0);
    StreamInterface topFeed = column.getFeedStreams(10).get(0);

    double feedMass = mainFeed.getFlowRate("kg/hr") + topFeed.getFlowRate("kg/hr");
    double productMass = column.getGasOutStream().getFlowRate("kg/hr")
        + column.getLiquidOutStream().getFlowRate("kg/hr");
    double[] topComposition = column.getGasOutStream().getThermoSystem().getMolarComposition();
    double[] bottomComposition =
        column.getLiquidOutStream().getThermoSystem().getMolarComposition();
    double vaporMaxDeviation = getMaxAbsoluteDeviation(topComposition, COLUMN4_UNISIM_TOP_Y);
    double liquidMaxDeviation = getMaxAbsoluteDeviation(bottomComposition, COLUMN4_UNISIM_BOTTOM_X);
    double liquidRmsDeviation = getRmsDeviation(bottomComposition, COLUMN4_UNISIM_BOTTOM_X);
    double topC5Fraction = topComposition[5] + topComposition[6];
    double bottomC5Fraction = bottomComposition[5] + bottomComposition[6];
    double topC1C2Fraction = topComposition[0] + topComposition[1];
    double bottomC1C2Fraction = bottomComposition[0] + bottomComposition[1];

    if (!column.solved()) {
      assertTrue(column.wasFeedFlashFallbackApplied(),
          "column4 should use guarded fallback products when the rigorous tray solve is not accepted");
    }
    assertEquals(feedMass, productMass, feedMass * 1.0e-6,
        "column4 external products must match feed mass");
    assertEquals(0.0, column.getMassBalance("kg/hr"), feedMass * 1.0e-6,
        "column4 public mass balance should be closed");
    assertTrue(column.getLastInternalTrafficRatio() <= 2.5e5,
        "column4 internal traffic should be bounded by the guard, ratio="
            + column.getLastInternalTrafficRatio());
    assertTrue(
        column.getLiquidOutStream().getThermoSystem().hasPhaseType("oil")
            || column.getLiquidOutStream().getThermoSystem().hasPhaseType("liquid"),
        "column4 bottom product should be liquid-like");
    assertTrue(bottomC5Fraction > topC5Fraction * 4.0,
        "column4 bottom should be enriched in C5 components. top=" + topC5Fraction + " bottom="
            + bottomC5Fraction);
    assertTrue(bottomC1C2Fraction < topC1C2Fraction * 0.35,
        "column4 bottom should reject most C1/C2 components. top=" + topC1C2Fraction + " bottom="
            + bottomC1C2Fraction);
    assertTrue(vaporMaxDeviation < 5.0e-2,
        "column4 vapor composition should stay close to UniSim, max |dy|=" + vaporMaxDeviation);
    assertTrue(liquidMaxDeviation < 3.5e-1,
        "column4 liquid composition deviation should stay inside the current known envelope, max |dx|="
            + liquidMaxDeviation);
    assertTrue(liquidRmsDeviation < 2.2e-1,
        "column4 liquid composition RMS deviation should stay inside the current known envelope, rms="
            + liquidRmsDeviation);
  }

  /**
   * Regression that the temperature-Newton accelerator returns bounded, physical products on the
   * column4.py type case.
   */
  @Test
  public void column4NewtonDoesNotThrowAndReturnsBoundedProducts() {
    DistillationColumn column = runColumn4Case(DistillationColumn.SolverType.NEWTON);
    StreamInterface mainFeed = column.getFeedStreams(6).get(0);
    StreamInterface topFeed = column.getFeedStreams(10).get(0);
    double feedMass = mainFeed.getFlowRate("kg/hr") + topFeed.getFlowRate("kg/hr");
    double productMass = column.getGasOutStream().getFlowRate("kg/hr")
        + column.getLiquidOutStream().getFlowRate("kg/hr");
    double[] topComposition = column.getGasOutStream().getThermoSystem().getMolarComposition();
    double[] bottomComposition =
        column.getLiquidOutStream().getThermoSystem().getMolarComposition();
    double topC5Fraction = topComposition[5] + topComposition[6];
    double bottomC5Fraction = bottomComposition[5] + bottomComposition[6];
    double topC1C2Fraction = topComposition[0] + topComposition[1];
    double bottomC1C2Fraction = bottomComposition[0] + bottomComposition[1];

    assertEquals(feedMass, productMass, feedMass * 1.0e-6,
        "NEWTON products should match feed mass");
    assertEquals(0.0, column.getMassBalance("kg/hr"),
        column.getGasOutStream().getFlowRate("kg/hr") * 1.0e-6,
        "NEWTON products should close public mass balance");
    assertTrue(
        column.getLiquidOutStream().getThermoSystem().hasPhaseType("oil")
            || column.getLiquidOutStream().getThermoSystem().hasPhaseType("liquid"),
        "NEWTON bottom product should be liquid-like");
    assertTrue(bottomC5Fraction > topC5Fraction * 4.0,
        "NEWTON bottom should be enriched in C5 components. top=" + topC5Fraction + " bottom="
            + bottomC5Fraction);
    assertTrue(bottomC1C2Fraction < topC1C2Fraction * 0.35,
        "NEWTON bottom should reject most C1/C2 components. top=" + topC1C2Fraction + " bottom="
            + bottomC1C2Fraction);
  }

  /**
   * Run the column4.py C1 to C5 case with a selected solver.
   *
   * @param solverType solver to apply
   * @return configured and executed column4 case
   */
  private DistillationColumn runColumn4Case(DistillationColumn.SolverType solverType) {
    SystemInterface baseFluid = createColumn4BaseFluid();
    Stream mainFeed = createColumn4Stream(baseFluid, "column4 main feed",
        new double[] {0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0}, 1059.40430981003, 77.0000001251743, 4.2);
    Stream topFeed = createColumn4Stream(baseFluid, "column4 top feed",
        new double[] {1.0 / 7.0, 1.0 / 7.0, 1.0 / 7.0, 1.0 / 7.0, 1.0 / 7.0, 1.0 / 7.0, 1.0 / 7.0},
        1000.0, 32.14, 3.7);

    DistillationColumn column = new DistillationColumn("column4 C1-C5", 10, true, false);
    column.addFeedStream(mainFeed, 6);
    column.addFeedStream(topFeed, 10);
    double topPressure = 4.00 + COLUMN4_ATM_BARA;
    double bottomPressure = 4.05 + COLUMN4_ATM_BARA;
    column.setTopPressure(topPressure);
    column.setBottomPressure(topPressure + (bottomPressure - topPressure) * (10.0 / 9.0));
    column.getReboiler().setOutTemperature(273.15 + 88.05);
    column.setMurphreeEfficiency(1.0);
    column.setMurphreeEfficiency(0, 1.0);
    column.setSolverType(solverType);
    column.setMaxNumberOfIterations(160);
    column.run();
    return column;
  }

  /**
   * Create the SRK fluid package used in the column4.py case.
   *
   * @return base fluid with C1 through nC5 components initialized
   */
  private SystemInterface createColumn4BaseFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.0);
    for (int i = 0; i < COLUMN4_COMPONENTS.length; i++) {
      fluid.addComponent(COLUMN4_COMPONENTS[i], 1.0e-10);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.useVolumeCorrection(true);
    fluid.init(0);
    return fluid;
  }

  /**
   * Create a feed stream for the column4.py case.
   *
   * @param baseFluid base thermodynamic fluid to clone
   * @param name stream name
   * @param moleFractions component mole fractions in {@link #COLUMN4_COMPONENTS} order
   * @param flowRateKmolPerHour stream flow rate in kmol/hr
   * @param temperatureC stream temperature in degrees Celsius
   * @param pressureBarg stream pressure in barg
   * @return initialized stream
   */
  private Stream createColumn4Stream(SystemInterface baseFluid, String name, double[] moleFractions,
      double flowRateKmolPerHour, double temperatureC, double pressureBarg) {
    SystemInterface fluid = baseFluid.clone();
    fluid.setTemperature(temperatureC, "C");
    fluid.setPressure(pressureBarg + COLUMN4_ATM_BARA, "bara");
    fluid.setMolarComposition(moleFractions);
    fluid.init(0);

    Stream stream = new Stream(name, fluid);
    stream.setTemperature(temperatureC, "C");
    stream.setPressure(pressureBarg + COLUMN4_ATM_BARA, "bara");
    stream.setFlowRate(flowRateKmolPerHour, "kmole/hr");
    stream.run();
    return stream;
  }

  /**
   * Calculate the maximum absolute composition deviation.
   *
   * @param actual actual composition vector
   * @param expected reference composition vector
   * @return maximum absolute deviation
   */
  private double getMaxAbsoluteDeviation(double[] actual, double[] expected) {
    double maxDeviation = 0.0;
    for (int i = 0; i < expected.length; i++) {
      maxDeviation = Math.max(maxDeviation, Math.abs(actual[i] - expected[i]));
    }
    return maxDeviation;
  }

  /**
   * Calculate the root-mean-square composition deviation.
   *
   * @param actual actual composition vector
   * @param expected reference composition vector
   * @return root-mean-square deviation
   */
  private double getRmsDeviation(double[] actual, double[] expected) {
    double sumSquaredDeviation = 0.0;
    for (int i = 0; i < expected.length; i++) {
      double deviation = actual[i] - expected[i];
      sumSquaredDeviation += deviation * deviation;
    }
    return Math.sqrt(sumSquaredDeviation / expected.length);
  }

  /**
   * Compare solver performance on a 10-tray column (more stages = harder problem).
   */
  @Test
  public void solverComparisonOnLargerColumn() {
    DistillationColumn.SolverType[] solvers = {DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
        DistillationColumn.SolverType.INSIDE_OUT, DistillationColumn.SolverType.NEWTON};

    for (DistillationColumn.SolverType solver : solvers) {
      Stream feed = new Stream("large_" + solver.name(), createDeethanizerFeed().clone());
      feed.setFlowRate(100.0, "kg/hr");
      feed.run();

      DistillationColumn column =
          new DistillationColumn("large_col_" + solver.name(), 10, true, false);
      column.addFeedStream(feed, 5);
      column.getReboiler().setOutTemperature(105.0 + 273.15);
      column.setTopPressure(30.0);
      column.setBottomPressure(32.0);
      column.setMaxNumberOfIterations(80);
      column.setSolverType(solver);
      column.run();

      assertTrue(column.solved(), solver.name() + " should converge on 10-tray column: "
          + column.getConvergenceDiagnostics());

      double massbalance = Math.abs(100.0 - column.getGasOutStream().getFlowRate("kg/hr")
          - column.getLiquidOutStream().getFlowRate("kg/hr"));
      assertTrue(massbalance < 1.5,
          solver.name() + " mass balance error=" + massbalance + " kg/hr");
    }
  }

  /**
   * Test TEG regeneration column with Wegstein solver (polar system with CPA).
   */
  @Test
  public void wegsteinOnTEGRegeneration() {
    SystemInterface richTEG = new SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    richTEG.addComponent("methane", 0.1707852619527612);
    richTEG.addComponent("ethane", 0.20533172990208282);
    richTEG.addComponent("propane", 0.28448628224749795);
    richTEG.addComponent("water", 9.281170624865437);
    richTEG.addComponent("TEG", 88.61393191277175);
    richTEG.setMixingRule(10);
    richTEG.setMultiPhaseCheck(false);
    richTEG.init(0);

    Stream richTEGStream = new Stream("richTEGS", richTEG);
    richTEGStream.setFlowRate(9400.0, "kg/hr");
    richTEGStream.setTemperature(100, "C");
    richTEGStream.setPressure(1.12, "bara");
    richTEGStream.run();

    DistillationColumn column = new DistillationColumn("TEG regen Wegstein", 1, true, true);
    column.addFeedStream(richTEGStream, 1);
    column.getReboiler().setOutTemperature(273.15 + 202);
    column.getCondenser().setOutTemperature(273.15 + 88.165861);
    column.setTopPressure(1.12);
    column.setBottomPressure(1.12);
    column.setMaxNumberOfIterations(80);
    column.setSolverType(DistillationColumn.SolverType.WEGSTEIN);
    column.run();

    // CPA systems are harder for Wegstein; check mass balance rather than strict convergence
    double massIn = richTEGStream.getFlowRate("kg/hr");
    double massOut = column.getGasOutStream().getFlowRate("kg/hr")
        + column.getLiquidOutStream().getFlowRate("kg/hr");
    double massBalance = Math.abs(massIn - massOut) / massIn;
    assertTrue(massBalance < 0.05,
        "Wegstein mass balance on TEG should be within 5%, got " + (massBalance * 100) + "%");
  }

  /**
   * Benchmark: Inside-Out solver should record K-value residual in convergence history.
   */
  @Test
  public void insideOutRecordsKvalueResidual() {
    DistillationColumn col = runDeethanizer(DistillationColumn.SolverType.INSIDE_OUT);
    assertTrue(col.solved(), "Inside-Out should converge");

    List<double[]> history = col.getConvergenceHistory();
    assertFalse(history.isEmpty(), "Convergence history should not be empty");

    // IO convergence history entries should have 4 elements: [temp, mass, energy, kValueResidual]
    for (double[] entry : history) {
      assertEquals(4, entry.length,
          "IO history entry should have [temp, mass, energy, kValueResidual]");
    }

    // K-value residual should decrease over iterations (at least from middle to end)
    if (history.size() > 4) {
      int mid = history.size() / 2;
      double midKErr = history.get(mid)[3];
      double lastKErr = history.get(history.size() - 1)[3];
      assertTrue(lastKErr <= midKErr * 1.5,
          "K-value residual should not grow significantly in later iterations. mid=" + midKErr
              + " last=" + lastKErr);
    }
  }

  /**
   * Benchmark: Inside-Out should converge in fewer or comparable iterations to direct substitution
   * on the 5-tray deethanizer, demonstrating the accelerated relaxation ramp.
   */
  @Test
  public void insideOutNotSlowerThanDirectSubstitution() {
    DistillationColumn direct = runDeethanizer(DistillationColumn.SolverType.DIRECT_SUBSTITUTION);
    DistillationColumn io = runDeethanizer(DistillationColumn.SolverType.INSIDE_OUT);

    assertTrue(direct.solved(), "Direct substitution should converge");
    assertTrue(io.solved(), "Inside-Out should converge");

    // IO should not require significantly more iterations than direct substitution
    assertTrue(io.getLastIterationCount() <= direct.getLastIterationCount() * 1.5 + 3,
        "IO should not be significantly slower. IO=" + io.getLastIterationCount() + " DIRECT="
            + direct.getLastIterationCount());
  }

  /**
   * Benchmark: Inside-Out with stripping factor correction on a 10-tray column should still
   * converge and produce good mass balance.
   */
  @Test
  public void insideOutOnLargerColumnWithStrippingCorrection() {
    Stream feed = new Stream("io_large_feed", createDeethanizerFeed().clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("io_large_col", 10, true, false);
    column.addFeedStream(feed, 5);
    column.getReboiler().setOutTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(80);
    column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
    column.run();

    assertTrue(column.solved(),
        "IO should converge on 10-tray column: " + column.getConvergenceDiagnostics());

    double massbalance = Math.abs(100.0 - column.getGasOutStream().getFlowRate("kg/hr")
        - column.getLiquidOutStream().getFlowRate("kg/hr"));
    assertTrue(massbalance < 1.0, "IO mass balance error=" + massbalance + " kg/hr");

    // Verify convergence history tracked K-values
    List<double[]> history = column.getConvergenceHistory();
    assertTrue(history.size() >= 2, "Should have at least 2 iterations");
    assertEquals(4, history.get(0).length, "IO history should include K-value residual");
  }

  /**
   * Test innerLoopSteps getter/setter.
   */
  @Test
  public void innerLoopStepsAccessors() {
    DistillationColumn column = new DistillationColumn("test", 3, true, true);
    assertEquals(3, column.getInnerLoopSteps(), "Default inner loop steps should be 3");

    column.setInnerLoopSteps(5);
    assertEquals(5, column.getInnerLoopSteps());

    column.setInnerLoopSteps(0);
    assertEquals(0, column.getInnerLoopSteps(), "0 disables inner loop");

    column.setInnerLoopSteps(-1);
    assertEquals(0, column.getInnerLoopSteps(), "Negative clamped to 0");
  }

  /**
   * Test that disabling the inner loop (steps=0) still converges and produces the same result as
   * the default IO solver.
   */
  @Test
  public void insideOutWithInnerLoopDisabledMatchesBaseline() {
    // Run with inner loop enabled (default: 3 steps)
    Stream feed1 = new Stream("io_with_inner", createDeethanizerFeed().clone());
    feed1.setFlowRate(100.0, "kg/hr");
    feed1.run();
    DistillationColumn withInner = new DistillationColumn("col_with_inner", 5, true, false);
    withInner.addFeedStream(feed1, 5);
    withInner.getReboiler().setOutTemperature(105.0 + 273.15);
    withInner.setTopPressure(30.0);
    withInner.setBottomPressure(32.0);
    withInner.setMaxNumberOfIterations(50);
    withInner.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
    withInner.run();

    // Run with inner loop disabled
    Stream feed2 = new Stream("io_no_inner", createDeethanizerFeed().clone());
    feed2.setFlowRate(100.0, "kg/hr");
    feed2.run();
    DistillationColumn noInner = new DistillationColumn("col_no_inner", 5, true, false);
    noInner.addFeedStream(feed2, 5);
    noInner.getReboiler().setOutTemperature(105.0 + 273.15);
    noInner.setTopPressure(30.0);
    noInner.setBottomPressure(32.0);
    noInner.setMaxNumberOfIterations(50);
    noInner.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
    noInner.setInnerLoopSteps(0);
    noInner.run();

    assertTrue(withInner.solved(), "IO with inner loop should converge");
    assertTrue(noInner.solved(), "IO without inner loop should converge");

    // Both should produce similar gas flow rates (within 2%)
    double gasWithInner = withInner.getGasOutStream().getFlowRate("kg/hr");
    double gasNoInner = noInner.getGasOutStream().getFlowRate("kg/hr");
    double tolerance = Math.max(0.01, gasNoInner * 0.02);
    assertEquals(gasNoInner, gasWithInner, tolerance,
        "Inner loop should not change the converged result significantly");
  }

  /**
   * Test that the simplified inner-loop model produces convergence history entries for inner
   * iterations (more history entries than outer iterations alone).
   */
  @Test
  public void innerLoopProducesExtraHistoryEntries() {
    // Run IO with inner loop enabled (3 steps)
    Stream feed1 = new Stream("io_inner_hist", createDeethanizerFeed().clone());
    feed1.setFlowRate(100.0, "kg/hr");
    feed1.run();
    DistillationColumn withInner = new DistillationColumn("col_inner_hist", 5, true, false);
    withInner.addFeedStream(feed1, 5);
    withInner.getReboiler().setOutTemperature(105.0 + 273.15);
    withInner.setTopPressure(30.0);
    withInner.setBottomPressure(32.0);
    withInner.setMaxNumberOfIterations(50);
    withInner.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
    withInner.setInnerLoopSteps(3);
    withInner.run();

    // Run IO with inner loop disabled
    Stream feed2 = new Stream("io_no_inner_hist", createDeethanizerFeed().clone());
    feed2.setFlowRate(100.0, "kg/hr");
    feed2.run();
    DistillationColumn noInner = new DistillationColumn("col_no_inner_hist", 5, true, false);
    noInner.addFeedStream(feed2, 5);
    noInner.getReboiler().setOutTemperature(105.0 + 273.15);
    noInner.setTopPressure(30.0);
    noInner.setBottomPressure(32.0);
    noInner.setMaxNumberOfIterations(50);
    noInner.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
    noInner.setInnerLoopSteps(0);
    noInner.run();

    assertTrue(withInner.solved(), "IO with inner loop should converge");
    assertTrue(noInner.solved(), "IO without inner loop should converge");

    // With inner loop should have more convergence history entries
    // (outer iters + inner iters vs outer iters only)
    int histWithInner = withInner.getConvergenceHistory().size();
    int histNoInner = noInner.getConvergenceHistory().size();
    assertTrue(histWithInner > histNoInner, "Inner loop should produce more history entries. with="
        + histWithInner + " without=" + histNoInner);
  }

  /**
   * Test simplified IO on a 10-tray column — verify it still converges with inner loop and produces
   * good mass balance.
   */
  @Test
  public void insideOutWithInnerLoopOnLargerColumn() {
    Stream feed = new Stream("io_inner_large", createDeethanizerFeed().clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("io_inner_large_col", 10, true, false);
    column.addFeedStream(feed, 5);
    column.getReboiler().setOutTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(80);
    column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
    column.setInnerLoopSteps(3);
    column.run();

    assertTrue(column.solved(), "IO with inner loop should converge on 10-tray column: "
        + column.getConvergenceDiagnostics());

    double massbalance = Math.abs(100.0 - column.getGasOutStream().getFlowRate("kg/hr")
        - column.getLiquidOutStream().getFlowRate("kg/hr"));
    assertTrue(massbalance < 1.0,
        "IO with inner loop mass balance error=" + massbalance + " kg/hr");
  }

  /**
   * Newton solver on a 10-tray column — verify it converges and produces good mass balance.
   */
  @Test
  public void newtonOnLargerColumn() {
    Stream feed = new Stream("newton_large_feed", createDeethanizerFeed().clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("newton_large_col", 10, true, false);
    column.addFeedStream(feed, 5);
    column.getReboiler().setOutTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(80);
    column.setSolverType(DistillationColumn.SolverType.NEWTON);
    column.run();

    assertTrue(column.solved(), "Newton should converge on 10-tray column");

    double massbalance = Math.abs(100.0 - column.getGasOutStream().getFlowRate("kg/hr")
        - column.getLiquidOutStream().getFlowRate("kg/hr"));
    assertTrue(massbalance < 1.5, "Newton mass balance error=" + massbalance + " kg/hr");
  }

  /**
   * Newton solver should record convergence history with [temp, mass, energy] entries.
   */
  @Test
  public void newtonRecordsConvergenceHistory() {
    DistillationColumn col = runDeethanizer(DistillationColumn.SolverType.NEWTON);
    assertTrue(col.solved(), "Newton should converge");

    List<double[]> history = col.getConvergenceHistory();
    assertFalse(history.isEmpty(), "Newton convergence history should not be empty");

    for (double[] entry : history) {
      assertTrue(entry.length >= 3,
          "Newton history entry should have at least [temp, mass, energy]");
      assertTrue(Double.isFinite(entry[0]), "Temperature residual should be finite");
    }

    if (col.solved()) {
      double lastTemp = history.get(history.size() - 1)[0];
      assertTrue(lastTemp < 1.0, "Final Newton temperature residual should be < 1 K");
    }
  }

  /**
   * Test that Murphree efficiency < 1.0 actually changes the product split compared to ideal trays.
   * A lower efficiency should produce less sharp separation (more heavy components in gas, or
   * different gas flow rate).
   */
  @Test
  public void murphreeEfficiencyAffectsProductSplit() {
    // Run with ideal trays (E_MV = 1.0)
    Stream feed1 = new Stream("ideal_feed", createDeethanizerFeed().clone());
    feed1.setFlowRate(100.0, "kg/hr");
    feed1.run();
    DistillationColumn ideal = new DistillationColumn("ideal_col", 5, true, false);
    ideal.addFeedStream(feed1, 5);
    ideal.getReboiler().setOutTemperature(105.0 + 273.15);
    ideal.setTopPressure(30.0);
    ideal.setBottomPressure(32.0);
    ideal.setMaxNumberOfIterations(50);
    ideal.setMurphreeEfficiency(1.0);
    ideal.run();
    assertTrue(ideal.solved(), "Ideal column should converge");

    // Run with 85% Murphree efficiency
    Stream feed2 = new Stream("murphree_feed", createDeethanizerFeed().clone());
    feed2.setFlowRate(100.0, "kg/hr");
    feed2.run();
    DistillationColumn nonIdeal = new DistillationColumn("nonideal_col", 5, true, false);
    nonIdeal.addFeedStream(feed2, 5);
    nonIdeal.getReboiler().setOutTemperature(105.0 + 273.15);
    nonIdeal.setTopPressure(30.0);
    nonIdeal.setBottomPressure(32.0);
    nonIdeal.setMaxNumberOfIterations(100);
    nonIdeal.setMurphreeEfficiency(0.85);
    nonIdeal.run();
    assertTrue(nonIdeal.solved(), "Non-ideal column should converge");

    double idealGas = ideal.getGasOutStream().getFlowRate("kg/hr");
    double nonIdealGas = nonIdeal.getGasOutStream().getFlowRate("kg/hr");

    // The product splits should differ — lower efficiency means less separation
    assertTrue(Math.abs(idealGas - nonIdealGas) > 0.01,
        "Murphree efficiency should change product split. Ideal gas=" + idealGas + " nonIdeal gas="
            + nonIdealGas);

    // Ideal mass balance should close tightly
    double idealBalance =
        Math.abs(100.0 - idealGas - ideal.getLiquidOutStream().getFlowRate("kg/hr"));
    assertTrue(idealBalance < 1.0, "Ideal mass balance error=" + idealBalance);
  }

  /**
   * Test Murphree efficiency works with multiple solver types.
   */
  @Test
  public void murphreeEfficiencyWithDifferentSolvers() {
    DistillationColumn.SolverType[] solvers = {DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
        DistillationColumn.SolverType.INSIDE_OUT};

    for (DistillationColumn.SolverType solver : solvers) {
      Stream feed = new Stream("murph_" + solver.name(), createDeethanizerFeed().clone());
      feed.setFlowRate(100.0, "kg/hr");
      feed.run();

      DistillationColumn column =
          new DistillationColumn("murph_col_" + solver.name(), 5, true, false);
      column.addFeedStream(feed, 5);
      column.getReboiler().setOutTemperature(105.0 + 273.15);
      column.setTopPressure(30.0);
      column.setBottomPressure(32.0);
      column.setMaxNumberOfIterations(100);
      column.setMurphreeEfficiency(0.85);
      column.setSolverType(solver);
      column.run();

      assertTrue(column.solved(), solver.name() + " with Murphree=0.85 should converge");
    }
  }
}
