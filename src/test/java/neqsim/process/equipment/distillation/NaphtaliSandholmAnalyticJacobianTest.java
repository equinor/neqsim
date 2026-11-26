package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.DistillationColumn.SolveStatus;
import neqsim.process.equipment.distillation.DistillationColumn.SolverType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Column-level checks of the simultaneous-correction solver with the analytic Jacobian.
 *
 * @author NeqSim / AI-assisted
 * @version 1.0
 */
class NaphtaliSandholmAnalyticJacobianTest extends neqsim.NeqSimTest {
  /**
   * Build a 20-tray depropanizer with a condenser reflux ratio and fixed terminal temperatures.
   *
   * @param solverType solver to use
   * @return configured column
   */
  private static DistillationColumn createDepropanizer(SolverType solverType) {
    return createDepropanizer(solverType, 1.8);
  }

  /**
   * Build a 20-tray depropanizer with the given condenser reflux ratio and fixed terminal temperatures.
   *
   * @param solverType solver to use
   * @param refluxRatio condenser reflux ratio
   * @return configured column
   */
  private static DistillationColumn createDepropanizer(SolverType solverType, double refluxRatio) {
    SystemSrkEos fluid = new SystemSrkEos(333.15, 10.0);
    fluid.addComponent("propane", 0.35);
    fluid.addComponent("n-butane", 0.45);
    fluid.addComponent("n-pentane", 0.20);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("depropanizer feed", fluid);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("depropanizer", 20, true, true);
    column.addFeedStream(feed, 10);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.2);
    column.getCondenser().setOutletTemperature(300.15);
    column.getReboiler().setOutletTemperature(370.15);
    column.setCondenserRefluxRatio(refluxRatio);
    column.setSolverType(solverType);
    return column;
  }

  /**
   * The direct solve must converge rigorously on its own and build every Jacobian analytically.
   */
  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void directSolveConvergesWithAnalyticJacobian() {
    DistillationColumn column = createDepropanizer(SolverType.NAPHTALI_SANDHOLM);
    column.run();

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertEquals(SolverType.NAPHTALI_SANDHOLM, column.getLastSolverTypeUsed(),
        "The simultaneous solver must not need the sequential fallback");
    assertEquals(SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus());
    assertTrue(column.getLastNaphtaliAnalyticJacobianColumns() > 0, "Jacobians must be analytic");
    assertEquals(0, column.getLastNaphtaliFiniteDifferenceJacobianColumns(),
        "No finite-difference Jacobian is expected for ideal stages");
    assertTrue(column.getLastMeshResidualNorm() < 1.0e-6, "MESH residual " + column.getLastMeshResidualNorm());
    double products = column.getGasOutStream().getFlowRate("kg/hr") + column.getLiquidOutStream().getFlowRate("kg/hr");
    assertEquals(250.0, products, 1.0e-6);
  }

  /**
   * AUTO must select the direct simultaneous solve for a condenser and reboiler column and report the same products.
   */
  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void autoSelectsSimultaneousCorrectionForCondenserColumn() {
    DistillationColumn direct = createDepropanizer(SolverType.NAPHTALI_SANDHOLM);
    direct.run();
    DistillationColumn automatic = createDepropanizer(SolverType.AUTO);
    automatic.run();

    assertTrue(automatic.solved(), automatic.getConvergenceDiagnostics());
    assertEquals(SolverType.NAPHTALI_SANDHOLM, automatic.getLastSolverTypeUsed());
    assertEquals(SolveStatus.RIGOROUS_CONVERGED, automatic.getLastSolveStatus());
    assertEquals(direct.getGasOutStream().getFlowRate("kg/hr"), automatic.getGasOutStream().getFlowRate("kg/hr"),
        1.0e-6);
    assertTrue(automatic.getLastAutoSolverSummary().contains("NAPHTALI_SANDHOLM"),
        automatic.getLastAutoSolverSummary());
  }

  /**
   * When the simultaneous solve does not converge, AUTO must take the residual-gated solver next and still report which
   * solver produced the state and what it tried.
   */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void autoFallsBackToMeshResidualAndReportsTheSolver() {
    DistillationColumn automatic = createDepropanizer(SolverType.AUTO, 4.0);
    automatic.run();

    assertTrue(automatic.solved(), automatic.getConvergenceDiagnostics());
    assertNotNull(automatic.getLastSolverTypeUsed(), "A solved column must name its solver");
    assertEquals(SolverType.MESH_RESIDUAL, automatic.getLastSolverTypeUsed());
    assertEquals(SolveStatus.RIGOROUS_CONVERGED, automatic.getLastSolveStatus());
    String summary = automatic.getLastAutoSolverSummary();
    assertNotNull(summary, "AUTO must keep its candidate summary");
    assertTrue(summary.contains("NAPHTALI_SANDHOLM") && summary.contains("MESH_RESIDUAL"), summary);
    assertTrue(!summary.contains("DAMPED_SUBSTITUTION"), "The sequential ladder must not run: " + summary);
  }

  /**
   * A column without a condenser keeps the existing AUTO route.
   */
  @Test
  void reboilerOnlyColumnIsNotASimultaneousCorrectionCandidate() {
    DistillationColumn stripper = new DistillationColumn("stripper", 5, true, false);
    assertTrue(!stripper.isPreferredSimultaneousCorrectionCandidate());
    DistillationColumn fractionator = new DistillationColumn("fractionator", 5, true, true);
    assertTrue(!fractionator.isPreferredSimultaneousCorrectionCandidate(),
        "Two terminal temperatures without a ratio keep the sequential ladder");
    fractionator.setCondenserRefluxRatio(1.5);
    assertTrue(fractionator.isPreferredSimultaneousCorrectionCandidate());
    fractionator.setMurphreeEfficiency(0.7);
    assertTrue(!fractionator.isPreferredSimultaneousCorrectionCandidate(),
        "Nonideal stages keep the sequential Murphree model");
  }

  /**
   * The flow step keeps a flow positive, is linear for moderate steps and is continuous at the switch point.
   */
  @Test
  void positiveFlowStepIsLinearThenExponential() {
    assertEquals(12.0, NaphtaliSandholmSolver.applyPositiveFlowStep(10.0, 2.0), 1.0e-12);
    assertEquals(5.0, NaphtaliSandholmSolver.applyPositiveFlowStep(10.0, -5.0), 1.0e-12);
    assertEquals(2.5, NaphtaliSandholmSolver.applyPositiveFlowStep(10.0, -7.5), 1.0e-12);
    double justBelow = NaphtaliSandholmSolver.applyPositiveFlowStep(10.0, -7.5 - 1.0e-9);
    assertEquals(2.5, justBelow, 1.0e-8);
    double damped = NaphtaliSandholmSolver.applyPositiveFlowStep(10.0, -100.0);
    assertTrue(damped > 0.0 && damped < 2.5, "A step past zero must stay positive: " + damped);
    assertTrue(NaphtaliSandholmSolver.applyPositiveFlowStep(0.0, -1.0) > 0.0);
  }
}
