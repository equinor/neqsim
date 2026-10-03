package neqsim.process.equipment.compressor.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ElectricMotorDriver#setCellBypassDerating(int, int)} — cascaded multi-cell medium-voltage VFD
 * derating after one or more power cells are bypassed (e.g. Siemens SINAMICS GH150 / Perfect Harmony drives).
 *
 * <p>
 * Motivated by a live PEPR field incident (Njord A, action 80303156): a 3rd-stage recompression-train VFD tripped on a
 * bypass fault at restart after a failed power module ("cell 113") had been swapped in with its bypass engaged; the
 * drive was returned to service running permanently on one fewer power cell.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ElectricMotorDriverCellBypassTest {

  /**
   * With no cell-bypass configured, the voltage derate fraction is 1.0 and the VFD max speed is unaffected.
   */
  @Test
  void testNoDeratingByDefault() {
    ElectricMotorDriver driver = new ElectricMotorDriver(5000.0, 3600.0, 0.96);
    driver.setHasVFD(true);
    driver.setMaxSpeedRatio(1.0);

    assertEquals(1.0, driver.getVoltageDerateFraction(), 1.0e-9);
    assertEquals(3600.0, driver.getMaxSpeed(), 1.0e-6);
    assertEquals(0, driver.getTotalCellsPerPhase());
    assertEquals(0, driver.getBypassedCellsPerPhase());
  }

  /**
   * One of nine series cells bypassed derates the voltage fraction and the VFD max speed by 1/9, matching the constant
   * volts-per-hertz assumption, regardless of whether the cell bypass or the VFD flag is configured first.
   */
  @Test
  void testOneOfNineCellsBypassedDeratesMaxSpeed() {
    ElectricMotorDriver driverBypassFirst = new ElectricMotorDriver(5000.0, 3600.0, 0.96);
    driverBypassFirst.setCellBypassDerating(9, 1);
    driverBypassFirst.setHasVFD(true);
    driverBypassFirst.setMaxSpeedRatio(1.0);

    ElectricMotorDriver driverVfdFirst = new ElectricMotorDriver(5000.0, 3600.0, 0.96);
    driverVfdFirst.setHasVFD(true);
    driverVfdFirst.setMaxSpeedRatio(1.0);
    driverVfdFirst.setCellBypassDerating(9, 1);

    double expectedFraction = 8.0 / 9.0;
    double expectedMaxSpeed = 3600.0 * expectedFraction;

    assertEquals(expectedFraction, driverBypassFirst.getVoltageDerateFraction(), 1.0e-9);
    assertEquals(expectedMaxSpeed, driverBypassFirst.getMaxSpeed(), 1.0e-6);
    assertEquals(expectedFraction, driverVfdFirst.getVoltageDerateFraction(), 1.0e-9);
    assertEquals(expectedMaxSpeed, driverVfdFirst.getMaxSpeed(), 1.0e-6);

    // Above the derated max speed the drive can no longer deliver power (matches existing VFD speed-limit checks).
    assertEquals(0.0, driverBypassFirst.getAvailablePower(expectedMaxSpeed + 10.0), 1.0e-9);
    assertTrue(driverBypassFirst.getAvailablePower(expectedMaxSpeed - 10.0) > 0.0);
  }

  /**
   * Invalid cell counts are rejected: non-positive total, or bypassing all (or more than all) cells.
   */
  @Test
  void testInvalidCellCountsRejected() {
    ElectricMotorDriver driver = new ElectricMotorDriver(5000.0, 3600.0, 0.96);
    assertThrows(IllegalArgumentException.class, () -> driver.setCellBypassDerating(0, 0));
    assertThrows(IllegalArgumentException.class, () -> driver.setCellBypassDerating(9, 9));
    assertThrows(IllegalArgumentException.class, () -> driver.setCellBypassDerating(9, -1));
  }
}
