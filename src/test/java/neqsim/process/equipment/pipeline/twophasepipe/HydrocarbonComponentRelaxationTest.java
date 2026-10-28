package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.apache.logging.log4j.LogManager;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent component-target and donor-momentum limits for the approximate hydrocarbon relaxation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HydrocarbonComponentRelaxationTest {
  /**
   * A depleted heavy species must return from oil using its own equilibrium target.
   *
   * @throws Exception if the controlled conserved-inventory fixture cannot be constructed
   */
  @Test
  void depletedHeavyComponentUsesItsOwnTargetAndEulerEndpointIsNonnegative() throws Exception {
    SystemInterface fluid = fluid();
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    Field field = TwoFluidComponentTransport.class.getDeclaredField("componentInventoryKg");
    field.setAccessible(true);
    double[][][] inventories = (double[][][]) field.get(transport);
    double swap = inventories[0][0][1] - 1e-12;
    assertTrue(swap > 0.0 && inventories[0][1][0] > swap);
    inventories[0][0][1] -= swap;
    inventories[0][1][1] += swap;
    inventories[0][0][0] += swap;
    inventories[0][1][0] -= swap;
    SystemInterface equilibrium = transport.createThermodynamicState(0, fluid, 50e5, 277.15);
    double[][][] before = transport.getInventoryKg();
    double[][][] rates = transport.createHydrocarbonRelaxationSourceRates(new SystemInterface[] {equilibrium}, cells,
        2.0);
    assertTrue(rates[0][0][1] > 0.0, "Depleted gas n-heptane must receive material from oil");
    double totalEquilibriumMass = 0.0;
    for (int phase = 0; phase < equilibrium.getNumberOfPhases(); phase++) {
      totalEquilibriumMass += equilibrium.getPhase(phase).getMass();
    }
    double[][] phaseRates = new double[1][3];
    for (int component = 0; component < 2; component++) {
      double total = before[0][0][component] + before[0][1][component];
      double targetOil = 2.0 * equilibrium.getPhase("oil").getMass() / totalEquilibriumMass
          * equilibrium.getPhase("oil").getComponent(component).getx()
          * equilibrium.getPhase("oil").getComponent(component).getMolarMass()
          / equilibrium.getPhase("oil").getMolarMass();
      assertEquals((targetOil - before[0][1][component]) / 2.0, rates[0][1][component], 1e-14);
      assertEquals(0.0, rates[0][0][component] + rates[0][1][component], 0.0);
      assertTrue(before[0][0][component] + 2.0 * rates[0][0][component] >= 0.0);
      assertTrue(before[0][1][component] + 2.0 * rates[0][1][component] >= 0.0);
      assertEquals(total, before[0][0][component] + 2.0 * rates[0][0][component] + before[0][1][component]
          + 2.0 * rates[0][1][component], 1e-14);
      phaseRates[0][0] += rates[0][0][component];
      phaseRates[0][1] += rates[0][1][component];
    }
    cells[0].setGasMassPerLength(1.0 + 2.0 * phaseRates[0][0]);
    cells[0].setOilMassPerLength(1.0 + 2.0 * phaseRates[0][1]);
    transport.advance(2.0, new double[2][3], phaseRates, rates, new double[1], cells, fluid, fluid, 1e-8);
    assertTrue(transport.createReport(2.0, 1, 1e-8).isConverged());
  }

  /** Opposing component fluxes at zero net phase mass still carry donor momentum. */
  @Test
  void zeroNetMassCounterTransferUsesBothActualDonorVelocities() {
    TwoFluidSection section = cells()[0];
    section.setGasVelocity(8.0);
    section.setOilVelocity(-2.0);
    double[][] rates = {{-0.3, 0.3}, {0.3, -0.3}, {0.0, 0.0}};
    double[] momentum = TwoFluidConservationEquations.componentTransferMomentumSources(section, rates);
    assertArrayEquals(new double[] {-3.0, 3.0, 0.0}, momentum, 1e-14);
    double initialKinetic = 0.5 * (8.0 * 8.0 + 2.0 * 2.0);
    double finalKinetic = 0.5 * (5.0 * 5.0 + 1.0 * 1.0);
    assertTrue(finalKinetic < initialKinetic, "Donor mixing dissipates kinetic energy at fixed total energy");
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setComponentMassTransferSources(new double[][][] {rates});
    equations.setIncludeMassTransfer(true);
    equations.setIncludeEnergyEquation(true);
    double[] source = equations.calcSourceTerms(new TwoFluidSection[] {section})[0];
    assertArrayEquals(new double[] {0.0, 0.0, 0.0}, new double[] {source[0], source[1], source[2]}, 0.0);
    assertEquals(0.0, source[6], 0.0, "Internal phase exchange cannot add total energy");
    assertThrows(IllegalArgumentException.class,
        () -> equations.setFiniteRateComponentSources(new double[][][] {rates}));
    assertThrows(IllegalArgumentException.class,
        () -> equations.setComponentMassTransferSources(new double[][][] {{{0}, {1}, {0}}}));
    assertThrows(IllegalArgumentException.class,
        () -> equations.setComponentMassTransferSources(new double[][][] {{{Double.NaN}, {0}, {0}}}));
  }

  /** Equilibrium is a zero-driving-force control for the component targets. */
  @Test
  void equilibriumComponentInventoriesHaveZeroSource() {
    SystemInterface fluid = fluid();
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    SystemInterface equilibrium = transport.createThermodynamicState(0, fluid, 50e5, 277.15);
    double[][][] rates = transport.createHydrocarbonRelaxationSourceRates(new SystemInterface[] {equilibrium}, cells,
        2.0);
    for (double[] phase : rates[0])
      for (double rate : phase)
        assertEquals(0.0, rate, 1e-12);
  }

  /**
   * Frozen-target Euler refinement must approach the independently known exponential.
   *
   * @throws Exception if the controlled nonequilibrium inventory cannot be constructed
   */
  @Test
  void closedFrozenTargetConvergesToAnalyticalExponential() throws Exception {
    double previousError = Double.POSITIVE_INFINITY;
    for (double dt : new double[] {0.2, 0.1, 0.05}) {
      SystemInterface fluid = fluid();
      TwoFluidSection[] cells = cells();
      TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
      Field field = TwoFluidComponentTransport.class.getDeclaredField("componentInventoryKg");
      field.setAccessible(true);
      double[][][] inventory = (double[][][]) field.get(transport);
      double exchange = inventory[0][0][1] * 0.5;
      inventory[0][0][1] -= exchange;
      inventory[0][1][1] += exchange;
      inventory[0][0][0] += exchange;
      inventory[0][1][0] -= exchange;
      SystemInterface equilibrium = transport.createThermodynamicState(0, fluid, 50e5, 277.15);
      double initial = transport.getInventoryKg()[0][1][1];
      double initialRate = transport.createHydrocarbonRelaxationSourceRates(new SystemInterface[] {equilibrium}, cells,
          2.0)[0][1][1];
      double target = initial + 2.0 * initialRate;
      assertTrue(Math.abs(initialRate) > 1e-8);
      int steps = (int) Math.round(2.0 / dt);
      for (int step = 0; step < steps; step++) {
        double[][][] rates = transport.createHydrocarbonRelaxationSourceRates(new SystemInterface[] {equilibrium},
            cells, 2.0);
        double[][] phase = new double[1][3];
        for (int p = 0; p < 3; p++)
          for (double rate : rates[0][p])
            phase[0][p] += rate;
        cells[0].setGasMassPerLength(cells[0].getGasMassPerLength() + dt * phase[0][0]);
        cells[0].setOilMassPerLength(cells[0].getOilMassPerLength() + dt * phase[0][1]);
        transport.advance(dt, new double[2][3], phase, rates, new double[1], cells, fluid, fluid, 1e-8);
      }
      double actual = transport.getInventoryKg()[0][1][1];
      assertEquals(target + (initial - target) * Math.pow(1.0 - dt / 2.0, steps), actual, 1e-13);
      double error = Math.abs(actual - (target + (initial - target) * Math.exp(-1.0)));
      assertTrue(error < previousError * 0.55);
      previousError = error;
    }
  }

  /** Invalid time and grid inputs must fail without mutating inventories. */
  @Test
  void invalidInputsAreRejected() {
    SystemInterface fluid = fluid();
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    for (double tau : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class,
          () -> transport.createHydrocarbonRelaxationSourceRates(new SystemInterface[] {fluid}, cells, tau));
    }
    assertThrows(IllegalArgumentException.class,
        () -> transport.createHydrocarbonRelaxationSourceRates(new SystemInterface[0], cells, 1.0));
  }

  /** Aqueous inventory keeps the existing separate transfer contract, including trace inventory. */
  @Test
  void aqueousInventoryDeclinesDryHydrocarbonRelaxation() {
    SystemInterface wet = new SystemSrkEos(277.15, 50.0);
    wet.addComponent("methane", 0.95);
    wet.addComponent("n-heptane", 0.05);
    wet.addComponent("water", 0.01);
    wet.setMixingRule("classic");
    wet.setMultiPhaseCheck(true);
    new ThermodynamicOperations(wet).TPflash();
    wet.initProperties();
    TwoFluidSection[] cells = cells();
    cells[0].setWaterMassPerLength(1e-15);
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(wet, cells);
    assertNull(transport.createHydrocarbonRelaxationSourceRates(new SystemInterface[] {wet}, cells, 2.0));
  }

  /**
   * Record paired source-kernel cost with warmed identical frozen EOS inputs, without a flaky timing assertion.
   *
   * @throws java.io.IOException if the benchmark artifact cannot be written
   */
  @Test
  @Tag("benchmark")
  void sourceKernelBenchmark() throws java.io.IOException {
    SystemInterface fluid = fluid();
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    SystemInterface[] states = {transport.createThermodynamicState(0, fluid, 50e5, 277.15)};
    double[][] phase = {{-0.01, 0.01, 0.0}};
    double checksum = 0.0;
    for (int warm = 0; warm < 5000; warm++) {
      checksum += transport.createHydrocarbonRelaxationSourceRates(states, cells, 2.0)[0][0][0];
      checksum += transport.createComponentSourceRates(phase, states, 1e-8)[0][0][0];
    }
    StringBuilder timings = new StringBuilder("batch,legacy_ns_per_cell,target_ns_per_cell\n");
    for (int batch = 0; batch < 3; batch++) {
      long start = System.nanoTime();
      for (int call = 0; call < 50000; call++) {
        checksum += transport.createComponentSourceRates(phase, states, 1e-8)[0][0][0];
      }
      long legacyNanos = System.nanoTime() - start;
      start = System.nanoTime();
      for (int call = 0; call < 50000; call++) {
        checksum += transport.createHydrocarbonRelaxationSourceRates(states, cells, 2.0)[0][0][0];
      }
      long targetNanos = System.nanoTime() - start;
      timings.append(batch).append(',').append(legacyNanos / 50000.0).append(',').append(targetNanos / 50000.0)
          .append('\n');
      LogManager.getLogger(HydrocarbonComponentRelaxationTest.class).info(
          "Source batch {}: legacy {} ns/cell, component-target {} ns/cell; checksum {}", batch, legacyNanos / 50000.0,
          targetNanos / 50000.0, checksum);
    }
    java.nio.file.Files.write(java.nio.file.Paths.get("target/hydrocarbon-source-benchmark.csv"),
        timings.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(Double.isFinite(checksum));
  }

  /** @return flashed dry gas/oil SRK mixture */
  private static SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(277.15, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertTrue(fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil"));
    return fluid;
  }

  /** @return one unit-length cell with independently prescribed gas/oil inertia */
  private static TwoFluidSection[] cells() {
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.2, 0.0);
    section.setPressure(50e5);
    section.setTemperature(277.15);
    section.setGasMassPerLength(1.0);
    section.setOilMassPerLength(1.0);
    return new TwoFluidSection[] {section};
  }
}
