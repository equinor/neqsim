package neqsim.physicalproperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Executable coverage for {@code docs/physical_properties/README.md}. */
class PhysicalPropertiesPackageDocumentationTest {

  @Test
  void basicOverviewExampleCalculatesPositiveGasProperties() {
    SystemInterface fluid = createNaturalGas(298.15, 50.0);
    fluid.setPhysicalPropertyModel(PhysicalPropertyModel.DEFAULT);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    assertPositiveFinite(fluid.getPhase("gas").getThermalConductivity("W/mK"));
    assertPositiveFinite(fluid.getPhase("gas").getDensity("kg/m3"));
    assertPositiveFinite(fluid.getPhase("gas").getKinematicViscosity());
  }

  @Test
  void modelSetSelectionUsesPhysicalPropertyModelApi() {
    SystemInterface fluid = createNaturalGas(298.15, 50.0);

    fluid.setPhysicalPropertyModel(PhysicalPropertyModel.GLYCOL);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    assertThrows(RuntimeException.class, () -> fluid.initPhysicalProperties("GLYCOL"));
  }

  @Test
  void phaseSpecificModelExampleRecalculatesBothPhases() {
    SystemInterface fluid = new SystemSrkEos(280.0, 30.0);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("n-pentane", 0.50);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    assertTrue(fluid.hasPhaseType("gas"));
    assertTrue(fluid.hasPhaseType("oil"));

    fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
    fluid.getPhase("gas").getPhysicalProperties().setConductivityModel("Chung");
    fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
    fluid.getPhase("oil").getPhysicalProperties().setConductivityModel("PFCT");

    fluid.getPhase("gas").initPhysicalProperties();
    fluid.getPhase("oil").initPhysicalProperties();

    assertEquals("FrictionTheoryViscosityMethod",
        fluid.getPhase("gas").getPhysicalProperties().getViscosityModel().getClass()
            .getSimpleName());
    assertEquals("LBCViscosityMethod",
        fluid.getPhase("oil").getPhysicalProperties().getViscosityModel().getClass()
            .getSimpleName());
    assertPositiveFinite(fluid.getPhase("gas").getViscosity("cP"));
    assertPositiveFinite(fluid.getPhase("oil").getViscosity("cP"));
  }

  @Test
  void clonedTemperatureSweepFlashesEachClone() {
    SystemInterface baseFluid = createNaturalGas(298.15, 50.0);
    double[] temperaturesK = {280.0, 300.0, 320.0};

    for (double temperatureK : temperaturesK) {
      SystemInterface fluid = baseFluid.clone();
      fluid.setTemperature(temperatureK, "K");

      new ThermodynamicOperations(fluid).TPflash();
      fluid.initPhysicalProperties();

      assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    }
  }

  private static SystemInterface createNaturalGas(double temperatureK, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static void assertPositiveFinite(double value) {
    assertTrue(value > 0.0 && Double.isFinite(value));
  }
}
