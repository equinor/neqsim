package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class NewComponentTest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem = null;

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
        "neqsim.util.exception.InvalidInputException: PhaseSrkEos:addComponent - Input compNumber  must be valid index, i.e., between 0 and "
            + ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS + ".",
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
    thermoSystem.createDatabase(true);
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
  }

  @Test
  public void molarMassTest() {
    thermoSystem = new SystemSrkEos(298.0, ThermodynamicConstantsInterface.referencePressure);
    thermoSystem.addComponent("iso-butene", 1.0);
    thermoSystem.init(0);
    thermoSystem.getPhase(0).getComponent(0).setMolarMass(0.090);
    assertEquals(0.09, thermoSystem.getPhase(0).getComponent(0).getMolarMass());
    assertEquals(0.09, thermoSystem.getPhase(0).getComponent(0).getMolarMass("kg/mol"));
    assertEquals(90.0, thermoSystem.getPhase(0).getComponent(0).getMolarMass("gr/mol"));
    assertEquals(90.0, thermoSystem.getPhase(0).getComponent(0).getMolarMass("lbm/lbmol"));

    thermoSystem.getPhase(0).getComponent(0).setMolarMass(90, "gr/mol");
    assertEquals(0.09, thermoSystem.getPhase(0).getComponent(0).getMolarMass());

    thermoSystem.getPhase(0).getComponent(0).setMolarMass(0.010, "kg/mol");
    assertEquals(0.01, thermoSystem.getPhase(0).getComponent(0).getMolarMass());

    thermoSystem.getPhase(0).getComponent(0).setMolarMass(85.0, "lbm/lbmol");
    assertEquals(85.0, thermoSystem.getPhase(0).getComponent(0).getMolarMass("gr/mol"));
  }

  @Test
  public void volTransTtest() {
    thermoSystem = new SystemPrEos(318.0, 20.01325);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.init(0);
    thermoSystem.init(1);
    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    assertEquals(36.68212551, thermoSystem.getDensity("kg/m3"), 0.01);

    thermoSystem.getComponent("CO2").setVolumeCorrectionT(0.2);

    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    assertEquals(37.6425616, thermoSystem.getDensity("kg/m3"), 0.01);

    thermoSystem.getComponent("CO2").setRacketZ(0.3);

    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    assertEquals(37.557573, thermoSystem.getDensity("kg/m3"), 0.01);
  }

  @Test
  public void newComponentNO2() {
    thermoSystem = new SystemSrkEos(298.0, ThermodynamicConstantsInterface.referencePressure);
    thermoSystem.addComponent("NO2", 1.0);
    thermoSystem.init(0);
    assertEquals(0.046005, thermoSystem.getMolarMass("kg/mol"), 0.01);

    thermoSystem = new SystemSrkEos(298.0, ThermodynamicConstantsInterface.referencePressure);
    thermoSystem.addComponent("SO2", 1.0);
    thermoSystem.init(0);
    assertEquals(0.064063, thermoSystem.getMolarMass("kg/mol"), 0.01);
  }

  @Test
  public void testComponentsulfuric_acid() {
    thermoSystem = new SystemSrkEos(273.15 + 47.9, 118.6);
    thermoSystem.addComponent("sulfuric acid", 1.0);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.createDatabase(true);
    thermoSystem.setMixingRule("classic");
    // thermoSystem.setMultiPhaseCheck(true);
    // ((PhaseEos) thermoSystem.getPhase(0)).getMixingRule().setBinaryInteractionParameter(0, 1,
    // 0.3012);
    // ((PhaseEos) thermoSystem.getPhase(1)).getMixingRule().setBinaryInteractionParameter(0, 1,
    // 0.3012);
    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    try {
      ops.TPflash();
    } catch (Exception e) {
      System.out.println("error in bubble point flash");
    }
    assertEquals(3.447289881042099E-6,
        thermoSystem.getPhase(0).getComponent("sulfuric acid").getx(), 100e-9);

    thermoSystem = new SystemSrkEos(273.15 + 25.0, 100.6);
    thermoSystem.addComponent("nitric acid", 1.0);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.createDatabase(true);
    thermoSystem.setMixingRule("classic");

    // thermoSystem.setMultiPhaseCheck(true);
    // ((PhaseEos) thermoSystem.getPhase(0)).getMixingRule().setBinaryInteractionParameter(0, 1,
    // 0.4012);
    // ((PhaseEos) thermoSystem.getPhase(1)).getMixingRule().setBinaryInteractionParameter(0, 1,
    // 0.4012);
    ops = new ThermodynamicOperations(thermoSystem);
    try {
      ops.TPflash();
    } catch (Exception e) {
      System.out.println("error in bubble point flash");
    }

    assertEquals(0.002568785, thermoSystem.getPhase(0).getComponent("nitric acid").getx(), 100e-6);
  }
}
