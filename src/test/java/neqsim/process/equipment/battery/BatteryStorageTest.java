package neqsim.process.equipment.battery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.DynamicCapability;
import neqsim.process.dynamics.DynamicCapabilityResolver;
import neqsim.process.dynamics.TransientStepIdentifier;

public class BatteryStorageTest extends neqsim.NeqSimTest {
  @Test
  void testChargeDischargeCycle() {
    BatteryStorage battery = new BatteryStorage("battery", 1000.0);

    // Charge for 1 hour at 100 W
    battery.charge(100.0, 1.0);
    battery.run();
    assertEquals(95.0, battery.getStateOfCharge(), 1e-6);
    assertEquals(100.0, battery.getEnergyStream().getDuty(), 1e-6);

    // Discharge for 1 hour at 50 W
    battery.discharge(50.0, 1.0);
    battery.run();
    double expectedSoc = 95.0 - 50.0 / 0.95;
    assertEquals(expectedSoc, battery.getStateOfCharge(), 1e-6);
    assertEquals(-50.0, battery.getEnergyStream().getDuty(), 1e-6);
  }

  /** Same-ID refinements recompute from physical-step start without consuming ramp or energy twice. */
  @Test
  void transientRefinementIsIdempotentForPowerStateOfChargeAndClock() {
    BatteryStorage battery = new BatteryStorage("dynamic battery", 1000.0);
    battery.setStateOfCharge(500.0);
    battery.setEfficiencies(1.0, 1.0);
    battery.setPowerLimits(1000.0, 1000.0);
    battery.setPowerRampRate(100.0);
    battery.setTargetPower(1000.0);

    UUID physicalStepA = TransientStepIdentifier.deterministicPhysicalStep("battery-refinement", 0L);
    UUID physicalStepB = TransientStepIdentifier.deterministicPhysicalStep("battery-refinement", 1L);

    battery.runTransient(2.0, physicalStepA);
    double stateAfterA = battery.getStateOfCharge();
    assertEquals(200.0, battery.getCurrentPower(), 1.0e-12);
    assertEquals(500.0 - 200.0 * 2.0 / 3600.0, stateAfterA, 1.0e-12);
    assertEquals(2.0, battery.getTime(), 0.0);

    battery.runTransient(2.0, physicalStepA);
    assertEquals(200.0, battery.getCurrentPower(), 1.0e-12);
    assertEquals(stateAfterA, battery.getStateOfCharge(), 1.0e-12);
    assertEquals(2.0, battery.getTime(), 0.0);

    battery.runTransient(2.0, physicalStepB);
    assertEquals(400.0, battery.getCurrentPower(), 1.0e-12);
    assertEquals(stateAfterA - 400.0 * 2.0 / 3600.0, battery.getStateOfCharge(), 1.0e-12);
    assertEquals(4.0, battery.getTime(), 0.0);
    assertEquals(physicalStepB, battery.getCalculationIdentifier());
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, DynamicCapabilityResolver.resolve(battery));
  }
}
