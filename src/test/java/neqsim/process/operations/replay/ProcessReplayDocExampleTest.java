package neqsim.process.operations.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Runs the Java snippet of {@code docs/process/process_replay.md} verbatim.
 */
class ProcessReplayDocExampleTest extends NeqSimTest {
  @Test
  void docExampleRuns() {
    ProcessSystem process = ProcessReplayTest.buildProcess();
    ReplayTimeSeries synthetic = ProcessReplayTest.syntheticData();
    double[] pressure = new double[50];
    double[] coolerTemperature = new double[50];
    double[] separatorGasTemperature = new double[50];
    for (int i = 0; i < 50; i++) {
      pressure[i] = synthetic.getValue("PT-100", i);
      coolerTemperature[i] = synthetic.getValue("TT-200", i);
      separatorGasTemperature[i] = synthetic.getValue("TT-300", i);
    }

    // --- snippet from docs/process/process_replay.md ---
    ReplayTimeSeries data = ReplayTimeSeries.uniform(50, 60.0).addColumn("PT-100", pressure)
        .addColumn("TT-200", coolerTemperature).addColumn("TT-300", separatorGasTemperature);

    ProcessReplay replay = new ProcessReplay(process).addInput(ReplayBinding.input("PT-100", "feed.pressure", "bara"))
        .addInput(ReplayBinding.input("TT-200", "cooler.outletTemperature", "C").withValidRange(-20.0, 80.0))
        .addOutput(ReplayBinding.output("separator.gasOutStream.temperature", "TT-300", "C")).setMaxIterations(5)
        .setWarmStart(true);

    ReplayResult result = replay.run(data);
    result.setBaselineWindow(0, 25);
    ReplayResult.WindowStatistics event = result.getWindowStatistics("TT-300", 30, 50);
    List<ReplayResult.ChangePoint> changes = result.detectChangePoints("TT-300", 0.5, 5.0, 1.0e-6);
    String summary = result.getSummary();
    String csv = result.toCsv();
    String json = result.toJson();
    // --- end of snippet ---

    assertEquals(-3.0, event.getBias(), 0.05);
    assertEquals(30, changes.get(0).getIndex());
    assertTrue(summary.contains("49 converged"), summary);
    assertTrue(csv.startsWith("index,time,status,attempts,input:PT-100"));
    assertTrue(json.contains("\"changePoints\""));
  }
}
