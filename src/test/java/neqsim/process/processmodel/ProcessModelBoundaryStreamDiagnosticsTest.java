package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the per-boundary-stream convergence diagnostics on {@link ProcessModel}. Without them a non-converged model
 * only reported an error magnitude, so a relative flow error of exactly 1.0 (a stream that stopped flowing between
 * outer passes) could not be traced back to a stream.
 *
 * @author esol
 * @version 1.0
 */
class ProcessModelBoundaryStreamDiagnosticsTest {

  /**
   * Creates a small gas fluid.
   *
   * @return configured gas fluid
   */
  private static SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds an upstream separator area feeding a downstream separator area.
   *
   * @return a runnable two-area ProcessModel
   */
  private static ProcessModel buildTwoAreaModel() {
    Stream feed = new Stream("feed", createGasFluid());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    Separator separator = new Separator("separator", feed);

    ProcessSystem upstream = new ProcessSystem("upstream");
    upstream.add(feed);
    upstream.add(separator);

    StreamInterface gasOut = separator.getGasOutStream();
    gasOut.setName("area boundary gas");
    Separator downstreamSeparator = new Separator("downstream separator", gasOut);
    ProcessSystem downstream = new ProcessSystem("downstream");
    downstream.add(gasOut);
    downstream.add(downstreamSeparator);

    ProcessModel model = new ProcessModel();
    model.add("upstream", upstream);
    model.add("downstream", downstream);
    return model;
  }

  /**
   * Every tracked boundary stream must be reported by name with its own flow, temperature and pressure errors.
   */
  @Test
  void testBoundaryStreamErrorsAreRecordedByName() {
    ProcessModel model = buildTwoAreaModel();
    model.runUntilConverged(25, 1e-4);

    List<ProcessModel.BoundaryStreamError> streamErrors = model.getLastBoundaryStreamErrors();
    assertFalse(streamErrors.isEmpty(), "boundary stream errors should be recorded");

    ProcessModel.BoundaryStreamError worst = model.getWorstBoundaryStreamError("flow");
    assertNotNull(worst, "a worst flow-error stream should be identified");
    assertEquals("area boundary gas", worst.getStreamName());
    assertEquals(worst.getStreamName(), model.getWorstBoundaryStreamName("flow"));
    assertTrue(worst.getCurrentFlow() > 0.0, "boundary flow should be recorded");
    assertFalse(worst.isFlowCollapsedToZero(), "a healthy boundary stream must not be flagged as collapsed");
  }

  /**
   * The convergence summary and JSON report must name the offending stream.
   */
  @Test
  void testConvergenceReportsNameTheWorstStream() {
    ProcessModel model = buildTwoAreaModel();
    model.runUntilConverged(25, 1e-4);

    String summary = model.getConvergenceSummary();
    assertTrue(summary.contains("area boundary gas"), "summary should name the worst boundary stream:\n" + summary);

    JsonObject report = JsonParser.parseString(model.getConvergenceReportJson()).getAsJsonObject();
    assertTrue(report.has("boundaryStreamErrors"), "report should include the per-stream error array");
    JsonObject flow = report.getAsJsonObject("errors").getAsJsonObject("flow");
    assertTrue(flow.has("worstStream"), "each error entry should carry the worst-offending stream");
    assertEquals("area boundary gas", flow.getAsJsonObject("worstStream").get("name").getAsString());
    assertTrue(flow.getAsJsonObject("worstStream").has("flowCollapsedToZero"));
  }

  /**
   * The variable name is validated so that a typo fails fast instead of silently returning nothing.
   */
  @Test
  void testWorstBoundaryStreamValidatesVariableName() {
    final ProcessModel model = buildTwoAreaModel();
    model.runUntilConverged(5, 1e-4);

    assertThrows(IllegalArgumentException.class, new Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
        model.getWorstBoundaryStreamError("massflow");
      }
    });
  }

  /**
   * Splitters auto-name their outlets {@code "Split Stream_0"}, {@code "Split Stream_1"}, ... so the same name appears
   * on every splitter in a plant. The diagnostics must name the producing unit as well, otherwise an offending boundary
   * stream cannot be traced to a splitter.
   */
  @Test
  void testBoundaryStreamErrorsNameTheProducingSplitter() {
    Stream feed = new Stream("feed", createGasFluid());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    Splitter splitter = new Splitter("gas splitter", feed, 2);
    splitter.setSplitFactors(new double[] { 0.6, 0.4 });

    ProcessSystem upstream = new ProcessSystem("upstream");
    upstream.add(feed);
    upstream.add(splitter);

    StreamInterface branch = splitter.getSplitStream(0);
    Separator downstreamSeparator = new Separator("downstream separator", branch);
    ProcessSystem downstream = new ProcessSystem("downstream");
    downstream.add(branch);
    downstream.add(downstreamSeparator);

    ProcessModel model = new ProcessModel();
    model.add("upstream", upstream);
    model.add("downstream", downstream);
    model.runUntilConverged(5, 1e-4);

    ProcessModel.BoundaryStreamError splitBranch = null;
    for (ProcessModel.BoundaryStreamError error : model.getLastBoundaryStreamErrors()) {
      if ("Split Stream_0".equals(error.getStreamName())) {
        splitBranch = error;
      }
    }
    assertNotNull(splitBranch, "the split branch must be tracked as a boundary stream");
    assertEquals("upstream::gas splitter", splitBranch.getProducerLabel());
    assertEquals("upstream::gas splitter -> Split Stream_0", splitBranch.getQualifiedName());
  }
}
