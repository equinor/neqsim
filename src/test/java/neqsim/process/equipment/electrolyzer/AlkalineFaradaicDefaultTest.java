package neqsim.process.equipment.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.Fluid;

/**
 * Independent nominal-reference and operating-mode checks for the alkaline part of issue 4682.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class AlkalineFaradaicDefaultTest extends neqsim.NeqSimTest {
  /**
   * Verifies the extrapolated nominal reference and its application by the native unit.
   *
   * @param usePolarization whether to use the existing coarse polarization model
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nominalReferenceClosesFaradayBalance(boolean usePolarization) {
    // Ulleberg (2003), Eq. (9), Table 3: PHOEBUS, 80 C, 7 bar.
    // f1 is in (mA/cm2)^2; convert the API current density before squaring.
    double referenceCurrentDensity = 400.0;
    double expectedEfficiency = 0.96 * referenceCurrentDensity * referenceCurrentDensity
        / (250.0 + referenceCurrentDensity * referenceCurrentDensity);
    assertEquals(expectedEfficiency, ElectrolyzerTechnology.ALKALINE.getDefaultFaradaicEfficiency(), 1e-14);
    assertTrue(expectedEfficiency > 0.95 && expectedEfficiency < 0.96,
        "nominal estimate must approach the published 0.96 high-current limit from below");

    Stream inlet = new Stream("alkaline water", new Fluid().create("water"));
    inlet.setTemperature(353.15, "K");
    inlet.setPressure(7.0, "bara");
    inlet.setFlowRate(1.0, "mole/sec");
    inlet.run();
    Electrolyzer power = new Electrolyzer("alkaline power", inlet);
    power.setTechnology(ElectrolyzerTechnology.ALKALINE);
    if (usePolarization) {
      power.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.ALKALINE));
    }
    power.sizeStack(1.0e6);
    power.setAvailablePower(1.0e6);
    power.run();

    double voltage = power.getCellVoltage();
    assertEquals(usePolarization ? 1.7064353992195167 : 1.85, voltage, 1e-10,
        "equipment guide must distinguish the fixed-voltage and I-V screening cases");
    double expectedHydrogen = 1.0e6 * expectedEfficiency / (2.0 * 96485.3329 * voltage);
    double expectedSec = 2.0 * 96485.3329 * voltage / (expectedEfficiency * 0.002016 * 3.6e6);
    assertEquals(usePolarization ? 47.33636892177723 : 51.31883840744359, expectedSec, 1e-8,
        "documented electrical SEC on the native hydrogen molar-mass basis");
    assertEquals(0.4, power.getCurrentDensity(), 1e-10);
    assertEquals(expectedHydrogen, power.getHydrogenOutStream().getFlowRate("mole/sec"), 1e-9);
    assertEquals(expectedHydrogen / 2.0, power.getOxygenOutStream().getFlowRate("mole/sec"), 1e-9);
    assertEquals(expectedHydrogen, inlet.getFlowRate("mole/sec"), 1e-9);
    assertEquals(expectedSec, power.getSpecificEnergyConsumption_kWh_per_kg_H2(), 1e-9);
    assertEquals(expectedSec, power.getSystemSpecificEnergyConsumption_kWh_per_kg_H2(), 1e-9);
    assertEquals(0.0, power.getMassBalance("kg/sec"), expectedHydrogen * 2.0e-6);

    Stream matchedFeed = new Stream("matched water", new Fluid().create("water"));
    matchedFeed.setTemperature(353.15, "K");
    matchedFeed.setPressure(7.0, "bara");
    matchedFeed.setFlowRate(expectedHydrogen, "mole/sec");
    matchedFeed.run();
    Electrolyzer water = new Electrolyzer("alkaline water feed", matchedFeed);
    water.setTechnology(ElectrolyzerTechnology.ALKALINE);
    water.setStackActiveArea(power.getStackActiveArea());
    if (usePolarization) {
      water.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.ALKALINE));
    }
    water.run();
    assertEquals(power.getStackCurrent(), water.getStackCurrent(), 1e-6);
    assertEquals(power.getStackPower(), water.getStackPower(), 1e-6);
    assertEquals(expectedSec, water.getSpecificEnergyConsumption_kWh_per_kg_H2(), 1e-9);
    if (!usePolarization) {
      assertEquals(1.85, voltage, 1e-12);
      assertTrue(expectedSec > 51.0 && expectedSec < 52.0, "documented fixed-voltage screening SEC");
    }
  }
}
