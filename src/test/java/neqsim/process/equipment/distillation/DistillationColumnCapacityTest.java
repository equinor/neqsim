package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the Fs-factor based capacity-constraint functionality of {@link DistillationColumn}.
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnCapacityTest {

  /**
   * The Fs factor must return 0 (not Infinity/NaN) before the column has been run, so the capacity
   * constraint supplier degrades gracefully.
   */
  @Test
  public void fsFactorReturnsZeroBeforeRun() {
    DistillationColumn column = new DistillationColumn("capacity column", 2, true, true);
    assertEquals(0.0, column.getFsFactor(), 1.0e-12);
    assertEquals(0.0, column.getFsFactorUtilization(), 1.0e-12);
    assertTrue(column.isFsFactorWithinDesignLimit());
    assertEquals(0.0, column.getMinimumDiameterForFsLimit(), 1.0e-12);
  }

  /**
   * The maximum allowable Fs factor has a sensible default and can be configured. Setting a
   * non-positive value must be rejected.
   */
  @Test
  public void maxAllowableFsFactorIsConfigurable() {
    DistillationColumn column = new DistillationColumn("capacity column", 2, true, true);
    assertEquals(2.5, column.getMaxAllowableFsFactor(), 1.0e-12);

    column.setMaxAllowableFsFactor(3.0);
    assertEquals(3.0, column.getMaxAllowableFsFactor(), 1.0e-12);

    assertThrows(IllegalArgumentException.class, () -> column.setMaxAllowableFsFactor(0.0));
    assertThrows(IllegalArgumentException.class, () -> column.setMaxAllowableFsFactor(-1.0));
  }

  /**
   * The column must expose an Fs-factor capacity constraint through the
   * {@code CapacityConstrainedEquipment} interface and keep its design value in sync with the
   * configured maximum allowable Fs factor.
   */
  @Test
  public void columnExposesFsFactorCapacityConstraint() {
    DistillationColumn column = new DistillationColumn("capacity column", 2, true, true);

    Map<String, CapacityConstraint> constraints = column.getCapacityConstraints();
    assertNotNull(constraints);
    assertFalse(constraints.isEmpty());
    assertTrue(constraints.containsKey("fsFactor"));

    CapacityConstraint fs = constraints.get("fsFactor");
    assertEquals(2.5, fs.getDesignValue(), 1.0e-12);

    column.setMaxAllowableFsFactor(2.0);
    assertEquals(2.0, column.getCapacityConstraints().get("fsFactor").getDesignValue(), 1.0e-12);
  }

  /**
   * Runs a small binary column and verifies that the Fs factor, utilization and capacity bottleneck
   * reporting respond to the column hydraulics and the configured diameter.
   */
  @Test
  public void capacityRespondsToColumnHydraulics() {
    SystemSrkEos feedFluid = new SystemSrkEos(273.15 + 60.0, 10.0);
    feedFluid.addComponent("propane", 0.5);
    feedFluid.addComponent("n-butane", 0.5);
    feedFluid.setMixingRule("classic");

    Stream feed = new Stream("feed", feedFluid);
    feed.setFlowRate(2000.0, "kg/hr");
    feed.setTemperature(60.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("debutanizer", 4, true, true);
    column.addFeedStream(feed, 2);
    column.getReboiler().setOutTemperature(273.15 + 95.0);
    column.getCondenser().setOutTemperature(273.15 + 40.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.setInternalDiameter(0.5);
    column.run();

    double fs = column.getFsFactor();
    assertTrue(Double.isFinite(fs), "Fs factor must be finite after run");
    assertTrue(fs > 0.0, "Fs factor must be positive for a running column");

    double utilization = column.getFsFactorUtilization();
    assertEquals(fs / column.getMaxAllowableFsFactor(), utilization, 1.0e-9);

    // Max utilization from the capacity framework should match the Fs utilization.
    assertEquals(utilization, column.getMaxUtilization(), 1.0e-6);

    // A larger diameter lowers the gas velocity and therefore the Fs factor.
    column.setInternalDiameter(1.0);
    double fsLarge = column.getFsFactor();
    assertTrue(fsLarge < fs, "Larger diameter must reduce the Fs factor");

    // The minimum diameter for the Fs limit must keep the column within the design limit.
    column.setInternalDiameter(column.getMinimumDiameterForFsLimit() * 1.01);
    assertTrue(column.isFsFactorWithinDesignLimit());
  }
}
