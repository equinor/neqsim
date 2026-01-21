package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Additional tests for SystemBnsEos to validate improvements.
 */
public class SystemBnsEosImprovementTest {
  @Test
  public void testInputValidationNegativeComposition() {
    SystemBnsEos sys = new SystemBnsEos();

    // Test negative CO2 mole fraction
    assertThrows(IllegalArgumentException.class, () -> {
      sys.setComposition(-0.01, 0.0, 0.0, 0.0);
    });

    // Test negative H2S mole fraction
    assertThrows(IllegalArgumentException.class, () -> {
      sys.setComposition(0.0, -0.01, 0.0, 0.0);
    });
  }

  @Test
  public void testInputValidationExcessiveComposition() {
    SystemBnsEos sys = new SystemBnsEos();

    // Test sum of mole fractions exceeding 1.0
    assertThrows(IllegalArgumentException.class, () -> {
      sys.setComposition(0.5, 0.3, 0.3, 0.1); // Sum = 1.2
    });
  }

  @Test
  public void testInputValidationNegativeRelativeDensity() {
    SystemBnsEos sys = new SystemBnsEos();

    // Test negative relative density
    assertThrows(IllegalArgumentException.class, () -> {
      sys.setRelativeDensity(-0.1);
    });

    // Test zero relative density
    assertThrows(IllegalArgumentException.class, () -> {
      sys.setRelativeDensity(0.0);
    });
  }

  @Test
  public void testValidCompositionSettings() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(300.0);
    sys.setPressure(10.0);
    sys.setAssociatedGas(false);
    sys.setRelativeDensity(0.65);

    // Test valid composition that sums to less than 1.0
    sys.setComposition(0.02, 0.0, 0.01, 0.0); // Sum = 0.03, HC = 0.97
    sys.useVolumeCorrection(true);
    sys.setMixingRule(12);

    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();

    // Should complete without errors
    double density = sys.getDensity("kg/m3");
    assertEquals(7.750037498921, density, 0.1);
  }

  @Test
  public void testBoundaryComposition() {
    SystemBnsEos sys = new SystemBnsEos();

    // Test composition where all components are zero (pure HC)
    sys.setComposition(0.0, 0.0, 0.0, 0.0);

    // Test composition where sum equals exactly 1.0 (no HC)
    sys.setComposition(0.5, 0.3, 0.15, 0.05);
  }

  @Test
  public void testSystemCloning() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(350.0);
    sys.setPressure(50.0);
    sys.setRelativeDensity(0.75);
    sys.setComposition(0.05, 0.02, 0.03, 0.01);

    SystemBnsEos cloned = sys.clone();

    assertEquals(sys.getTemperature(), cloned.getTemperature(), 1e-10);
    assertEquals(sys.getPressure(), cloned.getPressure(), 1e-10);
  }
}
