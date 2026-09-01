package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Executable coverage for {@code docs/physical_properties/viscosity_models.md}. */
class ViscosityDocumentationTest {

  @Test
  void phaseSpecificSelectionRecalculatesBothPhases() {
    SystemInterface fluid = new SystemSrkEos(280.0, 30.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-pentane", 0.5);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();
    assertTrue(fluid.hasPhaseType("gas"));
    assertTrue(fluid.hasPhaseType("oil"));

    fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
    fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
    fluid.getPhase("gas").initPhysicalProperties();
    fluid.getPhase("oil").initPhysicalProperties();

    assertPositiveFinite(fluid.getPhase("gas").getViscosity("cP"));
    assertPositiveFinite(fluid.getPhase("oil").getViscosity("cP"));
  }

  @Test
  void basicCalculationAndComparisonExamplesRun() {
    String[] models = { "LBC", "friction theory", "PFCT" };
    String[] expectedClasses = { "LBCViscosityMethod", "FrictionTheoryViscosityMethod", "PFCTViscosityMethodMod86" };
    double[] viscositiesCp = new double[models.length];

    for (int i = 0; i < models.length; i++) {
      SystemInterface fluid = createOil(298.15, 4.0);
      fluid.initPhysicalProperties();
      fluid.getPhase("oil").getPhysicalProperties().setViscosityModel(models[i]);
      fluid.getPhase("oil").initPhysicalProperties();

      assertEquals(expectedClasses[i],
          fluid.getPhase("oil").getPhysicalProperties().getViscosityModel().getClass().getSimpleName());
      viscositiesCp[i] = fluid.getPhase("oil").getViscosity("cP");
      assertPositiveFinite(viscositiesCp[i]);
    }
  }

  @Test
  void temperatureSweepRemainsLiquidAndDecreases() {
    double[] temperatures = { 300.0, 320.0, 340.0, 360.0, 380.0, 400.0 };
    double previousViscosityCp = Double.POSITIVE_INFINITY;

    for (double temperature : temperatures) {
      SystemInterface fluid = new SystemSrkEos(temperature, 20.0);
      fluid.addComponent("nC10", 1.0);
      fluid.setMixingRule("classic");
      new ThermodynamicOperations(fluid).TPflash();
      fluid.initPhysicalProperties();
      assertTrue(fluid.hasPhaseType("oil"));

      fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
      fluid.getPhase("oil").initPhysicalProperties();
      double viscosityCp = fluid.getPhase("oil").getViscosity("cP");
      assertPositiveFinite(viscosityCp);
      assertTrue(viscosityCp < previousViscosityCp);
      previousViscosityCp = viscosityCp;
    }
  }

  @Test
  void everyDocumentedSpecializedKeySelectsItsImplementation() {
    assertSpecializedGasModel("MethaneModel", "methane", "MethaneViscosityMethod");
    assertSpecializedGasModel("CO2Model", "CO2", "CO2ViscosityMethod");
    assertSpecializedGasModel("Muzny", "hydrogen", "MuznyViscosityMethod");
    assertSpecializedGasModel("Muzny_mod", "hydrogen", "MuznyModViscosityMethod");
    assertSpecializedGasModel("KTA", "helium", "KTAViscosityMethod");
    assertSpecializedGasModel("KTA_mod", "helium", "KTAViscosityMethodMod");

    SystemInterface water = new SystemSrkCPAstatoil(298.15, 10.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    new ThermodynamicOperations(water).TPflash();
    water.initPhysicalProperties();
    water.getPhase("aqueous").getPhysicalProperties().setViscosityModel("Salt Water");
    water.getPhase("aqueous").initPhysicalProperties();

    assertEquals("Water",
        water.getPhase("aqueous").getPhysicalProperties().getViscosityModel().getClass().getSimpleName());
    assertPositiveFinite(water.getPhase("aqueous").getViscosity("cP"));
  }

  @Test
  void tuningAndAdvancedParameterExamplesRun() {
    SystemInterface oil = createOil(323.15, 20.0);
    oil.initPhysicalProperties();

    oil.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
    double[] lbcParameters = { 0.1023, 0.023364, 0.058533, -0.040758, 0.0093324 };
    oil.getPhase("oil").getPhysicalProperties().setLbcParameters(lbcParameters);
    oil.getPhase("oil").getPhysicalProperties().setLbcParameter(0, 0.105);
    oil.getPhase("oil").initPhysicalProperties();
    lbcParameters[0] = 0.105;
    assertArrayEquals(lbcParameters, oil.getPhase("oil").getPhysicalProperties().getLbcParameters(), 1.0e-12);

    oil.getPhase("oil").getPhysicalProperties().setViscosityModel("PFCT");
    double[] cspParameters = { 0.95, 1.05, 1.0, 1.0 };
    oil.getPhase("oil").getPhysicalProperties().setCspViscosityParameters(cspParameters);
    oil.getPhase("oil").getPhysicalProperties().setCspViscosityParameter(3, 0.98);
    oil.getPhase("oil").initPhysicalProperties();
    cspParameters[3] = 0.98;
    assertArrayEquals(cspParameters, oil.getPhase("oil").getPhysicalProperties().getCspViscosityParameters(), 1.0e-12);

    oil.getPhase("oil").getPhysicalProperties().setViscosityModel("friction theory");
    FrictionTheoryViscosityMethod method = (FrictionTheoryViscosityMethod) oil.getPhase("oil").getPhysicalProperties()
        .getViscosityModel();
    double[][] kapa = { { -0.114804, 0.246622, -3.94638e-2 }, { 0.246622, -1.15648e-4, 4.18863e-5 },
        { -3.94638e-2, 4.18863e-5, -5.91999e-9 } };
    double[][] kapr = { { -0.315903, 0.566713, -7.29995e-2 }, { 0.566713, -1.0086e-4, 5.17459e-5 },
        { -7.29995e-2, 5.17459e-5, -5.68708e-9 } };
    method.setFrictionTheoryConstants(-0.165302, 6.99574e-3, 1.26358e-3, kapa, kapr, 1.35994e-8);
    oil.getPhase("oil").initPhysicalProperties();
    assertPositiveFinite(oil.getPhase("oil").getViscosity("cP"));
  }

  @Test
  void unsupportedKeyLeavesCurrentModelUnchanged() {
    SystemInterface gas = new SystemSrkEos(298.15, 10.0);
    gas.addComponent("methane", 1.0);
    new ThermodynamicOperations(gas).TPflash();
    gas.initPhysicalProperties();
    String before = gas.getPhase("gas").getPhysicalProperties().getViscosityModel().getClass().getSimpleName();

    gas.getPhase("gas").getPhysicalProperties().setViscosityModel("unsupported-key");

    assertEquals(before, gas.getPhase("gas").getPhysicalProperties().getViscosityModel().getClass().getSimpleName());
  }

  private static SystemInterface createOil(double temperature, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressureBara);
    fluid.addComponent("n-heptane", 0.5);
    fluid.addComponent("nC10", 0.5);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    return fluid;
  }

  private static void assertSpecializedGasModel(String key, String component, String expectedClass) {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent(component, 1.0);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();
    fluid.getPhase("gas").getPhysicalProperties().setViscosityModel(key);
    fluid.getPhase("gas").initPhysicalProperties();

    assertEquals(expectedClass,
        fluid.getPhase("gas").getPhysicalProperties().getViscosityModel().getClass().getSimpleName());
    assertPositiveFinite(fluid.getPhase("gas").getViscosity("cP"));
  }

  private static void assertPositiveFinite(double value) {
    assertTrue(value > 0.0 && Double.isFinite(value));
  }
}
