package neqsim.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Executes the examples in docs/thermo/thermodynamic_workflows.md. */
class ThermodynamicWorkflowsDocumentationTest {

  private SystemInterface buildAndFlashFluid() {
    SystemInterface fluid = new SystemPrEos(313.15, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addTBPfraction("C10", 0.10, 0.134, 0.792);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();
    return fluid;
  }

  /** Execute the complete characterized-fluid TP-flash example. */
  @Test
  void buildFlashAndReadExample() {
    SystemInterface fluid = buildAndFlashFluid();

    assertEquals(1.0, fluid.getTotalNumberOfMoles(), 1.0e-12);
    assertEquals(313.15, fluid.getTemperature("K"), 1.0e-10);
    assertEquals(80.0, fluid.getPressure("bara"), 1.0e-10);
    assertTrue(fluid.getNumberOfPhases() >= 1);
    assertTrue(Double.isFinite(fluid.getDensity("kg/m3")));
    assertTrue(fluid.getDensity("kg/m3") > 0.0);
    assertTrue(fluid.getMolarMass() > 0.0);
  }

  /** Prove the documented changed-state clone does not mutate the original fluid. */
  @Test
  void cloneSweepKeepsOriginalState() {
    SystemInterface fluid = buildAndFlashFluid();
    SystemInterface sweepCase = fluid.clone();
    assertNotSame(fluid, sweepCase);

    sweepCase.setTemperature(280.0, "K");
    sweepCase.setPressure(10.0, "bara");
    ThermodynamicOperations sweepOperations = new ThermodynamicOperations(sweepCase);
    sweepOperations.TPflash();
    sweepCase.initProperties();

    assertEquals(313.15, fluid.getTemperature("K"), 1.0e-10);
    assertEquals(80.0, fluid.getPressure("bara"), 1.0e-10);
    assertEquals(280.0, sweepCase.getTemperature("K"), 1.0e-10);
    assertEquals(10.0, sweepCase.getPressure("bara"), 1.0e-10);
    assertTrue(Double.isFinite(sweepCase.getDensity("kg/m3")));
    assertTrue(sweepCase.getDensity("kg/m3") > 0.0);
  }
}
