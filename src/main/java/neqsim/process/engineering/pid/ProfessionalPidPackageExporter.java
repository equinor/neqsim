package neqsim.process.engineering.pid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import neqsim.process.dynamics.DynamicCapabilityReport;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringStandard;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.engineering.dexpi.Dexpi201PidMaterializer;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolConvention;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolShape;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphBuilder;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20ConformanceAssessment;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.processmodel.diagram.EngineeringDiagramPidRegisters;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer;
import neqsim.process.processmodel.diagram.ProcessDiagramDocumentSetAdapter;
import neqsim.process.processmodel.diagram.ProfessionalPidDiagramRenderer;

/**
 * Coordinates a DEXPI 2.0.1 P&amp;ID proposal, native drawings and its executable simulation handoff.
 *
 * <p>
 * The package preserves unresolved projection, engineering and simulation gaps. A saved runnable process does not turn
 * unbound proposals into implemented controls or safeguards. Drawing and named-tool acceptance remain explicit review
 * tasks.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ProfessionalPidPackageExporter {
  /** Prevents utility construction. */
  private ProfessionalPidPackageExporter() {
  }

  /**
   * Generates source-connected native ISO-basis A1 review drawings and an assessed DEXPI 2.0.1 package.
   *
   * @param project controlled engineering project with a converged process
   * @param model P&amp;ID proposal for that project
   * @param bindings explicit links to executable simulation elements
   * @param metadata caller-supplied DEXPI provenance and plant metadata
   * @param directory new package directory, which must not exist
   * @return published directory
   * @throws IOException if generation, serialization or publication fails
   */
  public static Path export(EngineeringProject project, PidDesignModel model, PidSimulationBindings bindings,
      Dexpi20PlantExportMetadata metadata, Path directory) throws IOException {
    return export(project, model, bindings, metadata, directory, project == null ? "" : project.getProjectId() + "-PID",
        proposedConventions(metadata), new EngineeringDiagramLayoutRegister());
  }

  /**
   * Generates a coordinated package with controlled drawing identity, symbols and persistent sheet/layout inputs.
   *
   * @param project controlled engineering project
   * @param model P&amp;ID proposal for the current designed process
   * @param bindings explicit simulation binding register
   * @param metadata controlled DEXPI export metadata
   * @param directory new package directory
   * @param drawingNumber controlled drawing number
   * @param conventions evidence-bearing symbol conventions
   * @param layouts persistent sheet assignments, positions and routes
   * @return absolute published directory
   * @throws IOException if generation or publication fails
   */
  public static Path export(EngineeringProject project, PidDesignModel model, PidSimulationBindings bindings,
      Dexpi20PlantExportMetadata metadata, Path directory, String drawingNumber,
      EngineeringDiagramConventionRegister conventions, EngineeringDiagramLayoutRegister layouts) throws IOException {
    if (project == null || model == null || bindings == null || metadata == null || directory == null
        || conventions == null || layouts == null) {
      throw new IllegalArgumentException(
          "project, model, bindings, metadata, directory and drawing inputs are required");
    }
    bindings.assertCurrent(project, model);
    boolean editionSelected = false;
    for (EngineeringStandard standard : project.getDesignBasis().getStandards()) {
      if ("DEXPI".equals(standard.getCode())) {
        if (!"2.0.1".equals(standard.getEdition())) {
          throw new IllegalArgumentException("Professional P&ID project must explicitly declare DEXPI 2.0.1");
        }
        editionSelected = true;
      }
    }
    if (!editionSelected) {
      throw new IllegalArgumentException("Professional P&ID project has no declared DEXPI 2.0.1 design basis");
    }
    ProcessSystem process = project.getEngineeringProcessSystem();
    if (!process.solved()) {
      throw new IllegalStateException("Run and converge the source process before generating the P&ID package");
    }
    Path target = directory.toAbsolutePath().normalize();
    if (Files.exists(target)) {
      throw new IllegalArgumentException("package directory must not already exist: " + target);
    }
    Files.createDirectories(target.getParent());
    Path staging = Files.createTempDirectory(target.getParent(), ".neqsim-professional-pid-");
    try {
      PidEngineeringPackageExporter.export(project, model, staging);
      Dexpi20ConformanceAssessment.Report conformance = DexpiEngineeringExporter.exportDexpi201(project,
          staging.resolve("plant.dexpi.xml"), metadata, false);
      Dexpi201PidMaterializer.Result projected = Dexpi201PidMaterializer.materialize(project, model, bindings,
          staging.resolve("plant.dexpi.xml"));
      conformance = projected.getAssessment();
      writeJson(staging.resolve("dexpi-2.0.1-conformance.json"), conformance.toJson());

      EngineeringGraph graph = EngineeringGraphBuilder.fromProject(project);
      EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
          project.getProjectId(), project.getRevision(), drawingNumber, project.getName(), ContentProfile.PID,
          new EngineeringDiagramDesignationRegister(), layouts);
      PidPhysicalProjection physicalProjection = PidPhysicalProjection.resolve(process, model, bindings);
      EngineeringDiagramPidRegisters registers = EngineeringDiagramPidRegisters.fromDocumentSet(documents, model,
          project, physicalProjection);
      NativeEngineeringDiagramRenderer renderer = new NativeEngineeringDiagramRenderer(documents,
          NativeEngineeringDiagramRenderer.SheetFormat.A3_LANDSCAPE, conventions,
          NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL, registers, true);
      ProfessionalPidDiagramRenderer professional = null;
      NativeEngineeringDiagramRenderer.Result rendering;
      if (layouts.getPinnedPositions().isEmpty() && layouts.getProtectedRoutes().isEmpty()
          && layouts.getAssignments().isEmpty() && layouts.getOverviewRegions().isEmpty()
          && layouts.getSheets().isEmpty()) {
        professional = new ProfessionalPidDiagramRenderer(project, model, bindings, documents, conventions,
            metadata.getExportDateTime());
        rendering = professional.render();
        writeJson(staging.resolve("pid-drawing-content-report.json"), professional.getContentReportJson());
        writeJson(staging.resolve("pid-physical-projection.json"), professional.getPhysicalProjection().toJson());
      } else {
        // Existing controlled layouts retain their legacy representation; they are not silently overridden.
        rendering = renderer.render();
      }
      Path drawings = staging.resolve("drawings");
      Files.createDirectories(drawings);
      Files.write(drawings.resolve("pid-drawing-set.pdf"), rendering.getPdf());
      int sheet = 1;
      Map<String, String> sheetFiles = new LinkedHashMap<String, String>();
      for (Map.Entry<String, String> svg : rendering.getSvgBySheetId().entrySet()) {
        String name = String.format(java.util.Locale.ROOT, "pid-sheet-%03d.svg", Integer.valueOf(sheet++));
        writeJson(drawings.resolve(name), svg.getValue());
        if (professional != null) {
          Map<String, Object> specification = professional.getLayoutSpecifications().get(svg.getKey());
          writeJson(drawings.resolve(name.replace(".svg", ".layout.json")),
              new GsonBuilder().setPrettyPrinting().create().toJson(specification));
        }
        sheetFiles.put(svg.getKey(), "drawings/" + name);
      }
      writeJson(staging.resolve("engineering-graph.json"), graph.toJson());
      writeJson(staging.resolve("pid-document-set.json"), documents.toJson());
      writeJson(staging.resolve("pid-engineering-registers.json"), registers.toJson());
      writeJson(staging.resolve("pid-simulation-bindings.json"), bindings.toJson());
      writeJson(staging.resolve("dynamic-capability-report.json"), DynamicCapabilityReport.from(process).toJson());
      if (!process.copy().saveToNeqsim(staging.resolve("simulation-model.neqsim").toString())) {
        throw new IOException("Could not persist the executable process model");
      }
      if (project.getLatestEngineeringSimulationResult() != null) {
        writeJson(staging.resolve("engineering-simulation-results.json"),
            project.getLatestEngineeringSimulationResult().toJson());
      }

      Map<String, Object> readiness = readiness(project, model, documents, registers, rendering, conformance,
          staging.resolve("plant.dexpi.xml"));
      readiness.put("sheetFiles", sheetFiles);
      readiness.put("drawingProfile",
          professional == null ? "CONTROLLED_LEGACY_LAYOUT_REVIEW" : "ISO_10628_BASIS_NATIVE");
      readiness.put("drawingContentReport", professional == null ? "NOT_ASSESSED" : "pid-drawing-content-report.json");
      readiness.put("isoConformanceStatus", "CONTROLLED_STANDARD_AND_DISCIPLINE_REVIEW_REQUIRED");
      readiness.put("nativePidProjection", projected.toMap());
      readiness.put("visualFingerprints", rendering.getVisualFingerprintsBySheetId());
      readiness.put("pfdOverview",
          exportPfdOverview(project, model, bindings, documents, conventions, metadata, drawings));
      writeJson(staging.resolve("professional-pid-readiness.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(readiness));
      // Replaces the old profile-specific report after replacing the native exchange file.
      writeJson(staging.resolve("interoperability-report.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(readiness.get("exchangeQualification")));
      DexpiEngineeringExporter.refreshPackageManifest(staging);
      bindings.assertCurrent(project, model);
      try {
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(staging, target);
      }
      return target;
    } finally {
      removeStaging(staging);
    }
  }

  /**
   * Writes a small source-only native PFD overview and its independent diagnostics.
   *
   * @param project source project
   * @param model proposal identities
   * @param bindings explicit executable bindings
   * @param documents source document graph
   * @param conventions review records
   * @param metadata controlled issue date
   * @param drawings output drawing directory
   * @return supported scope and actual generation evidence
   * @throws IOException if a drawing cannot be written
   */
  private static Map<String, Object> exportPfdOverview(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, EngineeringDiagramDocumentSet documents,
      EngineeringDiagramConventionRegister conventions, Dexpi20PlantExportMetadata metadata, Path drawings)
      throws IOException {
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    int primaryCount = 0;
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : project.getEngineeringProcessSystem()
        .getUnitOperations()) {
      if (!(unit instanceof neqsim.process.equipment.stream.StreamInterface)
          && !(unit instanceof neqsim.process.equipment.valve.ThrottlingValve)) {
        primaryCount++;
      }
    }
    report.put("supportedPrimaryUnitCount", "1_TO_4_FORWARD_FLOW_TRAIN");
    report.put("sourceGraphFingerprint", documents.getSourceGraphFingerprint());
    report.put("isoConformanceStatus", "CONTROLLED_STANDARD_AND_DISCIPLINE_REVIEW_REQUIRED");
    if (primaryCount < 1 || primaryCount > 4) {
      report.put("generationStatus", "OUTSIDE_NATIVE_OVERVIEW_SCOPE");
      return report;
    }
    ProfessionalPidDiagramRenderer renderer = new ProfessionalPidDiagramRenderer(project, model, bindings, documents,
        conventions, metadata.getExportDateTime());
    NativeEngineeringDiagramRenderer.Result result = renderer.renderProcessFlowOverview();
    Files.write(drawings.resolve("pfd-overview.pdf"), result.getPdf());
    for (Map.Entry<String, String> entry : result.getSvgBySheetId().entrySet()) {
      writeJson(drawings.resolve("pfd-overview.svg"), entry.getValue());
      writeJson(drawings.resolve("pfd-overview.layout.json"), new GsonBuilder().setPrettyPrinting().create()
          .toJson(renderer.getLayoutSpecifications().get(entry.getKey())));
    }
    report.put("generationStatus", result.isComplete() ? "GENERATED_FOR_REVIEW" : "INCOMPLETE_DRAWING");
    report.put("pdf", "drawings/pfd-overview.pdf");
    report.put("performedChecks", result.getPerformedChecks());
    report.put("diagnostics", result.getDiagnostics());
    report.put("visualFingerprints", result.getVisualFingerprintsBySheetId());
    return report;
  }

  /**
   * Creates transparent proposal conventions rather than suggesting a standards-qualified symbol catalogue.
   *
   * @param metadata caller-supplied provenance
   * @return proposed process-equipment convention
   */
  private static EngineeringDiagramConventionRegister proposedConventions(Dexpi20PlantExportMetadata metadata) {
    if (metadata == null) {
      throw new IllegalArgumentException("metadata is required");
    }
    return new EngineeringDiagramConventionRegister()
        .withConvention(new SymbolConvention(EngineeringNode.Kind.EQUIPMENT, SymbolShape.PROCESS_EQUIPMENT, "#111827",
            "#ffffff", "NEQSIM-NATIVE-PROCESS-EQUIPMENT-PROPOSAL", EvidenceState.PROPOSED, "", "",
            metadata.getExportDateTime(), "1"))
        .withConvention(new SymbolConvention(EngineeringNode.Kind.LINE, SymbolShape.LINE_TERMINAL, "#111827", "#ffffff",
            "NEQSIM-NATIVE-LINE-TERMINAL-PROPOSAL", EvidenceState.PROPOSED, "", "", metadata.getExportDateTime(), "1"));
  }

  /**
   * Keeps exchange, drawing, project and runtime readiness separate.
   *
   * @param project controlled project
   * @param model proposed P&amp;ID
   * @param documents actual controlled drawing document model
   * @param registers detailed drawing registers
   * @param rendering actual rendered result
   * @param conformance actual native exchange assessment
   * @param nativeFile exact assessed native file
   * @return machine-readable unresolved-scope evidence
   * @throws IOException if the native exchange cannot be inspected
   */
  private static Map<String, Object> readiness(EngineeringProject project, PidDesignModel model,
      EngineeringDiagramDocumentSet documents, EngineeringDiagramPidRegisters registers,
      NativeEngineeringDiagramRenderer.Result rendering, Dexpi20ConformanceAssessment.Report conformance,
      Path nativeFile) throws IOException {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_professional_pid_package.v1");
    result.put("projectId", project.getProjectId());
    result.put("revision", project.getRevision());
    result.put("dexpiVersion", "2.0.1");
    result.put("approvalStatus", "REVIEW_REQUIRED");
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("sourceModel", "simulation-model.neqsim");
    result.put("simulationBindings", "pid-simulation-bindings.json");
    result.put("pidElementCount", Integer.valueOf(model.getElements().size()));
    result.put("sheetCount", Integer.valueOf(rendering.getSvgBySheetId().size()));
    result.put("engineeringGapCount", Integer.valueOf(registers.getGapCount()));
    result.put("documentModelValid", Boolean.valueOf(documents.isValid()));
    result.put("pidStructurallyComplete",
        Boolean.valueOf(PidCompletenessValidator.validate(model).isStructurallyComplete()));
    result.put("drawingHasNoErrors", Boolean.valueOf(rendering.isComplete()));
    boolean drawingWarnings = false;
    for (NativeEngineeringDiagramRenderer.Diagnostic diagnostic : rendering.getDiagnostics()) {
      drawingWarnings |= diagnostic.getSeverity() == NativeEngineeringDiagramRenderer.Severity.WARNING;
    }
    result.put("drawingHasUnresolvedWarnings", Boolean.valueOf(drawingWarnings));
    result.put("drawingChecksPerformed", rendering.getPerformedChecks());
    result.put("drawingDiagnostics", rendering.getDiagnostics());
    result.put("visualInspection", "REQUIRED");
    Map<String, Object> exchange = new LinkedHashMap<String, Object>();
    exchange.put("dexpiVersion", "2.0.1");
    exchange.put("schemaAndSupportedProfileConformant", Boolean.valueOf(conformance.isSchemaAndProfileConformant()));
    exchange.put("namedCaeRoundTripStatus", "NOT_TESTED");
    exchange.put("fullPidSemanticEquivalence", "NOT_QUALIFIED");
    exchange.put("details", "dexpi-2.0.1-conformance.json");
    result.put("exchangeQualification", exchange);
    Path projection = nativeFile.resolveSibling(nativeFile.getFileName().toString() + ".engineering-projection.json");
    if (Files.isRegularFile(projection)) {
      result.put("nativeEngineeringProjectionEvidence", projection.getFileName().toString());
      result.put("nativeEngineeringProjection",
          JsonParser.parseString(new String(Files.readAllBytes(projection), StandardCharsets.UTF_8)));
    }
    List<String> missing = missingProposalTags(model, nativeFile);
    result.put("nativeUnmappedProposalTags", missing);
    result.put("nativeProposalConnectivityQualification", "REQUIRED");
    result.put("generationStatus",
        !conformance.isSchemaAndProfileConformant() ? "INCOMPLETE_NATIVE_EXCHANGE"
            : !documents.isValid() ? "INCOMPLETE_DOCUMENT_MODEL"
                : !missing.isEmpty() ? "INCOMPLETE_NATIVE_PROPOSAL_PROJECTION"
                    : !rendering.isComplete() || drawingWarnings ? "INCOMPLETE_DRAWING" : "GENERATED_FOR_REVIEW");
    result.put("dynamicExecution", "USE_PID_SIMULATION_SESSION_WITH_EXPLICIT_BINDINGS_AND_STRICT_PREFLIGHT");
    return result;
  }

  /**
   * Reports proposed tags absent from the native exchange without treating a tag match as connectivity proof.
   *
   * @param model source proposals
   * @param file native XML
   * @return deterministic absent-tag list
   * @throws IOException if secure XML inspection fails
   */
  private static List<String> missingProposalTags(PidDesignModel model, Path file) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      NodeList strings = factory.newDocumentBuilder().parse(file.toFile()).getElementsByTagName("String");
      java.util.Set<String> values = new java.util.LinkedHashSet<String>();
      for (int i = 0; i < strings.getLength(); i++) {
        values.add(strings.item(i).getTextContent());
      }
      List<String> missing = new ArrayList<String>();
      for (PidElement element : model.getElements()) {
        if (!values.contains(element.getTag())) {
          missing.add(element.getTag());
        }
      }
      return missing;
    } catch (Exception ex) {
      throw new IOException("Could not inspect native P&ID proposal coverage", ex);
    }
  }

  /**
   * Writes UTF-8 text, including JSON and SVG, without changing source objects.
   *
   * @param path output path
   * @param content exact text
   * @throws IOException if writing fails
   */
  private static void writeJson(Path path, String content) throws IOException {
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Removes only the unpublished staging directory, preserving a successfully published package.
   *
   * @param staging temporary package directory
   * @throws IOException if cleanup fails
   */
  private static void removeStaging(Path staging) throws IOException {
    if (!Files.exists(staging)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(staging)) {
      for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
        Files.deleteIfExists(path);
      }
    }
  }
}
