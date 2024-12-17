package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.PlusFractionModel.WhitsonGammaModel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PlusFractionModelTest {

  @Test
  void testPedersenPlusModelCharacterization() {
    SystemInterface thermoSystem = null;
    thermoSystem = new SystemSrkEos(298.0, 10.0);

    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);

    thermoSystem.getCharacterization().setTBPModel("PedersenSRK"); // this need to be set before
                                                                   // adding oil components

    thermoSystem.addTBPfraction("C6", 1.0, 90.0 / 1000.0, 0.7);
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    thermoSystem.addTBPfraction("C8", 1.0, 120.0 / 1000.0, 0.76);
    thermoSystem.addTBPfraction("C9", 1.0, 140.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10", 11.0, 290.0 / 1000.0, 0.82);

    /*
     * Specify that the Pedersen plus fraction model will be used for characterizing the plus
     * component
     */
    thermoSystem.getCharacterization().setPlusFractionModel("Pedersen");
    thermoSystem.getCharacterization().setLumpingModel("PVTlumpingModel"); // this is default
                                                                           // lumping model in
                                                                           // neqsim. Needs to be
                                                                           // set before calling
                                                                           // characterisePlusFraction()

    thermoSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(9); // specif
                                                                                         // numer
                                                                                         // of
                                                                                         // lumped
                                                                                         // components
                                                                                         // (C6-C80
                                                                                         // components)
    thermoSystem.getCharacterization().characterisePlusFraction();
    assertEquals(17, thermoSystem.getNumberOfComponents());

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    // thermoSystem.prettyPrint();
    assertEquals(0.76652495787, thermoSystem.getBeta(), 1e-4);
  }

  @Test
  void testPedersenHeavyOilPlusModelCharacterization() {
    SystemInterface thermoSystem = null;
    thermoSystem = new SystemSrkEos(298.0, 10.0);

    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);

    thermoSystem.getCharacterization().setTBPModel("PedersenSRK"); // this need to be set before
                                                                   // adding oil components

    thermoSystem.addTBPfraction("C6", 1.0, 90.0 / 1000.0, 0.7);
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    thermoSystem.addTBPfraction("C8", 1.0, 120.0 / 1000.0, 0.76);
    thermoSystem.addTBPfraction("C9", 1.0, 140.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10", 11.0, 590.0 / 1000.0, 0.90);

    /*
     * Specify that the Pedersen heavy oil plus fraction model will be used for characterizing the
     * plus component
     */
    thermoSystem.getCharacterization().setPlusFractionModel("Pedersen Heavy Oil");

    thermoSystem.getCharacterization().setLumpingModel("PVTlumpingModel"); // this is default
                                                                           // lumping model in
                                                                           // neqsim. Needs to be
                                                                           // set before calling
                                                                           // characterisePlusFraction()
    thermoSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(3);
    thermoSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(8);
    // specify
    // numer
    // of
    // lumped
    // components
    // (C6-C80
    // components)
    thermoSystem.getCharacterization().characterisePlusFraction();
    assertEquals(12, thermoSystem.getNumberOfComponents());

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    // thermoSystem.prettyPrint();
    assertEquals(0.779829948507504, thermoSystem.getBeta(), 1e-4);
  }

  @Test
  void testGammaModelCharacterization() {
    SystemInterface thermoSystem = null;
    thermoSystem = new SystemSrkEos(298.0, 10.0);

    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);
    thermoSystem.getCharacterization().setTBPModel("PedersenSRK");

    thermoSystem.addTBPfraction("C6", 1.0, 90.0 / 1000.0, 0.7);
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    thermoSystem.addTBPfraction("C8", 1.0, 120.0 / 1000.0, 0.76);
    thermoSystem.addTBPfraction("C9", 1.0, 140.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10", 11.0, 290.0 / 1000.0, 0.82);

    thermoSystem.getCharacterization().setPlusFractionModel("Whitson Gamma Model");

    // Add how to set parameters in the gamma model here

    thermoSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);

    thermoSystem.getCharacterization().characterisePlusFraction();
    thermoSystem.setMixingRule("classic");
    assertEquals(16, thermoSystem.getNumberOfComponents());

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    // thermoSystem.prettyPrint();
    assertEquals(0.746485111, thermoSystem.getBeta(), 1e-4);

    // illustration of how to set parameters for the gamma model
    ((WhitsonGammaModel) thermoSystem.getCharacterization().getPlusFractionModel())
        .setGammaParameters(1.0, 90);
    double shape = ((WhitsonGammaModel) thermoSystem.getCharacterization().getPlusFractionModel())
        .getGammaParameters()[0];
    double minMW = ((WhitsonGammaModel) thermoSystem.getCharacterization().getPlusFractionModel())
        .getGammaParameters()[1];
    assertEquals(90.0, minMW, 1e-4);
  }

  @Test
  void testC6C7PlusModel() {
    SystemInterface thermoSystem = null;
    thermoSystem = new SystemSrkEos(298.0, 10.0);

    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);

    thermoSystem.getCharacterization().setTBPModel("PedersenSRK"); // this need to be set before
                                                                   // adding oil components

    String[] componentNames = {"C7"};
    double[] molarComposition = {0.15};
    double[] molarMasses = {0.092};
    double[] reldens = {0.82};

    thermoSystem.getCharacterization().setTBPModel("PedersenSRK"); // this need to be set before

    thermoSystem.addOilFractions(componentNames, molarComposition, molarMasses, reldens, true);

    // In this case the molar mass of the plus fraction is set to 0.092 kg/mol an is too low to
    // distribute the component into heavier components
    assertEquals(5, thermoSystem.getNumberOfComponents());

    thermoSystem = new SystemSrkEos(298.0, 10.0);

    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);

    thermoSystem.getCharacterization().setTBPModel("PedersenSRK"); // this need to be set before
                                                                   // adding oil components

    molarMasses = new double[] {0.120};

    thermoSystem.getCharacterization().setTBPModel("PedersenSRK"); // this need to be set before

    thermoSystem.addOilFractions(componentNames, molarComposition, molarMasses, reldens, true);
    // In this case the molar mass of the plus fraction is high enogh and can be characterized into
    // heavier components

    assertEquals(16, thermoSystem.getNumberOfComponents());

  }
}
