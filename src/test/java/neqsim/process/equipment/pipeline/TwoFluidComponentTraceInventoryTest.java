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
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Conservation of positive phase inventories around the absolute synchronization tolerance. */
class TwoFluidComponentTraceInventoryTest {
  /**
   * Retain every component in a closed positive trace phase across repeated substeps.
   *
   * @param gasMassKg initial gas mass in kg
   */
  @ParameterizedTest
  @ValueSource(doubles = {1.0e-15, 1.0e-13, 1.0e-12, 1.0e-11, 5.0e-11, 1.0e-10, 2.0e-10})
  void closedTracePhaseRetainsEveryComponentAcrossRepeatedSubsteps(double gasMassKg) {
    SystemInterface fluid = wetGas();
    TwoFluidSection[] cells = {cell(gasMassKg)};
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
    TwoFluidSection[] cells = {cell(1.0)};
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    double[] initial = transport.createReport(0.0, 0, 1.0e-8).getInitialInventoryKg();
    double waterPerStepKg = 2.5e-11;
    // Isolate the inventory balance with a prescribed, equal-and-opposite water transfer over one second.
    // This tests bookkeeping only; it does not approximate equilibrium or latent heat.
    double[][] sources = {{-waterPerStepKg, 0.0, waterPerStepKg}};
    double[][][] componentSources = {{{0.0, -waterPerStepKg}, {0.0, 0.0}, {0.0, waterPerStepKg}}};

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

  /**
   * Reject positive water mass without species even when it lies below the audit tolerance.
   *
   * @param waterMassKg unsupported hydrodynamic water mass in kg
   */
  /**
   * Carry the actual equilibrium oil composition for every nonzero condensation source.
   *
   * @param oilSourceKgPerMetreSecond oil source rate in kg/(m s)
   */
  @ParameterizedTest
  @ValueSource(doubles = {1.0e-15, 5.0e-11, 1.0e-10})
  void everyPositiveHydrodynamicPhaseRequiresNamedComponents(double waterMassKg) {
    SystemInterface fluid = wetGas();
    TwoFluidSection[] cells = {cell(1.0)};
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    String before = transport.createReport(0.0, 0, 1.0e-8).toJson();
    cells[0].setWaterMassPerLength(waterMassKg);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> transport.advance(1.0, new double[2][3], new double[1][3], cells, fluid, fluid, 1.0e-8));
    assertTrue(exception.getMessage().contains("Positive hydrodynamic water mass has no component inventory"));
    assertEquals(before, transport.createReport(0.0, 0, 1.0e-8).toJson());
  }

  @ParameterizedTest
  @ValueSource(doubles = {1.0e-15, 1.0e-13, 1.0e-12})
  void everyNonzeroCondensationSourceCarriesNamedComponents(double oilSourceKgPerMetreSecond) {
    SystemInterface fluid = richGas(150.0);
    TwoFluidSection[] cells = {cell(1.0)};
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    SystemInterface localEquilibrium = richGas(80.0);
    double[][] phaseSources = {{-oilSourceKgPerMetreSecond, oilSourceKgPerMetreSecond, 0.0}};
    double[][][] componentSources = transport.createComponentSourceRates(phaseSources,
        new SystemInterface[] {localEquilibrium}, 1.0e-8);

    assertEquals(oilSourceKgPerMetreSecond, sum(componentSources[0][1]), oilSourceKgPerMetreSecond * 1.0e-12);
    cells[0].setGasMassPerLength(1.0 - oilSourceKgPerMetreSecond);
    cells[0].setOilMassPerLength(oilSourceKgPerMetreSecond);
    transport.advance(1.0, new double[2][3], phaseSources, componentSources, new double[1], cells, fluid, fluid,
        1.0e-8);

    assertEquals(oilSourceKgPerMetreSecond, sum(transport.getInventoryKg()[0][1]), oilSourceKgPerMetreSecond * 1.0e-12);
    assertEquals(PhaseType.OIL, transport
        .createPhaseState(0, 1, fluid, cells[0].getPressure(), cells[0].getTemperature()).getPhase(0).getType());
    assertTrue(transport.createReport(1.0, 1, 1.0e-8).isConverged());
  }

  private static SystemInterface wetGas() {
    SystemInterface fluid = new SystemSrkEos(288.15, 70.0);
    fluid.addComponent("methane", 1.0 - 1.0e-6);
    fluid.addComponent("water", 1.0e-6);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Build and flash the public synthetic rich gas.
   *
   * @param pressureBara absolute pressure in bara
   * @return initialized equilibrium fluid
   */
  private static SystemInterface richGas(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(277.15, pressureBara);
    String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};
    double[] fractions = {0.916, 0.050, 0.020, 0.007, 0.003, 0.004};
    for (int component = 0; component < names.length; component++) {
      fluid.addComponent(names[component], fractions[component]);
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Sum values without applying a numerical presence floor.
   *
   * @param values values to sum
   * @return arithmetic sum
   */
  private static double sum(double[] values) {
    double result = 0.0;
    for (double value : values) {
      result += value;
    }
    return result;
  }

  private static TwoFluidSection cell(double gasMassKg) {
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.20, 0.0);
    section.setPressure(70.0e5);
    section.setTemperature(288.15);
    section.setGasMassPerLength(gasMassKg);
    return section;
  }
}
