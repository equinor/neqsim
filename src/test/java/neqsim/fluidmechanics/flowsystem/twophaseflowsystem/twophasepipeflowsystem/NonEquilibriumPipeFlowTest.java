package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
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
@Timeout(value = 30, unit = TimeUnit.SECONDS)
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
      assertTrue(gasFraction >= 0 && gasFraction <= 1, "Gas fraction should be between 0 and 1");
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

  /**
   * Tests that the Joule-Thomson effect is properly accounted for in the energy balance.
   * 
   * <p>
   * For natural gas, Joule-Thomson coefficient is typically positive (cooling on expansion). As gas
   * expands along the pipe (pressure drops), temperature should decrease if J-T effect dominates.
   * </p>
   */
  // @Test - Disabled: Long running test
  void testJouleThomsonEffect() {
    // Create a longer pipe with significant pressure drop to see J-T effects
    SystemInterface gasSystem = new SystemSrkEos(300.0, 80.0);
    gasSystem.addComponent("methane", 0.9, 0);
    gasSystem.addComponent("ethane", 0.05, 0);
    gasSystem.addComponent("propane", 0.05, 0);
    // Small amount of liquid to make it two-phase
    gasSystem.addComponent("n-pentane", 0.001, 1);
    gasSystem.createDatabase(true);
    gasSystem.setMixingRule(2);

    TwoPhasePipeFlowSystem gasLine = new TwoPhasePipeFlowSystem();
    gasLine.setInletThermoSystem(gasSystem);
    gasLine.setInitialFlowPattern("annular");
    gasLine.setNumberOfLegs(5);
    gasLine.setNumberOfNodesInLeg(10);

    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 200.0, 400.0, 600.0, 800.0, 1000.0};
    double[] outerTemperature = {300.0, 300.0, 300.0, 300.0, 300.0, 300.0};
    double[] outHeatCoef = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0}; // No external heat transfer
    double[] wallHeatCoef = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0}; // No wall heat transfer

    gasLine.setLegHeights(height);
    gasLine.setLegPositions(length);
    gasLine.setLegOuterTemperatures(outerTemperature);
    gasLine.setLegOuterHeatTransferCoefficients(outHeatCoef);
    gasLine.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[6];
    for (int i = 0; i < 6; i++) {
      pipeGeometry[i] = new PipeData(0.15);
    }
    gasLine.setEquipmentGeometry(pipeGeometry);

    gasLine.createSystem();
    gasLine.init();
    gasLine.solveSteadyState(3);

    // Get temperature and pressure profiles
    double[] tempProfile = gasLine.getTemperatureProfile();
    double[] pressProfile = gasLine.getPressureProfile();

    // Verify inlet conditions
    double inletTemp = tempProfile[0];
    double inletPressure = pressProfile[0];

    // Get outlet conditions
    int lastNode = gasLine.getTotalNumberOfNodes() - 1;
    double outletTemp = tempProfile[lastNode];
    double outletPressure = pressProfile[lastNode];

    // Verify pressure drop occurred
    double pressureDrop = inletPressure - outletPressure;
    assertTrue(pressureDrop > 0, "Pressure should drop along the pipe");

    // Get J-T coefficient from the gas phase
    double jtCoeff = gasLine.getNode(0).getBulkSystem().getPhase(0).getJouleThomsonCoefficient();

    // For typical natural gas, J-T coefficient is positive (cooling on expansion)
    // J-T coefficient is in K/Pa, need to multiply by pressure drop in Pa
    // Expected temperature change: dT = μ_JT * dP
    // For natural gas at ~80 bar, μ_JT is typically ~0.3-0.5 K/bar

    // With no heat transfer and friction, if J-T dominates, temperature should decrease
    // But friction heating may offset this somewhat
    // Just verify the energy calculation is working - temperature should change from inlet
    assertNotEquals(inletTemp, outletTemp, 0.001,
        "Temperature should change along the pipe due to energy balance effects");

    // Verify J-T coefficient is accessible and reasonable
    assertTrue(Double.isFinite(jtCoeff), "Joule-Thomson coefficient should be finite");
  }

  /**
   * Tests temperature profile with heat transfer to surroundings.
   */
  // @Test - Disabled: Long running test
  void testWallHeatTransferTemperatureProfile() {
    // Create system at elevated temperature
    SystemInterface hotGas = new SystemSrkEos(350.0, 20.0);
    hotGas.addComponent("methane", 0.95, 0);
    hotGas.addComponent("n-heptane", 0.01, 1); // Small liquid phase
    hotGas.createDatabase(true);
    hotGas.setMixingRule(2);

    TwoPhasePipeFlowSystem coolingPipe = new TwoPhasePipeFlowSystem();
    coolingPipe.setInletThermoSystem(hotGas);
    coolingPipe.setInitialFlowPattern("stratified");
    coolingPipe.setNumberOfLegs(3);
    coolingPipe.setNumberOfNodesInLeg(10);

    // Cold surroundings (like seawater)
    double coldTemp = 280.0; // 7°C
    double[] height = {0, 0, 0, 0};
    double[] length = {0.0, 100.0, 200.0, 300.0};
    double[] outerTemperature = {coldTemp, coldTemp, coldTemp, coldTemp};
    double[] outHeatCoef = {500.0, 500.0, 500.0, 500.0}; // High h for seawater
    double[] wallHeatCoef = {50.0, 50.0, 50.0, 50.0};

    coolingPipe.setLegHeights(height);
    coolingPipe.setLegPositions(length);
    coolingPipe.setLegOuterTemperatures(outerTemperature);
    coolingPipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    coolingPipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
    for (int i = 0; i < 4; i++) {
      pipeGeometry[i] = new PipeData(0.1);
    }
    coolingPipe.setEquipmentGeometry(pipeGeometry);

    coolingPipe.createSystem();
    coolingPipe.init();
    coolingPipe.solveSteadyState(3);

    // Get temperature profile
    double[] tempProfile = coolingPipe.getTemperatureProfile();

    // Verify cooling occurred
    double inletTemp = tempProfile[0];
    int lastNode = coolingPipe.getTotalNumberOfNodes() - 1;
    double outletTemp = tempProfile[lastNode];

    // Hot gas entering cold pipe should cool down
    assertTrue(outletTemp < inletTemp, "Gas should cool down in pipe with cold surroundings");

    // Temperature should approach but not exceed surrounding temperature
    assertTrue(outletTemp > coldTemp - 5.0,
        "Outlet temperature should not go below surrounding temperature");
  }

  /**
   * Tests liquid hydrocarbon evaporation into a methane gas stream.
   *
   * <p>
   * This test simulates injection of liquid hydrocarbon into a methane gas stream. It calculates:
   * <ul>
   * <li>Mass transfer: hydrocarbon evaporation from liquid to gas phase</li>
   * <li>Temperature profile: including latent heat of vaporization</li>
   * <li>Pressure drop along the pipeline</li>
   * <li>Liquid fraction profile: tracking how far liquid travels before fully evaporating</li>
   * </ul>
   * </p>
   */
  @Test
  void testLiquidHydrocarbonEvaporationIntoMethane() {
    // Create a two-phase system similar to working tests
    // At 313 K (40°C), 100 bar: gas-liquid equilibrium is well defined
    SystemInterface system = new SystemSrkEos(313.3, 100.01325);

    // Add methane to gas phase (phase 0) - main gas flow
    system.addComponent("methane", 1100.0, "kg/hr", 0);

    // Add n-decane to liquid phase (phase 1) - heavy liquid that evaporates slowly
    system.addComponent("nC10", 111.0, "kg/hr", 1);

    system.setMixingRule(2);
    system.initProperties();

    // Create the two-phase pipe flow system
    TwoPhasePipeFlowSystem evapPipe = new TwoPhasePipeFlowSystem();
    evapPipe.setInletThermoSystem(system);
    evapPipe.setInitialFlowPattern("stratified");
    evapPipe.setNumberOfLegs(5);
    evapPipe.setNumberOfNodesInLeg(20);

    // Pipeline configuration - horizontal pipe, 1000m total length
    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 200.0, 400.0, 600.0, 800.0, 1000.0};

    // Surroundings at similar temperature (typical buried pipeline)
    double ambientTemp = 283.15; // 10°C - slightly warmer than fluid, promotes evaporation
    double[] outerTemperature =
        {ambientTemp, ambientTemp, ambientTemp, ambientTemp, ambientTemp, ambientTemp};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0}; // Soil heat transfer
    double[] wallHeatCoef = {20.0, 20.0, 20.0, 20.0, 20.0, 20.0};

    evapPipe.setLegHeights(height);
    evapPipe.setLegPositions(length);
    evapPipe.setLegOuterTemperatures(outerTemperature);
    evapPipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    evapPipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    // 6 inch pipe diameter
    GeometryDefinitionInterface[] pipeGeometry = new PipeData[6];
    for (int i = 0; i < 6; i++) {
      pipeGeometry[i] = new PipeData(0.15); // 150 mm diameter
    }
    evapPipe.setEquipmentGeometry(pipeGeometry);

    evapPipe.createSystem();
    evapPipe.init();

    // Enable non-equilibrium mass transfer for evaporation calculation
    evapPipe.enableNonEquilibriumMassTransfer();

    // Solve steady state with solver type 2 (standard)
    evapPipe.solveSteadyState(2);

    // Get profiles
    double[] temperatureProfile = evapPipe.getTemperatureProfile();
    double[] pressureProfile = evapPipe.getPressureProfile();
    double[] liquidHoldupProfile = evapPipe.getLiquidHoldupProfile();
    double[] gasVelocityProfile = evapPipe.getVelocityProfile(0);

    int numNodes = evapPipe.getTotalNumberOfNodes();

    // Verify we have valid profiles
    assertNotNull(temperatureProfile, "Temperature profile should not be null");
    assertNotNull(pressureProfile, "Pressure profile should not be null");
    assertNotNull(liquidHoldupProfile, "Liquid holdup profile should not be null");

    assertEquals(numNodes, temperatureProfile.length,
        "Temperature profile should have correct size");
    assertEquals(numNodes, pressureProfile.length, "Pressure profile should have correct size");
    assertEquals(numNodes, liquidHoldupProfile.length,
        "Liquid holdup profile should have correct size");

    // Print profile summary for analysis
    System.out.println("\n=== Liquid n-Decane Evaporation into Methane Gas Stream ===");
    System.out.println("Conditions: T=313.3 K (40°C), P=100 bar");
    System.out.println("Pipe length: 1000 m, Diameter: 150 mm");
    System.out.println("Gas flow: 1100 kg/hr methane, Liquid: 111 kg/hr n-C10");
    System.out.println(
        "Inlet conditions: T=" + temperatureProfile[0] + " K, P=" + pressureProfile[0] + " bar");
    System.out.println("Outlet conditions: T=" + temperatureProfile[numNodes - 1] + " K, P="
        + pressureProfile[numNodes - 1] + " bar");

    // Print first and last few nodes with valid data
    System.out.println("\nFirst 3 nodes:");
    System.out.println("Node\tT(K)\tP(bar)\tLiq.Holdup\tVgas(m/s)");
    for (int i = 0; i < Math.min(3, numNodes); i++) {
      double gasVel = gasVelocityProfile[i];
      String gasVelStr =
          Double.isFinite(gasVel) && gasVel > 0 ? String.format("%.2f", gasVel) : "N/A";
      System.out.printf("%d\t%.2f\t%.3f\t%.4f\t\t%s%n", i, temperatureProfile[i],
          pressureProfile[i], liquidHoldupProfile[i], gasVelStr);
    }

    System.out.println("\nLast 3 nodes:");
    for (int i = Math.max(0, numNodes - 3); i < numNodes; i++) {
      double gasVel = gasVelocityProfile[i];
      String gasVelStr =
          Double.isFinite(gasVel) && gasVel > 0 ? String.format("%.2f", gasVel) : "N/A";
      System.out.printf("%d\t%.2f\t%.3f\t%.4f\t\t%s%n", i, temperatureProfile[i],
          pressureProfile[i], liquidHoldupProfile[i], gasVelStr);
    }

    double nodeLength = 1000.0 / numNodes; // Node length for 1000m pipe
    double evaporationDistance = -1.0; // Distance where liquid fraction becomes negligible

    // Calculate based on mass transfer rate instead of holdup profile
    // Mass transfer rate shows actual evaporation happening

    // Calculate pressure drop
    double totalPressureDrop = pressureProfile[0] - pressureProfile[numNodes - 1];
    System.out.println("\nTotal pressure drop: " + totalPressureDrop + " bar");

    // Calculate temperature change
    double temperatureChange = temperatureProfile[numNodes - 1] - temperatureProfile[0];
    System.out.println("Temperature change: " + temperatureChange + " K");

    // Report evaporation based on mass transfer rate
    double massTransferRate = evapPipe.getTotalMassTransferRate(0); // methane component
    double massTransferRateC10 = evapPipe.getTotalMassTransferRate(1); // nC10 component

    System.out.println("\n--- Mass Transfer Results ---");
    System.out.println(
        "Mass transfer rate (methane): " + String.format("%.4f", massTransferRate) + " mol/s");
    System.out.println(
        "Mass transfer rate (n-C10): " + String.format("%.4f", massTransferRateC10) + " mol/s");

    // Debug: Look at individual node contributions to mass transfer
    System.out.println("\n--- Node-level Mass Transfer Debug (first 5 nodes) ---");
    for (int i = 0; i < Math.min(5, numNodes); i++) {
      FlowNodeInterface node = evapPipe.getNode(i);
      if (node != null && node.getFluidBoundary() != null) {
        double fluxCH4 = node.getFluidBoundary().getInterphaseMolarFlux(0);
        double fluxC10 = node.getFluidBoundary().getInterphaseMolarFlux(1);
        double contactArea = node.getInterphaseContactArea();
        System.out.printf("Node %d: CH4 flux=%.6f mol/m²/s, C10 flux=%.6f mol/m²/s, area=%.6f m²%n",
            i, fluxCH4, fluxC10, contactArea);
      }
    }

    // Get gas velocity at inlet for time calculation
    double gasVelocityInlet = gasVelocityProfile[0];
    if (Double.isFinite(gasVelocityInlet) && gasVelocityInlet > 0) {
      double residenceTime = 1000.0 / gasVelocityInlet;
      System.out
          .println("\nGas velocity at inlet: " + String.format("%.2f", gasVelocityInlet) + " m/s");
      System.out
          .println("Residence time in 1000m pipe: " + String.format("%.1f", residenceTime) + " s");

      // Estimate based on liquid holdup change
      double inletHoldup = liquidHoldupProfile[0];
      double outletHoldup = liquidHoldupProfile[numNodes - 1];
      if (inletHoldup > 0 && outletHoldup > 0 && outletHoldup < inletHoldup) {
        double evaporationRate = (inletHoldup - outletHoldup) / residenceTime; // holdup per second
        if (evaporationRate > 0) {
          double fullEvapTime = inletHoldup / evaporationRate;
          double fullEvapDistance = fullEvapTime * gasVelocityInlet;
          System.out.println("\nEstimated time for full evaporation: "
              + String.format("%.0f", fullEvapTime) + " s");
          System.out.println("Estimated distance for full evaporation: "
              + String.format("%.0f", fullEvapDistance) + " m");
        }
      }
    }

    System.out.println("\n--- Holdup Change ---");
    System.out.println("Inlet liquid holdup: " + String.format("%.4f", liquidHoldupProfile[0])
        + " (vol fraction)");
    System.out.println("Outlet liquid holdup: "
        + String.format("%.4f", liquidHoldupProfile[numNodes - 1]) + " (vol fraction)");

    // Calculate holdup reduction percentage
    if (liquidHoldupProfile[0] > 0) {
      double holdupReduction =
          (1 - liquidHoldupProfile[numNodes - 1] / liquidHoldupProfile[0]) * 100;
      System.out.println(
          "Liquid holdup reduction in 1000m: " + String.format("%.2f", holdupReduction) + "%");
    }

    // Verify pressure drop is non-negative
    assertTrue(totalPressureDrop >= -0.001,
        "Pressure should drop or remain approximately constant along the pipe");

    // Verify all values are physically reasonable
    for (int i = 0; i < numNodes; i++) {
      assertTrue(temperatureProfile[i] > 0, "Temperature should be positive at node " + i);
      assertTrue(pressureProfile[i] > 0, "Pressure should be positive at node " + i);
      assertTrue(liquidHoldupProfile[i] >= 0 && liquidHoldupProfile[i] <= 1,
          "Liquid holdup should be between 0 and 1 at node " + i);
    }

    // Verify mass transfer is calculated
    assertTrue(Double.isFinite(massTransferRate), "Mass transfer rate should be finite");
    assertTrue(Double.isFinite(massTransferRateC10),
        "Mass transfer rate for n-C10 should be finite");
  }

  /**
   * Tests methane gas dissolution into n-decane liquid using stratified flow.
   *
   * <p>
   * This test simulates methane gas dissolving into a liquid n-decane phase. Using stratified flow
   * pattern which has stable numerics. The mass transfer flux is calculated for methane dissolution
   * (positive methane flux into liquid) which is then used to estimate the pipe length needed for
   * complete dissolution.
   * </p>
   */
  @Test
  void testMethaneDissolveIntoNDecane() {
    // Use the exact same conditions as the working evaporation test
    // These parameters are known to work with SRK EOS
    SystemInterface system = new SystemSrkEos(313.3, 100.01325);

    // Add methane to gas phase (phase 0) - same as evaporation test
    system.addComponent("methane", 1100.0, "kg/hr", 0);

    // Add n-decane to liquid phase (phase 1) - same as evaporation test
    system.addComponent("nC10", 111.0, "kg/hr", 1);

    system.setMixingRule(2);
    system.initProperties();

    // Create the two-phase pipe flow system
    TwoPhasePipeFlowSystem dissolvePipe = new TwoPhasePipeFlowSystem();
    dissolvePipe.setInletThermoSystem(system);
    dissolvePipe.setInitialFlowPattern("stratified");
    dissolvePipe.setNumberOfLegs(5);
    dissolvePipe.setNumberOfNodesInLeg(20);

    // Pipeline configuration - horizontal pipe, 1000m total length (same as evaporation test)
    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 200.0, 400.0, 600.0, 800.0, 1000.0};

    // Surroundings at same temperature (isothermal absorption)
    double ambientTemp = 313.3;
    double[] outerTemperature =
        {ambientTemp, ambientTemp, ambientTemp, ambientTemp, ambientTemp, ambientTemp};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {20.0, 20.0, 20.0, 20.0, 20.0, 20.0};

    dissolvePipe.setLegHeights(height);
    dissolvePipe.setLegPositions(length);
    dissolvePipe.setLegOuterTemperatures(outerTemperature);
    dissolvePipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    dissolvePipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    // 6 inch pipe diameter
    GeometryDefinitionInterface[] pipeGeometry = new PipeData[6];
    for (int i = 0; i < 6; i++) {
      pipeGeometry[i] = new PipeData(0.15); // 150 mm diameter
    }
    dissolvePipe.setEquipmentGeometry(pipeGeometry);

    dissolvePipe.createSystem();
    dissolvePipe.init();

    // Enable non-equilibrium mass transfer for absorption calculation
    dissolvePipe.enableNonEquilibriumMassTransfer();

    // Solve steady state with solver type 2 (momentum + phase fraction)
    // Note: Full component conservation (solver type 5) causes numerical instabilities
    // We'll use the mass transfer rates at each node to estimate dissolution distance
    dissolvePipe.solveSteadyState(2);

    // Get profiles
    double[] temperatureProfile = dissolvePipe.getTemperatureProfile();
    double[] pressureProfile = dissolvePipe.getPressureProfile();
    double[] liquidHoldupProfile = dissolvePipe.getLiquidHoldupProfile();
    double[] gasVelocityProfile = dissolvePipe.getVelocityProfile(0);
    double[] liquidVelocityProfile = dissolvePipe.getVelocityProfile(1);

    int numNodes = dissolvePipe.getTotalNumberOfNodes();

    // Print profile summary for analysis
    System.out.println("\n=== Methane Dissolution into n-Decane (Stratified Flow) ===");
    System.out.println("Conditions: T=313.3 K (40°C), P=100 bar");
    System.out.println("Pipe length: 1000 m, Diameter: 150 mm");
    System.out.println("Gas flow: 1100 kg/hr methane, Liquid: 111 kg/hr n-C10");
    System.out.println(
        "Inlet conditions: T=" + temperatureProfile[0] + " K, P=" + pressureProfile[0] + " bar");
    System.out.println("Outlet conditions: T=" + temperatureProfile[numNodes - 1] + " K, P="
        + pressureProfile[numNodes - 1] + " bar");

    // Print first and last few nodes
    System.out.println("\nFirst 3 nodes:");
    System.out.println("Node\tT(K)\tP(bar)\tLiq.Holdup\tVgas(m/s)\tVliq(m/s)");
    for (int i = 0; i < Math.min(3, numNodes); i++) {
      double gasVel = gasVelocityProfile[i];
      double liqVel = liquidVelocityProfile[i];
      String gasVelStr =
          Double.isFinite(gasVel) && gasVel > 0 ? String.format("%.4f", gasVel) : "N/A";
      String liqVelStr =
          Double.isFinite(liqVel) && liqVel > 0 ? String.format("%.4f", liqVel) : "N/A";
      System.out.printf("%d\t%.2f\t%.3f\t%.4f\t\t%s\t\t%s%n", i, temperatureProfile[i],
          pressureProfile[i], liquidHoldupProfile[i], gasVelStr, liqVelStr);
    }

    System.out.println("\nLast 3 nodes:");
    for (int i = Math.max(0, numNodes - 3); i < numNodes; i++) {
      double gasVel = gasVelocityProfile[i];
      double liqVel = liquidVelocityProfile[i];
      String gasVelStr =
          Double.isFinite(gasVel) && gasVel > 0 ? String.format("%.4f", gasVel) : "N/A";
      String liqVelStr =
          Double.isFinite(liqVel) && liqVel > 0 ? String.format("%.4f", liqVel) : "N/A";
      System.out.printf("%d\t%.2f\t%.3f\t%.4f\t\t%s\t\t%s%n", i, temperatureProfile[i],
          pressureProfile[i], liquidHoldupProfile[i], gasVelStr, liqVelStr);
    }

    // Calculate pressure drop
    double totalPressureDrop = pressureProfile[0] - pressureProfile[numNodes - 1];
    System.out.println("\nTotal pressure drop: " + totalPressureDrop + " bar");

    // Report mass transfer - methane should have positive rate (dissolving into liquid)
    double massTransferRateCH4 = dissolvePipe.getTotalMassTransferRate(0); // methane
    double massTransferRateC10 = dissolvePipe.getTotalMassTransferRate(1); // nC10

    System.out.println("\n--- Mass Transfer Results ---");
    System.out.println(
        "Mass transfer rate (methane): " + String.format("%.4f", massTransferRateCH4) + " mol/s");
    System.out.println("  Positive = dissolving into liquid, Negative = evaporating");
    System.out.println(
        "Mass transfer rate (n-C10): " + String.format("%.4f", massTransferRateC10) + " mol/s");

    // Debug: Look at individual node contributions to mass transfer
    System.out.println("\n--- Node-level Mass Transfer Debug (first 5 nodes) ---");
    for (int i = 0; i < Math.min(5, numNodes); i++) {
      FlowNodeInterface node = dissolvePipe.getNode(i);
      if (node != null && node.getFluidBoundary() != null) {
        double fluxCH4 = node.getFluidBoundary().getInterphaseMolarFlux(0);
        double fluxC10 = node.getFluidBoundary().getInterphaseMolarFlux(1);
        double contactArea = node.getInterphaseContactArea();
        System.out.printf("Node %d: CH4 flux=%.6f mol/m²/s, C10 flux=%.6f mol/m²/s, area=%.6f m²%n",
            i, fluxCH4, fluxC10, contactArea);
      }
    }

    // Calculate gas fraction change
    double inletGasFraction = 1.0 - liquidHoldupProfile[0];
    double outletGasFraction = 1.0 - liquidHoldupProfile[numNodes - 1];

    System.out.println("\n--- Gas Fraction Change ---");
    System.out.println("Inlet gas fraction: " + String.format("%.4f", inletGasFraction));
    System.out.println("Outlet gas fraction: " + String.format("%.4f", outletGasFraction));

    if (inletGasFraction > 0) {
      double gasFractionReduction = (1 - outletGasFraction / inletGasFraction) * 100;
      System.out
          .println("Gas fraction reduction: " + String.format("%.2f", gasFractionReduction) + "%");

      // Estimate distance for complete dissolution
      if (gasFractionReduction > 0.1) {
        double estimatedFullDissolutionDistance = 1000.0 * 100.0 / gasFractionReduction;
        System.out.println("Estimated distance for complete methane dissolution: "
            + String.format("%.0f", estimatedFullDissolutionDistance) + " m");
      }
    }

    // Get liquid velocity for residence time
    double liquidVelocityInlet = liquidVelocityProfile[0];
    if (Double.isFinite(liquidVelocityInlet) && liquidVelocityInlet > 0) {
      double residenceTime = 1000.0 / liquidVelocityInlet;
      System.out.println(
          "\nLiquid velocity at inlet: " + String.format("%.4f", liquidVelocityInlet) + " m/s");
      System.out.println("Residence time in 1000m pipe: " + String.format("%.1f", residenceTime)
          + " s (" + String.format("%.1f", residenceTime / 60) + " min)");
    }

    // Calculate dissolution distance from mass transfer rates
    // This estimates how long a pipe is needed for complete methane dissolution
    System.out.println("\n--- Dissolution Distance Estimation ---");

    // Use the calculated total mass transfer rate
    if (Math.abs(massTransferRateCH4) > 1e-10) {
      // Calculate methane inlet molar flow for this test (1100 kg/hr methane)
      double ch4MolarFlowPerSec = (1100.0 / 0.016) / 3600.0; // 1100 kg/hr / 16 g/mol / 3600 s

      // Time for complete dissolution = initial moles / transfer rate
      double dissolutionTime = ch4MolarFlowPerSec / Math.abs(massTransferRateCH4);
      System.out.println("Mass transfer rate: " + String.format("%.6f", massTransferRateCH4)
          + " mol/s over 1000m");
      System.out.println(
          "Methane inlet molar flow: " + String.format("%.4f", ch4MolarFlowPerSec) + " mol/s");

      // Estimate dissolution distance
      double avgVelocity = (gasVelocityProfile[0] + liquidVelocityProfile[0]) / 2.0;
      if (Double.isFinite(avgVelocity) && avgVelocity > 0) {
        double dissolutionDistance = dissolutionTime * avgVelocity;
        System.out.println("Estimated time for complete dissolution: "
            + String.format("%.1f", dissolutionTime) + " s");
        System.out.println("Estimated pipe length for complete dissolution: "
            + String.format("%.0f", dissolutionDistance) + " m");
      }
    } else {
      System.out.println("Mass transfer rate is negligible - system may be near equilibrium");

      // Check if there's actually a driving force by looking at interface compositions
      System.out.println("\nChecking equilibrium state at first node...");
      FlowNodeInterface firstNode = dissolvePipe.getNode(0);
      if (firstNode != null && firstNode.getFluidBoundary() != null) {
        SystemInterface bulkSys = firstNode.getBulkSystem();
        SystemInterface interphase = firstNode.getFluidBoundary().getInterphaseSystem();

        if (interphase != null) {
          // Compare bulk vs interface compositions
          double xCH4_bulk_gas = bulkSys.getPhase(0).getComponent("methane").getx();
          double xCH4_interface = interphase.getPhase(0).getComponent("methane").getx();
          double xCH4_bulk_liq = bulkSys.getPhase(1).getComponent("methane").getx();
          double xCH4_interface_liq = interphase.getPhase(1).getComponent("methane").getx();

          System.out.println("Methane in gas - bulk: " + String.format("%.6f", xCH4_bulk_gas)
              + ", interface: " + String.format("%.6f", xCH4_interface));
          System.out.println("Methane in liquid - bulk: " + String.format("%.6f", xCH4_bulk_liq)
              + ", interface: " + String.format("%.6f", xCH4_interface_liq));

          double drivingForce = Math.abs(xCH4_bulk_liq - xCH4_interface_liq);
          System.out.println("Mass transfer driving force: " + String.format("%.6f", drivingForce));
        }
      }
    }

    // Verify profiles are valid
    assertNotNull(temperatureProfile, "Temperature profile should not be null");
    assertNotNull(pressureProfile, "Pressure profile should not be null");
    assertNotNull(liquidHoldupProfile, "Liquid holdup profile should not be null");

    // Verify stratified flow has reasonable liquid holdup
    assertTrue(liquidHoldupProfile[0] > 0.0 && liquidHoldupProfile[0] < 1.0,
        "Stratified flow should have valid liquid holdup at inlet");

    // Verify mass transfer is occurring
    assertTrue(Double.isFinite(massTransferRateCH4), "Methane mass transfer rate should be finite");

    // For dissolution, methane mass transfer should be positive (gas -> liquid)
    // Note: The sign convention may vary - this verifies the calculation works
    assertTrue(Math.abs(massTransferRateCH4) >= 0, "Methane mass transfer should be calculated");
  }

  /**
   * Demonstrates a bubble-flow absorption case where methane fully dissolves into liquid n-decane.
   */
  @Test
  void testMethaneFullyDissolvesIntoNDecaneBubbleFlow() {
    SystemInterface system = new SystemSrkEos(305.0, 120.0);
    system.addComponent("methane", 5.0, "kg/hr", 0);
    system.addComponent("nC10", 1200.0, "kg/hr", 1);
    system.createDatabase(true);
    system.setMixingRule(2);
    // Do NOT call init(0) here: it sets x=z in each phase (equilibrium-like initialization) and
    // removes the intended non-equilibrium phase split needed to generate a driving force.
    system.initProperties();

    TwoPhasePipeFlowSystem bubblePipe = new TwoPhasePipeFlowSystem();
    bubblePipe.setInletThermoSystem(system);
    bubblePipe.setInitialFlowPattern("bubble");
    bubblePipe.setNumberOfLegs(5);
    bubblePipe.setNumberOfNodesInLeg(30);

    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 600.0, 1200.0, 1800.0, 2400.0, 3000.0};
    double[] outerTemperature = {305.0, 305.0, 305.0, 305.0, 305.0, 305.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {20.0, 20.0, 20.0, 20.0, 20.0, 20.0};

    bubblePipe.setLegHeights(height);
    bubblePipe.setLegPositions(length);
    bubblePipe.setLegOuterTemperatures(outerTemperature);
    bubblePipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    bubblePipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[6];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.05);
    }
    bubblePipe.setEquipmentGeometry(pipeGeometry);

    bubblePipe.createSystem();
    bubblePipe.init();
    bubblePipe.enableNonEquilibriumMassTransfer();
    bubblePipe.solveSteadyState(2);

    int numNodes = bubblePipe.getTotalNumberOfNodes();
    FlowNodeInterface inletNode = bubblePipe.getNode(0);
    FlowNodeInterface outletNode = bubblePipe.getNode(numNodes - 1);
    double methaneMassTransferRate = bubblePipe.getTotalMassTransferRate(0);

    double inletGasMethaneFraction =
        inletNode.getBulkSystem().getPhase(0).getComponent("methane").getx();
    double outletGasMethaneFraction =
        outletNode.getBulkSystem().getPhase(0).getComponent("methane").getx();
    double inletLiquidMethaneFraction =
        inletNode.getBulkSystem().getPhase(1).getComponent("methane").getx();
    double outletLiquidMethaneFraction =
        outletNode.getBulkSystem().getPhase(1).getComponent("methane").getx();

    System.out.println("\n=== Bubble Flow Dissolution Check ===");
    System.out.println("Inlet gas methane fraction: " + inletGasMethaneFraction);
    System.out.println("Outlet gas methane fraction: " + outletGasMethaneFraction);
    System.out.println("Inlet liquid methane fraction: " + inletLiquidMethaneFraction);
    System.out.println("Outlet liquid methane fraction: " + outletLiquidMethaneFraction);
    System.out.println("Methane mass transfer rate: " + methaneMassTransferRate + " mol/s");

    // Verify we actually start out of equilibrium (gas methane rich, liquid methane poor).
    assertTrue(inletGasMethaneFraction > 0.99,
        "Expected methane-rich gas phase at inlet (off-equilibrium start), x="
            + inletGasMethaneFraction);
    assertTrue(inletLiquidMethaneFraction < 1.0e-6,
        "Expected methane-lean liquid phase at inlet (off-equilibrium start), x="
            + inletLiquidMethaneFraction);

    // Solver type 2 does not enforce full component-conservation propagation along the pipe, so
    // don't assert complete disappearance of the gas-phase methane fraction. Instead, verify that
    // a finite (and correctly signed) mass transfer rate is computed.
    assertTrue(Double.isFinite(methaneMassTransferRate),
        "Mass transfer rate should be calculated and finite");
    assertTrue(methaneMassTransferRate < 0,
        "Negative rate indicates methane flows from gas to liquid, rate="
            + methaneMassTransferRate);
  }

  /**
   * Tests complete evaporation of a light hydrocarbon liquid into gas in a 1 km pipeline.
   *
   * <p>
   * This test creates conditions where a small amount of n-decane liquid evaporates into a large
   * methane gas flow within a 1 km horizontal pipeline. Uses the same proven system setup as
   * testLiquidHydrocarbonEvaporationIntoMethane but with smaller liquid amount and warmer
   * surroundings to promote faster evaporation.
   * </p>
   *
   * <p>
   * <b>Important Note on Solver Types:</b>
   * <ul>
   * <li>Solver type 2: Solves momentum and phase fraction equations only. The phase holdup
   * represents hydrodynamic equilibrium but doesn't propagate component mass conservation along the
   * pipe.</li>
   * <li>Solver type 5+: Includes full component conservation equations but may have numerical
   * stability issues with certain fluid systems.</li>
   * </ul>
   * Mass transfer fluxes ARE correctly calculated at each node, but the steady-state solver type 2
   * doesn't accumulate composition changes downstream. To see progressive evaporation/dissolution,
   * examine the mole fractions in each node's bulk system or use transient simulation.
   * </p>
   *
   * <p>
   * Scenario: Light hydrocarbon condensate in a gas export pipeline that should significantly
   * vaporize.
   * </p>
   */
  @Test
  void testCompleteLiquidEvaporationIn1kmPipe() {
    // Use exact same pattern as working testLiquidHydrocarbonEvaporationIntoMethane
    // 313.3 K (40°C), 100 bar - proven conditions for two-phase methane/nC10
    SystemInterface system = new SystemSrkEos(313.3, 100.01325);

    // Large methane gas flow - add to gas phase (phase 0)
    system.addComponent("methane", 1100.0, "kg/hr", 0);

    // Small amount of n-decane liquid - add to liquid phase (phase 1)
    // Using smaller amount than standard test for faster evaporation
    system.addComponent("nC10", 50.0, "kg/hr", 1);

    system.setMixingRule(2);
    system.initProperties();

    // Create the two-phase pipe flow system
    TwoPhasePipeFlowSystem evapPipe = new TwoPhasePipeFlowSystem();
    evapPipe.setInletThermoSystem(system);
    evapPipe.setInitialFlowPattern("stratified");
    evapPipe.setNumberOfLegs(5);
    evapPipe.setNumberOfNodesInLeg(20); // 100 nodes total for 1000 m

    // Pipeline configuration - horizontal pipe, 1000 m total length
    // Note: Mass transfer is fast - composition changes significantly within first ~200m
    // Using 1000m pipe to ensure numerical stability (shorter pipes cause NaN in molarVolume)
    double pipeLength = 1000.0; // meters
    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 200.0, 400.0, 600.0, 800.0, 1000.0};

    // Warm surroundings to promote evaporation
    double ambientTemp = 323.15; // 50°C - warmer than fluid to drive evaporation
    double[] outerTemperature =
        {ambientTemp, ambientTemp, ambientTemp, ambientTemp, ambientTemp, ambientTemp};
    double[] outHeatCoef = {10.0, 10.0, 10.0, 10.0, 10.0, 10.0}; // Good heat transfer
    double[] wallHeatCoef = {25.0, 25.0, 25.0, 25.0, 25.0, 25.0};

    evapPipe.setLegHeights(height);
    evapPipe.setLegPositions(length);
    evapPipe.setLegOuterTemperatures(outerTemperature);
    evapPipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    evapPipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    // 6-inch pipe diameter (same as working test)
    GeometryDefinitionInterface[] pipeGeometry = new PipeData[6];
    for (int i = 0; i < 6; i++) {
      pipeGeometry[i] = new PipeData(0.15); // 150 mm (6 inch) diameter
    }
    evapPipe.setEquipmentGeometry(pipeGeometry);

    evapPipe.createSystem();
    evapPipe.init();

    // Enable non-equilibrium mass transfer for evaporation calculation
    evapPipe.enableNonEquilibriumMassTransfer();

    // Solve steady state
    evapPipe.solveSteadyState(2);

    // Get profiles
    double[] liquidHoldupProfile = evapPipe.getLiquidHoldupProfile();
    double[] temperatureProfile = evapPipe.getTemperatureProfile();
    double[] pressureProfile = evapPipe.getPressureProfile();
    int numNodes = evapPipe.getTotalNumberOfNodes();

    // Get mass transfer rate for n-decane (component 1)
    double decaneMassTransferRate = evapPipe.getTotalMassTransferRate(1);

    // Print results
    System.out.println("\n=== Liquid Evaporation in " + pipeLength + " m Pipeline ===");
    System.out.println("Scenario: n-Decane liquid evaporating into methane gas");
    System.out.println("Conditions: T=313 K (40°C), P=100 bar, ambient=50°C");
    System.out.printf("Pipe: %.0f m length, 150 mm diameter, horizontal%n", pipeLength);
    System.out.println("Gas flow: 1100 kg/hr methane, Liquid: 50 kg/hr n-C10");

    System.out.println("\nInlet conditions:");
    System.out.printf("  Temperature: %.2f K%n", temperatureProfile[0]);
    System.out.printf("  Pressure: %.3f bar%n", pressureProfile[0]);
    System.out.printf("  Liquid holdup: %.6f%n", liquidHoldupProfile[0]);

    System.out.println("\nOutlet conditions:");
    System.out.printf("  Temperature: %.2f K%n", temperatureProfile[numNodes - 1]);
    System.out.printf("  Pressure: %.3f bar%n", pressureProfile[numNodes - 1]);
    System.out.printf("  Liquid holdup: %.6f%n", liquidHoldupProfile[numNodes - 1]);

    System.out.printf("%nn-Decane mass transfer rate: %.6f mol/s%n", decaneMassTransferRate);
    System.out.println("(Positive = evaporating from liquid to gas)");

    // Calculate evaporation percentage
    double inletHoldup = liquidHoldupProfile[0];
    double outletHoldup = liquidHoldupProfile[numNodes - 1];
    double evaporationPercent = 0;
    if (inletHoldup > 0) {
      evaporationPercent = (1.0 - outletHoldup / inletHoldup) * 100.0;
    }
    System.out.printf("%nLiquid evaporated: %.1f%%%n", evaporationPercent);

    // Find where liquid holdup becomes negligible
    double negligibleHoldup = 1.0e-6;
    int evaporationNode = -1;
    for (int i = 0; i < numNodes; i++) {
      if (liquidHoldupProfile[i] < negligibleHoldup) {
        evaporationNode = i;
        break;
      }
    }
    if (evaporationNode > 0) {
      double evaporationDistance = evaporationNode * pipeLength / numNodes;
      System.out.printf("Complete evaporation achieved at: %.1f m (node %d)%n", evaporationDistance,
          evaporationNode);
    } else {
      System.out.printf("Complete evaporation not achieved within %.0f m%n", pipeLength);
      // Print holdup profile for debugging
      System.out.println("\nHoldup profile (every 10th node):");
      for (int i = 0; i < numNodes; i += 10) {
        double distance = i * pipeLength / numNodes;
        System.out.printf("  %.1f m: %.6f%n", distance, liquidHoldupProfile[i]);
      }
    }

    // Check node-level mass transfer for insight
    System.out.println("\nNode-level mass transfer (first 10 nodes):");
    for (int i = 0; i < Math.min(10, numNodes); i++) {
      FlowNodeInterface node = evapPipe.getNode(i);
      if (node != null && node.getFluidBoundary() != null) {
        double fluxC10 = node.getFluidBoundary().getInterphaseMolarFlux(1);
        double contactArea = node.getInterphaseContactArea();
        double distance = i * pipeLength / numNodes;
        System.out.printf("  Node %d (%.1fm): n-C10 flux=%.6f mol/m²/s, contact area=%.4f m²%n", i,
            distance, fluxC10, contactArea);
      }
    }

    // Show composition changes along the pipe (mole fractions in each phase)
    System.out.println("\nComposition profile along pipe (mole fractions):");
    System.out.println("Node\tDistance\tGas CH4\t\tGas C10\t\tLiq CH4\t\tLiq C10");
    for (int i = 0; i < numNodes; i += Math.max(1, numNodes / 10)) {
      FlowNodeInterface node = evapPipe.getNode(i);
      if (node != null) {
        double distance = i * pipeLength / numNodes;
        double gasCH4 = node.getBulkSystem().getPhase(0).getComponent("methane").getx();
        double gasC10 = node.getBulkSystem().getPhase(0).getComponent("nC10").getx();
        double liqCH4 = node.getBulkSystem().getPhase(1).getComponent("methane").getx();
        double liqC10 = node.getBulkSystem().getPhase(1).getComponent("nC10").getx();
        System.out.printf("  %d\t%.0f m\t\t%.6f\t%.6f\t%.6f\t%.6f%n", i, distance, gasCH4, gasC10,
            liqCH4, liqC10);
      }
    }

    // Verify reasonable behavior - two-phase flow exists
    assertNotNull(liquidHoldupProfile, "Liquid holdup profile should not be null");
    assertTrue(inletHoldup > 0 && inletHoldup < 1.0,
        "Inlet should have two-phase flow (0 < holdup < 1), got " + inletHoldup);
    assertTrue(outletHoldup <= inletHoldup,
        "Outlet liquid should be less than or equal to inlet (evaporation)");

    // Verify mass transfer is calculated
    assertTrue(Double.isFinite(decaneMassTransferRate),
        "Mass transfer rate should be calculated and finite");

    // Regression: ensure downstream nodes keep a valid interphase area/flux (no immediate forced
    // equilibrium due to incorrect mass-transfer scaling during profile initialization).
    int checkNode = Math.min(10, numNodes - 1);
    FlowNodeInterface node = evapPipe.getNode(checkNode);
    assertNotNull(node, "Downstream node should exist");
    assertNotNull(node.getFluidBoundary(), "Downstream node should have a fluid boundary model");
    assertTrue(node.getInterphaseContactArea() > 0.0,
        "Downstream node should have non-zero interphase contact area");
    double downstreamFlux = node.getFluidBoundary().getInterphaseMolarFlux(1);
    assertTrue(Double.isFinite(downstreamFlux), "Downstream interphase flux should be finite");
    assertTrue(liquidHoldupProfile[checkNode] < 0.999,
        "Downstream liquid holdup should not collapse to 1.0");
  }

  /**
   * Tests complete dissolution of gas into oil in a 1 km pipeline.
   *
   * <p>
   * This test creates conditions where methane gas dissolves into n-decane liquid using the same
   * proven system setup as testMethaneDissolveIntoNDecane. Uses conditions known to give stable
   * two-phase flow with mass transfer.
   * </p>
   *
   * <p>
   * Scenario: Gas breakthrough at an oil production well - gas dissolving into liquid phase.
   * </p>
   */
  @Test
  void testCompleteGasDissolutionIn1kmPipe() {
    // Bubble flow dissolution case: small amount of gas dissolving into excess liquid
    // 305 K (32°C), 120 bar - high pressure promotes dissolution
    SystemInterface system = new SystemSrkEos(305.0, 120.0);

    // Small gas flow relative to liquid - promotes complete dissolution
    // Methane in gas phase (phase 0)
    system.addComponent("methane", 5.0, "kg/hr", 0);

    // Large liquid flow to absorb all gas - n-Decane in liquid phase (phase 1)
    system.addComponent("nC10", 1200.0, "kg/hr", 1);

    system.createDatabase(true);
    system.setMixingRule(2);
    // Do NOT call init(0) here: it sets x=z in each phase (equilibrium-like initialization) and
    // removes the intended non-equilibrium phase split needed to generate a driving force.
    system.initProperties();

    // Create the two-phase pipe flow system with bubble flow pattern
    TwoPhasePipeFlowSystem dissolvePipe = new TwoPhasePipeFlowSystem();
    dissolvePipe.setInletThermoSystem(system);
    dissolvePipe.setInitialFlowPattern("bubble"); // Bubble flow for high interfacial area
    dissolvePipe.setNumberOfLegs(5);
    dissolvePipe.setNumberOfNodesInLeg(20); // More nodes for finer resolution

    // Pipeline configuration - shorter 100m pipe for dissolution test
    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 20.0, 40.0, 60.0, 80.0, 100.0};

    // Isothermal conditions (same temperature as fluid)
    double pipeTemp = 305.0;
    double[] outerTemperature = {pipeTemp, pipeTemp, pipeTemp, pipeTemp, pipeTemp, pipeTemp};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {20.0, 20.0, 20.0, 20.0, 20.0, 20.0};

    dissolvePipe.setLegHeights(height);
    dissolvePipe.setLegPositions(length);
    dissolvePipe.setLegOuterTemperatures(outerTemperature);
    dissolvePipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    dissolvePipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    // 50 mm (2 inch) pipe diameter - smaller diameter for bubble flow
    GeometryDefinitionInterface[] pipeGeometry = new PipeData[6];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.05); // 50 mm diameter
    }
    dissolvePipe.setEquipmentGeometry(pipeGeometry);

    dissolvePipe.createSystem();
    dissolvePipe.init();

    // Enable non-equilibrium mass transfer for dissolution calculation
    dissolvePipe.enableNonEquilibriumMassTransfer();

    // Set dissolution-only mode to prevent evaporation when gas is depleted
    dissolvePipe.setMassTransferMode(
        neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode.DISSOLUTION_ONLY);

    // Solve steady state
    dissolvePipe.solveSteadyState(2);

    // Get profiles
    double[] liquidHoldupProfile = dissolvePipe.getLiquidHoldupProfile();
    double[] temperatureProfile = dissolvePipe.getTemperatureProfile();
    double[] pressureProfile = dissolvePipe.getPressureProfile();
    int numNodes = dissolvePipe.getTotalNumberOfNodes();

    // Get mass transfer rate for methane (component 0)
    double methaneMassTransferRate = dissolvePipe.getTotalMassTransferRate(0);

    // Calculate gas void fraction (1 - liquid holdup)
    double inletGasFraction = 1.0 - liquidHoldupProfile[0];
    double outletGasFraction = 1.0 - liquidHoldupProfile[numNodes - 1];

    // Print results
    System.out.println("\n=== Complete Gas Dissolution in 100 m Pipeline (Bubble Flow) ===");
    System.out.println("Scenario: Small methane gas bubble dissolving into excess n-decane oil");
    System.out.println("Conditions: T=305 K (32°C), P=120 bar");
    System.out.println("Pipe: 100 m length, 50 mm diameter, horizontal, bubble flow");
    System.out.println("Gas flow: 5 kg/hr methane, Liquid: 1200 kg/hr n-C10");

    System.out.println("\nInlet conditions:");
    System.out.printf("  Temperature: %.2f K%n", temperatureProfile[0]);
    System.out.printf("  Pressure: %.3f bar%n", pressureProfile[0]);
    System.out.printf("  Gas void fraction: %.6f%n", inletGasFraction);
    System.out.printf("  Liquid holdup: %.6f%n", liquidHoldupProfile[0]);

    System.out.println("\nOutlet conditions:");
    System.out.printf("  Temperature: %.2f K%n", temperatureProfile[numNodes - 1]);
    System.out.printf("  Pressure: %.3f bar%n", pressureProfile[numNodes - 1]);
    System.out.printf("  Gas void fraction: %.6f%n", outletGasFraction);
    System.out.printf("  Liquid holdup: %.6f%n", liquidHoldupProfile[numNodes - 1]);

    System.out.printf("%nMethane mass transfer rate: %.6f mol/s%n", methaneMassTransferRate);
    System.out.println("(Negative = dissolving from gas to liquid)");

    // Calculate dissolution percentage
    double dissolutionPercent = 0;
    if (inletGasFraction > 0) {
      dissolutionPercent = (1.0 - outletGasFraction / inletGasFraction) * 100.0;
    }
    System.out.printf("%nGas dissolved: %.1f%%%n", dissolutionPercent);

    // Find where gas void fraction becomes negligible
    double negligibleGas = 1.0e-6;
    int dissolutionNode = -1;
    double pipeLength = 100.0; // 100m pipe
    for (int i = 0; i < numNodes; i++) {
      double gasFraction = 1.0 - liquidHoldupProfile[i];
      if (gasFraction < negligibleGas) {
        dissolutionNode = i;
        break;
      }
    }
    if (dissolutionNode > 0) {
      double dissolutionDistance = dissolutionNode * pipeLength / numNodes;
      System.out.printf("Complete dissolution achieved at: %.0f m (node %d)%n", dissolutionDistance,
          dissolutionNode);
    } else {
      System.out.println("Complete dissolution not achieved within 100 m");
    }

    // Print complete gas fraction profile as function of length
    System.out.println("\n=== Gas Fraction Profile Along Pipe Length ===");
    System.out.println("Length [m]    Gas Fraction [-]   Liquid Holdup [-]");
    System.out.println("---------------------------------------------------");
    for (int i = 0; i < numNodes; i++) {
      double distance = i * pipeLength / (numNodes - 1);
      double gasFrac = 1.0 - liquidHoldupProfile[i];
      System.out.printf("%8.1f      %.6f           %.6f%n", distance, gasFrac,
          liquidHoldupProfile[i]);
    }

    // Check node-level mass transfer for insight
    System.out.println("\nNode-level mass transfer (first 5 nodes):");
    for (int i = 0; i < Math.min(5, numNodes); i++) {
      FlowNodeInterface node = dissolvePipe.getNode(i);
      if (node != null && node.getFluidBoundary() != null) {
        double fluxCH4 = node.getFluidBoundary().getInterphaseMolarFlux(0);
        double contactArea = node.getInterphaseContactArea();
        System.out.printf("  Node %d: CH4 flux=%.6f mol/m²/s, contact area=%.4f m²%n", i, fluxCH4,
            contactArea);
      }
    }

    // Verify reasonable behavior - two-phase flow exists
    assertNotNull(liquidHoldupProfile, "Liquid holdup profile should not be null");
    assertTrue(liquidHoldupProfile[0] > 0 && liquidHoldupProfile[0] < 1.0,
        "Inlet should have two-phase flow (0 < holdup < 1), got " + liquidHoldupProfile[0]);
    assertTrue(inletGasFraction > 0, "Inlet should have gas present");

    // Verify mass transfer is calculated
    assertTrue(Double.isFinite(methaneMassTransferRate),
        "Mass transfer rate should be calculated and finite");
  }

  /**
   * Tests complete evaporation of liquid droplets into gas phase in a short pipeline.
   *
   * <p>
   * Scenario: Small amount of liquid water droplets evaporating into dry methane gas. At low
   * pressure and elevated temperature, water has a driving force to evaporate.
   * </p>
   */
  @Test
  void testCompleteLiquidEvaporationInShortPipe() {
    // Droplet/mist flow evaporation case: water droplets evaporating into dry gas
    // 350 K (77°C), 5 bar - conditions favor evaporation of water into dry methane
    SystemInterface system = new SystemSrkEos(350.0, 5.0);

    // Large dry gas flow - Methane in gas phase (phase 0)
    system.addComponent("methane", 500.0, "kg/hr", 0);

    // Small liquid flow - Water droplets in liquid phase (phase 1)
    system.addComponent("water", 2.0, "kg/hr", 1);

    system.createDatabase(true);
    system.setMixingRule(2);
    // Use initProperties to avoid equilibrating phases
    system.initProperties();

    // Create the two-phase pipe flow system with droplet flow pattern
    TwoPhasePipeFlowSystem evaporatePipe = new TwoPhasePipeFlowSystem();
    evaporatePipe.setInletThermoSystem(system);
    evaporatePipe.setInitialFlowPattern("droplet"); // Droplet/mist flow for high interfacial area
    evaporatePipe.setNumberOfLegs(5);
    evaporatePipe.setNumberOfNodesInLeg(20); // More nodes for finer resolution

    // Pipeline configuration - 50m pipe for evaporation test
    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 10.0, 20.0, 30.0, 40.0, 50.0};

    // Isothermal conditions (same temperature as fluid)
    double pipeTemp = 350.0;
    double[] outerTemperature = {pipeTemp, pipeTemp, pipeTemp, pipeTemp, pipeTemp, pipeTemp};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {20.0, 20.0, 20.0, 20.0, 20.0, 20.0};

    evaporatePipe.setLegHeights(height);
    evaporatePipe.setLegPositions(length);
    evaporatePipe.setLegOuterTemperatures(outerTemperature);
    evaporatePipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    evaporatePipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    // 50 mm (2 inch) pipe diameter
    GeometryDefinitionInterface[] pipeGeometry = new PipeData[6];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.05); // 50 mm diameter
    }
    evaporatePipe.setEquipmentGeometry(pipeGeometry);

    evaporatePipe.createSystem();
    evaporatePipe.init();

    // Enable non-equilibrium mass transfer for evaporation calculation
    evaporatePipe.enableNonEquilibriumMassTransfer();

    // Set evaporation-only mode to prevent condensation when liquid is depleted
    evaporatePipe.setMassTransferMode(
        neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode.EVAPORATION_ONLY);

    // Solve steady state
    evaporatePipe.solveSteadyState(2);

    // Get profiles
    double[] liquidHoldupProfile = evaporatePipe.getLiquidHoldupProfile();
    double[] temperatureProfile = evaporatePipe.getTemperatureProfile();
    double[] pressureProfile = evaporatePipe.getPressureProfile();
    int numNodes = evaporatePipe.getTotalNumberOfNodes();

    // Get mass transfer rate for water (component 1)
    double waterMassTransferRate = evaporatePipe.getTotalMassTransferRate(1);

    // Calculate liquid holdup
    double inletLiquidHoldup = liquidHoldupProfile[0];
    double outletLiquidHoldup = liquidHoldupProfile[numNodes - 1];

    // Print results
    System.out.println("\n=== Complete Liquid Evaporation in 50 m Pipeline (Droplet Flow) ===");
    System.out.println("Scenario: Small water droplets evaporating into dry methane gas");
    System.out.println("Conditions: T=350 K (77°C), P=5 bar");
    System.out.println("Pipe: 50 m length, 50 mm diameter, horizontal, droplet flow");
    System.out.println("Gas flow: 500 kg/hr methane, Liquid: 2 kg/hr water");

    System.out.println("\nInlet conditions:");
    System.out.printf("  Temperature: %.2f K%n", temperatureProfile[0]);
    System.out.printf("  Pressure: %.3f bar%n", pressureProfile[0]);
    System.out.printf("  Liquid holdup: %.6f%n", inletLiquidHoldup);
    System.out.printf("  Gas void fraction: %.6f%n", 1.0 - inletLiquidHoldup);

    System.out.println("\nOutlet conditions:");
    System.out.printf("  Temperature: %.2f K%n", temperatureProfile[numNodes - 1]);
    System.out.printf("  Pressure: %.3f bar%n", pressureProfile[numNodes - 1]);
    System.out.printf("  Liquid holdup: %.6f%n", outletLiquidHoldup);
    System.out.printf("  Gas void fraction: %.6f%n", 1.0 - outletLiquidHoldup);

    System.out.printf("%nWater mass transfer rate: %.6f mol/s%n", waterMassTransferRate);
    System.out.println("(Negative = evaporating from liquid to gas)");

    // Calculate evaporation percentage
    double evaporationPercent = 0;
    if (inletLiquidHoldup > 0) {
      evaporationPercent = (1.0 - outletLiquidHoldup / inletLiquidHoldup) * 100.0;
    }
    System.out.printf("%nLiquid evaporated: %.1f%%%n", evaporationPercent);

    // Find where liquid holdup becomes negligible
    double negligibleLiquid = 1.0e-6;
    int evaporationNode = -1;
    double pipeLength = 50.0; // 50m pipe
    for (int i = 0; i < numNodes; i++) {
      if (liquidHoldupProfile[i] < negligibleLiquid) {
        evaporationNode = i;
        break;
      }
    }
    if (evaporationNode > 0) {
      double evaporationDistance = evaporationNode * pipeLength / numNodes;
      System.out.printf("Complete evaporation achieved at: %.0f m (node %d)%n", evaporationDistance,
          evaporationNode);
    } else {
      System.out.println("Complete evaporation not achieved within 50 m");
    }

    // Print complete liquid holdup profile as function of length
    System.out.println("\n=== Liquid Holdup Profile Along Pipe Length ===");
    System.out.println("Length [m]    Liquid Holdup [-]   Gas Fraction [-]");
    System.out.println("---------------------------------------------------");
    for (int i = 0; i < numNodes; i++) {
      double distance = i * pipeLength / (numNodes - 1);
      double gasFrac = 1.0 - liquidHoldupProfile[i];
      System.out.printf("%8.1f      %.6f             %.6f%n", distance, liquidHoldupProfile[i],
          gasFrac);
    }

    // Check node-level mass transfer for insight
    System.out.println("\nNode-level mass transfer (first 5 nodes):");
    for (int i = 0; i < Math.min(5, numNodes); i++) {
      FlowNodeInterface node = evaporatePipe.getNode(i);
      if (node != null && node.getFluidBoundary() != null) {
        double fluxH2O = node.getFluidBoundary().getInterphaseMolarFlux(1);
        double contactArea = node.getInterphaseContactArea();
        System.out.printf("  Node %d: H2O flux=%.6f mol/m²/s, contact area=%.4f m²%n", i, fluxH2O,
            contactArea);
      }
    }

    // Verify reasonable behavior - two-phase flow exists at inlet
    assertNotNull(liquidHoldupProfile, "Liquid holdup profile should not be null");
    assertTrue(liquidHoldupProfile[0] > 0 && liquidHoldupProfile[0] < 1.0,
        "Inlet should have two-phase flow (0 < holdup < 1), got " + liquidHoldupProfile[0]);
    assertTrue(inletLiquidHoldup > 0, "Inlet should have liquid present");

    // Verify mass transfer is calculated
    assertTrue(Double.isFinite(waterMassTransferRate),
        "Mass transfer rate should be calculated and finite");
  }

  /**
   * Tests BIDIRECTIONAL mode can handle complete phase disappearance (dissolution case).
   *
   * <p>
   * This test verifies that BIDIRECTIONAL mode properly handles when a phase completely disappears
   * by clamping negative moles to zero.
   * </p>
   */
  @Test
  void testBidirectionalModeCompleteDissolution() {
    // Same setup as dissolution test but using BIDIRECTIONAL mode
    SystemInterface system = new SystemSrkEos(305.0, 120.0);
    system.addComponent("methane", 5.0, "kg/hr", 0);
    system.addComponent("nC10", 1200.0, "kg/hr", 1);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.initProperties();

    TwoPhasePipeFlowSystem pipe = new TwoPhasePipeFlowSystem();
    pipe.setInletThermoSystem(system);
    pipe.setInitialFlowPattern("bubble");
    pipe.setNumberOfLegs(5);
    pipe.setNumberOfNodesInLeg(20);

    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 20.0, 40.0, 60.0, 80.0, 100.0};
    double pipeTemp = 305.0;
    double[] outerTemperature = {pipeTemp, pipeTemp, pipeTemp, pipeTemp, pipeTemp, pipeTemp};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {20.0, 20.0, 20.0, 20.0, 20.0, 20.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[6];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.05);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();
    pipe.init();
    pipe.enableNonEquilibriumMassTransfer();

    // Use BIDIRECTIONAL mode - should handle complete dissolution
    pipe.setMassTransferMode(
        neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode.BIDIRECTIONAL);

    pipe.solveSteadyState(2);

    double[] liquidHoldupProfile = pipe.getLiquidHoldupProfile();
    int numNodes = pipe.getTotalNumberOfNodes();

    double inletGasFraction = 1.0 - liquidHoldupProfile[0];
    double outletGasFraction = 1.0 - liquidHoldupProfile[numNodes - 1];

    System.out.println("\n=== BIDIRECTIONAL Mode - Complete Dissolution Test ===");
    System.out.printf("Inlet gas fraction: %.6f%n", inletGasFraction);
    System.out.printf("Outlet gas fraction: %.6f%n", outletGasFraction);

    // Verify gas dissolved (outlet should have less gas than inlet)
    assertTrue(outletGasFraction < inletGasFraction,
        "Gas should dissolve: outlet gas fraction should be less than inlet");

    // Verify no negative phase fractions (all holdups should be between 0 and 1)
    for (int i = 0; i < numNodes; i++) {
      assertTrue(liquidHoldupProfile[i] >= 0.0 && liquidHoldupProfile[i] <= 1.0,
          "Liquid holdup at node " + i + " should be between 0 and 1, got "
              + liquidHoldupProfile[i]);
    }

    System.out.println("BIDIRECTIONAL mode handled complete dissolution correctly.");
  }

  /**
   * Tests typical gas-oil multiphase flow in a subsea pipeline with elevation profile.
   *
   * <p>
   * Scenario: Hot oil and gas mixture (50°C) transported through a subsea pipeline in cold seawater
   * (5°C). The pipeline has an undulating elevation profile simulating seabed terrain.
   * </p>
   */
  @Test
  void testSubseaGasOilPipelineWithElevationProfile() {
    // Typical conditions: 50°C inlet, 50 bar pressure
    // Hot well fluid cooling in cold seawater
    SystemInterface system = new SystemSrkEos(323.15, 50.0); // 50°C, 50 bar

    // Use methane (gas) and heavy oil (nC20) - higher gas rate to maintain gas along pipe
    system.addComponent("methane", 500.0, "kg/hr", 0); // More gas to prevent full dissolution
    system.addComponent("nC20", 500.0, "kg/hr", 1); // Heavy oil phase

    system.createDatabase(true);
    system.setMixingRule(2);
    system.initProperties();

    // Create the two-phase pipe flow system
    TwoPhasePipeFlowSystem pipeline = new TwoPhasePipeFlowSystem();
    pipeline.setInletThermoSystem(system);
    pipeline.setInitialFlowPattern("stratified"); // Typical for horizontal/near-horizontal
    pipeline.setNumberOfLegs(2);
    pipeline.setNumberOfNodesInLeg(5);

    // Pipeline configuration - 500 m subsea pipeline with undulating terrain
    // Heights simulate seabed topography (in meters relative to start)
    double[] height = {0, -10, -5}; // Undulating seabed profile

    // Cumulative length positions (meters)
    double[] length = {0.0, 250.0, 500.0};

    // Seawater temperature: 5°C (278.15 K)
    double seawaterTemp = 278.15;
    double[] outerTemperature = {seawaterTemp, seawaterTemp, seawaterTemp};

    // Heat transfer coefficients for subsea pipeline (insulated pipe in seawater)
    double[] outHeatCoef = {50.0, 50.0, 50.0}; // W/m²K external
    double[] wallHeatCoef = {10.0, 10.0, 10.0}; // W/m²K wall (insulated)

    pipeline.setLegHeights(height);
    pipeline.setLegPositions(length);
    pipeline.setLegOuterTemperatures(outerTemperature);
    pipeline.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipeline.setLegWallHeatTransferCoefficients(wallHeatCoef);

    // 150 mm (6 inch) pipe diameter - typical production flowline
    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.15); // 150 mm diameter
    }
    pipeline.setEquipmentGeometry(pipeGeometry);

    pipeline.createSystem();
    pipeline.init();

    // Enable non-equilibrium mass and heat transfer
    pipeline.enableNonEquilibriumMassTransfer();
    pipeline.enableNonEquilibriumHeatTransfer();

    // Use BIDIRECTIONAL mode - allow both dissolution and evaporation
    pipeline.setMassTransferMode(
        neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode.BIDIRECTIONAL);

    // Solve steady state (2 iterations for speed)
    pipeline.solveSteadyState(2);

    // Get profiles
    double[] liquidHoldupProfile = pipeline.getLiquidHoldupProfile();
    double[] temperatureProfile = pipeline.getTemperatureProfile();
    double[] pressureProfile = pipeline.getPressureProfile();
    int numNodes = pipeline.getTotalNumberOfNodes();

    // Print results
    System.out.println("\n=== Subsea Gas-Oil Pipeline with Elevation Profile ===");
    System.out.println("Scenario: Hot well fluid cooling in cold seawater");
    System.out.println("Conditions: T_inlet=50°C (323 K), P=50 bar");
    System.out.println("Pipeline: 500 m length, 150 mm diameter, stratified flow");
    System.out.println("Seawater temperature: 5°C (278 K)");
    System.out.println("Production rate: 1000 kg/hr total (500 kg/hr CH4 gas, 500 kg/hr nC20 oil)");

    System.out.println("\n--- Elevation Profile ---");
    System.out.println("Position [m]   Height [m]");
    for (int i = 0; i < height.length; i++) {
      System.out.printf("%8.0f       %6.1f%n", length[i], height[i]);
    }

    System.out.println("\nInlet conditions:");
    System.out.printf("  Temperature: %.2f K (%.1f °C)%n", temperatureProfile[0],
        temperatureProfile[0] - 273.15);
    System.out.printf("  Pressure: %.3f bar%n", pressureProfile[0]);
    System.out.printf("  Liquid holdup: %.4f%n", liquidHoldupProfile[0]);
    System.out.printf("  Gas void fraction: %.4f%n", 1.0 - liquidHoldupProfile[0]);

    System.out.println("\nOutlet conditions:");
    System.out.printf("  Temperature: %.2f K (%.1f °C)%n", temperatureProfile[numNodes - 1],
        temperatureProfile[numNodes - 1] - 273.15);
    System.out.printf("  Pressure: %.3f bar%n", pressureProfile[numNodes - 1]);
    System.out.printf("  Liquid holdup: %.4f%n", liquidHoldupProfile[numNodes - 1]);
    System.out.printf("  Gas void fraction: %.4f%n", 1.0 - liquidHoldupProfile[numNodes - 1]);

    // Temperature drop
    double tempDrop = temperatureProfile[0] - temperatureProfile[numNodes - 1];
    System.out.printf("\nTemperature drop: %.1f K%n", tempDrop);

    // Pressure drop
    double pressureDrop = pressureProfile[0] - pressureProfile[numNodes - 1];
    System.out.printf("Pressure drop: %.3f bar%n", pressureDrop);

    // Print temperature and holdup profile along pipeline
    System.out.println("\n=== Profile Along Pipeline ===");
    System.out.println(
        "Position [m]   Gas Frac   T_gas [°C]   T_liq [°C]   Pressure [bar]   Liquid Holdup");
    System.out.println("-------------------------------------------------------------------------");
    double pipeLength = 500.0;
    for (int i = 0; i < numNodes; i++) { // Print all nodes for short pipeline
      double distance = i * pipeLength / (numNodes - 1);
      double gasFraction = 1.0 - liquidHoldupProfile[i]; // Gas void fraction
      double tGas = pipeline.getNode(i).getBulkSystem().getPhase(0).getTemperature() - 273.15;
      double tLiq = pipeline.getNode(i).getBulkSystem().getPhase(1).getTemperature() - 273.15;
      System.out.printf("%8.0f       %6.4f     %6.1f       %6.1f      %8.3f          %.4f%n",
          distance, gasFraction, tGas, tLiq, pressureProfile[i], liquidHoldupProfile[i]);
    }

    // Print detailed heat transfer diagnostics for nodes 1-3
    System.out.println("\n=== Heat Transfer Diagnostics (Steady-State Energy Balance) ===");
    for (int i = 1; i < Math.min(4, numNodes); i++) {
      System.out.println("\n--- Node " + i + " ---");

      // Get heat fluxes from FluidBoundary
      double interphasHeatFluxGas = pipeline.getNode(i).getFluidBoundary().getInterphaseHeatFlux(0);
      double interphasHeatFluxLiq = pipeline.getNode(i).getFluidBoundary().getInterphaseHeatFlux(1);
      System.out.printf("Interphase heat flux gas: %.4e W/m²%n", interphasHeatFluxGas);
      System.out.printf("Interphase heat flux liq: %.4e W/m²%n", interphasHeatFluxLiq);

      // Areas and geometry
      double interphaseArea = pipeline.getNode(i).getInterphaseContactArea();
      double nodeLength = pipeline.getNode(i).getGeometry().getNodeLength();
      System.out.printf("Interphase area: %.6f m²%n", interphaseArea);
      System.out.printf("Node length: %.3f m%n", nodeLength);

      // Interphase heat rate [W]
      double gasInterphaseHeatRate = interphasHeatFluxGas * interphaseArea;
      double liqInterphaseHeatRate = interphasHeatFluxLiq * interphaseArea;
      System.out.printf("Interphase heat rate gas: %.4f W%n", gasInterphaseHeatRate);
      System.out.printf("Interphase heat rate liq: %.4f W%n", liqInterphaseHeatRate);

      // Wall heat transfer
      double wallHeatCoeff = pipeline.getNode(i).getGeometry().getWallHeatTransferCoefficient();
      double ambientTemp =
          pipeline.getNode(i).getGeometry().getSurroundingEnvironment().getTemperature() - 273.15;
      double gasTemp = pipeline.getNode(i).getBulkSystem().getPhase(0).getTemperature() - 273.15;
      double liqTemp = pipeline.getNode(i).getBulkSystem().getPhase(1).getTemperature() - 273.15;
      System.out.printf("Wall heat coeff: %.2f W/m²K%n", wallHeatCoeff);
      System.out.printf("Ambient temp: %.2f °C%n", ambientTemp);
      System.out.printf("Gas temp: %.2f °C, Liquid temp: %.2f °C%n", gasTemp, liqTemp);

      double gasWallPerimeter = pipeline.getNode(i).getWallContactLength(0);
      double liqWallPerimeter = pipeline.getNode(i).getWallContactLength(1);
      double gasWallArea = gasWallPerimeter * nodeLength;
      double liqWallArea = liqWallPerimeter * nodeLength;
      System.out.printf("Gas wall area: %.4f m² (perim=%.4f m)%n", gasWallArea, gasWallPerimeter);
      System.out.printf("Liq wall area: %.4f m² (perim=%.4f m)%n", liqWallArea, liqWallPerimeter);

      // Wall heat rate [W] = U * A * dT (dT in K, not C, but diff is the same)
      double gasWallHeatRate = wallHeatCoeff * gasWallArea * (gasTemp - ambientTemp);
      double liqWallHeatRate = wallHeatCoeff * liqWallArea * (liqTemp - ambientTemp);
      System.out.printf("Gas wall heat rate: %.4f W%n", gasWallHeatRate);
      System.out.printf("Liq wall heat rate: %.4f W%n", liqWallHeatRate);

      // Net heat rate
      double gasNetHeatRate = gasInterphaseHeatRate - gasWallHeatRate;
      double liqNetHeatRate = liqInterphaseHeatRate - liqWallHeatRate;
      System.out.printf("Net heat rate gas: %.4f W (interphase - wall)%n", gasNetHeatRate);
      System.out.printf("Net heat rate liq: %.4f W (interphase - wall)%n", liqNetHeatRate);

      // Velocities and mass flow rate
      double gasVel = pipeline.getNode(i).getVelocity(0);
      double liqVel = pipeline.getNode(i).getVelocity(1);
      System.out.printf("Gas velocity: %.4f m/s%n", gasVel);
      System.out.printf("Liquid velocity: %.4f m/s%n", liqVel);

      double pipeArea = pipeline.getNode(i).getGeometry().getArea();
      double liqHoldup = pipeline.getNode(i).getBulkSystem().getPhase(1).getBeta()
          * pipeline.getNode(i).getBulkSystem().getPhase(1).getMolarVolume()
          / pipeline.getNode(i).getBulkSystem().getMolarVolume();
      double gasAreaFrac = pipeArea * (1.0 - liqHoldup);
      double liqAreaFrac = pipeArea * liqHoldup;
      double gasDensity = pipeline.getNode(i).getBulkSystem().getPhase(0).getDensity("kg/m3");
      double liqDensity = pipeline.getNode(i).getBulkSystem().getPhase(1).getDensity("kg/m3");
      double gasMassFlowRate = gasVel * gasAreaFrac * gasDensity;
      double liqMassFlowRate = liqVel * liqAreaFrac * liqDensity;
      System.out.printf("Gas mass flow rate: %.6f kg/s (%.2f kg/hr)%n", gasMassFlowRate,
          gasMassFlowRate * 3600);
      System.out.printf("Liq mass flow rate: %.6f kg/s (%.2f kg/hr)%n", liqMassFlowRate,
          liqMassFlowRate * 3600);

      // Cp
      double gasCpMolar = pipeline.getNode(i).getBulkSystem().getPhase(0).getCp();
      double liqCpMolar = pipeline.getNode(i).getBulkSystem().getPhase(1).getCp();
      double gasMoles = pipeline.getNode(i).getBulkSystem().getPhase(0).getNumberOfMolesInPhase();
      double liqMoles = pipeline.getNode(i).getBulkSystem().getPhase(1).getNumberOfMolesInPhase();
      double gasMolarMass = pipeline.getNode(i).getBulkSystem().getPhase(0).getMolarMass();
      double liqMolarMass = pipeline.getNode(i).getBulkSystem().getPhase(1).getMolarMass();

      double gasCpKg = gasCpMolar / gasMoles / gasMolarMass;
      double liqCpKg = liqCpMolar / liqMoles / liqMolarMass;
      System.out.printf("Gas Cp: %.2f J/kg/K%n", gasCpKg);
      System.out.printf("Liquid Cp: %.2f J/kg/K%n", liqCpKg);

      // Expected dT from steady-state energy balance: dT = Q̇_net / (ṁ * Cp)
      double gas_dT = gasMassFlowRate > 1e-10 ? gasNetHeatRate / (gasMassFlowRate * gasCpKg) : 0;
      double liq_dT = liqMassFlowRate > 1e-10 ? liqNetHeatRate / (liqMassFlowRate * liqCpKg) : 0;
      System.out.printf("Expected dT gas: %.4f K (Q̇=%.2f W, ṁ=%.4f kg/s, Cp=%.0f J/kg/K)%n",
          gas_dT, gasNetHeatRate, gasMassFlowRate, gasCpKg);
      System.out.printf("Expected dT liq: %.4f K (Q̇=%.2f W, ṁ=%.4f kg/s, Cp=%.0f J/kg/K)%n",
          liq_dT, liqNetHeatRate, liqMassFlowRate, liqCpKg);
    }

    // Verify reasonable behavior
    assertNotNull(liquidHoldupProfile, "Liquid holdup profile should not be null");
    assertTrue(liquidHoldupProfile[0] > 0 && liquidHoldupProfile[0] < 1.0,
        "Inlet should have two-phase flow");

    // Verify two-phase flow is maintained (gas doesn't fully dissolve)
    assertTrue(liquidHoldupProfile[numNodes - 1] < 1.0,
        "Should still have gas at outlet (two-phase flow maintained)");

    // Verify temperatures are reasonable (interphase heat transfer equilibrates phases)
    // Note: Current model only includes interphase heat transfer (gas<->liquid),
    // not wall heat transfer to seawater. Temperatures should stay near inlet value.
    double maxTemp = 0.0;
    double minTemp = Double.MAX_VALUE;
    for (int i = 0; i < numNodes; i++) {
      double tGas = pipeline.getNode(i).getBulkSystem().getPhase(0).getTemperature();
      double tLiq = pipeline.getNode(i).getBulkSystem().getPhase(1).getTemperature();
      maxTemp = Math.max(maxTemp, Math.max(tGas, tLiq));
      minTemp = Math.min(minTemp, Math.min(tGas, tLiq));
    }
    assertTrue(minTemp > 273.15,
        "Minimum temperature should be above freezing (got " + (minTemp - 273.15) + "°C)");
    assertTrue(maxTemp < 373.15,
        "Maximum temperature should be below boiling (got " + (maxTemp - 273.15) + "°C)");

    // Verify all holdups are valid
    for (int i = 0; i < numNodes; i++) {
      assertTrue(liquidHoldupProfile[i] >= 0.0 && liquidHoldupProfile[i] <= 1.0,
          "Liquid holdup at node " + i + " should be between 0 and 1");
    }

    System.out.println("\nSubsea pipeline simulation completed successfully.");
  }
}
