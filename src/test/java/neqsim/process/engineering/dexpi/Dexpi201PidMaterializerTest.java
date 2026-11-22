package neqsim.process.engineering.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi201ProcessModelWriter;
import neqsim.process.processmodel.dexpi.Dexpi20ConformanceAssessment;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.processmodel.dexpi.Dexpi20XmlWriter;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies actual native P&amp;ID sensing, actuation, inline and auxiliary route semantics.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public class Dexpi201PidMaterializerTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  /**
   * Reproduces proposal-order dependent recycle tees crossing isolation and non-return barriers.
   *
   * @throws Exception if native export or inspection fails
   */
  @Test
  void equipmentSideAuxiliaryTeesDoNotMoveAcrossLaterInlineProposals() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = project(process);
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "ORDERED-TIES");
    pid.add(new PidElement("ascv", "ASCV-101", PidElementType.CONTROL_VALVE).equipment("K-101")
        .attribute("proposalPurpose", "ANTI-SURGE").attribute("physicalSourceEquipmentTag", "K-101")
        .attribute("physicalSourceStreamTag", "COMP-OUT").attribute("physicalTargetEquipmentTag", "K-101")
        .attribute("physicalTargetStreamTag", "GAS"));
    pid.add(new PidElement("out-sdv", "SDV-OUT", PidElementType.SHUTDOWN_VALVE).equipment("K-101").line("COMP-OUT"));
    pid.add(new PidElement("check", "NRV", PidElementType.CHECK_VALVE).equipment("K-101").line("COMP-OUT"));
    pid.add(new PidElement("in-sdv", "SDV-IN", PidElementType.SHUTDOWN_VALVE).equipment("K-101").line("GAS"));
    pid.add(new PidElement("bdv", "BDV-101", PidElementType.BLOWDOWN_VALVE).equipment("K-101")
        .attribute("physicalSourceEquipmentTag", "K-101").attribute("physicalSourceStreamTag", "COMP-OUT")
        .attribute("destinationTag", "FLARE"));
    Path file = temporaryDirectory.resolve("equipment-side-ties.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Dexpi201PidMaterializer.Result result = Dexpi201PidMaterializer.materialize(project, pid,
        PidSimulationBindings.from(project, pid), file);
    assertTrue(result.isComplete(), result.toJson());
    Map<String, Element> objects = objects(parse(file));
    Element compressor = null;
    for (Element object : objects.values()) {
      if ("K-101".equals(stringData(object, "TagName"))) {
        compressor = object;
      }
    }
    assertNotNull(compressor);
    String owner = compressor.getAttribute("id");
    assertEquals(1, directedPipes(objects, owner + "Outlet1", "PidTee_Source_ascv"));
    assertEquals(1, directedPipes(objects, "PidTee_Source_ascv", "PidTee_Source_bdv"));
    assertEquals(1, directedPipes(objects, "PidTee_Source_bdv", result.getNativeIds().get("SDV-OUT")));
    assertEquals(1, directedPipes(objects, result.getNativeIds().get("SDV-OUT"), result.getNativeIds().get("NRV")));
    assertEquals(1, directedPipes(objects, result.getNativeIds().get("SDV-IN"), "PidTee_Target_ascv"));
    assertEquals(1, directedPipes(objects, "PidTee_Target_ascv", owner + "Inlet"));
    assertOwnedNodes(objects);
  }

  /**
   * Reproduces missing mandatory Method properties on compression and cooling steps.
   *
   * @throws Exception if export fails
   */
  @Test
  void processMethodsAreConformantWithoutTaskXmlPatches() throws Exception {
    Path file = temporaryDirectory.resolve("process.xml");
    Dexpi20ConformanceAssessment.Report report = Dexpi201ProcessModelWriter.writeAndAssess(process(), file.toFile(),
        metadata());
    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    Document document = parse(file);
    assertTrue(containsDataReference(document, "Process/Enumerations.CompressionMethod.Unspecified"));
    assertTrue(containsDataReference(document, "Process/Enumerations.HeatExchangeMethod.Generic"));
  }

  /**
   * Inspects physical sensor locations, connected valve nodes and typed controller/actuator references.
   *
   * @throws Exception if export fails
   */
  @Test
  void materializesActualMeasurementAliasesAndConnectedControlLoops() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = project(process);
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "TEST");
    pid.add(
        new PidElement("pt", "PT-DRAWING", PidElementType.MEASUREMENT).equipment("V-101").line("GAS").connect("pic"));
    pid.add(new PidElement("pic", "PIC-101", PidElementType.CONTROLLER).equipment("V-101").connect("pcv"));
    pid.add(new PidElement("pcv", "PCV-DRAWING", PidElementType.CONTROL_VALVE).equipment("E-101").line("COOLED"));
    pid.add(new PidElement("lt", "LT-101", PidElementType.MEASUREMENT).equipment("V-101")
        .attribute("sensingLocation", "VESSEL").connect("lic"));
    pid.add(new PidElement("lic", "LIC-101", PidElementType.CONTROLLER).equipment("V-101").connect("lcv"));
    pid.add(new PidElement("lcv", "LCV-NEW", PidElementType.CONTROL_VALVE).equipment("V-101").line("LIQUID"));
    PidSimulationBindings bindings = PidSimulationBindings.from(project, pid).bindMeasurement("PT-DRAWING", "PT-LIVE")
        .bindEquipment("PCV-DRAWING", "PCV-LIVE");
    Path file = temporaryDirectory.resolve("pid.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Dexpi201PidMaterializer.Result result = Dexpi201PidMaterializer.materialize(project, pid, bindings, file);
    assertTrue(result.getAssessment().isSchemaAndProfileConformant(), result.getAssessment().getErrors().toString());
    assertTrue(result.isComplete(), result.toJson());
    assertEquals(6, result.getNativeIds().size());
    assertEquals(4, result.getSignalLinks().size());
    assertTrue(result.getUnresolved().isEmpty());
    Map<String, Element> objects = objects(parse(file));
    assertEquals("Plant/Piping.PipingNetworkSegment",
        objects.get(reference(objects.get("PidSensor_pt"), "SensingLocation")).getAttribute("type"));
    assertEquals("Plant/ProcessEquipment.InstrumentNozzle",
        objects.get(reference(objects.get("PidSensor_lt"), "SensingLocation")).getAttribute("type"));
    Element valve = objects.get(result.getNativeIds().get("LCV-NEW"));
    assertEquals("Plant/Piping.OperatedValve", valve.getAttribute("type"));
    assertEquals(1, pipes(objects, valve.getAttribute("id"), "SourceItem"));
    assertEquals(1, pipes(objects, valve.getAttribute("id"), "TargetItem"));
    assertOwnedNodes(objects);
    assertEquals(6, process.getUnitOperations().size());
    assertEquals(1, process.getMeasurementDevices().size());
  }

  /**
   * Retains missing auxiliary paths as HOLDs without fake disconnected native devices.
   *
   * @throws Exception if export fails
   */
  @Test
  void unconfiguredAuxiliaryPathRemainsUnmapped() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = project(process);
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "TEST");
    pid.add(new PidElement("bdv", "BDV-101", PidElementType.BLOWDOWN_VALVE).equipment("V-101"));
    Path file = temporaryDirectory.resolve("unconfigured.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Dexpi201PidMaterializer.Result result = Dexpi201PidMaterializer.materialize(project, pid,
        PidSimulationBindings.from(project, pid), file);
    assertTrue(result.getAssessment().isSchemaAndProfileConformant());
    assertFalse(result.isComplete());
    assertEquals("EXPLICIT_AUXILIARY_SOURCE_AND_TARGET_REQUIRED", result.getUnresolved().get("BDV-101"));
    assertFalse(result.getNativeIds().containsKey("BDV-101"));
    assertFalse(objects(parse(file)).containsKey("PidValve_bdv"));
  }

  /**
   * Checks explicit recycle endpoints create real three-node tees and preserve node ownership.
   *
   * @throws Exception if export fails
   */
  @Test
  void explicitRecycleCreatesOwnedTeeNodesAndConnectedValve() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = project(process);
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "TEST");
    pid.add(new PidElement("ascv", "ASCV-101", PidElementType.CONTROL_VALVE).equipment("K-101")
        .attribute("proposalPurpose", "ANTISURGE-VALVE").attribute("physicalSourceEquipmentTag", "K-101")
        .attribute("physicalSourceStreamTag", "COMP-OUT").attribute("physicalTargetEquipmentTag", "K-101")
        .attribute("physicalTargetStreamTag", "GAS"));
    Path file = temporaryDirectory.resolve("recycle.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Dexpi201PidMaterializer.Result result = Dexpi201PidMaterializer.materialize(project, pid,
        PidSimulationBindings.from(project, pid), file);
    assertTrue(result.getAssessment().isSchemaAndProfileConformant(), result.getAssessment().getErrors().toString());
    assertTrue(result.isComplete(), result.toJson());
    Map<String, Element> objects = objects(parse(file));
    Element valve = objects.get(result.getNativeIds().get("ASCV-101"));
    assertEquals(1, pipes(objects, valve.getAttribute("id"), "SourceItem"));
    assertEquals(1, pipes(objects, valve.getAttribute("id"), "TargetItem"));
    int tees = 0;
    for (Element item : objects.values()) {
      if ("Plant/Piping.PipeTee".equals(item.getAttribute("type"))) {
        tees++;
      }
    }
    assertEquals(2, tees);
    assertOwnedNodes(objects);
  }

  /**
   * Rejects legacy files before inserting modern-only types.
   *
   * @throws Exception if export fails
   */
  @Test
  void rejectsLegacyEdition() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = project(process);
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "TEST");
    Path file = temporaryDirectory.resolve("legacy.xml");
    Dexpi20XmlWriter.write(process, file.toFile());
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi201PidMaterializer.materialize(project, pid, PidSimulationBindings.from(project, pid), file));
  }

  /**
   * Proves a proposed utility nozzle is owned by the cooler and shares no original process pipe nodes.
   *
   * @throws Exception if projection fails
   */
  @Test
  void proposedUtilityNozzleIsSeparateFromTheProcessFluidGraph() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = project(process);
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "UTILITY-TEST");
    pid.add(new PidElement("utility-ft", "FT-UTILITY", PidElementType.MEASUREMENT).equipment("E-101")
        .attribute("sensingLocation", "UTILITY_SUPPLY").attribute("measuredVariable", "FLOW")
        .attribute("physicalSourceEquipmentTag", "E-101").attribute("physicalSourceNozzleTag", "CW-RETURN")
        .attribute("physicalSourcePortRole", "UTILITY").attribute("designEvidenceReference", "SYNTHETIC-CW-DESIGN")
        .connect("utility-fic"));
    pid.add(new PidElement("utility-fic", "FIC-UTILITY", PidElementType.CONTROLLER).equipment("E-101")
        .connect("utility-tcv"));
    pid.add(new PidElement("utility-tcv", "TCV-UTILITY", PidElementType.CONTROL_VALVE).equipment("E-101")
        .attribute("proposalPurpose", "UTILITY-CONTROL-VALVE").attribute("physicalSourceEquipmentTag", "E-101")
        .attribute("physicalSourceNozzleTag", "CW-RETURN").attribute("physicalSourcePortRole", "UTILITY")
        .attribute("designEvidenceReference", "SYNTHETIC-CW-DESIGN").attribute("destinationTag", "CW-RETURN-HEADER"));
    Path file = temporaryDirectory.resolve("utility.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Map<String, Element> original = objects(parse(file));
    Dexpi201PidMaterializer.Result result = Dexpi201PidMaterializer.materialize(project, pid,
        PidSimulationBindings.from(project, pid), file);
    assertTrue(result.getAssessment().isSchemaAndProfileConformant(), result.getAssessment().getErrors().toString());
    assertTrue(result.isComplete(), result.toJson());
    Map<String, Element> generated = objects(parse(file));
    Element sensor = generated.get("PidSensor_utility_ft");
    Element nozzle = generated.get(reference(sensor, "SensingLocation"));
    assertEquals("Plant/ProcessEquipment.ProcessNozzle", nozzle.getAttribute("type"));
    assertEquals("CW-RETURN", stringData(nozzle, "SubTagName"));
    Element nozzleOwner = (Element) nozzle.getParentNode().getParentNode();
    assertEquals("E-101", stringData(nozzleOwner, "TagName"));
    assertEquals(1, pipes(generated, nozzle.getAttribute("id"), "SourceItem"));
    assertEquals(0, pipes(generated, nozzle.getAttribute("id"), "TargetItem"));
    for (Map.Entry<String, Element> item : original.entrySet()) {
      if (!"Plant/Piping.Pipe".equals(item.getValue().getAttribute("type"))) {
        continue;
      }
      for (String direction : new String[] {"SourceItem", "SourceNode", "TargetItem", "TargetNode"}) {
        assertEquals(reference(item.getValue(), direction), reference(generated.get(item.getKey()), direction));
      }
    }
    for (Element item : generated.values()) {
      assertFalse("Plant/Piping.PipeTee".equals(item.getAttribute("type")));
    }
    assertOwnedNodes(generated);
    assertTrue(result.toJson().contains("PROPOSED_NOZZLE_NOT_PRESENT_IN_RUNTIME_SOURCE"));
  }

  /**
   * Rejects invented utility nozzles when the controlled design reference is missing.
   *
   * @throws Exception if base export fails
   */
  @Test
  void utilityNozzleRequiresControlledDesignEvidence() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = project(process);
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "UTILITY-TEST");
    pid.add(new PidElement("utility-ft", "FT-UTILITY", PidElementType.MEASUREMENT).equipment("E-101")
        .attribute("sensingLocation", "UTILITY_SUPPLY").attribute("physicalSourceEquipmentTag", "E-101")
        .attribute("physicalSourceNozzleTag", "CW-IN").attribute("physicalSourcePortRole", "UTILITY"));
    Path file = temporaryDirectory.resolve("utility-no-evidence.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Dexpi201PidMaterializer.Result result = Dexpi201PidMaterializer.materialize(project, pid,
        PidSimulationBindings.from(project, pid), file);
    assertFalse(result.isComplete());
    assertEquals("CONTROLLED_PROPOSED_SENSING_NOZZLE_INPUT_REQUIRED", result.getUnresolved().get("FT-UTILITY"));
    assertFalse(objects(parse(file)).containsKey("PidDesignedNozzle_E_101_CW_IN"));
  }

  /**
   * Verifies explicit series order creates one utility path instead of a parallel shutdown bypass.
   *
   * @throws Exception if projection fails
   */
  @Test
  void explicitUtilitySeriesOrderCreatesOneSourceSdvTcvDestinationPath() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = project(process);
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "UTILITY-SERIES");
    // Deliberately reverse proposal-list order: only explicit order defines physical flow.
    pid.add(utilitySeriesValve("tcv", "TCV-UTILITY", PidElementType.CONTROL_VALVE, 20));
    pid.add(utilitySeriesValve("sdv", "SDV-UTILITY", PidElementType.SHUTDOWN_VALVE, 10));
    Path file = temporaryDirectory.resolve("utility-series.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Dexpi201PidMaterializer.Result result = Dexpi201PidMaterializer.materialize(project, pid,
        PidSimulationBindings.from(project, pid), file);
    assertTrue(result.getAssessment().isSchemaAndProfileConformant(), result.getAssessment().getErrors().toString());
    assertTrue(result.isComplete(), result.toJson());
    Map<String, Element> generated = objects(parse(file));
    String nozzle = "PidDesignedNozzle_E_101_CW_RETURN";
    String sdv = result.getNativeIds().get("SDV-UTILITY");
    String tcv = result.getNativeIds().get("TCV-UTILITY");
    assertEquals(1, pipes(generated, nozzle, "SourceItem"));
    assertEquals(1, directedPipes(generated, nozzle, sdv));
    assertEquals(1, directedPipes(generated, sdv, tcv));
    assertEquals(0, directedPipes(generated, nozzle, tcv));
    assertEquals(1, pipes(generated, tcv, "TargetItem"));
    assertEquals(1, pipes(generated, tcv, "SourceItem"));
    assertOwnedNodes(generated);
  }

  /**
   * Rejects an ambiguous series as one group before creating any physical device or nozzle.
   *
   * @throws Exception if base export fails
   */
  @Test
  void duplicateSeriesOrderLeavesTheEntireRouteUnprojected() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = project(process);
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "UTILITY-SERIES");
    pid.add(utilitySeriesValve("tcv", "TCV-UTILITY", PidElementType.CONTROL_VALVE, 10));
    pid.add(utilitySeriesValve("sdv", "SDV-UTILITY", PidElementType.SHUTDOWN_VALVE, 10));
    Path file = temporaryDirectory.resolve("utility-series-conflict.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Dexpi201PidMaterializer.Result result = Dexpi201PidMaterializer.materialize(project, pid,
        PidSimulationBindings.from(project, pid), file);
    assertTrue(result.getAssessment().isSchemaAndProfileConformant());
    assertFalse(result.isComplete());
    assertEquals(2, result.getUnresolved().size());
    assertTrue(result.getNativeIds().isEmpty());
    assertEquals("DUPLICATE_EXPLICIT_AUXILIARY_SERIES_ORDER", result.getUnresolved().get("TCV-UTILITY"));
    assertFalse(objects(parse(file)).containsKey("PidDesignedNozzle_E_101_CW_RETURN"));
    assertFalse(objects(parse(file)).containsKey("PidValve_tcv"));
  }

  /**
   * Creates an explicitly controlled utility-series design input.
   *
   * @param id proposal identity
   * @param tag design device tag
   * @param type physical operated-valve category
   * @param order explicit position along the material path
   * @return design proposal with common nozzle/destination/evidence
   */
  private static PidElement utilitySeriesValve(String id, String tag, PidElementType type, int order) {
    return new PidElement(id, tag, type).equipment("E-101").attribute("physicalSourceEquipmentTag", "E-101")
        .attribute("physicalSourceNozzleTag", "CW-RETURN").attribute("physicalSourcePortRole", "UTILITY")
        .attribute("designEvidenceReference", "SYNTHETIC-CW-DESIGN").attribute("destinationTag", "CW-RETURN-HEADER")
        .attribute("physicalRouteId", "E101-CW-RETURN").attribute("physicalRouteOrder", Integer.valueOf(order));
  }

  /**
   * Counts actual directed material connections between native component owners.
   *
   * @param objects native object index
   * @param source actual source item
   * @param target actual target item
   * @return number of physical pipes
   */
  private static int directedPipes(Map<String, Element> objects, String source, String target) {
    int count = 0;
    for (Element object : objects.values()) {
      if ("Plant/Piping.Pipe".equals(object.getAttribute("type")) && source.equals(reference(object, "SourceItem"))
          && target.equals(reference(object, "TargetItem"))) {
        count++;
      }
    }
    return count;
  }

  /**
   * Reads one direct scalar property.
   *
   * @param object native source
   * @param property native property
   * @return string value or empty
   */
  private static String stringData(Element object, String property) {
    for (Node node = object.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element && "Data".equals(((Element) node).getTagName())
          && property.equals(((Element) node).getAttribute("property"))) {
        return node.getTextContent().trim();
      }
    }
    return "";
  }

  /**
   * Builds a solved synthetic compression/cooling process.
   *
   * @return actual source fixture
   */
  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(308.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator separator = new Separator("V-101", feed);
    separator.getGasOutStream().setName("GAS");
    separator.getLiquidOutStream().setName("LIQUID");
    Compressor compressor = new Compressor("K-101", separator.getGasOutStream());
    compressor.setOutletPressure(80.0);
    compressor.getOutletStream().setName("COMP-OUT");
    Cooler cooler = new Cooler("E-101", compressor.getOutletStream());
    cooler.setOutTemperature(303.15);
    cooler.getOutletStream().setName("COOLED");
    ThrottlingValve valve = new ThrottlingValve("PCV-LIVE", cooler.getOutletStream());
    valve.setOutletPressure(70.0);
    ThrottlingValve drain = new ThrottlingValve("LCV-LIVE", separator.getLiquidOutStream());
    drain.setOutletPressure(20.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
    process.add(valve);
    process.add(drain);
    process.add(new PressureTransmitter("PT-LIVE", separator.getGasOutStream()));
    process.run();
    return process;
  }

  /**
   * Creates the controlled project identity.
   *
   * @param process source fixture
   * @return controlled project
   */
  private static EngineeringProject project(ProcessSystem process) {
    return NorsokOffshoreEngineeringBuilder.from("Native P&ID projection", process, "2.0.1").projectId("NATIVE-PID")
        .registerProposedInstruments(false).build().setRevision("A");
  }

  /** @return fixed synthetic export metadata */
  private static Dexpi20PlantExportMetadata metadata() {
    return Dexpi20PlantExportMetadata.builder("2026-11-03T16:00:00+01:00", "NeqSim test", "NeqSim", "development")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "NATIVE-PID")
        .build();
  }

  /**
   * Parses a generated native instance.
   *
   * @param file native file
   * @return parsed document
   * @throws Exception if parsing fails
   */
  private static Document parse(Path file) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
  }

  /**
   * Indexes actual object identities.
   *
   * @param document native instance
   * @return ID/object index
   */
  private static Map<String, Element> objects(Document document) {
    Map<String, Element> result = new LinkedHashMap<String, Element>();
    NodeList values = document.getElementsByTagName("Object");
    for (int i = 0; i < values.getLength(); i++) {
      Element value = (Element) values.item(i);
      result.put(value.getAttribute("id"), value);
    }
    return result;
  }

  /**
   * Reads a direct native reference.
   *
   * @param object native source
   * @param property native reference property
   * @return target ID without hash
   */
  private static String reference(Element object, String property) {
    for (Node node = object.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element && "References".equals(((Element) node).getTagName())
          && property.equals(((Element) node).getAttribute("property"))) {
        return ((Element) node).getAttribute("objects").replace("#", "");
      }
    }
    return "";
  }

  /**
   * Counts actual inbound/outbound pipes.
   *
   * @param objects native objects
   * @param owner endpoint owner ID
   * @param direction SourceItem or TargetItem
   * @return count
   */
  private static int pipes(Map<String, Element> objects, String owner, String direction) {
    int count = 0;
    for (Element object : objects.values()) {
      if ("Plant/Piping.Pipe".equals(object.getAttribute("type")) && owner.equals(reference(object, direction))) {
        count++;
      }
    }
    return count;
  }

  /**
   * Independently checks native endpoint ownership for every physical pipe.
   *
   * @param objects native object index
   */
  private static void assertOwnedNodes(Map<String, Element> objects) {
    for (Element pipe : objects.values()) {
      if (!"Plant/Piping.Pipe".equals(pipe.getAttribute("type"))) {
        continue;
      }
      for (String direction : new String[] {"Source", "Target"}) {
        Element node = objects.get(reference(pipe, direction + "Node"));
        assertNotNull(node);
        Element owner = (Element) node.getParentNode().getParentNode();
        assertEquals(owner.getAttribute("id"), reference(pipe, direction + "Item"));
      }
    }
  }

  /**
   * Reads the actually written required enumeration literal.
   *
   * @param document exported Process model
   * @param value expected literal
   * @return whether present
   */
  private static boolean containsDataReference(Document document, String value) {
    NodeList values = document.getElementsByTagName("DataReference");
    for (int i = 0; i < values.getLength(); i++) {
      if (value.equals(((Element) values.item(i)).getAttribute("data"))) {
        return true;
      }
    }
    return false;
  }
}
