package neqsim.process.equipment.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent Faraday-law, inventory and cross-mode regressions for issue 4681.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyzerFaradaicBalanceTest extends neqsim.NeqSimTest {
  /** Charge per mole of electrons, C/mol. */
  private static final double FARADAY = 96485.3329;
  /** Fixed test cell voltage, V. */
  private static final double VOLTAGE = 1.85;
  /** Nominal stack power, W. */
  private static final double POWER = 18.0e6;

  /**
   * Creates a pure-water feed at the issue's operating temperature and pressure.
   *
   * @param molarFlow feed rate in mol/s
   * @return initialized inlet stream
   */
  private Stream feed(double molarFlow) {
    SystemInterface water = new Fluid().create("water");
    Stream inlet = new Stream("water", water);
    inlet.setTemperature(353.15, "K");
    inlet.setPressure(7.0, "bara");
    inlet.setFlowRate(molarFlow, "mole/sec");
    inlet.run();
    return inlet;
  }

  /**
   * Creates a stack with explicitly supplied current efficiency and voltage.
   *
   * @param inlet pure-water inlet
   * @param efficiency current efficiency
   * @return configured stack
   */
  private Electrolyzer stack(Stream inlet, double efficiency) {
    Electrolyzer unit = new Electrolyzer("stack", inlet);
    unit.setCellVoltage(VOLTAGE);
    unit.setFaradaicEfficiency(efficiency);
    unit.setStackActiveArea(1.0e8);
    return unit;
  }

  /**
   * Checks water atoms, product stoichiometry and molecular-weight rounding independently.
   *
   * @param inlet water inlet
   * @param unit calculated stack
   * @param hydrogen expected hydrogen production in mol/s
   */
  private void assertInventory(Stream inlet, Electrolyzer unit, double hydrogen) {
    double water = inlet.getFlowRate("mole/sec");
    double actualHydrogen = unit.getHydrogenOutStream().getFlowRate("mole/sec");
    double oxygen = unit.getOxygenOutStream().getFlowRate("mole/sec");
    assertEquals(hydrogen, actualHydrogen, 1e-8);
    assertEquals(2.0 * water, 2.0 * actualHydrogen, 1e-8, "hydrogen atom balance");
    assertEquals(water, 2.0 * oxygen, 1e-8, "oxygen atom balance");
    assertEquals(water, unit.getWaterConsumption("mole/sec"), 1e-8);
    assertEquals(water * 0.018015, unit.getWaterConsumption("kg/sec"), 1e-8);
    // The component database rounds each species' molar mass independently.
    assertEquals(0.0, unit.getMassBalance("kg/sec"), Math.max(1e-12, water * 2.0e-6));
  }

  /**
   * Both modes must describe the same physical operating point, including BoP losses.
   *
   * @param efficiency current efficiency
   */
  @ParameterizedTest
  @ValueSource(doubles = {1.0, 0.98, 0.62})
  void modesCloseInventoriesAndAgree(double efficiency) {
    Stream powerFeed = feed(50.420770894856545);
    Electrolyzer power = stack(powerFeed, efficiency);
    power.setRectifierEfficiency(0.95);
    power.setAuxiliaryLoadFraction(0.05);
    double systemPower = POWER / 0.95 + 0.05 * POWER;
    power.setAvailablePower(systemPower);
    power.run();
    double expectedHydrogen = POWER * efficiency / (2.0 * FARADAY * VOLTAGE);
    assertInventory(powerFeed, power, expectedHydrogen);

    Stream waterFeed = feed(expectedHydrogen);
    Electrolyzer water = stack(waterFeed, efficiency);
    water.setRectifierEfficiency(0.95);
    water.setAuxiliaryLoadFraction(0.05);
    water.run();
    assertInventory(waterFeed, water, expectedHydrogen);
    assertEquals(POWER / VOLTAGE, water.getStackCurrent(), 1e-7);
    assertEquals(power.getStackCurrent(), water.getStackCurrent(), 1e-7);
    assertEquals(POWER, water.getStackPower(), 1e-6);
    assertEquals(systemPower, water.getSystemPower(), 1e-6);
    assertEquals(power.getCurrentDensity(), water.getCurrentDensity(), 1e-10);
    assertEquals(power.getSystemPower(), water.getSystemPower(), 1e-6);
    double specificEnergy = 2.0 * FARADAY * VOLTAGE / (efficiency * 0.002016 * 3.6e6);
    assertEquals(specificEnergy, power.getSpecificEnergyConsumption_kWh_per_kg_H2(), 1e-8);
    assertEquals(specificEnergy, water.getSpecificEnergyConsumption_kWh_per_kg_H2(), 1e-8);
    assertEquals(353.15, waterFeed.getTemperature("K"), 1e-8);
    assertEquals(7.0, waterFeed.getPressure("bara"), 1e-8);
  }

  /** Verifies matched current density and voltage with the nonlinear I-V model. */
  @Test
  void polarizationModelAgreesAcrossModes() {
    Stream powerFeed = feed(1.0);
    Electrolyzer power = stack(powerFeed, 0.98);
    power.setTechnology(ElectrolyzerTechnology.PEM);
    power.setFaradaicEfficiency(0.98);
    power.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
    power.sizeStack(1.0e6);
    power.setAvailablePower(0.5e6);
    power.run();
    double hydrogen = power.getHydrogenOutStream().getFlowRate("mole/sec");
    Stream waterFeed = feed(hydrogen);
    Electrolyzer water = stack(waterFeed, 0.98);
    water.setTechnology(ElectrolyzerTechnology.PEM);
    water.setFaradaicEfficiency(0.98);
    water.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
    water.setStackActiveArea(power.getStackActiveArea());
    water.run();
    assertInventory(waterFeed, water, hydrogen);
    assertEquals(power.getCurrentDensity(), water.getCurrentDensity(), 1e-10);
    assertEquals(power.getCellVoltage(), water.getCellVoltage(), 1e-10);
    assertEquals(power.getStackPower(), water.getStackPower(), 1e-6);
  }

  /** Checks that current losses appear as heat on the declared fixed-HHV screening basis. */
  @Test
  void electricalLossesAreIncludedInWasteHeat() {
    Electrolyzer unit = stack(feed(2.0), 0.62);
    unit.run();
    double chemicalPower = 2.0 * 2.0 * FARADAY * 1.481;
    assertEquals(unit.getStackPower(), chemicalPower + unit.getWasteHeat(), 1e-7);
    unit.setFaradaicEfficiency(1.0);
    unit.run();
    assertEquals((VOLTAGE - 1.481) * unit.getStackCurrent(), unit.getWasteHeat(), 1e-7);
    unit.setCellVoltage(1.30);
    unit.run();
    assertEquals(0.0, unit.getWasteHeat(), 0.0);
  }

  /** Repeated runs and changes in power, mode, efficiency and inlet flow must refresh outputs. */
  @Test
  void repeatedRunsDoNotRetainWaterOrProductInventory() {
    Stream inlet = feed(2.0);
    Electrolyzer unit = stack(inlet, 0.62);
    unit.setAvailablePower(POWER);
    unit.run();
    double hydrogen = POWER * 0.62 / (2.0 * FARADAY * VOLTAGE);
    assertInventory(inlet, unit, hydrogen);
    unit.run();
    assertInventory(inlet, unit, hydrogen);
    unit.setAvailablePower(0.0);
    unit.run();
    assertInventory(inlet, unit, 0.0);
    assertEquals(0.0, unit.getStackPower(), 0.0);
    assertEquals(0.0, unit.getWasteHeat(), 0.0);
    unit.setAvailablePower(POWER / 2.0);
    unit.setFaradaicEfficiency(0.98);
    unit.run();
    assertInventory(inlet, unit, POWER * 0.98 / (4.0 * FARADAY * VOLTAGE));
    unit.setOperationMode(Electrolyzer.OperationMode.WATER_FEED);
    inlet.setFlowRate(3.0, "mole/sec");
    inlet.run();
    unit.run();
    assertInventory(inlet, unit, 3.0);
    assertEquals(3.0, inlet.getFlowRate("mole/sec"), 1e-10);
  }

  /** Impure feeds must be rejected before power mode changes upstream flow. */
  @Test
  void rejectsUnbalancedMixedFeedsBeforeMutation() {
    SystemInterface mixture = new SystemSrkEos(353.15, 7.0);
    mixture.addComponent("water", 2.0);
    mixture.addComponent("nitrogen", 0.1);
    mixture.setMixingRule("classic");
    Stream inlet = new Stream("mixed feed", mixture);
    inlet.run();
    double originalFlow = inlet.getFlowRate("mole/sec");
    Electrolyzer unit = stack(inlet, 0.98);
    unit.setAvailablePower(POWER);
    assertThrows(IllegalArgumentException.class, unit::run);
    assertEquals(originalFlow, inlet.getFlowRate("mole/sec"), 0.0);
  }
}
