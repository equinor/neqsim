package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression tests for parachor surface-tension failure semantics. */
class ParachorSurfaceTensionTest {
  @Test
  void twoPhaseCalculationReturnsPhysicalValueAndInvalidIndexFails() {
    SystemInterface fluid = new SystemSrkEos(300.0, 20.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    assertTrue(fluid.getNumberOfPhases() >= 2, "Regression state must contain two phases");
    ParachorSurfaceTension calculator = new ParachorSurfaceTension(fluid);
    double sigma = calculator.calcSurfaceTension(0, 1);
    assertTrue(Double.isFinite(sigma) && sigma > 0.0);

    assertThrows(IllegalArgumentException.class, () -> calculator.calcSurfaceTension(0, 99));
    assertThrows(IllegalArgumentException.class, () -> calculator.calcSurfaceTension(0, 0));
  }

  @Test
  void onePhaseSystemStillReportsNoInterfaceAsZero() {
    SystemInterface fluid = new SystemSrkEos(300.0, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();

    assertEquals(1, fluid.getNumberOfPhases());
    assertEquals(0.0, new ParachorSurfaceTension(fluid).calcSurfaceTension(0, 1), 0.0);
  }
}
