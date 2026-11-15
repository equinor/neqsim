package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies basic thermodynamic consistency of the Burgoyne–Nielsen–Stanko PR model for a gas-phase system using
 * ThermodynamicModelTest utilities.
 */
public class SystemBnsEosModelTest {
  static SystemBnsEos system;
  static ThermodynamicModelTest modelTest;

  @BeforeAll
  public static void setUp() {
    system = new SystemBnsEos();
    system.setTemperature(300.0);
    system.setPressure(50.0);
    system.setAssociatedGas(false);
    system.setRelativeDensity(0.65);
    system.setComposition(0.02, 0.0, 0.01, 0.0);
    system.useVolumeCorrection(true);
    system.setMixingRule(12);
    new ThermodynamicOperations(system).TPflash();
    system.init(0);
    system.init(3);
    modelTest = new ThermodynamicModelTest(system);
    modelTest.setMaxError(1e-8);
    system.initProperties();
  }

  @Test
  public void testFugacityCoefficients() {
    assertTrue(modelTest.checkFugacityCoefficients());
  }

  @Test
  public void testFugacityCoefficientsDP() {
    PhaseInterface phase = system.getPhase(0);
    double derivative = 0.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      derivative += phase.getComponent(i).getx() * phase.getComponent(i).getdfugdp();
    }
    assertEquals((phase.getZvolcorr() - 1.0) / phase.getPressure(), derivative, 1e-10);
  }

  @Test
  public void testFugacityCoefficientsDT() {
    assertTrue(modelTest.checkFugacityCoefficientsDT());
  }
}
