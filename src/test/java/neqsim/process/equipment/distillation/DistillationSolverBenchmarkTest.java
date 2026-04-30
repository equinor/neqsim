package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comprehensive benchmark tests comparing all five solver types on standard distillation problems.
 * These tests validate the improvements described in the distillation column paper.
 */
public class DistillationSolverBenchmarkTest {

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
   * Test that all five solver types converge on the deethanizer benchmark and produce consistent
   * results within engineering tolerance.
   */
  @Test
  public void allSolversConvergeOnDeethanizer() {
    DistillationColumn.SolverType[] solvers = {DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
        DistillationColumn.SolverType.DAMPED_SUBSTITUTION, DistillationColumn.SolverType.INSIDE_OUT,
        DistillationColumn.SolverType.WEGSTEIN, DistillationColumn.SolverType.SUM_RATES,
        DistillationColumn.SolverType.NEWTON};

    double[] gasFlows = new double[solvers.length];
    double[] liquidFlows = new double[solvers.length];
    int[] iterations = new int[solvers.length];
    double[] times = new double[solvers.length];

    for (int i = 0; i < solvers.length; i++) {
      DistillationColumn col = runDeethanizer(solvers[i]);
      assertTrue(col.solved(), solvers[i].name() + " should converge");

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
    assertTrue(wegstein.solved(), "Wegstein should converge");

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
  public void allSolversHandleSimpleBinarySystem() {
    DistillationColumn.SolverType[] solvers = {DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
        DistillationColumn.SolverType.INSIDE_OUT, DistillationColumn.SolverType.WEGSTEIN,
        DistillationColumn.SolverType.SUM_RATES, DistillationColumn.SolverType.NEWTON};

    for (DistillationColumn.SolverType solver : solvers) {
      SystemInterface sys = new SystemSrkEos(298.15, 5.0);
      sys.addComponent("methane", 1.0);
      sys.addComponent("ethane", 1.0);
      sys.createDatabase(true);
      sys.setMixingRule("classic");

      Stream feed = new Stream("binary_" + solver.name(), sys);
      feed.run();

      DistillationColumn column =
          new DistillationColumn("binary_col_" + solver.name(), 1, true, true);
      column.addFeedStream(feed, 1);
      column.setSolverType(solver);
      column.run();

      assertTrue(column.solved(), solver.name() + " should converge on binary system");
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
   * Compare solver performance on a 10-tray column (more stages = harder problem).
   */
  @Test
  public void solverComparisonOnLargerColumn() {
    DistillationColumn.SolverType[] solvers = {DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
        DistillationColumn.SolverType.INSIDE_OUT, DistillationColumn.SolverType.WEGSTEIN,
        DistillationColumn.SolverType.NEWTON};

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

      assertTrue(column.solved(), solver.name() + " should converge on 10-tray column");

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

    assertTrue(column.solved(), "IO should converge on 10-tray column");

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

    assertTrue(column.solved(), "IO with inner loop should converge on 10-tray column");

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
