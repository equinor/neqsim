package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos1978;

/**
 * Verifies that a cloned component's attractive term follows the clone rather than the component it was originally
 * created for.
 *
 * <p>
 * EOS regression workflows clone a base fluid and then adjust critical properties on the clone. If the cloned
 * attractive term keeps pointing at the original component, alpha(T) is evaluated with the untuned critical temperature
 * and the tuning is silently discarded.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentEosCloneAttractiveTermTest {
  private static final double TEMPERATURE = 440.05;

  /**
   * Build a two-component gas condensate style fluid with one heavy pseudo-component.
   *
   * @return an initialised fluid
   */
  private SystemInterface createFluid() {
    SystemInterface fluid = new SystemPrEos1978(TEMPERATURE, 400.0);
    fluid.getCharacterization().setTBPModel("PedersenPR");
    fluid.addComponent("methane", 85.0);
    fluid.addTBPfraction("C12", 15.0, 161.0 / 1000.0, 0.804);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Changing the critical temperature on a clone must change the alpha function of that clone.
   */
  @Test
  void testSetTCOnCloneUpdatesAlpha() {
    SystemInterface fluid = createFluid();
    SystemInterface clone = fluid.clone();

    ComponentEosInterface original = (ComponentEosInterface) fluid.getPhase(0).getComponent(1);
    ComponentEosInterface cloned = (ComponentEosInterface) clone.getPhase(0).getComponent(1);

    double alphaBefore = cloned.getAttractiveTerm().alpha(TEMPERATURE);
    cloned.setTC(cloned.getTC() * 0.9);
    double alphaAfter = cloned.getAttractiveTerm().alpha(TEMPERATURE);

    assertNotEquals(alphaBefore, alphaAfter, 1.0e-9,
        "alpha must respond to a critical temperature change on the clone");

    // Reference value of the Peng-Robinson alpha function at the tuned critical temperature.
    double m = cloned.getAttractiveTerm().getm();
    double reduced = TEMPERATURE / cloned.getTC();
    double expected = Math.pow(1.0 + m * (1.0 - Math.sqrt(reduced)), 2.0);
    assertEquals(expected, alphaAfter, 1.0e-9);

    // The original fluid must be untouched.
    assertEquals(alphaBefore, original.getAttractiveTerm().alpha(TEMPERATURE), 1.0e-9);
  }

  /**
   * Tuning a clone must give the same thermodynamic state as tuning the fluid it was cloned from.
   */
  @Test
  void testTuningACloneMatchesTuningTheOriginal() {
    SystemInterface tunedDirectly = createFluid();
    scaleCriticalTemperature(tunedDirectly);

    SystemInterface tunedOnClone = createFluid().clone();
    scaleCriticalTemperature(tunedOnClone);

    double direct = fugacityCoefficient(tunedDirectly);
    double onClone = fugacityCoefficient(tunedOnClone);
    double reference = fugacityCoefficient(createFluid());

    assertEquals(direct, onClone, 1.0e-10,
        "tuning applied to a clone must have the same effect as tuning the original");
    assertTrue(Math.abs(direct - reference) > 1.0e-6,
        "the tuning must actually change the fugacity coefficient, tuned=" + direct + " untuned=" + reference);
  }

  /**
   * Lower the pseudo-component critical temperature on every phase object of a fluid.
   *
   * @param fluid the fluid to tune
   */
  private void scaleCriticalTemperature(SystemInterface fluid) {
    for (int i = 0; i < fluid.getPhases().length; i++) {
      if (fluid.getPhases()[i] == null) {
        continue;
      }
      ComponentEosInterface comp = (ComponentEosInterface) fluid.getPhases()[i].getComponent(1);
      comp.setTC(comp.getTC() * 0.9);
    }
    fluid.init(0);
  }

  /**
   * Fugacity coefficient of the heavy pseudo-component at fixed temperature and pressure.
   *
   * @param fluid the fluid to evaluate
   * @return the fugacity coefficient
   */
  private double fugacityCoefficient(SystemInterface fluid) {
    fluid.setTemperature(TEMPERATURE);
    fluid.setPressure(400.0);
    fluid.init(0);
    fluid.init(3);
    return fluid.getPhase(0).getComponent(1).getFugacityCoefficient();
  }
}
