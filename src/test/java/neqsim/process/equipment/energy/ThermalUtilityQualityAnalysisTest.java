package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.UtilityLevel;

class ThermalUtilityQualityAnalysisTest {
  @Test
  void screensHeatingAndCoolingTemperatureGrades() {
    UtilityEnergyBus steam = new UtilityEnergyBus("LP steam", UtilityLevel.LOW_PRESSURE_STEAM, 425.0, 383.0);
    assertTrue(ThermalUtilityQualityAnalysis.canServeProcessTemperature(steam, 400.0, 10.0));
    assertFalse(ThermalUtilityQualityAnalysis.canServeProcessTemperature(steam, 420.0, 10.0));

    UtilityEnergyBus coolingWater = new UtilityEnergyBus("cooling water", UtilityLevel.COOLING_WATER, 293.0, 313.0);
    assertTrue(ThermalUtilityQualityAnalysis.canServeProcessTemperature(coolingWater, 310.0, 10.0));
    assertFalse(ThermalUtilityQualityAnalysis.canServeProcessTemperature(coolingWater, 300.0, 10.0));
  }

  @Test
  void reportsPositiveExergyFactor() {
    UtilityEnergyBus steam = new UtilityEnergyBus("LP steam", UtilityLevel.LOW_PRESSURE_STEAM, 425.0, 383.0);
    assertTrue(ThermalUtilityQualityAnalysis.getEffectiveTemperature(steam) > 383.0);
    assertTrue(ThermalUtilityQualityAnalysis.getExergyFactor(steam, 298.15) > 0.0);
  }
}
