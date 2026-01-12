package neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.InterfacialAreaModel;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for interfacial area calculations in two-phase flow nodes.
 */
public class InterfacialAreaCalculationTest {
  private SystemInterface testSystem;
  private PipeData pipe;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(295.0, 5.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 0.05, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(3);

    pipe = new PipeData(0.1); // 10 cm diameter pipe
    pipe.setNodeLength(0.01); // 1 cm node length
  }

  @Test
  void testStratifiedFlowInterfacialArea() {
    StratifiedFlowNode node = new StratifiedFlowNode(testSystem, pipe);
    node.initFlowCalc();

    double area = node.calcInterfacialAreaPerVolume();
    assertTrue(area >= 0, "Interfacial area per volume should be non-negative");

    // For stratified flow with geometric model, area should be proportional to 1/diameter
    assertTrue(area < 100, "Interfacial area per volume should be reasonable for stratified flow");
  }

  @Test
  void testBubbleFlowInterfacialArea() {
    BubbleFlowNode node = new BubbleFlowNode(testSystem, pipe);
    node.setAverageBubbleDiameter(0.005); // 5 mm bubbles
    node.initFlowCalc();

    double area = node.calcInterfacialAreaPerVolume();
    assertTrue(area >= 0, "Interfacial area per volume should be non-negative");

    // For bubble flow: a = 6 * α_G / d_32
    // With small bubbles, should have high interfacial area
  }

  @Test
  void testDropletFlowInterfacialArea() {
    DropletFlowNode node = new DropletFlowNode(testSystem, pipe);
    node.setAverageDropletDiameter(0.0001); // 100 micron droplets
    node.initFlowCalc();

    double area = node.calcInterfacialAreaPerVolume();
    assertTrue(area >= 0, "Interfacial area per volume should be non-negative");
  }

  @Test
  void testAnnularFlowInterfacialArea() {
    AnnularFlow node = new AnnularFlow(testSystem, pipe);
    node.initFlowCalc();

    double area = node.calcInterfacialAreaPerVolume();
    assertTrue(area >= 0, "Interfacial area per volume should be non-negative");
  }

  @Test
  void testSlugFlowInterfacialArea() {
    SlugFlowNode node = new SlugFlowNode(testSystem, pipe);
    node.initFlowCalc();

    double area = node.calcInterfacialAreaPerVolume();
    assertTrue(area >= 0, "Interfacial area per volume should be non-negative");
  }

  @Test
  void testInterfacialAreaModelSelection() {
    StratifiedFlowNode node = new StratifiedFlowNode(testSystem, pipe);
    node.initFlowCalc();

    // Test default model (GEOMETRIC)
    assertEquals(InterfacialAreaModel.GEOMETRIC, node.getInterfacialAreaModel());

    // Test model switching
    node.setInterfacialAreaModel(InterfacialAreaModel.EMPIRICAL_CORRELATION);
    assertEquals(InterfacialAreaModel.EMPIRICAL_CORRELATION, node.getInterfacialAreaModel());

    double areaEmpirical = node.calcInterfacialAreaPerVolume();
    assertTrue(areaEmpirical >= 0, "Empirical interfacial area should be non-negative");

    // Test user-defined model
    node.setInterfacialAreaModel(InterfacialAreaModel.USER_DEFINED);
    node.setUserDefinedInterfacialAreaPerVolume(50.0);
    double areaUserDefined = node.calcInterfacialAreaPerVolume();
    assertEquals(50.0, areaUserDefined, 0.001, "User-defined area should match set value");
  }

  @Test
  void testBubbleSizeEffectOnInterfacialArea() {
    BubbleFlowNode node = new BubbleFlowNode(testSystem, pipe);
    node.initFlowCalc();

    // Smaller bubbles should have larger interfacial area per volume
    node.setAverageBubbleDiameter(0.01); // 10 mm
    node.initFlowCalc();
    double areaLargeBubbles = node.calcInterfacialAreaPerVolume();

    node.setAverageBubbleDiameter(0.001); // 1 mm
    node.initFlowCalc();
    double areaSmallBubbles = node.calcInterfacialAreaPerVolume();

    assertTrue(areaSmallBubbles > areaLargeBubbles,
        "Smaller bubbles should have larger interfacial area per volume");
  }

  @Test
  void testDropletSizeEffectOnInterfacialArea() {
    DropletFlowNode node = new DropletFlowNode(testSystem, pipe);
    node.initFlowCalc();

    // Smaller droplets should have larger interfacial area per volume
    node.setAverageDropletDiameter(0.001); // 1 mm
    node.initFlowCalc();
    double areaLargeDroplets = node.calcInterfacialAreaPerVolume();

    node.setAverageDropletDiameter(0.0001); // 100 micron
    node.initFlowCalc();
    double areaSmallDroplets = node.calcInterfacialAreaPerVolume();

    assertTrue(areaSmallDroplets > areaLargeDroplets,
        "Smaller droplets should have larger interfacial area per volume");
  }
}
