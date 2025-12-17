package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.FlowPattern;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for mass transfer and interfacial area methods in TwoPhasePipeFlowSystem.
 *
 * <p>
 * Tests the new methods:
 * <ul>
 * <li>getInterfacialAreaAtNode() and getInterfacialAreaProfile()</li>
 * <li>getLiquidMassTransferCoefficientAtNode() and profile</li>
 * <li>getGasMassTransferCoefficientAtNode() and profile</li>
 * <li>getVolumetricMassTransferCoefficientAtNode() and profile</li>
 * </ul>
 * </p>
 */
public class TwoPhasePipeFlowSystemMassTransferTest {

  private TwoPhasePipeFlowSystem pipe;
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    // Create a simple two-phase test system
    testFluid = new SystemSrkEos(295.3, 5.0);
    testFluid.addComponent("methane", 0.1, 0); // gas phase
    testFluid.addComponent("water", 0.05, 1); // liquid phase
    testFluid.createDatabase(true);
    testFluid.setMixingRule(2);

    // Create a simple pipe system using builder
    pipe = TwoPhasePipeFlowSystem.builder().withFluid(testFluid).withDiameter(0.1, "m")
        .withLength(100, "m").withNodes(10).withFlowPattern(FlowPattern.STRATIFIED).build();
  }

  @Test
  void testGetSpecificInterfacialAreaAtNode_ReturnsNonNegativeValue() {
    // System is already created via builder
    double area = pipe.getSpecificInterfacialAreaAtNode(0);
    assertTrue(area >= 0.0, "Specific interfacial area should be non-negative");
  }

  @Test
  void testGetSpecificInterfacialAreaProfile_ReturnsArrayOfCorrectSize() {
    double[] areas = pipe.getSpecificInterfacialAreaProfile();

    // Should have same number of elements as nodes
    assertEquals(pipe.getTotalNumberOfNodes(), areas.length,
        "Profile array should match number of nodes");
  }

  @Test
  void testGetLiquidMassTransferCoefficientAtNode_ReturnsNonNegativeValue() {
    double kL = pipe.getLiquidMassTransferCoefficientAtNode(0, 1.0e-9);
    assertTrue(kL >= 0.0, "Liquid mass transfer coefficient should be non-negative");
  }

  @Test
  void testGetGasMassTransferCoefficientAtNode_ReturnsNonNegativeValue() {
    double kG = pipe.getGasMassTransferCoefficientAtNode(0, 1.0e-5);
    assertTrue(kG >= 0.0, "Gas mass transfer coefficient should be non-negative");
  }

  @Test
  void testGetLiquidMassTransferCoefficientProfile_ReturnsArrayOfCorrectSize() {
    double diffusivity = 1.0e-9; // typical liquid diffusivity
    double[] kLProfile = pipe.getLiquidMassTransferCoefficientProfile(diffusivity);

    assertEquals(pipe.getTotalNumberOfNodes(), kLProfile.length,
        "Profile array should match number of nodes");
  }

  @Test
  void testGetGasMassTransferCoefficientProfile_ReturnsArrayOfCorrectSize() {
    double diffusivity = 1.0e-5; // typical gas diffusivity
    double[] kGProfile = pipe.getGasMassTransferCoefficientProfile(diffusivity);

    assertEquals(pipe.getTotalNumberOfNodes(), kGProfile.length,
        "Profile array should match number of nodes");
  }

  @Test
  void testGetVolumetricMassTransferCoefficientAtNode_ReturnsNonNegativeValue() {
    double kLa = pipe.getVolumetricMassTransferCoefficientAtNode(0, 1.0e-9);
    assertTrue(kLa >= 0.0, "Volumetric mass transfer coefficient should be non-negative");
  }

  @Test
  void testGetVolumetricMassTransferCoefficientProfile_ReturnsArrayOfCorrectSize() {
    double diffusivity = 1.0e-9;
    double[] kLaProfile = pipe.getVolumetricMassTransferCoefficientProfile(diffusivity);

    assertEquals(pipe.getTotalNumberOfNodes(), kLaProfile.length,
        "Profile array should match number of nodes");
  }

  @Test
  void testVolumetricMassTransferCoefficient_IsProductOfKLAndA() {
    double diffusivity = 1.0e-9;
    int nodeNumber = 0;

    double kL = pipe.getLiquidMassTransferCoefficientAtNode(nodeNumber, diffusivity);
    double a = pipe.getSpecificInterfacialAreaAtNode(nodeNumber);
    double kLa = pipe.getVolumetricMassTransferCoefficientAtNode(nodeNumber, diffusivity);

    // kLa should be the product of kL and a
    assertEquals(kL * a, kLa, 1e-15, "kL*a should equal kLa from volumetric method");
  }

  @Test
  void testSpecificInterfacialArea_AllValuesNonNegative() {
    double[] areas = pipe.getSpecificInterfacialAreaProfile();

    for (int i = 0; i < areas.length; i++) {
      assertTrue(areas[i] >= 0.0,
          "Specific interfacial area should be non-negative at all nodes, was " + areas[i]
              + " at node " + i);
    }
  }

  @Test
  void testMassTransferCoefficients_AllValuesNonNegative() {
    double diffusivityL = 1.0e-9;
    double diffusivityG = 1.0e-5;

    double[] kLProfile = pipe.getLiquidMassTransferCoefficientProfile(diffusivityL);
    double[] kGProfile = pipe.getGasMassTransferCoefficientProfile(diffusivityG);

    for (int i = 0; i < kLProfile.length; i++) {
      assertTrue(kLProfile[i] >= 0.0,
          "Liquid mass transfer coefficient should be non-negative at all nodes");
    }

    for (int i = 0; i < kGProfile.length; i++) {
      assertTrue(kGProfile[i] >= 0.0,
          "Gas mass transfer coefficient should be non-negative at all nodes");
    }
  }
}
