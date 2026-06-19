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
    assertTrue(sec > 40.0 && sec < 100.0, "PEM specific energy should be 40-100 kWh/kg, got " + sec);
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

  /** Helper building a 1 MW PEM electrolyzer sized at 80 C with an I-V model. */
  private static Electrolyzer megawattPemStack() {
    Stream inlet = waterFeed();
    inlet.setTemperature(353.15, "K");
    inlet.run();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.setTechnology(ElectrolyzerTechnology.PEM);
    el.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
    el.sizeStack(1.0e6);
    return el;
  }

  @Test
  void testSizeStackSetsGeometryAndRatedPower() {
    Electrolyzer el = megawattPemStack();
    assertEquals(1.0e6, el.getRatedPower(), 1e-6);
    assertTrue(el.getStackActiveArea() > 0.0, "stack area should be sized");
    assertEquals(2.0, el.getNominalCurrentDensity(), 1e-9);
  }

  @Test
  void testPowerModeProducesHydrogen() {
    Electrolyzer el = megawattPemStack();
    el.setAvailablePower(0.5e6);
    el.run();
    assertEquals(Electrolyzer.OperationMode.POWER, el.getOperationMode());
    assertTrue(el.getHydrogenOutStream().getFlowRate("mole/sec") > 0.0);
    // Rectifier=1, aux=0 => stack power tracks the available power.
    assertEquals(0.5e6, el.getStackPower(), 0.5e6 * 0.03);
    assertTrue(el.getStackCurrent() > 0.0);
  }

  @Test
  void testPowerModeRequiresGeometry() {
    Stream inlet = waterFeed();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.setAvailablePower(1.0e5);
    assertThrows(IllegalStateException.class, el::run);
  }

  @Test
  void testStandbyBelowMinimumLoad() {
    Electrolyzer el = megawattPemStack();
    el.setMinimumLoadFraction(0.2);
    el.setAvailablePower(0.1e6); // 10% < 20% minimum
    el.run();
    assertTrue(el.isStandby());
    assertEquals(0.0, el.getHydrogenOutStream().getFlowRate("mole/sec"), 1e-9);
    assertEquals(0.0, el.getStackPower(), 1e-9);
  }

  @Test
  void testCurtailmentAboveRatedPower() {
    Electrolyzer el = megawattPemStack();
    el.setAvailablePower(1.5e6); // 50% above 1 MW rated
    el.run();
    assertTrue(!el.isStandby());
    assertEquals(0.5e6, el.getCurtailedPower(), 1.0e6 * 0.03);
    assertEquals(1.0e6, el.getStackPower(), 1.0e6 * 0.03);
  }

  @Test
  void testRampRateLimitInTransient() {
    Electrolyzer el = megawattPemStack();
    el.setMaxRampRate(0.1); // 10% of rated per second => 100 kW/s
    el.setCalculateSteadyState(false);
    el.setAvailablePower(1.0e6);
    java.util.UUID id = java.util.UUID.randomUUID();

    el.runTransient(1.0, id);
    assertEquals(0.1e6, el.getOperatingPower(), 1.0);
    double h2Step1 = el.getHydrogenOutStream().getFlowRate("mole/sec");

    el.runTransient(1.0, id);
    assertEquals(0.2e6, el.getOperatingPower(), 1.0);
    double h2Step2 = el.getHydrogenOutStream().getFlowRate("mole/sec");

    assertTrue(h2Step2 > h2Step1, "hydrogen output should rise as the stack ramps up");
  }

  @Test
  void testBalanceOfPlantRaisesSystemEnergy() {
    Electrolyzer el = megawattPemStack();
    el.setRectifierEfficiency(0.95);
    el.setAuxiliaryLoadFraction(0.05);
    el.setAvailablePower(0.5e6);
    el.run();
    double stackSec = el.getSpecificEnergyConsumption_kWh_per_kg_H2();
    double systemSec = el.getSystemSpecificEnergyConsumption_kWh_per_kg_H2();
    assertTrue(systemSec > stackSec, "system SEC must exceed stack SEC with BoP losses");
    assertTrue(el.getSystemPower() > el.getStackPower());
    // The system power budget should match the available power.
    assertEquals(0.5e6, el.getSystemPower(), 0.5e6 * 0.02);
  }

  @Test
  void testWasteHeatAndWaterConsumption() {
    Electrolyzer el = megawattPemStack();
    el.setAvailablePower(0.5e6);
    el.run();
    // Operating voltage above thermoneutral => positive waste heat.
    assertTrue(el.getWasteHeat() > 0.0);
    double h2 = el.getHydrogenOutStream().getFlowRate("mole/sec");
    assertEquals(h2, el.getWaterConsumption("mole/sec"), 1e-9);
    assertEquals(h2 * 0.018015, el.getWaterConsumption("kg/sec"), 1e-9);
    assertEquals(h2 * 0.018015 * 3600.0, el.getWaterConsumption("kg/hr"), 1e-6);
  }

  @Test
  void testHydrogenDeliveryPressureAndCompression() {
    Electrolyzer el = megawattPemStack();
    el.setAvailablePower(0.5e6);
    el.setHydrogenDeliveryPressure(30.0); // inlet at 1 bara
    el.run();
    assertEquals(30.0, el.getHydrogenOutStream().getPressure("bara"), 1e-6);
    assertTrue(el.getHydrogenCompressionPower() > 0.0);
    // Oxygen stays at inlet pressure.
    assertEquals(1.0, el.getOxygenOutStream().getPressure("bara"), 1e-6);
  }

  @Test
  void testBackwardCompatibleDefaults() {
    Stream inlet = waterFeed();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.run();
    // Default water-feed mode and no BoP losses => system power equals stack power.
    assertEquals(Electrolyzer.OperationMode.WATER_FEED, el.getOperationMode());
    assertEquals(el.getStackPower(), el.getSystemPower(), 1e-6);
    assertTrue(!el.isStandby());
  }
}
