package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent circular-segment and conservative partition controls for a flowing gas/oil interface.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class StratifiedPoolTransferTest {
  /** Subdividing a uniform flowing state must preserve area and integrated component rates. */
  @Test
  void sourceIsInvariantUnderUniformAndNonuniformSubdivision() {
    double angle = Math.PI / 3.0;
    double holdup = (angle - Math.sin(angle) * Math.cos(angle)) / Math.PI;
    double expectedWidth = Math.sin(angle);
    for (double inclination : new double[] {-0.03, 0.0, 0.03}) {
      for (double heatSupply : new double[] {Double.POSITIVE_INFINITY, 10.0}) {
        HydrocarbonPoolTransfer closure = closure(heatSupply);
        double[] coarse = integratedSources(closure, new double[] {400.0}, inclination, holdup);
        for (double[] lengths : new double[][] {{200.0, 200.0}, {100.0, 100.0, 100.0, 100.0},
            {10.0, 30.0, 60.0, 120.0, 180.0}}) {
          double[] refined = integratedSources(closure, lengths, inclination, holdup);
          assertEquals(400.0 * expectedWidth, refined[0], 1.0e-10);
          for (int metric = 0; metric < coarse.length; metric++) {
            assertEquals(coarse[metric], refined[metric], 1.0e-10,
                "Physical area, heat supply and integrated species rates must survive subdivision");
          }
        }
      }
    }
  }

  /** Exact absence/fullness must remain blocked while a positive trace has a resolved interface. */
  @Test
  void circularAreaRetainsTraceAndBlocksFullLiquid() {
    for (double holdup : new double[] {1.0e-30, 1.0e-60, 1.0e-120, 1.0e-240, 1.0e-300, 0.5, 1.0}) {
      TwoFluidSection[] cells = cells(new double[] {400.0}, 0.03, holdup);
      SystemInterface fluid = fluid();
      TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
      SystemInterface[] equilibrium = {transport.createThermodynamicState(0, fluid, 50.0e5, 277.15)};
      HydrocarbonPoolTransfer.Result result = closure(Double.POSITIVE_INFINITY).calculate(transport, cells, equilibrium,
          0.01);
      double area = result.getInterfaceAreasSquareMetres()[0];
      assertFalse(result.getUnresolvedTraceAreaFallback()[0]);
      assertFalse(result.getEquilibriumAppearanceArea()[0]);
      if (holdup == 1.0) {
        assertEquals(0.0, area, 0.0);
        for (double[] phase : result.getSourcesKgPerMetreSecond()[0]) {
          for (double source : phase) {
            assertEquals(0.0, source, 0.0);
          }
        }
      } else if (holdup == 0.5) {
        assertEquals(400.0, area, 1.0e-10, "A half-full unit-diameter circle has a unit-width chord");
        assertEquals(4.0 * 0.01 / Math.PI, result.getRateConstantsPerSecond()[0][0], 1.0e-15);
      } else {
        double leadingDepth = Math.pow(3.0 * Math.PI * holdup / 16.0, 2.0 / 3.0);
        double traceArea = 400.0 * 2.0 * Math.sqrt(leadingDepth);
        assertTrue(area > 0.0);
        assertEquals(traceArea, area, traceArea * 1.0e-12,
            "The independent small-depth circular-segment limit must retain positive trace volume");
      }
    }
  }

  /**
   * First oil appearance must use the conservative equilibrium target without a seed or cell-length dependence.
   *
   * @throws Exception if the accepted inventory fixture cannot be prepared
   */
  @Test
  void unseededAppearancePreservesPartition() throws Exception {
    double previousArea = Double.NaN;
    double previousSource = Double.NaN;
    for (double[] lengths : new double[][] {{400.0}, {100.0, 100.0, 200.0}}) {
      TwoFluidSection[] cells = cells(lengths, 0.03, 0.0);
      SystemInterface fluid = fluid();
      TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
      Field field = TwoFluidComponentTransport.class.getDeclaredField("componentInventoryKg");
      field.setAccessible(true);
      double[][][] inventory = (double[][][]) field.get(transport);
      SystemInterface[] equilibrium = new SystemInterface[cells.length];
      for (int cell = 0; cell < cells.length; cell++) {
        for (int component = 0; component < inventory[cell][0].length; component++) {
          inventory[cell][0][component] += inventory[cell][1][component];
          inventory[cell][1][component] = 0.0;
        }
        cells[cell].setGasMassPerLength(2.0);
        cells[cell].setOilMassPerLength(0.0);
        equilibrium[cell] = transport.createThermodynamicState(cell, fluid, 50.0e5, 277.15);
      }
      double[][][] accepted = transport.getInventoryKg();
      HydrocarbonPoolTransfer.Result result = closure(Double.POSITIVE_INFINITY).calculate(transport, cells, equilibrium,
          0.01);
      double area = 0.0;
      double oilSource = 0.0;
      for (int cell = 0; cell < cells.length; cell++) {
        assertTrue(result.getEquilibriumAppearanceArea()[cell]);
        assertEquals(0.0, transport.getInventoryKg()[cell][1][1], 0.0);
        assertEquals(accepted[cell][0][1], transport.getInventoryKg()[cell][0][1], 0.0);
        area += result.getInterfaceAreasSquareMetres()[cell];
        oilSource += lengths[cell] * result.getSourcesKgPerMetreSecond()[cell][1][1];
      }
      assertTrue(area > 0.0 && oilSource > 0.0);
      if (Double.isFinite(previousArea)) {
        assertEquals(previousArea, area, 1.0e-10);
        assertEquals(previousSource, oilSource, 1.0e-10);
      }
      previousArea = area;
      previousSource = oilSource;
    }
  }

  /**
   * A serialized closure must retain its area selection and coefficients.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void serializationPreservesAreaSelection() throws Exception {
    HydrocarbonPoolTransfer original = closure(10.0);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    HydrocarbonPoolTransfer restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (HydrocarbonPoolTransfer) input.readObject();
    }
    double[] before = integratedSources(original, new double[] {400.0}, 0.03, 0.2);
    double[] after = integratedSources(restored, new double[] {400.0}, 0.03, 0.2);
    for (int metric = 0; metric < before.length; metric++) {
      assertEquals(before[metric], after[metric], 0.0);
    }
  }

  /**
   * Evaluate integrated area and paired component rates for a frozen uniform state.
   *
   * @param closure tested transfer closure
   * @param lengths cell lengths in metres
   * @param inclination signed pipe angle in radians
   * @param holdup accepted liquid volume fraction
   * @return total area in square metres and oil component sources in kg/s
   */
  private static double[] integratedSources(HydrocarbonPoolTransfer closure, double[] lengths, double inclination,
      double holdup) {
    TwoFluidSection[] cells = cells(lengths, inclination, holdup);
    SystemInterface fluid = fluid();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    SystemInterface[] equilibrium = new SystemInterface[cells.length];
    for (int cell = 0; cell < cells.length; cell++) {
      equilibrium[cell] = transport.createThermodynamicState(cell, fluid, 1.0e5, 330.0);
      assertFalse(equilibrium[cell].hasPhaseType("oil"), "Heating the frozen inventory must drive evaporation");
    }
    HydrocarbonPoolTransfer.Result result = closure.calculate(transport, cells, equilibrium, 0.01);
    double[][][] sources = result.getSourcesKgPerMetreSecond();
    double[] totals = new double[3];
    for (int cell = 0; cell < cells.length; cell++) {
      totals[0] += result.getInterfaceAreasSquareMetres()[cell];
      for (int component = 0; component < 2; component++) {
        assertEquals(-sources[cell][0][component], sources[cell][1][component], 0.0);
        assertEquals(0.0, sources[cell][2][component], 0.0);
        assertTrue(sources[cell][1][component] < 0.0, "A nonzero transfer must exercise partition invariance");
        totals[component + 1] += lengths[cell] * sources[cell][1][component];
      }
    }
    return totals;
  }

  /**
   * Build the declared flowing-interface closure.
   *
   * @param heatSupply finite evaporation heat supply in W/m or positive infinity
   * @return configured immutable closure
   */
  private static HydrocarbonPoolTransfer closure(double heatSupply) {
    return HydrocarbonPoolTransfer.forStratifiedFlow(new String[] {"methane", "n-heptane"}, new double[] {0.01, 0.002},
        new double[] {5.0e5, 3.0e5}, heatSupply);
  }

  /**
   * Prepare a dry two-phase SRK template with known component identities.
   *
   * @return initialized template at 50 bara and 277.15 K
   */
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

  /**
   * Create equal-state cells with independently prescribed volumes and phase inventories.
   *
   * @param lengths cell arclengths in metres
   * @param inclination pipe inclination in radians
   * @param holdup cell-average liquid fraction
   * @return contiguous unit-diameter cells
   */
  private static TwoFluidSection[] cells(double[] lengths, double inclination, double holdup) {
    TwoFluidSection[] cells = new TwoFluidSection[lengths.length];
    double position = 0.0;
    for (int cell = 0; cell < cells.length; cell++) {
      cells[cell] = new TwoFluidSection(position, lengths[cell], 1.0, inclination);
      cells[cell].setPressure(50.0e5);
      cells[cell].setTemperature(277.15);
      cells[cell].setGasMassPerLength(1.0);
      cells[cell].setOilMassPerLength(1.0);
      cells[cell].setGasHoldup(1.0 - holdup);
      cells[cell].setLiquidHoldup(holdup);
      position += lengths[cell];
    }
    return cells;
  }
}
