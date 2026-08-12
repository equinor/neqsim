package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the solver-tuning knobs on {@link DistillationColumn} that used to be unreachable or silently ignored: the
 * adaptive relaxation floor, the soft/hard iteration budget and the relative temperature tolerance.
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnSolverTuningTest {

  /**
   * Builds a small column with one feed for configuration tests.
   *
   * @param numberOfTrays number of theoretical stages
   * @return an unrun column
   */
  private static DistillationColumn buildColumn(int numberOfTrays) {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    fluid.addComponent("propane", 40.0);
    fluid.addComponent("n-butane", 30.0);
    fluid.addComponent("n-pentane", 30.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("tuning column", numberOfTrays, true, false);
    column.addFeedStream(feed, numberOfTrays / 2);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    return column;
  }

  /**
   * A relaxation factor below the historical 0.5 floor must actually take effect; previously the adaptive controller
   * clamped it back to 0.5 and the request was silently ignored.
   */
  @Test
  public void relaxationFactorBelowFloorLowersTheFloor() {
    DistillationColumn column = buildColumn(6);
    assertEquals(0.5, column.getMinSequentialRelaxation(), 1e-12, "default sequential relaxation floor");

    column.setRelaxationFactor(0.2);

    assertEquals(0.2, column.getRelaxationFactor(), 1e-12);
    assertEquals(0.2, column.getMinSequentialRelaxation(), 1e-12,
        "requesting heavier damping than the floor must lower the floor");
    assertEquals(0.2, column.getMinInsideOutRelaxation(), 1e-12);
  }

  /**
   * Damping must reduce the applied update without reducing the fixed-point residual used for convergence.
   */
  @Test
  public void reportedResidualIsUndampedFixedPointMismatch() {
    DistillationColumn column = buildColumn(6);
    column.getReboiler().setOutTemperature(273.15 + 80.0);
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.run();
    assertTrue(column.solved(), column.getConvergenceDiagnostics());

    column.setRelaxationFactor(0.2);
    column.getTray(2).setTemperature(column.getTray(2).getTemperature() + 1.0);
    column.setMaxNumberOfIterations(1, true);
    column.run();

    double appliedStep = column.getLastAppliedTemperatureStepResidual();
    double fixedPointResidual = column.getLastTemperatureResidual();
    assertTrue(appliedStep > 0.0, "the perturbed state must produce an applied temperature step");
    assertTrue(fixedPointResidual > 0.0, "the perturbed state must produce a fixed-point residual");
    assertEquals(0.2 * fixedPointResidual, appliedStep, Math.max(1.0e-10, 1.0e-8 * fixedPointResidual),
        "relaxation must scale only the applied step, not the convergence residual");
  }

  /**
   * A relaxation factor above the floor must leave the floor untouched.
   */
  @Test
  public void relaxationFactorAboveFloorKeepsTheFloor() {
    DistillationColumn column = buildColumn(6);
    column.setRelaxationFactor(0.8);

    assertEquals(0.8, column.getRelaxationFactor(), 1e-12);
    assertEquals(0.5, column.getMinSequentialRelaxation(), 1e-12);
  }

  /**
   * The relaxation floor is directly settable and validated.
   */
  @Test
  public void minSequentialRelaxationIsSettableAndValidated() {
    final DistillationColumn column = buildColumn(6);
    column.setMinSequentialRelaxation(0.05);
    assertEquals(0.05, column.getMinSequentialRelaxation(), 1e-12);

    column.setMinInsideOutRelaxation(0.1);
    assertEquals(0.1, column.getMinInsideOutRelaxation(), 1e-12);

    assertThrows(IllegalArgumentException.class, new Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
        column.setMinSequentialRelaxation(0.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
        column.setMinSequentialRelaxation(1.5);
      }
    });
    assertThrows(IllegalArgumentException.class, new Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
        column.setRelaxationFactor(0.0);
      }
    });
  }

  /**
   * The single-argument iteration setter is only a soft floor, so the effective budget stays at the tray-based limit.
   * The two-argument overload turns it into a hard cap.
   */
  @Test
  public void effectiveIterationLimitExposesSoftFloorBehaviour() {
    DistillationColumn column = buildColumn(11);

    column.setMaxNumberOfIterations(10);
    assertEquals(10, column.getMaxNumberOfIterations(), "configured value is stored as requested");
    assertTrue(column.getEffectiveMaxNumberOfIterations() >= 55,
        "soft floor must not shrink the adaptive tray-based budget, was " + column.getEffectiveMaxNumberOfIterations());

    column.setMaxNumberOfIterations(10, true);
    assertTrue(column.isHardIterationCap());
    assertEquals(10, column.getEffectiveMaxNumberOfIterations(), "hard cap must limit the effective budget");
  }

  /**
   * The relative temperature tolerance converts to an absolute Kelvin tolerance using the column reference temperature,
   * so a column can be aligned with the plant-level boundary tolerance.
   */
  @Test
  public void relativeTemperatureToleranceScalesWithReferenceTemperature() {
    final DistillationColumn column = buildColumn(6);
    double reference = column.getReferenceTemperature();
    assertTrue(reference > 100.0, "reference temperature should be a physical Kelvin value, was " + reference);

    double absolute = column.setTemperatureToleranceRelative(1.0e-3);

    assertEquals(1.0e-3 * reference, absolute, 1e-9);
    assertEquals(absolute, column.getTemperatureTolerance(), 1e-9);
    assertTrue(absolute > 0.02, "1e-3 relative should be looser than the 0.02 K default, was " + absolute);

    assertThrows(IllegalArgumentException.class, new Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
        column.setTemperatureToleranceRelative(0.0);
      }
    });
  }

  /**
   * Exactly 100 percent liquid withdrawal disconnects the internal downflow and must avoid the Naphtali-Sandholm
   * initialization divisions by the remaining liquid fraction. A nearby supported fraction must retain the requested
   * simultaneous solver rather than introducing an arbitrary broad cutoff.
   *
   * @throws Exception if the guarded solver selector cannot be inspected
   */
  @Test
  public void fullLiquidWithdrawalUsesResidualFallbackAtExactBoundary() throws Exception {
    DistillationColumn column = buildColumn(6);
    column.setSolverType(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    int drawTrayNumber = 3;
    Method selector = DistillationColumn.class.getDeclaredMethod("getEffectiveSolverTypeForRun");
    selector.setAccessible(true);

    column.setLiquidSideDrawFraction(drawTrayNumber, 1.0);
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, selector.invoke(column),
        "100 percent liquid withdrawal must avoid singular Naphtali-Sandholm initialization");

    column.setLiquidSideDrawFraction(drawTrayNumber, 0.99);
    assertEquals(DistillationColumn.SolverType.NAPHTALI_SANDHOLM, selector.invoke(column),
        "a nearby positive internal-flow fraction should remain on the simultaneous solver");
  }

}
