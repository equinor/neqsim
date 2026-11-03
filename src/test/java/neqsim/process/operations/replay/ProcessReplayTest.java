package neqsim.process.operations.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.NeqSimTest;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.InstrumentTagRole;
import neqsim.process.operations.OperationalTagBinding;
import neqsim.process.operations.OperationalTagMap;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests {@link ProcessReplay} on a feed, cooler and separator flowsheet with synthetic plant data containing a hidden
 * cooler temperature bias that starts at row {@value #CHANGE_ROW}.
 */
class ProcessReplayTest extends NeqSimTest {
  private static final int ROWS = 50;
  private static final int CHANGE_ROW = 30;
  private static final int INVALID_ROW = 12;
  private static final double HIDDEN_BIAS_C = 3.0;

  /**
   * Builds the flowsheet.
   *
   * @return process system with units "feed", "cooler" and "separator"
   */
  static ProcessSystem buildProcess() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 50.0);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-hexane", 0.03);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(40.0, "C");
    feed.setPressure(50.0, "bara");
    Heater cooler = new Heater("cooler", feed);
    cooler.setOutTemperature(15.0, "C");
    Separator separator = new Separator("separator", cooler.getOutletStream());
    ProcessSystem process = new ProcessSystem("replay test");
    process.add(feed);
    process.add(cooler);
    process.add(separator);
    process.run();
    return process;
  }

  /**
   * Generates synthetic plant data from a truth model whose cooler runs {@value #HIDDEN_BIAS_C} K warmer than reported
   * from row {@value #CHANGE_ROW}, with small measurement noise, and one missing input value.
   *
   * @return the time series
   */
  static ReplayTimeSeries syntheticData() {
    ProcessSystem truth = buildProcess();
    ProcessAutomation automation = truth.getAutomation();
    Random random = new Random(42L);
    double[] pressure = new double[ROWS];
    double[] coolerT = new double[ROWS];
    double[] gasT = new double[ROWS];
    double[] liquid = new double[ROWS];
    for (int i = 0; i < ROWS; i++) {
      pressure[i] = 50.0 + 2.0 * Math.sin(i / 7.0);
      coolerT[i] = 15.0 + 3.0 * Math.cos(i / 5.0);
      double actualT = coolerT[i] + (i >= CHANGE_ROW ? HIDDEN_BIAS_C : 0.0);
      automation.setVariableValue("feed.pressure", pressure[i], "bara");
      automation.setVariableValue("cooler.outletTemperature", actualT, "C");
      truth.run();
      gasT[i] = automation.getVariableValue("separator.gasOutStream.temperature", "C") + 0.02 * random.nextGaussian();
      liquid[i] = automation.getVariableValue("separator.liquidOutStream.flowRate", "kg/hr")
          * (1.0 + 0.001 * random.nextGaussian());
    }
    coolerT[INVALID_ROW] = Double.NaN;
    ReplayTimeSeries series = ReplayTimeSeries.uniform(ROWS, 60.0);
    series.addColumn("PT-100", pressure).addColumn("TT-200", coolerT).addColumn("TT-300", gasT).addColumn("FT-400",
        liquid);
    return series;
  }

  /**
   * Creates the replay with two inputs and two outputs.
   *
   * @param process process to replay
   * @return configured replay
   */
  static ProcessReplay configure(ProcessSystem process) {
    return new ProcessReplay(process).addInput(ReplayBinding.input("PT-100", "feed.pressure", "bara"))
        .addInput(ReplayBinding.input("TT-200", "cooler.outletTemperature", "C"))
        .addOutput(ReplayBinding.output("separator.gasOutStream.temperature", "TT-300", "C"))
        .addOutput(ReplayBinding.output("separator.liquidOutStream.flowRate", "FT-400", "kg/hr"));
  }

  @Test
  void moleFractionOutputOfAStreamComponent() {
    ProcessSystem process = buildProcess();
    ProcessAutomation automation = process.getAutomation();
    double fraction = automation.getVariableValue("separator.gasOutStream.moleFraction_methane", "");
    double ppm = automation.getVariableValue("separator.gasOutStream.moleFraction_methane", "ppm");
    double percent = automation.getVariableValue("separator.gasOutStream.moleFraction_methane", "mol%");
    assertTrue(fraction > 0.8 && fraction < 1.0, "methane fraction " + fraction);
    assertEquals(fraction * 1.0e6, ppm, 1.0e-6 * ppm);
    assertEquals(fraction * 100.0, percent, 1.0e-9);

    double[] pressure = {50.0, 45.0, 40.0};
    ReplayTimeSeries data = ReplayTimeSeries.uniform(3, 60.0).addColumn("PT-100", pressure);
    ReplayResult result = new ProcessReplay(process).addInput(ReplayBinding.input("PT-100", "feed.pressure", "bara"))
        .addOutput(ReplayBinding.output("separator.gasOutStream.moleFraction_n-hexane", "AT-500", "ppm")).run(data);
    assertEquals(3, result.getConvergedCount(), result.getSummary());
    double first = result.getStep(0).getPredicted().get("AT-500");
    double last = result.getStep(2).getPredicted().get("AT-500");
    assertTrue(first > 0.0 && last > 0.0 && Math.abs(last - first) > 1.0e-3,
        "hexane in the gas must respond to the separator pressure: " + first + " -> " + last);

    String unknown = automation.getVariableValueSafe("separator.gasOutStream.moleFraction_xenon", "ppm");
    assertFalse(unknown.contains("\"success\""), unknown);
  }

  @Test
  void replayDetectsHiddenBiasAndRecordsInvalidStep() {
    ReplayTimeSeries series = syntheticData();
    ReplayResult result = configure(buildProcess()).setMaxIterations(5).run(series);

    assertEquals(ROWS, result.size());
    assertEquals(ROWS - 1, result.getConvergedCount(), result.getSummary());
    assertEquals(ReplayStepRecord.Status.INPUT_INVALID, result.getStep(INVALID_ROW).getStatus());
    assertTrue(result.getStep(INVALID_ROW + 1).isConverged(), "replay must continue after the invalid row");
    assertEquals(1, result.getFailedCount());

    result.setBaselineWindow(0, 25);
    ReplayResult.WindowStatistics base = result.getBaseline("TT-300");
    assertEquals(24, base.getCount(), "invalid row is excluded from the baseline");
    assertEquals(0.0, base.getBias(), 0.02);
    assertTrue(base.getRms() < 0.05, "baseline residual RMS " + base.getRms());
    assertEquals(0.0, result.getBaseline("FT-400").getBias(), 0.01 * 1000.0);

    ReplayResult.WindowStatistics event = result.getWindowStatistics("TT-300", CHANGE_ROW, ROWS);
    assertEquals(-HIDDEN_BIAS_C, event.getBias(), 0.05);

    List<ReplayResult.ChangePoint> changes = result.detectChangePoints("TT-300", 0.5, 5.0, 1.0e-6);
    assertEquals(1, changes.size(), "one change expected, got " + changes.size());
    assertEquals(CHANGE_ROW, changes.get(0).getIndex());
    assertEquals(-1, changes.get(0).getDirection());
    assertEquals(CHANGE_ROW * 60.0, changes.get(0).getTime(), 1e-9);

    double[] z = result.getStandardizedResiduals("TT-300", 1.0e-6);
    assertTrue(Double.isNaN(z[INVALID_ROW]));
    assertTrue(z[ROWS - 1] < -10.0, "standardised residual after the change " + z[ROWS - 1]);
  }

  @Test
  void coldStartGivesSameResidualsAndLeavesModelUntouched() {
    ReplayTimeSeries series = syntheticData();
    ProcessSystem process = buildProcess();
    double before = process.getAutomation().getVariableValue("cooler.outletTemperature", "C");
    ReplayResult cold = configure(process).setWarmStart(false).run(series);
    ReplayResult warm = configure(buildProcess()).run(series);
    assertFalse(cold.isWarmStart());
    assertEquals(ROWS - 1, cold.getConvergedCount());
    assertEquals(before, process.getAutomation().getVariableValue("cooler.outletTemperature", "C"), 1e-12);
    for (int i = 0; i < ROWS; i++) {
      assertEquals(warm.getStep(i).getStatus(), cold.getStep(i).getStatus());
      if (warm.getStep(i).isConverged()) {
        assertEquals(warm.getStep(i).getResidual("TT-300"), cold.getStep(i).getResidual("TT-300"), 1e-6);
      }
    }
  }

  @Test
  void badAddressAndFilteredRowsAreRecordedNotThrown() {
    ReplayTimeSeries series = syntheticData();
    ProcessReplay replay = new ProcessReplay(buildProcess())
        .addInput(ReplayBinding.input("TT-200", "noSuchUnit.outletTemperature", "C"))
        .addOutput(ReplayBinding.output("separator.gasOutStream.temperature", "TT-300", "C"))
        .setStepFilter(new ProcessReplay.StepFilter() {
          @Override
          public boolean accept(int index, Map<String, Double> row) {
            return index % 2 == 0;
          }
        });
    ReplayResult result = replay.run(series);
    assertEquals(ROWS / 2, result.count(ReplayStepRecord.Status.SKIPPED));
    assertEquals(ROWS / 2, result.count(ReplayStepRecord.Status.INPUT_INVALID));
    assertEquals(0, result.getConvergedCount());
    assertTrue(result.getStep(0).getMessage().contains("noSuchUnit"), result.getStep(0).getMessage());
  }

  @Test
  void validRangeAndRetryHook() {
    ReplayTimeSeries series = syntheticData();
    final int[] retries = {0};
    ReplayResult result = configure(buildProcess()).setMaxRetries(2).setRetryHook(new ProcessReplay.RetryHook() {
      @Override
      public boolean beforeRetry(int attempt, ProcessAutomation automation, ReplayStepRecord record) {
        retries[0]++;
        return true;
      }
    }).run(series);
    assertEquals(0, retries[0], "converged steps are not retried");
    assertEquals(1, result.getStep(0).getAttempts());

    ProcessReplay ranged = new ProcessReplay(buildProcess())
        .addInput(ReplayBinding.input("TT-200", "cooler.outletTemperature", "C").withValidRange(14.0, 100.0))
        .addOutput(ReplayBinding.output("separator.gasOutStream.temperature", "TT-300", "C"));
    ReplayResult rangedResult = ranged.run(series);
    int below = 0;
    for (int i = 0; i < ROWS; i++) {
      double raw = series.getValue("TT-200", i);
      if (!Double.isNaN(raw) && raw < 14.0) {
        below++;
      }
    }
    assertTrue(below > 0);
    assertEquals(below + 1, rangedResult.count(ReplayStepRecord.Status.INPUT_INVALID));
  }

  @Test
  void csvAndJsonExportHaveExpectedColumns() {
    ReplayResult result = configure(buildProcess()).run(syntheticData()).setBaselineWindow(0, 25);
    List<String> expected = Arrays.asList("index", "time", "status", "attempts", "input:PT-100", "input:TT-200",
        "predicted:TT-300", "measured:TT-300", "residual:TT-300", "z:TT-300", "predicted:FT-400", "measured:FT-400",
        "residual:FT-400", "z:FT-400", "message");
    assertEquals(expected, result.getCsvColumns());
    String[] lines = result.toCsv().split("\n");
    assertEquals(ROWS + 1, lines.length);
    assertEquals(String.join(",", expected), lines[0]);
    assertTrue(lines[INVALID_ROW + 1].contains("INPUT_INVALID"));

    ReplayTimeSeries reread = ReplayTimeSeries.fromCsvLines(Arrays.asList(lines));
    assertEquals(ROWS, reread.size());
    assertTrue(reread.hasTag("residual:TT-300"));

    JsonObject json = JsonParser.parseString(result.toJson()).getAsJsonObject();
    assertEquals(ROWS, json.getAsJsonObject("summary").get("steps").getAsInt());
    assertEquals(ROWS - 1, json.getAsJsonObject("summary").get("converged").getAsInt());
    assertEquals(1, json.getAsJsonObject("summary").get("input_invalid").getAsInt());
    assertNotNull(json.getAsJsonObject("baseline").getAsJsonObject("TT-300"));
    assertTrue(json.getAsJsonArray("changePoints").size() >= 1);
    assertEquals(ROWS, json.getAsJsonArray("steps").size());
  }

  @Test
  void bindingsFromOperationalTagMap() {
    OperationalTagMap map = new OperationalTagMap()
        .addBinding(OperationalTagBinding.builder("cooler_outlet_temperature").historianTag("TT-200")
            .automationAddress("cooler.outletTemperature").unit("C").role(InstrumentTagRole.INPUT).build())
        .addBinding(OperationalTagBinding.builder("separator_gas_temperature").historianTag("TT-300")
            .automationAddress("separator.gasOutStream.temperature").unit("C").role(InstrumentTagRole.BENCHMARK)
            .build());
    ProcessReplay replay = ProcessReplay.fromTagMap(buildProcess(), map);
    assertEquals(1, replay.getInputs().size());
    assertEquals(1, replay.getOutputs().size());
    ReplayResult result = replay.run(syntheticData());
    assertEquals(ROWS - 1, result.getConvergedCount());
    assertEquals(0.0, result.getWindowStatistics("TT-300", 0, 25).getBias(), 0.02);
  }
}
