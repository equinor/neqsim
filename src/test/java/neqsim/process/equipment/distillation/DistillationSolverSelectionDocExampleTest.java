package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies the solver set-up snippets in docs/process/equipment/distillation.md ("Choosing a solver" and "Anderson
 * mixing in the sequential solvers"), the neqsim-distillation-design skill and the process-model agent.
 */
public class DistillationSolverSelectionDocExampleTest {
  /**
   * Build the five-tray reboiled deethanizer used by the documented solver measurements.
   *
   * @return configured column without a solver selection
   */
  private static DistillationColumn createDeethanizer() {
    SystemInterface gas = new SystemSrkEos(216.0, 30.0);
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
    Stream feed = new Stream("doc deethanizer feed", gas);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("doc deethanizer", 5, true, false);
    column.addFeedStream(feed, 5);
    column.getReboiler().setOutletTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    return column;
  }

  /** The documented default ladder converges and reports status and solver. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void recommendedSolverLadderConverges() {
    DistillationColumn column = createDeethanizer();

    column.setSolverType(DistillationColumn.SolverType.NEWTON);
    column.run();
    if (!column.solved()) {
      column.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);
      column.run();
    }
    if (!column.solved()) {
      throw new IllegalStateException(column.getConvergenceDiagnostics());
    }
    DistillationColumn.SolveStatus status = column.getLastSolveStatus();
    DistillationColumn.SolverType used = column.getLastSolverTypeUsed();

    assertNotNull(status);
    assertNotNull(used);
    double products = column.getGasOutStream().getFlowRate("kg/hr") + column.getLiquidOutStream().getFlowRate("kg/hr");
    // NEWTON may finish as RECONCILED_PRODUCTS; the documented closure for that status is 0.1 %.
    assertEquals(100.0, products, 0.1);
  }

  /** The verification solver agrees with the default solver within the documented spread. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void verificationSolverAgreesWithinDocumentedSpread() {
    DistillationColumn fast = createDeethanizer();
    fast.setSolverType(DistillationColumn.SolverType.NEWTON);
    fast.run();
    DistillationColumn verified = createDeethanizer();
    verified.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);
    verified.run();

    assertTrue(fast.solved());
    assertTrue(verified.solved());
    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, verified.getLastSolveStatus());
    double fastGas = fast.getGasOutStream().getFlowRate("kg/hr");
    double verifiedGas = verified.getGasOutStream().getFlowRate("kg/hr");
    assertEquals(verifiedGas, fastGas, 3.0e-3 * verifiedGas);
  }

  /** The documented Anderson switch and counter behave as described. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void andersonSwitchAndCounter() {
    DistillationColumn mixed = createDeethanizer();
    mixed.setSolverType(DistillationColumn.SolverType.DIRECT_SUBSTITUTION);
    mixed.run();
    int mixedSteps = mixed.getLastAndersonAccelerationCount();

    DistillationColumn plain = createDeethanizer();
    plain.setSolverType(DistillationColumn.SolverType.DIRECT_SUBSTITUTION);
    plain.setAndersonAccelerationEnabled(false); // plain damped substitution
    plain.run();

    assertTrue(mixed.solved());
    assertTrue(plain.solved());
    assertTrue(mixedSteps > 0);
    assertEquals(0, plain.getLastAndersonAccelerationCount());
    assertTrue(mixed.getLastIterationCount() < plain.getLastIterationCount());
  }
}
