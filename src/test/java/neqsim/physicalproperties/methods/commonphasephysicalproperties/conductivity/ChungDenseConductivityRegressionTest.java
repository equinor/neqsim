package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Physical and lifecycle regressions for the selectable dense Chung conductivity model.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ChungDenseConductivityRegressionTest {
  /**
   * Builds and flashes a reproducible mixture without depending on stored transport-property state.
   *
   * @param amount total amount in mol
   * @param reverse whether to reverse component insertion order
   * @return flashed system at 350 K and 100 bara
   */
  private SystemInterface mixture(double amount, boolean reverse) {
    SystemInterface fluid = new SystemSrkEos(350.0, 100.0);
    if (reverse) {
      fluid.addComponent("ethane", 0.25 * amount);
      fluid.addComponent("methane", 0.75 * amount);
    } else {
      fluid.addComponent("methane", 0.75 * amount);
      fluid.addComponent("ethane", 0.25 * amount);
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    return fluid;
  }

  /**
   * Calls the model directly so a property-package exception handler cannot hide a failure.
   *
   * @param fluid initialized system
   * @return conductivity in W/(m K)
   */
  private double conductivity(SystemInterface fluid) {
    PhysicalProperties properties = fluid.getPhase(0).getPhysicalProperties();
    properties.setConductivityModel("Chung-dense");
    return properties.getConductivityModel().calcConductivity();
  }

  /** Verifies that conductivity is intensive over six orders of magnitude in amount. */
  @Test
  void amountDoesNotChangeConductivity() {
    double reference = conductivity(mixture(1.0, false));
    assertEquals(reference, conductivity(mixture(0.001, false)), reference * 1e-10);
    assertEquals(reference, conductivity(mixture(1000.0, false)), reference * 1e-10);
  }

  /** Verifies conformal mixing is invariant to component insertion order. */
  @Test
  void insertionOrderDoesNotChangeConductivity() {
    assertEquals(conductivity(mixture(1.0, false)), conductivity(mixture(1.0, true)), 1e-12);
  }

  /** Verifies conformal mixing reduces to the pure-component fast path for an inactive second component. */
  @Test
  void zeroFractionComponentPreservesPureLimit() {
    SystemInterface pure = new SystemSrkEos(350.0, 100.0);
    pure.addComponent("methane", 1.0);
    pure.setMixingRule("classic");
    new ThermodynamicOperations(pure).TPflash();
    pure.init(3);
    double reference = conductivity(pure);

    SystemInterface withInactiveComponent = new SystemSrkEos(350.0, 100.0);
    withInactiveComponent.addComponent("methane", 1.0);
    withInactiveComponent.addComponent("ethane", 0.0);
    withInactiveComponent.setMixingRule("classic");
    new ThermodynamicOperations(withInactiveComponent).TPflash();
    withInactiveComponent.init(3);
    assertEquals(2, withInactiveComponent.getPhase(0).getNumberOfComponents());
    assertEquals(0.0, withInactiveComponent.getPhase(0).getComponent("ethane").getx(), 0.0);
    assertEquals(reference, conductivity(withInactiveComponent), reference * 1e-12);
  }

  /** Guards the missing dense-fluid term with nitrogen's expected positive pressure dependence. */
  @Test
  void nitrogenDenseContributionIsPresent() {
    SystemInterface gas = new SystemSrkEos(300.0, 0.001);
    gas.addComponent("nitrogen", 1.0);
    gas.setMixingRule("classic");
    new ThermodynamicOperations(gas).TPflash();
    gas.init(3);
    double dilute = conductivity(gas);
    gas.setPressure(100.0);
    new ThermodynamicOperations(gas).TPflash();
    gas.init(3);
    double dense = conductivity(gas);
    assertTrue(dilute > 0.024 && dilute < 0.028, "Dilute nitrogen: " + dilute);
    assertTrue(dense > 1.15 * dilute, "Dense contribution missing: " + dilute + " -> " + dense);
    assertTrue(dense < 0.040, "Dense nitrogen: " + dense);
  }

  /** Verifies state changes and cloning do not retain composition or heat-capacity caches. */
  @Test
  void repeatedAndClonedEvaluationsFollowState() {
    SystemInterface original = mixture(1.0, false);
    double initial = conductivity(original);
    SystemInterface copy = original.clone();
    copy.setTemperature(450.0);
    new ThermodynamicOperations(copy).TPflash();
    copy.init(3);
    double changed = conductivity(copy);
    assertTrue(Math.abs(changed - initial) > 0.001);
    assertEquals(initial, conductivity(original), 1e-14);
    assertEquals(changed, conductivity(copy), 1e-14);
  }
}
