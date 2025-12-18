package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.FlowPattern;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the simplified TwoPhasePipeFlowSystem API.
 *
 * <p>
 * Tests the new static factory methods, solve() method, and PipeFlowResult container.
 * </p>
 */
class TwoPhasePipeFlowSystemSimplifiedAPITest {

  /**
   * Creates a test fluid with proper two-phase conditions.
   */
  private SystemInterface createTestFluid() {
    SystemInterface fluid = new SystemSrkEos(295.3, 5.0);
    fluid.addComponent("methane", 0.1, 0); // Gas phase
    fluid.addComponent("water", 0.05, 1); // Liquid phase
    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    return fluid;
  }

  // ==================== STATIC FACTORY METHOD TESTS ====================

  @Test
  void testHorizontalPipeFactory() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.1, 100, 10);

    assertNotNull(pipe, "Pipe should be created");
    assertTrue(pipe.getTotalNumberOfNodes() >= 10, "Should have at least 10 nodes");
  }

  @Test
  void testVerticalPipeFactory() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipeUp = TwoPhasePipeFlowSystem.verticalPipe(fluid, 0.1, 100, 10, true);
    TwoPhasePipeFlowSystem pipeDown =
        TwoPhasePipeFlowSystem.verticalPipe(fluid, 0.1, 100, 10, false);

    assertNotNull(pipeUp, "Upward pipe should be created");
    assertNotNull(pipeDown, "Downward pipe should be created");
  }

  @Test
  void testInclinedPipeFactory() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.inclinedPipe(fluid, 0.1, 100, 10, 15.0);

    assertNotNull(pipe, "Inclined pipe should be created");
  }

  @Test
  void testSubseaPipeFactory() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.subseaPipe(fluid, 0.15, 1000, 50, 4.0);

    assertNotNull(pipe, "Subsea pipe should be created");
    // Subsea should have convective boundary
    assertEquals(neqsim.fluidmechanics.flownode.WallHeatTransferModel.CONVECTIVE_BOUNDARY,
        pipe.getWallHeatTransferModel());
  }

  @Test
  void testBuriedPipeFactory() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.buriedPipe(fluid, 0.15, 1000, 50, 15.0);

    assertNotNull(pipe, "Buried pipe should be created");
  }

  @Test
  void testFactoryWithNullFluidThrows() {
    assertThrows(IllegalArgumentException.class, () -> {
      TwoPhasePipeFlowSystem.horizontalPipe(null, 0.1, 100, 10);
    });
  }

  @Test
  void testFactoryWithInvalidDiameterThrows() {
    SystemInterface fluid = createTestFluid();

    assertThrows(IllegalArgumentException.class, () -> {
      TwoPhasePipeFlowSystem.horizontalPipe(fluid, -0.1, 100, 10);
    });
  }

  @Test
  void testFactoryWithInvalidNodesThrows() {
    SystemInterface fluid = createTestFluid();

    assertThrows(IllegalArgumentException.class, () -> {
      TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.1, 100, 1);
    });
  }

  // ==================== SOLVE METHOD TESTS ====================

  @Test
  void testSolveReturnsResult() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);

    PipeFlowResult result = pipe.solve();

    assertNotNull(result, "Result should not be null");
    assertTrue(result.getInletPressure() > 0, "Inlet pressure should be positive");
    assertTrue(result.getInletTemperature() > 0, "Inlet temperature should be positive");
  }

  @Test
  void testSolveWithMassTransfer() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);

    PipeFlowResult result = pipe.solveWithMassTransfer();

    assertNotNull(result, "Result should not be null");
    assertTrue(result.getNumberOfNodes() >= 10, "Should have nodes");
  }

  @Test
  void testSolveWithHeatAndMassTransfer() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);

    PipeFlowResult result = pipe.solveWithHeatAndMassTransfer();

    assertNotNull(result, "Result should not be null");
  }

  // ==================== PIPEFLOWRESULT TESTS ====================

  @Test
  void testResultContainsAllProfiles() {
    SystemInterface fluid = createTestFluid();
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);
    PipeFlowResult result = pipe.solve();

    // Check all profiles exist and have correct length
    int numNodes = result.getNumberOfNodes();

    assertEquals(numNodes, result.getPositionProfile().length, "Position profile size");
    assertEquals(numNodes, result.getPressureProfile().length, "Pressure profile size");
    assertEquals(numNodes, result.getTemperatureProfile().length, "Temperature profile size");
    assertEquals(numNodes, result.getLiquidHoldupProfile().length, "Liquid holdup profile size");
    assertEquals(numNodes, result.getVoidFractionProfile().length, "Void fraction profile size");
    assertEquals(numNodes, result.getGasVelocityProfile().length, "Gas velocity profile size");
    assertEquals(numNodes, result.getLiquidVelocityProfile().length,
        "Liquid velocity profile size");
  }

  @Test
  void testResultSummaryValues() {
    SystemInterface fluid = createTestFluid();
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);
    PipeFlowResult result = pipe.solve();

    // Check summary values are consistent
    double[] pressures = result.getPressureProfile();
    assertEquals(pressures[0], result.getInletPressure(), 1e-10, "Inlet pressure consistency");
    assertEquals(pressures[pressures.length - 1], result.getOutletPressure(), 1e-10,
        "Outlet pressure consistency");

    double expectedDrop = result.getInletPressure() - result.getOutletPressure();
    assertEquals(expectedDrop, result.getTotalPressureDrop(), 1e-10, "Pressure drop consistency");
  }

  @Test
  void testResultToMap() {
    SystemInterface fluid = createTestFluid();
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);
    PipeFlowResult result = pipe.solve();

    Map<String, double[]> data = result.toMap();

    assertNotNull(data, "Map should not be null");
    assertTrue(data.containsKey("position_m"), "Should contain position");
    assertTrue(data.containsKey("pressure_bar"), "Should contain pressure");
    assertTrue(data.containsKey("temperature_K"), "Should contain temperature");
    assertTrue(data.containsKey("liquid_holdup"), "Should contain liquid holdup");
    assertTrue(data.containsKey("void_fraction"), "Should contain void fraction");
  }

  @Test
  void testResultGetSummary() {
    SystemInterface fluid = createTestFluid();
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);
    PipeFlowResult result = pipe.solve();

    Map<String, Object> summary = result.getSummary();

    assertNotNull(summary, "Summary should not be null");
    assertTrue(summary.containsKey("pipe_length_m"), "Should contain pipe length");
    assertTrue(summary.containsKey("total_pressure_drop_bar"), "Should contain pressure drop");
    assertTrue(summary.containsKey("inlet_temperature_K"), "Should contain inlet temperature");
  }

  @Test
  void testResultToString() {
    SystemInterface fluid = createTestFluid();
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);
    PipeFlowResult result = pipe.solve();

    String summary = result.toString();

    assertNotNull(summary, "String should not be null");
    assertTrue(summary.contains("Pressure"), "Should mention pressure");
    assertTrue(summary.contains("Temperature"), "Should mention temperature");
  }

  @Test
  void testResultMassTransferRate() {
    SystemInterface fluid = createTestFluid();
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);
    pipe.enableNonEquilibriumMassTransfer();
    PipeFlowResult result = pipe.solve();

    // Should be able to get mass transfer by index
    double rate0 = result.getTotalMassTransferRate(0);
    assertTrue(Double.isFinite(rate0), "Mass transfer rate should be finite");

    // Should be able to get mass transfer by name
    double rateMethane = result.getTotalMassTransferRate("methane");
    assertEquals(rate0, rateMethane, 1e-10, "Rate by index and name should match");
  }

  @Test
  void testResultComponentNames() {
    SystemInterface fluid = createTestFluid();
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);
    PipeFlowResult result = pipe.solve();

    String[] names = result.getComponentNames();
    assertEquals(2, names.length, "Should have 2 components");
    assertEquals("methane", names[0], "First component should be methane");
    assertEquals("water", names[1], "Second component should be water");
  }

  @Test
  void testResultFlowPatternProfile() {
    SystemInterface fluid = createTestFluid();
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);
    PipeFlowResult result = pipe.solve();

    FlowPattern[] patterns = result.getFlowPatternProfile();

    assertNotNull(patterns, "Flow pattern profile should not be null");
    assertEquals(result.getNumberOfNodes(), patterns.length, "Should have pattern at each node");

    // Default is stratified
    for (FlowPattern pattern : patterns) {
      assertEquals(FlowPattern.STRATIFIED, pattern, "Default flow pattern should be stratified");
    }
  }

  // ==================== BUILDER VALIDATION TESTS ====================

  @Test
  void testBuilderValidatesFluid() {
    assertThrows(IllegalStateException.class, () -> {
      TwoPhasePipeFlowSystem.builder().withDiameter(0.1, "m").withLength(100, "m").withNodes(10)
          .build();
    });
  }

  @Test
  void testBuilderValidatesEmptyFluid() {
    SystemInterface emptyFluid = new SystemSrkEos(300, 10);
    // No components added

    assertThrows(IllegalStateException.class, () -> {
      TwoPhasePipeFlowSystem.builder().withFluid(emptyFluid).withDiameter(0.1, "m")
          .withLength(100, "m").withNodes(10).build();
    });
  }

  // ==================== INTEGRATION TESTS ====================

  @Test
  void testSimplifiedAPIWorkflow() {
    // This test demonstrates the complete simplified workflow
    SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 0.8, 0);
    fluid.addComponent("n-heptane", 0.2, 1);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    // Create pipe using factory
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.1, 100, 20);

    // Solve and get results
    PipeFlowResult result = pipe.solve();

    // Access results - all in one place
    System.out.println("=== Simplified API Test ===");
    System.out.println(result);

    double dP = result.getTotalPressureDrop();
    double dT = result.getTemperatureChange();

    System.out.printf("Pressure drop: %.4f bar%n", dP);
    System.out.printf("Temperature change: %.2f K%n", dT);

    // Verify results are reasonable
    assertTrue(dP >= 0, "Pressure should drop along flow");
    assertTrue(result.getPipeLength() > 0, "Pipe length should be positive");
  }

  @Test
  void testBuilderAndFactoryGiveSameResults() {
    SystemInterface fluid = createTestFluid();

    // Using factory
    TwoPhasePipeFlowSystem pipeFactory = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3, 10);
    PipeFlowResult resultFactory = pipeFactory.solve();

    // Using builder with same parameters
    TwoPhasePipeFlowSystem pipeBuilder = TwoPhasePipeFlowSystem.builder().withFluid(fluid)
        .withDiameter(0.025, "m").withLength(3, "m").withNodes(10).horizontal()
        .withFlowPattern(FlowPattern.STRATIFIED).build();
    PipeFlowResult resultBuilder = pipeBuilder.solve();

    // Results should be very similar
    assertEquals(resultFactory.getInletPressure(), resultBuilder.getInletPressure(), 0.001,
        "Inlet pressures should match");
    assertEquals(resultFactory.getTotalPressureDrop(), resultBuilder.getTotalPressureDrop(), 0.001,
        "Pressure drops should match");
  }
}
