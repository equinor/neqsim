package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PhaseSrkEosTest {
  static PhaseSrkEos p;

  @BeforeEach
  void setUp() {
    p = new PhaseSrkEos();
  }

  @Test
  void testAddcomponent() {
    Assertions.assertEquals(0, p.getNumberOfComponents());

    p.addComponent("ethane", 0, 0, 0);
    Assertions.assertEquals(1, p.getNumberOfComponents());
    Assertions.assertTrue(p.hasComponent("ethane"));
    Assertions.assertTrue(p.hasComponent("C2"));
    Assertions.assertFalse(p.hasComponent("C2", false));
    try {
      p.addComponent("methane", 0, 0, 0);
    } catch (Exception e) {
      // Do nothing.
    }
    Assertions.assertEquals(1, p.getNumberOfComponents());

    p.addComponent("methane", 0, 0, 1);
    Assertions.assertEquals(2, p.getNumberOfComponents());
    String[] d = p.getComponentNames();
    Assertions.assertTrue(d[0].equals("ethane"));
    Assertions.assertTrue(d[1].equals("methane"));
  }

  @Test
  void testClone() {
    PhaseSrkEos p2 = null;

    for (PhaseType pt : PhaseType.values()) {
      // System.out.println("Set phase type to " + pt);
      p.setType(pt);
      p2 = p.clone();
      Assertions.assertEquals(p.getType(), p2.getType());
    }

    p.setType(PhaseType.GAS);
    p2.setType(PhaseType.LIQUID);

    Assertions.assertNotEquals(p.getType(), p2.getType());
  }
}
