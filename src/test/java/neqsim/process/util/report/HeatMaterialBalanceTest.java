package neqsim.process.util.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for HeatMaterialBalance report generation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class HeatMaterialBalanceTest extends neqsim.NeqSimTest {

  private static ProcessSystem process;

  @BeforeAll
  public static void setUp() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");

    Separator separator = new Separator("HP Separator", feed);

    Compressor compressor = new Compressor("Gas Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0);

    Cooler cooler = new Cooler("After Cooler", compressor.getOutletStream());
    cooler.setOutTemperature(273.15 + 30.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
    process.run();
  }

  @Test
  public void testToJson() {
    HeatMaterialBalance hmb = new HeatMaterialBalance(process);
    String json = hmb.toJson();

    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("streamTable"), "Should contain stream table");
    assertTrue(json.contains("equipmentSummary"), "Should contain equipment summary");
    assertTrue(json.contains("Feed"), "Should contain feed stream");
  }

  @Test
  public void testStreamTableToCSV() {
    HeatMaterialBalance hmb = new HeatMaterialBalance(process);
    String csv = hmb.streamTableToCSV();

    assertNotNull(csv);
    assertFalse(csv.isEmpty());
    assertTrue(csv.contains(","), "CSV should contain comma separators");
  }

  @Test
  public void testGetAllStreams() {
    HeatMaterialBalance hmb = new HeatMaterialBalance(process);
    java.util.List<neqsim.process.equipment.stream.StreamInterface> streams = hmb.getAllStreams();

    assertNotNull(streams);
    assertTrue(streams.size() >= 3, "Should have at least feed + separator outlets");
  }

  @Test
  public void testCustomUnits() {
    HeatMaterialBalance hmb = new HeatMaterialBalance(process);
    hmb.setTemperatureUnit("K").setPressureUnit("bara").setFlowUnit("kg/hr");

    String json = hmb.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
  }
}
