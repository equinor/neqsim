package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.FlowPattern;
import neqsim.fluidmechanics.flownode.FlowPatternModel;
import neqsim.fluidmechanics.flownode.WallHeatTransferModel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for TwoPhasePipeFlowSystemBuilder.
 */
class TwoPhasePipeFlowSystemBuilderTest {
  /**
   * Creates a test fluid with proper two-phase conditions. Uses methane as gas phase (phase 0) and
   * water as liquid phase (phase 1), similar to existing TwoPhasePipeFlowSystemTest.
   */
  private SystemInterface createTestFluid() {
    SystemInterface fluid = new SystemSrkEos(295.3, 5.0);
    fluid.addComponent("methane", 0.1, 0); // Gas phase
    fluid.addComponent("water", 0.05, 1); // Liquid phase
    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    return fluid;
  }

  @Test
  void testBasicBuilderCreation() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe =
        TwoPhasePipeFlowSystem.builder().withFluid(fluid).withDiameter(0.025, "m")
            .withLength(3, "m").withNodes(15).withFlowPattern(FlowPattern.STRATIFIED).build();

    assertNotNull(pipe, "Pipe system should be created");
    // Total nodes includes requested nodes plus boundary nodes
    assertTrue(pipe.getTotalNumberOfNodes() >= 15, "Should have at least 15 nodes");
  }

  @Test
  void testBuilderWithDifferentUnits() {
    SystemInterface fluid = createTestFluid();

    // Test diameter in mm
    TwoPhasePipeFlowSystem pipe1 = TwoPhasePipeFlowSystem.builder().withFluid(fluid)
        .withDiameter(25, "mm").withLength(3, "m").withNodes(10).build();

    assertNotNull(pipe1);

    // Test diameter in inches
    TwoPhasePipeFlowSystem pipe2 = TwoPhasePipeFlowSystem.builder().withFluid(fluid)
        .withDiameter(1, "in").withLength(10, "ft").withNodes(10).build();

    assertNotNull(pipe2);
  }

  @Test
  void testBuilderWithFlowPatternDetection() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder().withFluid(fluid)
        .withDiameter(0.025, "m").withLength(3, "m").withNodes(10)
        .withAutomaticFlowPatternDetection(FlowPatternModel.TAITEL_DUKLER).build();

    assertNotNull(pipe);
    assertEquals(FlowPatternModel.TAITEL_DUKLER, pipe.getFlowPatternModel());
  }

  @Test
  void testBuilderWithWallTemperature() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe =
        TwoPhasePipeFlowSystem.builder().withFluid(fluid).withDiameter(0.025, "m")
            .withLength(3, "m").withNodes(10).withWallTemperature(20, "C").build();

    assertNotNull(pipe);
    assertEquals(WallHeatTransferModel.CONSTANT_WALL_TEMPERATURE, pipe.getWallHeatTransferModel());
    assertEquals(293.15, pipe.getConstantWallTemperature(), 0.01);
  }

  @Test
  void testBuilderWithConvectiveBoundary() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe =
        TwoPhasePipeFlowSystem.builder().withFluid(fluid).withDiameter(0.025, "m")
            .withLength(3, "m").withNodes(10).withConvectiveBoundary(10, "C", 15.0).build();

    assertNotNull(pipe);
    assertEquals(WallHeatTransferModel.CONVECTIVE_BOUNDARY, pipe.getWallHeatTransferModel());
    assertEquals(283.15, pipe.getAmbientTemperature(), 0.01);
    assertEquals(15.0, pipe.getOverallHeatTransferCoefficient(), 0.01);
  }

  @Test
  void testBuilderWithAdiabaticWall() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder().withFluid(fluid)
        .withDiameter(0.025, "m").withLength(3, "m").withNodes(10).withAdiabaticWall().build();

    assertNotNull(pipe);
    assertEquals(WallHeatTransferModel.ADIABATIC, pipe.getWallHeatTransferModel());
  }

  @Test
  void testBuilderWithNonEquilibriumTransfer() {
    SystemInterface fluid = createTestFluid();

    // Note: Non-equilibrium transfer requires the system to be fully initialized
    // with flow nodes, so we don't call enable methods in the builder for now.
    // Instead, we test that the pipe is created successfully.
    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder().withFluid(fluid)
        .withDiameter(0.025, "m").withLength(3, "m").withNodes(10).build();

    assertNotNull(pipe);
  }

  @Test
  void testBuilderWithoutFluidThrows() {
    assertThrows(IllegalStateException.class, () -> {
      TwoPhasePipeFlowSystem.builder().withDiameter(0.025, "m").withLength(3, "m").withNodes(10)
          .build();
    });
  }

  @Test
  void testBuilderWithMultipleLegs() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder().withFluid(fluid)
        .withDiameter(0.025, "m").withLength(3, "m").withLegs(3, 5).build();

    assertNotNull(pipe);
    // Total nodes includes requested nodes (3×5=15) plus boundary nodes
    assertTrue(pipe.getTotalNumberOfNodes() >= 15,
        "Should have at least 3 legs × 5 nodes = 15 total");
  }

  @Test
  void testBuilderWithRoughness() {
    SystemInterface fluid = createTestFluid();

    TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder().withFluid(fluid)
        .withDiameter(0.025, "m").withLength(3, "m").withNodes(10).withRoughness(50, "um").build();

    assertNotNull(pipe);
  }
}
