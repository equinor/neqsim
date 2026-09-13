package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidComponentTransport;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Conservation of positive phase inventories around the absolute synchronization tolerance. */
class TwoFluidComponentTraceInventoryTest {
  @ParameterizedTest
  @ValueSource(doubles = { 1.0e-11, 5.0e-11, 1.0e-10, 2.0e-10 })
  void closedTracePhaseRetainsEveryComponentAcrossRepeatedSubsteps(double gasMassKg) {
    SystemInterface fluid = wetGas();
    TwoFluidSection[] cells = { cell(gasMassKg) };
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    double[] initial = transport.createReport(0.0, 0, 1.0e-8).getInitialInventoryKg();

    for (int step = 1; step <= 5; step++) {
      transport.advance(1.0, new double[2][3], new double[1][3], cells, fluid, fluid, 1.0e-8);
      TwoFluidComponentConservationReport report = transport.createReport(step, step, 1.0e-8);
      assertArrayEquals(initial, report.getFinalInventoryKg(), gasMassKg * 1.0e-14);
      assertTrue(report.isConverged(), report.getMessage());
    }
  }

  @Test
  void prescribedWaterCondensationRetainsTracePhaseAndClosesAcrossTheThreshold() {
    SystemInterface fluid = wetGas();
    TwoFluidSection[] cells = { cell(1.0) };
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    double[] initial = transport.createReport(0.0, 0, 1.0e-8).getInitialInventoryKg();
    double waterPerStepKg = 2.5e-11;
    // Isolate the inventory balance with a prescribed, equal-and-opposite water transfer over one second.
    // This tests bookkeeping only; it does not approximate equilibrium or latent heat.
    double[][] sources = { { -waterPerStepKg, 0.0, waterPerStepKg } };
    double[][][] componentSources = { { { 0.0, -waterPerStepKg }, { 0.0, 0.0 }, { 0.0, waterPerStepKg } } };

    for (int step = 1; step <= 8; step++) {
      double waterMassKg = step * waterPerStepKg;
      cells[0].setGasMassPerLength(1.0 - waterMassKg);
      cells[0].setWaterMassPerLength(waterMassKg);
      transport.advance(1.0, new double[2][3], sources, componentSources, new double[1], cells, fluid, fluid, 1.0e-8);
      TwoFluidComponentConservationReport report = transport.createReport(step, step, 1.0e-8);
      assertEquals(waterMassKg, report.getFinalPhaseInventoryKg(Phase.WATER, "water"), waterMassKg * 1.0e-14);
      assertEquals(waterMassKg, report.getInterphaseTransferKg(Phase.WATER, "water"), waterMassKg * 1.0e-14);
      for (int component = 0; component < initial.length; component++) {
        assertEquals(initial[component], report.getFinalInventoryKg()[component], initial[component] * 1.0e-13);
      }
      assertEquals(0.0, report.getMaximumInterphaseTransferResidualKg(), 0.0);
      assertTrue(report.isConverged(), report.getMessage());
    }
  }

  @Test
  void hydrodynamicRoundoffDoesNotCreateComponentsAndLargerMismatchRejects() {
    SystemInterface fluid = wetGas();
    TwoFluidSection[] cells = { cell(1.0) };
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    cells[0].setWaterMassPerLength(5.0e-11);
    transport.advance(1.0, new double[2][3], new double[1][3], cells, fluid, fluid, 1.0e-8);
    TwoFluidComponentConservationReport report = transport.createReport(1.0, 1, 1.0e-8);
    assertEquals(0.0, report.getFinalPhaseInventoryKg(Phase.WATER, "water"), 0.0);
    assertEquals(5.0e-11, report.getMaximumPhaseMassSynchronizationErrorKg(), 0.0);
    assertTrue(report.isConverged(), report.getMessage());
    String before = transport.createReport(0.0, 0, 1.0e-8).toJson();
    cells[0].setWaterMassPerLength(2.0e-10);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> transport.advance(1.0, new double[2][3], new double[1][3], cells, fluid, fluid, 1.0e-8));
    assertTrue(exception.getMessage().contains("mass differ"));
    assertEquals(before, transport.createReport(0.0, 0, 1.0e-8).toJson());
  }

  private static SystemInterface wetGas() {
    SystemInterface fluid = new SystemSrkEos(288.15, 70.0);
    fluid.addComponent("methane", 1.0 - 1.0e-6);
    fluid.addComponent("water", 1.0e-6);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static TwoFluidSection cell(double gasMassKg) {
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.20, 0.0);
    section.setPressure(70.0e5);
    section.setTemperature(288.15);
    section.setGasMassPerLength(gasMassKg);
    return section;
  }
}
