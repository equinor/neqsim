package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the Naphtali-Sandholm warm-state cache introduced with the warm-solve speed-up.
 *
 * <p>
 * The cache reuses an accepted tray solution when a fingerprint of the solver inputs is unchanged. The fingerprint
 * originally covered only the feed streams and the optional top/bottom column specifications, so a change to column
 * pressure or to a reboiler/condenser temperature - neither of which marks the column for re-initialization - returned
 * the previous solution bit for bit. These tests pin the column configuration into the fingerprint.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnWarmStateCacheTest {

  /**
   * Builds a small stripper solved with the simultaneous-correction solver.
   *
   * @return an unrun column configured for Naphtali-Sandholm
   */
  private static DistillationColumn buildColumn() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    fluid.addComponent("propane", 40.0);
    fluid.addComponent("n-butane", 30.0);
    fluid.addComponent("n-pentane", 30.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("warm cache column", 6, true, false);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.getReboiler().setOutTemperature(273.15 + 80.0);
    column.setSolverType(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    return column;
  }

  /**
   * A reboiler temperature change must invalidate the warm state. {@code Reboiler.setOutTemperature} does not mark the
   * column for re-initialization, so the fingerprint is the only thing that can catch it.
   */
  @Test
  public void reboilerTemperatureChangeInvalidatesWarmState() {
    DistillationColumn column = buildColumn();
    column.run();
    double firstGasFlow = column.getGasOutStream().getFlowRate("kg/hr");
    double firstBottomFlow = column.getLiquidOutStream().getFlowRate("kg/hr");
    assertTrue(firstGasFlow > 0.0, "the first solve should produce overhead flow");

    column.getReboiler().setOutTemperature(273.15 + 110.0);
    column.run();

    assertNotEquals(firstGasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 1.0,
        "a 30 K reboiler temperature change must change the overhead flow instead of reusing the warm state");
    assertNotEquals(firstBottomFlow, column.getLiquidOutStream().getFlowRate("kg/hr"), 1.0,
        "a 30 K reboiler temperature change must change the bottoms flow instead of reusing the warm state");
  }

  /**
   * A column pressure change must invalidate the warm state. {@code setTopPressure} and {@code setBottomPressure} do
   * not mark the column for re-initialization either.
   */
  @Test
  public void columnPressureChangeInvalidatesWarmState() {
    DistillationColumn column = buildColumn();
    column.run();
    double firstGasFlow = column.getGasOutStream().getFlowRate("kg/hr");

    column.setTopPressure(5.0);
    column.setBottomPressure(5.5);
    column.run();

    assertNotEquals(firstGasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 1.0,
        "halving the column pressure must change the product split instead of reusing the warm state");
  }

  /**
   * Re-running an unchanged column must still hit the cache - the fix must not disable the speed-up it guards.
   */
  @Test
  public void unchangedColumnStillReusesWarmState() {
    DistillationColumn column = buildColumn();
    column.run();
    double firstGasFlow = column.getGasOutStream().getFlowRate("kg/hr");

    column.run();

    assertEquals(firstGasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 1.0e-9,
        "an unchanged column must return the same solution");
    assertTrue(column.getLastSolveStatusReason().contains("Reused"),
        "an unchanged column should reuse the accepted warm state, reason was " + column.getLastSolveStatusReason());
  }
}
