package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.Phase;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verify deferred transport initialization of inactive multiphase templates.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SystemThermoMultiphaseInitializationTest {
  /** Verify enabling a phase search does not calculate transport properties for an inactive phase. */
  @Test
  void inactiveTemplateDefersTransportUntilAccess() {
    SystemInterface fluid = createFluid();
    fluid.init(0);
    fluid.setMultiPhaseCheck(true);
    Phase template = (Phase) fluid.getPhases()[2];
    assertNull(template.physicalPropertyHandler, "An inactive phase needs no transport calculation");
    template.init(fluid.getTotalNumberOfMoles(), fluid.getNumberOfComponents(), 1, template.getType(), 1.0);
    assertTrue(template.getPhysicalProperties().getDensity() > 0.0);
    assertTrue(template.getPhysicalProperties().getViscosity() > 0.0);
  }

  /** Verify lazy and eager templates produce the same gas, oil and aqueous equilibrium and properties. */
  @Test
  void lazyAndEagerTemplatesProduceIdenticalThreePhaseResults() {
    SystemInterface lazy = createFluid();
    SystemInterface eager = createFluid();
    lazy.setMultiPhaseCheck(true);
    eager.setMultiPhaseCheck(true);
    eager.getPhases()[2].initPhysicalProperties();
    new ThermodynamicOperations(lazy).TPflash();
    new ThermodynamicOperations(eager).TPflash();
    lazy.initProperties();
    eager.initProperties();
    assertEquals(3, lazy.getNumberOfPhases());
    assertEquals(eager.getNumberOfPhases(), lazy.getNumberOfPhases());
    for (int phase = 0; phase < lazy.getNumberOfPhases(); phase++) {
      assertEquals(eager.getPhase(phase).getType(), lazy.getPhase(phase).getType());
      assertEquals(eager.getBeta(phase), lazy.getBeta(phase), 1.0e-12);
      assertEquals(eager.getPhase(phase).getDensity(), lazy.getPhase(phase).getDensity(), 1.0e-9);
      assertEquals(eager.getPhase(phase).getViscosity(), lazy.getPhase(phase).getViscosity(), 1.0e-12);
      assertEquals(eager.getPhase(phase).getThermalConductivity(), lazy.getPhase(phase).getThermalConductivity(),
          1.0e-12);
      for (int component = 0; component < lazy.getNumberOfComponents(); component++) {
        assertEquals(eager.getPhase(phase).getComponent(component).getx(),
            lazy.getPhase(phase).getComponent(component).getx(), 1.0e-12);
      }
    }
  }

  /**
   * Create a reproducible three-phase cubic-EOS feed.
   *
   * @return methane, heavy hydrocarbon and water mixture
   */
  private SystemInterface createFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 15.0);
    fluid.addComponent("methane", 7.0);
    fluid.addComponent("n-decane", 1.0);
    fluid.addComponent("water", 3.0);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
