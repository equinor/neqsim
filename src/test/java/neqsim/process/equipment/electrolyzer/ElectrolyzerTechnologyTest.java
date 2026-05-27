package neqsim.process.equipment.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Sanity tests for {@link ElectrolyzerTechnology} — defaults must remain in textbook ranges so
 * downstream cost and energy estimates stay consistent with public benchmarks (IRENA 2022, IEA
 * 2023).
 */
class ElectrolyzerTechnologyTest {

  @Test
  void testPemDefaults() {
    ElectrolyzerTechnology t = ElectrolyzerTechnology.PEM;
    assertEquals(1.8, t.getDefaultCellVoltage(), 1e-9);
    assertEquals(2.0, t.getDefaultCurrentDensity(), 1e-9);
    assertEquals(80.0, t.getDefaultTemperatureC(), 1e-9);
    assertEquals(30.0, t.getDefaultPressureBara(), 1e-9);
    assertTrue(t.getDefaultFaradaicEfficiency() > 0.5 && t.getDefaultFaradaicEfficiency() < 0.8);
  }

  @Test
  void testAlkalineDefaults() {
    ElectrolyzerTechnology t = ElectrolyzerTechnology.ALKALINE;
    assertTrue(t.getDefaultCurrentDensity() < ElectrolyzerTechnology.PEM.getDefaultCurrentDensity(),
        "Alkaline operates at lower current density than PEM");
    assertTrue(t.getDefaultPressureBara() < ElectrolyzerTechnology.PEM.getDefaultPressureBara(),
        "Alkaline pressure default should be below PEM");
  }

  @Test
  void testSoecDefaults() {
    ElectrolyzerTechnology t = ElectrolyzerTechnology.SOEC;
    assertTrue(t.getDefaultTemperatureC() > 500.0, "SOEC must be high-temperature");
    assertTrue(t.getDefaultCellVoltage() < 1.5,
        "SOEC cell voltage benefits from thermo-neutral operation");
  }

  @Test
  void testAllValuesPresent() {
    // Defensive: ensure no defaults were left at zero by an enum reorder.
    for (ElectrolyzerTechnology t : ElectrolyzerTechnology.values()) {
      assertTrue(t.getDefaultCellVoltage() > 0.0, t + " cellVoltage");
      assertTrue(t.getDefaultCurrentDensity() > 0.0, t + " currentDensity");
      assertTrue(t.getDefaultTemperatureC() > 0.0, t + " T");
      assertTrue(t.getDefaultPressureBara() > 0.0, t + " P");
      assertTrue(t.getDefaultFaradaicEfficiency() > 0.0 && t.getDefaultFaradaicEfficiency() <= 1.0,
          t + " eta_F");
    }
  }
}
