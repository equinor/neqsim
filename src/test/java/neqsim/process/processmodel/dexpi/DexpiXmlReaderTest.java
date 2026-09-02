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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

  @Test
  public void testReadWithDiagnosticsReportsPipingProvenanceAndMetadataGaps() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel><PipingNetworkSystem>"
        + "<PipingNetworkSegment ComponentClass=\"PipingNetworkSegment\" ID=\"S-1\">" + "<GenericAttributes>"
        + "<GenericAttribute Name=\"SegmentNumberAssignmentClass\" Value=\"S-1\"/>"
        + "<GenericAttribute Name=\"LineNumberAssignmentClass\" Value=\"100-FG-001\"/>"
        + "<GenericAttribute Name=\"FluidCodeAssignmentClass\" Value=\"FG\"/>"
        + "<GenericAttribute Name=\"NominalDiameterRepresentationAssignmentClass\" Value=\"DN 100\"/>"
        + "<GenericAttribute Name=\"PipingClassCodeAssignmentClass\" Value=\"A1\"/>"
        + "<GenericAttribute Name=\"InsulationTypeAssignmentClass\" Value=\"H\"/>"
        + "<GenericAttribute Name=\"OperatingPressureValue\" Value=\"not-a-number\"/>"
        + "<GenericAttribute Name=\"OperatingPressureUnit\" Value=\"bara\"/>"
        + "<GenericAttribute Name=\"OperatingTemperatureValue\" Value=\"25.0\"/>"
        + "</GenericAttributes></PipingNetworkSegment>" + "<PipingNetworkSegment/>"
        + "</PipingNetworkSystem></PlantModel>";

    DexpiXmlReader.ImportResult result = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(2, result.getProcessSystem().getAllUnitNames().size());
    DexpiStream sourceBacked = (DexpiStream) result.getProcessSystem().getUnit("100-FG-001-S-1");
    assertNotNull(sourceBacked);
    assertEquals("DN 100", sourceBacked.getNominalDiameterRepresentation());
    assertEquals("A1", sourceBacked.getPipingClassCode());
    assertEquals("H", sourceBacked.getInsulationType());
    assertEquals(50.0, sourceBacked.getPressure("bara"), 1.0e-12);
    assertEquals(25.0, sourceBacked.getTemperature("C"), 1.0e-12);

    assertEquals(14, result.getDiagnostics().size());
    assertEquals("DEXPI_IMPORT_DEFAULT_TEMPLATE_USED", result.getDiagnostics().get(0).getCode());
    assertEquals("DEXPI_IMPORT_PRESSURE_INVALID", result.getDiagnostics().get(1).getCode());
    assertEquals("DEXPI_IMPORT_TEMPERATURE_UNIT_DEFAULTED", result.getDiagnostics().get(2).getCode());
    assertEquals("DEXPI_IMPORT_FLOW_FROM_TEMPLATE", result.getDiagnostics().get(3).getCode());
    assertEquals(DexpiXmlReader.ImportDiagnosticSeverity.INFO, result.getDiagnostics().get(3).getSeverity());
    assertEquals("DEXPI_IMPORT_SEGMENT_IDENTITY_MISSING", result.getDiagnostics().get(4).getCode());
    assertEquals("DEXPI_IMPORT_SEGMENT_CLASS_MISSING", result.getDiagnostics().get(5).getCode());
    assertEquals("DEXPI_IMPORT_LINE_NUMBER_MISSING", result.getDiagnostics().get(6).getCode());
    assertEquals("DEXPI_IMPORT_SERVICE_CODE_MISSING", result.getDiagnostics().get(7).getCode());
    assertEquals("DEXPI_IMPORT_NOMINAL_SIZE_MISSING", result.getDiagnostics().get(8).getCode());
    assertEquals("DEXPI_IMPORT_PIPING_CLASS_MISSING", result.getDiagnostics().get(9).getCode());
    assertEquals("DEXPI_IMPORT_INSULATION_UNSPECIFIED", result.getDiagnostics().get(10).getCode());
    assertEquals("DEXPI_IMPORT_PRESSURE_FROM_TEMPLATE", result.getDiagnostics().get(11).getCode());
    assertEquals("DEXPI_IMPORT_TEMPERATURE_FROM_TEMPLATE", result.getDiagnostics().get(12).getCode());
    assertEquals("DEXPI_IMPORT_FLOW_FROM_TEMPLATE", result.getDiagnostics().get(13).getCode());
    assertTrue(result.hasLosses());
    assertFalse(result.hasErrors());
  }

  @Test
  public void testReadWithDiagnosticsIncludesValidInstrumentationInventory() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "<Nozzle ID=\"N-SENSE\"/><Nozzle ID=\"N-ACT\"/><FinalControlElement ID=\"V-100\"/>"
        + "<ProcessInstrumentationFunction ComponentClass=\"ProcessInstrumentationFunction\" ID=\"PIF-PT-100\">"
        + instrumentAttributes("PT-100", "P", "T", "100")
        + "<InformationFlow ComponentClass=\"MeasuringLineFunction\" ID=\"MLF-100\">"
        + "<Association Type=\"has logical start\" ItemID=\"PSGF-100\"/>"
        + "<Association Type=\"has logical end\" ItemID=\"PIF-PT-100\"/>"
        + "<Association Type=\"is attached to\" ItemID=\"N-SENSE\"/>" + "</InformationFlow>"
        + "<ProcessSignalGeneratingFunction ComponentClass=\"ProcessSignalGeneratingFunction\" ID=\"PSGF-100\"/>"
        + "</ProcessInstrumentationFunction>"
        + "<ProcessInstrumentationFunction ComponentClass=\"ProcessControlFunction\" ID=\"PIF-PC-100\">"
        + instrumentAttributes("PC-100", "P", "C", "100")
        + signalFlow("SIG-MEASURED", "PIF-PT-100", "PIF-PC-100", "ElectricalSignalConveying")
        + "<ActuatingFunction ComponentClass=\"ActuatingFunction\" ID=\"AF-100\">"
        + "<GenericAttributes><GenericAttribute Name=\"ActuatingFunctionNumberAssignmentClass\" Value=\"PC-100\"/>"
        + "<GenericAttribute Name=\"FinalControlElementID\" Value=\"V-100\"/>"
        + "</GenericAttributes><Association Type=\"is located in\" ItemID=\"N-ACT\"/>" + "</ActuatingFunction>"
        + signalFlow("SIG-ACTUATE", "PIF-PC-100", "AF-100", "PneumaticSignalConveying")
        + "</ProcessInstrumentationFunction>"
        + "<InstrumentationLoopFunction ComponentClass=\"InstrumentationLoopFunction\" ID=\"LOOP-100\">"
        + "<GenericAttributes><GenericAttribute Name=\"InstrumentationLoopFunctionNumberAssignmentClass\" Value=\"100\"/>"
        + "</GenericAttributes><Association Type=\"is a collection including\" ItemID=\"PIF-PT-100\"/>"
        + "<Association Type=\"is a collection including\" ItemID=\"PIF-PC-100\"/>"
        + "</InstrumentationLoopFunction></PlantModel>";

    DexpiXmlReader.ImportResult first = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    DexpiXmlReader.ImportResult second = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(2, first.getInstruments().size());
    assertEquals("PT-100", first.getInstruments().get(0).getTagName());
    assertEquals("100", first.getInstruments().get(0).getLoopNumber());
    assertEquals("PC-100", first.getInstruments().get(1).getTagName());
    assertEquals("PC-100", first.getInstruments().get(1).getActuatingTag());
    assertTrue(first.getDiagnostics().isEmpty());
    assertFalse(first.hasLosses());
    assertTrue(first.toJson().contains("\"instrumentCount\": 2"));
    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  public void testReadWithDiagnosticsReportsBrokenInstrumentationTopology() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "<ProcessInstrumentationFunction ComponentClass=\"ProcessInstrumentationFunction\">"
        + "<GenericAttributes><GenericAttribute Name=\"MeasurementAttachmentStatus\" Value=\"MISSING_SOURCE_DATA\"/>"
        + "</GenericAttributes><InformationFlow ComponentClass=\"MeasuringLineFunction\" ID=\"MLF-BROKEN\">"
        + "<Association Type=\"has logical end\" ItemID=\"UNKNOWN-INSTRUMENT\"/>" + "</InformationFlow>"
        + "</ProcessInstrumentationFunction>"
        + "<InstrumentationLoopFunction ComponentClass=\"InstrumentationLoopFunction\">"
        + "<Association Type=\"is a collection including\" ItemID=\"UNKNOWN-MEMBER\"/>"
        + "</InstrumentationLoopFunction>" + "<InformationFlow ComponentClass=\"SignalLineFunction\" ID=\"SIG-BROKEN\">"
        + "<Association Type=\"has logical start\" ItemID=\"UNKNOWN-SOURCE\"/>" + "</InformationFlow>"
        + "<ActuatingFunction ComponentClass=\"ActuatingFunction\" ID=\"AF-BROKEN\">"
        + "<GenericAttributes><GenericAttribute Name=\"FinalControlElementID\" Value=\"UNKNOWN-FINAL\"/>"
        + "</GenericAttributes></ActuatingFunction>" + "</PlantModel>";

    DexpiXmlReader.ImportResult first = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    DexpiXmlReader.ImportResult second = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(1, first.getInstruments().size());
    assertDiagnostic(first, "DEXPI_IMPORT_INSTRUMENT_ID_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_INSTRUMENT_FUNCTION_METADATA_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_INSTRUMENT_NUMBER_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_INSTRUMENT_TAG_SYNTHESIZED");
    assertDiagnostic(first, "DEXPI_IMPORT_SENSING_ATTACHMENT_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_MEASURING_SOURCE_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_MEASURING_TARGET_UNRESOLVED");
    assertDiagnostic(first, "DEXPI_IMPORT_MEASURING_ATTACHMENT_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_LOOP_ID_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_LOOP_NUMBER_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_LOOP_MEMBER_UNRESOLVED");
    assertDiagnostic(first, "DEXPI_IMPORT_SIGNAL_SOURCE_UNRESOLVED");
    assertDiagnostic(first, "DEXPI_IMPORT_SIGNAL_TARGET_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_SIGNAL_MEDIUM_MISSING");
    assertDiagnostic(first, "DEXPI_IMPORT_FINAL_ELEMENT_UNRESOLVED");
    assertDiagnostic(first, "DEXPI_IMPORT_ACTUATION_LOCATION_MISSING");
    assertTrue(first.hasLosses());
    assertFalse(first.hasErrors());
    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  public void testReadWithDiagnosticsPreservesParallelMaterialConnections() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "<Equipment ID=\"E-OUT\"><Nozzle ID=\"N-OUT\"/></Equipment>"
        + "<Equipment ID=\"E-IN\"><Nozzle ID=\"N-IN\"/></Equipment>"
        + "<PipingNetworkSegment ID=\"S-1\" ComponentClass=\"PipingNetworkSegment\">"
        + "<Connection FromID=\"N-OUT\" ToID=\"N-IN\"/></PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-2\" ComponentClass=\"PipingNetworkSegment\">"
        + "<Connection FromID=\"N-OUT\" ToID=\"N-IN\"/></PipingNetworkSegment>" + "</PlantModel>";

    DexpiXmlReader.ImportResult first = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    DexpiXmlReader.ImportResult second = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(2, first.getConnections().size());
    DexpiConnectionInfo firstConnection = first.getConnections().get(0);
    assertEquals("S-1/connection-1", firstConnection.getId());
    assertFalse(firstConnection.hasSourceId());
    assertEquals("S-1", firstConnection.getSegmentId());
    assertEquals("N-OUT", firstConnection.getFromId());
    assertEquals("N-IN", firstConnection.getToId());
    assertEquals("Nozzle", firstConnection.getFromElementName());
    assertEquals("Nozzle", firstConnection.getToElementName());
    assertEquals("E-OUT", firstConnection.getFromOwnerId());
    assertEquals("E-IN", firstConnection.getToOwnerId());
    assertEquals("Equipment", firstConnection.getFromOwnerElementName());
    assertEquals("Equipment", firstConnection.getToOwnerElementName());
    assertTrue(firstConnection.isResolved());
    assertTrue(firstConnection.isOwnershipResolved());
    assertEquals("S-2/connection-1", first.getConnections().get(1).getId());
    assertEquals(2, countDiagnostics(first, "DEXPI_IMPORT_CONNECTION_ID_SYNTHESIZED"));
    assertTrue(first.toJson().contains("\"connectionCount\": 2"));
    assertEquals(first.toJson(), second.toJson());
    assertThrows(UnsupportedOperationException.class, () -> first.getConnections().clear());
  }

  @Test
  public void testReadWithDiagnosticsReportsMalformedMaterialConnections() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "<Nozzle ID=\"N-1\"/>"
        + "<PipingNetworkSegment ID=\"S-BROKEN\" ComponentClass=\"PipingNetworkSegment\">"
        + "<Connection ID=\"C-1\" ToID=\"UNKNOWN\"/></PipingNetworkSegment>"
        + "<Connection ID=\"C-1\" FromID=\"N-1\" ToID=\"N-1\"/>" + "</PlantModel>";

    DexpiXmlReader.ImportResult result = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(2, result.getConnections().size());
    assertEquals("C-1", result.getConnections().get(0).getId());
    assertFalse(result.getConnections().get(0).isResolved());
    assertEquals("C-1#2", result.getConnections().get(1).getId());
    assertTrue(result.getConnections().get(1).isSelfReference());
    assertDiagnostic(result, "DEXPI_IMPORT_CONNECTION_SOURCE_MISSING");
    assertDiagnostic(result, "DEXPI_IMPORT_CONNECTION_TARGET_UNRESOLVED");
    assertDiagnostic(result, "DEXPI_IMPORT_CONNECTION_ID_DUPLICATE");
    assertDiagnostic(result, "DEXPI_IMPORT_CONNECTION_SEGMENT_MISSING");
    assertDiagnostic(result, "DEXPI_IMPORT_CONNECTION_SELF_REFERENCE");
    assertDiagnostic(result, "DEXPI_IMPORT_CONNECTION_SOURCE_OWNER_MISSING");
    assertDiagnostic(result, "DEXPI_IMPORT_CONNECTION_TARGET_OWNER_MISSING");
  }

  @Test
  public void testReadWithDiagnosticsReportsMissingOwnerIdentity() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "<Equipment><Nozzle ID=\"N-A\"/></Equipment>"
        + "<PipingComponent ID=\"PC-1\"><Nozzle ID=\"N-B\"/></PipingComponent>"
        + "<PipingNetworkSegment ID=\"S-OWNER\" ComponentClass=\"PipingNetworkSegment\">"
        + "<Connection ID=\"C-OWNER\" FromID=\"N-A\" ToID=\"N-B\"/></PipingNetworkSegment>" + "</PlantModel>";

    DexpiXmlReader.ImportResult result = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    DexpiConnectionInfo connection = result.getConnections().get(0);

    assertEquals("", connection.getFromOwnerId());
    assertEquals("Equipment", connection.getFromOwnerElementName());
    assertEquals("PC-1", connection.getToOwnerId());
    assertEquals("PipingComponent", connection.getToOwnerElementName());
    assertFalse(connection.isOwnershipResolved());
    assertDiagnostic(result, "DEXPI_IMPORT_CONNECTION_SOURCE_OWNER_ID_MISSING");
    assertTrue(result.toJson().contains("\"fromOwnerElementName\": \"Equipment\""));

    DexpiConnectionInfo legacy = new DexpiConnectionInfo("C", "C", "S", "A", "B", "Nozzle", "Nozzle", true, true);
    assertEquals("", legacy.getFromOwnerId());
    assertEquals("", legacy.getToOwnerId());
  }

  @Test
  public void testReadWithDiagnosticsSummarizesConnectionEndpointIncidence() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "<Equipment ID=\"E-A\"><Nozzle ID=\"N-A\"/></Equipment>"
        + "<Equipment ID=\"E-B\"><Nozzle ID=\"N-B\"/></Equipment>"
        + "<PipingComponent ID=\"PC-J\"><Nozzle ID=\"N-J\"/></PipingComponent>"
        + "<Equipment ID=\"E-C\"><Nozzle ID=\"N-C\"/></Equipment>"
        + "<PipingNetworkSegment ID=\"S-1\"><Connection ID=\"C-1\" FromID=\"N-A\" ToID=\"N-J\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-2\"><Connection ID=\"C-2\" FromID=\"N-B\" ToID=\"N-J\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-3\"><Connection ID=\"C-3\" FromID=\"N-J\" ToID=\"N-C\"/>"
        + "</PipingNetworkSegment>" + "<PipingNetworkSegment ID=\"S-4\"><Connection ID=\"C-4\" FromID=\"UNKNOWN\"/>"
        + "</PipingNetworkSegment>" + "</PlantModel>";

    DexpiXmlReader.ImportResult first = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    DexpiXmlReader.ImportResult second = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(5, first.getConnectionEndpoints().size());
    DexpiConnectionEndpointInfo junction = first.getConnectionEndpoints().get(1);
    assertEquals("N-J", junction.getEndpointId());
    assertEquals("Nozzle", junction.getElementName());
    assertEquals("PC-J", junction.getOwnerId());
    assertTrue(junction.isResolved());
    assertEquals(2, junction.getIncomingConnectionCount());
    assertEquals(1, junction.getOutgoingConnectionCount());
    assertEquals(3, junction.getConnectionCount());
    assertTrue(junction.isReferencedMultipleTimes());
    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.MERGE, junction.getIncidenceRole());
    assertTrue(junction.isPotentialMultiConnectionNode());
    assertEquals(Arrays.asList("C-1", "C-2"), junction.getIncomingConnectionIds());
    assertEquals(Collections.singletonList("C-3"), junction.getOutgoingConnectionIds());

    DexpiConnectionEndpointInfo unresolved = first.getConnectionEndpoints().get(4);
    assertEquals("UNKNOWN", unresolved.getEndpointId());
    assertFalse(unresolved.isResolved());
    assertEquals(Collections.singletonList("C-4"), unresolved.getOutgoingConnectionIds());
    assertEquals(0, unresolved.getIncomingConnectionCount());
    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.SOURCE, unresolved.getIncidenceRole());
    assertFalse(unresolved.isPotentialMultiConnectionNode());
    assertThrows(UnsupportedOperationException.class, () -> unresolved.getOutgoingConnectionIds().clear());
    assertThrows(UnsupportedOperationException.class, () -> first.getConnectionEndpoints().clear());
    assertTrue(first.toJson().contains("\"connectionEndpointCount\": 5"));
    assertTrue(first.toJson().contains("\"incidenceRole\": \"MERGE\""));
    assertTrue(first.toJson().contains("\"potentialMultiConnectionNode\": true"));
    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  public void testConnectionEndpointIncidenceRoleClassification() {
    DexpiConnectionEndpointInfo source = new DexpiConnectionEndpointInfo("SOURCE", "Nozzle", "", "", true,
        Collections.<String>emptyList(), Collections.singletonList("C-1"));
    DexpiConnectionEndpointInfo sink = new DexpiConnectionEndpointInfo("SINK", "Nozzle", "", "", true,
        Collections.singletonList("C-1"), Collections.<String>emptyList());
    DexpiConnectionEndpointInfo passThrough = new DexpiConnectionEndpointInfo("PASS", "Nozzle", "", "", true,
        Collections.singletonList("C-1"), Collections.singletonList("C-2"));
    DexpiConnectionEndpointInfo split = new DexpiConnectionEndpointInfo("SPLIT", "Nozzle", "", "", true,
        Collections.singletonList("C-1"), Arrays.asList("C-2", "C-3"));
    DexpiConnectionEndpointInfo merge = new DexpiConnectionEndpointInfo("MERGE", "Nozzle", "", "", true,
        Arrays.asList("C-1", "C-2"), Collections.singletonList("C-3"));
    DexpiConnectionEndpointInfo complex = new DexpiConnectionEndpointInfo("COMPLEX", "Nozzle", "", "", true,
        Arrays.asList("C-1", "C-2"), Arrays.asList("C-3", "C-4"));

    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.SOURCE, source.getIncidenceRole());
    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.SINK, sink.getIncidenceRole());
    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.PASS_THROUGH, passThrough.getIncidenceRole());
    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.SPLIT, split.getIncidenceRole());
    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.MERGE, merge.getIncidenceRole());
    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.COMPLEX, complex.getIncidenceRole());
    assertFalse(passThrough.isPotentialMultiConnectionNode());
    assertTrue(split.isPotentialMultiConnectionNode());
    assertTrue(merge.isPotentialMultiConnectionNode());
    assertTrue(complex.isPotentialMultiConnectionNode());
  }

  @Test
  public void testConnectionEndpointSummaryPreservesParallelOccurrences() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "<Nozzle ID=\"N-OUT\"/><Nozzle ID=\"N-IN\"/>"
        + "<PipingNetworkSegment ID=\"S-1\"><Connection FromID=\"N-OUT\" ToID=\"N-IN\"/>" + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-2\"><Connection FromID=\"N-OUT\" ToID=\"N-IN\"/>" + "</PipingNetworkSegment>"
        + "</PlantModel>";

    DexpiXmlReader.ImportResult result = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(2, result.getConnectionEndpoints().size());
    assertEquals(Arrays.asList("S-1/connection-1", "S-2/connection-1"),
        result.getConnectionEndpoints().get(0).getOutgoingConnectionIds());
    assertEquals(Arrays.asList("S-1/connection-1", "S-2/connection-1"),
        result.getConnectionEndpoints().get(1).getIncomingConnectionIds());
    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.COMPLEX,
        result.getConnectionEndpoints().get(0).getIncidenceRole());
    assertEquals(DexpiConnectionEndpointInfo.IncidenceRole.COMPLEX,
        result.getConnectionEndpoints().get(1).getIncidenceRole());
    assertTrue(result.getConnectionEndpoints().get(0).isPotentialMultiConnectionNode());
    assertTrue(result.getConnectionEndpoints().get(1).isPotentialMultiConnectionNode());
  }

  @Test
  public void testReadWithDiagnosticsSummarizesConnectionReferenceComponents() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "<Equipment ID=\"E-A\"><Nozzle ID=\"N-A\"/></Equipment>"
        + "<Equipment ID=\"E-J\"><Nozzle ID=\"N-J\"/></Equipment>"
        + "<Equipment ID=\"E-C\"><Nozzle ID=\"N-C\"/></Equipment>"
        + "<Equipment ID=\"E-X\"><Nozzle ID=\"N-X\"/></Equipment>"
        + "<Equipment ID=\"E-Y\"><Nozzle ID=\"N-Y\"/></Equipment>"
        + "<PipingNetworkSegment ID=\"S-1\"><Connection ID=\"C-1\" FromID=\"N-A\" ToID=\"N-J\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-2\"><Connection ID=\"C-2\" FromID=\"N-J\" ToID=\"N-C\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-3\"><Connection ID=\"C-3\" FromID=\"N-X\" ToID=\"N-Y\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-4\"><Connection ID=\"C-4\" FromID=\"N-X\" ToID=\"N-Y\"/>"
        + "</PipingNetworkSegment>" + "<PipingNetworkSegment ID=\"S-5\"><Connection ID=\"C-5\" FromID=\"UNKNOWN\"/>"
        + "</PipingNetworkSegment>" + "<PipingNetworkSegment ID=\"S-6\"><Connection ID=\"C-6\"/>"
        + "</PipingNetworkSegment>" + "</PlantModel>";

    DexpiXmlReader.ImportResult first = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    DexpiXmlReader.ImportResult second = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(6, first.getConnections().size());
    assertEquals(6, first.getConnectionEndpoints().size());
    assertEquals(3, first.getConnectionComponents().size());

    DexpiConnectionComponentInfo chain = first.getConnectionComponents().get(0);
    assertEquals("component-1", chain.getId());
    assertEquals(Arrays.asList("N-A", "N-J", "N-C"), chain.getEndpointIds());
    assertEquals(Arrays.asList("C-1", "C-2"), chain.getConnectionIds());
    assertEquals(Collections.singletonList("N-A"), chain.getSourceEndpointIds());
    assertEquals(Collections.singletonList("N-C"), chain.getSinkEndpointIds());
    assertEquals(3, chain.getEndpointCount());
    assertEquals(2, chain.getConnectionCount());
    assertFalse(chain.hasUnresolvedEndpoints());
    assertFalse(chain.hasPotentialMultiConnectionNodes());

    DexpiConnectionComponentInfo parallel = first.getConnectionComponents().get(1);
    assertEquals("component-2", parallel.getId());
    assertEquals(Arrays.asList("N-X", "N-Y"), parallel.getEndpointIds());
    assertEquals(Arrays.asList("C-3", "C-4"), parallel.getConnectionIds());
    assertEquals(Arrays.asList("N-X", "N-Y"), parallel.getPotentialMultiConnectionEndpointIds());
    assertTrue(parallel.hasPotentialMultiConnectionNodes());

    DexpiConnectionComponentInfo unresolved = first.getConnectionComponents().get(2);
    assertEquals("component-3", unresolved.getId());
    assertEquals(Collections.singletonList("UNKNOWN"), unresolved.getEndpointIds());
    assertEquals(Collections.singletonList("C-5"), unresolved.getConnectionIds());
    assertEquals(Collections.singletonList("UNKNOWN"), unresolved.getSourceEndpointIds());
    assertEquals(Collections.singletonList("UNKNOWN"), unresolved.getUnresolvedEndpointIds());
    assertTrue(unresolved.hasUnresolvedEndpoints());

    assertThrows(UnsupportedOperationException.class, () -> chain.getEndpointIds().clear());
    assertThrows(UnsupportedOperationException.class, () -> first.getConnectionComponents().clear());
    assertTrue(first.toJson().contains("\"connectionComponentCount\": 3"));
    assertTrue(first.toJson().contains("\"hasUnresolvedEndpoints\": true"));
    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  public void testReadWithDiagnosticsSummarizesDirectedConnectionCycles() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "<Equipment ID=\"E-A\"><Nozzle ID=\"N-A\"/></Equipment>"
        + "<Equipment ID=\"E-B\"><Nozzle ID=\"N-B\"/></Equipment>"
        + "<Equipment ID=\"E-C\"><Nozzle ID=\"N-C\"/></Equipment>"
        + "<Equipment ID=\"E-X\"><Nozzle ID=\"N-X\"/></Equipment>"
        + "<Equipment ID=\"E-Y\"><Nozzle ID=\"N-Y\"/></Equipment>"
        + "<Equipment ID=\"E-Z\"><Nozzle ID=\"N-Z\"/></Equipment>"
        + "<Equipment ID=\"E-P\"><Nozzle ID=\"N-P\"/></Equipment>"
        + "<Equipment ID=\"E-Q\"><Nozzle ID=\"N-Q\"/></Equipment>"
        + "<Equipment ID=\"E-S\"><Nozzle ID=\"N-S\"/></Equipment>"
        + "<PipingNetworkSegment ID=\"S-1\"><Connection ID=\"C-1\" FromID=\"N-A\" ToID=\"N-B\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-2\"><Connection ID=\"C-2\" FromID=\"N-B\" ToID=\"N-C\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-3\"><Connection ID=\"C-3\" FromID=\"N-X\" ToID=\"N-Y\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-3P\"><Connection ID=\"C-3P\" FromID=\"N-X\" ToID=\"N-Y\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-4\"><Connection ID=\"C-4\" FromID=\"N-Y\" ToID=\"N-Z\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-5\"><Connection ID=\"C-5\" FromID=\"N-Z\" ToID=\"N-X\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-6\"><Connection ID=\"C-6\" FromID=\"N-P\" ToID=\"N-Q\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-7\"><Connection ID=\"C-7\" FromID=\"N-P\" ToID=\"N-Q\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-8\"><Connection ID=\"C-8\" FromID=\"N-S\" ToID=\"N-S\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-9\"><Connection ID=\"C-9\" FromID=\"UNKNOWN-1\" ToID=\"UNKNOWN-2\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-10\"><Connection ID=\"C-10\" FromID=\"UNKNOWN-2\" ToID=\"UNKNOWN-1\"/>"
        + "</PipingNetworkSegment>" + "</PlantModel>";

    DexpiXmlReader.ImportResult first = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    DexpiXmlReader.ImportResult second = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(11, first.getConnections().size());
    assertEquals(11, first.getConnectionEndpoints().size());
    assertEquals(5, first.getConnectionComponents().size());
    assertEquals(3, first.getConnectionCycles().size());

    DexpiConnectionCycleInfo threeEndpointCycle = first.getConnectionCycles().get(0);
    assertEquals("cycle-1", threeEndpointCycle.getId());
    assertEquals("component-2", threeEndpointCycle.getConnectionComponentId());
    assertEquals(Arrays.asList("N-X", "N-Y", "N-Z"), threeEndpointCycle.getEndpointIds());
    assertEquals(Arrays.asList("C-3", "C-3P", "C-4", "C-5"), threeEndpointCycle.getConnectionIds());
    assertEquals(3, threeEndpointCycle.getEndpointCount());
    assertEquals(4, threeEndpointCycle.getConnectionCount());
    assertFalse(threeEndpointCycle.hasSelfReference());
    assertFalse(threeEndpointCycle.hasUnresolvedEndpoints());

    DexpiConnectionCycleInfo selfReference = first.getConnectionCycles().get(1);
    assertEquals("cycle-2", selfReference.getId());
    assertEquals("component-4", selfReference.getConnectionComponentId());
    assertEquals(Collections.singletonList("N-S"), selfReference.getEndpointIds());
    assertEquals(Collections.singletonList("C-8"), selfReference.getConnectionIds());
    assertTrue(selfReference.hasSelfReference());

    DexpiConnectionCycleInfo unresolved = first.getConnectionCycles().get(2);
    assertEquals("cycle-3", unresolved.getId());
    assertEquals("component-5", unresolved.getConnectionComponentId());
    assertEquals(Arrays.asList("UNKNOWN-1", "UNKNOWN-2"), unresolved.getEndpointIds());
    assertEquals(Arrays.asList("C-9", "C-10"), unresolved.getConnectionIds());
    assertEquals(Arrays.asList("UNKNOWN-1", "UNKNOWN-2"), unresolved.getUnresolvedEndpointIds());
    assertTrue(unresolved.hasUnresolvedEndpoints());

    for (DexpiConnectionCycleInfo cycle : first.getConnectionCycles()) {
      assertFalse(cycle.getEndpointIds().contains("N-P"));
      assertFalse(cycle.getEndpointIds().contains("N-Q"));
    }
    assertThrows(UnsupportedOperationException.class, () -> threeEndpointCycle.getEndpointIds().clear());
    assertThrows(UnsupportedOperationException.class, () -> first.getConnectionCycles().clear());
    assertTrue(first.toJson().contains("\"connectionCycleCount\": 3"));
    assertTrue(first.toJson().contains("\"connectionComponentId\": \"component-4\""));
    assertTrue(first.toJson().contains("\"selfReference\": true"));
    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  public void testReadWithDiagnosticsSummarizesDirectedCycleBoundaries() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>"
        + "<Equipment ID=\"E-A\"><Nozzle ID=\"N-A\"/></Equipment>"
        + "<Equipment ID=\"E-X\"><Nozzle ID=\"N-X\"/></Equipment>"
        + "<Equipment ID=\"E-Y\"><Nozzle ID=\"N-Y\"/></Equipment>"
        + "<Equipment ID=\"E-B\"><Nozzle ID=\"N-B\"/></Equipment>"
        + "<Equipment ID=\"E-S\"><Nozzle ID=\"N-S\"/></Equipment>"
        + "<Equipment ID=\"E-P\"><Nozzle ID=\"N-P\"/></Equipment>"
        + "<Equipment ID=\"E-Q\"><Nozzle ID=\"N-Q\"/></Equipment>"
        + "<PipingNetworkSegment ID=\"S-IN-1\"><Connection ID=\"C-IN-1\" FromID=\"N-A\" ToID=\"N-X\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-XY\"><Connection ID=\"C-XY\" FromID=\"N-X\" ToID=\"N-Y\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-OUT-1\"><Connection ID=\"C-OUT-1\" FromID=\"N-Y\" ToID=\"N-B\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-IN-2\"><Connection ID=\"C-IN-2\" FromID=\"UNKNOWN-U\" ToID=\"N-X\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-YX\"><Connection ID=\"C-YX\" FromID=\"N-Y\" ToID=\"N-X\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-OUT-2\"><Connection ID=\"C-OUT-2\" FromID=\"N-Y\" ToID=\"N-B\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-SELF\"><Connection ID=\"C-SELF\" FromID=\"N-S\" ToID=\"N-S\"/>"
        + "</PipingNetworkSegment>"
        + "<PipingNetworkSegment ID=\"S-PQ\"><Connection ID=\"C-PQ\" FromID=\"N-P\" ToID=\"N-Q\"/>"
        + "</PipingNetworkSegment>" + "</PlantModel>";

    DexpiXmlReader.ImportResult first = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    DexpiXmlReader.ImportResult second = DexpiXmlReader
        .readWithDiagnostics(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(2, first.getConnectionCycles().size());

    DexpiConnectionCycleInfo connectedCycle = first.getConnectionCycles().get(0);
    assertEquals("cycle-1", connectedCycle.getId());
    assertEquals("component-1", connectedCycle.getConnectionComponentId());
    assertEquals(Arrays.asList("N-X", "N-Y"), connectedCycle.getEndpointIds());
    assertEquals(Arrays.asList("C-XY", "C-YX"), connectedCycle.getConnectionIds());
    assertEquals(Arrays.asList("C-IN-1", "C-IN-2"), connectedCycle.getIncomingBoundaryConnectionIds());
    assertEquals(Arrays.asList("C-OUT-1", "C-OUT-2"), connectedCycle.getOutgoingBoundaryConnectionIds());
    assertEquals(2, connectedCycle.getIncomingBoundaryConnectionCount());
    assertEquals(2, connectedCycle.getOutgoingBoundaryConnectionCount());
    assertEquals(4, connectedCycle.getBoundaryConnectionCount());

    List<DexpiConnectionCycleBoundaryInfo> boundaries = connectedCycle.getBoundaryConnections();
    assertEquals(Arrays.asList("C-IN-1", "C-OUT-1", "C-IN-2", "C-OUT-2"),
        Arrays.asList(boundaries.get(0).getConnectionId(), boundaries.get(1).getConnectionId(),
            boundaries.get(2).getConnectionId(), boundaries.get(3).getConnectionId()));
    assertEquals(DexpiConnectionCycleBoundaryInfo.Direction.INCOMING, boundaries.get(0).getDirection());
    assertEquals("N-X", boundaries.get(0).getInternalEndpointId());
    assertEquals("N-A", boundaries.get(0).getExternalEndpointId());
    assertTrue(boundaries.get(0).isInternalEndpointResolved());
    assertTrue(boundaries.get(0).isExternalEndpointResolved());
    assertEquals("Nozzle", boundaries.get(0).getInternalEndpointElementName());
    assertEquals("E-X", boundaries.get(0).getInternalOwnerId());
    assertEquals("Equipment", boundaries.get(0).getInternalOwnerElementName());
    assertEquals("Nozzle", boundaries.get(0).getExternalEndpointElementName());
    assertEquals("E-A", boundaries.get(0).getExternalOwnerId());
    assertEquals("Equipment", boundaries.get(0).getExternalOwnerElementName());
    assertEquals(DexpiConnectionCycleBoundaryInfo.Direction.OUTGOING, boundaries.get(1).getDirection());
    assertEquals("N-Y", boundaries.get(1).getInternalEndpointId());
    assertEquals("N-B", boundaries.get(1).getExternalEndpointId());
    assertEquals("E-Y", boundaries.get(1).getInternalOwnerId());
    assertEquals("E-B", boundaries.get(1).getExternalOwnerId());
    assertEquals(DexpiConnectionCycleBoundaryInfo.Direction.INCOMING, boundaries.get(2).getDirection());
    assertEquals("N-X", boundaries.get(2).getInternalEndpointId());
    assertEquals("UNKNOWN-U", boundaries.get(2).getExternalEndpointId());
    assertTrue(boundaries.get(2).isInternalEndpointResolved());
    assertFalse(boundaries.get(2).isExternalEndpointResolved());
    assertEquals("", boundaries.get(2).getExternalEndpointElementName());
    assertEquals("", boundaries.get(2).getExternalOwnerId());
    assertEquals("", boundaries.get(2).getExternalOwnerElementName());

    DexpiConnectionCycleInfo closedSelfReference = first.getConnectionCycles().get(1);
    assertEquals("cycle-2", closedSelfReference.getId());
    assertEquals(Collections.singletonList("C-SELF"), closedSelfReference.getConnectionIds());
    assertTrue(closedSelfReference.getIncomingBoundaryConnectionIds().isEmpty());
    assertTrue(closedSelfReference.getOutgoingBoundaryConnectionIds().isEmpty());
    assertTrue(closedSelfReference.getBoundaryConnections().isEmpty());

    for (DexpiConnectionCycleInfo cycle : first.getConnectionCycles()) {
      assertFalse(cycle.getEndpointIds().contains("N-P"));
      assertFalse(cycle.getEndpointIds().contains("N-Q"));
    }
    assertThrows(UnsupportedOperationException.class, () -> connectedCycle.getIncomingBoundaryConnectionIds().clear());
    assertThrows(UnsupportedOperationException.class, () -> connectedCycle.getOutgoingBoundaryConnectionIds().clear());
    assertThrows(UnsupportedOperationException.class, () -> connectedCycle.getBoundaryConnections().clear());
    DexpiConnectionCycleInfo legacy = new DexpiConnectionCycleInfo("legacy", "component-legacy",
        Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList(),
        Collections.<String>emptyList(), Collections.<String>emptyList(), false);
    assertTrue(legacy.getBoundaryConnections().isEmpty());
    DexpiConnectionCycleBoundaryInfo legacyBoundary = new DexpiConnectionCycleBoundaryInfo("legacy-boundary",
        DexpiConnectionCycleBoundaryInfo.Direction.INCOMING, "N-INTERNAL", "N-EXTERNAL", true, false);
    assertEquals("", legacyBoundary.getInternalEndpointElementName());
    assertEquals("", legacyBoundary.getInternalOwnerId());
    assertEquals("", legacyBoundary.getExternalEndpointElementName());
    assertEquals("", legacyBoundary.getExternalOwnerId());
    assertTrue(first.toJson().contains("\"incomingBoundaryConnectionCount\": 2"));
    assertTrue(first.toJson().contains("\"boundaryConnectionCount\": 4"));
    assertTrue(first.toJson().contains("\"direction\": \"INCOMING\""));
    assertTrue(first.toJson().contains("\"externalEndpointId\": \"UNKNOWN-U\""));
    assertTrue(first.toJson().contains("\"internalOwnerId\": \"E-X\""));
    assertTrue(first.toJson().contains("\"externalOwnerElementName\": \"Equipment\""));
    assertTrue(first.toJson().contains("\"outgoingBoundaryConnectionIds\": ["));
    assertEquals(first.toJson(), second.toJson());
  }

  private static int countDiagnostics(DexpiXmlReader.ImportResult result, String expectedCode) {
    int count = 0;
    for (DexpiXmlReader.ImportDiagnostic diagnostic : result.getDiagnostics()) {
      if (expectedCode.equals(diagnostic.getCode())) {
        count++;
      }
    }
    return count;
  }

  private static String instrumentAttributes(String tag, String category, String functions, String number) {
    return "<GenericAttributes>" + "<GenericAttribute Name=\"TagNameAssignmentClass\" Value=\"" + tag + "\"/>"
        + "<GenericAttribute Name=\"ProcessInstrumentationFunctionCategoryAssignmentClass\" Value=\"" + category
        + "\"/>" + "<GenericAttribute Name=\"ProcessInstrumentationFunctionsAssignmentClass\" Value=\"" + functions
        + "\"/>" + "<GenericAttribute Name=\"ProcessInstrumentationFunctionNumberAssignmentClass\" Value=\"" + number
        + "\"/>" + "</GenericAttributes>";
  }

  private static String signalFlow(String id, String source, String target, String signalType) {
    return "<InformationFlow ComponentClass=\"SignalLineFunction\" ID=\"" + id + "\">"
        + "<Association Type=\"has logical start\" ItemID=\"" + source + "\"/>"
        + "<Association Type=\"has logical end\" ItemID=\"" + target + "\"/>" + "<GenericAttributes>"
        + "<GenericAttribute Name=\"SignalConveyingTypeSpecialization\" Value=\"" + signalType + "\"/>"
        + "</GenericAttributes></InformationFlow>";
  }

  private static void assertDiagnostic(DexpiXmlReader.ImportResult result, String expectedCode) {
    for (DexpiXmlReader.ImportDiagnostic diagnostic : result.getDiagnostics()) {
      if (expectedCode.equals(diagnostic.getCode())) {
        return;
      }
    }
    throw new AssertionError("Missing diagnostic " + expectedCode + " in " + result.toJson());
  }
}
