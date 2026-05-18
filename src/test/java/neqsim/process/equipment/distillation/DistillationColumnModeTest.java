package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.util.validation.ValidationResult;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests explicit condenser and reboiler mode configuration on distillation columns.
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnModeTest {

  /**
   * Test explicit condenser mode switching.
   */
  @Test
  public void condenserModeCanBeConfiguredAtColumnLevel() {
    DistillationColumn column = new DistillationColumn("mode column", 2, true, true);

    assertEquals(DistillationColumn.CondenserMode.PARTIAL, column.getCondenserMode());
    column.setCondenserMode(DistillationColumn.CondenserMode.TOTAL);
    assertEquals(DistillationColumn.CondenserMode.TOTAL, column.getCondenserMode());
    column.setCondenserLiquidReflux(10.0, "kg/hr");
    assertEquals(DistillationColumn.CondenserMode.LIQUID_REFLUX_SPLIT,
        column.getCondenserMode());
    column.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    assertEquals(DistillationColumn.CondenserMode.PARTIAL, column.getCondenserMode());
  }

  /**
   * Test explicit reboiler mode switching.
   */
  @Test
  public void reboilerModeCanBeConfiguredAtColumnLevel() {
    DistillationColumn column = new DistillationColumn("reboiler mode column", 2, true, true);

    assertEquals(DistillationColumn.ReboilerMode.EQUILIBRIUM, column.getReboilerMode());
    column.setReboilerVaporBoilupRatio(0.8);
    assertEquals(DistillationColumn.ReboilerMode.VAPOR_BOILUP_RATIO, column.getReboilerMode());
    column.setReboilerMode(DistillationColumn.ReboilerMode.EQUILIBRIUM);
    assertEquals(DistillationColumn.ReboilerMode.EQUILIBRIUM, column.getReboilerMode());
  }

  /**
   * Test that missing hardware is reported clearly.
   */
  @Test
  public void missingHardwareThrowsForModeWrappers() {
    DistillationColumn column = new DistillationColumn("bare column", 2, false, false);

    assertThrows(IllegalStateException.class, () -> column.getCondenserMode());
    assertThrows(IllegalStateException.class, () -> column.getReboilerMode());
  }

  /**
   * Test liquid pumparound configuration and validation.
   */
  @Test
  public void liquidPumparoundCanBeConfiguredAtColumnLevel() {
    DistillationColumn column = new DistillationColumn("pumparound column", 3, true, true);

    DistillationColumn.ColumnPumparound pumparound = column.addLiquidPumparound("PA-1", 2, 1,
        0.15, 10.0);

    assertEquals(1, column.getPumparounds().size());
    assertEquals("PA-1", pumparound.getName());
    assertEquals(2, pumparound.getDrawTrayNumber());
    assertEquals(1, pumparound.getReturnTrayNumber());
    assertEquals(0.15, column.getTray(2).getLiquidPumparoundDrawFraction(), 1.0e-12);
    assertThrows(IllegalArgumentException.class,
        () -> column.addLiquidPumparound("duplicate", 2, 1, 0.10, 10.0));
  }

  /**
   * Test that a pumparound run updates and re-solves with a final return stream state.
   */
  @Test
  public void liquidPumparoundRunCreatesFinalReturnStream() {
    Stream feed = createLiquidPentaneFeed("pumparound liquid feed");
    DistillationColumn column = new DistillationColumn("running pumparound column", 1, false,
        false);
    column.addFeedStream(feed, 0);
    DistillationColumn.ColumnPumparound pumparound = column.addLiquidPumparound("PA-run", 0, 0,
        0.20, 5.0);

    column.run(UUID.randomUUID());

    assertNotNull(pumparound.getReturnStream());
    assertTrue(pumparound.getReturnStream().getFlowRate("kg/hr") >= 0.0);
    assertTrue(column.getLastColumnTearIterationCount() > 0);
    assertTrue(column.getLastPumparoundRelativeChange() >= 0.0);
  }

  /**
   * Test hydraulic pressure-drop coupling API and diagnostics.
   */
  @Test
  public void hydraulicPressureDropCouplingCanBeEnabled() {
    DistillationColumn column = new DistillationColumn("hydraulic coupling column", 2, true,
        true);

    column.enableHydraulicPressureDropCoupling("sieve");

    assertTrue(column.isHydraulicPressureDropCouplingEnabled());
    assertEquals(0.0, column.getLastHydraulicPressureDropPa(), 1.0e-12);
    assertThrows(IllegalArgumentException.class,
        () -> column.setHydraulicPressureDropInternalsType(""));
  }

  /**
   * Test that the simplified dynamic model is clearly labelled as experimental.
   */
  @Test
  public void dynamicColumnModelReportsExperimentalStatus() {
    DistillationColumn column = new DistillationColumn("dynamic label column", 2, true, true);
    column.setDynamicColumnEnabled(true);
    ValidationResult result = column.validateSetup();

    assertEquals(DistillationColumn.DynamicColumnModel.EXPERIMENTAL_EULER,
        column.getDynamicColumnModel());
    assertTrue(column.isDynamicColumnModelExperimental());
    assertTrue(result.hasWarnings());
    assertTrue(result.getWarnings().stream()
        .anyMatch(warning -> warning.getMessage().contains("explicit-Euler holdup screening")));
  }

  /**
   * Test that nonphysical pumparound return temperatures fail explicitly.
   */
  @Test
  public void nonPhysicalPumparoundReturnTemperatureThrows() {
    Stream feed = createLiquidPentaneFeed("cold pumparound feed");
    DistillationColumn column = new DistillationColumn("cold pumparound column", 1, false,
        false);
    column.addFeedStream(feed, 0);
    column.addLiquidPumparound("PA-cold", 0, 0, 0.20, 400.0);

    assertThrows(IllegalStateException.class, () -> column.run(UUID.randomUUID()));
  }

  /**
   * Create a liquid pentane feed for pumparound tests.
   *
   * @param name stream name
   * @return initialized n-pentane stream
   */
  private Stream createLiquidPentaneFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 2.0);
    fluid.addComponent("n-pentane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();
    return feed;
  }
}