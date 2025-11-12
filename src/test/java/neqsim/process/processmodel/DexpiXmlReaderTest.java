package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.ProcessEquipmentInterface;


/**
 * 
 * Tests for {@link DexpiXmlReader}.
 * 
 */

public class DexpiXmlReaderTest extends NeqSimTest {
  @Test
  public void testRead() throws IOException, DexpiXmlReaderException {
    // Create a simple DEXPI XML file for testing
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"P-101\">"
        + "      <GenericAttributes>"
        + "        <GenericAttribute Name=\"TagNameAssignmentClass\" Value=\"P-101\" />"
        + "      </GenericAttributes>" + "    </PlateHeatExchanger>" + "  </Equipment>"
        + "</PlantModel>";

    // Create a temporary file to write the XML to
    File tempFile = File.createTempFile("test", ".xml");
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(xml);
    }

    // Read the XML file
    ProcessSystem processSystem = DexpiXmlReader.read(tempFile);

    // Verify that the process system is not null
    assertNotNull(processSystem);

    // Verify that the process system has one unit
    assertEquals(1, processSystem.getAllUnitNames().size());

    // Verify that the unit is a PlateHeatExchanger
    ProcessEquipmentInterface unit = processSystem.getUnit("P-101");
    assertNotNull(unit);
    assertEquals("P-101", unit.getName());
    assertEquals("PlateHeatExchanger", ((DexpiProcessUnit) unit).getDexpiClass());
  }

  @Test
  public void testReadInvalidXml() throws IOException {
    // Create an invalid DEXPI XML file for testing
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"P-101\">"
        + "      <GenericAttributes>"
        + "        <GenericAttribute Name=\"TagNameAssignmentClass\" Value=\"P-101\" />"
        + "      </GenericAttributes>" + "    </PlateHeatExchanger>" + "  </Equipment>"
        + "</PlantModel2>";

    // Create a temporary file to write the XML to
    File tempFile = File.createTempFile("test", ".xml");
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(xml);
    }

    // Verify that a DexpiXmlReaderException is thrown
    assertThrows(DexpiXmlReaderException.class, () -> DexpiXmlReader.read(tempFile));
  }
}
