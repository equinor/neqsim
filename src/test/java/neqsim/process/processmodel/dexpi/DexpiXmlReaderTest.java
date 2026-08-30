package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link DexpiXmlReader}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiXmlReaderTest extends NeqSimTest {
  @Test
  public void testRead() throws IOException, DexpiXmlReaderException {
    // Create a simple DEXPI XML file for testing
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"P-101\">" + "      <GenericAttributes>"
        + "        <GenericAttribute Name=\"TagNameAssignmentClass\" Value=\"P-101\" />" + "      </GenericAttributes>"
        + "    </PlateHeatExchanger>" + "  </Equipment>" + "</PlantModel>";

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
        + "    <PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"P-101\">" + "      <GenericAttributes>"
        + "        <GenericAttribute Name=\"TagNameAssignmentClass\" Value=\"P-101\" />" + "      </GenericAttributes>"
        + "    </PlateHeatExchanger>" + "  </Equipment>" + "</PlantModel2>";

    // Create a temporary file to write the XML to
    File tempFile = File.createTempFile("test", ".xml");
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(xml);
    }

    // Verify that a DexpiXmlReaderException is thrown
    assertThrows(DexpiXmlReaderException.class, () -> DexpiXmlReader.read(tempFile));
  }

  @Test
  public void testReadInvalidXmlDoesNotLogToStderr() throws IOException {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"P-101\">" + "      <GenericAttributes>"
        + "        <GenericAttribute Name=\"TagNameAssignmentClass\" Value=\"P-101\" />" + "      </GenericAttributes>"
        + "    </PlateHeatExchanger>" + "  </Equipment>" + "</PlantModel2>";

    File tempFile = File.createTempFile("test", ".xml");
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(xml);
    }

    PrintStream originalErr = System.err;
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errContent));
    try {
      assertThrows(DexpiXmlReaderException.class, () -> DexpiXmlReader.read(tempFile));
    } finally {
      System.setErr(originalErr);
    }

    assertEquals("", errContent.toString().trim());
  }

  @Test
  public void testRoundTripProfileValidatesSuccessfully() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    DexpiStream stream = new DexpiStream("Line-001", fluid, "PipingSegment", "L-001", "NG");
    stream.setFlowRate(1.0, "MSm3/day");

    DexpiProcessUnit unit = new DexpiProcessUnit("V-100", "HorizontalVessel", EquipmentEnum.Separator, "L-001", null);

    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(unit);

    DexpiRoundTripProfile.ValidationResult result = DexpiRoundTripProfile.minimalRunnableProfile().validate(process);
    assertTrue(result.isSuccessful(), "Violations: " + result.getViolations());
  }

  @Test
  public void testRoundTripProfileReportsViolationsForEmptyProcess() {
    ProcessSystem process = new ProcessSystem();

    DexpiRoundTripProfile.ValidationResult result = DexpiRoundTripProfile.minimalRunnableProfile().validate(process);
    assertFalse(result.isSuccessful());
    assertTrue(result.getViolations().size() >= 2, "Expected at least 2 violations (no stream, no equipment)");
  }

  @Test
  public void testReadWithDiagnosticsReportsUnsupportedObjectsDeterministically() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel><Equipment>"
        + "<PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"E-1\">"
        + "<GenericAttributes><GenericAttribute Name=\"TagNameAssignmentClass\" Value=\"E-1\"/>"
        + "</GenericAttributes></PlateHeatExchanger>"
        + "<ProjectSpecificObject ComponentClass=\"OwnerCustomPackage\" ID=\"X-1\"/>"
        + "<UnclassifiedObject ID=\"X-2\"/>" + "</Equipment></PlantModel>";

    DexpiXmlReader.ImportResult first = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    DexpiXmlReader.ImportResult second = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(1, first.getProcessSystem().getAllUnitNames().size());
    assertNotNull(first.getProcessSystem().getUnit("E-1"));
    assertTrue(first.hasLosses());
    assertFalse(first.hasErrors());
    assertEquals(2, first.getDiagnostics().size());
    assertEquals("DEXPI_IMPORT_COMPONENT_UNSUPPORTED", first.getDiagnostics().get(0).getCode());
    assertEquals("X-1", first.getDiagnostics().get(0).getElementId());
    assertEquals("OwnerCustomPackage", first.getDiagnostics().get(0).getComponentClass());
    assertEquals("ProjectSpecificObject", first.getDiagnostics().get(0).getElementName());
    assertEquals(DexpiXmlReader.ImportDiagnosticSeverity.WARNING, first.getDiagnostics().get(0).getSeverity());
    assertEquals("DEXPI_IMPORT_COMPONENT_CLASS_MISSING", first.getDiagnostics().get(1).getCode());
    assertEquals("X-2", first.getDiagnostics().get(1).getElementId());
    assertEquals(first.toJson(), second.toJson());
    assertTrue(first.toJson().contains("neqsim_dexpi_proteus_import.v1"));

    ProcessSystem legacy = DexpiXmlReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    assertEquals(first.getProcessSystem().getAllUnitNames(), legacy.getAllUnitNames());
  }
}
