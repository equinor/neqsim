package neqsim.process.equipment.pipeline.twophasepipe.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.validation.TwoFluidBenchmarkHarness.BenchmarkPoint;
import neqsim.process.equipment.pipeline.twophasepipe.validation.TwoFluidBenchmarkHarness.Comparison;
import neqsim.process.equipment.pipeline.twophasepipe.validation.TwoFluidBenchmarkHarness.Snapshot;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class TwoFluidBenchmarkHarnessTest {

  @Test
  void testReadsExternalSimulatorCsvFixture() throws Exception {
    Path csv = resourcePath(
        "neqsim/process/equipment/pipeline/twophasepipe/validation/" + "reference_export_fixture.csv");

    List<BenchmarkPoint> points = TwoFluidBenchmarkHarness.readCsv(csv);

    assertEquals(4, points.size());
    assertEquals("pressure_bara", points.get(0).getVariable());
    assertEquals("synthetic reference export fixture", points.get(0).getSource());
  }

  @Test
  void testComparesPipeSnapshotAgainstCsvReference() throws Exception {
    TwoFluidPipe pipe = createSolvedWetGasPipe();
    Snapshot snapshot = TwoFluidBenchmarkHarness.capture(pipe);

    assertTrue(snapshot.getAvailableVariables().contains("entrainment_fraction"));
    assertTrue(snapshot.getAvailableVariables().contains("entrained_droplet_diameter_m"));
    assertTrue(snapshot.getAvailableVariables().contains("inclined_section_gas_carryover_number"));
    assertTrue(snapshot.getAvailableVariables().contains("inclined_section_liquid_fallback_flag"));
    assertTrue(snapshot.getAvailableVariables().contains("severe_slugging_number"));
    assertTrue(snapshot.getAvailableVariables().contains("water_wetting_flag"));
    assertTrue(snapshot.getAvailableVariables().contains("water_dropout_risk_flag"));
    assertTrue(snapshot.getAvailableVariables().contains("severe_slug_potential_flag"));

    double inletPressure = pipe.getPressureProfile()[0] * 1e-5;
    double outletPressure = pipe.getPressureProfile()[pipe.getPressureProfile().length - 1] * 1e-5;
    double averageHoldup = Arrays.stream(pipe.getLiquidHoldupProfile()).average()
        .orElseThrow(() -> new IllegalStateException("No liquid holdup values"));
    List<BenchmarkPoint> reference = Arrays.asList(
        new BenchmarkPoint("fixture", 0.0, pipe.getPositionProfile()[0], "pressure_bara", inletPressure, 0.05, 0.001,
            "generated"),
        new BenchmarkPoint("fixture", 0.0, pipe.getPositionProfile()[pipe.getPositionProfile().length - 1],
            "pressure_bara", outletPressure, 0.05, 0.001, "generated"),
        new BenchmarkPoint("fixture", 0.0, 500.0, "liquid_holdup", averageHoldup, 0.20, 1.0, "generated"),
        new BenchmarkPoint("fixture", 0.0, pipe.getPositionProfile()[0], "entrainment_fraction",
            pipe.getEntrainmentFractionProfile()[0], 1.0e-12, 0.0, "generated"));

    Comparison comparison = TwoFluidBenchmarkHarness.compare(snapshot, reference);

    assertTrue(comparison.isPassed(), comparison.failureSummary());
    assertEquals(0, comparison.getFailureCount());
  }

  @Test
  void testInterpolatesTransientSnapshotsInTimeAndPosition() {
    Snapshot t0 = new Snapshot(0.0, new double[] { 0.0, 100.0 },
        java.util.Collections.singletonMap("pressure_bara", new double[] { 50.0, 49.0 }));
    Snapshot t10 = new Snapshot(10.0, new double[] { 0.0, 100.0 },
        java.util.Collections.singletonMap("pressure_bara", new double[] { 48.0, 47.0 }));
    BenchmarkPoint point = new BenchmarkPoint("transient", 5.0, 50.0, "pressure_bara", 48.5, 1e-12, 0.0, "unit");

    Comparison comparison = TwoFluidBenchmarkHarness.compare(Arrays.asList(t10, t0),
        java.util.Collections.singletonList(point));

    assertTrue(comparison.isPassed(), comparison.failureSummary());
  }

  @Test
  void testKeepsFlagInterpolationBinary() {
    Snapshot t0 = new Snapshot(0.0, new double[] { 0.0, 100.0 },
        java.util.Collections.singletonMap("water_wetting_flag", new double[] { 0.0, 1.0 }));
    Snapshot t10 = new Snapshot(10.0, new double[] { 0.0, 100.0 },
        java.util.Collections.singletonMap("water_wetting_flag", new double[] { 1.0, 0.0 }));
    List<BenchmarkPoint> points = Arrays.asList(
        new BenchmarkPoint("early", 2.0, 75.0, "water_wetting_flag", 1.0, 0.0, 0.0, "unit"),
        new BenchmarkPoint("late", 8.0, 75.0, "water_wetting_flag", 0.0, 0.0, 0.0, "unit"));

    Comparison comparison = TwoFluidBenchmarkHarness.compare(Arrays.asList(t10, t0), points);

    assertTrue(comparison.isPassed(), comparison.failureSummary());
    assertEquals(1.0, comparison.getRows().get(0).getModelValue(), 0.0);
    assertEquals(0.0, comparison.getRows().get(1).getModelValue(), 0.0);
  }

  @Test
  void testPreservesNonFiniteSentinelsDuringInterpolation() {
    Snapshot spatial = new Snapshot(0.0, new double[] { 0.0, 100.0 },
        java.util.Collections.singletonMap("severe_slugging_number", new double[] { Double.POSITIVE_INFINITY, 2.0 }));

    assertTrue(Double.isInfinite(spatial.valueAt("severe_slugging_number", 25.0)));
    assertEquals(2.0, spatial.valueAt("severe_slugging_number", 75.0), 0.0);

    Snapshot t0 = new Snapshot(0.0, new double[] { 0.0 },
        java.util.Collections.singletonMap("severe_slugging_number", new double[] { Double.POSITIVE_INFINITY }));
    Snapshot t10 = new Snapshot(10.0, new double[] { 0.0 },
        java.util.Collections.singletonMap("severe_slugging_number", new double[] { 2.0 }));
    BenchmarkPoint earlyPoint = new BenchmarkPoint("early", 2.0, 0.0, "severe_slugging_number", 0.0, 0.0, 0.0, "unit");
    BenchmarkPoint latePoint = new BenchmarkPoint("late", 8.0, 0.0, "severe_slugging_number", 2.0, 0.0, 0.0, "unit");

    Comparison early = TwoFluidBenchmarkHarness.compare(Arrays.asList(t0, t10),
        java.util.Collections.singletonList(earlyPoint));
    Comparison late = TwoFluidBenchmarkHarness.compare(Arrays.asList(t0, t10),
        java.util.Collections.singletonList(latePoint));

    assertTrue(Double.isInfinite(early.getRows().get(0).getModelValue()));
    assertTrue(late.isPassed(), late.failureSummary());
    assertEquals(2.0, late.getRows().get(0).getModelValue(), 0.0);
  }

  private TwoFluidPipe createSolvedWetGasPipe() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 35.0, 60.0);
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("propane", 0.02);
    fluid.addComponent("n-heptane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("benchmark-feed", fluid);
    inlet.setFlowRate(15000.0, "kg/hr");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("benchmark-pipe", inlet);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.1524);
    pipe.setRoughness(4.6e-5);
    pipe.setNumberOfSections(10);
    pipe.setElevationProfile(new double[10]);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();
    return pipe;
  }

  private Path resourcePath(String resource) throws URISyntaxException {
    return Paths.get(Thread.currentThread().getContextClassLoader().getResource(resource).toURI());
  }
}
