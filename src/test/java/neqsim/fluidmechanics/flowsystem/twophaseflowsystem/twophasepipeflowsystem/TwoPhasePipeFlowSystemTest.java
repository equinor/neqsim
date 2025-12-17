package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
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
}
