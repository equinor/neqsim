package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.thermo.component.ComponentGePitzer;

public class PhasePitzerTest {
  PhasePitzer phase;

  @BeforeEach
  void setUp() {
    phase = new PhasePitzer();
    phase.setTemperature(298.15);
    phase.setPressure(1.0);
    phase.addComponent("water", 55.5, 55.5, 0);
    phase.addComponent("Na+", 1.0, 1.0, 1);
    phase.addComponent("Cl-", 1.0, 1.0, 2);
    phase.setBinaryParameters(1, 2, 0.0765, 0.2664, 0.00127);
  }

  @Test
  void testGamma() {
    double gammaNa = ((ComponentGePitzer) phase.getComponent(1)).getGamma(phase,
        phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure(), phase.getType());
    Assertions.assertTrue(gammaNa > 0.5 && gammaNa < 1.0);
  }
}

