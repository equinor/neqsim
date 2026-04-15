package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link DexpiSimulationBuilder}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiSimulationBuilderTest extends NeqSimTest {

  @TempDir
  File tempDir;

  /**
   * Creates a simple two-equipment DEXPI XML file for testing.
   *
   * @return the temp file containing the XML
   * @throws IOException if file creation fails
   */
  private File writeSimpleDexpiXml() throws IOException {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<PlantModel>\n"
        + "  <Equipment ID=\"E-1\">\n"
        + "    <Separator ComponentClass=\"Separator\" ID=\"Sep-1\" TagName=\"V-100\">\n"
        + "      <GenericAttributes Set=\"DesignData\">\n"
        + "        <GenericAttribute Name=\"InsideDiameter\" Value=\"2.0\"/>\n"
        + "        <GenericAttribute Name=\"TangentToTangentLength\" Value=\"6.0\"/>\n"
        + "        <GenericAttribute Name=\"Orientation\" Value=\"Horizontal\"/>\n"
        + "      </GenericAttributes>\n" + "      <Nozzle ID=\"N-1\" TagName=\"VN-101\"/>\n"
        + "      <Nozzle ID=\"N-2\" TagName=\"VN-102\"/>\n" + "    </Separator>\n"
        + "  </Equipment>\n" + "  <Equipment ID=\"E-2\">\n"
        + "    <Compressor ComponentClass=\"CentrifugalCompressor\" ID=\"Comp-1\" "
        + "TagName=\"K-200\">\n" + "      <Nozzle ID=\"N-3\" TagName=\"KN-201\"/>\n"
        + "      <Nozzle ID=\"N-4\" TagName=\"KN-202\"/>\n" + "    </Compressor>\n"
        + "  </Equipment>\n" + "  <PipingNetworkSystem>\n"
        + "    <PipingNetworkSegment ID=\"PNS-1\">\n"
        + "      <Connection FromID=\"N-2\" ToID=\"N-3\"/>\n" + "    </PipingNetworkSegment>\n"
        + "  </PipingNetworkSystem>\n" + "</PlantModel>";
    File file = new File(tempDir, "simple_plant.xml");
    FileWriter writer = new FileWriter(file);
    try {
      writer.write(xml);
    } finally {
      writer.close();
    }
    return file;
  }

  /**
   * Creates a minimal single-equipment DEXPI XML.
   *
   * @return the temp file
   * @throws IOException if file creation fails
   */
  private File writeSingleEquipmentXml() throws IOException {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<PlantModel>\n"
        + "  <Equipment ID=\"E-1\">\n"
        + "    <Separator ComponentClass=\"Separator\" ID=\"Sep-1\" TagName=\"V-100\">\n"
        + "      <Nozzle ID=\"N-1\"/>\n" + "      <Nozzle ID=\"N-2\"/>\n" + "    </Separator>\n"
        + "  </Equipment>\n" + "</PlantModel>";
    File file = new File(tempDir, "single_equip.xml");
    FileWriter writer = new FileWriter(file);
    try {
      writer.write(xml);
    } finally {
      writer.close();
    }
    return file;
  }

  /**
   * Tests builder with default fluid and a two-equipment topology.
   *
   * @throws Exception if build fails
   */
  @Test
  public void testBuildWithDefaultFluid() throws Exception {
    File xmlFile = writeSimpleDexpiXml();

    ProcessSystem process = new DexpiSimulationBuilder(xmlFile).setFeedPressure(60.0, "bara")
        .setFeedTemperature(25.0, "C").setFeedFlowRate(0.5, "MSm3/day").build();

    assertNotNull(process);
    // Should contain at least feed stream + 2 equipment
    assertTrue(process.getUnitOperations().size() >= 2);
  }

  /**
   * Tests builder with a user-provided fluid template.
   *
   * @throws Exception if build fails
   */
  @Test
  public void testBuildWithCustomFluid() throws Exception {
    File xmlFile = writeSimpleDexpiXml();

    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ProcessSystem process =
        new DexpiSimulationBuilder(xmlFile).setFluidTemplate(fluid).setFeedPressure(50.0, "bara")
            .setFeedTemperature(30.0, "C").setFeedFlowRate(1.0, "MSm3/day").build();

    assertNotNull(process);
    assertTrue(process.getUnitOperations().size() >= 2);
  }

  /**
   * Tests builder with a single-equipment file.
   *
   * @throws Exception if build fails
   */
  @Test
  public void testBuildSingleEquipment() throws Exception {
    File xmlFile = writeSingleEquipmentXml();

    ProcessSystem process = new DexpiSimulationBuilder(xmlFile).build();

    assertNotNull(process);
    // At least the feed stream + 1 separator
    assertTrue(process.getUnitOperations().size() >= 2);
  }

  /**
   * Tests that non-existent file throws IOException.
   */
  @Test
  public void testNonExistentFileThrows() {
    File missingFile = new File(tempDir, "does_not_exist.xml");
    DexpiSimulationBuilder builder = new DexpiSimulationBuilder(missingFile);
    assertThrows(IOException.class, builder::build);
  }

  /**
   * Tests that null file throws NullPointerException in constructor.
   */
  @Test
  public void testNullFileThrowsNpe() {
    assertThrows(NullPointerException.class, () -> new DexpiSimulationBuilder(null));
  }

  /**
   * Tests that invalid XML throws DexpiXmlReaderException.
   *
   * @throws IOException if temp file creation fails
   */
  @Test
  public void testInvalidXmlThrows() throws IOException {
    File file = new File(tempDir, "bad.xml");
    FileWriter writer = new FileWriter(file);
    try {
      writer.write("this is not XML at all");
    } finally {
      writer.close();
    }
    DexpiSimulationBuilder builder = new DexpiSimulationBuilder(file);
    assertThrows(DexpiXmlReaderException.class, builder::build);
  }

  /**
   * Tests builder with fluent API chaining.
   *
   * @throws Exception if build fails
   */
  @Test
  public void testFluentApiChaining() throws Exception {
    File xmlFile = writeSingleEquipmentXml();

    DexpiSimulationBuilder builder = new DexpiSimulationBuilder(xmlFile)
        .setFeedPressure(40.0, "bara").setFeedTemperature(20.0, "C")
        .setFeedFlowRate(2.0, "MSm3/day").setAutoInstrument(false);

    assertNotNull(builder);
    ProcessSystem process = builder.build();
    assertNotNull(process);
  }
}
