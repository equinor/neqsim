package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for ZFactorCorrelations.
 *
 * <p>
 * Core Tpr/Ppr methods are dimensionless; convenience methods use Kelvin and bara.
 * </p>
 */
class ZFactorCorrelationsTest {

  // ========== HALL-YARBOROUGH ==========

  @Test
  void testHallYarboroughIdealGasLimit() {
    double z = ZFactorCorrelations.hallYarborough(1.5, 0.001);
    assertEquals(1.0, z, 0.05, "Z should approach 1.0 at very low Ppr");
  }

  @Test
  void testHallYarboroughTypicalConditions() {
    double z = ZFactorCorrelations.hallYarborough(1.5, 2.0);
    assertTrue(z > 0.7 && z < 1.0, "Z at Tpr=1.5, Ppr=2.0 should be ~0.85, got " + z);
  }

  @Test
  void testHallYarboroughHighPressure() {
    double z = ZFactorCorrelations.hallYarborough(2.0, 10.0);
    assertTrue(z > 0.5 && z < 2.5, "Z at high pressure should be realistic, got " + z);
  }

  @Test
  void testHallYarboroughZeroPressure() {
    double z = ZFactorCorrelations.hallYarborough(1.5, 0.0);
    assertEquals(1.0, z, 1e-10, "Z at zero pressure should be 1.0");
  }

  // ========== DRANCHUK-ABOU-KASSEM ==========

  @Test
  void testDAKIdealGasLimit() {
    double z = ZFactorCorrelations.dranchukAbouKassem(1.5, 0.001);
    assertEquals(1.0, z, 0.05, "DAK Z should approach 1.0 at low Ppr");
  }

  @Test
  void testDAKTypicalConditions() {
    double z = ZFactorCorrelations.dranchukAbouKassem(1.5, 2.0);
    assertTrue(z > 0.7 && z < 1.0, "DAK Z at Tpr=1.5, Ppr=2.0 should be ~0.85, got " + z);
  }

  @Test
  void testDAKConsistentWithHallYarborough() {
    double zHY = ZFactorCorrelations.hallYarborough(1.5, 3.0);
    double zDAK = ZFactorCorrelations.dranchukAbouKassem(1.5, 3.0);
    // Both should agree within ~5% for typical conditions
    double relDiff = Math.abs(zHY - zDAK) / zHY;
    assertTrue(relDiff < 0.10, "HY and DAK should agree within 10%, diff=" + relDiff * 100 + "%");
  }

  // ========== PAPAY ==========

  @Test
  void testPapayTypicalConditions() {
    double z = ZFactorCorrelations.papay(1.5, 2.0);
    assertTrue(z > 0.5 && z < 1.2, "Papay Z should be reasonable, got " + z);
  }

  @Test
  void testPapayExplicitAndFast() {
    // Papay is explicit so should always return a value
    for (double ppr = 0.5; ppr <= 10.0; ppr += 0.5) {
      double z = ZFactorCorrelations.papay(1.5, ppr);
      assertTrue(z > 0.0, "Papay Z should always be positive, got " + z + " at Ppr=" + ppr);
    }
  }

  // ========== CONVENIENCE METHODS (Kelvin / bara) ==========

  @Test
  void testZFactorSutton() {
    // 137.9 bara (2000 psia), 366.48 K (200 F), gammaG=0.65
    double z = ZFactorCorrelations.zFactorSutton(137.9, 366.48, 0.65);
    assertTrue(z > 0.5 && z < 1.5, "Z-factor should be realistic, got " + z);
  }

  @Test
  void testZFactorSourGas() {
    double z = ZFactorCorrelations.zFactorSourGas(137.9, 366.48, 0.75, 0.10, 0.05);
    assertTrue(z > 0.5 && z < 1.5, "Sour gas Z-factor should be realistic, got " + z);
  }

  @Test
  void testGasDensity() {
    // Gas density at 137.9 bara, 366.48 K, gammaG=0.65, MW=16.04
    double rho = ZFactorCorrelations.gasDensity(137.9, 366.48, 0.65, 16.04);
    // Natural gas density at ~2000 psia, 200 F: roughly 50-150 kg/m3
    assertTrue(rho > 10 && rho < 300, "Gas density should be 10-300 kg/m3, got " + rho);
  }

  @Test
  void testGasDensityIncreasesWithPressure() {
    double rho1 = ZFactorCorrelations.gasDensity(50.0, 366.48, 0.65, 16.04);
    double rho2 = ZFactorCorrelations.gasDensity(200.0, 366.48, 0.65, 16.04);
    assertTrue(rho2 > rho1, "Gas density should increase with pressure");
  }

  @Test
  void testGasFVFFromZ() {
    double bg = ZFactorCorrelations.gasFVFFromZ(137.9, 366.48, 0.65);
    // Gas FVF at reservoir conditions: typically 0.003 - 0.02 res m3/std m3
    assertTrue(bg > 0.001 && bg < 0.1, "Gas FVF should be 0.001-0.1 res m3/std m3, got " + bg);
  }

  @Test
  void testGasFVFDecreasesWithPressure() {
    double bg1 = ZFactorCorrelations.gasFVFFromZ(50.0, 366.48, 0.65);
    double bg2 = ZFactorCorrelations.gasFVFFromZ(200.0, 366.48, 0.65);
    assertTrue(bg2 < bg1, "Gas FVF should decrease with pressure");
  }

  // ========== COMPARE ALL ==========

  @Test
  void testCompareAllContainsThreeCorrelations() {
    Map<String, Double> results = ZFactorCorrelations.compareAll(1.5, 3.0);
    assertEquals(3, results.size());
    assertTrue(results.containsKey("Hall-Yarborough"));
    assertTrue(results.containsKey("Dranchuk-Abou-Kassem"));
    assertTrue(results.containsKey("Papay"));

    for (Map.Entry<String, Double> entry : results.entrySet()) {
      assertTrue(entry.getValue() > 0.0,
          entry.getKey() + " Z should be positive, got " + entry.getValue());
    }
  }
}
