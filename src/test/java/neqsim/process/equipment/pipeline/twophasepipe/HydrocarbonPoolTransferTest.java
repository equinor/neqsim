package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Analytical and conservation qualification for finite-rate hydrocarbon-pool transfer. */
class HydrocarbonPoolTransferTest {
  @Test
  void componentConductanceUsesOneConservativeEquilibriumTarget() throws Exception {
    Fixture fixture = nonequilibriumFixture();
    double[] coefficients = {0.01, 0.002};
    HydrocarbonPoolTransfer closure = new HydrocarbonPoolTransfer(new String[] {"methane", "n-heptane"}, coefficients,
        new double[] {5.0e5, 3.0e5}, 0.5, Double.POSITIVE_INFINITY);
    double[][][] target = fixture.transport
        .createHydrocarbonEquilibriumInventoryChanges(new SystemInterface[] {fixture.equilibrium}, fixture.cells);
    HydrocarbonPoolTransfer.Result result = closure.calculate(fixture.transport, fixture.cells,
        new SystemInterface[] {fixture.equilibrium}, 0.01);
    double[][][] source = result.getSourcesKgPerMetreSecond();
    double[] constants = result.getRateConstantsPerSecond()[0];
    for (int component = 0; component < 2; component++) {
      double expectedConstant = 4.0 * coefficients[component] * 0.5 / (Math.PI * fixture.cells[0].getDiameter());
      assertEquals(expectedConstant, constants[component], 1e-15);
      assertEquals(expectedConstant * target[0][1][component], source[0][1][component], 1e-14);
      assertEquals(-source[0][1][component], source[0][0][component], 0.0);
      assertEquals(0.0, source[0][2][component], 0.0);
    }
    assertFalse(result.getDonorLimited()[0]);
    assertTrue(Math.abs(constants[0] - constants[1]) > 1e-6, "Species retain independently supplied kinetics");
  }

  @Test
  void hydrostaticPoolUsesVolumePreservingCellArea() throws Exception {
    Fixture fixture = nonequilibriumFixture();
    fixture.cells[0].setGasHoldup(0.5);
    fixture.cells[0].setLiquidHoldup(0.5);
    HydrocarbonPoolTransfer closure = HydrocarbonPoolTransfer.forHydrostaticPool(new String[] {"methane", "n-heptane"},
        new double[] {0.01, 0.002}, new double[] {5.0e5, 3.0e5}, Double.POSITIVE_INFINITY);

    HydrocarbonPoolTransfer.Result result = closure.calculate(fixture.transport, fixture.cells,
        new SystemInterface[] {fixture.equilibrium}, 0.01);
    double expectedArea = fixture.cells[0].getHydrostaticCellGeometry().getInterfaceArea();
    double cellVolume = fixture.cells[0].getArea() * fixture.cells[0].getLength();

    assertEquals(expectedArea, result.getInterfaceAreasSquareMetres()[0], 0.0);
    assertEquals(0.01 * expectedArea / cellVolume, result.getRateConstantsPerSecond()[0][0], 1.0e-15);
    double[] escaped = result.getInterfaceAreasSquareMetres();
    escaped[0] = -1.0;
    assertEquals(expectedArea, result.getInterfaceAreasSquareMetres()[0], 0.0,
        "Published geometry diagnostics must be defensive");
  }

  @Test
  void hydrostaticPoolUsesEquilibriumAreaForUnseededAppearance() throws Exception {
    Fixture fixture = nonequilibriumFixture();
    Field field = TwoFluidComponentTransport.class.getDeclaredField("componentInventoryKg");
    field.setAccessible(true);
    double[][][] inventory = (double[][][]) field.get(fixture.transport);
    for (int component = 0; component < inventory[0][0].length; component++) {
      inventory[0][0][component] += inventory[0][1][component];
      inventory[0][1][component] = 0.0;
    }
    fixture.cells[0].setGasHoldup(1.0);
    fixture.cells[0].setLiquidHoldup(0.0);
    fixture.cells[0].setGasMassPerLength(2.0);
    fixture.cells[0].setOilMassPerLength(0.0);
    HydrocarbonPoolTransfer.Result result = HydrocarbonPoolTransfer
        .forHydrostaticPool(new String[] {"methane", "n-heptane"}, new double[] {0.01, 0.002},
            new double[] {5.0e5, 3.0e5}, Double.POSITIVE_INFINITY)
        .calculate(fixture.transport, fixture.cells, new SystemInterface[] {fixture.equilibrium}, 0.01);

    assertTrue(result.getEquilibriumAppearanceArea()[0]);
    assertTrue(result.getInterfaceAreasSquareMetres()[0] > 0.0);
    assertTrue(result.getSourcesKgPerMetreSecond()[0][1][1] > 0.0);
  }

  @Test
  void unresolvedInclinedTraceUsesReportedStratifiedAreaWithoutInventoryClipping() throws Exception {
    Fixture fixture = nonequilibriumFixture();
    fixture.cells[0].setInclination(0.1);
    fixture.cells[0].setGasHoldup(1.0 - 1.0e-30);
    fixture.cells[0].setLiquidHoldup(1.0e-30);
    HydrocarbonPoolTransfer.Result result = HydrocarbonPoolTransfer
        .forHydrostaticPool(new String[] {"methane", "n-heptane"}, new double[] {0.01, 0.002},
            new double[] {5.0e5, 3.0e5}, Double.POSITIVE_INFINITY)
        .calculate(fixture.transport, fixture.cells, new SystemInterface[] {fixture.equilibrium}, 0.01);

    assertTrue(result.getUnresolvedTraceAreaFallback()[0]);
    assertTrue(result.getInterfaceAreasSquareMetres()[0] >= 0.0);
  }

  @Test
  void zeroAreaAndZeroDriveProduceNoTransfer() {
    SystemInterface fluid = fluid(277.15);
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    SystemInterface equilibrium = transport.createThermodynamicState(0, fluid, 50e5, 277.15);
    for (HydrocarbonPoolTransfer closure : new HydrocarbonPoolTransfer[] {
        new HydrocarbonPoolTransfer(new String[] {"methane", "n-heptane"}, new double[] {0.1, 0.1},
            new double[] {5.0e5, 3.0e5}, 0.5, Double.POSITIVE_INFINITY),
        new HydrocarbonPoolTransfer(new String[] {"methane", "n-heptane"}, new double[] {0.1, 0.1},
            new double[] {5.0e5, 3.0e5}, 0.0, Double.POSITIVE_INFINITY)}) {
      double[][][] sources = closure.calculate(transport, cells, new SystemInterface[] {equilibrium}, 0.1)
          .getSourcesKgPerMetreSecond();
      for (double[] phase : sources[0]) {
        for (double source : phase) {
          assertEquals(0.0, source, 1e-12);
        }
      }
    }
  }

  @Test
  void donorAndHeatLimitsAreExplicitAndPassive() throws Exception {
    Fixture fixture = nonequilibriumFixture();
    HydrocarbonPoolTransfer donorLimited = new HydrocarbonPoolTransfer(new String[] {"methane", "n-heptane"},
        new double[] {1.0e8, 1.0e8}, new double[] {5.0e5, 3.0e5}, 1.0, Double.POSITIVE_INFINITY);
    HydrocarbonPoolTransfer.Result donorResult = donorLimited.calculate(fixture.transport, fixture.cells,
        new SystemInterface[] {fixture.equilibrium}, 2.0);
    assertTrue(donorResult.getDonorLimited()[0]);
    double[][][] inventory = fixture.transport.getInventoryKg();
    double[][][] donorSource = donorResult.getSourcesKgPerMetreSecond();
    for (int component = 0; component < 2; component++) {
      int donor = donorSource[0][1][component] > 0.0 ? 0 : 1;
      assertTrue(Math.abs(donorSource[0][1][component]) <= 0.5 * inventory[0][donor][component] / 2.0 + 1e-14);
    }

    SystemInterface hotFluid = fluid(277.15);
    TwoFluidSection[] hotCells = cells();
    TwoFluidComponentTransport hotTransport = new TwoFluidComponentTransport(hotFluid, hotCells);
    hotCells[0].setPressure(1.0e5);
    hotCells[0].setTemperature(330.0);
    SystemInterface gasTarget = hotTransport.createThermodynamicState(0, hotFluid, 1.0e5, 330.0);
    assertFalse(gasTarget.hasPhaseType("oil"));
    HydrocarbonPoolTransfer heatLimited = new HydrocarbonPoolTransfer(new String[] {"methane", "n-heptane"},
        new double[] {0.1, 0.1}, new double[] {5.0e5, 3.0e5}, 1.0, 10.0);
    HydrocarbonPoolTransfer.Result heatResult = heatLimited.calculate(hotTransport, hotCells,
        new SystemInterface[] {gasTarget}, 0.01);
    assertTrue(heatResult.getUnconstrainedLatentHeatWPerMetre()[0] < -10.0,
        "Expected evaporation heat demand, got " + heatResult.getUnconstrainedLatentHeatWPerMetre()[0] + " from "
            + java.util.Arrays.deepToString(heatResult.getSourcesKgPerMetreSecond()));
    assertTrue(heatResult.getHeatScale()[0] > 0.0 && heatResult.getHeatScale()[0] < 1.0);
    double[][][] limitedSource = heatResult.getSourcesKgPerMetreSecond();
    double limitedHeat = limitedSource[0][1][0] * 5.0e5 + limitedSource[0][1][1] * 3.0e5;
    assertEquals(-10.0, limitedHeat, 1e-8);

    HydrocarbonPoolTransfer adiabatic = new HydrocarbonPoolTransfer(new String[] {"methane", "n-heptane"},
        new double[] {0.1, 0.1}, new double[] {5.0e5, 3.0e5}, 1.0, 0.0);
    double[][][] blocked = adiabatic.calculate(hotTransport, hotCells, new SystemInterface[] {gasTarget}, 0.01)
        .getSourcesKgPerMetreSecond();
    for (double[] phase : blocked[0]) {
      for (double source : phase) {
        assertEquals(0.0, source, 0.0);
      }
    }
  }

  @Test
  void oilReappearsFromConservedGasWithoutInventorySeed() throws Exception {
    SystemInterface fluid = fluid(277.15);
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    Field field = TwoFluidComponentTransport.class.getDeclaredField("componentInventoryKg");
    field.setAccessible(true);
    double[][][] inventory = (double[][][]) field.get(transport);
    for (int component = 0; component < inventory[0][0].length; component++) {
      inventory[0][0][component] += inventory[0][1][component];
      inventory[0][1][component] = 0.0;
    }
    cells[0].setGasMassPerLength(2.0);
    cells[0].setOilMassPerLength(0.0);
    SystemInterface equilibrium = transport.createThermodynamicState(0, fluid, 50e5, 277.15);
    assertTrue(equilibrium.hasPhaseType("oil"));
    HydrocarbonPoolTransfer closure = HydrocarbonPoolTransfer.forHydrostaticPool(new String[] {"methane", "n-heptane"},
        new double[] {0.01, 0.01}, new double[] {5.0e5, 3.0e5}, Double.POSITIVE_INFINITY);
    HydrocarbonPoolTransfer.Result result = closure.calculate(transport, cells, new SystemInterface[] {equilibrium},
        0.1);
    double[][][] source = result.getSourcesKgPerMetreSecond();
    assertTrue(source[0][1][1] > 0.0, "The flash target can form oil without a numerical liquid seed");
    assertEquals(-source[0][1][1], source[0][0][1], 0.0);
    assertTrue(result.getEquilibriumAppearanceArea()[0]);
  }

  @Test
  void frozenTargetEulerRefinementConvergesToAnalyticalExponential() throws Exception {
    double previousError = Double.POSITIVE_INFINITY;
    double lambda = 0.5;
    double coefficient = lambda * Math.PI * 0.2 / 2.0;
    for (double dt : new double[] {0.2, 0.1, 0.05}) {
      Fixture fixture = nonequilibriumFixture();
      HydrocarbonPoolTransfer closure = new HydrocarbonPoolTransfer(new String[] {"methane", "n-heptane"},
          new double[] {coefficient, coefficient}, new double[] {5.0e5, 3.0e5}, 0.5, Double.POSITIVE_INFINITY);
      double initial = fixture.transport.getInventoryKg()[0][1][1];
      double targetChange = fixture.transport.createHydrocarbonEquilibriumInventoryChanges(
          new SystemInterface[] {fixture.equilibrium}, fixture.cells)[0][1][1];
      double target = initial + targetChange;
      int steps = (int) Math.round(1.0 / dt);
      for (int step = 0; step < steps; step++) {
        double[][][] source = closure
            .calculate(fixture.transport, fixture.cells, new SystemInterface[] {fixture.equilibrium}, dt)
            .getSourcesKgPerMetreSecond();
        double[][] phaseSource = phaseSources(source);
        fixture.cells[0].setGasMassPerLength(fixture.cells[0].getGasMassPerLength() + dt * phaseSource[0][0]);
        fixture.cells[0].setOilMassPerLength(fixture.cells[0].getOilMassPerLength() + dt * phaseSource[0][1]);
        fixture.transport.advance(dt, new double[2][3], phaseSource, source, new double[1], fixture.cells,
            fixture.fluid, fixture.fluid, 1e-8);
      }
      double actual = fixture.transport.getInventoryKg()[0][1][1];
      double analytical = target + (initial - target) * Math.exp(-lambda);
      double error = Math.abs(actual - analytical);
      assertTrue(error < previousError * 0.58);
      previousError = error;
    }
  }

  @Test
  void invalidConfigurationFailsBeforeEvaluation() {
    assertThrows(IllegalArgumentException.class,
        () -> new HydrocarbonPoolTransfer(new String[] {"methane"}, new double[] {-1.0}, new double[] {1.0}, 0.5, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> new HydrocarbonPoolTransfer(new String[] {"methane"}, new double[] {1.0}, new double[] {1.0}, 1.1, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> new HydrocarbonPoolTransfer(new String[] {"methane"}, new double[] {1.0}, new double[] {1.0}, 0.5, -1.0));
  }

  /** Record the no-extra-flash source-kernel cost without a platform-dependent timing threshold. */
  @Test
  @Tag("benchmark")
  void sourceKernelBenchmark() throws Exception {
    Fixture fixture = nonequilibriumFixture();
    HydrocarbonPoolTransfer closure = new HydrocarbonPoolTransfer(new String[] {"methane", "n-heptane"},
        new double[] {0.01, 0.002}, new double[] {5.0e5, 3.0e5}, 0.5, Double.POSITIVE_INFINITY);
    SystemInterface[] states = {fixture.equilibrium};
    double checksum = 0.0;
    for (int warm = 0; warm < 1000; warm++) {
      checksum += closure.calculate(fixture.transport, fixture.cells, states, 0.01)
          .getSourcesKgPerMetreSecond()[0][0][0];
    }
    StringBuilder timings = new StringBuilder("batch,ns_per_cell\n");
    for (int batch = 0; batch < 3; batch++) {
      long start = System.nanoTime();
      for (int call = 0; call < 10000; call++) {
        checksum += closure.calculate(fixture.transport, fixture.cells, states, 0.01)
            .getSourcesKgPerMetreSecond()[0][0][0];
      }
      double nanosPerCell = (System.nanoTime() - start) / 10000.0;
      timings.append(batch).append(',').append(nanosPerCell).append('\n');
      LogManager.getLogger(HydrocarbonPoolTransferTest.class)
          .info("Hydrocarbon-pool source batch {}: {} ns/cell; checksum {}", batch, nanosPerCell, checksum);
    }
    java.nio.file.Files.write(java.nio.file.Paths.get("target/hydrocarbon-pool-source-benchmark.csv"),
        timings.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(Double.isFinite(checksum));
  }

  private static double[][] phaseSources(double[][][] source) {
    double[][] result = new double[source.length][3];
    for (int cell = 0; cell < source.length; cell++) {
      for (int phase = 0; phase < 3; phase++) {
        for (double component : source[cell][phase]) {
          result[cell][phase] += component;
        }
      }
    }
    return result;
  }

  private static Fixture nonequilibriumFixture() throws Exception {
    SystemInterface fluid = fluid(277.15);
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
    return new Fixture(fluid, cells, transport, equilibrium);
  }

  private static SystemInterface fluid(double temperature) {
    SystemInterface fluid = new SystemSrkEos(temperature, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertTrue(fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil"));
    return fluid;
  }

  private static TwoFluidSection[] cells() {
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.2, 0.0);
    section.setPressure(50e5);
    section.setTemperature(277.15);
    section.setGasMassPerLength(1.0);
    section.setOilMassPerLength(1.0);
    return new TwoFluidSection[] {section};
  }

  private static final class Fixture {
    private final SystemInterface fluid;
    private final TwoFluidSection[] cells;
    private final TwoFluidComponentTransport transport;
    private final SystemInterface equilibrium;

    private Fixture(SystemInterface fluid, TwoFluidSection[] cells, TwoFluidComponentTransport transport,
        SystemInterface equilibrium) {
      this.fluid = fluid;
      this.cells = cells;
      this.transport = transport;
      this.equilibrium = equilibrium;
    }
  }
}
