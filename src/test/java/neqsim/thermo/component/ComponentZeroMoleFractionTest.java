package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Regression tests for exact-zero phase fractions, issue 3998.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ComponentZeroMoleFractionTest {
  /**
   * Creates the binary fluid from the issue reproducer.
   *
   * @return initialized binary fluid
   */
  private SystemInterface fluid() {
    SystemInterface fluid = new SystemPrEos(250.0, 19.5);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("n-butane", 1.0);
    fluid.setMixingRule(2);
    fluid.init(0);
    return fluid;
  }

  /** Both signs of zero must remove a stale phase fraction. */
  @Test
  void zeroOverwritesPreviousFraction() {
    ComponentInterface component = fluid().getPhase(0).getComponent(0);
    component.setx(0.5);
    component.setx(0.0);
    assertEquals(0.0, component.getx(), 0.0);
    component.setx(0.5);
    component.setx(-0.0);
    assertEquals(0.0, component.getx(), 0.0);
  }

  /** Recycle-style clipping must publish a normalized phase. */
  @Test
  void clampedCompositionDoesNotRetainAbsentComponent() {
    SystemInterface fluid = fluid();
    for (int phase = 0; phase < 2; phase++) {
      fluid.getPhase(phase).getComponent(0).setx(1.0);
      fluid.getPhase(phase).getComponent(1).setx(0.0);
      assertEquals(1.0, fluid.getPhase(phase).getComponent(0).getx() + fluid.getPhase(phase).getComponent(1).getx(),
          0.0);
    }
  }

  /** Existing invalid-input guards and upper bound remain unchanged. */
  @Test
  void otherSetterGuardsRemainCompatible() {
    ComponentInterface component = fluid().getComponent(0);
    component.setx(0.25);
    component.setx(Double.NaN);
    component.setx(Double.POSITIVE_INFINITY);
    component.setx(Double.NEGATIVE_INFINITY);
    assertEquals(0.25, component.getx(), 0.0);
    component.setx(-0.1);
    assertEquals(1e-50, component.getx(), 0.0);
    component.setx(6.0);
    assertEquals(5.0, component.getx(), 0.0);
  }
}
