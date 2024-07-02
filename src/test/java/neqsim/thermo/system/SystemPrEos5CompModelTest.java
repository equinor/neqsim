package neqsim.thermo.system;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentPR_5CompModel;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class SystemPrEos5CompModelTest {

  /**
   * <p>
   * testDensity.
   * </p>
   */
  @Test
  void testDensity() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemPrEos5CompModel(273.15 + 177, 50.0);
    testSystem.addComponent("CO2", 1.0);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    Assertions.assertEquals(45.7388, testSystem.getComponent("CO2").getTC("C"), 0.01);
    Assertions.assertEquals(1069.51, testSystem.getComponent("CO2").getPC("psia"), 0.01);
    Assertions.assertEquals(0.12256, testSystem.getComponent("CO2").getAcentricFactor(), 0.01);

    Assertions.assertEquals(0.12256, testSystem.getComponent("CO2").getAcentricFactor(), 0.01);
    Assertions.assertEquals(0.427705,
        ((ComponentPR_5CompModel) testSystem.getComponent("CO2")).getOmegaA(), 0.01);
    Assertions.assertEquals(0.0696460,
        ((ComponentPR_5CompModel) testSystem.getComponent("CO2")).getOmegaB(), 0.01);
    Assertions.assertEquals(44.01, testSystem.getComponent("CO2").getMolarMass("gr/mol"), 0.01);
    Assertions.assertEquals(0.919497398, testSystem.getZvolcorr(), 0.001);
    Assertions.assertEquals(63.94100, testSystem.getDensity("kg/m3"), 0.001);
  }

  
}
