package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PhaseGEUnifacPSRKTest {
  static PhaseGEUnifacPSRK p;

  @BeforeEach
  void setUp() {
    p = new PhaseGEUnifacPSRK();
  }

  @Test
  void testAddcomponent() {
    Assertions.assertEquals(0, p.getNumberOfComponents());

    p.addComponent("ethane", 0, 0, 0);
    Assertions.assertEquals(1, p.getNumberOfComponents());

    p.addComponent("methane", 0, 0, 1);
    Assertions.assertEquals(2, p.getNumberOfComponents());
  }
}
