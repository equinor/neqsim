package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSpanWagnerEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Verifies every Java API pattern in the thermal-conductivity documentation. */
public class ThermalConductivityDocumentationTest {
  private static void flashAndInitialize(SystemInterface fluid) {
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();
  }

  private static void assertPositiveFinite(double value) {
    assertTrue(Double.isFinite(value), "Conductivity must be finite");
    assertTrue(value > 0.0, "Conductivity must be positive: " + value);
  }

  /** Verifies the basic natural-gas example, including its formerly missing interface import. */
  @Test
  void basicConductivityExample() {
    SystemInterface fluid = new SystemSrkEos(350.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    flashAndInitialize(fluid);

    assertPositiveFinite(fluid.getPhase("gas").getThermalConductivity("W/mK"));
  }

  /** Verifies the self-contained model-comparison example. */
  @Test
  void compareConductivityModelsExample() {
    String[] models = { "PFCT", "Chung-dense" };
    double[] conductivities = new double[models.length];

    for (int i = 0; i < models.length; i++) {
      SystemInterface fluid = new SystemSrkEos(350.0, 50.0);
      fluid.addComponent("methane", 0.85);
      fluid.addComponent("ethane", 0.10);
      fluid.addComponent("propane", 0.05);
      fluid.setMixingRule("classic");
      flashAndInitialize(fluid);

      fluid.getPhase("gas").getPhysicalProperties().setConductivityModel(models[i]);
      fluid.getPhase("gas").initPhysicalProperties();
      conductivities[i] = fluid.getPhase("gas").getThermalConductivity("W/mK");
      assertPositiveFinite(conductivities[i]);
    }
  }

  /** Verifies cloning, pressure units, flashing, and property access in the pressure sweep. */
  @Test
  void conductivityPressureSweepExample() {
    SystemInterface baseFluid = new SystemSrkEos(350.0, 10.0);
    baseFluid.addComponent("methane", 1.0);
    baseFluid.setMixingRule("classic");

    double[] pressuresBara = { 10, 50, 100, 150, 200 };
    for (double pressureBara : pressuresBara) {
      SystemInterface fluid = baseFluid.clone();
      fluid.setPressure(pressureBara, "bara");
      flashAndInitialize(fluid);
      assertPositiveFinite(fluid.getPhase(0).getThermalConductivity("W/mK"));
    }
  }

  /** Verifies phase-name guards and conductivity access for the multiphase example. */
  @Test
  void twoPhaseConductivityExample() {
    SystemInterface fluid = new SystemSrkEos(280.0, 30.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-pentane", 0.5);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    flashAndInitialize(fluid);

    assertTrue(fluid.hasPhaseType("gas"), "Expected a gas phase");
    assertTrue(fluid.hasPhaseType("oil"), "Expected an oil phase");
    assertPositiveFinite(fluid.getPhase("gas").getThermalConductivity("W/mK"));
    assertPositiveFinite(fluid.getPhase("oil").getThermalConductivity("W/mK"));
  }

  /** Verifies the case-sensitive model keys used by the page's model-specific snippets. */
  @Test
  void documentedConductivityModelKeys() {
    SystemInterface gas = new SystemSrkEos(300.0, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    flashAndInitialize(gas);
    for (String model : new String[] { "PFCT", "Chung", "Chung-dense" }) {
      gas.getPhase("gas").getPhysicalProperties().setConductivityModel(model);
      gas.getPhase("gas").initPhysicalProperties();
      assertPositiveFinite(gas.getPhase("gas").getThermalConductivity("W/mK"));
    }
    gas.getPhase("gas").getPhysicalProperties().setConductivityModel("friction theory");
    gas.getPhase("gas").initPhysicalProperties();
    assertPositiveFinite(gas.getPhase("gas").getThermalConductivity("W/mK"));

    SystemInterface liquid = new SystemSrkEos(300.0, 10.0);
    liquid.addComponent("n-heptane", 1.0);
    liquid.setMixingRule("classic");
    flashAndInitialize(liquid);
    liquid.getPhase("oil").getPhysicalProperties().setConductivityModel("polynom");
    liquid.getPhase("oil").initPhysicalProperties();
    assertPositiveFinite(liquid.getPhase("oil").getThermalConductivity("W/mK"));

    SystemInterface carbonDioxide = new SystemSpanWagnerEos(300.0, 10.0);
    flashAndInitialize(carbonDioxide);
    carbonDioxide.getPhase(0).getPhysicalProperties().setConductivityModel("CO2Model");
    carbonDioxide.getPhase(0).initPhysicalProperties();
    assertPositiveFinite(carbonDioxide.getPhase(0).getThermalConductivity("W/mK"));

    SystemInterface water = new SystemSrkEos(400.0, 1.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    flashAndInitialize(water);
    water.getPhase(0).getPhysicalProperties().setConductivityModel("WaterModel");
    water.getPhase(0).initPhysicalProperties();
    assertPositiveFinite(water.getPhase(0).getThermalConductivity("W/mK"));

    SystemInterface hydrogen = new SystemSrkEos(300.0, 1.0);
    hydrogen.addComponent("hydrogen", 1.0);
    hydrogen.setMixingRule("classic");
    flashAndInitialize(hydrogen);
    hydrogen.getPhase(0).getPhysicalProperties().setConductivityModel("H2Model");
    hydrogen.getPhase(0).initPhysicalProperties();
    assertPositiveFinite(hydrogen.getPhase(0).getThermalConductivity("W/mK"));

    SystemInterface aqueous = new SystemSrkCPAstatoil(298.15, 1.013);
    aqueous.addComponent("water", 0.7);
    aqueous.addComponent("MEG", 0.3);
    aqueous.setMixingRule(10);
    aqueous.setMultiPhaseCheck(true);
    flashAndInitialize(aqueous);
    int aqueousPhaseIndex = 0;
    for (int i = 0; i < aqueous.getNumberOfPhases(); i++) {
      String phaseType = aqueous.getPhase(i).getType().toString();
      if (phaseType.contains("LIQUID") || phaseType.contains("AQUEOUS")) {
        aqueousPhaseIndex = i;
        break;
      }
    }
    aqueous.getPhase(aqueousPhaseIndex).getPhysicalProperties().setConductivityModel("Filippov");
    aqueous.getPhase(aqueousPhaseIndex).initPhysicalProperties();
    assertPositiveFinite(aqueous.getPhase(aqueousPhaseIndex).getThermalConductivity("W/mK"));
  }
}
