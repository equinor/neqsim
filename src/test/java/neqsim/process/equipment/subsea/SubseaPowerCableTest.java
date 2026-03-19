package neqsim.process.equipment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for SubseaPowerCable class.
 */
public class SubseaPowerCableTest extends neqsim.NeqSimTest {

  @Test
  public void testACCableBasicCalculation() {
    SubseaPowerCable cable = new SubseaPowerCable("Export Cable");
    cable.setCableType(SubseaPowerCable.CableType.XLPE_AC);
    cable.setVoltage(66.0);
    cable.setLength(50.0);
    cable.setPowerRating(200.0);
    cable.setConductorArea(1000.0);
    cable.setSeawaterTemperature(10.0);
    cable.run();

    assertTrue(cable.getTotalPowerLoss() > 0, "Should have power losses");
    assertTrue(cable.getVoltageDrop() > 0, "Should have voltage drop");
    assertTrue(cable.getOperatingCurrent() > 0, "Should have operating current");
    assertTrue(cable.getEfficiency() > 0.9, "Efficiency should be > 90%");
    assertTrue(cable.getReactivePower() > 0, "AC cable should generate reactive power");
  }

  @Test
  public void testHVDCCable() {
    SubseaPowerCable cable = new SubseaPowerCable("HVDC Export");
    cable.setCableType(SubseaPowerCable.CableType.XLPE_HVDC);
    cable.setVoltage(320.0);
    cable.setLength(200.0);
    cable.setPowerRating(1000.0);
    cable.setConductorArea(2000.0);
    cable.run();

    assertTrue(cable.getTotalPowerLoss() > 0, "Should have power losses");
    assertEquals(0.0, cable.getDielectricLoss(), 1e-6, "No dielectric loss for DC");
    assertEquals(0.0, cable.getReactivePower(), 1e-6, "No reactive power for DC");
  }

  @Test
  public void testLossesIncreaseWithLength() {
    SubseaPowerCable cable1 = new SubseaPowerCable("Short Cable");
    cable1.setCableType(SubseaPowerCable.CableType.XLPE_AC);
    cable1.setVoltage(66.0);
    cable1.setLength(10.0);
    cable1.setPowerRating(200.0);
    cable1.setConductorArea(1000.0);
    cable1.run();

    SubseaPowerCable cable2 = new SubseaPowerCable("Long Cable");
    cable2.setCableType(SubseaPowerCable.CableType.XLPE_AC);
    cable2.setVoltage(66.0);
    cable2.setLength(100.0);
    cable2.setPowerRating(200.0);
    cable2.setConductorArea(1000.0);
    cable2.run();

    assertTrue(cable2.getTotalPowerLoss() > cable1.getTotalPowerLoss(),
        "Longer cable should have higher losses");
  }

  @Test
  public void testCostEstimate() {
    SubseaPowerCable cable = new SubseaPowerCable("Cost Cable");
    cable.setCableType(SubseaPowerCable.CableType.XLPE_AC);
    cable.setVoltage(66.0);
    cable.setLength(50.0);
    cable.setConductorArea(800.0);
    cable.setNumberOfCables(2);
    cable.run();

    assertTrue(cable.getCableCost() > 0, "Cost should be positive");
  }

  @Test
  public void testDesignResults() {
    SubseaPowerCable cable = new SubseaPowerCable("Results Cable");
    cable.setCableType(SubseaPowerCable.CableType.XLPE_AC);
    cable.setVoltage(132.0);
    cable.setLength(80.0);
    cable.setPowerRating(500.0);
    cable.setConductorArea(1200.0);
    cable.run();

    java.util.Map<String, Object> results = cable.getDesignResults();
    assertTrue(results.containsKey("totalPowerLoss_MW"));
    assertTrue(results.containsKey("voltageDrop_pct"));
    assertTrue(results.containsKey("efficiency_pct"));
  }

  @Test
  public void testEquipmentFactory() {
    SubseaPowerCable cable = (SubseaPowerCable) neqsim.process.equipment
        .EquipmentFactory.createEquipment("cable", "subseapowercable");
    assertTrue(cable instanceof SubseaPowerCable);
  }
}
