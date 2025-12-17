package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for non-equilibrium two-phase pipe flow simulation.
 *
 * <p>
 * Tests validate mass and heat transfer calculations based on the Krishna-Standart film model for
 * non-equilibrium gas-liquid flow in pipes, as described in Solbraa (2002).
 * </p>
 *
 * @author ASMF
 */
public class NonEquilibriumPipeFlowTest {
  private TwoPhasePipeFlowSystem pipe;
  private SystemInterface testSystem;

  @BeforeEach
  void setUp() {
    pipe = new TwoPhasePipeFlowSystem();
    testSystem = new SystemSrkEos(295.3, 5.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 0.05, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
  }

  /**
   * Helper method to set up a basic pipe configuration.
   */
  private void setupBasicPipe(int numLegs, int nodesPerLeg) {
    pipe.setInletThermoSystem(testSystem);
    pipe.setNumberOfLegs(numLegs);
    pipe.setNumberOfNodesInLeg(nodesPerLeg);

    double[] height = new double[numLegs + 1];
    double[] length = new double[numLegs + 1];
    double[] outerTemperature = new double[numLegs + 1];
    double[] outHeatCoef = new double[numLegs + 1];
    double[] wallHeatCoef = new double[numLegs + 1];

    for (int i = 0; i <= numLegs; i++) {
      height[i] = 0;
      length[i] = i * 1.0;
      outerTemperature[i] = 278.0;
      outHeatCoef[i] = 5.0;
      wallHeatCoef[i] = 15.0;
    }

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
    for (int i = 0; i <= numLegs; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);
  }

  @Test
  void testTimeStepConfiguration() {
    pipe.setTimeStep(0.5);
    assertEquals(0.5, pipe.getTimeStep(), 1e-10, "Time step should be set correctly");

    pipe.setSimulationTime(20.0);
    assertEquals(20.0, pipe.getSimulationTime(), 1e-10, "Simulation time should be set correctly");
  }

  @Test
  void testEnableNonEquilibriumMassTransfer() {
    setupBasicPipe(2, 5);
    pipe.createSystem();
    pipe.init();

    // Enable non-equilibrium mass transfer
    pipe.enableNonEquilibriumMassTransfer();

    // Verify that each node has mass transfer calculation enabled
    for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
      assertNotNull(pipe.getNode(i).getFluidBoundary(),
          "Fluid boundary should exist for mass transfer");
    }
  }

  @Test
  void testEnableNonEquilibriumHeatTransfer() {
    setupBasicPipe(2, 5);
    pipe.createSystem();
    pipe.init();

    // Enable non-equilibrium heat transfer
    pipe.enableNonEquilibriumHeatTransfer();

    // Verify that each node has heat transfer capabilities
    for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
      assertNotNull(pipe.getNode(i).getFluidBoundary(),
          "Fluid boundary should exist for heat transfer");
    }
  }

  @Test
  void testSteadyStateWithNonEquilibriumMassTransfer() {
    // Use CPA system for water-hydrocarbon mass transfer
    SystemInterface cpaSystem = new SystemSrkCPAstatoil(298.15, 10.0);
    cpaSystem.addComponent("methane", 0.1, "MSm3/day", 0);
    cpaSystem.addComponent("water", 1.0, "kg/hr", 1);
    cpaSystem.createDatabase(true);
    cpaSystem.setMixingRule(10);

    TwoPhasePipeFlowSystem pipeWithCPA = new TwoPhasePipeFlowSystem();
    pipeWithCPA.setInletThermoSystem(cpaSystem);
    pipeWithCPA.setNumberOfLegs(3);
    pipeWithCPA.setNumberOfNodesInLeg(10);

    double[] height = {0, 0, 0, 0};
    double[] length = {0.0, 2.0, 4.0, 6.0};
    double[] outerTemperature = {298.0, 298.0, 298.0, 298.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0, 15.0};

    pipeWithCPA.setLegHeights(height);
    pipeWithCPA.setLegPositions(length);
    pipeWithCPA.setLegOuterTemperatures(outerTemperature);
    pipeWithCPA.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipeWithCPA.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
    for (int i = 0; i < 4; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipeWithCPA.setEquipmentGeometry(pipeGeometry);

    pipeWithCPA.createSystem();
    pipeWithCPA.init();

    // Enable non-equilibrium mass transfer
    pipeWithCPA.enableNonEquilibriumMassTransfer();

    // Solve steady state
    pipeWithCPA.solveSteadyState(2);

    // Verify solution exists
    assertNotNull(pipeWithCPA.getNode(0).getBulkSystem());
    assertTrue(pipeWithCPA.getNode(0).getBulkSystem().getTemperature() > 0);
  }

  @Test
  void testMassTransferBetweenPhases() {
    // Use CPA system for proper water-gas equilibrium
    SystemInterface cpaSystem = new SystemSrkCPAstatoil(298.15, 10.0);
    cpaSystem.addComponent("methane", 0.1, "MSm3/day", 0);
    cpaSystem.addComponent("water", 1.0, "kg/hr", 1);
    cpaSystem.createDatabase(true);
    cpaSystem.setMixingRule(10);

    TwoPhasePipeFlowSystem pipeWithCPA = new TwoPhasePipeFlowSystem();
    pipeWithCPA.setInletThermoSystem(cpaSystem);
    pipeWithCPA.setNumberOfLegs(3);
    pipeWithCPA.setNumberOfNodesInLeg(10);

    double[] height = {0, 0, 0, 0};
    double[] length = {0.0, 3.0, 6.0, 9.0};
    double[] outerTemperature = {298.0, 298.0, 298.0, 298.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0, 15.0};

    pipeWithCPA.setLegHeights(height);
    pipeWithCPA.setLegPositions(length);
    pipeWithCPA.setLegOuterTemperatures(outerTemperature);
    pipeWithCPA.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipeWithCPA.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
    for (int i = 0; i < 4; i++) {
      pipeGeometry[i] = new PipeData(0.05);
    }
    pipeWithCPA.setEquipmentGeometry(pipeGeometry);

    pipeWithCPA.createSystem();
    pipeWithCPA.init();

    // Enable non-equilibrium mass transfer
    pipeWithCPA.enableNonEquilibriumMassTransfer();

    // Solve steady state
    pipeWithCPA.solveSteadyState(2);

    // Get total mass transfer rate (should be finite)
    double massTransferRate = pipeWithCPA.getTotalMassTransferRate(0);
    // Mass transfer rate should be a finite number (could be positive, negative, or zero)
    assertTrue(Double.isFinite(massTransferRate), "Mass transfer rate should be finite");
  }

  @Test
  void testHeatLossCalculation() {
    // Create system with temperature difference to surroundings
    // Use setup similar to existing working tests
    SystemInterface hotSystem = new SystemSrkEos(320.0, 5.0);
    hotSystem.addComponent("methane", 0.1, 0);
    hotSystem.addComponent("water", 0.05, 1);
    hotSystem.createDatabase(true);
    hotSystem.setMixingRule(2);

    TwoPhasePipeFlowSystem hotPipe = new TwoPhasePipeFlowSystem();
    hotPipe.setInletThermoSystem(hotSystem);
    hotPipe.setNumberOfLegs(2);
    hotPipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0};
    double[] length = {0.0, 1.0, 2.0};
    double[] outerTemperature = {295.0, 295.0, 295.0}; // Slightly cold surroundings
    double[] outHeatCoef = {5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0};

    hotPipe.setLegHeights(height);
    hotPipe.setLegPositions(length);
    hotPipe.setLegOuterTemperatures(outerTemperature);
    hotPipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    hotPipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < 3; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    hotPipe.setEquipmentGeometry(pipeGeometry);

    hotPipe.createSystem();
    hotPipe.init();
    hotPipe.solveSteadyState(2);

    // Calculate total heat loss
    double heatLoss = hotPipe.getTotalHeatLoss();

    // Heat loss should be positive (fluid is hotter than surroundings)
    assertTrue(heatLoss > 0, "Heat loss should be positive for hot fluid in cold surroundings");
  }

  @Test
  void testFlowPatternSlug() {
    // Test slug flow pattern
    pipe.setInletThermoSystem(testSystem);
    pipe.setInitialFlowPattern("slug");
    pipe.setNumberOfLegs(2);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0};
    double[] length = {0.0, 1.0, 2.0};
    double[] outerTemperature = {278.0, 278.0, 278.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < 3; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();

    assertNotNull(pipe.getNode(0));
    assertEquals("slug", pipe.getNode(0).getFlowNodeType());
  }

  @Test
  void testFlowPatternDroplet() {
    // Test droplet/mist flow pattern
    pipe.setInletThermoSystem(testSystem);
    pipe.setInitialFlowPattern("droplet");
    pipe.setNumberOfLegs(2);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0};
    double[] length = {0.0, 1.0, 2.0};
    double[] outerTemperature = {278.0, 278.0, 278.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < 3; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();

    assertNotNull(pipe.getNode(0));
    assertEquals("droplet", pipe.getNode(0).getFlowNodeType());
  }

  @Test
  void testFlowPatternBubble() {
    // Test bubble flow pattern
    pipe.setInletThermoSystem(testSystem);
    pipe.setInitialFlowPattern("bubble");
    pipe.setNumberOfLegs(2);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0};
    double[] length = {0.0, 1.0, 2.0};
    double[] outerTemperature = {278.0, 278.0, 278.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < 3; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();

    assertNotNull(pipe.getNode(0));
    assertEquals("bubble", pipe.getNode(0).getFlowNodeType());
  }

  @Test
  void testPhaseFractionConsistency() {
    setupBasicPipe(3, 10);
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    // Phase fractions should sum to 1 at each node
    for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
      double gasFraction = pipe.getNode(i).getPhaseFraction(0);
      double liquidFraction = pipe.getNode(i).getPhaseFraction(1);
      double sum = gasFraction + liquidFraction;

      assertEquals(1.0, sum, 0.01, "Phase fractions should sum to 1 at node " + i);
      assertTrue(gasFraction >= 0 && gasFraction <= 1,
          "Gas fraction should be between 0 and 1");
      assertTrue(liquidFraction >= 0 && liquidFraction <= 1,
          "Liquid fraction should be between 0 and 1");
    }
  }

  @Test
  void testVelocityPositive() {
    setupBasicPipe(3, 10);
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    // Velocities should be non-negative for forward flow
    for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
      double gasVelocity = pipe.getNode(i).getVelocity(0);
      double liquidVelocity = pipe.getNode(i).getVelocity(1);

      assertTrue(gasVelocity >= 0, "Gas velocity should be non-negative at node " + i);
      assertTrue(liquidVelocity >= 0, "Liquid velocity should be non-negative at node " + i);
    }
  }

  @Test
  void testInterphaseContactArea() {
    setupBasicPipe(3, 10);
    pipe.setInitialFlowPattern("stratified");
    pipe.createSystem();
    pipe.init();

    // Each node should have an interphase contact area for mass/heat transfer
    for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
      double contactArea = pipe.getNode(i).getInterphaseContactArea();
      assertTrue(contactArea >= 0, "Interphase contact area should be non-negative at node " + i);
    }
  }

  @Test
  void testTotalPressureDrop() {
    setupBasicPipe(3, 10);
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    double totalPressureDrop = pipe.getTotalPressureDrop();

    // For forward flow with friction, pressure drop should be non-negative
    assertTrue(totalPressureDrop >= 0,
        "Total pressure drop should be non-negative for forward flow");
  }

  @Test
  void testTEGMassTransfer() {
    // Test with TEG-water-methane system (similar to notebook example)
    SystemInterface tegSystem = new SystemSrkCPAstatoil(298.15, 50.0);
    tegSystem.addComponent("methane", 0.5, "MSm3/day", 0);
    tegSystem.addComponent("water", 0.1, "kg/hr", 0); // Some water in gas
    tegSystem.addComponent("TEG", 100.0, "kg/hr", 1);
    tegSystem.addComponent("water", 5.0, "kg/hr", 1); // Water in TEG
    tegSystem.createDatabase(true);
    tegSystem.setMixingRule(10);

    TwoPhasePipeFlowSystem tegPipe = new TwoPhasePipeFlowSystem();
    tegPipe.setInletThermoSystem(tegSystem);
    tegPipe.setInitialFlowPattern("stratified");
    tegPipe.setNumberOfLegs(3);
    tegPipe.setNumberOfNodesInLeg(10);

    double[] height = {0, 0, 0, 0};
    double[] length = {0.0, 1.0, 2.0, 3.0};
    double[] outerTemperature = {298.0, 298.0, 298.0, 298.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0, 15.0};

    tegPipe.setLegHeights(height);
    tegPipe.setLegPositions(length);
    tegPipe.setLegOuterTemperatures(outerTemperature);
    tegPipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    tegPipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
    for (int i = 0; i < 4; i++) {
      pipeGeometry[i] = new PipeData(0.1);
    }
    tegPipe.setEquipmentGeometry(pipeGeometry);

    tegPipe.createSystem();
    tegPipe.init();
    tegPipe.enableNonEquilibriumMassTransfer();
    tegPipe.solveSteadyState(2);

    // Verify the system solved correctly
    assertNotNull(tegPipe.getNode(0).getBulkSystem());
    assertTrue(tegPipe.getNode(0).getBulkSystem().getNumberOfPhases() > 0);
  }
}
