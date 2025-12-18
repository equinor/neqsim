package neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for two-phase pipe flow nodes.
 *
 * <p>
 * Tests verify that each flow pattern (stratified, annular, droplet, bubble) uses the correct
 * transport coefficients and fluid boundary models.
 * </p>
 *
 * @author ASMF
 */
public class TwoPhaseFlowNodeTest {

  @Test
  void testStratifiedFlowNodeType() {
    SystemInterface testSystem = new SystemSrkEos(295.3, 5.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 0.05, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initPhysicalProperties();

    PipeData pipe = new PipeData(0.025);
    FlowNodeInterface node = new StratifiedFlowNode(testSystem, pipe);

    assertEquals("stratified", node.getFlowNodeType());
    assertNotNull(node.getFluidBoundary());
    assertNotNull(node.getInterphaseTransportCoefficient());
  }

  @Test
  void testAnnularFlowNodeType() {
    SystemInterface testSystem = new SystemSrkEos(295.3, 5.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 0.05, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initPhysicalProperties();

    PipeData pipe = new PipeData(0.025);
    FlowNodeInterface node = new AnnularFlow(testSystem, pipe);

    assertEquals("annular", node.getFlowNodeType());
    assertNotNull(node.getFluidBoundary());
    assertNotNull(node.getInterphaseTransportCoefficient());

    // Verify annular flow uses correct transport coefficient class
    String transportClassName = node.getInterphaseTransportCoefficient().getClass().getSimpleName();
    assertEquals("InterphaseAnnularFlow", transportClassName,
        "AnnularFlow should use InterphaseAnnularFlow transport coefficients");
  }

  @Test
  void testDropletFlowNodeType() {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 5.0, 10.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 100.0, "kg/min", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.initPhysicalProperties();
    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    PipeData pipe = new PipeData(0.203);
    FlowNodeInterface node = new DropletFlowNode(testSystem, pipe);

    assertEquals("droplet", node.getFlowNodeType());
    assertNotNull(node.getFluidBoundary());
    assertNotNull(node.getInterphaseTransportCoefficient());

    // Verify droplet flow uses correct transport coefficient class
    String transportClassName = node.getInterphaseTransportCoefficient().getClass().getSimpleName();
    assertEquals("InterphaseDropletFlow", transportClassName,
        "DropletFlowNode should use InterphaseDropletFlow transport coefficients");
  }

  @Test
  void testBubbleFlowNodeType() {
    SystemInterface testSystem = new SystemSrkEos(295.3, 50.0);
    testSystem.addComponent("CO2", 100.0, "kg/hr", 0);
    testSystem.addComponent("water", 1000.0, "kg/hr", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);
    testSystem.initPhysicalProperties();
    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    PipeData pipe = new PipeData(0.025);
    FlowNodeInterface node = new BubbleFlowNode(testSystem, pipe);

    assertEquals("bubble", node.getFlowNodeType());
    assertNotNull(node.getFluidBoundary());
    assertNotNull(node.getInterphaseTransportCoefficient());
  }

  @Test
  void testStratifiedFlowContactLength() {
    SystemInterface testSystem = new SystemSrkEos(295.3, 5.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 0.05, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initPhysicalProperties();

    PipeData pipe = new PipeData(0.1); // 10cm diameter
    StratifiedFlowNode node = new StratifiedFlowNode(testSystem, pipe);
    node.setLengthOfNode(1.0);
    node.initFlowCalc();

    // Contact lengths should be positive
    assertTrue(node.getInterphaseContactArea() > 0,
        "Interphase contact area should be positive for stratified flow");
  }

  @Test
  void testAnnularFlowContactLength() {
    SystemInterface testSystem = new SystemSrkEos(295.3, 5.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 0.05, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initPhysicalProperties();

    PipeData pipe = new PipeData(0.1); // 10cm diameter
    AnnularFlow node = new AnnularFlow(testSystem, pipe);
    node.setLengthOfNode(1.0);
    node.initFlowCalc();

    // For annular flow, gas is in center, liquid on wall
    assertTrue(node.getInterphaseContactArea() >= 0,
        "Interphase contact area should be non-negative for annular flow");
  }

  @Test
  void testMassTransferCalculation() {
    SystemInterface testSystem = new SystemSrkCPAstatoil(325.3, 100.0);
    testSystem.addComponent("methane", 0.1, "MSm3/day", 0);
    testSystem.addComponent("water", 2.0, "kg/hr", 1);
    testSystem.addComponent("MEG", 3.0, "kg/hr", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.initPhysicalProperties();
    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    PipeData pipe = new PipeData(0.25);
    FlowNodeInterface node = new StratifiedFlowNode(testSystem, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.001);
    node.getFluidBoundary().setHeatTransferCalc(false);
    node.getFluidBoundary().setMassTransferCalc(true);

    node.initFlowCalc();
    node.calcFluxes();

    // After flux calculation, the fluid boundary should have computed fluxes
    assertNotNull(node.getFluidBoundary());
  }

  @Test
  void testHeatTransferCalculation() {
    SystemInterface testSystem = new SystemSrkCPAstatoil(350.0, 70.0);
    testSystem.addComponent("methane", 10.0, "MSm^3/day", 0);
    testSystem.addComponent("water", 5.0, "kg/min", 1);
    testSystem.addComponent("MEG", 95.0, "kg/min", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.initPhysicalProperties();
    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    PipeData pipe = new PipeData(0.5);
    pipe.getWall().addMaterialLayer(
        new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.MaterialLayer("steel",
            0.02));
    pipe.getSurroundingEnvironment().setTemperature(273.15 + 4.0); // Cold surroundings

    FlowNodeInterface node = new AnnularFlow(testSystem, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.01);
    node.getFluidBoundary().setHeatTransferCalc(true);
    node.getFluidBoundary().setMassTransferCalc(true);

    node.initFlowCalc();
    node.calcFluxes();

    // Heat transfer should be computed
    assertNotNull(node.getFluidBoundary());
  }

  @Test
  void testNodeCloning() {
    SystemInterface testSystem = new SystemSrkEos(295.3, 5.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 0.05, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initPhysicalProperties();

    PipeData pipe = new PipeData(0.025);
    StratifiedFlowNode node = new StratifiedFlowNode(testSystem, pipe);
    node.setLengthOfNode(1.0);
    node.initFlowCalc();

    // Clone the node
    StratifiedFlowNode clonedNode = node.clone();

    assertNotNull(clonedNode);
    assertEquals(node.getFlowNodeType(), clonedNode.getFlowNodeType());
    assertEquals(node.getLengthOfNode(), clonedNode.getLengthOfNode());
  }

  @Test
  void testDropletAverageDiameter() {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 5.0, 10.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 100.0, "kg/min", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.initPhysicalProperties();
    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    PipeData pipe = new PipeData(0.203);
    DropletFlowNode node = new DropletFlowNode(testSystem, pipe);

    // Default droplet diameter
    double defaultDiameter = node.getAverageDropletDiameter();
    assertTrue(defaultDiameter > 0, "Default droplet diameter should be positive");

    // Set custom droplet diameter
    node.setAverageDropletDiameter(50.0e-6); // 50 microns
    assertEquals(50.0e-6, node.getAverageDropletDiameter(), 1e-10);
  }

  @Test
  void testBubbleAverageDiameter() {
    SystemInterface testSystem = new SystemSrkEos(295.3, 50.0);
    testSystem.addComponent("CO2", 100.0, "kg/hr", 0);
    testSystem.addComponent("water", 1000.0, "kg/hr", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);
    testSystem.initPhysicalProperties();
    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    PipeData pipe = new PipeData(0.025);
    BubbleFlowNode node = new BubbleFlowNode(testSystem, pipe);

    // Default bubble diameter
    double defaultDiameter = node.getAverageBubbleDiameter();
    assertTrue(defaultDiameter > 0, "Default bubble diameter should be positive");

    // Set custom bubble diameter
    node.setAverageBubbleDiameter(2.0e-3); // 2 mm
    assertEquals(2.0e-3, node.getAverageBubbleDiameter(), 1e-10);
  }
}
