package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.TbpClosure;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Exercises the heavy-fraction Python cookbook recipes through their underlying Java APIs.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ThermodynamicsCookbookCharacterizationTest {
  /** Verify the documented oil inventory, post-characterization mixing rule and flash. */
  @Test
  void characterizedOilHasRequestedInventoryAndFlashes() {
    SystemPrEos fluid = new SystemPrEos(333.15, 200.0);
    fluid.addComponent("nitrogen", 0.5);
    fluid.addComponent("CO2", 2.0);
    fluid.addComponent("methane", 45.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("n-pentane", 2.0);
    fluid.addComponent("n-hexane", 2.0);
    fluid.addPlusFraction("C7", 32.5, 0.220, 0.82);
    fluid.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    fluid.getCharacterization().characterisePlusFraction();
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    assertEquals(20, fluid.getNumberOfComponents());
    assertEquals(100.0, fluid.getTotalNumberOfMoles(), 1e-8);
    flashAndCheck(fluid);
    // A nearby pressure must use the same characterized component/mixing-rule inventory.
    fluid.setPressure(190.0, "bara");
    flashAndCheck(fluid);
    assertEquals(20, fluid.getNumberOfComponents());
    assertEquals(100.0, fluid.getTotalNumberOfMoles(), 1e-8);
  }

  /** Verify default and explicit closures on separate one-cut fluids. */
  @Test
  void closureAlternativesAreIndependent() {
    SystemPrEos defaultFluid = new SystemPrEos(333.15, 20.0);
    defaultFluid.addTBPfraction_Sg_Tb("C10", 1.0, 0.734, 447.3);
    SystemPrEos rd1980Fluid = new SystemPrEos(333.15, 20.0);
    rd1980Fluid.addTBPfraction_Sg_Tb("C10", 1.0, 0.734, 447.3, TbpClosure.RIAZI_DAUBERT_1980);
    for (SystemPrEos candidate : new SystemPrEos[] {defaultFluid, rd1980Fluid}) {
      candidate.setMixingRule("classic");
      flashAndCheck(candidate);
      assertEquals(1, candidate.getNumberOfComponents());
      assertEquals(1.0, candidate.getTotalNumberOfMoles(), 1e-12);
      double molarMass = candidate.getMolarMass("kg/mol");
      assertTrue(Double.isFinite(molarMass) && molarMass > 0.0);
    }
    assertTrue(Math.abs(defaultFluid.getMolarMass() - rd1980Fluid.getMolarMass()) > 1e-6);
  }

  /** Verify that the standalone DHA recipe constructs and flashes its own cut. */
  @Test
  void dhaCutIsStandalone() {
    SystemPrEos fluid = new SystemPrEos(333.15, 20.0);
    fluid.addTBPfraction_Tb_Pna("cut1", 1.0, 450.0, 0.60, 0.25, 0.15);
    fluid.setMixingRule("classic");
    flashAndCheck(fluid);
    assertEquals(1, fluid.getNumberOfComponents());
    assertEquals(1.0, fluid.getTotalNumberOfMoles(), 1e-12);
  }

  /**
   * Flash a recipe state and check finite positive density and normalized phase compositions.
   *
   * @param fluid configured recipe fluid
   */
  private void flashAndCheck(SystemPrEos fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    double density = fluid.getDensity("kg/m3");
    assertTrue(Double.isFinite(density) && density > 0.0);
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double sum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(fraction) && fraction >= 0.0);
        sum += fraction;
      }
      assertEquals(1.0, sum, 1e-8);
      betaSum += fluid.getPhase(phase).getBeta();
    }
    assertEquals(1.0, betaSum, 1e-8);
  }
}
