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
 * Tests for the rejection, rollback and exclusion paths of Anderson mixing in sequential substitution.
 */
public class DistillationColumnAndersonSafeguardTest {
  /**
   * Build the deethanizer feed stream.
   *
   * @return feed stream that has been run
   */
  private static Stream createDeethanizerFeed() {
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
    Stream feed = new Stream("deethanizer feed", gas);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();
    return feed;
  }

  /**
   * Build a reboiled deethanizer with the feed on the top tray.
   *
   * @param trays number of trays
   * @return configured direct-substitution column
   */
  private static DistillationColumn createDeethanizer(int trays) {
    return createDeethanizer(createDeethanizerFeed(), trays);
  }

  /**
   * Build a reboiled deethanizer for a given feed on the top tray.
   *
   * @param feed feed stream
   * @param trays number of trays
   * @return configured direct-substitution column
   */
  private static DistillationColumn createDeethanizer(Stream feed, int trays) {
    DistillationColumn column = new DistillationColumn("deethanizer", trays, true, false);
    column.addFeedStream(feed, trays);
    column.getReboiler().setOutletTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(80);
    column.setSolverType(SolverType.DIRECT_SUBSTITUTION);
    return column;
  }

  /**
   * Build a seven-tray reboiled demethanizer with loose convergence tolerances.
   *
   * @return configured direct-substitution column
   */
  private static DistillationColumn createLooseDemethanizer() {
    SystemInterface fluid = new SystemSrkEos(216.0, 30.0);
    fluid.addComponent("methane", 0.55);
    fluid.addComponent("ethane", 0.20);
    fluid.addComponent("propane", 0.15);
    fluid.addComponent("n-butane", 0.07);
    fluid.addComponent("n-pentane", 0.03);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("demethanizer feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("demethanizer", 7, true, false);
    column.addFeedStream(feed, 4);
    column.setTopPressure(30.0);
    column.setBottomPressure(31.0);
    column.getReboiler().setOutletTemperature(273.15 + 100.0);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    column.setSolverType(SolverType.DIRECT_SUBSTITUTION);
    return column;
  }

  /**
   * Check a column whose every mixing phase is forced to be rejected against plain substitution.
   *
   * @param forced column with a zero stagnation limit, not yet run
   * @param plain identical column with mixing disabled, not yet run
   */
  private static void assertForcedRejectionsRecover(DistillationColumn forced, DistillationColumn plain) {
    forced.setAndersonStagnationSweeps(0);
    forced.run();
    plain.setAndersonAccelerationEnabled(false);
    plain.run();

    assertTrue(plain.solved());
    assertTrue(forced.solved(), "A solve whose mixing phases are all rejected must still converge");
    assertTrue(forced.getLastAndersonRejectionCount() > 0, "The rejection path must have been exercised");
    assertTrue(forced.getLastAndersonRejectionCount() <= 3);
    assertEquals(0, forced.getLastAndersonAccelerationCount(), "No mixed state may survive a zero stagnation limit");
    assertTrue(forced.getLastSweepsAfterAndersonRejection() >= 1,
        "A rollback must be followed by a sweep of the restored state before a result is accepted");
    double plainGas = plain.getGasOutStream().getFlowRate("kg/hr");
    assertEquals(plainGas, forced.getGasOutStream().getFlowRate("kg/hr"), 5.0e-3 * plainGas);
    double products = forced.getGasOutStream().getFlowRate("kg/hr") + forced.getLiquidOutStream().getFlowRate("kg/hr");
    assertEquals(100.0, products, 0.1);
  }

  /** Rejected phases restore their best state, refund their sweeps and leave a converged column. */
  @Test
  @Timeout(value = 180, unit = TimeUnit.SECONDS)
  public void rejectedPhasesRollBackAndStillConverge() {
    assertForcedRejectionsRecover(createDeethanizer(5), createDeethanizer(5));
    assertForcedRejectionsRecover(createDeethanizer(10), createDeethanizer(10));
  }

  /** With loose tolerances a rejection can coincide with a small sweep step; it must not be accepted there. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void rollbackIsNotAcceptedWithoutFreshSweep() {
    assertForcedRejectionsRecover(createLooseDemethanizer(), createLooseDemethanizer());
  }

  /** An undisturbed solve reports no rejection. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void acceptedPhaseReportsNoRejection() {
    DistillationColumn column = createDeethanizer(5);
    column.run();

    assertTrue(column.solved());
    assertTrue(column.getLastAndersonAccelerationCount() > 0);
    assertEquals(0, column.getLastAndersonRejectionCount());
    assertEquals(-1, column.getLastSweepsAfterAndersonRejection());
  }

  /** A warm restart after an input change runs damped substitution without mixing. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void warmRestartDoesNotMix() {
    Stream feed = createDeethanizerFeed();
    DistillationColumn column = createDeethanizer(feed, 5);
    column.run();
    assertTrue(column.getLastAndersonAccelerationCount() > 0);

    feed.setFlowRate(102.0, "kg/hr");
    feed.run();
    column.run();

    assertTrue(column.solved());
    assertEquals(0, column.getLastAndersonAccelerationCount());
    assertEquals(0, column.getLastAndersonRejectionCount());
  }

  /** A hard iteration cap disables mixing, because rejected sweeps could not be refunded. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void hardIterationCapDisablesMixing() {
    DistillationColumn column = createDeethanizer(5);
    column.setMaxNumberOfIterations(400, true);
    column.run();

    assertTrue(column.solved());
    assertEquals(0, column.getLastAndersonAccelerationCount());
    assertEquals(0, column.getLastAndersonRejectionCount());
  }

  /** Nonideal Murphree stages disable mixing. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void murphreeEfficiencyDisablesMixing() {
    DistillationColumn column = createDeethanizer(5);
    column.setMurphreeEfficiency(0.8);
    column.run();

    assertEquals(0, column.getLastAndersonAccelerationCount());
    assertEquals(0, column.getLastAndersonRejectionCount());
  }

  /** The public switch disables mixing. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void switchDisablesMixing() {
    DistillationColumn column = createDeethanizer(5);
    column.setAndersonAccelerationEnabled(false);
    column.run();

    assertTrue(column.solved());
    assertEquals(0, column.getLastAndersonAccelerationCount());
    assertEquals(0, column.getLastAndersonRejectionCount());
  }
}
