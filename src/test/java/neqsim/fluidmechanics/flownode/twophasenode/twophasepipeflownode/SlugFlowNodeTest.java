package neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for SlugFlowNode class.
 *
 * <p>
 * Tests verify that slug flow correctly calculates transport coefficients, contact areas, and slug
 * characteristics.
 * </p>
 *
 * @author esol
 */
public class SlugFlowNodeTest {
  private SystemInterface testSystem;
  private PipeData pipe;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(295.3, 5.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 0.05, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initPhysicalProperties();

    pipe = new PipeData(0.1); // 10 cm diameter
  }

  @Test
  void testSlugFlowNodeType() {
    FlowNodeInterface node = new SlugFlowNode(testSystem, pipe);
    assertEquals("slug", node.getFlowNodeType());
    assertNotNull(node.getFluidBoundary());
    assertNotNull(node.getInterphaseTransportCoefficient());
  }

  @Test
  void testSlugFlowTransportCoefficient() {
    FlowNodeInterface node = new SlugFlowNode(testSystem, pipe);

    // Verify slug flow uses correct transport coefficient class
    String transportClassName = node.getInterphaseTransportCoefficient().getClass().getSimpleName();
    assertEquals("InterphaseSlugFlow", transportClassName,
        "SlugFlowNode should use InterphaseSlugFlow transport coefficients");
  }

  @Test
  void testSlugFlowContactArea() {
    SlugFlowNode node = new SlugFlowNode(testSystem, pipe);
    node.setLengthOfNode(1.0);
    node.initFlowCalc();

    // Contact area should be positive
    double contactArea = node.calcGasLiquidContactArea();
    assertTrue(contactArea > 0, "Interphase contact area should be positive for slug flow");
  }

  @Test
  void testSlugCharacteristicsCalculation() {
    SystemInterface system = new SystemSrkCPAstatoil(300.0, 50.0);
    system.addComponent("methane", 1.0, "MSm3/day", 0);
    system.addComponent("water", 100.0, "kg/hr", 1);
    system.createDatabase(true);
    system.setMixingRule(10);
    system.initPhysicalProperties();

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    PipeData slugPipe = new PipeData(0.2);
    SlugFlowNode node = new SlugFlowNode(system, slugPipe);
    node.setVelocityIn(2.0); // Set a reasonable mixture velocity
    node.setVelocity(0, 5.0); // Gas velocity
    node.setVelocity(1, 1.0); // Liquid velocity
    node.initFlowCalc();

    // Slug frequency should be non-negative
    assertTrue(node.getSlugFrequency() >= 0, "Slug frequency should be non-negative");

    // Slug translational velocity calculation depends on velocities
    // It may be zero if velocities are not properly set, but should be finite
    assertTrue(Double.isFinite(node.getSlugTranslationalVelocity()),
        "Slug translational velocity should be finite");

    // Slug length ratio should be reasonable
    assertTrue(node.getSlugLengthRatio() > 0, "Slug length ratio should be positive");
  }

  @Test
  void testSlugFlowClone() {
    SlugFlowNode node = new SlugFlowNode(testSystem, pipe);
    node.setLengthOfNode(0.5);
    node.initFlowCalc();

    SlugFlowNode cloned = node.clone();

    assertNotNull(cloned);
    assertEquals(node.getFlowNodeType(), cloned.getFlowNodeType());
    assertEquals(node.getLengthOfNode(), cloned.getLengthOfNode(), 1e-10);
  }

  @Test
  void testSlugFlowGetNextNode() {
    SlugFlowNode node = new SlugFlowNode(testSystem, pipe);
    node.initFlowCalc();

    FlowNodeInterface nextNode = node.getNextNode();

    assertNotNull(nextNode);
    assertTrue(nextNode instanceof SlugFlowNode, "Next node should also be SlugFlowNode");
    assertEquals("slug", nextNode.getFlowNodeType());
  }

  @Test
  void testSlugFlowWithThreeArgConstructor() {
    SystemInterface interphaseSystem = testSystem.clone();

    SlugFlowNode node = new SlugFlowNode(testSystem, interphaseSystem, pipe);

    assertEquals("slug", node.getFlowNodeType());
    assertNotNull(node.getFluidBoundary());
    assertNotNull(node.getInterphaseTransportCoefficient());
  }

  @Test
  void testDefaultConstructor() {
    SlugFlowNode node = new SlugFlowNode();
    assertEquals("slug", node.getFlowNodeType());
  }

  @Test
  void testSlugLengthRatioSetter() {
    SlugFlowNode node = new SlugFlowNode(testSystem, pipe);

    node.setSlugLengthRatio(40.0);
    assertEquals(40.0, node.getSlugLengthRatio(), 1e-10);
  }

  @Test
  void testSlugFrequencySetter() {
    SlugFlowNode node = new SlugFlowNode(testSystem, pipe);

    node.setSlugFrequency(0.5);
    assertEquals(0.5, node.getSlugFrequency(), 1e-10);
  }

  @Test
  void testWallContactLengths() {
    SlugFlowNode node = new SlugFlowNode(testSystem, pipe);
    node.initFlowCalc();

    double contactLength = node.calcContactLength();

    // Wall contact length for gas phase should be positive
    assertTrue(contactLength > 0, "Wall contact length should be positive");
  }
}
