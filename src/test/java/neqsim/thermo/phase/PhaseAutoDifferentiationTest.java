package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PhaseAutoDifferentiationTest {

  static PhaseAutoDifferentiation phase = null;
  static DerivativeStructure function = null;

  @BeforeAll
  public static void setUp() {
    phase = new PhaseAutoDifferentiation();
    phase.addcomponent("methane", 1.0, 1.0, 0);
    phase.setTemperature(300.0);
    phase.setMolarVolume(100.0);
    phase.numberOfMolesInPhase = 1.0;
    phase.setTotalVolume(100.0);
    phase.init(1, 1, 0, 1, 1);
    phase.init(1, 1, 1, 1, 1);
    function = phase.getFunctionStruc();

  }

  @Test
  void testDFdV() {
    assertEquals(3.0, function.getPartialDerivative(0, 1), 1e-6);
  }

  @Test
  void testDFdT() {
    assertEquals(1.0, function.getPartialDerivative(1, 0), 1e-6);
  }

  @Test
  void testDFdVdV() {
    assertEquals(0.0, function.getPartialDerivative(0, 2), 1e-6);
  }

  @Test
  void testGetF() {
    assertEquals(600.0, function.getValue(), 1e-6);
  }


}
