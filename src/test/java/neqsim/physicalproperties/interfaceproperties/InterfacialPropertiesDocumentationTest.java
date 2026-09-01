package neqsim.physicalproperties.interfaceproperties;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.interfaceproperties.surfacetension.CDFTSurfaceTension;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class InterfacialPropertiesDocumentationTest {
  private SystemInterface createBubblePointMethane() {
    SystemInterface fluid = new SystemPrEos(120.0, 1.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    try {
      operations.bubblePointPressureFlash(false);
    } catch (Exception exception) {
      throw new AssertionError("Bubble-point flash failed", exception);
    }
    fluid.initProperties();
    assertTrue(fluid.hasPhaseType("gas"));
    assertTrue(fluid.hasPhaseType("oil"));
    return fluid;
  }

  @Test
  void documentedParachorWorkflowUsesNamedPhaseOrder() {
    SystemInterface fluid = createBubblePointMethane();
    fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Parachor");

    int gas = fluid.getPhaseNumberOfPhase("gas");
    int oil = fluid.getPhaseNumberOfPhase("oil");
    double sigmaNPerM = fluid.getInterphaseProperties().getSurfaceTension(gas, oil);

    assertTrue(Double.isFinite(sigmaNPerM));
    assertTrue(sigmaNPerM > 0.0);
  }

  @Test
  void documentedCDFTAliasesSelectCDFT() {
    SystemInterface fluid = createBubblePointMethane();
    int gas = fluid.getPhaseNumberOfPhase("gas");

    fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "cDFT");
    assertInstanceOf(CDFTSurfaceTension.class, fluid.getInterphaseProperties().getSurfaceTensionModel(gas));

    fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Classical DFT");
    assertInstanceOf(CDFTSurfaceTension.class, fluid.getInterphaseProperties().getSurfaceTensionModel(gas));
  }

  @Test
  void missingNamedPhaseReturnsNaN() {
    SystemInterface fluid = createBubblePointMethane();
    assertTrue(Double.isNaN(fluid.getInterfacialTension("gas", "aqueous")));
  }
}
