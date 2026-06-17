package neqsim.process.equipment.distillation;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Diagnostic that bypasses the {@link ColumnSolverFactory} fallback wrapper and calls each
 * accelerator's inner solve method directly on the 5-tray deethanizer benchmark, then dumps the
 * pre-fallback convergence state. Used to investigate why accelerators silently fall back to damped
 * substitution on small heavy-rich columns.
 */
public class DistillationAcceleratorDiagnosticTest {

  /**
   * Build a deethanizer feed matching the benchmark test's composition.
   *
   * @return configured feed system
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
   * Build a 5-tray deethanizer ready to run with the given solver type.
   *
   * @param solverType solver to configure
   * @return configured column (un-run)
   */
  private DistillationColumn buildDeethanizer(DistillationColumn.SolverType solverType) {
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
    return column;
  }

  /**
   * Run each accelerator's inner solve method directly (bypassing the factory fallback) and print
   * pre-fallback diagnostics. Read the stdout of this test to see which gate (mass residual,
   * external mass balance, internal traffic, bottom phase) triggers the silent fallback.
   */
  @Test
  public void dumpAcceleratorPreFallbackState() {
    DistillationColumn.SolverType[] accelerators =
        {DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
            DistillationColumn.SolverType.DAMPED_SUBSTITUTION,
            DistillationColumn.SolverType.INSIDE_OUT,
            DistillationColumn.SolverType.MATRIX_INSIDE_OUT,
            DistillationColumn.SolverType.WEGSTEIN, DistillationColumn.SolverType.SUM_RATES,
            DistillationColumn.SolverType.NEWTON, DistillationColumn.SolverType.MESH_RESIDUAL,
            DistillationColumn.SolverType.NAPHTALI_SANDHOLM};

    System.out.println();
    System.out.println("=== Accelerator pre-fallback diagnostic (5-tray deethanizer) ===");
    for (DistillationColumn.SolverType type : accelerators) {
      DistillationColumn column = buildDeethanizer(type);
      // Mimic the front-end without going through ColumnSolverFactory: initialize feed-tray
      // products and then call the inner solve method directly.
      UUID id = UUID.randomUUID();
      try {
        callInnerSolver(column, type, id);
      } catch (RuntimeException ex) {
        System.out.println(type.name() + ": EXCEPTION " + ex.getClass().getSimpleName() + ": "
            + ex.getMessage());
        continue;
      }
      System.out.println();
      System.out.println("--- " + type.name() + " ---");
      System.out.println("  solved()                       = " + column.solved());
      System.out.println("  lastSolveStatus                = " + column.getLastSolveStatus());
      System.out.println("  lastSolveStatusReason          = " + column.getLastSolveStatusReason());
      System.out.println("  wasFeedFlashFallbackApplied()  = " + column.wasFeedFlashFallbackApplied());
      System.out.println("  lastIterationCount             = " + column.getLastIterationCount());
      System.out.println("  lastMassResidual               = " + column.getLastMassResidual());
      System.out.println("  lastEnergyResidual             = " + column.getLastEnergyResidual());
      System.out.println("  lastMeshResidualNorm           = " + column.getLastMeshResidualNorm());
      System.out.println("  lastMeshMaterialResidualNorm   = "
          + column.getLastMeshMaterialResidualNorm());
      System.out.println("  lastMeshEquilibriumResidualNorm= "
          + column.getLastMeshEquilibriumResidualNorm());
      System.out.println("  lastMeshSummationResidualNorm  = "
          + column.getLastMeshSummationResidualNorm());
      System.out.println("  lastMeshEnergyResidualNorm     = "
          + column.getLastMeshEnergyResidualNorm());
      System.out.println("  lastMeshProductDrawResidualNorm= "
          + column.getLastMeshProductDrawResidualNorm());
      System.out.println("  lastInternalTrafficRatio       = " + column.getLastInternalTrafficRatio());
      System.out.println("  gasOut flow (kg/hr)            = "
          + column.getGasOutStream().getFlowRate("kg/hr"));
      System.out.println("  liquidOut flow (kg/hr)         = "
          + column.getLiquidOutStream().getFlowRate("kg/hr"));
      // Direct tray reads — what the inner solver actually computed BEFORE fallback overwrite
      try {
        double topTrayGas = column.getTray(column.getNumberOfTrays() - 1).getGasOutStream()
            .getFlowRate("kg/hr");
        double botTrayLiq = column.getTray(0).getLiquidOutStream().getFlowRate("kg/hr");
        System.out.println("  topTray gasOut    (kg/hr)      = " + topTrayGas);
        System.out.println("  botTray liquidOut (kg/hr)      = " + botTrayLiq);
        // Bottom tray phase inventory
        SystemInterface bot = column.getTray(0).getLiquidOutStream().getThermoSystem();
        System.out.println("  botTray hasPhase oil/liq/aq    = " + bot.hasPhaseType("oil") + "/"
            + bot.hasPhaseType("liquid") + "/" + bot.hasPhaseType("aqueous"));
        System.out.println("  botTray numberOfPhases         = " + bot.getNumberOfPhases());
        if (bot.getNumberOfPhases() > 0) {
          System.out.println("  botTray phase0 name            = " + bot.getPhase(0).getPhaseTypeName());
        }
      } catch (RuntimeException re) {
        System.out.println("  tray read failed: " + re.getMessage());
      }
      // Reflection probes for private gates
      probePrivate(column, "internalTrafficSatisfied");
      probePrivate(column, "bottomProductPhaseInvalid");
      probePrivate(column, "getExternalMassBalanceError");
      probePrivate(column, "getEffectiveMassBalanceTolerance");
    }
    System.out.println("=== end diagnostic ===");
    System.out.println();
  }

  /**
   * Dispatch to the inner solve method for the given solver type.
   *
   * @param column column to drive
   * @param type solver type
   * @param id calculation identifier
   */
  private void callInnerSolver(DistillationColumn column, DistillationColumn.SolverType type,
      UUID id) {
    switch (type) {
      case DIRECT_SUBSTITUTION:
        column.solveDirectSubstitution(id);
        return;
      case DAMPED_SUBSTITUTION:
        column.solveDampedSubstitution(id);
        return;
      case INSIDE_OUT:
        column.solveInsideOut(id);
        return;
      case MATRIX_INSIDE_OUT:
        column.solveMatrixInsideOut(id);
        return;
      case WEGSTEIN:
        column.solveWegstein(id);
        return;
      case SUM_RATES:
        column.solveSumRates(id);
        return;
      case NEWTON:
        column.solveNewton(id);
        return;
      case MESH_RESIDUAL:
        column.solveMeshResidual(id);
        return;
      case NAPHTALI_SANDHOLM:
        column.solveNaphtaliSandholm(id);
        return;
      default:
        throw new IllegalArgumentException("Unsupported solver: " + type);
    }
  }

  /**
   * Reflection helper to invoke a private no-arg method on the column and print the result.
   *
   * @param column column instance
   * @param methodName method to invoke
   */
  private void probePrivate(DistillationColumn column, String methodName) {
    try {
      Method m = DistillationColumn.class.getDeclaredMethod(methodName);
      m.setAccessible(true);
      Object result = m.invoke(column);
      System.out.println(String.format("  %-30s = %s", methodName + "()", result));
    } catch (ReflectiveOperationException ex) {
      System.out.println("  " + methodName + "() = REFLECTION FAILED: " + ex.getMessage());
    }
  }
}
