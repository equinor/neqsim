package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Verifies stored composition limits without altering positive trace fractions on read.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ComponentMoleFractionLimitsTest {
  /** Verifies trace-positive fractions and exact zero survive round trips. */
  @Test
  void preservesTraceFractionsAndExactZero() {
    ComponentSrk component = new ComponentSrk("methane", 1.0, 1.0, 0);
    component.setx(1.0e-80);
    assertEquals(1.0e-80, component.getx(), 0.0);
    component.setx(0.0);
    assertEquals(0.0, component.getx(), 0.0);
  }

  /** Verifies finite input limits and rejection of non-finite assignments. */
  @Test
  void boundsAssignmentsAndRetainsLastFiniteValue() {
    ComponentSrk component = new ComponentSrk("methane", 1.0, 1.0, 0);
    component.setx(-1.0);
    assertEquals(1.0e-50, component.getx(), 0.0);
    component.setx(6.0);
    assertEquals(5.0, component.getx(), 0.0);
    component.setx(0.25);
    component.setx(Double.NaN);
    component.setx(Double.POSITIVE_INFINITY);
    assertEquals(0.25, component.getx(), 0.0);
  }
}
