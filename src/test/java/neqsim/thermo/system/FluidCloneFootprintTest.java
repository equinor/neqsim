package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies that fitting the per-component derivative arrays to the component count keeps fluids fully functional.
 */
public class FluidCloneFootprintTest {
  /**
   * Creates a three-component SRK test fluid.
   *
   * @return the test fluid
   */
  private static SystemInterface createFluid() {
    SystemInterface fluid = new SystemSrkEos(283.15, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("propane", 0.15);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Work arrays are fitted to the component count and stay readable and writable beyond it. */
  @Test
  public void derivativeArraysFollowComponentCount() {
    SystemInterface fluid = createFluid();
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    ComponentEos component = (ComponentEos) fluid.getPhase(0).getComponent(0);
    assertEquals(3, component.Aij.length);
    assertEquals(3, component.dfugdx.length);
    // Entries beyond the fitted size read as zero and can still be written.
    assertEquals(0.0, component.getdfugdn(10), 0.0);
    component.setdfugdn(10, 1.5);
    assertEquals(1.5, component.getdfugdn(10), 0.0);
  }

  /** A component can be added to a clone whose arrays were fitted to a smaller component count. */
  @Test
  public void componentCanBeAddedToInitializedClone() {
    SystemInterface fluid = createFluid();
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    double referenceDerivative = fluid.getPhase(0).getComponent(0).getdfugdn(1);

    SystemInterface extended = fluid.clone();
    extended.addComponent("CO2", 0.02);
    extended.setMixingRule("classic");
    new ThermodynamicOperations(extended).TPflash();
    extended.init(3);
    assertEquals(4, extended.getPhase(0).getNumberOfComponents());
    assertTrue(Double.isFinite(extended.getPhase(0).getComponent(3).getdfugdn(0)));
    assertTrue(Double.isFinite(extended.getPhase(0).getComponent(0).getdfugdn(3)));

    // The source fluid is untouched by the extension of its clone.
    fluid.init(3);
    assertEquals(referenceDerivative, fluid.getPhase(0).getComponent(0).getdfugdn(1),
        1.0e-12 * Math.abs(referenceDerivative));
  }

  /** A thermodynamics-only clone carries the full thermodynamic state of its source. */
  @Test
  public void thermoOnlyCloneReproducesThermodynamicState() {
    SystemInterface fluid = createFluid();
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();

    SystemInterface workCopy = fluid.cloneThermoOnly();
    new ThermodynamicOperations(workCopy).TPflash();
    workCopy.init(2);
    fluid.init(2);
    assertEquals(fluid.getEnthalpy(), workCopy.getEnthalpy(), 1.0e-9 * Math.abs(fluid.getEnthalpy()));
    assertEquals(fluid.getBeta(), workCopy.getBeta(), 1.0e-12);

    // A regular clone still carries its physical-property models; a work copy rebuilds them on demand.
    assertNotNull(fluid.clone().getPhase(0).getPhysicalProperties());
    workCopy.initProperties();
    assertTrue(workCopy.getPhase(0).getPhysicalProperties().getViscosity() > 0.0);
  }
}
