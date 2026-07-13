package neqsim.process.operations;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.InstrumentTagRole;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link OperationalTagMap} JSON (de)serialisation and benchmark comparison.
 *
 * @author ESOL
 * @version 1.0
 */
class OperationalTagMapTest extends neqsim.NeqSimTest {

  /**
   * toJson followed by fromJson must preserve all binding fields.
   */
  @Test
  void testJsonRoundTrip() {
    OperationalTagMap map = new OperationalTagMap();
    map.addBinding(
        OperationalTagBinding.builder("20FT0001").historianTag("20FT0001.PV").automationAddress("feed.flowRate")
            .unit("kg/hr").role(InstrumentTagRole.INPUT).pidReference("C074-1").description("Feed rate").build());
    map.addBinding(OperationalTagBinding.builder("27PT1001").historianTag("27PT1001.PV")
        .automationAddress("compressor.outletStream.pressure").unit("bara").role(InstrumentTagRole.BENCHMARK)
        .description("Discharge P").build());

    String json = map.toJson();
    Assertions.assertTrue(json.contains("20FT0001"));
    Assertions.assertTrue(json.contains("bindings"));

    OperationalTagMap restored = OperationalTagMap.fromJson(json);
    Assertions.assertEquals(2, restored.getBindings().size());
    OperationalTagBinding b = restored.getBinding("27PT1001");
    Assertions.assertNotNull(b);
    Assertions.assertEquals("27PT1001.PV", b.getHistorianTag());
    Assertions.assertEquals("compressor.outletStream.pressure", b.getAutomationAddress());
    Assertions.assertEquals("bara", b.getUnit());
    Assertions.assertEquals(InstrumentTagRole.BENCHMARK, b.getRole());
    Assertions.assertEquals("Discharge P", b.getDescription());
  }

  /**
   * fromJson must accept snake_case keys and reject malformed input.
   */
  @Test
  void testFromJsonSnakeCaseAndValidation() {
    String json = "{\"bindings\":[{\"logical_tag\":\"T1\",\"automation_address\":\"feed.temperature\","
        + "\"unit\":\"C\",\"role\":\"input\"}]}";
    OperationalTagMap map = OperationalTagMap.fromJson(json);
    Assertions.assertEquals(1, map.getBindings().size());
    Assertions.assertEquals(InstrumentTagRole.INPUT, map.getBinding("T1").getRole());

    Assertions.assertThrows(IllegalArgumentException.class, () -> OperationalTagMap.fromJson(""));
    Assertions.assertThrows(IllegalArgumentException.class, () -> OperationalTagMap.fromJson("{\"x\":1}"));
  }

  /**
   * compareBenchmarks must report measured vs simulated for BENCHMARK tags only.
   */
  @Test
  void testCompareBenchmarks() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();

    OperationalTagMap map = new OperationalTagMap();
    map.addBinding(
        OperationalTagBinding.builder("20PT0001").historianTag("20PT0001.PV").automationAddress("feed.pressure")
            .unit("bara").role(InstrumentTagRole.BENCHMARK).description("Feed pressure").build());
    // An INPUT tag must be ignored by compareBenchmarks.
    map.addBinding(
        OperationalTagBinding.builder("20FT0001").historianTag("20FT0001.PV").automationAddress("feed.flowRate")
            .unit("kg/hr").role(InstrumentTagRole.INPUT).description("Feed flow").build());

    Map<String, Double> field = new HashMap<String, Double>();
    field.put("20PT0001", 49.0); // measured slightly below simulated 50
    field.put("20FT0001", 9000.0);

    String result = map.compareBenchmarks(process, field);
    Assertions.assertTrue(result.contains("20PT0001"), "benchmark tag should be present");
    Assertions.assertFalse(result.contains("20FT0001"), "input tag must be excluded");
    Assertions.assertTrue(result.contains("\"count\": 1"), "exactly one benchmark compared: " + result);
    Assertions.assertTrue(result.contains("measured"));
    Assertions.assertTrue(result.contains("simulated"));
    Assertions.assertTrue(result.contains("deltaPercent"));
  }
}
