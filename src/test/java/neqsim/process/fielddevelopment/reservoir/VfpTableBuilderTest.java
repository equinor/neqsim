package neqsim.process.fielddevelopment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.pipeline.TubingPerformance;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests of {@link VfpTableBuilder}, {@link VfpDeckReader}, {@link RecombinationFluidFactory}, {@link TubingVlpProvider}
 * and {@link ForwardVlpProvider} on a synthetic generic gas well.
 *
 * @author ESOL
 * @version 1.0
 */
class VfpTableBuilderTest {
  @TempDir
  Path tempDir;

  /** Smooth analytic lift model: hydrostatic plus friction that grows with rate and water ratio. */
  private static VlpProvider analytic() {
    return new VlpProvider() {
      @Override
      public double bottomHolePressure(double rate, double thp, double wr, double gr, double alq) {
        double q = rate / 1.0e6;
        return thp + 55.0 + 4.0 * q * q + 800.0 * wr + 20.0 * gr;
      }
    };
  }

  private static VfpTableBuilder gasBuilder() {
    return VfpTableBuilder.gasWell(7).setDatumDepth(2500.0).setTitle("Synthetic gas well")
        .setRates(VfpTableBuilder.linearAxis(0.2e6, 2.0e6, 4)).setOutletPressures(new double[] {30.0, 50.0, 70.0})
        .setWaterRatios(new double[] {1.0e-6, 1.0e-4}).setGasRatios(new double[] {1.0e-5, 1.0e-4});
  }

  private static SystemInterface template() {
    SystemInterface fluid = new SystemSrkEos(358.15, 200.0);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.015);
    fluid.addComponent("n-hexane", 0.01);
    fluid.addComponent("n-octane", 0.005);
    fluid.addComponent("water", 1.0e-6);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static RecombinationFluidFactory factory() {
    SystemInterface gas = new SystemSrkEos(288.15, 1.01325);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.06);
    gas.addComponent("propane", 0.03);
    gas.addComponent("n-butane", 0.01);
    gas.setMixingRule("classic");
    SystemInterface oil = new SystemSrkEos(288.15, 1.01325);
    oil.addComponent("n-hexane", 0.6);
    oil.addComponent("n-octane", 0.4);
    oil.setMixingRule("classic");
    return new RecombinationFluidFactory(template(), gas, oil).setRateBasis("GAS").setWaterRatioType("WGR")
        .setGasRatioType("OGR");
  }

  @Test
  void deckRoundTripReproducesEveryPressureInMetricUnits() {
    VfpTableBuilder.Result result = gasBuilder().build(analytic());
    VfpDeckReader.ParsedTable parsed = VfpDeckReader.parse(result.toDeckString());
    assertFalse(parsed.isInjection());
    assertEquals(7, parsed.getTableNumber());
    assertEquals(2500.0, parsed.getDatumDepth(), 1e-9);
    assertEquals("GAS", parsed.getDefinitions().get(0));
    assertEquals("METRIC", parsed.getDefinitions().get(5));
    double[][] axes = parsed.getAxes();
    assertEquals(4, axes[0].length);
    assertEquals(2, axes[2].length);
    for (int f = 0; f < 4; f++) {
      for (int t = 0; t < 3; t++) {
        for (int w = 0; w < 2; w++) {
          for (int g = 0; g < 2; g++) {
            assertEquals(result.getBhp(f, t, w, g, 0), parsed.getBhp()[f][t][w][g][0], 1e-9);
          }
        }
      }
    }
    assertEquals(0, result.getRepairCount());
  }

  @Test
  void fieldDeckConvertsPressuresAndGasRates() {
    VfpTableBuilder.Result result = gasBuilder().setUnitSystem("FIELD").build(analytic());
    VfpDeckReader.ParsedTable parsed = VfpDeckReader.parse(result.toDeckString());
    assertEquals("FIELD", parsed.getDefinitions().get(5));
    assertEquals(30.0 * 14.503773773, parsed.getAxes()[1][0], 1e-4);
    assertEquals(0.2e6 / 28.316846592, parsed.getAxes()[0][0], 1e-3);
    assertEquals(result.getBhp(1, 2, 1, 0, 0) * 14.503773773, parsed.getBhp()[1][2][1][0][0], 1e-3);
    assertEquals(2500.0 / 0.3048, parsed.getDatumDepth(), 1e-6);
  }

  @Test
  void interpolationReproducesALinearModelOffGrid() {
    VfpTableBuilder.Result result = VfpTableBuilder.gasWell(1).setDatumDepth(2000.0)
        .setRates(VfpTableBuilder.linearAxis(1.0e6, 3.0e6, 3)).setOutletPressures(new double[] {20.0, 80.0})
        .setWaterRatios(new double[] {0.0, 1.0e-3}).setGasRatios(new double[] {0.0, 1.0e-4}).build(new VlpProvider() {
          @Override
          public double bottomHolePressure(double rate, double thp, double wr, double gr, double alq) {
            return 1.0 * thp + 50.0 + 10.0 * rate / 1.0e6 + 500.0 * wr + 100.0 * gr;
          }
        });
    double expected = 1.0 * 33.0 + 50.0 + 10.0 * 1.7 + 500.0 * 4.0e-4 + 100.0 * 3.0e-5;
    assertEquals(expected, result.interpolate(1.7e6, 33.0, 4.0e-4, 3.0e-5, 0.0), 1e-9);
    assertTrue(result.isInsideAxes(1.7e6, 33.0, 4.0e-4, 3.0e-5, 0.0));
    assertFalse(result.isInsideAxes(3.5e6, 33.0, 4.0e-4, 3.0e-5, 0.0));
  }

  @Test
  void thpMonotonicityIsEnforcedAndReported() {
    VfpTableBuilder.Result result = VfpTableBuilder.gasWell(1).setRates(new double[] {1.0e6, 2.0e6})
        .setOutletPressures(new double[] {30.0, 50.0, 70.0}).build(new VlpProvider() {
          @Override
          public double bottomHolePressure(double rate, double thp, double wr, double gr, double alq) {
            return thp == 50.0 ? 70.0 : thp + 60.0;
          }
        });
    assertTrue(result.getBhp(0, 1, 0, 0, 0) >= result.getBhp(0, 0, 0, 0, 0) + 0.5 * 20.0 - 1e-9);
    assertTrue(result.getRepairCount() > 0);
    assertFalse(result.getWarnings().isEmpty());
    assertTrue(result.toDeckString().contains("-- NOTE:"));
  }

  @Test
  void bhpBelowOutletPressureIsRaisedToTheOutletPressure() {
    VfpTableBuilder.Result result = VfpTableBuilder.gasWell(1).setRates(new double[] {1.0e6})
        .setOutletPressures(new double[] {30.0, 50.0}).build(new VlpProvider() {
          @Override
          public double bottomHolePressure(double rate, double thp, double wr, double gr, double alq) {
            return thp - 5.0;
          }
        });
    assertEquals(30.0, result.getBhp(0, 0, 0, 0, 0), 1e-9);
    assertTrue(result.getBhp(0, 1, 0, 0, 0) >= 50.0);
  }

  @Test
  void rateCapReplacesPressureAboveCapByPenaltySlopeAndIsRecorded() {
    VfpTableBuilder.Result result = gasBuilder().setRateCap(1.0e6, 200.0).build(analytic());
    double atCap = analytic().bottomHolePressure(1.0e6, 30.0, 1.0e-6, 1.0e-5, 0.0);
    // rate axis 0.2, 0.8, 1.4, 2.0 MSm3/d: indices 2 and 3 are above the cap
    assertEquals(atCap + 200.0 * (1.4 - 1.0), result.getBhp(2, 0, 0, 0, 0), 1e-9);
    assertEquals(atCap + 200.0 * (2.0 - 1.0), result.getBhp(3, 0, 0, 0, 0), 1e-9);
    assertTrue(result.getCapPointCount() > 0);
    assertTrue(result.toDeckString().contains("RATE CAP"));
    VfpDeckReader.parse(result.toDeckString());
  }

  @Test
  void infeasiblePointFailsByDefaultAndExtrapolatesOnRequest() {
    VlpProvider failingAtHighRate = new VlpProvider() {
      @Override
      public double bottomHolePressure(double rate, double thp, double wr, double gr, double alq) {
        return rate > 1.5e6 ? Double.NaN : thp + 50.0 + 5.0 * rate / 1.0e6;
      }
    };
    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> gasBuilder().build(failingAtHighRate));
    assertTrue(ex.getMessage().contains("Infeasible VFP coordinate"));
    VfpTableBuilder.Result result = gasBuilder().setFailurePolicy(VfpTableBuilder.FailurePolicy.EXTRAPOLATE_RATE)
        .build(failingAtHighRate);
    assertTrue(result.getExtrapolatedCount() > 0);
    assertEquals(30.0 + 50.0 + 5.0 * 2.0, result.getBhp(3, 0, 0, 0, 0), 1e-6);
  }

  @Test
  void dryGasDiscontinuityIsDetectedAndCanBeBypassedWithFloor() {
    VlpProvider jump = new VlpProvider() {
      @Override
      public double bottomHolePressure(double rate, double thp, double wr, double gr, double alq) {
        return thp + 50.0 + (wr == 0.0 ? 15.0 : 0.0);
      }
    };
    VfpTableBuilder.Result flagged = VfpTableBuilder.gasWell(1).setRates(new double[] {1.0e6})
        .setOutletPressures(new double[] {30.0, 50.0}).setWaterRatios(new double[] {0.0, 1.0e-4}).build(jump);
    assertTrue(flagged.getWarnings().toString().contains("Dry-gas discontinuity"));
    VfpTableBuilder.Result floored = VfpTableBuilder.gasWell(1).setRates(new double[] {1.0e6})
        .setOutletPressures(new double[] {30.0, 50.0}).setWaterRatios(new double[] {0.0, 1.0e-4})
        .setZeroRatioFloor(1.0e-6).build(jump);
    assertEquals(0, floored.getWarnings().size());
    assertEquals(80.0, floored.getBhp(0, 0, 0, 0, 0), 1e-9);
  }

  @Test
  void injectionTableIsFormattedAsVfpInj() {
    VfpTableBuilder.Result result = VfpTableBuilder.injector(3, "WAT").setDatumDepth(2200.0)
        .setRates(new double[] {1000.0, 5000.0, 10000.0}).setOutletPressures(new double[] {50.0, 100.0})
        .build(new VlpProvider() {
          @Override
          public double bottomHolePressure(double rate, double thp, double wr, double gr, double alq) {
            return thp + 215.0 + 2.0e-9 * rate * rate;
          }
        });
    String deck = result.toDeckString();
    assertTrue(deck.contains("VFPINJ"));
    VfpDeckReader.ParsedTable parsed = VfpDeckReader.parse(deck);
    assertTrue(parsed.isInjection());
    assertEquals(3, parsed.getTableNumber());
    assertEquals(result.getBhp(2, 1, 0, 0, 0), parsed.getBhp()[2][1][0][0][0], 1e-9);
  }

  @Test
  void invalidAxesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> VfpTableBuilder.gasWell(1).setRates(new double[] {2.0e6, 1.0e6})
        .setOutletPressures(new double[] {30.0}).build(analytic()));
    assertThrows(IllegalArgumentException.class,
        () -> VfpTableBuilder.gasWell(1).setRates(new double[] {1.0e6}).build(analytic()));
  }

  @Test
  void deckCanBeWrittenToFile() throws IOException {
    Path file = tempDir.resolve("vfp7.inc");
    gasBuilder().addProvenance("synthetic analytic model").build(analytic()).writeDeck(file);
    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertTrue(text.startsWith("-- Synthetic gas well"));
    assertTrue(text.contains("synthetic analytic model"));
    assertTrue(text.contains("VFPPROD"));
  }

  @Test
  void forwardProviderInvertsAForwardModel() {
    ForwardVlpProvider provider = new ForwardVlpProvider(new ForwardVlpProvider.ForwardPressureModel() {
      @Override
      public double outletPressure(double rate, double inlet, double wr, double gr, double alq) {
        double dp = 40.0 + 6.0 * Math.pow(rate / 1.0e6, 2);
        return Math.max(1.0, inlet - dp);
      }
    });
    assertEquals(60.0 + 40.0 + 6.0 * 4.0, provider.bottomHolePressure(2.0e6, 60.0, 0.0, 0.0, 0.0), 0.05);
    assertTrue(Double.isNaN(new ForwardVlpProvider(new ForwardVlpProvider.ForwardPressureModel() {
      @Override
      public double outletPressure(double rate, double inlet, double wr, double gr, double alq) {
        return Double.NaN;
      }
    }).bottomHolePressure(1.0e6, 50.0, 0.0, 0.0, 0.0)));
  }

  @Test
  void fullRateMonotoneModeRemovesTheUShapeThatStallsOpmFlow() {
    VlpProvider uShaped = new VlpProvider() {
      @Override
      public double bottomHolePressure(double rate, double thp, double wr, double gr, double alq) {
        double q = rate / 1.0e6;
        return thp + 50.0 + 6.0 * (q - 1.0) * (q - 1.0);
      }
    };
    double[] rates = {0.2e6, 0.6e6, 1.0e6, 1.4e6};
    VfpTableBuilder.Result aboveMin = VfpTableBuilder.gasWell(1).setRates(rates).setOutletPressures(new double[] {40.0})
        .build(uShaped);
    assertTrue(aboveMin.getBhp(0, 0, 0, 0, 0) > aboveMin.getBhp(2, 0, 0, 0, 0), "loading side is kept");
    VfpTableBuilder.Result full = VfpTableBuilder.gasWell(1).setRates(rates).setOutletPressures(new double[] {40.0})
        .setRateMonotoneMode(VfpTableBuilder.RateMonotoneMode.FULL).build(uShaped);
    for (int f = 1; f < rates.length; f++) {
      assertTrue(full.getBhp(f, 0, 0, 0, 0) >= full.getBhp(f - 1, 0, 0, 0, 0));
    }
    assertEquals(aboveMin.getBhp(0, 0, 0, 0, 0), full.getBhp(3, 0, 0, 0, 0), 1e-9);
    assertTrue(full.getRepairCount() > 0);
  }

  @Test
  void documentedGasWellExampleRuns() throws IOException {
    SystemInterface surfaceGas = new SystemSrkEos(288.15, 1.01325);
    surfaceGas.addComponent("methane", 0.90);
    surfaceGas.addComponent("ethane", 0.06);
    surfaceGas.addComponent("propane", 0.03);
    surfaceGas.addComponent("n-butane", 0.01);
    surfaceGas.setMixingRule("classic");
    SystemInterface condensate = new SystemSrkEos(288.15, 1.01325);
    condensate.addComponent("n-hexane", 0.6);
    condensate.addComponent("n-octane", 0.4);
    condensate.setMixingRule("classic");
    RecombinationFluidFactory wellStream = new RecombinationFluidFactory(template(), surfaceGas, condensate)
        .setRateBasis("GAS").setWaterRatioType("WGR").setGasRatioType("OGR");
    TubingVlpProvider vlp = new TubingVlpProvider(wellStream, new double[] {0.0, 1500.0, 3200.0},
        new double[] {0.0, 1450.0, 2500.0}, new double[] {0.15, 0.15}).setTemperatures(95.0, 40.0)
        .setNumberOfSegments(6).setCorrelation(TubingPerformance.CorrelationType.GRAY);
    VfpTableBuilder.Result result = VfpTableBuilder.gasWell(1).setDatumDepth(2500.0)
        .setRates(VfpTableBuilder.rateAxis(0.2e6, 1.0e6, 5, 1.2))
        .setOutletPressures(VfpTableBuilder.pressureAxis(40.0, 80.0, 4, 0.1, 0.1))
        .setWaterRatios(new double[] {1.0e-6, 1.0e-4}).setGasRatios(new double[] {1.0e-5, 1.0e-4})
        .setRateMonotoneMode(VfpTableBuilder.RateMonotoneMode.FULL).setRateCap(1.1e6, 200.0)
        .addProvenance("Gray tubing survey, synthetic well").build(vlp);
    Path file = tempDir.resolve("vfp1.inc");
    result.writeDeck(file);
    assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("RATE CAP"));
    assertEquals(0, vlp.getFailures());
    assertTrue(result.getBhp(0, 0, 0, 0, 0) > result.getOutletPressures()[0]);
  }

  @Test
  void reservoirCouplingExporterUsesTheProviderInsteadOfThePlaceholder() {
    ReservoirCouplingExporter exporter = new ReservoirCouplingExporter();
    exporter.setRateRange(1000.0, 3000.0, 3);
    exporter.setPressureRange(20.0, 60.0, 3);
    exporter.setWctRange(0.0, 0.5, 2);
    exporter.setGorRange(100.0, 200.0, 2);
    exporter.setVlpProvider(analytic());
    ReservoirCouplingExporter.VfpTable table = exporter.generateVfpProd("W1", null, 1);
    assertEquals(analytic().bottomHolePressure(3000.0, 60.0, 0.5, 200.0, 0.0), table.getBhpValues()[2][2][1][1][0],
        1e-9);
    assertEquals(analytic().bottomHolePressure(1000.0, 20.0, 0.0, 100.0, 0.0), table.getBhpValues()[0][0][0][0][0],
        1e-9);
  }

  @Test
  void recombinationFactoryHonoursSurfaceVolumes() {
    RecombinationFluidFactory factory = factory();
    double rate = 1.0e6;
    SystemInterface fluid = factory.createWellStream(rate, 2.0e-4, 1.0e-4, 0.0);
    double gasMol = rate * RecombinationFluidFactory.getStandardGasMolarDensity();
    double waterMol = 2.0e-4 * rate * 1000.0 / 0.018015;
    double total = fluid.getFlowRate("mole/sec") * 86400.0;
    double liquidMol = total - gasMol - waterMol;
    // 100 Sm3/d of a C6/C8 condensate of about 97 g/mol and 690 kg/m3
    assertEquals(100.0 * 690.0 / 0.0974, liquidMol, 0.15 * 100.0 * 690.0 / 0.0974);
    double zWater = fluid.getPhase(0).getComponent("water").getz();
    assertEquals(waterMol / total, zWater, 1e-6);
    SystemInterface lifted = factory.createWellStream(rate, 2.0e-4, 1.0e-4, 1.0e5);
    assertTrue(lifted.getFlowRate("mole/sec") > fluid.getFlowRate("mole/sec"));
  }

  @Test
  void tubingTableMatchesDirectSolveAtOffGridPointsWithin03Bar() {
    RecombinationFluidFactory factory = factory();
    double[] md = {0.0, 1500.0, 3200.0};
    double[] tvd = {0.0, 1450.0, 2500.0};
    double[] id = {0.15, 0.15};
    TubingVlpProvider provider = new TubingVlpProvider(factory, md, tvd, id).setTemperatures(95.0, 40.0)
        .setNumberOfSegments(6).setCorrelation(TubingPerformance.CorrelationType.GRAY).setTolerance(0.01);
    VfpTableBuilder builder = VfpTableBuilder.gasWell(1).setDatumDepth(2500.0)
        .setRates(VfpTableBuilder.linearAxis(0.2e6, 1.4e6, 7))
        .setOutletPressures(VfpTableBuilder.linearAxis(40.0, 80.0, 5))
        .setWaterRatios(new double[] {1.0e-6, 5.0e-5, 1.0e-4}).setGasRatios(new double[] {1.0e-5, 5.0e-5, 1.0e-4});
    VfpTableBuilder.Result result = builder.build(provider);

    double[][] points = {{0.7e6, 55.0, 7.5e-5, 7.5e-5}, {1.1e6, 75.0, 2.5e-5, 2.5e-5}, {0.5e6, 45.0, 8.0e-5, 9.0e-5}};
    for (double[] p : points) {
      double direct = provider.bottomHolePressure(p[0], p[1], p[2], p[3], 0.0);
      double table = result.interpolate(p[0], p[1], p[2], p[3], 0.0);
      assertEquals(direct, table, 0.3, "off-grid point " + java.util.Arrays.toString(p));
    }
    for (int f = 0; f < 7; f++) {
      for (int t = 0; t < 5; t++) {
        assertTrue(result.getBhp(f, t, 0, 0, 0) > result.getOutletPressures()[t]);
        if (t > 0) {
          assertTrue(result.getBhp(f, t, 0, 0, 0) > result.getBhp(f, t - 1, 0, 0, 0));
        }
      }
    }
    assertEquals(0, provider.getFailures());
    VfpDeckReader.parse(result.toDeckString());
  }
}
