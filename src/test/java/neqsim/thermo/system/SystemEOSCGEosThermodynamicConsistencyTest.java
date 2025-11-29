package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies EOS-CG thermodynamic consistency using the generic model test harness.
 */
public class SystemEOSCGEosThermodynamicConsistencyTest extends neqsim.NeqSimTest {
  private static SystemInterface system;
  private static ThermodynamicModelTest modelTest;

  @BeforeAll
  public static void setUp() {
    system = new SystemEOSCGEos(305.0, 80.0);
    system.addComponent("methane", 0.55);
    system.addComponent("CO2", 0.4);
    system.addComponent("nitrogen", 0.05);
    system.init(0);
    system.init(3);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(3);
    system.initProperties();

    modelTest = new ThermodynamicModelTest(system);
    modelTest.setMaxError(1.0e2);
  }

  @Test
  @DisplayName("EOS-CG fugacity coefficients satisfy consistency relation")
  public void testFugacityCoefficientsConsistency() {
    assertTrue(modelTest.checkFugacityCoefficients());
  }

  @Test
  @DisplayName("EOS-CG pressure derivative fugacities are consistent")
  public void testFugacityCoefficientsDP() {
    assertTrue(modelTest.checkFugacityCoefficientsDP());
  }

  @Test
  @DisplayName("EOS-CG temperature derivative fugacities are consistent")
  public void testFugacityCoefficientsDT() {
    assertTrue(modelTest.checkFugacityCoefficientsDT());
  }

  @Test
  @DisplayName("EOS-CG composition derivative fugacities are consistent")
  public void testFugacityCoefficientsDn() {
    assertTrue(modelTest.checkFugacityCoefficientsDn());
  }
}
