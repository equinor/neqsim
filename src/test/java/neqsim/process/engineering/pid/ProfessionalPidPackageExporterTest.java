package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.dexpi.Dexpi201XmlWriter;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the documented professional P&amp;ID handoff, including saved simulation and explicit unresolved scope.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProfessionalPidPackageExporterTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  /**
   * Retains the legacy builder edition and rejects an inconsistent modern package basis before writing files.
   *
   * @throws Exception if the rejected workflow unexpectedly writes a file
   */
  @Test
  void requiresAnExplicit201ProjectBasis() throws Exception {
    ProcessSystem process = process();
    EngineeringProject legacy = NorsokOffshoreEngineeringBuilder.from("Legacy edition", process).projectId("LEGACY-PID")
        .registerProposedInstruments(false).build();
    assertEquals("2.0", legacy.getDesignBasis().getStandards().get(0).getEdition());
    PidDesignModel pid = PidDesignSynthesizer.synthesize(legacy, new PidDesignBasis("COMPLETE-PROPOSALS", "20"),
        NorsokPidRuleCatalog.completeProposals());
    Path target = temporaryDirectory.resolve("wrong-edition");
    assertThrows(IllegalArgumentException.class, () -> ProfessionalPidPackageExporter.export(legacy, pid,
        PidSimulationBindings.from(legacy, pid), metadata(), target));
    assertFalse(Files.exists(target));
    assertThrows(IllegalArgumentException.class,
        () -> NorsokOffshoreEngineeringBuilder.from("Unknown edition", process, "3.0"));
    ProcessModel plant = new ProcessModel();
    plant.add("separation", process);
    assertEquals("2.0.1", NorsokOffshoreEngineeringBuilder.fromProcessModel("Area project", plant, false, "2.0.1")
        .get(0).getDesignBasis().getStandards().get(0).getEdition());
  }

  /**
   * Exercises the public workflow and independently inspects the resulting exchange, drawing and simulation files.
   *
   * @throws Exception if a required artifact cannot be generated or inspected
   */
  @Test
  void exportsGenuine201AndRunnableModelWithoutPromotingUnboundProposals() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder
        .from("Public separator P&ID reference", process, "2.0.1").projectId("PID-201-REFERENCE")
        .registerProposedInstruments(false).build().setRevision("A");
    PidDesignModel pid = PidDesignSynthesizer.synthesize(project, new PidDesignBasis("COMPLETE-PROPOSALS", "20"),
        NorsokPidRuleCatalog.completeProposals());
    PidSimulationBindings bindings = PidSimulationBindings.from(project, pid);
    String gasControlTag = pid.getElementsByType(PidElementType.CONTROL_VALVE).get(0).getTag();
    bindings.bindEquipment(gasControlTag, "20-PCV-001");
    int equipmentCount = process.getUnitOperations().size();
    int measurementCount = process.getMeasurementDevices().size();
    double pressure = process.getUnit("20-PCV-001").getOutletStreams().get(0).getPressure();
    Path target = ProfessionalPidPackageExporter.export(project, pid, bindings, metadata(),
        temporaryDirectory.resolve("pid"));

    assertTrue(Dexpi201XmlWriter.assess(target.resolve("plant.dexpi.xml")).isSchemaAndProfileConformant());
    String xml = text(target.resolve("plant.dexpi.xml"));
    assertTrue(xml.contains("https://data.dexpi.org/models/2.0.1/Plant.xml"));
    assertFalse(xml.contains("https://data.dexpi.org/models/2.0.0/Plant.xml"));
    assertTrue(text(target.resolve("drawings/pid-sheet-001.svg")).contains("<svg"));
    byte[] pdf = Files.readAllBytes(target.resolve("drawings/pid-drawing-set.pdf"));
    assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"));
    assertTrue(Files.size(target.resolve("simulation-model.neqsim")) > 0);
    ProcessSystem loaded = ProcessSystem.loadFromNeqsim(target.resolve("simulation-model.neqsim").toString());
    assertNotNull(loaded);
    assertNotNull(loaded.getUnit("20-PCV-001"));
    assertTrue(loaded.solved());
    EngineeringProject restored = NorsokOffshoreEngineeringBuilder.from("Restored P&ID reference", loaded, "2.0.1")
        .projectId(project.getProjectId()).registerProposedInstruments(false).build()
        .setRevision(project.getRevision());
    PidDesignModel restoredPid = PidDesignModel.fromJson(text(target.resolve("pid-design-model.json")));
    PidSimulationBindings restoredBindings = PidSimulationBindings.fromJson(restored, restoredPid,
        text(target.resolve("pid-simulation-bindings.json")));
    assertEquals("20-PCV-001", restoredBindings.getSimulationName(gasControlTag));
    assertTrue(new PidSimulationSession(restored, restoredPid, restoredBindings).runSteadyState().solved());
    JsonObject readiness = JsonParser.parseString(text(target.resolve("professional-pid-readiness.json")))
        .getAsJsonObject();
    assertEquals("2.0.1", readiness.get("dexpiVersion").getAsString());
    assertFalse(readiness.get("fitnessForConstruction").getAsBoolean());
    assertEquals("NOT_QUALIFIED",
        readiness.getAsJsonObject("exchangeQualification").get("fullPidSemanticEquivalence").getAsString());
    assertTrue(readiness.getAsJsonArray("nativeUnmappedProposalTags").size() > 0);
    assertEquals("INCOMPLETE_NATIVE_PROPOSAL_PROJECTION", readiness.get("generationStatus").getAsString());
    assertTrue(text(target.resolve("package-manifest.json")).contains("simulation-model.neqsim"));
    assertEquals(equipmentCount, process.getUnitOperations().size());
    assertEquals(measurementCount, process.getMeasurementDevices().size());
    assertEquals(pressure, process.getUnit("20-PCV-001").getOutletStreams().get(0).getPressure(), 1.0e-12);
    assertThrows(IllegalArgumentException.class,
        () -> ProfessionalPidPackageExporter.export(project, pid, bindings, metadata(), target));
  }

  /**
   * Creates retained synthetic drawing evidence when explicitly requested by a validation invocation.
   *
   * @throws Exception if reference publication fails
   */
  @Test
  void retainsReferencePackageWhenRequested() throws Exception {
    String output = System.getProperty("neqsim.professionalPidReferenceDirectory", "");
    if (output.isEmpty()) {
      return;
    }
    ProcessSystem process = process();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder
        .from("Synthetic separator review package", process, "2.0.1").projectId("PID-201-REFERENCE")
        .registerProposedInstruments(false).build().setRevision("A");
    PidDesignModel pid = PidDesignSynthesizer.synthesize(project, new PidDesignBasis("COMPLETE-PROPOSALS", "20"),
        NorsokPidRuleCatalog.completeProposals());
    Path result = ProfessionalPidPackageExporter.export(project, pid, PidSimulationBindings.from(project, pid),
        metadata(), Paths.get(output));
    assertTrue(Files.exists(result.resolve("drawings/pid-drawing-set.pdf")));
  }

  /**
   * Preserves diagnostic artifacts while preventing a stale manual layout from appearing complete.
   *
   * @throws Exception if diagnostic publication fails
   */
  @Test
  void invalidDrawingModelIsExplicitlyIncomplete() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Invalid layout regression", process, "2.0.1")
        .projectId("PID-201-REFERENCE").registerProposedInstruments(false).build().setRevision("A");
    PidDesignModel pid = PidDesignSynthesizer.synthesize(project, new PidDesignBasis("COMPLETE-PROPOSALS", "20"),
        NorsokPidRuleCatalog.completeProposals());
    EngineeringDiagramLayoutRegister layouts = new EngineeringDiagramLayoutRegister()
        .withPinnedPosition(new EngineeringDiagramLayoutRegister.PinnedPosition("equipment:removed", "plant", 50.0,
            50.0, EngineeringDiagramLayoutRegister.CoordinateUnit.MILLIMETRE, "stale synthetic layout",
            EngineeringDiagramLayoutRegister.EvidenceState.PROPOSED, "software regression", "2026-11-03T08:00:00+01:00",
            "A"));
    Path target = ProfessionalPidPackageExporter.export(project, pid, PidSimulationBindings.from(project, pid),
        metadata(), temporaryDirectory.resolve("invalid-layout"), "PID-INVALID-LAYOUT",
        new EngineeringDiagramConventionRegister(), layouts);
    JsonObject readiness = JsonParser.parseString(text(target.resolve("professional-pid-readiness.json")))
        .getAsJsonObject();
    assertFalse(readiness.get("documentModelValid").getAsBoolean());
    assertEquals("INCOMPLETE_DOCUMENT_MODEL", readiness.get("generationStatus").getAsString());
    assertTrue(Files.exists(target.resolve("pid-document-set.json")));
  }

  /**
   * Retains actual and boundary line-design values and one coordinated auxiliary-series line without approval.
   *
   * @throws Exception if the coordinated synthetic package cannot be generated or inspected
   */
  @Test
  void exportsProjectLineInputsAndAuxiliaryRouteRegisterWithoutApproval() throws Exception {
    ProcessSystem process = process();
    Separator separator = (Separator) process.getUnit("20-VG-001");
    separator.getGasOutStream().setName("20-GAS-TO-VALVE");
    process.getUnit("20-PCV-001").getOutletStreams().get(0).setName("20-GAS-PRODUCT");
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Synthetic line fidelity", process, "2.0.1")
        .projectId("PID-201-REFERENCE").registerProposedInstruments(false).build().setRevision("A");
    project.addLineDesignInput(syntheticLine("20-GAS-TO-VALVE", "20-VG-001"));
    project.addLineDesignInput(syntheticLine("20-GAS-PRODUCT", "20-PCV-001"));
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "SYNTHETIC-AUXILIARY");
    pid.add(auxiliarySeriesValve("AUX-BDV", PidElementType.BLOWDOWN_VALVE, 20));
    pid.add(auxiliarySeriesValve("AUX-XV", PidElementType.ISOLATION_VALVE, 10));
    Path target = ProfessionalPidPackageExporter.export(project, pid, PidSimulationBindings.from(project, pid),
        metadata(), temporaryDirectory.resolve("line-fidelity"));
    JsonObject registers = JsonParser.parseString(text(target.resolve("pid-engineering-registers.json")))
        .getAsJsonObject();
    JsonObject actual = registerLine(registers, "20-GAS-TO-VALVE");
    assertEquals("DN 50", actual.get("nominalPipeSize").getAsString());
    assertEquals("SYNTHETIC-CLASS", actual.get("pipingClass").getAsString());
    assertEquals(60.0, actual.get("designPressureBara").getAsDouble(), 0.0);
    assertEquals("SYNTHETIC-LINE-LIST-A", actual.getAsJsonArray("evidenceReferences").get(0).getAsString());
    assertEquals("REVIEW_REQUIRED", actual.get("qualificationStatus").getAsString());
    JsonObject product = registerLine(registers, "20-GAS-PRODUCT");
    assertEquals("ACTUAL_STREAM_BOUNDARY", product.get("lineOrigin").getAsString());
    assertEquals("20-PCV-001", product.get("sourceEquipmentTag").getAsString());
    assertFalse(product.has("semanticConnectionId"));
    JsonObject auxiliary = registerLine(registers, "SYNTHETIC-FLARE-ROUTE");
    assertEquals("AUX-XV", auxiliary.getAsJsonArray("sourceProposalTags").get(0).getAsString());
    assertEquals("AUX-BDV", auxiliary.getAsJsonArray("sourceProposalTags").get(1).getAsString());
    assertEquals("20-VG-001", auxiliary.get("physicalSourceEquipmentTag").getAsString());
    assertEquals("20-GAS-TO-VALVE", auxiliary.get("physicalSourceStreamTag").getAsString());
    assertEquals("SYNTHETIC-FLARE-HEADER", auxiliary.get("destinationTag").getAsString());
    assertEquals("HOLD", auxiliary.get("sizingStatus").getAsString());
    assertEquals("HOLD", auxiliary.get("pipingClassStatus").getAsString());
    assertFalse(auxiliary.has("semanticConnectionId"));
    JsonObject readiness = JsonParser.parseString(text(target.resolve("professional-pid-readiness.json")))
        .getAsJsonObject();
    assertEquals(registers.getAsJsonArray("gaps").size(), readiness.get("engineeringGapCount").getAsInt());
    assertEquals("REVIEW_REQUIRED", registers.get("qualificationStatus").getAsString());
    assertFalse(registers.get("fitnessForConstruction").getAsBoolean());
    assertFalse(readiness.get("fitnessForConstruction").getAsBoolean());
    assertTrue(text(target.resolve("pid-physical-projection.json")).contains("SYNTHETIC-FLARE-ROUTE"));
  }

  /**
   * Creates complete synthetic design data used only to verify package fidelity.
   *
   * @param tag actual stream designation
   * @param equipment associated actual source equipment
   * @return review-required synthetic line input
   */
  private static LineDesignInput syntheticLine(String tag, String equipment) {
    return new LineDesignInput(tag, equipment).setNominalPipeSize("DN 50").setPipingClass("SYNTHETIC-CLASS")
        .setSchedule("40").setMaterialGrade("SYNTHETIC-MATERIAL").setOuterDiameter(60.3, "mm")
        .setNominalWallThickness(3.91, "mm").setCorrosionAllowance(1.0, "mm").setDesignPressureBara(60.0)
        .setDesignTemperatureC(80.0).addEvidenceReference("SYNTHETIC-LINE-LIST-A");
  }

  /**
   * Creates one explicitly routed member of a synthetic gas-to-flare proposal series.
   *
   * @param tag proposed valve identity
   * @param type proposed valve category
   * @param order explicit series order
   * @return source-backed auxiliary proposal
   */
  private static PidElement auxiliarySeriesValve(String tag, PidElementType type, int order) {
    return new PidElement(tag, tag, type).equipment("20-VG-001").attribute("physicalSourceEquipmentTag", "20-VG-001")
        .attribute("physicalSourceStreamTag", "20-GAS-TO-VALVE").attribute("destinationTag", "SYNTHETIC-FLARE-HEADER")
        .attribute("physicalRouteId", "SYNTHETIC-FLARE-ROUTE").attribute("physicalRouteOrder", Integer.valueOf(order))
        .attribute("designEvidenceReference", "SYNTHETIC-AUX-A");
  }

  /**
   * Selects one exact line designation from the emitted register.
   *
   * @param registers emitted register object
   * @param lineTag exact material line designation
   * @return matching row
   */
  private static JsonObject registerLine(JsonObject registers, String lineTag) {
    JsonObject found = null;
    for (JsonElement candidate : registers.getAsJsonArray("lines")) {
      JsonObject row = candidate.getAsJsonObject();
      if (row.has("lineTag") && lineTag.equals(row.get("lineTag").getAsString())) {
        assertTrue(found == null, "Duplicate material line " + lineTag);
        found = row;
      }
    }
    assertNotNull(found, "Missing material line " + lineTag);
    return found;
  }

  /**
   * Builds a small, public, stream-connected separator/control-valve train.
   *
   * @return successfully solved reference process
   */
  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("n-heptane", 0.15);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator separator = new Separator("20-VG-001", feed);
    ThrottlingValve valve = new ThrottlingValve("20-PCV-001", separator.getGasOutStream());
    valve.setOutletPressure(30.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(valve);
    process.run();
    return process;
  }

  /**
   * Supplies explicit synthetic provenance rather than inventing project approvals.
   *
   * @return fixed reference metadata
   */
  private static Dexpi20PlantExportMetadata metadata() {
    return Dexpi20PlantExportMetadata
        .builder("2026-11-03T08:00:00+01:00", "NeqSim reference workflow", "NeqSim", "3.27.0-development")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "PID-201-REFERENCE")
        .build();
  }

  /**
   * Reads one generated UTF-8 artifact for independent assertions.
   *
   * @param file generated artifact
   * @return UTF-8 content
   * @throws Exception if reading fails
   */
  private static String text(Path file) throws Exception {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
