package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;

/** Public wrappers must preserve failed-saturation outcomes. */
class SaturationFailurePropagationTest {
  @Test
  void warmMixtureBubbleTemperatureRecoversNontrivialEquilibrium() throws IsNaNException {
    double referenceTemperature = Double.NaN;
    for (double initialTemperature : new double[] {140.0, 298.15, 400.0}) {
      SystemSrkEos system = new SystemSrkEos(initialTemperature, 5.0);
      system.addComponent("methane", 0.5);
      system.addComponent("ethane", 0.5);
      system.setMixingRule("classic");
      ThermodynamicOperations operations = new ThermodynamicOperations(system);
      operations.TPflash();
      operations.bubblePointTemperatureFlash();
      system.init(1);
      assertTrue(system.getTemperature() > 140.0 && system.getTemperature() < 200.0);
      if (Double.isFinite(referenceTemperature)) {
        assertEquals(referenceTemperature, system.getTemperature(), 1.0e-4);
      }
      referenceTemperature = system.getTemperature();
      assertTrue(Math.abs(system.getPhase(0).getComponent(0).getx() - system.getPhase(1).getComponent(0).getx()) > 0.1,
          "Reject the trivial equal-composition root");
      for (int component = 0; component < 2; component++) {
        double vaporFugacity = system.getPhase(0).getComponent(component).getx()
            * system.getPhase(0).getComponent(component).getFugacityCoefficient();
        double liquidFugacity = system.getPhase(1).getComponent(component).getx()
            * system.getPhase(1).getComponent(component).getFugacityCoefficient();
        assertEquals(liquidFugacity, vaporFugacity, 1.0e-6);
      }
    }
  }

  @Test
  void mixtureBubblePressureRecoversFromLowAndHighGuesses() throws IsNaNException {
    for (double initialPressure : new double[] {1.0, 50.0, 300.0}) {
      SystemInterface system = new SystemPrEos(373.15, initialPressure);
      system.addComponent("methane", 0.5);
      system.addComponent("n-heptane", 0.5);
      system.setMixingRule("classic");
      new ThermodynamicOperations(system).bubblePointPressureFlash(false);
      assertEquals(145.85, system.getPressure(), 0.1);
      assertMixtureEquilibrium(system);
    }
  }

  @Test
  void mixtureDewTemperatureRecoversFromWarmAndColdGuesses() throws IsNaNException {
    for (double initialTemperature : new double[] {180.0, 240.0, 298.15}) {
      SystemInterface system = new SystemSrkEos(initialTemperature, 50.0);
      system.addComponent("methane", 0.85);
      system.addComponent("ethane", 0.10);
      system.addComponent("propane", 0.05);
      system.setMixingRule("classic");
      new ThermodynamicOperations(system).dewPointTemperatureFlash();
      assertEquals(244.87, system.getTemperature(), 0.1);
      assertMixtureEquilibrium(system);
    }
  }

  /** Verify phase separation and component fugacity equality, not just a finite result. */
  private void assertMixtureEquilibrium(SystemInterface system) {
    system.init(1);
    assertTrue(Math.abs(system.getPhase(0).getComponent(0).getx() - system.getPhase(1).getComponent(0).getx()) > 0.1);
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      double vaporFugacity = system.getPhase(0).getComponent(component).getx()
          * system.getPhase(0).getComponent(component).getFugacityCoefficient();
      double liquidFugacity = system.getPhase(1).getComponent(component).getx()
          * system.getPhase(1).getComponent(component).getFugacityCoefficient();
      assertEquals(liquidFugacity, vaporFugacity, 1.0e-6);
    }
  }

  @Test
  void highPressureMixtureStillRejectsTrivialSaturationRoot() {
    SystemSrkEos system = new SystemSrkEos(298.15, 500.0);
    system.addComponent("methane", 0.5);
    system.addComponent("ethane", 0.5);
    system.setMixingRule("classic");
    assertThrows(IsNaNException.class, () -> new ThermodynamicOperations(system).bubblePointTemperatureFlash());
  }

  @Test
  void booleanBubbleWrapperDoesNotSwallowSupercriticalFailure() {
    SystemSrkEos system = new SystemSrkEos(300.0, 10.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    IsNaNException failure = assertThrows(IsNaNException.class,
        () -> new ThermodynamicOperations(system).bubblePointPressureFlash(false));
    assertTrue(failure.getCause() instanceof IllegalStateException);
  }

  @Test
  void validBubbleAndDewCalculationsStillReturnFinitePositivePressure() throws IsNaNException {
    for (boolean bubble : new boolean[] {false, true}) {
      SystemSrkEos system = new SystemSrkEos(150.0, 10.0);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      ThermodynamicOperations operations = new ThermodynamicOperations(system);
      if (bubble) {
        operations.bubblePointPressureFlash(false);
      } else {
        operations.dewPointPressureFlash();
      }
      assertTrue(Double.isFinite(system.getPressure()) && system.getPressure() > 0);
      assertEquals(10.47, system.getPressure(), 0.2);
    }
  }
}
