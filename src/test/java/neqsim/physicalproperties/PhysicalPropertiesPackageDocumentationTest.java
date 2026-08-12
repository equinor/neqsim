package neqsim.physicalproperties;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.FrictionTheoryViscosityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.LBCViscosityMethod;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Executable coverage for {@code docs/physical_properties/README.md}. */
class PhysicalPropertiesPackageDocumentationTest {

  @Test
  void basicOverviewExampleCalculatesPositiveGasProperties() {
    SystemInterface fluid = createNaturalGas(298.15, 50.0);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.setPhysicalPropertyModel(PhysicalPropertyModel.DEFAULT);
    fluid.initPhysicalProperties();

    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    assertPositiveFinite(fluid.getPhase("gas").getThermalConductivity("W/mK"));
    assertPositiveFinite(fluid.getPhase("gas").getDensity("kg/m3"));
    assertPositiveFinite(fluid.getPhase("gas").getPhysicalProperties().getKinematicViscosity());
  }

  @Test
  void diffusivityGuideUsesPublicBinaryAndEffectiveAccessors() {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.01325);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("nitrogen", 0.50);
    fluid.setMixingRule("classic");

    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    PhysicalProperties properties = fluid.getPhase("gas").getPhysicalProperties();
    properties.setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
    fluid.getPhase("gas").initPhysicalProperties();

    assertPositiveFinite(properties.getDiffusionCoefficient("methane", "nitrogen"));

    properties.calcEffectiveDiffusionCoefficients();
    assertPositiveFinite(properties.getEffectiveDiffusionCoefficient("methane"));

    fluid.setPhysicalPropertyModel(PhysicalPropertyModel.AMINE);
    fluid.initPhysicalProperties();
    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
  }

  @Test
  void modelSetSelectionUsesPhysicalPropertyModelApi() {
    SystemInterface fluid = createNaturalGas(298.15, 50.0);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.setPhysicalPropertyModel(PhysicalPropertyModel.GLYCOL);
    fluid.initPhysicalProperties();

    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    fluid.initPhysicalProperties("DENSITY");
    assertPositiveFinite(fluid.getPhase("gas").getDensity("kg/m3"));
    fluid.initPhysicalProperties("VISCOSITY");
    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    fluid.initPhysicalProperties("CONDUCTIVITY");
    assertPositiveFinite(fluid.getPhase("gas").getThermalConductivity("W/mK"));
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

    assertInstanceOf(FrictionTheoryViscosityMethod.class,
        fluid.getPhase("gas").getPhysicalProperties().getViscosityModel());
    assertInstanceOf(LBCViscosityMethod.class, fluid.getPhase("oil").getPhysicalProperties().getViscosityModel());
    assertPositiveFinite(fluid.getPhase("gas").getViscosity("cP"));
    assertPositiveFinite(fluid.getPhase("oil").getViscosity("cP"));
  }

  @Test
  void clonedTemperatureSweepFlashesEachClone() {
    SystemInterface baseFluid = createNaturalGas(298.15, 50.0);
    double[] temperaturesK = { 280.0, 300.0, 320.0 };

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
