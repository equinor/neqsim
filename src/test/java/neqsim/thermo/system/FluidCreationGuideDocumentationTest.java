package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.NeqSimTest;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;

/** Executes the complete workflows and corrected heavy-end units in the fluid creation guide. */
class FluidCreationGuideDocumentationTest extends NeqSimTest {
  @Test
  void testNaturalGasWorkflow() {
    SystemInterface gas = new SystemSrkEos(283.15, 70.0);
    gas.addComponent("nitrogen", 0.02);
    gas.addComponent("CO2", 0.01);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.06);
    gas.addComponent("propane", 0.03);
    gas.addComponent("i-butane", 0.01);
    gas.addComponent("n-butane", 0.01);
    gas.addComponent("i-pentane", 0.005);
    gas.addComponent("n-pentane", 0.005);
    gas.setMixingRule("classic");

    new ThermodynamicOperations(gas).TPflash();
    gas.initProperties();

    assertTrue(gas.getDensity("kg/m3") > 0.0);
    assertTrue(Double.isFinite(gas.getZ()));
    assertTrue(gas.getMolarMass() > 0.0);
  }

  @Test
  void testWaterHydrocarbonWorkflow() {
    SystemInterface fluid = new SystemSrkCPAstatoil(323.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("water", 0.10);
    fluid.addComponent("MEG", 0.05);
    fluid.setMixingRule(10);

    new ThermodynamicOperations(fluid).TPflash();

    assertEquals(5, fluid.getNumberOfComponents());
    assertTrue(fluid.getNumberOfPhases() >= 1);
  }

  @Test
  void testGergDensityComparisonWorkflow() {
    SystemInterface gas = new SystemGERG2008Eos(288.15, 40.0);
    gas.addComponent("methane", 0.92);
    gas.addComponent("ethane", 0.04);
    gas.addComponent("propane", 0.02);
    gas.addComponent("nitrogen", 0.01);
    gas.addComponent("CO2", 0.01);
    gas.createDatabase(true);

    new ThermodynamicOperations(gas).TPflash();

    double density = gas.getPhase(0).getDensity_GERG2008();
    assertTrue(Double.isFinite(density));
    assertTrue(density > 0.0);
  }

  @Test
  void testOilCharacterizationUsesKilogramsPerMole() {
    SystemInterface oil = new SystemPrEos(350.0, 150.0);
    oil.addComponent("nitrogen", 0.005);
    oil.addComponent("CO2", 0.02);
    oil.addComponent("methane", 0.35);
    oil.addComponent("ethane", 0.08);
    oil.addComponent("propane", 0.06);
    oil.addComponent("i-butane", 0.02);
    oil.addComponent("n-butane", 0.03);
    oil.addComponent("i-pentane", 0.02);
    oil.addComponent("n-pentane", 0.02);
    oil.addComponent("n-hexane", 0.03);
    oil.addTBPfraction("C7", 0.05, 0.096, 0.738);
    oil.addTBPfraction("C8", 0.04, 0.107, 0.765);
    oil.addTBPfraction("C9", 0.03, 0.121, 0.781);
    oil.addTBPfraction("C10", 0.02, 0.134, 0.792);
    oil.addPlusFraction("C11", 0.18, 0.250, 0.85);

    assertEquals(0.096, oil.getPhase(0).getComponent("C7_PC").getMolarMass(), 1.0e-12);
    assertEquals(0.250, oil.getPhase(0).getComponent("C11_PC").getMolarMass(), 1.0e-12);

    oil.setMixingRule("classic");
    new ThermodynamicOperations(oil).TPflash();
    oil.initProperties();

    assertTrue(oil.getNumberOfPhases() >= 1);
    assertTrue(oil.getDensity("kg/m3") > 0.0);
  }
}
