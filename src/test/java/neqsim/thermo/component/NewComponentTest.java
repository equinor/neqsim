package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

public class NewComponentTest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {}

  @Test
  public void createComponentTest() {
    PhaseSrkEos p = new PhaseSrkEos();
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {
      p.addComponent("", 0, 0, 0);
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: PhaseSrkEos:addcomponent - Input name can not be empty.",
        thrown.getMessage());

    RuntimeException thrown_2 = Assertions.assertThrows(RuntimeException.class, () -> {
      p.addComponent(null, 0, 0, 0);
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: PhaseSrkEos:addcomponent - Input name can not be null",
        thrown_2.getMessage());

    RuntimeException thrown_3 = Assertions.assertThrows(RuntimeException.class, () -> {
      p.addComponent("ethane", 0, 0, -1);
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: PhaseSrkEos:addComponent - Input compNumber  must be valid index, i.e., between 0 and 100.",
        thrown_3.getMessage());

    p.addComponent("ethane", 0, 0, 0);
    RuntimeException thrown_4 = Assertions.assertThrows(RuntimeException.class, () -> {
      p.addComponent("methane", 0, 0, 0);
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: PhaseSrkEos:addComponent - Input compNumber number is already in use.",
        thrown_4.getMessage());
  }

  @Test
  public void newComponentTest() {
    thermoSystem = new SystemSrkEos(298.0, ThermodynamicConstantsInterface.referencePressure);
    thermoSystem.addComponent("ammonia", 1.0);
    thermoSystem.init(0);
    assertEquals(0.01703052, thermoSystem.getMolarMass("kg/mol"), 0.01);
    assertEquals(405.4, thermoSystem.getComponent(0).getTC(), 0.01);
  }

  @Test
  public void newComponentIsoButeneTest() {
    thermoSystem = new SystemSrkEos(298.0, ThermodynamicConstantsInterface.referencePressure);
    thermoSystem.addComponent("iso-butene", 1.0);
    thermoSystem.init(0);
    assertEquals(56.10632e-3, thermoSystem.getMolarMass("kg/mol"), 0.01);
  }

  @Test
  public void setTCPC_test() {
    thermoSystem = new SystemSrkEos(298.0, ThermodynamicConstantsInterface.referencePressure);
    thermoSystem.addComponent("iso-butene", 1.0);
    thermoSystem.init(0);
    thermoSystem.getPhase(0).getComponent(0).setTC(190.0);
    assertEquals(190.0, thermoSystem.getPhase(0).getComponent(0).getTC(), 0.01);

    thermoSystem.getPhase(0).getComponent(0).setTC(190.0, "R");
    assertEquals(190.0, thermoSystem.getPhase(0).getComponent(0).getTC("R"), 0.01);

    thermoSystem.getPhase(0).getComponent(0).setTC(190.0, "R");
    assertEquals(105.55555, thermoSystem.getPhase(0).getComponent(0).getTC("K"), 0.01);

    thermoSystem.getPhase(0).getComponent(0).setPC(1290.0, "psia");
    assertEquals(1290.0, thermoSystem.getPhase(0).getComponent(0).getPC("psia"), 0.01);
    assertEquals(88.9423690, thermoSystem.getPhase(0).getComponent(0).getPC("bara"), 0.01);

    thermoSystem.getPhase(0).getComponent(0).setMolarMass(0.090);
    assertEquals(0.09, thermoSystem.getPhase(0).getComponent(0).getMolarMass(), 0.01);
    assertEquals(90.0, thermoSystem.getPhase(0).getComponent(0).getMolarMass("gr/mol"), 0.01);
    assertEquals(90.0, thermoSystem.getPhase(0).getComponent(0).getMolarMass("lbm/lbmol"), 0.01);

    thermoSystem.getPhase(0).getComponent(0).setMolarMass(85.0, "lbm/lbmol");
    assertEquals(85.0, thermoSystem.getPhase(0).getComponent(0).getMolarMass("gr/mol"), 0.01);

  }

  @Test
  public void volTransTtest() {
    thermoSystem = new SystemPrEos(318.0, 20.01325);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.init(0);
    thermoSystem.init(1);
    thermoSystem.initPhysicalProperties("density");
    assertEquals(36.68212551, thermoSystem.getDensity("kg/m3"), 0.01);

    thermoSystem.getComponent("CO2").setVolumeCorrectionT(0.2);

    thermoSystem.initPhysicalProperties("density");
    assertEquals(37.6425616, thermoSystem.getDensity("kg/m3"), 0.01);

    thermoSystem.getComponent("CO2").setRacketZ(0.3);

    thermoSystem.initPhysicalProperties("density");
    assertEquals(37.557573, thermoSystem.getDensity("kg/m3"), 0.01);
  }
}
