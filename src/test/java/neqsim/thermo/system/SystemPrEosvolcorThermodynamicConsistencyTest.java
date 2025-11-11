package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemPrEosvolcorThermodynamicConsistencyTest extends neqsim.NeqSimTest {

  private static ThermodynamicModelTest prepareSystem(SystemInterface system, double[] cTValues) {
    applyVolumeTranslation(system, cTValues);
    system.init(3);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(3);

    applyVolumeTranslation(system, cTValues);
    system.init(3);
    system.initProperties();
    verifyTranslationDerivatives(system, cTValues);

    return new ThermodynamicModelTest(system);
  }

  private static void applyVolumeTranslation(SystemInterface system, double[] cTValues) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      for (int compIndex = 0; compIndex < phase.getNumberOfComponents(); compIndex++) {
        ComponentInterface component = phase.getComponent(compIndex);
        double translation = component.getVolumeCorrection();
        component.setVolumeCorrection(translation);
        if (cTValues != null && compIndex < cTValues.length) {
          component.setVolumeCorrectionT(cTValues[compIndex]);
        }
      }
    }
  }

  private static void verifyTranslationDerivatives(SystemInterface system, double[] cTValues) {
    if (cTValues == null) {
      return;
    }

    for (int compIndex = 0;
        compIndex < Math.min(system.getPhase(0).getNumberOfComponents(), cTValues.length);
        compIndex++) {
      double expectedDerivative = cTValues[compIndex];
      assertEquals(expectedDerivative, system.getComponent(compIndex).getVolumeCorrectionT(), 1e-12,
          "Volume-translation temperature derivative should match the configured value");
      assertTrue(Math.abs(expectedDerivative) > 0.0,
          "Configured translation derivative should be non-zero to exercise the implementation");

      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        double actualDerivative =
            system.getPhase(phaseIndex).getComponent(compIndex).getVolumeCorrectionT();
        assertEquals(expectedDerivative, actualDerivative, 1e-12,
            "All phases must share the same translation derivative for a component");
      }
    }
  }

  private static void assertThermodynamicConsistency(ThermodynamicModelTest modelTest) {
    assertTrue(modelTest.checkFugacityCoefficients());
    assertTrue(modelTest.checkFugacityCoefficientsDP());
    assertTrue(modelTest.checkFugacityCoefficientsDT());
    assertTrue(modelTest.checkFugacityCoefficientsDn());
    assertTrue(modelTest.checkFugacityCoefficientsDn2());
  }

  @Test
  @DisplayName("Thermodynamic consistency for a pure component with temperature-dependent translation")
  public void testPureComponentThermodynamicConsistency() {
    SystemInterface system = new SystemPrEosvolcor(230.0, 30.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");

    ThermodynamicModelTest modelTest = prepareSystem(system, new double[] {5.0e-5});
    assertThermodynamicConsistency(modelTest);
  }

  @Test
  @DisplayName("Thermodynamic consistency for a mixture with temperature-dependent translation")
  public void testMixtureThermodynamicConsistency() {
    SystemInterface system = new SystemPrEosvolcor(298.15, 30.0);
    system.addComponent("methane", 0.7);
    system.addComponent("ethane", 0.2);
    system.addComponent("n-butane", 0.1);
    system.setMixingRule("classic");

    ThermodynamicModelTest modelTest =
        prepareSystem(system, new double[] {4.0e-5, 3.5e-5, 2.5e-5});
    assertThermodynamicConsistency(modelTest);
  }
}
