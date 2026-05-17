package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for GasPseudoPressure calculator.
 *
 * <p>All correlation-based methods now use bara/Kelvin. EOS methods already used bara.</p>
 */
class GasPseudoPressureTest {

  // ========== HALL-YARBOROUGH Z-FACTOR ==========

  @Test
  void testHallYarboroughIdealGasLimit() {
    double z = GasPseudoPressure.hallYarboroughZ(1.5, 0.001);
    assertEquals(1.0, z, 0.05, "Z-factor should approach 1.0 at very low Ppr");
  }

  @Test
  void testHallYarboroughTypicalConditions() {
    double z = GasPseudoPressure.hallYarboroughZ(1.5, 2.0);
    assertTrue(z > 0.7 && z < 1.0,
        "Z at Tpr=1.5, Ppr=2.0 should be between 0.7 and 1.0, got " + z);
  }

  @Test
  void testHallYarboroughHighPressure() {
    double z = GasPseudoPressure.hallYarboroughZ(2.0, 10.0);
    assertTrue(z > 0.5 && z < 2.5,
        "Z at Tpr=2.0, Ppr=10.0 should be realistic, got " + z);
  }

  @Test
  void testHallYarboroughZeroPressure() {
    double z = GasPseudoPressure.hallYarboroughZ(1.5, 0.0);
    assertEquals(1.0, z, 1e-10, "Z at zero pressure should be 1.0");
  }

  // ========== CORRELATION-BASED PSEUDOPRESSURE (bara / K) ==========

  @Test
  void testCorrelationPseudoPressurePositive() {
    // 206.84 bara (~3000 psia), ref 1.01325 bara (~14.696 psia), 366.48 K (~200 F)
    double mP = GasPseudoPressure.calculateFromCorrelation(
        206.84, 1.01325, 366.48, 0.65, 16.04);
    assertTrue(mP > 0, "Pseudopressure should be positive when p > pRef");
  }

  @Test
  void testCorrelationPseudoPressureZeroRange() {
    double mP = GasPseudoPressure.calculateFromCorrelation(
        68.95, 68.95, 366.48, 0.65, 16.04);
    assertEquals(0.0, mP, 1e-6, "Pseudopressure over zero range should be zero");
  }

  @Test
  void testDeltaPseudoPressureSymmetry() {
    double dm1 = GasPseudoPressure.deltaPseudoPressure(
        206.84, 68.95, 366.48, 0.65, 16.04);
    double dm2 = GasPseudoPressure.deltaPseudoPressure(
        68.95, 206.84, 366.48, 0.65, 16.04);
    assertEquals(dm1, dm2, dm1 * 0.01, "Delta pseudopressure should be symmetric");
  }

  @Test
  void testCorrelationPseudoPressureIncreases() {
    double m1 = GasPseudoPressure.calculateFromCorrelation(
        68.95, 1.01325, 366.48, 0.65, 16.04);
    double m2 = GasPseudoPressure.calculateFromCorrelation(
        137.9, 1.01325, 366.48, 0.65, 16.04);
    double m3 = GasPseudoPressure.calculateFromCorrelation(
        206.84, 1.01325, 366.48, 0.65, 16.04);

    assertTrue(m2 > m1, "m(137.9 bara) should be > m(68.95 bara)");
    assertTrue(m3 > m2, "m(206.84 bara) should be > m(137.9 bara)");
  }

  @Test
  void testCorrelationReasonableMagnitude() {
    // For methane at typical conditions, pseudopressure in bara^2/cP
    // ~1e8 psia^2/cP / (14.5^2) ~ 4.75e5 bara^2/cP
    double mP = GasPseudoPressure.calculateFromCorrelation(
        206.84, 1.01325, 366.48, 0.65, 16.04);
    assertTrue(mP > 1e3, "Pseudopressure should be > 1e3 bara^2/cP, got " + mP);
    assertTrue(mP < 1e8, "Pseudopressure should be < 1e8 bara^2/cP, got " + mP);
  }

  // ========== EOS-BASED PSEUDOPRESSURE ==========

  @Test
  void testEOSBasedPseudoPressure() {
    neqsim.thermo.system.SystemInterface gas =
        new neqsim.thermo.system.SystemSrkEos(373.15, 100.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");

    GasPseudoPressure calc = new GasPseudoPressure(gas);
    double mP = calc.calculate(200.0, 10.0);

    assertTrue(mP > 0, "EOS pseudopressure should be positive");
  }

  @Test
  void testEOSPseudoPressureProfile() {
    neqsim.thermo.system.SystemInterface gas =
        new neqsim.thermo.system.SystemSrkEos(373.15, 100.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    GasPseudoPressure calc = new GasPseudoPressure(gas);
    calc.setNumberOfSteps(50);

    double[][] profile = calc.pseudoPressureProfile(10.0, 300.0, 10);

    assertEquals(10, profile[0].length);
    assertEquals(10, profile[1].length);
    assertEquals(10.0, profile[0][0], 1e-6);
    assertEquals(300.0, profile[0][9], 1e-6);
    assertEquals(0.0, profile[1][0], 1e-6, "mP at reference pressure should be 0");

    for (int i = 1; i < profile[1].length; i++) {
      assertTrue(profile[1][i] >= profile[1][i - 1],
          "Pseudopressure should increase with pressure");
    }
  }

  @Test
  void testNumberOfStepsValidation() {
    neqsim.thermo.system.SystemInterface gas =
        new neqsim.thermo.system.SystemSrkEos(373.15, 100.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    GasPseudoPressure calc = new GasPseudoPressure(gas);
    assertThrows(IllegalArgumentException.class, () -> calc.setNumberOfSteps(5));
  }

  @Test
  void testPressureUnitConversion() {
    neqsim.thermo.system.SystemInterface gas =
        new neqsim.thermo.system.SystemSrkEos(373.15, 100.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    GasPseudoPressure calc = new GasPseudoPressure(gas);
    double mBara = calc.calculate(200.0, 10.0);
    double mPsia = calc.calculate(200.0 * 14.5038, 10.0 * 14.5038, "psia");

    assertTrue(mBara > 0);
    assertTrue(mPsia > 0);
  }
}
