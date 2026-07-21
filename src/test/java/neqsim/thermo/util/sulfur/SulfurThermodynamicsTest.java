package neqsim.thermo.util.sulfur;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link SulfurThermodynamics}. */
public class SulfurThermodynamicsTest {
  /** Sulfur boils at 444.61 C at one atmosphere in the reference table. */
  @Test
  void testNormalBoilingPointAnchor() {
    assertEquals(1.01325, SulfurThermodynamics.calculateVapourPressureBar(717.76),
        1.0e-8);
  }

  /** Dew-point inversion must recover the original temperature. */
  @Test
  void testDewPointIsInverseOfVapourPressure() {
    double temperatureK = 423.15;
    double pressureBar = SulfurThermodynamics.calculateVapourPressureBar(temperatureK);
    assertEquals(temperatureK,
        SulfurThermodynamics.calculateDewPointTemperatureK(pressureBar), 1.0e-6);
  }

  /** The equilibrium distribution must normalize and show the expected S8-to-S2 transition. */
  @Test
  void testAllotropeDistribution() {
    Map<String, Double> cold =
        SulfurThermodynamics.calculateAllotropeMoleFractions(450.0, 1.0);
    Map<String, Double> hot =
        SulfurThermodynamics.calculateAllotropeMoleFractions(1200.0, 1.0);

    double coldSum = 0.0;
    double hotSum = 0.0;
    for (Double value : cold.values()) {
      coldSum += value;
    }
    for (Double value : hot.values()) {
      hotSum += value;
    }

    assertEquals(1.0, coldSum, 1.0e-12);
    assertEquals(1.0, hotSum, 1.0e-12);
    assertTrue(cold.get("S8") > 0.80, "S8 should dominate cold sulfur vapour");
    assertTrue(hot.get("S2") > 0.80, "S2 should dominate hot sulfur vapour");
  }

  /** Pressure must influence the molecular association state. */
  @Test
  void testPressureDependence() {
    double lowPressureAtoms =
        SulfurThermodynamics.calculateMeanSulfurAtomsPerMolecule(800.0, 0.01);
    double highPressureAtoms =
        SulfurThermodynamics.calculateMeanSulfurAtomsPerMolecule(800.0, 10.0);
    assertTrue(highPressureAtoms > lowPressureAtoms,
        "Higher pressure should favour larger sulfur allotropes");
  }

  /** Inputs beyond the traceable vapour-pressure data range are rejected. */
  @Test
  void testVapourPressureValidityRange() {
    assertThrows(IllegalArgumentException.class,
        () -> SulfurThermodynamics.calculateVapourPressureBar(300.0));
    assertThrows(IllegalArgumentException.class,
        () -> SulfurThermodynamics.calculateDewPointTemperatureK(500.0));
  }
}
