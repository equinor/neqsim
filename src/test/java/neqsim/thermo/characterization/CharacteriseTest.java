package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CharacteriseTest extends neqsim.NeqSimTest {
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
    thermoSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    thermoSystem.getCharacterization().setLumpingModel("PVTlumpingModel");
    thermoSystem.getCharacterization().characterisePlusFraction();
    assertEquals(15, thermoSystem.getNumberOfComponents());
  }

  
  @Test
  void testCharacterisePlusFractionGAMMA() {
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
    thermoSystem.getCharacterization().setPlusFractionModel("Whitson Gamma Model");
    thermoSystem.getCharacterization().getPlusFractionModel().setLastPlusFractionNumber(2);
    thermoSystem.getCharacterization().characterisePlusFraction();
    // logger.info("number of components " + thermoSystem.getNumberOfComponents());
    //assertEquals(86, thermoSystem.getNumberOfComponents());
    System.out.println(thermoSystem.getComponent("C1-2_PC").getz());
  }

}
