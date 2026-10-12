package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Executes the plus-fraction recipes in the API-patterns and EOS-regression skills.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CharacterizationSkillExamplesTest extends NeqSimTest {
  /** Verifies the API recipe also rebuilds a mixing rule selected before adding heavy fractions. */
  @Test
  void apiPatternsCharacterizationRecipe() {
    SystemInterface fluid = new SystemSrkEos(373.15, 200.0);
    fluid.addComponent("methane", 0.89);
    fluid.setMixingRule("classic");

    fluid.addTBPfraction("C7", 0.05, 92.0 / 1000, 0.727);
    fluid.addTBPfraction("C8", 0.04, 104.0 / 1000, 0.749);
    fluid.addPlusFraction("C20", 0.02, 350.0 / 1000, 0.88);
    fluid.getCharacterization().configureLumping().model("standard").totalPseudoComponents(6).build();
    fluid.getCharacterization().characterisePlusFraction();
    fluid.setMixingRule("classic");

    verifyCharacterizedFluid(fluid, 1);
    assertEquals(20, fluid.getCharacterization().getPlusFractionModel().getFirstPlusFractionNumber());
  }

  /** Verifies the complete PR-fluid setup from the EOS-regression skill. */
  @Test
  void eosRegressionCharacterizationRecipe() {
    SystemInterface fluid = new SystemPrEos(273.15 + 100.0, 200.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addTBPfraction("C7", 0.05, 92.0 / 1000, 0.727);
    fluid.addTBPfraction("C8", 0.04, 104.0 / 1000, 0.749);
    fluid.addTBPfraction("C9", 0.03, 119.0 / 1000, 0.768);
    fluid.addPlusFraction("C10", 0.10, 200.0 / 1000, 0.830);
    fluid.getCharacterization().configureLumping().model("standard").totalPseudoComponents(6).build();
    fluid.getCharacterization().characterisePlusFraction();
    fluid.setMixingRule("classic");

    verifyCharacterizedFluid(fluid, 5);
    assertEquals(10, fluid.getCharacterization().getPlusFractionModel().getFirstPlusFractionNumber());
  }

  /**
   * Checks lump count, complete interaction matrices, and a physically valid flash.
   *
   * @param fluid characterized fluid from the documented recipe
   * @param lightComponents number of components outside the characterized heavy fraction
   */
  private void verifyCharacterizedFluid(SystemInterface fluid, int lightComponents) {
    assertEquals(lightComponents + 6, fluid.getNumberOfComponents(), "Exactly six heavy pseudo-components");
    assertFalse(fluid.getCharacterization().getPlusFractionModel().hasPlusFraction());
    for (PhaseInterface phase : fluid.getPhases()) {
      if (phase == null) {
        continue;
      }
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
          assertTrue(
              Double.isFinite(((PhaseEosInterface) phase).getEosMixingRule().getBinaryInteractionParameter(i, j)));
        }
      }
    }
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertTrue(Double.isFinite(fluid.getDensity("kg/m3")));
    assertTrue(fluid.getDensity("kg/m3") > 0.0);
    double totalBeta = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      assertTrue(fluid.getBeta(phase) >= 0.0 && fluid.getBeta(phase) <= 1.0);
      totalBeta += fluid.getBeta(phase);
    }
    assertEquals(1.0, totalBeta, 1e-10);
    assertEquals(1.0, fluid.getNumberOfMoles(), 1e-6);
  }
}
