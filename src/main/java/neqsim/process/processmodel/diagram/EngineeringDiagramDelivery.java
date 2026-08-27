package neqsim.process.processmodel.diagram;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20ProcessModelPackageWriter;
import neqsim.process.processmodel.dexpi.Dexpi20ProcessModelWriter;
import neqsim.process.processmodel.dexpi.Dexpi20ProcessTopologyAssessment;

/**
 * Publishes one assessed, deterministic engineering-diagram delivery from a canonical NeqSim process model.
 *
 * <p>
 * A delivery contains the controlled semantic document JSON, native SVG sheets, one native multi-page PDF, a DEXPI 2.0
 * Process exchange, and a machine-readable evidence manifest. A {@link ProcessSystem} produces one native DEXPI Process
 * XML file. A multi-area {@link ProcessModel} produces the existing deterministic NeqSim package of native per-area
 * DEXPI Process files and explicit plant-wide connection diagnostics; the package is not represented as a native
 * whole-plant DEXPI profile.
 * </p>
 *
 * <p>
 * Publication is fail-closed: the destination must not already exist, output is first written to a sibling staging
 * directory, and no delivery is published when the controlled document, native rendering, or bounded DEXPI assessment
 * has an error. Generated PFDs are simulation-driven engineering proposals. A P&amp;ID profile remains review-required
 * and is never approved for design or construction by this class. No ISO 10628 conformance is claimed.
 * </p>
 *
 * <p>
 * This opt-in facade does not change legacy DOT/Graphviz, Classic, Proteus, DEXPI, document-model, or renderer APIs.
 * </p>
 */
public final class EngineeringDiagramDelivery {
  private static final String SCHEMA_VERSION = "neqsim_engineering_diagram_delivery.v1";
  private static final String DOCUMENT_FILE = "document-set.json";
  private static final String PDF_FILE = "drawing-set.pdf";
  private static final String MANIFEST_FILE = "delivery-manifest.json";
  private static final String PROCESS_SYSTEM_DEXPI_FILE = "dexpi-process.xml";
  private static final String PROCESS_MODEL_DEXPI_FILE = "dexpi-process-model.zip";

  private EngineeringDiagramDelivery() {
  }

  /** Immutable controlled delivery request. */
  public static final class Request {
    private final String plantId;
    private final String revision;
    private final String drawingNumber;
    private final String title;
    private final ContentProfile contentProfile;
    private final String operatingCaseId;
    private final NativeEngineeringDiagramRenderer.SheetFormat sheetFormat;
    private final NativeEngineeringDiagramRenderer.RoutingMode routingMode;
    private final EngineeringDiagramDesignationRegister designationRegister;
    private final EngineeringDiagramLayoutRegister layoutRegister;
    private final EngineeringDiagramConventionRegister conventionRegister;

    private Request(Builder builder) {
      plantId = requireText(builder.plantId, "plantId");
      revision = requireText(builder.revision, "revision");
      drawingNumber = requireText(builder.drawingNumber, "drawingNumber");
      title = requireText(builder.title, "title");
      if (builder.contentProfile == null) {
        throw new IllegalArgumentException("contentProfile must not be null");
      }
      contentProfile = builder.contentProfile;
      operatingCaseId = builder.operatingCaseId == null ? null
          : requireText(builder.operatingCaseId, "operatingCaseId");
      sheetFormat = builder.sheetFormat;
      routingMode = builder.routingMode;
      designationRegister = builder.designationRegister;
      layoutRegister = builder.layoutRegister;
      conventionRegister = builder.conventionRegister;
    }

    /**
     * Starts a request for one controlled delivery.
     *
     * @param plantId persistent plant or project identity
     * @param revision controlled source-model revision
     * @param drawingNumber controlled drawing number
     * @param title drawing-set title
     * @param contentProfile BFD, PFD, or review-required PID proposal content
     * @return mutable builder with deterministic native defaults
     */
    public static Builder builder(String plantId, String revision, String drawingNumber, String title,
        ContentProfile contentProfile) {
      return new Builder(plantId, revision, drawingNumber, title, contentProfile);
    }

    /** Mutable builder for {@link Request}. */
    public static final class Builder {
      private final String plantId;
      private final String revision;
      private final String drawingNumber;
      private final String title;
      private final ContentProfile contentProfile;
      private String operatingCaseId;
      private NativeEngineeringDiagramRenderer.SheetFormat sheetFormat = NativeEngineeringDiagramRenderer.SheetFormat.A3_LANDSCAPE;
      private NativeEngineeringDiagramRenderer.RoutingMode routingMode = NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL;
      private EngineeringDiagramDesignationRegister designationRegister = new EngineeringDiagramDesignationRegister();
      private EngineeringDiagramLayoutRegister layoutRegister = new EngineeringDiagramLayoutRegister();
      private EngineeringDiagramConventionRegister conventionRegister = new EngineeringDiagramConventionRegister();

      private Builder(String plantId, String revision, String drawingNumber, String title,
          ContentProfile contentProfile) {
        this.plantId = plantId;
        this.revision = revision;
        this.drawingNumber = drawingNumber;
        this.title = title;
        this.contentProfile = contentProfile;
      }

      /**
       * @param value stable successfully-run operating-case identity
       * @return this builder
       */
      public Builder operatingCaseId(String value) {
        operatingCaseId = value;
        return this;
      }

      /**
       * @param value controlled sheet format
       * @return this builder
       */
      public Builder sheetFormat(NativeEngineeringDiagramRenderer.SheetFormat value) {
        sheetFormat = requireNonNull(value, "sheetFormat");
        return this;
      }

      /**
       * @param value native connection-routing behavior
       * @return this builder
       */
      public Builder routingMode(NativeEngineeringDiagramRenderer.RoutingMode value) {
        routingMode = requireNonNull(value, "routingMode");
        return this;
      }

      /**
       * @param value reviewed project designation evidence
       * @return this builder
       */
      public Builder designationRegister(EngineeringDiagramDesignationRegister value) {
        designationRegister = requireNonNull(value, "designationRegister");
        return this;
      }

      /**
       * @param value controlled manual sheet and layout evidence
       * @return this builder
       */
      public Builder layoutRegister(EngineeringDiagramLayoutRegister value) {
        layoutRegister = requireNonNull(value, "layoutRegister");
        return this;
      }

      /**
       * @param value evidence-bearing project symbol conventions
       * @return this builder
       */
      public Builder conventionRegister(EngineeringDiagramConventionRegister value) {
        conventionRegister = requireNonNull(value, "conventionRegister");
        return this;
      }

      /** @return validated immutable request */
      public Request build() {
        return new Request(this);
      }
    }
  }

  /** Immutable evidence for one published delivery. */
  public static final class Report {
    private final Path directory;
    private final String sourceScope;
    private final EngineeringDiagramDocumentSet documentSet;
    private final NativeEngineeringDiagramRenderer.Result rendering;
    private final Map<String, Object> dexpiAssessment;
    private final boolean dexpiComplete;
    private final Map<String, Artifact> artifacts;
    private final String fingerprint;

    private Report(Path directory, String sourceScope, EngineeringDiagramDocumentSet documentSet,
        NativeEngineeringDiagramRenderer.Result rendering, Map<String, Object> dexpiAssessment, boolean dexpiComplete,
        Map<String, Artifact> artifacts) {
      this.directory = directory;
      this.sourceScope = sourceScope;
      this.documentSet = documentSet;
      this.rendering = rendering;
      this.dexpiAssessment = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(dexpiAssessment));
      this.dexpiComplete = dexpiComplete;
      this.artifacts = Collections.unmodifiableMap(new LinkedHashMap<String, Artifact>(artifacts));
      fingerprint = fingerprint(toMapWithoutFingerprint());
    }

    /** @return published delivery directory */
    public Path getDirectory() {
      return directory;
    }

    /** @return canonical controlled document set used by every view */
    public EngineeringDiagramDocumentSet getDocumentSet() {
      return documentSet;
    }

    /** @return deterministic native rendering result */
    public NativeEngineeringDiagramRenderer.Result getRendering() {
      return rendering;
    }

    /** @return immutable relative-path-to-artifact evidence map */
    public Map<String, Artifact> getArtifacts() {
      return artifacts;
    }

    /** @return deterministic manifest content fingerprint */
    public String getFingerprint() {
      return fingerprint;
    }

    /** @return true when every bounded document, rendering, and DEXPI gate passed */
    public boolean isComplete() {
      return documentSet.isValid() && rendering.isComplete() && dexpiComplete;
    }

    /** @return deterministic machine-readable evidence, excluding the environment-specific output directory */
    public Map<String, Object> toMap() {
      Map<String, Object> result = toMapWithoutFingerprint();
      result.put("fingerprint", fingerprint);
      return result;
    }

    /** @return pretty-printed deterministic manifest JSON */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }

    private Map<String, Object> toMapWithoutFingerprint() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", SCHEMA_VERSION);
      result.put("sourceScope", sourceScope);
      result.put("plantId", documentSet.getPlantId());
      result.put("revision", documentSet.getRevision());
      result.put("documentSetId", documentSet.getId());
      result.put("sourceGraphFingerprint", documentSet.getSourceGraphFingerprint());
      result.put("documentFingerprint", documentSet.toMap().get("fingerprint"));
      result.put("documentStatus", documentSet.getStatus().name());
      result.put("issuePurpose", documentSet.getIssuePurpose().name());
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("iso10628ConformanceClaimed", Boolean.FALSE);
      result.put("completeWithinDeclaredScope", Boolean.valueOf(isComplete()));
      result.put("visualFingerprintsBySheetId",
          new LinkedHashMap<String, String>(rendering.getVisualFingerprintsBySheetId()));
      List<Map<String, Object>> rendererDiagnostics = new ArrayList<Map<String, Object>>();
      for (NativeEngineeringDiagramRenderer.Diagnostic diagnostic : rendering.getDiagnostics()) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("severity", diagnostic.getSeverity().name());
        item.put("code", diagnostic.getCode());
        item.put("message", diagnostic.getMessage());
        item.put("subjectId", diagnostic.getSubjectId());
        rendererDiagnostics.add(item);
      }
      result.put("rendererDiagnostics", rendererDiagnostics);
      result.put("dexpiAssessment", dexpiAssessment);
      List<Map<String, Object>> artifactMaps = new ArrayList<Map<String, Object>>();
      for (Artifact artifact : artifacts.values()) {
        artifactMaps.add(artifact.toMap());
      }
      result.put("artifacts", artifactMaps);
      return result;
    }
  }

  /** Immutable content evidence for one published artifact. */
  public static final class Artifact {
    private final String relativePath;
    private final String mediaType;
    private final long sizeBytes;
    private final String sha256;

    private Artifact(String relativePath, String mediaType, byte[] content) {
      this.relativePath = relativePath;
      this.mediaType = mediaType;
      sizeBytes = content.length;
      sha256 = sha256(content);
    }

    /** @return delivery-root-relative artifact path */
    public String getRelativePath() {
      return relativePath;
    }

    /** @return artifact media type */
    public String getMediaType() {
      return mediaType;
    }

    /** @return exact artifact size in bytes */
    public long getSizeBytes() {
      return sizeBytes;
    }

    /** @return exact artifact content SHA-256 */
    public String getSha256() {
      return sha256;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("relativePath", relativePath);
      result.put("mediaType", mediaType);
      result.put("sizeBytes", Long.valueOf(sizeBytes));
      result.put("sha256", sha256);
      return result;
    }
  }

  /**
   * Publishes a single-area ProcessSystem delivery.
   *
   * @param processSystem source simulation topology
   * @param directory new destination directory
   * @param request controlled delivery request
   * @return published delivery evidence
   * @throws IOException when staging, assessed export, or publication fails
   */
  public static Report deliver(ProcessSystem processSystem, Path directory, Request request) throws IOException {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    requireArguments(directory, request);
    EngineeringDiagramDocumentSet documents = documentSet(processSystem, request);
    return publish(directory, "PROCESS_SYSTEM", documents, request, new DexpiPublication() {
      @Override
      public DexpiEvidence write(Path stagingDirectory) throws IOException {
        Path target = stagingDirectory.resolve(PROCESS_SYSTEM_DEXPI_FILE);
        Dexpi20ProcessTopologyAssessment.Report assessment = request.operatingCaseId == null
            ? Dexpi20ProcessModelWriter.writeAndAssessTopology(processSystem, target.toFile(), request.plantId,
                request.revision)
            : Dexpi20ProcessModelWriter.writeAndAssessTopology(processSystem, target.toFile(), request.plantId,
                request.revision, request.operatingCaseId);
        return new DexpiEvidence(PROCESS_SYSTEM_DEXPI_FILE, "application/xml", assessment.toMap(),
            assessment.isSchemaProfileAndSupportedTopologyValid());
      }
    });
  }

  /**
   * Publishes a multi-area ProcessModel delivery.
   *
   * @param processModel source multi-area simulation topology
   * @param directory new destination directory
   * @param request controlled delivery request
   * @return published delivery evidence
   * @throws IOException when staging, assessed export, or publication fails
   */
  public static Report deliver(ProcessModel processModel, Path directory, Request request) throws IOException {
    if (processModel == null) {
      throw new IllegalArgumentException("processModel must not be null");
    }
    requireArguments(directory, request);
    EngineeringDiagramDocumentSet documents = documentSet(processModel, request);
    return publish(directory, "PROCESS_MODEL", documents, request, new DexpiPublication() {
      @Override
      public DexpiEvidence write(Path stagingDirectory) throws IOException {
        Path target = stagingDirectory.resolve(PROCESS_MODEL_DEXPI_FILE);
        Dexpi20ProcessModelPackageWriter.Report assessment = request.operatingCaseId == null
            ? Dexpi20ProcessModelPackageWriter.writeAndAssess(processModel, target.toFile(), request.plantId,
                request.revision)
            : Dexpi20ProcessModelPackageWriter.writeAndAssess(processModel, target.toFile(), request.plantId,
                request.revision, request.operatingCaseId);
        return new DexpiEvidence(PROCESS_MODEL_DEXPI_FILE, "application/zip", assessment.toMap(),
            assessment.isComplete());
      }
    });
  }

  private static Report publish(Path requestedDirectory, String sourceScope, EngineeringDiagramDocumentSet documents,
      Request request, DexpiPublication dexpiPublication) throws IOException {
    Path directory = requestedDirectory.toAbsolutePath().normalize();
    if (Files.exists(directory)) {
      throw new IllegalArgumentException("delivery directory must not already exist: " + directory);
    }
    Path parent = directory.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("delivery directory must have a parent");
    }
    Files.createDirectories(parent);
    Path staging = Files.createTempDirectory(parent, ".neqsim-diagram-delivery-");
    try {
      NativeEngineeringDiagramRenderer.Result rendering = new NativeEngineeringDiagramRenderer(documents,
          request.sheetFormat, request.conventionRegister, request.routingMode).render();
      if (!documents.isValid() || !rendering.isComplete()) {
        throw new IOException("Engineering-diagram delivery failed controlled document or rendering gates");
      }

      Map<String, Artifact> artifacts = new TreeMap<String, Artifact>();
      writeArtifact(staging, DOCUMENT_FILE, "application/json", documents.toJson().getBytes(StandardCharsets.UTF_8),
          artifacts);
      writeArtifact(staging, PDF_FILE, "application/pdf", rendering.getPdf(), artifacts);
      for (Map.Entry<String, String> svg : rendering.getSvgBySheetId().entrySet()) {
        String relativePath = "svg/" + safeFileName(svg.getKey()) + "-" + sha256(svg.getKey()).substring(0, 12)
            + ".svg";
        writeArtifact(staging, relativePath, "image/svg+xml", svg.getValue().getBytes(StandardCharsets.UTF_8),
            artifacts);
      }

      DexpiEvidence dexpi = dexpiPublication.write(staging);
      byte[] dexpiBytes = Files.readAllBytes(staging.resolve(dexpi.relativePath));
      artifacts.put(dexpi.relativePath, new Artifact(dexpi.relativePath, dexpi.mediaType, dexpiBytes));
      if (!dexpi.complete) {
        throw new IOException("Engineering-diagram delivery failed the bounded DEXPI assessment");
      }

      Report stagedReport = new Report(directory, sourceScope, documents, rendering, dexpi.assessment, dexpi.complete,
          artifacts);
      if (!stagedReport.isComplete()) {
        throw new IOException("Engineering-diagram delivery is incomplete within its declared scope");
      }
      Files.write(staging.resolve(MANIFEST_FILE), stagedReport.toJson().getBytes(StandardCharsets.UTF_8));
      movePublishedDirectory(staging, directory);
      return new Report(directory, sourceScope, documents, rendering, dexpi.assessment, dexpi.complete, artifacts);
    } catch (IOException ex) {
      deleteRecursively(staging);
      throw ex;
    } catch (RuntimeException ex) {
      deleteRecursively(staging);
      throw ex;
    }
  }

  private static EngineeringDiagramDocumentSet documentSet(ProcessSystem processSystem, Request request) {
    if (request.operatingCaseId == null) {
      return ProcessDiagramDocumentSetAdapter.fromProcessSystem(processSystem, request.plantId, request.revision,
          request.drawingNumber, request.title, request.contentProfile, request.designationRegister,
          request.layoutRegister);
    }
    return ProcessDiagramDocumentSetAdapter.fromProcessSystem(processSystem, request.plantId, request.revision,
        request.drawingNumber, request.title, request.contentProfile, request.operatingCaseId,
        request.designationRegister, request.layoutRegister);
  }

  private static EngineeringDiagramDocumentSet documentSet(ProcessModel processModel, Request request) {
    if (request.operatingCaseId == null) {
      return ProcessDiagramDocumentSetAdapter.fromProcessModel(processModel, request.plantId, request.revision,
          request.drawingNumber, request.title, request.contentProfile, request.designationRegister,
          request.layoutRegister);
    }
    return ProcessDiagramDocumentSetAdapter.fromProcessModel(processModel, request.plantId, request.revision,
        request.drawingNumber, request.title, request.contentProfile, request.operatingCaseId,
        request.designationRegister, request.layoutRegister);
  }

  private static void writeArtifact(Path staging, String relativePath, String mediaType, byte[] content,
      Map<String, Artifact> artifacts) throws IOException {
    Path target = staging.resolve(relativePath);
    Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(target, content);
    artifacts.put(relativePath, new Artifact(relativePath, mediaType, content));
  }

  private static void movePublishedDirectory(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(source, target);
    }
  }

  private static void deleteRecursively(Path path) {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try {
      if (Files.isDirectory(path)) {
        DirectoryStream<Path> children = Files.newDirectoryStream(path);
        try {
          for (Path child : children) {
            deleteRecursively(child);
          }
        } finally {
          children.close();
        }
      }
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort cleanup must not hide the original publication failure.
    }
  }

  private static void requireArguments(Path directory, Request request) {
    if (directory == null || request == null) {
      throw new IllegalArgumentException("directory and request must not be null");
    }
  }

  private static String safeFileName(String value) {
    String normalized = requireText(value, "sheetId").replaceAll("[^A-Za-z0-9._-]+", "-");
    normalized = normalized.replaceAll("^-+|-+$", "");
    return normalized.isEmpty() ? "sheet" : normalized;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be null or blank");
    }
    return value.trim();
  }

  private static <T> T requireNonNull(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    return value;
  }

  private static String fingerprint(Map<String, Object> value) {
    return sha256(new GsonBuilder().create().toJson(value));
  }

  private static String sha256(String value) {
    return sha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256(byte[] value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
      StringBuilder result = new StringBuilder();
      for (byte item : digest) {
        result.append(String.format("%02x", item & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", ex);
    }
  }

  private interface DexpiPublication {
    DexpiEvidence write(Path stagingDirectory) throws IOException;
  }

  private static final class DexpiEvidence {
    private final String relativePath;
    private final String mediaType;
    private final Map<String, Object> assessment;
    private final boolean complete;

    private DexpiEvidence(String relativePath, String mediaType, Map<String, Object> assessment, boolean complete) {
      this.relativePath = relativePath;
      this.mediaType = mediaType;
      this.assessment = assessment;
      this.complete = complete;
    }
  }
}
