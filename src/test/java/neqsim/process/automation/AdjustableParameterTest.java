package neqsim.process.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the adjustable-parameter registry exposed through {@link ProcessAutomation}.
 */
class AdjustableParameterTest {

  /**
   * Builds a small process with a separator, compressor, cooler, and an adjuster whose name does not match the variable
   * it actually drives.
   *
   * @return the constructed and run process system
   */
  private ProcessSystem buildProcess() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 70.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(70.0, "bara");

    Separator separator = new Separator("HP Sep", feed);

    Compressor compressor = new Compressor("Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(120.0);

    Cooler cooler = new Cooler("HT injection cooler", compressor.getOutletStream());
    cooler.setOutletTemperature(30.0, "C");

    // Adjuster handle whose name suggests a "suction cooler" but actually drives the
    // HT injection cooler temperature toward a target. The target (compressor outlet
    // pressure already at 120 bara) is satisfied immediately so the loop converges.
    Adjuster adjuster = new Adjuster("fourth stage suction cooler temperature");
    adjuster.setAdjustedVariable(cooler, "temperature", "C");
    adjuster.setTargetVariable(compressor, "pressure", 120.0, "bara");
    adjuster.setMinAdjustedValue(10.0);
    adjuster.setMaxAdjustedValue(60.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
    process.add(adjuster);
    process.run();
    return process;
  }

  @Test
  void testRegistryContainsAdjusterWithRealTarget() {
    ProcessSystem process = buildProcess();
    ProcessAutomation automation = new ProcessAutomation(process);

    List<AdjustableParameter> params = automation.getAdjustableParameters();
    assertNotNull(params);
    assertFalse(params.isEmpty());

    AdjustableParameter adjusterParam = null;
    for (AdjustableParameter p : params) {
      if (p.getSource() == AdjustableParameter.Source.ADJUSTER) {
        adjusterParam = p;
        break;
      }
    }
    assertNotNull(adjusterParam, "registry should expose the adjuster as an adjustable parameter");

    // The handle is named after a "suction cooler" but actually drives the HT injection cooler.
    assertEquals("fourth stage suction cooler temperature", adjusterParam.getName());
    assertEquals("HT injection cooler", adjusterParam.getTargetUnitName());
    assertEquals("temperature", adjusterParam.getTargetProperty());
    assertEquals("C", adjusterParam.getUnit());
    assertEquals(Double.valueOf(10.0), adjusterParam.getLowerBound());
    assertEquals(Double.valueOf(60.0), adjusterParam.getUpperBound());
    // Address should point at the unit/property the adjuster actually affects.
    assertEquals("HT injection cooler.temperature", adjusterParam.getAddress());
  }

  @Test
  void testRegistryContainsWritableInputVariables() {
    ProcessSystem process = buildProcess();
    ProcessAutomation automation = new ProcessAutomation(process);

    List<AdjustableParameter> params = automation.getAdjustableParameters();

    boolean foundInput = false;
    for (AdjustableParameter p : params) {
      if (p.getSource() == AdjustableParameter.Source.INPUT_VARIABLE) {
        foundInput = true;
        assertNotNull(p.getAddress());
        assertNotNull(p.getName());
        assertNotNull(p.getTargetUnitName());
      }
    }
    assertTrue(foundInput, "registry should expose writable INPUT variables");
  }

  @Test
  void testGetAdjustableParametersJsonIsWellFormed() {
    ProcessSystem process = buildProcess();
    ProcessAutomation automation = new ProcessAutomation(process);

    String json = automation.getAdjustableParametersJson();
    assertNotNull(json);

    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertEquals(ProcessAutomation.SCHEMA_VERSION, root.get("schemaVersion").getAsString());
    assertTrue(root.has("parameters"));
    JsonArray arr = root.getAsJsonArray("parameters");
    assertEquals(root.get("count").getAsInt(), arr.size());
    assertTrue(arr.size() > 0);

    boolean adjusterInJson = false;
    for (int i = 0; i < arr.size(); i++) {
      JsonObject p = arr.get(i).getAsJsonObject();
      assertTrue(p.has("name"));
      assertTrue(p.has("address"));
      assertTrue(p.has("source"));
      if ("ADJUSTER".equals(p.get("source").getAsString())) {
        adjusterInJson = true;
        assertEquals("HT injection cooler", p.get("targetUnitName").getAsString());
        assertEquals("temperature", p.get("targetProperty").getAsString());
      }
    }
    assertTrue(adjusterInJson, "JSON registry should include the adjuster-sourced parameter");
  }
}
