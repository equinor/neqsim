package neqsim.process.equipment.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ElectrolyzerIVCharacteristic}. Validates against textbook electrochemistry
 * benchmarks: reversible voltage at 25 &deg;C, PEM operating-point voltage at 80 &deg;C and 2
 * A/cm&sup2;, and qualitative ordering between technologies.
 */
class ElectrolyzerIVCharacteristicTest {

  @Test
  void testReversibleVoltageAt25C() {
    ElectrolyzerIVCharacteristic iv = new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM);
    double eRev = iv.getReversibleVoltage(298.15);
    assertEquals(1.229, eRev, 1e-3);
  }

  @Test
  void testReversibleVoltageDecreasesWithTemperature() {
    ElectrolyzerIVCharacteristic iv = new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM);
    double eRev25 = iv.getReversibleVoltage(298.15);
    double eRev80 = iv.getReversibleVoltage(353.15);
    assertTrue(eRev80 < eRev25,
        "Reversible voltage should decrease with temperature, got " + eRev25 + " -> " + eRev80);
  }

  @Test
  void testPemAtOperatingPoint() {
    ElectrolyzerIVCharacteristic iv = new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM);
    double v = iv.getCellVoltage(2.0, 353.15);
    // Textbook PEM stack: ~1.85 V at j=2 A/cm2 and 80 C. Allow ±10% engineering tolerance.
    assertTrue(v > 1.6 && v < 2.05,
        "PEM cell voltage at 2 A/cm2, 80 C should be ~1.85 V, got " + v);
  }

  @Test
  void testAlkalineAtOperatingPoint() {
    ElectrolyzerIVCharacteristic iv =
        new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.ALKALINE);
    double v = iv.getCellVoltage(0.4, 353.15);
    // Textbook alkaline: ~1.85 V at j=0.4 A/cm2, 80 C.
    assertTrue(v > 1.7 && v < 2.05,
        "Alkaline cell voltage at 0.4 A/cm2, 80 C should be ~1.85 V, got " + v);
  }

  @Test
  void testSoecBelowPemAtSameCurrentDensity() {
    ElectrolyzerIVCharacteristic pem = new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM);
    ElectrolyzerIVCharacteristic soec =
        new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.SOEC);
    double pemVoltage = pem.getCellVoltage(1.0, 353.15);
    double soecVoltage = soec.getCellVoltage(1.0, 1073.15);
    assertTrue(soecVoltage < pemVoltage,
        "SOEC voltage at 800 C should be below PEM voltage at 80 C, got " + soecVoltage + " vs "
            + pemVoltage);
  }

  @Test
  void testBelowExchangeCurrentReturnsReversible() {
    ElectrolyzerIVCharacteristic iv = new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM);
    double v = iv.getCellVoltage(1e-5, 298.15);
    // Below j0 we should be at (or below) E_rev — the kinetic correction is suppressed.
    assertEquals(iv.getReversibleVoltage(298.15), v, 1e-6);
  }

  @Test
  void testValidation() {
    ElectrolyzerIVCharacteristic iv = new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM);
    assertThrows(IllegalArgumentException.class, () -> iv.getCellVoltage(-0.1, 353.15));
    assertThrows(IllegalArgumentException.class, () -> iv.getReversibleVoltage(0.0));
    assertThrows(IllegalArgumentException.class, () -> iv.setTafelSlope(0.0));
    assertThrows(IllegalArgumentException.class, () -> iv.setExchangeCurrentDensity(0.0));
    assertThrows(IllegalArgumentException.class, () -> iv.setAreaSpecificResistance(-0.1));
    assertThrows(IllegalArgumentException.class, () -> new ElectrolyzerIVCharacteristic(null));
  }
}
