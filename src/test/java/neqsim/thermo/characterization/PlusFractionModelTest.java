package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

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
     * Specify that the Pedersen plus fraction model will be used for characterizing
     * the plus
     * component
     */
    thermoSystem.getCharacterization().setPlusFractionModel("Pedersen");

    thermoSystem.getCharacterization().setLumpingModel("PVTlumpingModel"); // this is default
                                                                           // lumping model in
                                                                           // neqsim. Needs to be
                                                                           // set before calling
                                                                           // characterisePlusFraction()

    thermoSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12); // specify
                                                                                          // numer
                                                                                          // of
                                                                                          // lumped
                                                                                          // components
                                                                                          // (C6-C80
                                                                                          // components)
    thermoSystem.getCharacterization().characterisePlusFraction();
    assertEquals(16, thermoSystem.getNumberOfComponents());

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();

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
     * Specify that the Pedersen heavy oil plus fraction model will be used for
     * characterizing the
     * plus component
     */
    thermoSystem.getCharacterization().setPlusFractionModel("Pedersen Heavy Oil");

    thermoSystem.getCharacterization().setLumpingModel("PVTlumpingModel"); // this is default
                                                                           // lumping model in
                                                                           // neqsim. Needs to be
                                                                           // set before calling
                                                                           // characterisePlusFraction()

    thermoSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12); // specify
                                                                                          // numer
                                                                                          // of
                                                                                          // lumped
                                                                                          // components
                                                                                          // (C6-C80
                                                                                          // components)
    thermoSystem.getCharacterization().characterisePlusFraction();
    assertEquals(16, thermoSystem.getNumberOfComponents());

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();

    assertEquals(0.767085187, thermoSystem.getBeta(), 1e-4);

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

    thermoSystem.getCharacterization().setPlusFractionModel("Whitson Gamma Model"); // this is default
  
    // lumping model in
    // neqsim. Needs to be
    // set before calling
    // characterisePlusFraction()

    thermoSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);

    thermoSystem.getCharacterization().characterisePlusFraction();
    assertEquals(16, thermoSystem.getNumberOfComponents());

  }

}
