package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies genuine 2.0.1 type, relationship and provenance semantics and legacy compatibility.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public class Dexpi201XmlWriterTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  /**
   * Does not invent air cooling or fired construction from a generic energy-duty object.
   *
   * @throws Exception if export fails
   */
  @Test
  void genericThermalDutyRetainsGeneric201EquipmentType() throws Exception {
    ProcessSystem process = process();
    neqsim.process.equipment.heatexchanger.Cooler cooler = new neqsim.process.equipment.heatexchanger.Cooler(
        "E-GENERIC", process.getUnit("PV-101").getOutletStreams().get(0));
    cooler.setOutTemperature(295.15);
    process.add(cooler);
    process.run();
    Path file = temporaryDirectory.resolve("generic-thermal.xml");
    Dexpi20ConformanceAssessment.Report report = Dexpi201XmlWriter.writeAndAssess(process, file.toFile(), metadata());
    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    String xml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertTrue(xml.contains("Plant/ProcessEquipment.HeatExchanger"));
    assertFalse(xml.contains("Plant/ProcessEquipment.AirCoolingSystem"));
  }

  /**
   * Checks exact imports, typed nozzles, deterministic output and version-bound evidence.
   *
   * @throws Exception if export fails
   */
  @Test
  void emitsActual201AndPreservesLegacy() throws Exception {
    ProcessSystem process = process();
    Path modern = temporaryDirectory.resolve("modern.xml");
    Path repeated = temporaryDirectory.resolve("repeated.xml");
    Dexpi20ConformanceAssessment.Report report = Dexpi201XmlWriter.writeAndAssess(process, modern.toFile(), metadata());
    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    assertEquals("2.0.1", report.toMap().get("dexpiSpecificationVersion"));
    Dexpi201XmlWriter.writeAndAssess(process, repeated.toFile(), metadata());
    assertArrayEquals(Files.readAllBytes(modern), Files.readAllBytes(repeated));
    String xml = new String(Files.readAllBytes(modern), StandardCharsets.UTF_8);
    assertTrue(xml.contains("https://data.dexpi.org/models/2.0.1/Plant.xml"));
    assertTrue(xml.contains("Plant/ProcessEquipment.ProcessNozzle"));
    assertFalse(xml.contains("models/2.0.0/"));
    assertFalse(Dexpi20ConformanceAssessment.assess(modern, Dexpi20ConformanceAssessment.Profile.PLANT_P_ID)
        .isSchemaAndProfileConformant());
    Path legacy = temporaryDirectory.resolve("legacy.xml");
    Dexpi20XmlWriter.write(process, legacy.toFile());
    assertTrue(new String(Files.readAllBytes(legacy), StandardCharsets.UTF_8).contains("models/2.0.0/"));
    assertThrows(IllegalArgumentException.class, () -> Dexpi201XmlWriter.writeAndAssess(process, repeated.toFile(),
        Dexpi20PlantExportOptions.builder(metadata()).build()));
  }

  /**
   * Verifies a real control valve is inserted on connected piping with owned nodes.
   *
   * @throws Exception if export or parsing fails
   */
  @Test
  void insertsControlValveOnPhysicalPipingPath() throws Exception {
    Path file = temporaryDirectory.resolve("valve.xml");
    Dexpi201XmlWriter.writeAndAssess(process(), file.toFile(), metadata());
    Document document = parse(file);
    Element valve = object(document, "Plant/Piping.OperatedValve");
    assertEquals("Items", ((Element) valve.getParentNode()).getAttribute("property"));
    NodeList components = valve.getElementsByTagName("Components");
    for (int index = 0; index < components.getLength(); index++) {
      assertFalse("Nozzles".equals(((Element) components.item(index)).getAttribute("property")));
    }
    assertTrue(valve.getTextContent().contains("PV-101"));
    assertEquals(2, valve.getElementsByTagName("Object").getLength());
    int inbound = 0;
    int outbound = 0;
    NodeList objects = document.getElementsByTagName("Object");
    java.util.Map<String, Element> identities = new java.util.LinkedHashMap<String, Element>();
    for (int index = 0; index < objects.getLength(); index++) {
      Element value = (Element) objects.item(index);
      identities.put(value.getAttribute("id"), value);
    }
    for (int index = 0; index < objects.getLength(); index++) {
      Element pipe = (Element) objects.item(index);
      if (!"Plant/Piping.Pipe".equals(pipe.getAttribute("type"))) {
        continue;
      }
      java.util.Map<String, String> endpoints = new java.util.LinkedHashMap<String, String>();
      NodeList refs = pipe.getElementsByTagName("References");
      for (int refIndex = 0; refIndex < refs.getLength(); refIndex++) {
        Element reference = (Element) refs.item(refIndex);
        endpoints.put(reference.getAttribute("property"), reference.getAttribute("objects").substring(1));
      }
      for (String direction : new String[] {"Source", "Target"}) {
        Element node = identities.get(endpoints.get(direction + "Node"));
        Element owner = (Element) node.getParentNode().getParentNode();
        assertTrue(endpoints.containsKey(direction + "Item"));
        assertTrue(endpoints.containsKey(direction + "Node"));
        assertEquals(endpoints.get(direction + "Item"), owner.getAttribute("id"));
      }
      if (valve.getAttribute("id").equals(endpoints.get("SourceItem"))) {
        outbound++;
      }
      if (valve.getAttribute("id").equals(endpoints.get("TargetItem"))) {
        inbound++;
      }
    }
    assertEquals(1, inbound);
    assertEquals(1, outbound);
  }

  /**
   * Prevents protection-device subclasses from silently becoming control valves.
   *
   * @throws Exception if export fails
   */
  @Test
  void preservesValveFunctionalFamiliesWithoutInventingBodyTypes() throws Exception {
    ProcessSystem process = process();
    Stream feed = (Stream) process.getUnit("FEED");
    process.add(new neqsim.process.equipment.valve.ESDValve("ESDV-201", feed));
    process.add(new neqsim.process.equipment.valve.CheckValve("NRV-201", feed));
    process.add(new neqsim.process.equipment.valve.SafetyValve("PSV-201", feed));
    process.add(new neqsim.process.equipment.valve.RuptureDisk("RD-201", feed));
    Path file = temporaryDirectory.resolve("protection-families.xml");
    Dexpi20ConformanceAssessment.Report report = Dexpi201XmlWriter.writeAndAssess(process, file.toFile(), metadata());
    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    Document document = parse(file);
    assertEquals("Plant/Piping.CheckValve", object(document, "Plant/Piping.CheckValve").getAttribute("type"));
    assertEquals("Plant/Piping.SafetyValveOrFitting",
        object(document, "Plant/Piping.SafetyValveOrFitting").getAttribute("type"));
    assertEquals("Plant/Piping.RuptureDisc", object(document, "Plant/Piping.RuptureDisc").getAttribute("type"));
    assertFalse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("SpringLoadedGlobeSafetyValve"));
  }

  /**
   * Verifies optional Process export uses the same exact release and official definitions.
   *
   * @throws Exception if Process serialization fails
   */
  @Test
  void exports201ProcessProfile() throws Exception {
    Path file = temporaryDirectory.resolve("process201.xml");
    Dexpi20ConformanceAssessment.Report report = Dexpi201ProcessModelWriter.writeAndAssess(process(), file.toFile(),
        metadata());
    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    assertEquals("2.0.1", report.toMap().get("dexpiSpecificationVersion"));
    assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        .contains("https://data.dexpi.org/models/2.0.1/Process.xml"));
  }

  /**
   * Rejects syntactically valid data with incorrect official model declarations.
   *
   * @throws Exception if fixture setup fails
   */
  @Test
  void rejectsUnknownPropertyMissingRequiredAndWrongRelationship() throws Exception {
    Path file = temporaryDirectory.resolve("valid.xml");
    Dexpi201XmlWriter.writeAndAssess(process(), file.toFile(), metadata());
    Document document = parse(file);
    Element equipment = object(document, "Plant/ProcessEquipment.Separator");
    Element bad = document.createElement("Data");
    bad.setAttribute("property", "InventedDesignApproval");
    Element text = document.createElement("String");
    text.setTextContent("approved");
    bad.appendChild(text);
    equipment.appendChild(bad);
    assertTrue(Dexpi201ModelValidator.validate(document).toString().contains("Unknown DEXPI 2.0.1 property"));

    document = parse(file);
    Element engineering = object(document, "Core/EngineeringModel");
    NodeList data = engineering.getElementsByTagName("Data");
    for (int index = 0; index < data.getLength(); index++) {
      Element value = (Element) data.item(index);
      if ("OriginatingSystemName".equals(value.getAttribute("property"))) {
        value.getParentNode().removeChild(value);
        break;
      }
    }
    assertTrue(Dexpi201ModelValidator.validate(document).toString().contains("multiplicity violation"));

    document = parse(file);
    Element pipe = object(document, "Plant/Piping.Pipe");
    NodeList refs = pipe.getElementsByTagName("References");
    for (int index = 0; index < refs.getLength(); index++) {
      Element ref = (Element) refs.item(index);
      if ("SourceItem".equals(ref.getAttribute("property"))) {
        ref.setAttribute("objects", "#" + object(document, "Plant/ProcessEquipment.Separator").getAttribute("id"));
      }
    }
    assertTrue(Dexpi201ModelValidator.validate(document).toString().contains("relationship type mismatch"));
    assertTrue(Dexpi201ModelValidator.validate(document).toString().contains("piping node is not owned"));
    document = parse(file);
    pipe = object(document, "Plant/Piping.Pipe");
    refs = pipe.getElementsByTagName("References");
    for (int index = 0; index < refs.getLength(); index++) {
      Element reference = (Element) refs.item(index);
      if ("SourceItem".equals(reference.getAttribute("property"))) {
        reference.getParentNode().removeChild(reference);
        break;
      }
    }
    assertTrue(
        Dexpi201ModelValidator.validate(document).toString().contains("supported physical pipe requires SourceItem"));
  }

  /**
   * Verifies safety functions and sensing locations use actual 2.0.1 subclasses.
   *
   * @throws Exception if engineering export fails
   */
  @Test
  void materializesGovernedSafetyAndInstrumentNozzles() throws Exception {
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Synthetic 201", process()).build();
    project.addRequirement(new EngineeringRequirement("PSHH-101", "V-101", EngineeringRequirement.Type.TRIP,
        "Synthetic high-pressure shutdown", "Synthetic software regression only"));
    Path source = temporaryDirectory.resolve("governed-source.xml");
    Dexpi201XmlWriter.writeAndAssess(project.getEngineeringProcessSystem(), source.toFile(), metadata());
    int sourcePipes = Dexpi20ModelInspector.inspect(source).getPipingConnectionCount();
    int sourceBoundaries = Dexpi20ModelInspector.inspect(source).getOffPageConnectorCount();
    Path file = temporaryDirectory.resolve("governed.xml");
    Dexpi20ConformanceAssessment.Report report = DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    Document document = parse(file);
    assertEquals("Plant/Instrumentation.ProcessSafetyFunction",
        object(document, "Plant/Instrumentation.ProcessSafetyFunction").getAttribute("type"));
    assertEquals("Plant/ProcessEquipment.InstrumentNozzle",
        object(document, "Plant/ProcessEquipment.InstrumentNozzle").getAttribute("type"));
    assertTrue(Dexpi20ModelInspector.inspect(file).getInstrumentationFunctionCount() > 0);
    assertEquals(sourcePipes, Dexpi20ModelInspector.inspect(file).getPipingConnectionCount());
    assertEquals(sourceBoundaries, Dexpi20ModelInspector.inspect(file).getOffPageConnectorCount());
    String projection = new String(
        Files.readAllBytes(file.resolveSibling(file.getFileName().toString() + ".engineering-projection.json")),
        StandardCharsets.UTF_8);
    assertTrue(projection.contains("PSHH-101"));
    assertTrue(projection.contains("NO_CONTROLLED_FINAL_ELEMENT_ROUTE"));
    assertTrue(projection.contains("HOLD"));
  }

  /**
   * Requires credible relief evidence and a resolved disposal boundary before adding a relief proposal.
   *
   * @throws Exception if controlled relief export fails
   */
  @Test
  void controlledReliefProposalHasNoUnprotectedParallelBypass() throws Exception {
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Controlled relief 201", process()).build();
    project.addRequirement(new EngineeringRequirement("RELIEF-EXPLICIT-V101", "V-101",
        EngineeringRequirement.Type.RELIEF, "Synthetic controlled relief", "Software test only"));
    project.addReliefScenarioBasis(new neqsim.process.engineering.ReliefScenarioBasis("V-101")
        .require(neqsim.process.safety.overpressure.ReliefCause.BLOCKED_OUTLET)
        .setHazardReviewReference("SYNTHETIC-REVIEW").addEvidenceReference("SYNTHETIC-CAUSE-EVIDENCE"));
    project.addBoundary(new neqsim.process.engineering.EngineeringBoundary("FLARE-CONTROLLED", "V-101",
        neqsim.process.engineering.EngineeringBoundary.Type.FLARE_HEADER)
        .resolve("SYNTHETIC-FLARE-DRAWING", "SYNTHETIC-BOUNDARY-EVIDENCE"));
    Path source = temporaryDirectory.resolve("relief-source.xml");
    Dexpi201XmlWriter.writeAndAssess(project.getEngineeringProcessSystem(), source.toFile(), metadata());
    Path file = temporaryDirectory.resolve("controlled-relief.xml");
    Dexpi20ConformanceAssessment.Report report = DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    int proposals = 0;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getType() == EngineeringRequirement.Type.RELIEF) {
        proposals++;
      }
    }
    assertEquals(Dexpi20ModelInspector.inspect(source).getPipingConnectionCount() + 2 * proposals,
        Dexpi20ModelInspector.inspect(file).getPipingConnectionCount());
    assertEquals(Dexpi20ModelInspector.inspect(source).getOffPageConnectorCount() + proposals,
        Dexpi20ModelInspector.inspect(file).getOffPageConnectorCount());
    String projection = new String(
        Files.readAllBytes(file.resolveSibling(file.getFileName().toString() + ".engineering-projection.json")),
        StandardCharsets.UTF_8);
    assertTrue(projection.contains("CONTROLLED_RELIEF_PROPOSAL_REQUIRES_EXECUTABLE_MODEL_BINDING"));
    assertTrue(projection.contains("HOLD"));
  }

  /**
   * Preserves a solved split/recycle facility as real mixer/tee connectivity.
   *
   * @throws Exception if projection fails
   */
  @Test
  void projectsBranchedRecycleWithDistinctOwnedPorts() throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 40.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FRESH-FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Stream tear = new Stream("RETURN-TEAR", fluid.clone());
    // Analytic initialization: recycle = fresh feed * 0.2 / (1 - 0.2).
    tear.setFlowRate(250.0, "kg/hr");
    neqsim.process.equipment.mixer.Mixer mixer = new neqsim.process.equipment.mixer.Mixer("MX-101");
    mixer.addStream(feed);
    mixer.addStream(tear);
    neqsim.process.equipment.splitter.Splitter splitter = new neqsim.process.equipment.splitter.Splitter("TEE-101",
        mixer.getOutletStream(), 2);
    splitter.setSplitFactors(new double[] {0.8, 0.2});
    neqsim.process.equipment.util.Recycle recycle = new neqsim.process.equipment.util.Recycle("RECYCLE-SOLVER");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(tear);
    ProcessSystem process = new ProcessSystem();
    process.setName("Branched recycle regression");
    process.add(feed);
    process.add(tear);
    process.add(mixer);
    process.add(splitter);
    process.add(recycle);
    assertTrue(process.runUntilConverged(100));
    assertEquals(feed.getFlowRate("kg/hr"), splitter.getSplitStream(0).getFlowRate("kg/hr"), 1.0e-5);
    double sourceFlow = mixer.getOutletStream().getFlowRate("kg/hr");
    Path file = temporaryDirectory.resolve("recycle-plant.xml");
    Dexpi20ConformanceAssessment.Report report = Dexpi201XmlWriter.writeAndAssess(process, file.toFile(), metadata());
    assertTrue(report.isSchemaAndProfileConformant(), report.getErrors().toString());
    Document document = parse(file);
    Element mixerItem = object(document, "Plant/ProcessEquipment.Mixer");
    Element teeItem = object(document, "Plant/Piping.PipeTee");
    assertEquals(3, teeItem.getElementsByTagName("Object").getLength());
    assertEquals(4, Dexpi20ModelInspector.inspect(file).getPipingConnectionCount());
    assertEquals(2, Dexpi20ModelInspector.inspect(file).getOffPageConnectorCount());
    java.util.Map<String, Element> identities = new java.util.LinkedHashMap<String, Element>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element item = (Element) objects.item(index);
      identities.put(item.getAttribute("id"), item);
    }
    java.util.Set<String> mixerInletItems = new java.util.LinkedHashSet<String>();
    int mixerToTee = 0;
    int teeToMixer = 0;
    for (int index = 0; index < objects.getLength(); index++) {
      Element pipe = (Element) objects.item(index);
      if (!"Plant/Piping.Pipe".equals(pipe.getAttribute("type"))) {
        continue;
      }
      java.util.Map<String, String> endpoints = new java.util.LinkedHashMap<String, String>();
      NodeList references = pipe.getElementsByTagName("References");
      for (int refIndex = 0; refIndex < references.getLength(); refIndex++) {
        Element reference = (Element) references.item(refIndex);
        endpoints.put(reference.getAttribute("property"), reference.getAttribute("objects").substring(1));
      }
      String source = physicalOwner(identities.get(endpoints.get("SourceItem")));
      String target = physicalOwner(identities.get(endpoints.get("TargetItem")));
      if (source.equals(mixerItem.getAttribute("id")) && target.equals(teeItem.getAttribute("id"))) {
        mixerToTee++;
      }
      if (source.equals(teeItem.getAttribute("id")) && target.equals(mixerItem.getAttribute("id"))) {
        teeToMixer++;
      }
      if (target.equals(mixerItem.getAttribute("id"))) {
        mixerInletItems.add(endpoints.get("TargetItem"));
      }
    }
    assertEquals(1, mixerToTee);
    assertEquals(1, teeToMixer);
    assertEquals(2, mixerInletItems.size());
    assertFalse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("RECYCLE-SOLVER"));
    String evidence = new String(
        Files.readAllBytes(file.resolveSibling(file.getFileName().toString() + ".process-projection.json")),
        StandardCharsets.UTF_8);
    assertTrue(evidence.contains("RECYCLE-SOLVER"));
    assertTrue(evidence.contains("DIRECTED_STREAM_IDENTITY_ALIAS"));
    assertEquals(sourceFlow, mixer.getOutletStream().getFlowRate("kg/hr"), 0.0);
    Path repeated = temporaryDirectory.resolve("recycle-repeated.xml");
    Dexpi201XmlWriter.writeAndAssess(process, repeated.toFile(), metadata());
    assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(repeated));
    IllegalArgumentException processGap = assertThrows(IllegalArgumentException.class, () -> Dexpi201ProcessModelWriter
        .writeAndAssess(process, temporaryDirectory.resolve("unsupported-recycle-process.xml").toFile(), metadata()));
    assertTrue(processGap.getMessage().contains("no reviewed physical alias projection"));
  }

  /**
   * Resolves an endpoint nozzle to its physical equipment owner.
   *
   * @param item endpoint item
   * @return owner identity
   */
  private static String physicalOwner(Element item) {
    if (item.getAttribute("type").endsWith("Nozzle")) {
      return ((Element) item.getParentNode().getParentNode()).getAttribute("id");
    }
    return item.getAttribute("id");
  }

  /**
   * Keeps pure calculation helpers out of physical drawings and reports the omission.
   *
   * @throws Exception if export fails
   */
  @Test
  void omitsCalculatorAndAdjusterWithoutInventingEquipment() throws Exception {
    ProcessSystem process = process();
    Path source = temporaryDirectory.resolve("helper-free.xml");
    Dexpi201XmlWriter.writeAndAssess(process, source.toFile(), metadata());
    process.add(new neqsim.process.equipment.util.Calculator("CALCULATION-HELPER"));
    process.add(new neqsim.process.equipment.util.Adjuster("ADJUSTMENT-HELPER"));
    Path file = temporaryDirectory.resolve("with-helpers.xml");
    Dexpi201XmlWriter.writeAndAssess(process, file.toFile(), metadata());
    assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(file));
    String evidence = new String(
        Files.readAllBytes(file.resolveSibling(file.getFileName().toString() + ".process-projection.json")),
        StandardCharsets.UTF_8);
    assertTrue(evidence.contains("CALCULATION-HELPER"));
    assertTrue(evidence.contains("ADJUSTMENT-HELPER"));
    assertTrue(evidence.contains("RETAINED_IN_EXECUTABLE_SIMULATION_MODEL_ONLY"));
  }

  /**
   * Rejects branch and convergence structures without a reviewed physical projection.
   *
   * @throws Exception if fixture setup fails
   */
  @Test
  void rejectsUnsupportedFanoutAndAmbiguousRecycleAliases() throws Exception {
    ProcessSystem process = process();
    Stream feed = (Stream) process.getUnit("FEED");
    neqsim.process.equipment.splitter.Splitter splitter = new neqsim.process.equipment.splitter.Splitter(
        "UNSUPPORTED-THREE-WAY", feed, 3);
    process.add(splitter);
    Path file = temporaryDirectory.resolve("unsupported.xml");
    IllegalArgumentException fanout = assertThrows(IllegalArgumentException.class,
        () -> Dexpi201XmlWriter.writeAndAssess(process, file.toFile(), metadata()));
    assertTrue(fanout.getMessage().contains("two-outlet Splitter"));
    ProcessSystem ambiguous = process();
    neqsim.process.equipment.util.Recycle recycle = new neqsim.process.equipment.util.Recycle("AMBIGUOUS-TEAR");
    recycle.addStream(((Separator) ambiguous.getUnit("V-101")).getGasOutStream());
    recycle.addStream(((Separator) ambiguous.getUnit("V-101")).getLiquidOutStream());
    recycle.setOutletStream((Stream) ambiguous.getUnit("FEED"));
    ambiguous.add(recycle);
    java.io.IOException alias = assertThrows(java.io.IOException.class,
        () -> Dexpi201XmlWriter.writeAndAssess(ambiguous, file.toFile(), metadata()));
    assertTrue(alias.getMessage().contains("one actual inlet and outlet"));
  }

  /**
   * Builds a converged synthetic vessel process.
   *
   * @return process fixture
   */
  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 40.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("n-heptane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator separator = new Separator("V-101", feed);
    ProcessSystem process = new ProcessSystem();
    process.setName("Synthetic 201 test");
    process.add(feed);
    process.add(separator);
    ThrottlingValve valve = new ThrottlingValve("PV-101", separator.getGasOutStream());
    valve.setOutletPressure(35.0);
    process.add(valve);
    process.run();
    return process;
  }

  /**
   * Provides explicit synthetic export provenance.
   *
   * @return controlled metadata
   */
  private static Dexpi20PlantExportMetadata metadata() {
    return Dexpi20PlantExportMetadata.builder("2026-11-03T00:00:00Z", "NeqSim", "Equinor", "test")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_NAME, "Synthetic software test").build();
  }

  /**
   * Parses a local test exchange.
   *
   * @param file XML file
   * @return parsed XML
   * @throws Exception if parsing fails
   */
  private static Document parse(Path file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(file.toFile());
  }

  /**
   * Finds an exact object type.
   *
   * @param document parsed exchange
   * @param type exact type
   * @return matching object
   */
  private static Element object(Document document, String type) {
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (type.equals(object.getAttribute("type"))) {
        return object;
      }
    }
    throw new AssertionError("Missing object " + type);
  }
}
