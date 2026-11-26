package neqsim.process.fielddevelopment.hybrid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.fielddevelopment.integrated.IntegratedProductionModel;
import neqsim.process.fielddevelopment.integrated.IntegratedSolveResult;
import neqsim.process.fielddevelopment.integrated.WellDeliverabilityCurve;
import neqsim.process.fielddevelopment.reservoir.VfpTableBuilder;
import neqsim.process.fielddevelopment.reservoir.VlpProvider;

/**
 * Tests of the reservoir-state proxies and the VFP table lookup of the hybrid production model.
 *
 * @author ESOL
 * @version 1.0
 */
class ReservoirStateProxyTest {
  /**
   * Builds a four-point proxy with all state columns.
   *
   * @param name proxy name
   * @return the proxy
   */
  private static ReservoirStateProxy proxy(String name) {
    return new ReservoirStateProxy(name, new double[] {0.0, 1.0e6, 2.0e6, 3.0e6},
        new double[] {250.0, 220.0, 195.0, 175.0}).setWaterCut(new double[] {0.0, 0.1, 0.3, 0.5})
        .setGasOilRatio(new double[] {150.0, 150.0, 200.0, 300.0})
        .setProductivityIndex(new double[] {20.0, 18.0, 15.0, 12.0});
  }

  /** Interpolates linearly between table points and follows the cumulative oil produced. */
  @Test
  void interpolatesStateAgainstCumulativeOil() {
    ReservoirStateProxy state = proxy("W1");
    assertEquals(250.0, state.getReservoirPressure(), 1.0e-12);
    state.produce(1.5e6, 2.0e5, 2.5e8);
    assertEquals(207.5, state.getReservoirPressure(), 1.0e-9);
    assertEquals(0.2, state.getWaterCut(), 1.0e-12);
    assertEquals(175.0, state.getGasOilRatio(), 1.0e-9);
    assertEquals(16.5, state.getProductivityIndex(), 1.0e-9);
    assertEquals(1.7e6, state.getCumulative(CumulativeQuantity.LIQUID_PRODUCED), 1.0e-6);
    assertFalse(state.isOutsideValidity());
  }

  /** Holds the last value beyond the simulator run and reports that the proxy is outside its data. */
  @Test
  void holdsEndValueAndFlagsOutsideValidity() {
    ReservoirStateProxy state = proxy("W1");
    state.produce(3.5e6, 0.0, 0.0);
    assertEquals(175.0, state.getReservoirPressure(), 1.0e-12);
    assertEquals(0.5, state.getWaterCut(), 1.0e-12);
    assertTrue(state.isOutsideValidity());
  }

  /** Rejects tables that cannot be interpolated. */
  @Test
  void rejectsInconsistentTables() {
    assertThrows(IllegalArgumentException.class,
        () -> new ReservoirStateProxy("bad", new double[] {0.0, 1.0, 1.0}, new double[] {200.0, 190.0, 180.0}));
    assertThrows(IllegalArgumentException.class, () -> proxy("bad").setWaterCut(new double[] {0.0, 0.1, 0.3, 1.0}));
    assertThrows(IllegalArgumentException.class, () -> proxy("bad").setGasOilRatio(new double[] {150.0, 150.0}));
  }

  /**
   * Reads a proxy with composition and an injection column from a comma-separated file.
   *
   * @param folder temporary folder
   * @throws IOException if the file cannot be written
   */
  @Test
  void readsCsvWithCompositionAndInjectionColumn(@TempDir Path folder) throws IOException {
    Path file = folder.resolve("proxy.csv");
    String text = "# synthetic proxy\n"
        + "cumulative,pressure,waterCut,gor,pi,WATER_INJECTED,molesPerSm3,z:methane,z:nC10\n"
        + "0,250,0.0,150,20,0,9000,0.60,0.40\n" + "1000000,220,0.1,160,18,500000,9200,0.64,0.36\n"
        + "2000000,200,0.3,200,15,1200000,9800,0.70,0.30\n";
    Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    ReservoirStateProxy state = ReservoirStateProxy.fromCsv("W1", CumulativeQuantity.OIL_PRODUCED, file);
    state.produce(5.0e5, 0.0, 0.0);
    assertEquals(235.0, state.getReservoirPressure(), 1.0e-9);
    assertEquals(250000.0, state.quantityAt(CumulativeQuantity.WATER_INJECTED, 5.0e5), 1.0e-6);
    assertTrue(state.hasComposition());
    assertEquals("[methane, nC10]", Arrays.toString(state.getComponentNames()));
    assertEquals(0.62, state.getComposition()[0], 1.0e-9);
    assertEquals(9100.0, state.getHydrocarbonMolesPerSm3(), 1.0e-9);
  }

  /** A simulator-derived pressure table drives the existing integrated production model as a reservoir drive. */
  @Test
  void worksAsReservoirDriveOfIntegratedProductionModel() {
    ReservoirStateProxy drive = proxy("tank");
    IntegratedProductionModel model = new IntegratedProductionModel("field");
    model.setExportPressure(40.0);
    model.addWell("W1", drive, WellDeliverabilityCurve.fromVogel(5000.0, 250.0));
    IntegratedSolveResult initial = model.solve();
    assertTrue(initial.isConverged());
    model.runProfile(2.0, 0.25);
    assertTrue(drive.getCumulativeProduction() > 0.0);
    assertTrue(drive.getReservoirPressure() < 250.0);
    assertTrue(model.solve().getFieldRate() < initial.getFieldRate());
  }

  /** Blends two scenarios on cumulative water injected and flags a strategy outside the scenario envelope. */
  @Test
  void blendsScenariosOnCumulativeInjection() {
    ReservoirStateProxy low = new ReservoirStateProxy("low", new double[] {0.0, 1.0e6, 2.0e6},
        new double[] {250.0, 200.0, 160.0}).setWaterCut(new double[] {0.0, 0.05, 0.10})
        .setGasOilRatio(new double[] {150.0, 220.0, 400.0}).setProductivityIndex(new double[] {20.0, 16.0, 10.0})
        .setQuantityColumn(CumulativeQuantity.WATER_INJECTED, new double[] {0.0, 0.0, 0.0});
    ReservoirStateProxy high = new ReservoirStateProxy("high", new double[] {0.0, 1.0e6, 2.0e6},
        new double[] {250.0, 240.0, 230.0}).setWaterCut(new double[] {0.0, 0.20, 0.60})
        .setGasOilRatio(new double[] {150.0, 150.0, 150.0}).setProductivityIndex(new double[] {20.0, 19.0, 17.0})
        .setQuantityColumn(CumulativeQuantity.WATER_INJECTED, new double[] {0.0, 2.0e6, 4.0e6});
    ScenarioReservoirState blend = new ScenarioReservoirState("unit", CumulativeQuantity.WATER_INJECTED)
        .addScenario(low).addScenario(high);
    blend.produce(1.0e6, 0.0, 0.0);
    blend.inject(5.0e5, 0.0);
    assertEquals(0.25, blend.getBlendWeight(), 1.0e-12);
    assertEquals(210.0, blend.getReservoirPressure(), 1.0e-9);
    assertEquals(0.0875, blend.getWaterCut(), 1.0e-12);
    assertEquals(202.5, blend.getGasOilRatio(), 1.0e-9);
    assertEquals(16.75, blend.getProductivityIndex(), 1.0e-9);
    assertFalse(blend.isOutsideValidity());
    blend.inject(3.0e6, 0.0);
    assertEquals(240.0, blend.getReservoirPressure(), 1.0e-9);
    assertTrue(blend.isOutsideValidity());
  }

  /** Elapsed time separates a fast and a slow depletion scenario at the same cumulative oil. */
  @Test
  void blendsScenariosOnElapsedTime() {
    ReservoirStateProxy fast = new ReservoirStateProxy("fast", new double[] {0.0, 1.0e6, 2.0e6},
        new double[] {250.0, 200.0, 160.0}).setWaterCut(new double[] {0.0, 0.30, 0.50})
        .setGasOilRatio(new double[] {150.0, 150.0, 150.0}).setProductivityIndex(new double[] {20.0, 12.0, 8.0})
        .setQuantityColumn(CumulativeQuantity.ELAPSED_TIME, new double[] {0.0, 500.0, 1500.0});
    ReservoirStateProxy slow = new ReservoirStateProxy("slow", new double[] {0.0, 1.0e6, 2.0e6},
        new double[] {250.0, 210.0, 180.0}).setWaterCut(new double[] {0.0, 0.10, 0.20})
        .setGasOilRatio(new double[] {150.0, 150.0, 150.0}).setProductivityIndex(new double[] {20.0, 16.0, 14.0})
        .setQuantityColumn(CumulativeQuantity.ELAPSED_TIME, new double[] {0.0, 1500.0, 4500.0});
    ScenarioReservoirState blend = new ScenarioReservoirState("well", CumulativeQuantity.ELAPSED_TIME).addScenario(fast)
        .addScenario(slow);
    blend.produce(1.0e6, 0.0, 0.0);
    blend.elapse(500.0);
    assertEquals(0.30, blend.getWaterCut(), 1.0e-12);
    blend.elapse(500.0);
    assertEquals("[fast, slow]", Arrays.toString(blend.getActiveScenarios()));
    assertEquals(0.5, blend.getBlendWeight(), 1.0e-12);
    assertEquals(0.20, blend.getWaterCut(), 1.0e-12);
    assertEquals(205.0, blend.getReservoirPressure(), 1.0e-9);
    assertEquals(14.0, blend.getProductivityIndex(), 1.0e-9);
    assertEquals(1000.0, blend.getCumulative(CumulativeQuantity.ELAPSED_TIME), 1.0e-9);
    assertFalse(blend.isOutsideValidity());
  }

  /** Reads an OIL/WCT/GOR table like a simulator: exact for a multilinear function, linear extrapolation outside. */
  @Test
  void looksUpOilBasisTableWithLinearExtrapolation() {
    VlpProvider analytic = (rate, thp, wct, gor, alq) -> thp + 80.0 + 0.01 * rate + 30.0 * wct - 0.02 * gor
        + 2.0e-5 * rate * gor;
    VfpTableBuilder.Result built = VfpTableBuilder.oilWell(3).setDatumDepth(1800.0)
        .setRates(new double[] {100.0, 500.0, 1500.0}).setOutletPressures(new double[] {20.0, 60.0, 120.0})
        .setWaterRatios(new double[] {0.0, 0.4, 0.8}).setGasRatios(new double[] {100.0, 300.0}).build(analytic);
    VfpTableLookup lookup = VfpTableLookup.fromResult(built);
    assertEquals(3, lookup.getTableNumber());
    assertEquals("OIL", lookup.getRateType());
    assertEquals(analytic.bottomHolePressure(800.0, 45.0, 0.25, 180.0, 0.0),
        lookup.bottomHolePressure(800.0, 45.0, 0.25, 180.0, 0.0), 0.02);
    assertEquals(0, lookup.getOutsideAxesCount());
    assertEquals(analytic.bottomHolePressure(2000.0, 45.0, 0.25, 180.0, 0.0),
        lookup.bottomHolePressure(2000.0, 45.0, 0.25, 180.0, 0.0), 0.05);
    assertEquals(1, lookup.getOutsideAxesCount());
    lookup.setExtrapolate(false);
    assertEquals(analytic.bottomHolePressure(1500.0, 45.0, 0.25, 180.0, 0.0),
        lookup.bottomHolePressure(2000.0, 45.0, 0.25, 180.0, 0.0), 0.05);
  }

  /** Converts oil rate, water cut and gas-oil ratio to the basis of a liquid-rate table with gas-liquid ratio. */
  @Test
  void convertsOilBasisToLiquidBasisTable() {
    VlpProvider analytic = (rate, thp, wct, glr, alq) -> thp + 70.0 + 0.008 * rate + 25.0 * wct - 0.03 * glr;
    VfpTableBuilder.Result built = VfpTableBuilder.liquidWell(4).setRates(new double[] {100.0, 1000.0, 3000.0})
        .setOutletPressures(new double[] {20.0, 100.0}).setWaterRatios(new double[] {0.0, 0.5, 0.9})
        .setGasRatios(new double[] {20.0, 400.0}).build(analytic);
    VfpTableLookup lookup = VfpTableLookup.fromResult(built);
    assertEquals("LIQ", lookup.getRateType());
    double oil = 600.0;
    double waterCut = 0.4;
    double gasOilRatio = 200.0;
    double expected = analytic.bottomHolePressure(oil / (1.0 - waterCut), 50.0, waterCut,
        gasOilRatio * (1.0 - waterCut), 0.0);
    assertEquals(expected, lookup.onOilBasis().bottomHolePressure(oil, 50.0, waterCut, gasOilRatio, 0.0), 0.02);
  }
}
