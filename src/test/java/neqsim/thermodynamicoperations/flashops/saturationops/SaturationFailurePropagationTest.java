package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;

/** Public wrappers must preserve failed-saturation outcomes. */
class SaturationFailurePropagationTest {
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
