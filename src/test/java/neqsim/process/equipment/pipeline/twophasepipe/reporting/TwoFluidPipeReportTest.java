package neqsim.process.equipment.pipeline.twophasepipe.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.validation.TwoFluidBenchmarkHarness;
import neqsim.process.equipment.pipeline.twophasepipe.validation.TwoFluidBenchmarkHarness.BenchmarkPoint;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class TwoFluidPipeReportTest {

  @Test
  void testReportExportsProfilesSummariesAndComparisonTable() throws Exception {
    TwoFluidPipe pipe = runTwoFluidPipe("report-gas", methaneFluid(50.0, 298.15), 25000.0, 1000.0,
        0.2, 10);

    String steadyCsv = TwoFluidPipeReport.toSteadyStateProfileCsv(pipe);
    assertTrue(steadyCsv.startsWith("position_m,pressure_bara,temperature_C"));
    assertTrue(steadyCsv.contains("flow_regime"));
    assertEquals(pipe.getPositionProfile().length + 1, steadyCsv.split("\\R").length);

    List<TwoFluidPipeReport.ProfileSnapshot> snapshots = TwoFluidPipeReport.newSnapshotList();
    snapshots.add(TwoFluidPipeReport.capture(pipe));
    pipe.runTransient(0.1, java.util.UUID.randomUUID());
    snapshots.add(TwoFluidPipeReport.capture(pipe));
    String transientCsv = TwoFluidPipeReport.toTransientProfileCsv(snapshots);
    assertTrue(transientCsv.startsWith("time_s,position_m,pressure_bara"));
    assertTrue(transientCsv.split("\\R").length >= 2 * pipe.getPositionProfile().length);

    String summary = TwoFluidPipeReport.toSummaryText(pipe);
    assertTrue(summary.contains("TwoFluidPipe summary"));
    assertTrue(summary.contains("Average liquid holdup"));

    String json = TwoFluidPipeReport.toSummaryJson(pipe);
    assertTrue(JsonParser.parseString(json).getAsJsonObject().has("outletPressureBara"));

    String events = TwoFluidPipeReport.toSlugAndFlowAssuranceCsv(pipe);
    assertTrue(events.startsWith("event_type,position_m,value,unit,description"));
    assertTrue(events.contains("erosion_margin"));

    BenchmarkPoint point = new BenchmarkPoint("report", pipe.getSimulationTime(),
        pipe.getPositionProfile()[0], "pressure_bara", pipe.getPressureProfile()[0] * 1e-5, 0.01,
        0.001, "unit");
    TwoFluidBenchmarkHarness.Comparison comparison = TwoFluidBenchmarkHarness
        .compare(TwoFluidBenchmarkHarness.capture(pipe), java.util.Collections.singletonList(point));
    String comparisonCsv = TwoFluidPipeReport.toComparisonCsv(comparison);
    assertTrue(comparisonCsv.startsWith("case,time_s,position_m,variable"));
    assertTrue(comparisonCsv.contains(",true,"));

    Path tempDir = Files.createTempDirectory("twofluid-report-test");
    Path profilePath = tempDir.resolve("profile.csv");
    Path summaryPath = tempDir.resolve("summary.json");
    TwoFluidPipeReport.writeSteadyStateProfileCsv(pipe, profilePath);
    TwoFluidPipeReport.writeSummaryJson(pipe, summaryPath);
    assertTrue(Files.readString(profilePath).startsWith("position_m,pressure_bara"));
    assertTrue(JsonParser.parseString(Files.readString(summaryPath)).getAsJsonObject()
        .has("averageLiquidHoldup"));
  }

  @Test
  void testOnePhaseGasReasonableAgainstBeggsAndBrill() {
    SystemInterface fluid = methaneFluid(50.0, 298.15);
    double flowKgHr = 25000.0;
    TwoFluidPipe tf = runTwoFluidPipe("one-phase-gas", fluid, flowKgHr, 5000.0, 0.2, 25);
    PipeBeggsAndBrills bb = runBeggsAndBrill("bb-gas", fluid, flowKgHr, 5000.0, 0.2, 25);

    double dpTf = tf.getInletPressure() - tf.getOutletPressure();
    double dpBb = bb.getInletStream().getPressure("bara") - bb.getOutletStream().getPressure("bara");
    double ratio = dpTf / dpBb;

    assertTrue(dpTf > 0.0);
    assertTrue(dpBb > 0.0);
    assertTrue(ratio > 0.5 && ratio < 2.0,
        "One-phase gas pressure drop should be close to Beggs & Brill. Ratio=" + ratio);
  }

  @Test
  void testTwoPhaseWetGasReasonableAgainstBeggsAndBrillAndLiteratureRange() {
    SystemInterface fluid = wetGasFluid(80.0, 313.15);
    double flowKgHr = 30000.0;
    double length = 10000.0;
    TwoFluidPipe tf = runTwoFluidPipe("two-phase-wet-gas", fluid, flowKgHr, length, 0.254, 30);
    PipeBeggsAndBrills bb = runBeggsAndBrill("bb-wet-gas", fluid, flowKgHr, length, 0.254, 30);

    double dpTf = tf.getInletPressure() - tf.getOutletPressure();
    double dpBb = bb.getInletStream().getPressure("bara") - bb.getOutletStream().getPressure("bara");
    double dpPerKm = dpTf / (length / 1000.0);
    double ratio = dpTf / dpBb;
    double avgHoldup = Arrays.stream(tf.getLiquidHoldupProfile()).average().orElse(0.0);

    assertTrue(dpTf > 0.0);
    assertTrue(dpBb > 0.0);
    assertTrue(ratio > 0.3 && ratio < 3.0,
        "Two-phase pressure drop should be same order as Beggs & Brill. Ratio=" + ratio);
    assertTrue(dpPerKm > 0.01 && dpPerKm < 5.0,
        "Wet-gas pressure gradient should be within broad literature range. dP/km=" + dpPerKm);
    assertTrue(avgHoldup >= 0.0 && avgHoldup <= 1.0);
  }

  @Test
  void testThreePhaseReasonableAgainstBeggsAndBrillAndPhysicalRanges() {
    SystemInterface fluid = threePhaseFluid(60.0, 323.15);
    double flowKgHr = 25000.0;
    double length = 5000.0;
    TwoFluidPipe tf = runTwoFluidPipe("three-phase", fluid, flowKgHr, length, 0.203, 25);
    PipeBeggsAndBrills bb = runBeggsAndBrill("bb-three-phase", fluid, flowKgHr, length, 0.203, 25);

    double dpTf = tf.getInletPressure() - tf.getOutletPressure();
    double dpBb = bb.getInletStream().getPressure("bara") - bb.getOutletStream().getPressure("bara");
    double avgHoldup = Arrays.stream(tf.getLiquidHoldupProfile()).average().orElse(0.0);
    double avgWaterCut = Arrays.stream(tf.getWaterCutProfile()).average().orElse(0.0);
    double outletFlow = tf.getOutletStream().getFlowRate("kg/hr");

    assertTrue(dpTf > 0.0);
    assertTrue(dpBb > 0.0);
    assertTrue(dpTf / dpBb > 0.2 && dpTf / dpBb < 5.0,
        "Three-phase dP should be same engineering order as Beggs & Brill");
    assertTrue(avgHoldup >= 0.0 && avgHoldup <= 1.0);
    assertTrue(avgWaterCut >= 0.0 && avgWaterCut <= 1.0);
    assertTrue(Math.abs(outletFlow - flowKgHr) / flowKgHr < 0.05);
    assertFalse(tf.getDominantFlowRegime().isBlank());
  }

  private TwoFluidPipe runTwoFluidPipe(String name, SystemInterface fluid, double flowKgHr,
      double length, double diameter, int sections) {
    Stream inlet = new Stream(name + "-feed", fluid.clone());
    inlet.setFlowRate(flowKgHr, "kg/hr");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe(name, inlet);
    pipe.setLength(length);
    pipe.setDiameter(diameter);
    pipe.setRoughness(4.6e-5);
    pipe.setNumberOfSections(sections);
    pipe.setElevationProfile(new double[sections]);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();
    return pipe;
  }

  private PipeBeggsAndBrills runBeggsAndBrill(String name, SystemInterface fluid, double flowKgHr,
      double length, double diameter, int increments) {
    Stream inlet = new Stream(name + "-feed", fluid.clone());
    inlet.setFlowRate(flowKgHr, "kg/hr");
    inlet.run();

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(name, inlet);
    pipe.setLength(length);
    pipe.setDiameter(diameter);
    pipe.setPipeWallRoughness(4.6e-5);
    pipe.setAngle(0.0);
    pipe.setNumberOfIncrements(increments);
    pipe.run();
    return pipe;
  }

  private SystemInterface methaneFluid(double pressureBara, double temperatureK) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private SystemInterface wetGasFluid(double pressureBara, double temperatureK) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("propane", 0.02);
    fluid.addComponent("n-heptane", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private SystemInterface threePhaseFluid(double pressureBara, double temperatureK) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("propane", 0.02);
    fluid.addComponent("n-pentane", 0.08);
    fluid.addComponent("n-heptane", 0.07);
    fluid.addComponent("nC10", 0.05);
    fluid.addComponent("water", 0.10);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }
}
