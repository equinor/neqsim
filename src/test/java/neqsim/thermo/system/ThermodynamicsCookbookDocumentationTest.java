package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.Gson;
import java.util.Map;
import neqsim.NeQSimTest;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;

/** Executes the API families illustrated by the thermodynamics cookbook recipes. */
class ThermodynamicsCookbookDocumentationTest extends NeQSimTest {
  private SystemInterface createNaturalGas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.setMixingRule("classic");
    return fluid;
  }

  @Test
  void testFluidCreationRecipes() {
    assertEquals(7, createNaturalGas().getNumberOfComponents());

    SystemInterface oil = new SystemPrEos(333.15, 200.0);
    oil.addComponent("nitrogen", 0.5);
    oil.addComponent("CO2", 2.0);
    oil.addComponent("methane", 45.0);
    oil.addComponent("ethane", 8.0);
    oil.addComponent("propane", 5.0);
    oil.addComponent("n-butane", 3.0);
    oil.addComponent("n-pentane", 2.0);
    oil.addComponent("n-hexane", 2.0);
    oil.addPlusFraction("C7", 32.5, 0.220, 0.82);
    oil.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    oil.getCharacterization().characterisePlusFraction();
    oil.setMixingRule("classic");
    oil.setMultiPhaseCheck(true);
    assertTrue(oil.getNumberOfComponents() > 9);

    SystemInterface cpaFluid = new SystemSrkCPAstatoil(298.15, 100.0);
    cpaFluid.addComponent("CO2", 0.95);
    cpaFluid.addComponent("methane", 0.03);
    cpaFluid.addComponent("water", 0.02);
    cpaFluid.setMixingRule(10);
    assertEquals(3, cpaFluid.getNumberOfComponents());
  }

  @Test
  void testFlashAndPropertyRecipes() {
    SystemInterface tpFluid = createNaturalGas();
    ThermodynamicOperations tpOps = new ThermodynamicOperations(tpFluid);
    tpOps.TPflash();
    tpFluid.initProperties();
    assertTrue(tpFluid.getNumberOfPhases() >= 1);

    SystemInterface phFluid = createNaturalGas();
    ThermodynamicOperations phOps = new ThermodynamicOperations(phFluid);
    phOps.TPflash();
    phFluid.initProperties();
    double initialEnthalpy = phFluid.getEnthalpy("J");
    phFluid.setPressure(30.0, "bara");
    phOps.PHflash(initialEnthalpy);
    assertTrue(Double.isFinite(phFluid.getTemperature()));

    SystemInterface psFluid = createNaturalGas();
    ThermodynamicOperations psOps = new ThermodynamicOperations(psFluid);
    psOps.TPflash();
    psFluid.initProperties();
    double initialEntropy = psFluid.getEntropy("J/K");
    psFluid.setPressure(20.0, "bara");
    psOps.PSflash(initialEntropy);
    assertTrue(Double.isFinite(psFluid.getTemperature()));

    assertTrue(tpFluid.getDensity() > 0.0);
    assertTrue(tpFluid.getDensity("kg/m3") > 0.0);
    assertTrue(tpFluid.getMolarMass("g/mol") > 0.0);
    assertTrue(Double.isFinite(tpFluid.getZ()));
    assertTrue(Double.isFinite(tpFluid.getEnthalpy("kJ/kg")));
    assertTrue(Double.isFinite(tpFluid.getEntropy("J/kgK")));
    assertTrue(tpFluid.getCp("kJ/kgK") > 0.0);
    assertTrue(tpFluid.getCv("kJ/kgK") > 0.0);
    assertTrue(tpFluid.getSoundSpeed("m/s") > 0.0);
    assertTrue(tpFluid.getViscosity("cP") > 0.0);
    assertTrue(tpFluid.getThermalConductivity("W/mK") > 0.0);
    assertTrue(tpFluid.hasPhaseType("gas"));
    assertTrue(tpFluid.getPhase("gas").getDensity("kg/m3") > 0.0);
    assertTrue(tpFluid.getPhase("gas").getViscosity("cP") > 0.0);
    assertTrue(tpFluid.getPhase("gas").getBeta() > 0.0);
    assertTrue(tpFluid.getComponent(0).getTC() > 0.0);
    assertTrue(tpFluid.getComponent(0).getPC() > 0.0);
  }

  @Test
  void testPhaseEnvelopeRecipe() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    assertEquals(ops.get("dewT").length, ops.get("dewP").length);
    assertEquals(ops.get("bubT").length, ops.get("bubP").length);
    assertTrue(ops.get("dewT").length > 2);
    assertTrue(ops.get("bubT").length > 2);
    assertTrue(ops.get("cricondenbar")[1] > 0.0);
    assertTrue(ops.get("cricondentherm")[0] > 0.0);
  }

  @SuppressWarnings("unchecked")
  @Test
  void testModelSelectionJsonAndCloneRecipes() {
    double temperature = 288.15;
    double pressure = 70.0;
    assertEquals("SRK-EOS", new SystemSrkEos(temperature, pressure).getModelName());
    assertEquals("PR-EOS", new SystemPrEos(temperature, pressure).getModelName());
    assertFalse(new SystemSrkCPAstatoil(temperature, pressure).getModelName().isEmpty());
    assertEquals("GERG2008-EOS", new SystemGERG2008Eos(temperature, pressure).getModelName());

    SystemInterface fluid = createNaturalGas();
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();

    Map<String, Object> root = new Gson().fromJson(fluid.toJson(), Map.class);
    Map<String, Object> conditions = (Map<String, Object>) root.get("conditions");
    Map<String, Object> overall = (Map<String, Object>) conditions.get("overall");
    Map<String, Object> temperatureValue = (Map<String, Object>) overall.get("temperature");
    String temperatureStringValue = (String) temperatureValue.get("value");
    try {
      double parsedTemperature = Double.parseDouble(temperatureStringValue);
      assertTrue(Double.isFinite(parsedTemperature));
    } catch (NumberFormatException ex) {
      fail("Temperature value is not a valid number: " + temperatureStringValue);
    }
    assertFalse(((String) temperatureValue.get("unit")).isEmpty());

    SystemInterface copy = fluid.clone();
    assertNotSame(fluid, copy);
    double originalTemperature = fluid.getTemperature();
    copy.setTemperature(300.0);
    assertEquals(300.0, copy.getTemperature(), 1.0e-12);
    assertEquals(originalTemperature, fluid.getTemperature(), 1.0e-12);
  }
}
