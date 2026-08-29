package neqsim.process.processmodel.diagram;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares two independently assessed engineering-diagram deliveries.
 *
 * <p>
 * The comparison consumes only complete {@link EngineeringDiagramDeliveryAssessment.Report} instances. It classifies
 * every declared artifact as added, removed, modified, or unchanged and maps changes onto deterministic
 * engineering-review scopes. Directory paths are deliberately excluded from the comparison fingerprint so equivalent
 * transferred copies produce identical evidence.
 * </p>
 *
 * <p>
 * A comparison does not reconstruct or execute a process model, repeat DEXPI semantic assessment, approve a drawing,
 * decide management of change, establish fitness for construction, or claim standards conformance.
 * </p>
 */
public final class EngineeringDiagramDeliveryComparison {
  private static final String SCHEMA = "neqsim_engineering_diagram_delivery_comparison.v1";

  private EngineeringDiagramDeliveryComparison() {
  }

  /** Overall comparison status. */
  public enum Status {
    IDENTICAL, CHANGED, REVISION_REUSE
  }

  /** Artifact-level change type. */
  public enum ChangeType {
    ADDED, REMOVED, MODIFIED, UNCHANGED
  }

  /** Controlled review scope implicated by changed delivery evidence. */
  public enum ReviewScope {
    CONTROLLED_DOCUMENT_SET, DELIVERY_MANIFEST, DEXPI_PROCESS_EXCHANGE, DEXPI_PROCESS_MODEL_PACKAGE, ENGINEERING_REVIEW,
    NATIVE_PDF, NATIVE_SVG
  }

  /** Diagnostic severity. */
  public enum Severity {
    INFO, WARNING, ERROR
  }

  /** Immutable comparison diagnostic. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subject;

    private Diagnostic(Severity severity, String code, String message, String subject) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.subject = subject;
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getCode() {
      return code;
    }

    public String getMessage() {
      return message;
    }

    public String getSubject() {
      return subject;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("message", message);
      result.put("subject", subject);
      return result;
    }
  }

  /** Immutable artifact comparison evidence. */
  public static final class ArtifactChange implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String relativePath;
    private final ChangeType changeType;
    private final EngineeringDiagramDeliveryAssessment.ArtifactEvidence baseline;
    private final EngineeringDiagramDeliveryAssessment.ArtifactEvidence revised;

    private ArtifactChange(String relativePath, ChangeType changeType,
        EngineeringDiagramDeliveryAssessment.ArtifactEvidence baseline,
        EngineeringDiagramDeliveryAssessment.ArtifactEvidence revised) {
      this.relativePath = relativePath;
      this.changeType = changeType;
      this.baseline = baseline;
      this.revised = revised;
    }

    public String getRelativePath() {
      return relativePath;
    }

    public ChangeType getChangeType() {
      return changeType;
    }

    public EngineeringDiagramDeliveryAssessment.ArtifactEvidence getBaseline() {
      return baseline;
    }

    public EngineeringDiagramDeliveryAssessment.ArtifactEvidence getRevised() {
      return revised;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("relativePath", relativePath);
      result.put("changeType", changeType.name());
      result.put("baseline", artifactMap(baseline));
      result.put("revised", artifactMap(revised));
      return result;
    }
  }

  /** Immutable serializable comparison report. */
  public static final class Report implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String plantId;
    private final String sourceScope;
    private final String baselineRevision;
    private final String revisedRevision;
    private final String baselineManifestSha256;
    private final String revisedManifestSha256;
    private final String baselineManifestFingerprint;
    private final String revisedManifestFingerprint;
    private final Status status;
    private final List<ArtifactChange> artifactChanges;
    private final List<String> changedArtifactPaths;
    private final List<ReviewScope> reviewScopes;
    private final List<Diagnostic> diagnostics;
    private final boolean complete;
    private final String fingerprint;

    private Report(Builder builder) {
      plantId = builder.baseline.getPlantId();
      sourceScope = builder.baseline.getSourceScope();
      baselineRevision = builder.baseline.getRevision();
      revisedRevision = builder.revised.getRevision();
      baselineManifestSha256 = builder.baseline.getManifestSha256();
      revisedManifestSha256 = builder.revised.getManifestSha256();
      baselineManifestFingerprint = builder.baseline.getManifestFingerprint();
      revisedManifestFingerprint = builder.revised.getManifestFingerprint();
      artifactChanges = immutableChanges(builder.artifactChanges);
      changedArtifactPaths = immutableStrings(builder.changedArtifactPaths);
      reviewScopes = immutableScopes(builder.reviewScopes);
      diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(builder.diagnostics));
      boolean revisionReused = baselineRevision.equals(revisedRevision)
          && (!changedArtifactPaths.isEmpty() || !baselineManifestSha256.equals(revisedManifestSha256));
      if (revisionReused) {
        status = Status.REVISION_REUSE;
      } else if (changedArtifactPaths.isEmpty() && baselineManifestSha256.equals(revisedManifestSha256)) {
        status = Status.IDENTICAL;
      } else {
        status = Status.CHANGED;
      }
      complete = !hasErrors(diagnostics);
      fingerprint = sha256(
          new GsonBuilder().create().toJson(toMapWithoutFingerprint()).getBytes(StandardCharsets.UTF_8));
    }

    public String getPlantId() {
      return plantId;
    }

    public String getSourceScope() {
      return sourceScope;
    }

    public String getBaselineRevision() {
      return baselineRevision;
    }

    public String getRevisedRevision() {
      return revisedRevision;
    }

    public String getBaselineManifestSha256() {
      return baselineManifestSha256;
    }

    public String getRevisedManifestSha256() {
      return revisedManifestSha256;
    }

    public String getBaselineManifestFingerprint() {
      return baselineManifestFingerprint;
    }

    public String getRevisedManifestFingerprint() {
      return revisedManifestFingerprint;
    }

    public Status getStatus() {
      return status;
    }

    public List<ArtifactChange> getArtifactChanges() {
      return Collections.unmodifiableList(new ArrayList<ArtifactChange>(artifactChanges));
    }

    public List<String> getChangedArtifactPaths() {
      return Collections.unmodifiableList(new ArrayList<String>(changedArtifactPaths));
    }

    public List<ReviewScope> getReviewScopes() {
      return Collections.unmodifiableList(new ArrayList<ReviewScope>(reviewScopes));
    }

    public List<Diagnostic> getDiagnostics() {
      return Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
    }

    public boolean isComplete() {
      return complete;
    }

    public boolean isEngineeringReviewRequired() {
      return status != Status.IDENTICAL;
    }

    public String getFingerprint() {
      return fingerprint;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = toMapWithoutFingerprint();
      result.put("fingerprint", fingerprint);
      return result;
    }

    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }

    private Map<String, Object> toMapWithoutFingerprint() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", SCHEMA);
      result.put("plantId", plantId);
      result.put("sourceScope", sourceScope);
      result.put("baselineRevision", baselineRevision);
      result.put("revisedRevision", revisedRevision);
      result.put("baselineManifestSha256", baselineManifestSha256);
      result.put("revisedManifestSha256", revisedManifestSha256);
      result.put("baselineManifestFingerprint", baselineManifestFingerprint);
      result.put("revisedManifestFingerprint", revisedManifestFingerprint);
      result.put("status", status.name());
      List<Map<String, Object>> changes = new ArrayList<Map<String, Object>>();
      for (ArtifactChange change : artifactChanges) {
        changes.add(change.toMap());
      }
      result.put("artifactChanges", changes);
      result.put("changedArtifactPaths", changedArtifactPaths);
      List<String> scopes = new ArrayList<String>();
      for (ReviewScope scope : reviewScopes) {
        scopes.add(scope.name());
      }
      result.put("reviewScopes", scopes);
      List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
      for (Diagnostic diagnostic : diagnostics) {
        diagnosticMaps.add(diagnostic.toMap());
      }
      result.put("diagnostics", diagnosticMaps);
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("engineeringReviewRequired", Boolean.valueOf(isEngineeringReviewRequired()));
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("iso10628ConformanceClaimed", Boolean.FALSE);
      result.put("completeWithinDeclaredScope", Boolean.valueOf(complete));
      result.put("limitations", limitations());
      return result;
    }
  }

  /**
   * Assesses and compares two delivery directories without modifying their contents.
   *
   * @param baselineDirectory baseline delivery root
   * @param revisedDirectory revised delivery root
   * @return deterministic comparison evidence
   * @throws IOException when either delivery cannot be assessed within its intake limits
   */
  public static Report compare(Path baselineDirectory, Path revisedDirectory) throws IOException {
    return compare(EngineeringDiagramDeliveryAssessment.assess(baselineDirectory),
        EngineeringDiagramDeliveryAssessment.assess(revisedDirectory));
  }

  /**
   * Compares two complete independent delivery assessments.
   *
   * @param baseline complete baseline assessment
   * @param revised complete revised assessment
   * @return deterministic comparison evidence
   */
  public static Report compare(EngineeringDiagramDeliveryAssessment.Report baseline,
      EngineeringDiagramDeliveryAssessment.Report revised) {
    requireCompatible(baseline, revised);
    Builder builder = new Builder(baseline, revised);
    compareArtifacts(builder);
    if (!baseline.getManifestSha256().equals(revised.getManifestSha256())) {
      builder.reviewScopes.add(ReviewScope.DELIVERY_MANIFEST);
      builder.reviewScopes.add(ReviewScope.ENGINEERING_REVIEW);
    }
    if (baseline.getRevision().equals(revised.getRevision()) && (!builder.changedArtifactPaths.isEmpty()
        || !baseline.getManifestSha256().equals(revised.getManifestSha256()))) {
      builder.diagnostics.add(new Diagnostic(Severity.ERROR, "DELIVERY_REVISION_REUSED_WITH_CHANGED_CONTENT",
          "Changed delivery content must not reuse the same controlled revision", baseline.getRevision()));
    } else if (builder.changedArtifactPaths.isEmpty()
        && baseline.getManifestSha256().equals(revised.getManifestSha256())) {
      builder.diagnostics.add(new Diagnostic(Severity.INFO, "DELIVERY_COMPARISON_IDENTICAL",
          "The independently assessed deliveries are byte-equivalent within the declared contract",
          baseline.getPlantId()));
    } else {
      builder.diagnostics.add(new Diagnostic(Severity.WARNING, "DELIVERY_COMPARISON_REVIEW_REQUIRED",
          "Changed delivery artifacts require review in every reported scope", revised.getRevision()));
    }
    return new Report(builder);
  }

  private static void requireCompatible(EngineeringDiagramDeliveryAssessment.Report baseline,
      EngineeringDiagramDeliveryAssessment.Report revised) {
    if (baseline == null || revised == null) {
      throw new IllegalArgumentException("baseline and revised assessments must not be null");
    }
    if (!baseline.isComplete() || !revised.isComplete()) {
      throw new IllegalArgumentException("only complete independently assessed deliveries may be compared");
    }
    if (!baseline.getPlantId().equals(revised.getPlantId())) {
      throw new IllegalArgumentException("deliveries must use the same plant identity");
    }
    if (!baseline.getSourceScope().equals(revised.getSourceScope())) {
      throw new IllegalArgumentException("deliveries must use the same ProcessSystem or ProcessModel source scope");
    }
  }

  private static void compareArtifacts(Builder builder) {
    Map<String, EngineeringDiagramDeliveryAssessment.ArtifactEvidence> before = new TreeMap<String, EngineeringDiagramDeliveryAssessment.ArtifactEvidence>(
        builder.baseline.getArtifacts());
    Map<String, EngineeringDiagramDeliveryAssessment.ArtifactEvidence> after = new TreeMap<String, EngineeringDiagramDeliveryAssessment.ArtifactEvidence>(
        builder.revised.getArtifacts());
    Set<String> paths = new TreeSet<String>();
    paths.addAll(before.keySet());
    paths.addAll(after.keySet());
    for (String path : paths) {
      EngineeringDiagramDeliveryAssessment.ArtifactEvidence first = before.get(path);
      EngineeringDiagramDeliveryAssessment.ArtifactEvidence second = after.get(path);
      ChangeType type = changeType(first, second);
      builder.artifactChanges.add(new ArtifactChange(path, type, first, second));
      if (type != ChangeType.UNCHANGED) {
        builder.changedArtifactPaths.add(path);
        addScope(path, builder.reviewScopes);
      }
    }
  }

  private static ChangeType changeType(EngineeringDiagramDeliveryAssessment.ArtifactEvidence baseline,
      EngineeringDiagramDeliveryAssessment.ArtifactEvidence revised) {
    if (baseline == null) {
      return ChangeType.ADDED;
    }
    if (revised == null) {
      return ChangeType.REMOVED;
    }
    if (baseline.getSizeBytes() == revised.getSizeBytes() && baseline.getMediaType().equals(revised.getMediaType())
        && baseline.getSha256().equals(revised.getSha256())) {
      return ChangeType.UNCHANGED;
    }
    return ChangeType.MODIFIED;
  }

  private static void addScope(String path, Set<ReviewScope> scopes) {
    scopes.add(ReviewScope.ENGINEERING_REVIEW);
    if ("document-set.json".equals(path)) {
      scopes.add(ReviewScope.CONTROLLED_DOCUMENT_SET);
    } else if ("drawing-set.pdf".equals(path)) {
      scopes.add(ReviewScope.NATIVE_PDF);
    } else if ("dexpi-process.xml".equals(path)) {
      scopes.add(ReviewScope.DEXPI_PROCESS_EXCHANGE);
    } else if ("dexpi-process-model.zip".equals(path)) {
      scopes.add(ReviewScope.DEXPI_PROCESS_MODEL_PACKAGE);
    } else if (path.startsWith("svg/") && path.endsWith(".svg")) {
      scopes.add(ReviewScope.NATIVE_SVG);
    }
  }

  private static Map<String, Object> artifactMap(EngineeringDiagramDeliveryAssessment.ArtifactEvidence artifact) {
    if (artifact == null) {
      return null;
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("relativePath", artifact.getRelativePath());
    result.put("mediaType", artifact.getMediaType());
    result.put("sizeBytes", Long.valueOf(artifact.getSizeBytes()));
    result.put("sha256", artifact.getSha256());
    return result;
  }

  private static List<String> limitations() {
    List<String> result = new ArrayList<String>();
    result.add("Does not reconstruct or execute a ProcessSystem or ProcessModel");
    result.add("Does not repeat DEXPI semantic or external-validator assessment");
    result.add("Does not decide management of change or accountable drawing approval");
    result.add("Does not establish fitness for construction or standards conformance");
    return Collections.unmodifiableList(result);
  }

  private static boolean hasErrors(List<Diagnostic> diagnostics) {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return true;
      }
    }
    return false;
  }

  private static List<ArtifactChange> immutableChanges(List<ArtifactChange> changes) {
    List<ArtifactChange> result = new ArrayList<ArtifactChange>(changes);
    Collections.sort(result, new Comparator<ArtifactChange>() {
      @Override
      public int compare(ArtifactChange first, ArtifactChange second) {
        return first.getRelativePath().compareTo(second.getRelativePath());
      }
    });
    return Collections.unmodifiableList(result);
  }

  private static List<String> immutableStrings(Set<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(new TreeSet<String>(values)));
  }

  private static List<ReviewScope> immutableScopes(Set<ReviewScope> values) {
    List<ReviewScope> result = new ArrayList<ReviewScope>(values);
    Collections.sort(result, new Comparator<ReviewScope>() {
      @Override
      public int compare(ReviewScope first, ReviewScope second) {
        return first.name().compareTo(second.name());
      }
    });
    return Collections.unmodifiableList(result);
  }

  private static String sha256(byte[] content) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
      StringBuilder result = new StringBuilder();
      for (byte value : hash) {
        result.append(String.format("%02x", value & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private static final class Builder {
    private final EngineeringDiagramDeliveryAssessment.Report baseline;
    private final EngineeringDiagramDeliveryAssessment.Report revised;
    private final List<ArtifactChange> artifactChanges = new ArrayList<ArtifactChange>();
    private final Set<String> changedArtifactPaths = new TreeSet<String>();
    private final Set<ReviewScope> reviewScopes = new TreeSet<ReviewScope>();
    private final List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();

    private Builder(EngineeringDiagramDeliveryAssessment.Report baseline,
        EngineeringDiagramDeliveryAssessment.Report revised) {
      this.baseline = baseline;
      this.revised = revised;
    }
  }
}
