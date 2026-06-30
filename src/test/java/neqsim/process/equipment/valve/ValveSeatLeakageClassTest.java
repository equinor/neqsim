package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for ValveSeatLeakageClass.
 *
 * @author ESOL
 */
public class ValveSeatLeakageClassTest {

  @Test
  void testAllConstantsHaveDesignationAndDescription() {
    for (ValveSeatLeakageClass leakageClass : ValveSeatLeakageClass.values()) {
      assertNotNull(leakageClass.getStandardDesignation(), "Designation should not be null");
      assertFalse(leakageClass.getStandardDesignation().isEmpty(), "Designation should not be empty");
      assertNotNull(leakageClass.getDescription(), "Description should not be null");
      assertFalse(leakageClass.getDescription().isEmpty(), "Description should not be empty");
    }
  }

  @Test
  void testFciClassVIIsEssentiallyZeroLeakage() {
    assertTrue(ValveSeatLeakageClass.FCI_70_2_CLASS_VI.isEssentiallyZeroLeakage());
  }

  @Test
  void testFciClassIvIsNotEssentiallyZeroLeakage() {
    assertFalse(ValveSeatLeakageClass.FCI_70_2_CLASS_IV.isEssentiallyZeroLeakage());
  }

  @Test
  void testEstimateFciClassVLeakRateMatchesFormula() {
    double diffPressurePsi = 100.0;
    double portDiameterInch = 2.0;
    double diffPressurePa = diffPressurePsi * 6894.757293168;
    double portDiameterM = portDiameterInch * 0.0254;

    double expectedMlPerMin = 5.0e-4 * portDiameterInch * diffPressurePsi;
    double actualMlPerMin = ValveSeatLeakageClass.estimateFciClassVLeakRate(diffPressurePa, portDiameterM);

    assertEquals(expectedMlPerMin, actualMlPerMin, expectedMlPerMin * 1.0e-6);
  }

  @Test
  void testEstimateFciClassVLeakRateInvalidInputsThrow() {
    assertThrows(IllegalArgumentException.class, () -> ValveSeatLeakageClass.estimateFciClassVLeakRate(0.0, 0.05));
    assertThrows(IllegalArgumentException.class, () -> ValveSeatLeakageClass.estimateFciClassVLeakRate(1.0e5, 0.0));
    assertThrows(IllegalArgumentException.class, () -> ValveSeatLeakageClass.estimateFciClassVLeakRate(-1.0e5, 0.05));
  }
}
