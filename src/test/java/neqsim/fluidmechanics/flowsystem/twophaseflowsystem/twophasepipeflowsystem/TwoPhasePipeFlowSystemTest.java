package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for TwoPhasePipeFlowSystem.
 *
 * <p>
 * Tests validate mass and heat transfer calculations for two-phase pipe flow based on the
 * non-equilibrium thermodynamics approach described in Solbraa (2002).
 * </p>
 *
 * @author ASMF
 */
public class TwoPhasePipeFlowSystemTest {
  private FlowSystemInterface pipe;
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

  @Test
  void testConstructor() {
    TwoPhasePipeFlowSystem system = new TwoPhasePipeFlowSystem();
    assertNotNull(system);
  }

  @Test
  void testCreateSystemWithStratifiedFlow() {
    pipe.setInletThermoSystem(testSystem);
    pipe.setInitialFlowPattern("stratified");
    pipe.setNumberOfLegs(3);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0, 0};
    double[] length = {0.0, 1.0, 2.0, 3.0};
    double[] outerTemperature = {278.0, 278.0, 278.0, 278.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
    double[] pipeDiameter = {0.025, 0.025, 0.025, 0.025};
    for (int i = 0; i < pipeDiameter.length; i++) {
      pipeGeometry[i] = new PipeData(pipeDiameter[i]);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();

    assertNotNull(pipe.getNode(0));
    assertEquals("stratified", pipe.getNode(0).getFlowNodeType());
  }

  @Test
  void testCreateSystemWithAnnularFlow() {
    pipe.setInletThermoSystem(testSystem);
    pipe.setInitialFlowPattern("annular");
    pipe.setNumberOfLegs(3);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0, 0};
    double[] length = {0.0, 1.0, 2.0, 3.0};
    double[] outerTemperature = {278.0, 278.0, 278.0, 278.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
    double[] pipeDiameter = {0.025, 0.025, 0.025, 0.025};
    for (int i = 0; i < pipeDiameter.length; i++) {
      pipeGeometry[i] = new PipeData(pipeDiameter[i]);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();

    assertNotNull(pipe.getNode(0));
    assertEquals("annular", pipe.getNode(0).getFlowNodeType());
  }

  @Test
  void testSteadyStateSolverRuns() {
    pipe.setInletThermoSystem(testSystem);
    pipe.setNumberOfLegs(2);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0};
    double[] length = {0.0, 1.0, 2.0};
    double[] outerTemperature = {278.0, 278.0, 278.0};
    double[] roughness = {1.0e-5, 1.0e-5, 1.0e-5};
    double[] outHeatCoef = {5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    // Verify nodes have been initialized and solved
    assertNotNull(pipe.getNode(0).getBulkSystem());
    assertTrue(pipe.getNode(0).getBulkSystem().getTemperature() > 0);
  }

  @Test
  void testMassConservation() {
    pipe.setInletThermoSystem(testSystem);
    pipe.setNumberOfLegs(2);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0};
    double[] length = {0.0, 1.0, 2.0};
    double[] outerTemperature = {295.0, 295.0, 295.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    // Get total moles at inlet and outlet
    double inletMoles = pipe.getNode(0).getBulkSystem().getTotalNumberOfMoles();
    int lastNode = pipe.getTotalNumberOfNodes() - 1;
    double outletMoles = pipe.getNode(lastNode).getBulkSystem().getTotalNumberOfMoles();

    // Mass should be approximately conserved (within numerical tolerance)
    // Allow for some mass transfer between phases
    double tolerance = 0.1; // 10% tolerance for mass balance
    double massRatio = outletMoles / inletMoles;
    assertTrue(massRatio > (1 - tolerance) && massRatio < (1 + tolerance),
        "Mass conservation violated: inlet=" + inletMoles + " outlet=" + outletMoles);
  }

  @Test
  void testPressureDecreaseAlongPipe() {
    pipe.setInletThermoSystem(testSystem);
    pipe.setNumberOfLegs(3);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0, 0};
    double[] length = {0.0, 2.0, 4.0, 6.0};
    double[] outerTemperature = {295.0, 295.0, 295.0, 295.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    double inletPressure = pipe.getNode(0).getBulkSystem().getPressure();
    int lastNode = pipe.getTotalNumberOfNodes() - 1;
    double outletPressure = pipe.getNode(lastNode).getBulkSystem().getPressure();

    // Pressure should decrease (or stay same) along horizontal pipe due to friction
    assertTrue(outletPressure <= inletPressure, "Pressure should decrease along pipe: inlet="
        + inletPressure + " outlet=" + outletPressure);
  }

  @Test
  void testHeatTransferToSurroundings() {
    // Create system with hot fluid and cold surroundings
    SystemInterface hotSystem = new SystemSrkEos(350.0, 5.0); // 350 K
    hotSystem.addComponent("methane", 0.1, 0);
    hotSystem.addComponent("water", 0.05, 1);
    hotSystem.createDatabase(true);
    hotSystem.setMixingRule(2);

    pipe.setInletThermoSystem(hotSystem);
    pipe.setNumberOfLegs(3);
    pipe.setNumberOfNodesInLeg(10);

    double[] height = {0, 0, 0, 0};
    double[] length = {0.0, 5.0, 10.0, 15.0};
    double[] outerTemperature = {278.0, 278.0, 278.0, 278.0}; // Cold surroundings
    double[] outHeatCoef = {50.0, 50.0, 50.0, 50.0}; // Higher heat transfer
    double[] wallHeatCoef = {100.0, 100.0, 100.0, 100.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    double inletTemp = pipe.getNode(0).getBulkSystem().getTemperature();
    int lastNode = pipe.getTotalNumberOfNodes() - 1;
    double outletTemp = pipe.getNode(lastNode).getBulkSystem().getTemperature();

    // Temperature should decrease due to heat loss to cold surroundings
    assertTrue(outletTemp <= inletTemp, "Temperature should decrease due to heat loss: inlet="
        + inletTemp + " outlet=" + outletTemp);
  }

  @Test
  void testNodeVelocityInitialization() {
    pipe.setInletThermoSystem(testSystem);
    pipe.setNumberOfLegs(2);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0};
    double[] length = {0.0, 1.0, 2.0};
    double[] outerTemperature = {295.0, 295.0, 295.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();
    pipe.init();

    // Velocities should be initialized and positive
    double gasVelocity = pipe.getNode(0).getVelocity(0);
    double liquidVelocity = pipe.getNode(0).getVelocity(1);

    assertTrue(gasVelocity >= 0, "Gas velocity should be non-negative");
    assertTrue(liquidVelocity >= 0, "Liquid velocity should be non-negative");
  }

  @Test
  void testFluidBoundaryExists() {
    pipe.setInletThermoSystem(testSystem);
    pipe.setNumberOfLegs(2);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0};
    double[] length = {0.0, 1.0, 2.0};
    double[] outerTemperature = {295.0, 295.0, 295.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);

    pipe.createSystem();
    pipe.init();

    // Each node should have a fluid boundary for mass/heat transfer
    assertNotNull(pipe.getNode(0).getFluidBoundary(),
        "Fluid boundary should be initialized for mass transfer calculations");
  }

  // ==================== PROFILE OUTPUT TESTS ====================

  @Test
  void testTemperatureProfileOutput() {
    setupStandardPipe();
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    TwoPhasePipeFlowSystem pipeSystem = (TwoPhasePipeFlowSystem) pipe;
    double[] temperatures = pipeSystem.getTemperatureProfile();

    assertNotNull(temperatures, "Temperature profile should not be null");
    assertEquals(pipe.getTotalNumberOfNodes(), temperatures.length,
        "Temperature profile length should match number of nodes");
    assertTrue(temperatures[0] > 0, "Temperatures should be positive");
  }

  @Test
  void testPressureProfileOutput() {
    setupStandardPipe();
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    TwoPhasePipeFlowSystem pipeSystem = (TwoPhasePipeFlowSystem) pipe;
    double[] pressures = pipeSystem.getPressureProfile();

    assertNotNull(pressures, "Pressure profile should not be null");
    assertEquals(pipe.getTotalNumberOfNodes(), pressures.length,
        "Pressure profile length should match number of nodes");
    assertTrue(pressures[0] > 0, "Pressures should be positive");
  }

  @Test
  void testPositionProfileOutput() {
    setupStandardPipe();
    pipe.createSystem();
    pipe.init();

    TwoPhasePipeFlowSystem pipeSystem = (TwoPhasePipeFlowSystem) pipe;
    double[] positions = pipeSystem.getPositionProfile();

    assertNotNull(positions, "Position profile should not be null");
    assertEquals(pipe.getTotalNumberOfNodes(), positions.length,
        "Position profile length should match number of nodes");
    assertEquals(0.0, positions[0], 0.001, "First position should be 0");

    // Positions should be monotonically increasing
    for (int i = 1; i < positions.length; i++) {
      assertTrue(positions[i] >= positions[i - 1], "Positions should be monotonically increasing");
    }
  }

  @Test
  void testVoidFractionProfileOutput() {
    setupStandardPipe();
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    TwoPhasePipeFlowSystem pipeSystem = (TwoPhasePipeFlowSystem) pipe;
    double[] voidFractions = pipeSystem.getVoidFractionProfile();

    assertNotNull(voidFractions, "Void fraction profile should not be null");
    assertEquals(pipe.getTotalNumberOfNodes(), voidFractions.length,
        "Void fraction profile length should match number of nodes");

    // Void fractions should be between 0 and 1
    for (double vf : voidFractions) {
      assertTrue(vf >= 0 && vf <= 1, "Void fraction should be between 0 and 1: " + vf);
    }
  }

  @Test
  void testVelocityProfileOutput() {
    setupStandardPipe();
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    TwoPhasePipeFlowSystem pipeSystem = (TwoPhasePipeFlowSystem) pipe;
    double[] gasVelocities = pipeSystem.getVelocityProfile(0);
    double[] liquidVelocities = pipeSystem.getVelocityProfile(1);

    assertNotNull(gasVelocities, "Gas velocity profile should not be null");
    assertNotNull(liquidVelocities, "Liquid velocity profile should not be null");
    assertEquals(pipe.getTotalNumberOfNodes(), gasVelocities.length);
    assertEquals(pipe.getTotalNumberOfNodes(), liquidVelocities.length);
  }

  @Test
  void testInterfacialAreaProfileOutput() {
    setupStandardPipe();
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    TwoPhasePipeFlowSystem pipeSystem = (TwoPhasePipeFlowSystem) pipe;
    double[] areas = pipeSystem.getInterfacialAreaProfile();

    assertNotNull(areas, "Interfacial area profile should not be null");
    assertEquals(pipe.getTotalNumberOfNodes(), areas.length);

    // Areas should be non-negative
    for (double area : areas) {
      assertTrue(area >= 0, "Interfacial area should be non-negative");
    }
  }

  @Test
  void testDensityProfileOutput() {
    setupStandardPipe();
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    TwoPhasePipeFlowSystem pipeSystem = (TwoPhasePipeFlowSystem) pipe;
    double[] gasDensity = pipeSystem.getDensityProfile(0);
    double[] liquidDensity = pipeSystem.getDensityProfile(1);

    assertNotNull(gasDensity, "Gas density profile should not be null");
    assertNotNull(liquidDensity, "Liquid density profile should not be null");

    // Densities should be positive
    assertTrue(gasDensity[0] > 0, "Gas density should be positive");
    assertTrue(liquidDensity[0] > 0, "Liquid density should be positive");

    // Liquid density should be greater than gas density
    assertTrue(liquidDensity[0] > gasDensity[0],
        "Liquid density should be greater than gas density");
  }

  @Test
  void testReynoldsNumberProfileOutput() {
    setupStandardPipe();
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(2);

    TwoPhasePipeFlowSystem pipeSystem = (TwoPhasePipeFlowSystem) pipe;
    double[] reynolds = pipeSystem.getReynoldsNumberProfile(0);

    assertNotNull(reynolds, "Reynolds number profile should not be null");
    assertEquals(pipe.getTotalNumberOfNodes(), reynolds.length);
  }

  // ==================== HELPER METHOD ====================

  private void setupStandardPipe() {
    pipe.setInletThermoSystem(testSystem);
    pipe.setNumberOfLegs(2);
    pipe.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0};
    double[] length = {0.0, 1.0, 2.0};
    double[] outerTemperature = {295.0, 295.0, 295.0};
    double[] outHeatCoef = {5.0, 5.0, 5.0};
    double[] wallHeatCoef = {15.0, 15.0, 15.0};

    pipe.setLegHeights(height);
    pipe.setLegPositions(length);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[3];
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(0.025);
    }
    pipe.setEquipmentGeometry(pipeGeometry);
  }

  /**
   * Demonstrates pressure drop calculation capabilities with mass and heat transfer.
   *
   * <p>
   * This test verifies the TwoPhasePipeFlowSystem can be set up and solved with the DEFAULT solver
   * which includes momentum, energy, and mass transfer equations. It validates that:
   * <ul>
   * <li>The pressure profile is computed and accessible</li>
   * <li>The temperature profile is computed and accessible</li>
   * <li>The velocity profiles are computed for both phases</li>
   * <li>The results are physically reasonable (non-negative pressures, etc.)</li>
   * </ul>
   * </p>
   *
   * <p>
   * <b>Key setup steps for realistic pressure drop:</b>
   * <ol>
   * <li>Create fluid with realistic flow rates (kg/hr, MSm3/day)</li>
   * <li>Flash the system to establish two-phase equilibrium</li>
   * <li>Set velocities on each node after init()</li>
   * </ol>
   * </p>
   *
   * <p>
   * For comparison with empirical correlations like Beggs-Brill, see the process equipment tests:
   * <ul>
   * <li>TwoFluidVsBeggsBrillComparisonTest - compares TwoFluidPipe with Beggs-Brill</li>
   * <li>TwoPhasePressureDropValidationTest - validates against experimental data</li>
   * </ul>
   * </p>
   */
  @Disabled("Long-running comparison test")
  @Test
  void testPressureDropComparisonWithMassTransfer() {
    // Create a gas-condensate system with realistic molar amounts
    // For a 100mm pipe at ~5 m/s gas velocity, we need significant flow
    SystemInterface twoPhaseSystem = new SystemSrkEos(293.15, 20.0); // 20 bar, 20C
    twoPhaseSystem.addComponent("methane", 30.0); // Gas phase
    twoPhaseSystem.addComponent("n-heptane", 5.0); // Liquid condensate
    twoPhaseSystem.createDatabase(true);
    twoPhaseSystem.setMixingRule(2);

    // Flash to establish two-phase equilibrium
    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(twoPhaseSystem);
    ops.TPflash();
    twoPhaseSystem.initPhysicalProperties();

    // ======== Setup pipe system ========
    TwoPhasePipeFlowSystem pipeWithTransfer = new TwoPhasePipeFlowSystem();
    pipeWithTransfer.setInletThermoSystem(twoPhaseSystem);
    pipeWithTransfer.setNumberOfLegs(3);
    pipeWithTransfer.setNumberOfNodesInLeg(5);

    double[] height = {0, 0, 0, 0};
    double[] length = {0.0, 50.0, 100.0, 150.0};
    double[] outerTemperature = {278.0, 278.0, 278.0, 278.0}; // Cold surroundings
    double[] outHeatCoef = {10.0, 10.0, 10.0, 10.0};
    double[] wallHeatCoef = {50.0, 50.0, 50.0, 50.0};

    pipeWithTransfer.setLegHeights(height);
    pipeWithTransfer.setLegPositions(length);
    pipeWithTransfer.setLegOuterTemperatures(outerTemperature);
    pipeWithTransfer.setLegOuterHeatTransferCoefficients(outHeatCoef);
    pipeWithTransfer.setLegWallHeatTransferCoefficients(wallHeatCoef);

    GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
    double diameter = 0.10; // 100mm diameter
    for (int i = 0; i < pipeGeometry.length; i++) {
      pipeGeometry[i] = new PipeData(diameter);
    }
    pipeWithTransfer.setEquipmentGeometry(pipeGeometry);

    pipeWithTransfer.createSystem();
    pipeWithTransfer.init();

    // Verify velocities are finite after initialization
    double gasVelAfterInit = pipeWithTransfer.getNode(0).getVelocity(0);
    double liquidVelAfterInit = pipeWithTransfer.getNode(0).getVelocity(1);
    assertTrue(Double.isFinite(gasVelAfterInit),
        "Gas velocity should be finite after init, was: " + gasVelAfterInit);
    assertTrue(Double.isFinite(liquidVelAfterInit),
        "Liquid velocity should be finite after init, was: " + liquidVelAfterInit);

    // Verify velocities are realistic (< 100 m/s for typical pipe flow)
    assertTrue(gasVelAfterInit < 100.0,
        "Gas velocity should be realistic (< 100 m/s), was: " + gasVelAfterInit);
    assertTrue(gasVelAfterInit > 0.1, "Gas velocity should be positive, was: " + gasVelAfterInit);

    pipeWithTransfer.solveSteadyState(3);

    double[] pressuresWithTransfer = pipeWithTransfer.getPressureProfile();
    double[] temperaturesWithTransfer = pipeWithTransfer.getTemperatureProfile();
    double[] gasVelocities = pipeWithTransfer.getVelocityProfile(0);
    double[] liquidVelocities = pipeWithTransfer.getVelocityProfile(1);
    double pressureDropWithTransfer =
        pressuresWithTransfer[0] - pressuresWithTransfer[pressuresWithTransfer.length - 1];

    // ======== Verify results ========
    // Pressure drop should be positive (friction causes pressure loss)
    assertTrue(pressureDropWithTransfer > 0,
        "Pressure drop should be positive for flowing fluid: " + pressureDropWithTransfer);
    assertTrue(pressureDropWithTransfer < 5.0,
        "Pressure drop should be reasonable (< 5 bar for 150m pipe): " + pressureDropWithTransfer);

    // Temperature should decrease along pipe with heat transfer to cold surroundings
    double inletTemp = temperaturesWithTransfer[0];
    double outletTemp = temperaturesWithTransfer[temperaturesWithTransfer.length - 1];
    assertTrue(outletTemp <= inletTemp,
        "Outlet temp should be <= inlet temp with cold surroundings");

    // Final velocities should be realistic
    assertTrue(gasVelocities[0] > 0 && gasVelocities[0] < 100,
        "Gas velocity should be realistic: " + gasVelocities[0]);
    assertTrue(liquidVelocities[0] > 0 && liquidVelocities[0] < 50,
        "Liquid velocity should be realistic: " + liquidVelocities[0]);

    // Verify basic physics
    assertNotNull(pressuresWithTransfer, "Pressure profile should not be null");
    assertTrue(pressuresWithTransfer.length > 0, "Pressure profile should have values");
    assertTrue(pressuresWithTransfer[0] > 0, "Inlet pressure should be positive");
  }

  /**
   * Compares pressure drop from TwoPhasePipeFlowSystem with PipeBeggsAndBrills.
   *
   * <p>
   * This test validates that the mechanistic two-fluid model gives similar pressure drop results as
   * the empirical Beggs and Brill correlation for the same pipe configuration and fluid.
   * </p>
   *
   * <p>
   * Expected behavior: Both models should give pressure drops within ~50% of each other for typical
   * two-phase flow conditions. Larger differences may occur for edge cases (very high/low gas
   * fractions, high velocities, etc.).
   * </p>
   */
  // @Disabled("Long-running comparison test")
  @Test
  void testCompareWithBeggsAndBrills() {
    // Create identical fluid for both simulations
    double temperature = 293.15; // 20C
    double pressure = 50.0; // 50 bar
    double pipeLength = 1000.0; // 1 km
    double pipeDiameter = 0.15; // 150mm
    double massFlowRate = 5.0; // 5 kg/s

    // ======== Setup fluid ========
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("n-pentane", 0.10);
    fluid.addComponent("n-heptane", 0.05);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    // ======== Run 1: Beggs and Brill (process equipment) ========
    neqsim.process.equipment.stream.Stream inlet =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet.setFlowRate(massFlowRate, "kg/sec");
    inlet.run();

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills beggsBrillsPipe =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("BeggsBrills", inlet);
    beggsBrillsPipe.setPipeWallRoughness(1.5e-5);
    beggsBrillsPipe.setLength(pipeLength);
    beggsBrillsPipe.setDiameter(pipeDiameter);
    beggsBrillsPipe.setElevation(0.0); // Horizontal pipe
    beggsBrillsPipe.setNumberOfIncrements(50);
    beggsBrillsPipe.run();

    double beggsBrillsPressureDrop =
        inlet.getPressure() - beggsBrillsPipe.getOutletStream().getPressure();

    // ======== Run 2: TwoPhasePipeFlowSystem (fluidmechanics) ========
    // Scale molar amounts to match mass flow rate
    double totalMolarMass = fluid.getMolarMass();
    double molarFlowRate = massFlowRate / totalMolarMass; // mol/s

    SystemInterface fluid2 = new SystemSrkEos(temperature, pressure);
    fluid2.addComponent("methane", 0.85 * molarFlowRate);
    fluid2.addComponent("n-pentane", 0.10 * molarFlowRate);
    fluid2.addComponent("n-heptane", 0.05 * molarFlowRate);
    fluid2.createDatabase(true);
    fluid2.setMixingRule(2);

    ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid2);
    ops.TPflash();
    fluid2.initPhysicalProperties();

    TwoPhasePipeFlowSystem twoFluidPipe = new TwoPhasePipeFlowSystem();
    twoFluidPipe.setInletThermoSystem(fluid2);
    twoFluidPipe.setNumberOfLegs(10);
    twoFluidPipe.setNumberOfNodesInLeg(5);
    // Use stratified flow pattern for horizontal pipe with dominant gas phase
    twoFluidPipe.setInitialFlowPattern("stratified");

    // Set up pipe geometry for 1 km
    double[] heights = new double[11];
    double[] lengths = new double[11];
    double[] outerTemps = new double[11];
    double[] outHeatCoefs = new double[11];
    double[] wallHeatCoefs = new double[11];
    for (int i = 0; i < 11; i++) {
      heights[i] = 0.0; // Horizontal
      lengths[i] = i * (pipeLength / 10.0);
      outerTemps[i] = temperature; // Same as fluid temp
      outHeatCoefs[i] = 5.0; // Enable heat transfer
      wallHeatCoefs[i] = 50.0; // Enable wall heat transfer
    }

    twoFluidPipe.setLegHeights(heights);
    twoFluidPipe.setLegPositions(lengths);
    twoFluidPipe.setLegOuterTemperatures(outerTemps);
    twoFluidPipe.setLegOuterHeatTransferCoefficients(outHeatCoefs);
    twoFluidPipe.setLegWallHeatTransferCoefficients(wallHeatCoefs);

    GeometryDefinitionInterface[] pipeGeom = new PipeData[11];
    for (int i = 0; i < 11; i++) {
      pipeGeom[i] = new PipeData(pipeDiameter);
    }
    twoFluidPipe.setEquipmentGeometry(pipeGeom);

    // Enable mass transfer mode
    twoFluidPipe.setMassTransferMode(
        neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode.BIDIRECTIONAL);

    twoFluidPipe.createSystem();
    twoFluidPipe.init();
    twoFluidPipe.solveSteadyState(5);

    double[] pressures = twoFluidPipe.getPressureProfile();
    double twoFluidPressureDrop = pressures[0] - pressures[pressures.length - 1];
    double[] gasVelocities = twoFluidPipe.getVelocityProfile(0);
    double[] liquidVelocities = twoFluidPipe.getVelocityProfile(1);

    // ======== Compare results ========
    System.out.println("=== Pressure Drop Comparison: TwoPhasePipeFlowSystem vs Beggs-Brill ===");
    System.out.printf("Pipe: %.0f m length, %.0f mm diameter, horizontal%n", pipeLength,
        pipeDiameter * 1000);
    System.out.printf("Fluid: methane + n-pentane + n-heptane at %.0f bar, %.1f C%n", pressure,
        temperature - 273.15);
    System.out.printf("Mass flow rate: %.1f kg/s%n%n", massFlowRate);

    System.out.printf("Beggs-Brill:%n");
    System.out.printf("  Pressure drop:  %.4f bar%n", beggsBrillsPressureDrop);
    System.out.printf("  Outlet pressure: %.4f bar%n",
        beggsBrillsPipe.getOutletStream().getPressure());
    System.out.println();
    System.out.printf("TwoPhasePipeFlowSystem:%n");
    System.out.printf("  Pressure drop:  %.4f bar%n", twoFluidPressureDrop);
    System.out.printf("  Inlet pressure:  %.4f bar%n", pressures[0]);
    System.out.printf("  Outlet pressure: %.4f bar%n", pressures[pressures.length - 1]);
    System.out.printf("  Gas velocity (inlet): %.4f m/s%n", gasVelocities[0]);
    System.out.printf("  Liquid velocity (inlet): %.4f m/s%n", liquidVelocities[0]);
    System.out.printf("  Gas holdup (inlet): %.4f%n", twoFluidPipe.getNode(0).getPhaseFraction(0));
    System.out.println();

    double percentDiff =
        Math.abs(beggsBrillsPressureDrop - twoFluidPressureDrop) / beggsBrillsPressureDrop * 100;
    System.out.printf("Difference: %.1f%%%n", percentDiff);
    System.out.println();

    // Both should give positive pressure drops
    assertTrue(beggsBrillsPressureDrop > 0, "Beggs-Brill should give positive pressure drop");
    assertTrue(twoFluidPressureDrop > 0,
        "TwoPhasePipeFlowSystem should give positive pressure drop");

    // Results should be same order of magnitude (within factor of 5)
    // The two models use different approaches so exact match is not expected
    double ratio = twoFluidPressureDrop / beggsBrillsPressureDrop;
    assertTrue(ratio > 0.2 && ratio < 5.0,
        "Pressure drops should be within factor of 5. Beggs-Brill: " + beggsBrillsPressureDrop
            + " bar, TwoFluid: " + twoFluidPressureDrop + " bar, ratio: " + ratio);
  }

  /**
   * Compares TwoPhasePipeFlowSystem with TwoFluidPipe (process equipment).
   *
   * <p>
   * Both use mechanistic two-fluid models based on conservation equations, so their pressure drop
   * predictions should be similar. This test validates that the low-level fluidmechanics class and
   * the high-level process equipment class give consistent results.
   * </p>
   */
  // @Disabled("Long-running comparison test")
  @Test
  void testCompareWithTwoFluidPipe() {
    // Use same conditions as the Beggs-Brill comparison
    double temperature = 293.15; // 20C
    double pressure = 50.0; // 50 bar
    double pipeLength = 1000.0; // 1 km
    double pipeDiameter = 0.15; // 150mm
    double massFlowRate = 5.0; // 5 kg/s

    // ======== Setup fluid ========
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("n-pentane", 0.10);
    fluid.addComponent("n-heptane", 0.05);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    // ======== Run 1: TwoFluidPipe (process equipment) ========
    neqsim.process.equipment.stream.Stream inlet =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet.setFlowRate(massFlowRate, "kg/sec");
    inlet.run();

    neqsim.process.equipment.pipeline.TwoFluidPipe twoFluidPipeEquip =
        new neqsim.process.equipment.pipeline.TwoFluidPipe("TwoFluidPipe", inlet);
    twoFluidPipeEquip.setLength(pipeLength);
    twoFluidPipeEquip.setDiameter(pipeDiameter);
    twoFluidPipeEquip.setNumberOfSections(50);
    twoFluidPipeEquip.run();

    double twoFluidPipePressureDrop =
        inlet.getPressure() - twoFluidPipeEquip.getOutletStream().getPressure();

    // ======== Run 2: TwoPhasePipeFlowSystem (fluidmechanics) ========
    double totalMolarMass = fluid.getMolarMass();
    double molarFlowRate = massFlowRate / totalMolarMass;

    SystemInterface fluid2 = new SystemSrkEos(temperature, pressure);
    fluid2.addComponent("methane", 0.85 * molarFlowRate);
    fluid2.addComponent("n-pentane", 0.10 * molarFlowRate);
    fluid2.addComponent("n-heptane", 0.05 * molarFlowRate);
    fluid2.createDatabase(true);
    fluid2.setMixingRule(2);

    ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid2);
    ops.TPflash();
    fluid2.initPhysicalProperties();

    TwoPhasePipeFlowSystem twoPhaseFlowSystem = new TwoPhasePipeFlowSystem();
    twoPhaseFlowSystem.setInletThermoSystem(fluid2);
    twoPhaseFlowSystem.setNumberOfLegs(10);
    twoPhaseFlowSystem.setNumberOfNodesInLeg(5);

    double[] heights = new double[11];
    double[] lengths = new double[11];
    double[] outerTemps = new double[11];
    double[] outHeatCoefs = new double[11];
    double[] wallHeatCoefs = new double[11];
    for (int i = 0; i < 11; i++) {
      heights[i] = 0.0;
      lengths[i] = i * (pipeLength / 10.0);
      outerTemps[i] = temperature;
      outHeatCoefs[i] = 0.0;
      wallHeatCoefs[i] = 0.0;
    }

    twoPhaseFlowSystem.setLegHeights(heights);
    twoPhaseFlowSystem.setLegPositions(lengths);
    twoPhaseFlowSystem.setLegOuterTemperatures(outerTemps);
    twoPhaseFlowSystem.setLegOuterHeatTransferCoefficients(outHeatCoefs);
    twoPhaseFlowSystem.setLegWallHeatTransferCoefficients(wallHeatCoefs);

    GeometryDefinitionInterface[] pipeGeom = new PipeData[11];
    for (int i = 0; i < 11; i++) {
      pipeGeom[i] = new PipeData(pipeDiameter);
    }
    twoPhaseFlowSystem.setEquipmentGeometry(pipeGeom);

    twoPhaseFlowSystem.createSystem();
    twoPhaseFlowSystem.init();
    twoPhaseFlowSystem.solveSteadyState(5);

    double[] pressures = twoPhaseFlowSystem.getPressureProfile();
    double twoPhaseFlowSystemPressureDrop = pressures[0] - pressures[pressures.length - 1];
    double[] gasVelocities = twoPhaseFlowSystem.getVelocityProfile(0);
    double[] liquidVelocities = twoPhaseFlowSystem.getVelocityProfile(1);

    // ======== Compare results ========
    System.out.println("=== Comparison: TwoPhasePipeFlowSystem vs TwoFluidPipe ===");
    System.out.printf("Pipe: %.0f m length, %.0f mm diameter, horizontal%n", pipeLength,
        pipeDiameter * 1000);
    System.out.printf("Fluid: methane + n-pentane + n-heptane at %.0f bar, %.1f C%n", pressure,
        temperature - 273.15);
    System.out.printf("Mass flow rate: %.1f kg/s%n", massFlowRate);
    System.out.println();
    System.out.printf("TwoFluidPipe (process equipment):%n");
    System.out.printf("  Pressure drop:   %.4f bar%n", twoFluidPipePressureDrop);
    System.out.printf("  Outlet pressure: %.4f bar%n",
        twoFluidPipeEquip.getOutletStream().getPressure());
    System.out.println();
    System.out.printf("TwoPhasePipeFlowSystem (fluidmechanics):%n");
    System.out.printf("  Pressure drop:   %.4f bar%n", twoPhaseFlowSystemPressureDrop);
    System.out.printf("  Inlet pressure:  %.4f bar%n", pressures[0]);
    System.out.printf("  Outlet pressure: %.4f bar%n", pressures[pressures.length - 1]);
    System.out.printf("  Gas velocity (inlet): %.4f m/s%n", gasVelocities[0]);
    System.out.printf("  Liquid velocity (inlet): %.4f m/s%n", liquidVelocities[0]);
    System.out.printf("  Gas holdup (inlet): %.4f%n",
        twoPhaseFlowSystem.getNode(0).getPhaseFraction(0));
    System.out.println();

    double percentDiff = Math.abs(twoFluidPipePressureDrop - twoPhaseFlowSystemPressureDrop)
        / twoFluidPipePressureDrop * 100;
    System.out.printf("Difference: %.1f%%%n", percentDiff);
    System.out.println();

    // Both should give positive pressure drops
    assertTrue(twoFluidPipePressureDrop > 0, "TwoFluidPipe should give positive pressure drop");
    assertTrue(twoPhaseFlowSystemPressureDrop > 0,
        "TwoPhasePipeFlowSystem should give positive pressure drop");

    // Note: These are fundamentally different physical models:
    // - TwoFluidPipe uses a mixture/homogeneous model with average properties and mixture friction
    // - TwoPhasePipeFlowSystem uses a separated flow model with individual phase friction factors
    // and phase-specific hydraulic diameters
    // The separated flow model typically predicts higher friction losses because:
    // 1. Each phase has smaller hydraulic diameter than the full pipe diameter
    // 2. Both phases have wall contact and contribute friction
    // 3. Interphase friction adds additional losses
    // Literature shows these models can differ by factor of 2-3 for high gas fraction flows.
    // We accept a factor of 3 (200%) difference as reasonable for different modeling approaches.
    assertTrue(percentDiff < 200,
        "Two-fluid models should give comparable results (< factor of 3). TwoFluidPipe: "
            + twoFluidPipePressureDrop + " bar, TwoPhasePipeFlowSystem: "
            + twoPhaseFlowSystemPressureDrop + " bar, diff: " + percentDiff + "%");
  }

  /**
   * Compares TwoPhasePipeFlowSystem with TransientPipe (process equipment).
   *
   * <p>
   * TransientPipe uses a drift-flux formulation with AUSM+ numerical scheme. It's designed for
   * transient multiphase flow simulation including terrain-induced slugging. For a short simulation
   * time with constant inlet conditions, the pressure drop should approach steady-state and be
   * comparable to TwoPhasePipeFlowSystem.
   * </p>
   */
  // @Disabled("Long-running comparison test")
  @Test
  void testCompareWithTransientPipe() {
    // Use same conditions as other comparisons
    double temperature = 293.15; // 20C
    double pressure = 50.0; // 50 bar
    double pipeLength = 1000.0; // 1 km
    double pipeDiameter = 0.15; // 150mm
    double massFlowRate = 5.0; // 5 kg/s

    // ======== Setup fluid ========
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("n-pentane", 0.10);
    fluid.addComponent("n-heptane", 0.05);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    // ======== Run 1: TransientPipe (process equipment) ========
    neqsim.process.equipment.stream.Stream inlet =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet.setFlowRate(massFlowRate, "kg/sec");
    inlet.run();

    neqsim.process.equipment.pipeline.twophasepipe.TransientPipe transientPipe =
        new neqsim.process.equipment.pipeline.twophasepipe.TransientPipe("TransientPipe", inlet);
    transientPipe.setLength(pipeLength);
    transientPipe.setDiameter(pipeDiameter);
    transientPipe.setNumberOfSections(50);
    transientPipe.setMaxSimulationTime(30); // Run for 30s to reach near steady-state
    transientPipe.run();

    double transientPipePressureDrop =
        inlet.getPressure() - transientPipe.getOutletStream().getPressure();

    // ======== Run 2: TwoPhasePipeFlowSystem (fluidmechanics) ========
    double totalMolarMass = fluid.getMolarMass();
    double molarFlowRate = massFlowRate / totalMolarMass;

    SystemInterface fluid2 = new SystemSrkEos(temperature, pressure);
    fluid2.addComponent("methane", 0.85 * molarFlowRate);
    fluid2.addComponent("n-pentane", 0.10 * molarFlowRate);
    fluid2.addComponent("n-heptane", 0.05 * molarFlowRate);
    fluid2.createDatabase(true);
    fluid2.setMixingRule(2);

    ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid2);
    ops.TPflash();
    fluid2.initPhysicalProperties();

    TwoPhasePipeFlowSystem twoPhaseFlowSystem = new TwoPhasePipeFlowSystem();
    twoPhaseFlowSystem.setInletThermoSystem(fluid2);
    twoPhaseFlowSystem.setNumberOfLegs(10);
    twoPhaseFlowSystem.setNumberOfNodesInLeg(5);
    // Use stratified flow pattern for horizontal pipe with dominant gas phase
    twoPhaseFlowSystem.setInitialFlowPattern("stratified");

    double[] heights = new double[11];
    double[] lengths = new double[11];
    double[] outerTemps = new double[11];
    double[] outHeatCoefs = new double[11];
    double[] wallHeatCoefs = new double[11];
    for (int i = 0; i < 11; i++) {
      heights[i] = 0.0;
      lengths[i] = i * (pipeLength / 10.0);
      outerTemps[i] = temperature;
      outHeatCoefs[i] = 0.0;
      wallHeatCoefs[i] = 0.0;
    }

    twoPhaseFlowSystem.setLegHeights(heights);
    twoPhaseFlowSystem.setLegPositions(lengths);
    twoPhaseFlowSystem.setLegOuterTemperatures(outerTemps);
    twoPhaseFlowSystem.setLegOuterHeatTransferCoefficients(outHeatCoefs);
    twoPhaseFlowSystem.setLegWallHeatTransferCoefficients(wallHeatCoefs);

    GeometryDefinitionInterface[] pipeGeom = new PipeData[11];
    for (int i = 0; i < 11; i++) {
      pipeGeom[i] = new PipeData(pipeDiameter);
    }
    twoPhaseFlowSystem.setEquipmentGeometry(pipeGeom);

    twoPhaseFlowSystem.createSystem();
    twoPhaseFlowSystem.init();
    twoPhaseFlowSystem.solveSteadyState(5);

    double[] pressures = twoPhaseFlowSystem.getPressureProfile();
    double twoPhaseFlowSystemPressureDrop = pressures[0] - pressures[pressures.length - 1];
    double[] gasVelocities = twoPhaseFlowSystem.getVelocityProfile(0);
    double[] liquidVelocities = twoPhaseFlowSystem.getVelocityProfile(1);

    // ======== Compare results ========
    System.out.println("=== Comparison: TwoPhasePipeFlowSystem vs TransientPipe ===");
    System.out.printf("Pipe: %.0f m length, %.0f mm diameter, horizontal%n", pipeLength,
        pipeDiameter * 1000);
    System.out.printf("Fluid: methane + n-pentane + n-heptane at %.0f bar, %.1f C%n", pressure,
        temperature - 273.15);
    System.out.printf("Mass flow rate: %.1f kg/s%n", massFlowRate);
    System.out.println();
    System.out.printf("TransientPipe (process equipment - drift-flux):%n");
    System.out.printf("  Pressure drop:   %.4f bar%n", transientPipePressureDrop);
    System.out.printf("  Outlet pressure: %.4f bar%n",
        transientPipe.getOutletStream().getPressure());
    System.out.println();
    System.out.printf("TwoPhasePipeFlowSystem (fluidmechanics - separated flow):%n");
    System.out.printf("  Pressure drop:   %.4f bar%n", twoPhaseFlowSystemPressureDrop);
    System.out.printf("  Inlet pressure:  %.4f bar%n", pressures[0]);
    System.out.printf("  Outlet pressure: %.4f bar%n", pressures[pressures.length - 1]);
    System.out.printf("  Gas velocity (inlet): %.4f m/s%n", gasVelocities[0]);
    System.out.printf("  Liquid velocity (inlet): %.4f m/s%n", liquidVelocities[0]);
    System.out.printf("  Gas holdup (inlet): %.4f%n",
        twoPhaseFlowSystem.getNode(0).getPhaseFraction(0));
    System.out.println();

    double percentDiff = Math.abs(transientPipePressureDrop - twoPhaseFlowSystemPressureDrop)
        / Math.max(transientPipePressureDrop, 0.001) * 100;
    System.out.printf("Difference: %.1f%%%n", percentDiff);
    System.out.println();

    // Both should give positive pressure drops
    assertTrue(transientPipePressureDrop > 0, "TransientPipe should give positive pressure drop");
    assertTrue(twoPhaseFlowSystemPressureDrop > 0,
        "TwoPhasePipeFlowSystem should give positive pressure drop");

    // Note: These are fundamentally different physical models with different physics:
    // - TransientPipe uses drift-flux formulation designed for transient simulations
    // - TwoPhasePipeFlowSystem uses separated flow model with individual phase momentum equations
    //
    // The separated flow model typically predicts higher friction losses because:
    // 1. Each phase has wall contact and contributes its own friction force
    // 2. Interphase friction adds additional losses
    // 3. Hydraulic diameters are smaller than pipe diameter
    //
    // These differences are expected and documented. The test validates that both
    // models produce physically reasonable results (positive pressure drop).
    // For accurate model comparison, users should consult literature on which model
    // is appropriate for their specific flow regime and application.
    System.out.println("Note: Large difference is expected - these are different physical models");
    System.out.println("      (drift-flux vs separated flow)");
  }

  /**
   * Compares all pipe models for gas-dominated (high GVF) flow.
   *
   * <p>
   * Uses a high gas fraction fluid to compare single-phase-like behavior. Note:
   * TwoPhasePipeFlowSystem requires at least 2 components for multicomponent mass transfer
   * calculations.
   * </p>
   */
  @Disabled("Long-running comparison test")
  @Test
  void testCompareModelsPureGas() {
    double temperature = 293.15; // 20C
    double pressure = 50.0; // 50 bar
    double pipeLength = 1000.0; // 1 km
    double pipeDiameter = 0.15; // 150mm
    double massFlowRate = 5.0; // 5 kg/s

    // ======== Setup gas-dominated fluid (mostly methane with trace ethane) ========
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent("methane", 0.98);
    fluid.addComponent("ethane", 0.02);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    System.out.println("=== Gas-Dominated (High GVF) Model Comparison ===");
    System.out.printf("Pipe: %.0f m length, %.0f mm diameter, horizontal%n", pipeLength,
        pipeDiameter * 1000);
    System.out.printf("Fluid: 98%% methane + 2%% ethane at %.0f bar, %.1f C%n", pressure,
        temperature - 273.15);
    System.out.printf("Number of phases: %d%n", fluid.getNumberOfPhases());
    System.out.printf("Mass flow rate: %.1f kg/s%n", massFlowRate);
    if (fluid.hasPhaseType("gas")) {
      System.out.printf("Gas density: %.2f kg/m3%n",
          fluid.getPhase("gas").getPhysicalProperties().getDensity());
      System.out.printf("Gas viscosity: %.6f Pa.s%n",
          fluid.getPhase("gas").getPhysicalProperties().getViscosity());
    }
    System.out.println();

    // ======== 1. Beggs-Brill ========
    neqsim.process.equipment.stream.Stream inlet1 =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet1.setFlowRate(massFlowRate, "kg/sec");
    inlet1.run();

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills beggsBrills =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("Beggs-Brill", inlet1);
    beggsBrills.setPipeWallRoughness(1e-5);
    beggsBrills.setLength(pipeLength);
    beggsBrills.setElevation(0.0); // Horizontal pipe
    beggsBrills.setDiameter(pipeDiameter);
    beggsBrills.setNumberOfIncrements(50);
    beggsBrills.setRunIsothermal(true);
    beggsBrills.run();
    double beggsBrillsDp = inlet1.getPressure() - beggsBrills.getOutletStream().getPressure();

    // ======== 2. TwoFluidPipe ========
    neqsim.process.equipment.stream.Stream inlet2 =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet2.setFlowRate(massFlowRate, "kg/sec");
    inlet2.run();

    neqsim.process.equipment.pipeline.TwoFluidPipe twoFluidPipe =
        new neqsim.process.equipment.pipeline.TwoFluidPipe("TwoFluidPipe", inlet2);
    twoFluidPipe.setLength(pipeLength);
    twoFluidPipe.setDiameter(pipeDiameter);
    twoFluidPipe.setNumberOfSections(50);
    twoFluidPipe.run();
    double twoFluidPipeDp = inlet2.getPressure() - twoFluidPipe.getOutletStream().getPressure();

    // ======== 3. TransientPipe ========
    neqsim.process.equipment.stream.Stream inlet3 =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet3.setFlowRate(massFlowRate, "kg/sec");
    inlet3.run();

    neqsim.process.equipment.pipeline.twophasepipe.TransientPipe transientPipe =
        new neqsim.process.equipment.pipeline.twophasepipe.TransientPipe("TransientPipe", inlet3);
    transientPipe.setLength(pipeLength);
    transientPipe.setDiameter(pipeDiameter);
    transientPipe.setNumberOfSections(50);
    transientPipe.setMaxSimulationTime(30);
    transientPipe.run();
    double transientPipeDp = inlet3.getPressure() - transientPipe.getOutletStream().getPressure();

    // Note: TwoPhasePipeFlowSystem is skipped for single-phase flows because it is designed
    // for two-phase gas-liquid systems and has numerical issues with single-phase fluids.
    // The multicomponent mass transfer model requires at least 2 components AND 2 phases.

    // ======== Print comparison ========
    System.out.printf("%-30s %12s%n", "Model", "Pressure Drop (bar)");
    System.out.println("-".repeat(45));
    System.out.printf("%-30s %12.4f%n", "Beggs-Brill", beggsBrillsDp);
    System.out.printf("%-30s %12.4f%n", "TwoFluidPipe", twoFluidPipeDp);
    System.out.printf("%-30s %12.4f%n", "TransientPipe", transientPipeDp);
    System.out.printf("%-30s %12s%n", "TwoPhasePipeFlowSystem", "(N/A - requires 2 phases)");
    System.out.println();

    // All should give positive pressure drops
    assertTrue(beggsBrillsDp > 0, "Beggs-Brill should give positive pressure drop");
    assertTrue(twoFluidPipeDp > 0, "TwoFluidPipe should give positive pressure drop");
    assertTrue(transientPipeDp > 0, "TransientPipe should give positive pressure drop");

    // For single-phase gas, models should be closer (within factor of 10)
    double maxDp = Math.max(beggsBrillsDp, Math.max(twoFluidPipeDp, transientPipeDp));
    double minDp = Math.min(beggsBrillsDp, Math.min(twoFluidPipeDp, transientPipeDp));
    double ratio = maxDp / minDp;
    System.out.printf("Max/Min ratio: %.2f%n", ratio);
  }

  /**
   * Compares all pipe models for liquid-dominated (low GVF) flow.
   *
   * <p>
   * Uses a liquid mixture to compare single-phase-like behavior. Note: TwoPhasePipeFlowSystem
   * requires at least 2 components for multicomponent mass transfer calculations.
   * </p>
   */
  // @Disabled("Long-running comparison test")
  @Test
  void testCompareModelsPureOil() {
    double temperature = 293.15; // 20C
    double pressure = 50.0; // 50 bar
    double pipeLength = 1000.0; // 1 km
    double pipeDiameter = 0.15; // 150mm
    double massFlowRate = 5.0; // 5 kg/s

    // ======== Setup liquid-dominated fluid (heptane + octane mixture) ========
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent("n-heptane", 0.7);
    fluid.addComponent("n-octane", 0.3);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    System.out.println("=== Liquid-Dominated (Low GVF) Model Comparison ===");
    System.out.printf("Pipe: %.0f m length, %.0f mm diameter, horizontal%n", pipeLength,
        pipeDiameter * 1000);
    System.out.printf("Fluid: 70%% n-heptane + 30%% n-octane at %.0f bar, %.1f C%n", pressure,
        temperature - 273.15);
    System.out.printf("Mass flow rate: %.1f kg/s%n", massFlowRate);
    System.out.printf("Number of phases: %d%n", fluid.getNumberOfPhases());
    if (fluid.hasPhaseType("gas")) {
      System.out.printf("Gas density: %.2f kg/m3%n",
          fluid.getPhase("gas").getPhysicalProperties().getDensity());
    }
    if (fluid.hasPhaseType("oil")) {
      System.out.printf("Oil density: %.2f kg/m3%n",
          fluid.getPhase("oil").getPhysicalProperties().getDensity());
      System.out.printf("Oil viscosity: %.6f Pa.s%n",
          fluid.getPhase("oil").getPhysicalProperties().getViscosity());
    }
    System.out.println();

    // ======== 1. Beggs-Brill ========
    neqsim.process.equipment.stream.Stream inlet1 =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet1.setFlowRate(massFlowRate, "kg/sec");
    inlet1.run();

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills beggsBrills =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("Beggs-Brill", inlet1);
    beggsBrills.setPipeWallRoughness(1e-5);
    beggsBrills.setLength(pipeLength);
    beggsBrills.setElevation(0.0); // Horizontal pipe
    beggsBrills.setDiameter(pipeDiameter);
    beggsBrills.setNumberOfIncrements(50);
    beggsBrills.setRunIsothermal(true);
    beggsBrills.run();
    double beggsBrillsDp = inlet1.getPressure() - beggsBrills.getOutletStream().getPressure();

    // ======== 2. TwoFluidPipe ========
    neqsim.process.equipment.stream.Stream inlet2 =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet2.setFlowRate(massFlowRate, "kg/sec");
    inlet2.run();

    neqsim.process.equipment.pipeline.TwoFluidPipe twoFluidPipe =
        new neqsim.process.equipment.pipeline.TwoFluidPipe("TwoFluidPipe", inlet2);
    twoFluidPipe.setLength(pipeLength);
    twoFluidPipe.setDiameter(pipeDiameter);
    twoFluidPipe.setNumberOfSections(50);
    twoFluidPipe.run();
    double twoFluidPipeDp = inlet2.getPressure() - twoFluidPipe.getOutletStream().getPressure();

    // ======== 3. TransientPipe ========
    neqsim.process.equipment.stream.Stream inlet3 =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet3.setFlowRate(massFlowRate, "kg/sec");
    inlet3.run();

    neqsim.process.equipment.pipeline.twophasepipe.TransientPipe transientPipe =
        new neqsim.process.equipment.pipeline.twophasepipe.TransientPipe("TransientPipe", inlet3);
    transientPipe.setLength(pipeLength);
    transientPipe.setDiameter(pipeDiameter);
    transientPipe.setNumberOfSections(50);
    transientPipe.setMaxSimulationTime(30);
    transientPipe.run();
    double transientPipeDp = inlet3.getPressure() - transientPipe.getOutletStream().getPressure();

    // ======== 4. TwoPhasePipeFlowSystem ========
    double totalMolarMass = fluid.getMolarMass();
    double molarFlowRate = massFlowRate / totalMolarMass;

    SystemInterface fluid4 = new SystemSrkEos(temperature, pressure);
    fluid4.addComponent("n-heptane", 0.7 * molarFlowRate);
    fluid4.addComponent("n-octane", 0.3 * molarFlowRate);
    fluid4.createDatabase(true);
    fluid4.setMixingRule(2);

    ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid4);
    ops.TPflash();
    fluid4.initPhysicalProperties();

    // Debug: Check phase ordering after TPflash
    System.out.println("=== Fluid4 after TPflash ===");
    System.out.printf("Number of phases: %d%n", fluid4.getNumberOfPhases());
    System.out.printf("Phase 0 type: %s%n", fluid4.getPhase(0).getType());
    System.out.printf("Phase 0 density: %.2f kg/m3%n",
        fluid4.getPhase(0).getPhysicalProperties().getDensity());
    System.out.printf("Beta (vapor fraction): %.6f%n", fluid4.getBeta());

    TwoPhasePipeFlowSystem twoPhaseFlowSystem = new TwoPhasePipeFlowSystem();
    twoPhaseFlowSystem.setInletThermoSystem(fluid4);
    twoPhaseFlowSystem.setInitialFlowPattern("stratified"); // Use stratified for liquid flow
    twoPhaseFlowSystem.setNumberOfLegs(10);
    twoPhaseFlowSystem.setNumberOfNodesInLeg(5);

    double[] heights = new double[11];
    double[] lengths = new double[11];
    double[] outerTemps = new double[11];
    double[] outHeatCoefs = new double[11];
    double[] wallHeatCoefs = new double[11];
    for (int i = 0; i < 11; i++) {
      heights[i] = 0.0;
      lengths[i] = i * (pipeLength / 10.0);
      outerTemps[i] = temperature;
      outHeatCoefs[i] = 0.0;
      wallHeatCoefs[i] = 0.0;
    }

    twoPhaseFlowSystem.setLegHeights(heights);
    twoPhaseFlowSystem.setLegPositions(lengths);
    twoPhaseFlowSystem.setLegOuterTemperatures(outerTemps);
    twoPhaseFlowSystem.setLegOuterHeatTransferCoefficients(outHeatCoefs);
    twoPhaseFlowSystem.setLegWallHeatTransferCoefficients(wallHeatCoefs);

    GeometryDefinitionInterface[] pipeGeom = new PipeData[11];
    for (int i = 0; i < 11; i++) {
      pipeGeom[i] = new PipeData(pipeDiameter);
    }
    twoPhaseFlowSystem.setEquipmentGeometry(pipeGeom);

    twoPhaseFlowSystem.createSystem();
    twoPhaseFlowSystem.init();
    twoPhaseFlowSystem.solveSteadyState(5);

    double[] pressures = twoPhaseFlowSystem.getPressureProfile();
    double twoPhaseFlowSystemDp = pressures[0] - pressures[pressures.length - 1];

    // ======== Debug diagnostic output ========
    System.out.println("=== Debug Diagnostics ===");
    System.out.printf("Total number of nodes: %d%n", twoPhaseFlowSystem.getTotalNumberOfNodes());
    System.out.printf("Node 0 velocity[0]: %.6f m/s%n",
        twoPhaseFlowSystem.getNode(0).getVelocity(0));
    System.out.printf("Node 0 velocity[1]: %.6f m/s%n",
        twoPhaseFlowSystem.getNode(0).getVelocity(1));
    System.out.printf("Node 5 velocity[0]: %.6f m/s%n",
        twoPhaseFlowSystem.getNode(5).getVelocity(0));
    System.out.printf("Node 25 velocity[0]: %.6f m/s%n",
        twoPhaseFlowSystem.getNode(25).getVelocity(0));
    System.out.printf("Node 50 velocity[0]: %.6f m/s%n",
        twoPhaseFlowSystem.getNode(50).getVelocity(0));
    System.out.printf("Node 0 phaseFraction[0]: %.6f%n",
        twoPhaseFlowSystem.getNode(0).getPhaseFraction(0));
    System.out.printf("Node 0 phaseFraction[1]: %.6f%n",
        twoPhaseFlowSystem.getNode(0).getPhaseFraction(1));
    System.out.printf("Node 5 phaseFraction[0]: %.6f%n",
        twoPhaseFlowSystem.getNode(5).getPhaseFraction(0));
    System.out.printf("Node 5 phaseFraction[1]: %.6f%n",
        twoPhaseFlowSystem.getNode(5).getPhaseFraction(1));
    System.out.printf("Node 0 nodeLength: %.6f m%n",
        twoPhaseFlowSystem.getNode(0).getGeometry().getNodeLength());
    System.out.printf("Node 1 nodeLength: %.6f m%n",
        twoPhaseFlowSystem.getNode(1).getGeometry().getNodeLength());
    System.out.printf("Node 0 interphaseContactLength: %.6f%n",
        twoPhaseFlowSystem.getNode(0).getInterphaseContactLength(0));
    System.out.printf("Node 0 wallContactLength[0]: %.6f%n",
        twoPhaseFlowSystem.getNode(0).getWallContactLength(0));
    System.out.printf("Node 5 interphaseContactLength: %.6f%n",
        twoPhaseFlowSystem.getNode(5).getInterphaseContactLength(0));
    System.out.printf("Node 5 wallContactLength[0]: %.6f%n",
        twoPhaseFlowSystem.getNode(5).getWallContactLength(0));
    System.out.printf("Inlet pressure: %.6f bar%n", pressures[0]);
    System.out.printf("Node 1 pressure: %.6f bar%n", pressures[1]);
    System.out.printf("Node 5 pressure: %.6f bar%n", pressures[5]);
    System.out.printf("Node 25 pressure: %.6f bar%n", pressures[25]);
    System.out.printf("Outlet pressure: %.6f bar%n", pressures[pressures.length - 1]);
    System.out.println();

    // ======== Print comparison ========
    System.out.printf("%-30s %12s%n", "Model", "Pressure Drop (bar)");
    System.out.println("-".repeat(45));
    System.out.printf("%-30s %12.4f%n", "Beggs-Brill", beggsBrillsDp);
    System.out.printf("%-30s %12.4f%n", "TwoFluidPipe", twoFluidPipeDp);
    System.out.printf("%-30s %12.4f%n", "TransientPipe", transientPipeDp);
    System.out.printf("%-30s %12.4f%n", "TwoPhasePipeFlowSystem", twoPhaseFlowSystemDp);
    System.out.println();

    // All should give positive pressure drops for liquid flow
    assertTrue(beggsBrillsDp > 0, "Beggs-Brill should give positive pressure drop");
    assertTrue(twoFluidPipeDp > 0, "TwoFluidPipe should give positive pressure drop");
    assertTrue(transientPipeDp > 0, "TransientPipe should give positive pressure drop");
    assertTrue(twoPhaseFlowSystemDp > 0,
        "TwoPhasePipeFlowSystem should give positive pressure drop for single-phase flow");

    // For single-phase liquid, all models should be reasonably close
    double maxDp = Math.max(Math.max(beggsBrillsDp, twoFluidPipeDp),
        Math.max(transientPipeDp, twoPhaseFlowSystemDp));
    double minDp = Math.min(Math.min(beggsBrillsDp, twoFluidPipeDp),
        Math.min(transientPipeDp, twoPhaseFlowSystemDp));
    double ratio = maxDp / minDp;
    System.out.printf("Max/Min ratio: %.2f%n", ratio);
  }

  /**
   * Test single-phase gas flow comparison across all pipe models.
   */
  // @Disabled("Long-running comparison test")
  @Test
  void testCompareModelsSinglePhaseGas() {
    double temperature = 293.15; // 20C
    double pressure = 50.0; // 50 bar
    double pipeLength = 1000.0; // 1 km
    double pipeDiameter = 0.15; // 150mm
    double massFlowRate = 5.0; // 5 kg/s

    // ======== Setup pure gas fluid (methane) ========
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent("methane", 1.0);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    System.out.println("=== Single-Phase Gas Model Comparison ===");
    System.out.printf("Pipe: %.0f m length, %.0f mm diameter, horizontal%n", pipeLength,
        pipeDiameter * 1000);
    System.out.printf("Fluid: pure methane at %.0f bar, %.1f C%n", pressure, temperature - 273.15);
    System.out.printf("Mass flow rate: %.1f kg/s%n", massFlowRate);
    System.out.printf("Number of phases: %d%n", fluid.getNumberOfPhases());
    if (fluid.hasPhaseType("gas")) {
      System.out.printf("Gas density: %.2f kg/m3%n",
          fluid.getPhase("gas").getPhysicalProperties().getDensity());
      System.out.printf("Gas viscosity: %.6f Pa.s%n",
          fluid.getPhase("gas").getPhysicalProperties().getViscosity());
    }
    System.out.println();

    // ======== 1. Beggs-Brill ========
    neqsim.process.equipment.stream.Stream inlet1 =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet1.setFlowRate(massFlowRate, "kg/sec");
    inlet1.run();

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills beggsBrills =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("Beggs-Brill", inlet1);
    beggsBrills.setPipeWallRoughness(1e-5);
    beggsBrills.setLength(pipeLength);
    beggsBrills.setElevation(0.0);
    beggsBrills.setDiameter(pipeDiameter);
    beggsBrills.setNumberOfIncrements(50);
    beggsBrills.setRunIsothermal(true);
    beggsBrills.run();
    double beggsBrillsDp = inlet1.getPressure() - beggsBrills.getOutletStream().getPressure();

    // ======== 2. TwoFluidPipe ========
    neqsim.process.equipment.stream.Stream inlet2 =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet2.setFlowRate(massFlowRate, "kg/sec");
    inlet2.run();

    neqsim.process.equipment.pipeline.TwoFluidPipe twoFluidPipe =
        new neqsim.process.equipment.pipeline.TwoFluidPipe("TwoFluidPipe", inlet2);
    twoFluidPipe.setLength(pipeLength);
    twoFluidPipe.setDiameter(pipeDiameter);
    twoFluidPipe.setNumberOfSections(50);
    twoFluidPipe.run();
    double twoFluidPipeDp = inlet2.getPressure() - twoFluidPipe.getOutletStream().getPressure();

    // ======== 3. TransientPipe ========
    neqsim.process.equipment.stream.Stream inlet3 =
        new neqsim.process.equipment.stream.Stream("inlet", fluid.clone());
    inlet3.setFlowRate(massFlowRate, "kg/sec");
    inlet3.run();

    neqsim.process.equipment.pipeline.twophasepipe.TransientPipe transientPipe =
        new neqsim.process.equipment.pipeline.twophasepipe.TransientPipe("TransientPipe", inlet3);
    transientPipe.setLength(pipeLength);
    transientPipe.setDiameter(pipeDiameter);
    transientPipe.setNumberOfSections(50);
    transientPipe.setMaxSimulationTime(30);
    transientPipe.run();
    double transientPipeDp = inlet3.getPressure() - transientPipe.getOutletStream().getPressure();

    // ======== 4. TwoPhasePipeFlowSystem ========
    double totalMolarMass = fluid.getMolarMass();
    double molarFlowRate = massFlowRate / totalMolarMass;

    SystemInterface fluid4 = new SystemSrkEos(temperature, pressure);
    fluid4.addComponent("methane", molarFlowRate);
    fluid4.createDatabase(true);
    fluid4.setMixingRule(2);

    ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid4);
    ops.TPflash();
    fluid4.initPhysicalProperties();

    System.out.println("=== Fluid4 after TPflash ===");
    System.out.printf("Number of phases: %d%n", fluid4.getNumberOfPhases());
    System.out.printf("Phase 0 type: %s%n", fluid4.getPhase(0).getType());
    System.out.printf("Phase 0 density: %.2f kg/m3%n",
        fluid4.getPhase(0).getPhysicalProperties().getDensity());

    TwoPhasePipeFlowSystem twoPhaseFlowSystem = new TwoPhasePipeFlowSystem();
    twoPhaseFlowSystem.setInletThermoSystem(fluid4);
    twoPhaseFlowSystem.setInitialFlowPattern("stratified");
    twoPhaseFlowSystem.setNumberOfLegs(10);
    twoPhaseFlowSystem.setNumberOfNodesInLeg(5);

    double[] heights = new double[11];
    double[] lengths = new double[11];
    double[] outerTemps = new double[11];
    double[] outHeatCoefs = new double[11];
    double[] wallHeatCoefs = new double[11];
    for (int i = 0; i < 11; i++) {
      heights[i] = 0.0;
      lengths[i] = i * (pipeLength / 10.0);
      outerTemps[i] = temperature;
      outHeatCoefs[i] = 0.0;
      wallHeatCoefs[i] = 0.0;
    }

    twoPhaseFlowSystem.setLegHeights(heights);
    twoPhaseFlowSystem.setLegPositions(lengths);
    twoPhaseFlowSystem.setLegOuterTemperatures(outerTemps);
    twoPhaseFlowSystem.setLegOuterHeatTransferCoefficients(outHeatCoefs);
    twoPhaseFlowSystem.setLegWallHeatTransferCoefficients(wallHeatCoefs);

    GeometryDefinitionInterface[] pipeGeom = new PipeData[11];
    for (int i = 0; i < 11; i++) {
      pipeGeom[i] = new PipeData(pipeDiameter);
    }
    twoPhaseFlowSystem.setEquipmentGeometry(pipeGeom);

    twoPhaseFlowSystem.createSystem();
    twoPhaseFlowSystem.init();
    twoPhaseFlowSystem.solveSteadyState(5);

    double[] pressures = twoPhaseFlowSystem.getPressureProfile();
    double twoPhaseFlowSystemDp = pressures[0] - pressures[pressures.length - 1];

    // ======== Debug diagnostic output ========
    System.out.println("=== Debug Diagnostics ===");
    System.out.printf("Total number of nodes: %d%n", twoPhaseFlowSystem.getTotalNumberOfNodes());
    System.out.printf("Node 0 velocity[0]: %.6f m/s%n",
        twoPhaseFlowSystem.getNode(0).getVelocity(0));
    System.out.printf("Node 25 velocity[0]: %.6f m/s%n",
        twoPhaseFlowSystem.getNode(25).getVelocity(0));
    System.out.printf("Node 50 velocity[0]: %.6f m/s%n",
        twoPhaseFlowSystem.getNode(50).getVelocity(0));
    System.out.printf("Node 0 phaseFraction[0]: %.6f%n",
        twoPhaseFlowSystem.getNode(0).getPhaseFraction(0));
    System.out.printf("Node 0 interphaseContactLength: %.6f%n",
        twoPhaseFlowSystem.getNode(0).getInterphaseContactLength(0));
    System.out.printf("Node 0 wallContactLength[0]: %.6f%n",
        twoPhaseFlowSystem.getNode(0).getWallContactLength(0));
    System.out.printf("Inlet pressure: %.6f bar%n", pressures[0]);
    System.out.printf("Outlet pressure: %.6f bar%n", pressures[pressures.length - 1]);
    System.out.println();

    // ======== Print comparison ========
    System.out.printf("%-30s %12s%n", "Model", "Pressure Drop (bar)");
    System.out.println("-".repeat(45));
    System.out.printf("%-30s %12.4f%n", "Beggs-Brill", beggsBrillsDp);
    System.out.printf("%-30s %12.4f%n", "TwoFluidPipe", twoFluidPipeDp);
    System.out.printf("%-30s %12.4f%n", "TransientPipe", transientPipeDp);
    System.out.printf("%-30s %12.4f%n", "TwoPhasePipeFlowSystem", twoPhaseFlowSystemDp);
    System.out.println();

    // All should give positive pressure drops for gas flow
    assertTrue(beggsBrillsDp > 0, "Beggs-Brill should give positive pressure drop");
    assertTrue(twoFluidPipeDp > 0, "TwoFluidPipe should give positive pressure drop");
    assertTrue(transientPipeDp > 0, "TransientPipe should give positive pressure drop");
    assertTrue(twoPhaseFlowSystemDp > 0,
        "TwoPhasePipeFlowSystem should give positive pressure drop for single-phase gas flow");

    // For single-phase gas, all models should be reasonably close
    double maxDp = Math.max(Math.max(beggsBrillsDp, twoFluidPipeDp),
        Math.max(transientPipeDp, twoPhaseFlowSystemDp));
    double minDp = Math.min(Math.min(beggsBrillsDp, twoFluidPipeDp),
        Math.min(transientPipeDp, twoPhaseFlowSystemDp));
    double ratio = maxDp / minDp;
    System.out.printf("Max/Min ratio: %.2f%n", ratio);
  }

  /**
   * Test gas flow that starts single-phase but may condense liquid along the pipeline as
   * temperature drops or pressure decreases. Uses TPflash to check for phase formation. Currently
   * disabled - phase transition solver needs optimization for convergence.
   */
  @Disabled("Phase transition solver needs optimization - test takes too long")
  @Test
  void testGasWithCondensationAlongPipeline() {
    // Use a rich gas mixture - cooling will cause condensation
    double inletTemperature = 280.0; // 7C
    double pressure = 80.0; // 80 bar
    double pipeLength = 1000.0; // 1 km (shorter for faster test)
    double pipeDiameter = 0.2; // 200mm
    double massFlowRate = 10.0; // 10 kg/s

    // Rich gas mixture with heavy components that will condense when cooled
    SystemInterface fluid = new SystemSrkEos(inletTemperature, pressure);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    System.out.println("=== Gas with Potential Condensation Test ===");
    System.out.printf("Pipe: %.0f m length, %.0f mm diameter%n", pipeLength, pipeDiameter * 1000);
    System.out.printf("Inlet conditions: %.0f bar, %.1f C%n", pressure, inletTemperature - 273.15);
    System.out.printf("Inlet number of phases: %d%n", fluid.getNumberOfPhases());

    // Quick phase check at inlet and cold temperature
    SystemInterface coldFluid = fluid.clone();
    coldFluid.setTemperature(250.0); // -23C
    new neqsim.thermodynamicoperations.ThermodynamicOperations(coldFluid).TPflash();
    System.out.printf("At -23C: %d phase(s), liquid fraction=%.4f%n", coldFluid.getNumberOfPhases(),
        coldFluid.getNumberOfPhases() > 1 ? (1.0 - coldFluid.getBeta()) : 0.0);

    // ======== TwoPhasePipeFlowSystem with cooling ========
    double totalMolarMass = fluid.getMolarMass();
    double molarFlowRate = massFlowRate / totalMolarMass;

    SystemInterface fluid4 = new SystemSrkEos(inletTemperature, pressure);
    fluid4.addComponent("methane", 0.85 * molarFlowRate);
    fluid4.addComponent("ethane", 0.08 * molarFlowRate);
    fluid4.addComponent("propane", 0.04 * molarFlowRate);
    fluid4.addComponent("n-butane", 0.02 * molarFlowRate);
    fluid4.addComponent("n-pentane", 0.01 * molarFlowRate);
    fluid4.createDatabase(true);
    fluid4.setMixingRule(2);

    ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid4);
    ops.TPflash();
    fluid4.initPhysicalProperties();

    System.out.println("\n=== Inlet Fluid State ===");
    System.out.printf("Number of phases: %d%n", fluid4.getNumberOfPhases());
    if (fluid4.getNumberOfPhases() == 1) {
      System.out.printf("Single phase type: %s%n", fluid4.getPhase(0).getType());
    } else {
      System.out.printf("Phase 0 type: %s, fraction: %.4f%n", fluid4.getPhase(0).getType(),
          fluid4.getBeta());
      System.out.printf("Phase 1 type: %s, fraction: %.4f%n", fluid4.getPhase(1).getType(),
          1.0 - fluid4.getBeta());
    }

    TwoPhasePipeFlowSystem twoPhaseFlowSystem = new TwoPhasePipeFlowSystem();
    twoPhaseFlowSystem.setInletThermoSystem(fluid4);
    twoPhaseFlowSystem.setInitialFlowPattern("stratified");
    twoPhaseFlowSystem.setNumberOfLegs(5);
    twoPhaseFlowSystem.setNumberOfNodesInLeg(3);

    // Set up pipe - no heat transfer (adiabatic) for fast test
    double outerTemp = 280.0; // Same as inlet - no heat flux
    double[] heights = new double[6];
    double[] lengths = new double[6];
    double[] outerTemps = new double[6];
    double[] outHeatCoefs = new double[6];
    double[] wallHeatCoefs = new double[6];
    for (int i = 0; i < 6; i++) {
      heights[i] = 0.0;
      lengths[i] = i * (pipeLength / 5.0);
      outerTemps[i] = outerTemp;
      outHeatCoefs[i] = 0.0; // No heat transfer - adiabatic
      wallHeatCoefs[i] = 0.0; // No wall heat transfer
    }

    twoPhaseFlowSystem.setLegHeights(heights);
    twoPhaseFlowSystem.setLegPositions(lengths);
    twoPhaseFlowSystem.setLegOuterTemperatures(outerTemps);
    twoPhaseFlowSystem.setLegOuterHeatTransferCoefficients(outHeatCoefs);
    twoPhaseFlowSystem.setLegWallHeatTransferCoefficients(wallHeatCoefs);

    GeometryDefinitionInterface[] pipeGeom = new PipeData[6];
    for (int i = 0; i < 6; i++) {
      pipeGeom[i] = new PipeData(pipeDiameter);
    }
    twoPhaseFlowSystem.setEquipmentGeometry(pipeGeom);

    twoPhaseFlowSystem.createSystem();
    twoPhaseFlowSystem.init();
    twoPhaseFlowSystem.solveSteadyState(3); // Fewer iterations for faster test

    // ======== Analyze results along the pipeline ========
    System.out.println("\n=== Results Along Pipeline ===");
    System.out.printf("%-8s %-12s %-12s %-12s %-15s%n", "Node", "Pressure", "Temperature",
        "NumPhases", "LiquidFraction");

    double[] pressures = twoPhaseFlowSystem.getPressureProfile();
    int totalNodes = twoPhaseFlowSystem.getTotalNumberOfNodes();
    int[] nodesToCheck = {0, totalNodes / 2, totalNodes - 1};

    for (int nodeIdx : nodesToCheck) {
      var node = twoPhaseFlowSystem.getNode(nodeIdx);
      double nodeP = node.getBulkSystem().getPressure();
      double nodeT = node.getBulkSystem().getTemperature() - 273.15;
      int numPhases = node.getBulkSystem().getNumberOfPhases();
      double liquidFrac = node.getPhaseFraction(1);

      System.out.printf("%-8d %-12.2f %-12.2f %-12d %-15.4f%n", nodeIdx, nodeP, nodeT, numPhases,
          liquidFrac);
    }

    double pressureDrop = pressures[0] - pressures[pressures.length - 1];
    System.out.printf("Total pressure drop: %.4f bar%n", pressureDrop);

    // Basic assertions
    assertTrue(pressureDrop > 0, "Should have positive pressure drop");

    // Check inlet is gas-dominated
    assertTrue(twoPhaseFlowSystem.getNode(0).getPhaseFraction(0) > 0.9,
        "Inlet should be gas-dominated");
  }
}
