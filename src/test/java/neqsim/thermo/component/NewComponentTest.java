package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class NewComponentTest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {}

  @Test
  public void newComponentTest() {
    thermoSystem = new SystemSrkEos(298.0, 1.01325);
    thermoSystem.addComponent("ammonia", 1.0);
    thermoSystem.init(0);
    assertEquals(0.01703052, thermoSystem.getMolarMass("kg/mol"), 0.01);
    assertEquals(405.6, thermoSystem.getComponent(0).getTC(), 0.01);
  }

  @Test
  public void aceticAcidComponentTestSrk() {
    thermoSystem = new SystemSrkEos(298.0, 1.01325);
    thermoSystem.addComponent("Acetic Acid", 1.0);
    thermoSystem.init(0);
    thermoSystem.useVolumeCorrection(true);

    assertEquals(0.06005196, thermoSystem.getMolarMass("kg/mol"), 0.01);
    assertEquals(590.7, thermoSystem.getComponent(0).getTC(), 0.01);

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();

    assertEquals(857.7999403195936, thermoSystem.getDensity("kg/m3"), 0.01);


  }

  @Test
  public void aceticAcidComponentTestCPA() {
    thermoSystem = new SystemElectrolyteCPAstatoil(298.0, 1.01325);
    thermoSystem.addComponent("AceticAcid2", 1.0);
    thermoSystem.init(0);

    assertEquals(0.06005196, thermoSystem.getMolarMass("kg/mol"), 0.01);
    assertEquals(590.7, thermoSystem.getComponent(0).getTC(), 0.01);

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();

    assertEquals(713.525130, thermoSystem.getDensity("kg/m3"), 0.01);


  }
}
