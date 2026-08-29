package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.ProcessDiagramGoldenFixtures;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Tests official DEXPI 2.0 Process-model serialization and scoped conformance evidence. */
class Dexpi20ProcessModelWriterTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void provesSimpleAndParallelBranchTopologyAgainstCanonicalManifest() throws Exception {
    assertGoldenTopology(ProcessDiagramGoldenFixtures.simpleTrain(), "DEXPI-GOLDEN-SIMPLE", "simple.dexpi.xml");
    assertGoldenTopology(ProcessDiagramGoldenFixtures.parallelBranchTrain(), "DEXPI-GOLDEN-BRANCH",
        "parallel-branch.dexpi.xml");
  }

  @Test
  void assessedExportConsumesCanonicalSnapshotWithoutChangingLegacyXml() throws Exception {
    Path legacyOutput = temporaryDirectory.resolve("legacy-parallel-branch.dexpi.xml");
    Path canonicalOutput = temporaryDirectory.resolve("canonical-parallel-branch.dexpi.xml");
    Dexpi20ProcessModelWriter.write(ProcessDiagramGoldenFixtures.parallelBranchTrain().getProcessSystem(),
        legacyOutput.toFile());

    Dexpi20ProcessTopologyAssessment.Report report = Dexpi20ProcessModelWriter.writeAndAssessTopology(
        ProcessDiagramGoldenFixtures.parallelBranchTrain().getProcessSystem(), canonicalOutput.toFile(),
        "DEXPI-CANONICAL-PROJECTION", "A");

    assertEquals(new String(Files.readAllBytes(legacyOutput), StandardCharsets.UTF_8),
        new String(Files.readAllBytes(canonicalOutput), StandardCharsets.UTF_8));
    assertEquals("CANONICAL_ENGINEERING_GRAPH", report.getExportTopologySource());
    assertNull(report.getExportOperatingValueSource());
    assertFalse(report.toMap().containsKey("exportOperatingValueSource"));
    assertTrue(report.toJson().contains("\"exportTopologySource\": \"CANONICAL_ENGINEERING_GRAPH\""));
    assertTrue(report.isSchemaProfileAndSupportedTopologyValid(), report.getDiagnostics().toString());
  }

  @Test
  void optInAssessedExportUsesCanonicalOperatingValuesWithDeterministicUnitConversion() throws Exception {
    ProcessSystem process = operatingProcess();
    process.run();
    Path first = temporaryDirectory.resolve("canonical-values-first.dexpi.xml");
    Path second = temporaryDirectory.resolve("canonical-values-second.dexpi.xml");

    Dexpi20ProcessTopologyAssessment.Report firstReport = Dexpi20ProcessModelWriter.writeAndAssessTopology(process,
        first.toFile(), "DEXPI-CANONICAL-VALUES", "A", "NORMAL-001");
    Dexpi20ProcessTopologyAssessment.Report secondReport = Dexpi20ProcessModelWriter.writeAndAssessTopology(process,
        second.toFile(), "DEXPI-CANONICAL-VALUES", "A", "NORMAL-001");

    assertEquals("CANONICAL_ENGINEERING_GRAPH_CALCULATION_NODES", firstReport.getExportOperatingValueSource());
    assertEquals(1000.0, physicalQuantity(first, "10-FEED-001", "MassFlow"), 1.0e-9);
    assertEquals(40.0, physicalQuantity(first, "10-FEED-001", "Pressure"), 1.0e-12);
    assertEquals(25.0, physicalQuantity(first, "10-FEED-001", "Temperature"), 1.0e-12);
    assertTrue(hasDiagnostic(firstReport, "DEXPI_PROCESS_OPERATING_VALUE_MISSING"));
    assertEquals(new String(Files.readAllBytes(first), StandardCharsets.UTF_8),
        new String(Files.readAllBytes(second), StandardCharsets.UTF_8));
    assertEquals(firstReport.toJson(), secondReport.toJson());
    assertTrue(firstReport.isSchemaProfileAndSupportedTopologyValid(), firstReport.getDiagnostics().toString());
  }

  @Test
  void optInAssessedExportOmitsUnrunValuesWithoutDirectStreamFallback() throws Exception {
    ProcessSystem process = operatingProcess();
    Path output = temporaryDirectory.resolve("canonical-values-unrun.dexpi.xml");

    Dexpi20ProcessTopologyAssessment.Report report = Dexpi20ProcessModelWriter.writeAndAssessTopology(process,
        output.toFile(), "DEXPI-CANONICAL-VALUES-UNRUN", "A", "NORMAL-001");
    String xml = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);

    assertFalse(xml.contains("PhysicalQuantities.MassFlowRateUnit"));
    assertFalse(xml.contains("PhysicalQuantities.PressureAbsoluteUnit"));
    assertFalse(xml.contains("PhysicalQuantities.TemperatureUnit"));
    assertTrue(hasDiagnostic(report, "DIAGRAM_OPERATING_CASE_NOT_SUCCESSFUL"));
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_OPERATING_VALUE_MISSING"));
    assertEquals("CANONICAL_ENGINEERING_GRAPH_CALCULATION_NODES", report.getExportOperatingValueSource());
    assertTrue(report.getConformanceReport().isSchemaAndProfileConformant());
  }

  @Test
  void reportsUnsupportedTopologyAndDocumentScopesWithoutHidingMaterialEquivalence() throws Exception {
    ProcessDiagramGoldenFixtures.Fixture fixture = ProcessDiagramGoldenFixtures.simpleTrain();
    ProcessSystem process = fixture.getProcessSystem();
    process.connect("heater", "temperatureSignal", "feed", "temperatureSetpoint",
        ProcessConnection.ConnectionType.SIGNAL);
    process.connect("heater", "calculatedDuty", "feed", "availableDuty", ProcessConnection.ConnectionType.ENERGY);
    Path output = temporaryDirectory.resolve("scoped-losses.dexpi.xml");

    Dexpi20ProcessTopologyAssessment.Report report = Dexpi20ProcessModelWriter.writeAndAssessTopology(process,
        output.toFile(), "DEXPI-SCOPED-LOSSES", "A");

    assertTrue(report.isSupportedMaterialTopologyEquivalent(), report.getDiagnostics().toString());
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_SIGNAL_CONNECTION_UNSUPPORTED"));
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_ENERGY_CONNECTION_UNSUPPORTED"));
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_MULTI_AREA_UNSUPPORTED"));
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_DOCUMENT_SEMANTICS_UNSUPPORTED"));
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_GRAPHICS_UNSUPPORTED"));
  }

  @Test
  void reportsCanonicalMaterialConnectionWithoutSimulationStreamAsLoss() throws Exception {
    ProcessSystem process = ProcessDiagramGoldenFixtures.simpleTrain().getProcessSystem();
    process.connect("heater", "manualRecycleOutlet", "feed", "manualRecycleInlet",
        ProcessConnection.ConnectionType.MATERIAL);
    Path output = temporaryDirectory.resolve("unresolved-canonical-material.dexpi.xml");

    Dexpi20ProcessTopologyAssessment.Report report = Dexpi20ProcessModelWriter.writeAndAssessTopology(process,
        output.toFile(), "DEXPI-CANONICAL-LOSS", "A");

    assertFalse(report.isSupportedMaterialTopologyEquivalent());
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_MATERIAL_CONNECTION_MISSING"));
    assertTrue(report.getConformanceReport().isSchemaAndProfileConformant());
  }

  @Test
  void writesSchemaValidProcessModelWithStepsPortsStreamsAndPhysicalQuantities() throws Exception {
    ProcessSystem process = process();
    Path output = temporaryDirectory.resolve("process-pfd.dexpi.xml");

    Dexpi20ConformanceAssessment.Report report = Dexpi20ProcessModelWriter.writeAndAssess(process, output.toFile());
    String xml = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);

    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    assertTrue(xml.contains("type=\"Process/ProcessModel\""));
    assertTrue(xml.contains("type=\"Process/Process.Compressing\""));
    assertTrue(xml.contains("type=\"Process/Process.Pumping\""));
    assertTrue(xml.contains("type=\"Process/Process.TransportingFluids\""));
    assertTrue(xml.contains("property=\"ConnectorReference\""));
    assertTrue(xml.contains("MassFlowRateUnit.KilogramPerHour"));
    assertTrue(report.toJson().contains("NOT_A_DEXPI_EV_CERTIFICATE"));
  }

  @Test
  void exportsZeroFlowTerminalAndIsolatedEmptyStreamsWithoutInvalidPorts() throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 1.0);
    Stream feed = new Stream("10-FEED-EMPTY", fluid);
    feed.setFlowRate(0.0, "kg/hr");
    Heater heater = new Heater("10-HA-EMPTY", feed);
    Stream product = new Stream("10-PRODUCT-EMPTY", heater.getOutletStream());
    Stream isolated = new Stream("10-ISOLATED-EMPTY");
    ProcessSystem process = new ProcessSystem("DEXPI empty-stream regression");
    process.add(feed);
    process.add(heater);
    process.add(product);
    process.add(isolated);
    Path output = temporaryDirectory.resolve("empty-streams.dexpi.xml");

    Dexpi20ProcessTopologyAssessment.Report report = Dexpi20ProcessModelWriter.writeAndAssessTopology(process,
        output.toFile(), "DEXPI-EMPTY-STREAMS", "A");

    assertTrue(report.isSchemaProfileAndSupportedTopologyValid(), report.getDiagnostics().toString());
    assertEquals(report.getCanonicalMaterialConnections(), report.getExportedMaterialConnections());
    assertTrue(report.getCanonicalMaterialConnections().contains("10-HA-EMPTY->10-PRODUCT-EMPTY"));
    assertTrue(hasAssessedConnection(report, "10-HA-EMPTY", "10-PRODUCT-EMPTY", false));
    assertEquals("Process/Process.Sink", processStepType(output, "10-PRODUCT-EMPTY"));
    assertEquals("Process/Process.Source", processStepType(output, "10-ISOLATED-EMPTY"));
    assertFalse(hasDirectPorts(output, "10-ISOLATED-EMPTY"));
    assertEquals(heater.getOutletStream(), product.getInletStreams().get(0));
  }

  @Test
  void documentationExampleWritesAssessedPlantAndProcessExchanges() throws Exception {
    ProcessSystem process = documentationProcess();
    process.run();

    Path outputDirectory = temporaryDirectory.resolve("dexpi-output");
    Files.createDirectories(outputDirectory);
    Path plantXml = outputDirectory.resolve("plant.dexpi.xml");
    Path processXml = outputDirectory.resolve("process.dexpi.xml");

    Dexpi20ConformanceAssessment.Report plantReport = Dexpi20XmlWriter.writeAndAssess(process, plantXml.toFile());
    Dexpi20ConformanceAssessment.Report processReport = Dexpi20ProcessModelWriter.writeAndAssess(process,
        processXml.toFile());

    assertTrue(plantReport.isSchemaAndProfileConformant(), plantReport.getErrors().toString());
    assertTrue(processReport.isSchemaAndProfileConformant(), processReport.getErrors().toString());

    Path plantReportFile = outputDirectory.resolve("plant.dexpi.conformance.json");
    Path processReportFile = outputDirectory.resolve("process.dexpi.conformance.json");
    Files.write(plantReportFile, plantReport.toJson().getBytes(StandardCharsets.UTF_8));
    Files.write(processReportFile, processReport.toJson().getBytes(StandardCharsets.UTF_8));

    assertTrue(Files.size(plantXml) > 0L);
    assertTrue(Files.size(processXml) > 0L);
    assertTrue(Files.size(plantReportFile) > 0L);
    assertTrue(Files.size(processReportFile) > 0L);
  }

  @Test
  void plantAndProcessAssessmentsCannotBeInterchanged() throws Exception {
    Path plant = temporaryDirectory.resolve("plant.dexpi.xml");
    Dexpi20XmlWriter.write(process(), plant.toFile());

    Dexpi20ConformanceAssessment.Report correct = Dexpi20ConformanceAssessment.assess(plant,
        Dexpi20ConformanceAssessment.Profile.PLANT_P_ID);
    Dexpi20ConformanceAssessment.Report wrong = Dexpi20ConformanceAssessment.assess(plant,
        Dexpi20ConformanceAssessment.Profile.PROCESS_PFD_BFD);

    assertTrue(correct.isSchemaAndProfileConformant(), correct.getErrors().toString());
    assertFalse(wrong.isSchemaAndProfileConformant());
    assertTrue(wrong.getErrors().toString().contains("official Core and Process model imports"));
  }

  @Test
  void semanticValidationRejectsIncompleteProcessPortAndStreamRelationships() throws Exception {
    String xml = "<Model name=\"InvalidProcess\" uri=\"urn:invalid:process\">"
        + "<Import prefix=\"Core\" source=\"https://data.dexpi.org/models/2.0.0/Core.xml\"/>"
        + "<Import prefix=\"Process\" source=\"https://data.dexpi.org/models/2.0.0/Process.xml\"/>"
        + "<Object type=\"Core/EngineeringModel\"><Components property=\"ConceptualModel\">"
        + "<Object id=\"ProcessModel1\" type=\"Process/ProcessModel\">"
        + "<Components property=\"ProcessSteps\"><Object id=\"Step1\" type=\"Process/Process.Source\">"
        + "<Data property=\"Identifier\"><String>source</String></Data>"
        + "<Components property=\"Ports\"><Object id=\"Port1\" type=\"Process/Process.MaterialPort\">"
        + "<Data property=\"Identifier\"><String>Port1</String></Data>" + "</Object></Components></Object></Components>"
        + "<Components property=\"ProcessConnections\"><Object id=\"Stream1\" type=\"Process/Process.Stream\">"
        + "<Data property=\"Identifier\"><String>Stream1</String></Data>"
        + "</Object></Components></Object></Components></Object></Model>";
    Path file = temporaryDirectory.resolve("invalid-process.dexpi.xml");
    Files.write(file, xml.getBytes(StandardCharsets.UTF_8));

    Dexpi20SemanticValidator.ValidationReport report = Dexpi20SemanticValidator.validate(file);

    assertFalse(report.isValid());
    assertTrue(report.getErrors().toString().contains("NominalDirection"));
    assertTrue(report.getErrors().toString().contains("ConnectorReference"));
    assertTrue(report.getErrors().toString().contains("Source reference"));
  }

  private ProcessSystem documentationProcess() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("10-FEED-001", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator separator = new Separator("10-VA-001", feed);
    Compressor compressor = new Compressor("10-KA-001", separator.getGasOutStream());
    compressor.setOutletPressure(80.0);

    ProcessSystem process = new ProcessSystem();
    process.setName("DEXPI 2.0 example");
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    return process;
  }

  private void assertGoldenTopology(ProcessDiagramGoldenFixtures.Fixture fixture, String plantId, String fileName)
      throws Exception {
    Path output = temporaryDirectory.resolve(fileName);

    Dexpi20ProcessTopologyAssessment.Report report = Dexpi20ProcessModelWriter
        .writeAndAssessTopology(fixture.getProcessSystem(), output.toFile(), plantId, "A");

    assertTrue(report.isSchemaProfileAndSupportedTopologyValid(), report.getDiagnostics().toString());
    assertTrue(report.isSupportedMaterialTopologyEquivalent(), report.getDiagnostics().toString());
    assertTrue(report.getConformanceReport().isSchemaAndProfileConformant());
    assertEquals(64, report.getCanonicalFingerprint().length());
    assertEquals(fixture.getMaterialConnections(), report.getCanonicalMaterialConnections());
    assertEquals(fixture.getMaterialConnections(), report.getExportedMaterialConnections());
    assertEquals(fixture.getMaterialConnections().size(),
        new java.util.LinkedHashSet<String>(report.getCanonicalConnectionIds()).size());
    assertTrue(hasDistinctPorts(report.getExportedConnections()));
  }

  private static boolean hasDistinctPorts(List<Dexpi20ProcessTopologyAssessment.ExportedConnection> connections) {
    java.util.Set<String> ports = new java.util.LinkedHashSet<String>();
    for (Dexpi20ProcessTopologyAssessment.ExportedConnection connection : connections) {
      ports.add(connection.getSourcePortId());
      ports.add(connection.getTargetPortId());
    }
    return ports.size() == connections.size() * 2;
  }

  private static boolean hasDiagnostic(Dexpi20ProcessTopologyAssessment.Report report, String code) {
    for (Dexpi20ProcessTopologyAssessment.Diagnostic diagnostic : report.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasAssessedConnection(Dexpi20ProcessTopologyAssessment.Report report, String source,
      String target, boolean boundaryTarget) {
    for (Dexpi20ProcessTopologyAssessment.ExportedConnection connection : report.getExportedConnections()) {
      if (source.equals(connection.getSourceStep()) && target.equals(connection.getTargetStep())
          && boundaryTarget == connection.isBoundaryTarget()) {
        return true;
      }
    }
    return false;
  }

  private static double physicalQuantity(Path file, String streamLabel, String property) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    NodeList objects = factory.newDocumentBuilder().parse(file.toFile()).getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (!"Process/Process.Stream".equals(object.getAttribute("type")) || !streamLabel.equals(label(object))) {
        continue;
      }
      for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof Element && "Components".equals(((Element) child).getTagName())
            && property.equals(((Element) child).getAttribute("property"))) {
          NodeList values = ((Element) child).getElementsByTagName("Double");
          if (values.getLength() > 0) {
            return Double.parseDouble(values.item(0).getTextContent());
          }
        }
      }
    }
    throw new AssertionError("Missing " + property + " for stream " + streamLabel);
  }

  private static String processStepType(Path file, String stepLabel) throws Exception {
    Element step = processStep(file, stepLabel);
    return step.getAttribute("type");
  }

  private static boolean hasDirectPorts(Path file, String stepLabel) throws Exception {
    Element step = processStep(file, stepLabel);
    for (Node child = step.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Components".equals(((Element) child).getTagName())
          && "Ports".equals(((Element) child).getAttribute("property"))) {
        return true;
      }
    }
    return false;
  }

  private static Element processStep(Path file, String stepLabel) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    NodeList objects = factory.newDocumentBuilder().parse(file.toFile()).getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (object.getAttribute("type").startsWith("Process/Process.") && stepLabel.equals(label(object))) {
        return object;
      }
    }
    throw new AssertionError("Missing process step " + stepLabel);
  }

  private static String label(Element object) {
    for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Data".equals(((Element) child).getTagName())
          && "Label".equals(((Element) child).getAttribute("property"))) {
        NodeList strings = ((Element) child).getElementsByTagName("String");
        return strings.getLength() == 0 ? "" : strings.item(0).getTextContent();
      }
    }
    return "";
  }

  private ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    Stream feed = new Stream("10-FEED-001", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator separator = new Separator("10-VA-001", feed);
    Compressor compressor = new Compressor("10-KA-001", separator.getGasOutStream());
    AdiabaticPipe pipeline = new AdiabaticPipe("10-PL-001", compressor.getOutletStream());
    Pump pump = new Pump("10-PA-001", separator.getLiquidOutStream());
    ProcessSystem process = new ProcessSystem();
    process.setName("DEXPI 2.0 Process exchange");
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(pipeline);
    process.add(pump);
    return process;
  }

  private static ProcessSystem operatingProcess() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("10-FEED-001", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Heater heater = new Heater("10-HA-001", feed);
    ProcessSystem process = new ProcessSystem("DEXPI canonical operating-value test");
    process.add(feed);
    process.add(heater);
    return process;
  }
}
