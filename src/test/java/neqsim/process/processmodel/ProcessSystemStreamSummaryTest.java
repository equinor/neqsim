package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ProcessSystem stream summary methods.
 */
class ProcessSystemStreamSummaryTest {

  @Test
  void testGetStreamSummaryTable() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    Separator sep = new Separator("HP Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    String table = process.getStreamSummaryTable();
    assertNotNull(table, "Stream summary table should not be null");
    assertFalse(table.isEmpty(), "Stream summary table should not be empty");

    // Table should contain key headers
    assertTrue(table.contains("Temperature"), "Table should contain temperature row");
    assertTrue(table.contains("Pressure"), "Table should contain pressure row");
    assertTrue(table.contains("methane"), "Table should contain methane");
    assertTrue(table.contains("Feed"), "Table should contain feed stream name");
  }

  @Test
  void testGetStreamSummaryJson() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 40.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("InletGas", fluid);
    feed.setFlowRate(5000.0, "kg/hr");

    Separator sep = new Separator("TestSep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    String json = process.getStreamSummaryJson();
    assertNotNull(json, "JSON summary should not be null");
    assertFalse(json.isEmpty(), "JSON summary should not be empty");

    // Should be valid JSON-like structure with stream names
    assertTrue(json.contains("InletGas"), "JSON should contain feed stream name");
    assertTrue(json.contains("temperature_C"), "JSON should contain temperature field");
    assertTrue(json.contains("pressure_bara"), "JSON should contain pressure field");
    assertTrue(json.contains("methane"), "JSON should contain component name");
  }

  @Test
  void testGetAllStreams() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.15);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("FeedStream", fluid);
    feed.setFlowRate(8000.0, "kg/hr");

    Separator sep = new Separator("Separator", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    java.util.List<neqsim.process.equipment.stream.StreamInterface> streams =
        process.getAllStreams();
    assertNotNull(streams, "getAllStreams should not return null");
    // Should have at least the feed stream, gas outlet, and liquid outlet
    assertTrue(streams.size() >= 1, "Should find at least 1 stream");
  }
}
