package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PhaseWaxTest {
  @Test
  void testAddcomponent() {
    PhaseWax p = new PhaseWax();
    Assertions.assertEquals(0, p.getNumberOfComponents());

    p.addcomponent("ethane", 0, 0, 0);
    Assertions.assertEquals(1, p.getNumberOfComponents());

    p.addcomponent("methane", 0, 0, 0);
    Assertions.assertEquals(2, p.getNumberOfComponents());
  }
}
