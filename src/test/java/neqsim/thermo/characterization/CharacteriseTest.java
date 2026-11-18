package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CharacteriseTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(CharacteriseTest.class);

  static SystemInterface thermoSystem = null;

  @Test
  void testCharacterisePlusFraction() {
    thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);
    thermoSystem.addTBPfraction("C6", 1.0, 90.0 / 1000.0, 0.7);
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    thermoSystem.addTBPfraction("C8", 1.0, 120.0 / 1000.0, 0.76);
    thermoSystem.addTBPfraction("C9", 1.0, 140.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10", 11.0, 290.0 / 1000.0, 0.82);
    thermoSystem.getCharacterization().setLumpingModel("no lumping");
    thermoSystem.getCharacterization().characterisePlusFraction();
    // logger.info("number of components " + thermoSystem.getNumberOfComponents());
    assertEquals(77, thermoSystem.getNumberOfComponents());

    thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);
    thermoSystem.addTBPfraction("C6", 1.0, 90.0 / 1000.0, 0.7);
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    thermoSystem.addTBPfraction("C8", 1.0, 120.0 / 1000.0, 0.76);
    thermoSystem.addTBPfraction("C9", 1.0, 140.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10", 11.0, 290.0 / 1000.0, 0.82);
    // thermoSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(6);
    // thermoSystem.getCharacterization().setLumpingModel("PVTlumpingModel");
    thermoSystem.getCharacterization().setLumpingModel("no lumping");
    thermoSystem.getCharacterization().characterisePlusFraction();
    // assertEquals(15, thermoSystem.getNumberOfComponents());
    // thermoSystem.prettyPrint();
  }

  /**
   * An empty heavy end retains its place in the assay and can be characterized after material is added.
   *
   * @param model plus-fraction distribution
   */
  @ParameterizedTest
  @ValueSource(strings = {"Pedersen", "Whitson Gamma Model"})
  void retainsEmptyPlusFractionUntilPopulated(String model) {
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 10.0);
    fluid.addComponent("nitrogen", 0.0);
    fluid.addPlusFraction("C7", 0.0, 0.2400000474587263, 0.9193944763618844);
    fluid.init(0);
    String[] names = fluid.getComponentNames();
    double[] composition = fluid.getMolarComposition();
    Characterise characterization = fluid.getCharacterization();
    characterization.setPlusFractionModel(model);
    characterization.getLumpingModel().setNumberOfLumpedComponents(6);
    for (int attempt = 0; attempt < 2; attempt++) {
      characterization.characterisePlusFraction();
      assertArrayEquals(names, fluid.getComponentNames());
      assertArrayEquals(composition, fluid.getMolarComposition(), 0.0);
      assertTrue(fluid.getComponent("C7_PC").isIsPlusFraction());
      assertEquals(0.0, fluid.getComponent("C7_PC").getNumberOfmoles(), 0.0);
      assertEquals(10.0, fluid.getTotalNumberOfMoles(), 1.0e-12);
    }
    fluid.addComponent("C7_PC", 1.0);
    fluid.init(0);
    double feedMass = fluid.getFlowRate("kg/hr");
    characterization.characterisePlusFraction();
    fluid.init(0);
    assertTrue(fluid.getNumberOfComponents() > names.length);
    assertEquals(11.0, fluid.getTotalNumberOfMoles(), 1.0e-10);
    assertEquals(feedMass, fluid.getFlowRate("kg/hr"), feedMass * 1.0e-11);
    assertEquals(0.0, fluid.getComponent("nitrogen").getNumberOfmoles(), 0.0);
  }

  /**
   * Keep gamma-distribution fitting available while refusing unsupported heavy EOS properties.
   */
  @Test
  void testCharacterisePlusFractionGAMMA() {
    thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addPlusFraction("C10", 11.0, 290.0 / 1000.0, 0.82);
    thermoSystem.getCharacterization().setPlusFractionModel("Whitson Gamma Model");
    thermoSystem.getCharacterization().setLumpingModel("PVTlumpingModel");
    thermoSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(15);
    thermoSystem.init(0);
    Characterise characterization = thermoSystem.getCharacterization();
    PlusFractionModelInterface model = characterization.getPlusFractionModel();
    assertTrue(model.hasPlusFraction());
    assertTrue(model.characterizePlusFraction(characterization.getTBPModel()));
    double sumMoleFraction = 0.0;
    double sumMolarMass = 0.0;
    for (int fraction = model.getFirstPlusFractionNumber(); fraction < model.getLastPlusFractionNumber(); fraction++) {
      assertTrue(Double.isFinite(model.getZ()[fraction]) && model.getZ()[fraction] >= 0.0);
      assertTrue(Double.isFinite(model.getM()[fraction]) && model.getM()[fraction] > 0.0);
      assertTrue(Double.isFinite(model.getDens()[fraction]) && model.getDens()[fraction] > 0.0);
      sumMoleFraction += model.getZ()[fraction];
      sumMolarMass += model.getZ()[fraction] * model.getM()[fraction];
    }
    assertEquals(model.getZPlus(), sumMoleFraction, 1.0e-8);
    assertEquals(0.290, sumMolarMass / sumMoleFraction, 1.0e-4);
    double[] fittedMoleFractions = model.getZ().clone();
    double[] fittedMolarMasses = model.getM().clone();
    double[] fittedDensities = model.getDens().clone();

    UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
        characterization::characterisePlusFraction);
    assertTrue(failure.getMessage().contains("heavy-oil"));
    assertTrue(failure.getMessage().contains("molecular weight") && failure.getMessage().contains("g/mol"));
    assertArrayEquals(fittedMoleFractions, model.getZ());
    assertArrayEquals(fittedMolarMasses, model.getM());
    assertArrayEquals(fittedDensities, model.getDens());
  }
}
