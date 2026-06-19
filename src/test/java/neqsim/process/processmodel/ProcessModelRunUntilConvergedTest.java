package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessModel#runUntilConverged(int, double)} and {@link ProcessModel#getConvergenceReportJson()}.
 *
 * @author NeqSim
 * @version 1.0
 */
class ProcessModelRunUntilConvergedTest {

  /**
   * Creates a small single-component gas fluid.
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
   * Builds a simple two-area model: an upstream area with a separator feeding a downstream area.
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
    Separator downstreamSep = new Separator("downstream separator", gasOut);
    ProcessSystem downstream = new ProcessSystem("downstream");
    downstream.add(gasOut);
    downstream.add(downstreamSep);

    ProcessModel model = new ProcessModel();
    model.add("upstream", upstream);
    model.add("downstream", downstream);
    return model;
  }

  /**
   * runUntilConverged should run in iterating mode and report convergence for a simple model.
   */
  @Test
  void testRunUntilConvergedConverges() {
    ProcessModel model = buildTwoAreaModel();
    boolean converged = model.runUntilConverged(25, 1e-4);

    assertTrue(converged, "Simple feed-forward model should converge");
    assertTrue(model.isModelConverged(), "isModelConverged should agree with the return value");
    assertFalse(model.isRunStep(), "runUntilConverged must force iterating mode");
    assertTrue(model.getLastIterationCount() >= 1, "At least one iteration should run");
    assertEquals(25, model.getMaxIterations(), "maxIterations should be applied");
  }

  /**
   * The JSON convergence report should be parseable and describe each area.
   */
  @Test
  void testConvergenceReportJson() {
    ProcessModel model = buildTwoAreaModel();
    model.runUntilConverged(25, 1e-4);

    String json = model.getConvergenceReportJson();
    JsonObject report = JsonParser.parseString(json).getAsJsonObject();

    assertEquals("1.0", report.get("schemaVersion").getAsString());
    assertTrue(report.get("converged").getAsBoolean(), "Report should show converged");
    assertTrue(report.has("errors"), "Report should include an errors block");
    assertTrue(report.getAsJsonObject("errors").getAsJsonObject("flow").has("tolerance"));

    JsonArray areas = report.getAsJsonArray("areas");
    assertEquals(2, areas.size(), "Both process areas should be reported");
    JsonObject firstArea = areas.get(0).getAsJsonObject();
    assertTrue(firstArea.has("name"));
    assertTrue(firstArea.has("solved"));
    assertTrue(firstArea.has("unsolvedUnits"));
  }

  /**
   * Invalid arguments should be rejected before running.
   */
  @Test
  void testRunUntilConvergedValidatesArguments() {
    ProcessModel model = buildTwoAreaModel();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
	model.runUntilConverged(0, 1e-4);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
	model.runUntilConverged(10, -1.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
	model.runUntilConverged(10, Double.NaN);
      }
    });
  }
}
