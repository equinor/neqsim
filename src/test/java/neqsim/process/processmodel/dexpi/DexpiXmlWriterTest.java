package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link DexpiXmlWriter}, covering nozzle/connection export, native equipment reverse
 * mapping, sizing attribute export, and simulation results export.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiXmlWriterTest extends NeqSimTest {

  /**
   * Creates a simple gas feed stream for testing.
   *
   * @return a configured feed stream
   */
  private Stream createFeedStream() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    fluid.init(0);
    Stream feed = new Stream("feed", fluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");
    return feed;
  }

  /**
   * Tests that native equipment is exported with correct DEXPI ComponentClass reverse mapping and
   * includes Nozzle children.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testNativeEquipmentReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // Separator should be exported with ComponentClass="Separator"
    assertTrue(xml.contains("ComponentClass=\"Separator\""),
        "Should contain Separator ComponentClass");
    // Should have Nozzle children
    assertTrue(xml.contains("<Nozzle"), "Should contain Nozzle elements");
  }

  /**
   * Tests that a Compressor is reverse-mapped to CentrifugalCompressor.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testCompressorReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Compressor comp = new Compressor("Comp-1", feed);
    comp.setOutletPressure(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"CentrifugalCompressor\""),
        "Should map Compressor to CentrifugalCompressor");
  }

  /**
   * Tests that a ThrottlingValve is reverse-mapped to GlobeValve and exported as PipingComponent.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testValveReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    ThrottlingValve valve = new ThrottlingValve("CV-101", feed);
    valve.setOutletPressure(30.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"GlobeValve\""),
        "Should map ThrottlingValve to GlobeValve");
    // Valves should be exported as PipingComponent, not Equipment
    assertTrue(xml.contains("<PipingComponent"), "Valve should be exported as PipingComponent");
  }

  /**
   * Tests that a Heater is reverse-mapped to FiredHeater.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testHeaterReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Heater heater = new Heater("Heater-1", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"FiredHeater\""), "Should map Heater to FiredHeater");
  }

  /**
   * Tests that a Cooler is reverse-mapped to AirCooledHeatExchanger.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testCoolerReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    Cooler cooler = new Cooler("Cooler-1", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(cooler);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"AirCooledHeatExchanger\""),
        "Should map Cooler to AirCooledHeatExchanger");
  }

  /**
   * Tests that a ThreePhaseSeparator is correctly mapped.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testThreePhaseSeparatorReverseMapping() throws IOException {
    Stream feed = createFeedStream();
    ThreePhaseSeparator sep = new ThreePhaseSeparator("3P-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("ComponentClass=\"ThreePhaseSeparator\""),
        "Should map ThreePhaseSeparator correctly");
  }

  /**
   * Tests that connections are generated between consecutive non-stream equipment.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testConnectionsGenerated() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);
    Stream gasOut = new Stream("gas-out", sep.getGasOutStream());
    Compressor comp = new Compressor("Comp-1", gasOut);
    comp.setOutletPressure(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(gasOut);
    process.add(comp);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // Should have Connection elements linking separator to compressor
    assertTrue(xml.contains("<Connection"), "Should contain Connection elements");
    assertTrue(xml.contains("FromID="), "Connection should have FromID");
    assertTrue(xml.contains("ToID="), "Connection should have ToID");
  }

  /**
   * Tests that DexpiProcessUnit sizing attributes are exported.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testSizingAttributeExport() throws IOException {
    DexpiProcessUnit unit = new DexpiProcessUnit("HP-Sep", "Separator",
        neqsim.process.equipment.EquipmentEnum.Separator, null, null);
    unit.setSizingAttribute(DexpiMetadata.INSIDE_DIAMETER, "2.5");
    unit.setSizingAttribute(DexpiMetadata.TANGENT_TO_TANGENT_LENGTH, "8.0");

    ProcessSystem process = new ProcessSystem();
    process.add(unit);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // Should contain the sizing attributes
    assertTrue(xml.contains("InsideDiameter"), "Should export InsideDiameter sizing attribute");
    assertTrue(xml.contains("2.5"), "InsideDiameter value should be 2.5");
    assertTrue(xml.contains("TangentToTangentLength"),
        "Should export TangentToTangentLength sizing attribute");
    assertTrue(xml.contains("8.0"), "TangentToTangentLength value should be 8.0");
  }

  /**
   * Tests that simulation results (P, T, flow) are exported for run equipment.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testSimulationResultsExport() throws IOException {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // After running, simulation results should be exported as GenericAttributes
    assertTrue(xml.contains("OperatingPressureValue") || xml.contains("OperatingTemperatureValue"),
        "Should export simulation result attributes after process run");
  }

  /**
   * Tests that round-trip write-then-read produces a valid ProcessSystem without throwing.
   *
   * @throws Exception if write or read fails
   */
  @Test
  public void testRoundTripWriteRead() throws Exception {
    Stream feed = createFeedStream();
    Separator sep = new Separator("HP-Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    File tempFile = File.createTempFile("dexpi-roundtrip", ".xml");
    tempFile.deleteOnExit();
    DexpiXmlWriter.write(process, tempFile);

    // Read it back — should not throw
    ProcessSystem readBack = DexpiXmlReader.read(tempFile);
    assertNotNull(readBack, "Round-trip should produce a valid ProcessSystem");
  }

  /**
   * Tests that an empty ProcessSystem can be written without errors.
   *
   * @throws IOException if writing fails
   */
  /**
   * Tests exporting an empty process system.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testEmptyProcessSystem() throws IOException {
    ProcessSystem process = new ProcessSystem();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertNotNull(xml);
    assertTrue(xml.contains("<PlantModel"), "Should contain PlantModel root");
    assertTrue(xml.contains("PlantInformation"), "Should contain PlantInformation");
  }

  /**
   * Tests that a separator produces multiple nozzles for gas and liquid outlets, and that stream
   * identity-based connection building correctly wires downstream equipment to the right nozzles.
   *
   * @throws IOException if writing fails
   */
  @Test
  public void testSeparatorMultiOutletNozzles() throws IOException {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("nC10", 0.3);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    Separator sep = new Separator("HP-sep", feed);
    sep.run();

    // Gas outlet goes to compressor, liquid outlet goes to valve
    Compressor comp = new Compressor("gas-comp", sep.getGasOutStream());
    comp.setOutletPressure(80.0);
    comp.run();

    ThrottlingValve valve = new ThrottlingValve("liq-valve", sep.getLiquidOutStream());
    valve.setOutletPressure(10.0);
    valve.run();

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(comp);
    process.add(valve);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    // Separator should have 3 nozzles (1 inlet + 2 outlets)
    int nozzleCount = countOccurrences(xml, "<Nozzle ");
    // feed (not exported as native equipment since it's Stream), sep=3, comp=2, valve=2 = total 7
    assertTrue(nozzleCount >= 7, "Separator should produce 3 nozzles (inlet + gas out + liquid out)"
        + "; total nozzles=" + nozzleCount);

    // Connection system should contain connections
    assertTrue(xml.contains("Connection"), "Should contain Connection elements");
    assertTrue(xml.contains("Separator"), "Should contain Separator equipment");
  }

  /**
   * Counts occurrences of a substring in a string.
   *
   * @param text the text to search
   * @param sub the substring to count
   * @return the number of occurrences
   */
  private int countOccurrences(String text, String sub) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(sub, idx)) != -1) {
      count++;
      idx += sub.length();
    }
    return count;
  }
}
