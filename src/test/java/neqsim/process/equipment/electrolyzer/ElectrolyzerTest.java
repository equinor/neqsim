package neqsim.process.equipment.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;

class ElectrolyzerTest extends neqsim.NeqSimTest {

  private static Stream waterFeed() {
    SystemInterface water = new Fluid().create("water");
    Stream inlet = new Stream("water", water);
    inlet.setPressure(1.0, "bara");
    inlet.setTemperature(298.15, "K");
    inlet.setFlowRate(2.0, "mole/sec");
    inlet.run();
    return inlet;
  }

  @Test
  void testElectrolyzer() {
    Stream inlet = waterFeed();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.run();

    assertEquals(2.0, el.getHydrogenOutStream().getFlowRate("mole/sec"), 1e-6);
    assertEquals(1.0, el.getOxygenOutStream().getFlowRate("mole/sec"), 1e-6);
    double expectedPower = 2.0 * 2.0 * 96485.3329 * 1.23;
    assertEquals(expectedPower, el.getEnergyStream().getDuty(), 1.0);
    // Backward compatibility: default technology/IV must be null.
    assertNull(el.getTechnology());
    assertNull(el.getIVCharacteristic());
  }

  @Test
  void testFaradaicEfficiencyReducesH2() {
    Stream inlet = waterFeed();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.setFaradaicEfficiency(0.5);
    el.run();
    // Half the current actually splits water.
    assertEquals(1.0, el.getHydrogenOutStream().getFlowRate("mole/sec"), 1e-6);
  }

  @Test
  void testSetTechnologyAppliesDefaults() {
    Stream inlet = waterFeed();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.setTechnology(ElectrolyzerTechnology.PEM);
    assertEquals(ElectrolyzerTechnology.PEM, el.getTechnology());
    assertEquals(1.8, el.getCellVoltage(), 1e-9);
    assertEquals(2.0, el.getCurrentDensity(), 1e-9);
    assertEquals(0.65, el.getFaradaicEfficiency(), 1e-9);
  }

  @Test
  void testIVCharacteristicOverridesCellVoltage() {
    Stream inlet = waterFeed();
    inlet.setTemperature(353.15, "K");
    inlet.run();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.setTechnology(ElectrolyzerTechnology.PEM);
    ElectrolyzerIVCharacteristic iv = new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM);
    el.setIVCharacteristic(iv);
    el.run();
    // PEM at 2 A/cm2 and 80 C should land roughly between 1.7 and 2.0 V.
    double v = el.getCellVoltage();
    assertTrue(v > 1.6 && v < 2.05, "PEM operating voltage should be ~1.85 V, got " + v);
    // Stack power should be positive.
    assertTrue(el.getStackPower() > 0.0);
  }

  @Test
  void testSpecificEnergyInExpectedRange() {
    Stream inlet = waterFeed();
    inlet.setTemperature(353.15, "K");
    inlet.run();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.setTechnology(ElectrolyzerTechnology.PEM);
    el.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
    el.run();
    double sec = el.getSpecificEnergyConsumption_kWh_per_kg_H2();
    // PEM commercial stacks: ~45-75 kWh/kg (IRENA 2022, IEA Global H2 Review 2023).
    assertTrue(sec > 40.0 && sec < 100.0,
        "PEM specific energy should be 40-100 kWh/kg, got " + sec);
  }

  @Test
  void testValidation() {
    Stream inlet = waterFeed();
    Electrolyzer el = new Electrolyzer("el", inlet);
    assertThrows(IllegalArgumentException.class, () -> el.setFaradaicEfficiency(0.0));
    assertThrows(IllegalArgumentException.class, () -> el.setFaradaicEfficiency(1.5));
    assertThrows(IllegalArgumentException.class, () -> el.setCurrentDensity(-0.1));
    assertThrows(IllegalArgumentException.class, () -> el.setTechnology(null));
  }

  @Test
  void testStackPowerMatchesEnergyStream() {
    Stream inlet = waterFeed();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.run();
    assertNotNull(el.getEnergyStream());
    assertEquals(el.getStackPower(), el.getEnergyStream().getDuty(), 1.0);
  }
}
