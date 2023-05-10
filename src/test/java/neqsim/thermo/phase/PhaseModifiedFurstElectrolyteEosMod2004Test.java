package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PhaseModifiedFurstElectrolyteEosMod2004Test {
  static PhaseModifiedFurstElectrolyteEosMod2004 p;

  @BeforeEach
  void setUp() {
    p = new PhaseModifiedFurstElectrolyteEosMod2004();
  }

  @Test
  void testAddcomponent() {
    Assertions.assertEquals(0, p.getNumberOfComponents());

    p.addComponent("ethane", 0, 0, 0);
    Assertions.assertEquals(1, p.getNumberOfComponents());

    p.addComponent("methane", 0, 0, 0);
    Assertions.assertEquals(2, p.getNumberOfComponents());
  }
}
