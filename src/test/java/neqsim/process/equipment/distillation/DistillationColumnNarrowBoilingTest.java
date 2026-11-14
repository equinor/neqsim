package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.DistillationColumn.SolverType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for columns whose tray mixtures boil over a narrow temperature range.
 *
 * <p>
 * On such trays almost all of dH/dT is latent heat, so a temperature Newton step based on the fixed-composition heat
 * capacity overshoots the enthalpy root. The tray enthalpy flash must bracket the root instead of oscillating until its
 * iteration limit.
 * </p>
 */
public class DistillationColumnNarrowBoilingTest {
  /**
   * Build a three-component debutanizer with condenser and reboiler.
   *
   * @param solverType solver to use
   * @return configured column
   */
  private static DistillationColumn createDebutanizer(SolverType solverType) {
    SystemInterface fluid = new SystemSrkEos(353.15, 6.0);
    fluid.addComponent("n-butane", 0.35);
    fluid.addComponent("n-pentane", 0.45);
    fluid.addComponent("n-hexane", 0.20);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("debutanizer feed", fluid);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("debutanizer", 6, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(6.0);
    column.setBottomPressure(6.2);
    column.getCondenser().setOutletTemperature(318.15);
    column.getReboiler().setOutletTemperature(403.15);
    column.setCondenserRefluxRatio(1.8);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    column.setSolverType(solverType);
    return column;
  }

  /** The inside-out path must converge instead of exhausting tray enthalpy-flash iterations. */
  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  public void insideOutConvergesOnNarrowBoilingTrays() {
    DistillationColumn column = createDebutanizer(SolverType.INSIDE_OUT);
    column.setInnerLoopSteps(2);
    column.run();

    assertTrue(column.solved(), "Inside-out must converge the narrow-boiling debutanizer");
    double products = column.getGasOutStream().getFlowRate("kg/hr") + column.getLiquidOutStream().getFlowRate("kg/hr");
    assertEquals(250.0, products, 250.0 * 1.0e-6);
  }

  /** The residual-audited solver must reach a rigorous state, not reconciled products. */
  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  public void meshResidualReachesRigorousConvergence() {
    DistillationColumn column = createDebutanizer(SolverType.MESH_RESIDUAL);
    column.run();

    assertTrue(column.solved());
    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus());
    assertTrue(column.getLastMassResidual() < 1.0e-8, "Mass residual " + column.getLastMassResidual());
    double products = column.getGasOutStream().getFlowRate("kg/hr") + column.getLiquidOutStream().getFlowRate("kg/hr");
    assertEquals(250.0, products, 250.0 * 1.0e-8);
  }

  /** Sequential substitution and the simultaneous solver must agree on the product split. */
  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  public void sequentialAndSimultaneousSolversAgree() {
    DistillationColumn sequential = createDebutanizer(SolverType.DIRECT_SUBSTITUTION);
    sequential.run();
    DistillationColumn simultaneous = createDebutanizer(SolverType.NAPHTALI_SANDHOLM);
    simultaneous.run();

    assertTrue(sequential.solved());
    assertTrue(simultaneous.solved());
    assertEquals(simultaneous.getGasOutStream().getFlowRate("kg/hr"), sequential.getGasOutStream().getFlowRate("kg/hr"),
        250.0 * 5.0e-3);
  }
}
