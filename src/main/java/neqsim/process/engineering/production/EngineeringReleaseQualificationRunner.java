package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives release-quality evidence from reproducibility, performance and compatibility measurements. */
public final class EngineeringReleaseQualificationRunner {
  private EngineeringReleaseQualificationRunner() {
  }

  /** Controlled release-verification inputs populated by CI and review workflows. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String releaseId;
    private final String evidenceReference;
    private final String accountableReviewer;
    private boolean fullCiPassed;
    private final Set<String> requiredJavaVersions = new LinkedHashSet<String>();
    private final Set<String> passedJavaVersions = new LinkedHashSet<String>();
    private final List<String> deterministicFingerprints = new ArrayList<String>();
    private final List<Double> elapsedSeconds = new ArrayList<Double>();
    private double maximumAcceptedSeconds;
    private String apiBaselineFingerprint = "";
    private String apiCandidateFingerprint = "";
    private boolean serializationMigrationPassed;
    private int openHighSeveritySecurityFindings;
    private boolean securityReviewSupplied;

    public Input(String releaseId, String evidenceReference, String accountableReviewer) {
      this.releaseId = text(releaseId, "releaseId");
      this.evidenceReference = text(evidenceReference, "evidenceReference");
      this.accountableReviewer = text(accountableReviewer, "accountableReviewer");
    }

    public Input fullCiPassed(boolean value) {
      fullCiPassed = value;
      return this;
    }

    public Input requireJavaVersion(String value) {
      requiredJavaVersions.add(text(value, "requiredJavaVersion"));
      return this;
    }

    public Input passedJavaVersion(String value) {
      passedJavaVersions.add(text(value, "passedJavaVersion"));
      return this;
    }

    public Input deterministicFingerprint(String value) {
      deterministicFingerprints.add(text(value, "deterministicFingerprint"));
      return this;
    }

    public Input performanceSample(double elapsedSeconds) {
      if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0) {
        throw new IllegalArgumentException("elapsedSeconds must be finite and non-negative");
      }
      this.elapsedSeconds.add(Double.valueOf(elapsedSeconds));
      return this;
    }

    public Input maximumAcceptedSeconds(double value) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalArgumentException("maximumAcceptedSeconds must be finite and positive");
      }
      maximumAcceptedSeconds = value;
      return this;
    }

    public Input apiFingerprints(String baseline, String candidate) {
      apiBaselineFingerprint = text(baseline, "apiBaselineFingerprint");
      apiCandidateFingerprint = text(candidate, "apiCandidateFingerprint");
      return this;
    }

    public Input serializationMigrationPassed(boolean value) {
      serializationMigrationPassed = value;
      return this;
    }

    public Input openHighSeveritySecurityFindings(int value) {
      if (value < 0) {
        throw new IllegalArgumentException("Security finding count must be non-negative");
      }
      openHighSeveritySecurityFindings = value;
      securityReviewSupplied = true;
      return this;
    }
  }

  /** Release evidence plus the measurements used to derive each boolean gate. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final EngineeringReleaseQualityEvidence evidence;
    private final Map<String, Object> measurements;

    Result(EngineeringReleaseQualityEvidence evidence, Map<String, Object> measurements) {
      this.evidence = evidence;
      this.measurements = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(measurements));
    }

    public EngineeringReleaseQualityEvidence getEvidence() {
      return evidence;
    }

    public Map<String, Object> getMeasurements() {
      return measurements;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("evidence", evidence.toMap());
      result.put("measurements", new LinkedHashMap<String, Object>(measurements));
      return result;
    }
  }

  public static Result run(Input input) {
    if (input == null) {
      throw new IllegalArgumentException("release qualification input must not be null");
    }
    boolean javaMatrix = !input.requiredJavaVersions.isEmpty()
        && input.passedJavaVersions.containsAll(input.requiredJavaVersions);
    boolean deterministic = input.deterministicFingerprints.size() >= 2;
    if (deterministic) {
      String first = input.deterministicFingerprints.get(0);
      for (String value : input.deterministicFingerprints) {
        deterministic &= first.equals(value);
      }
    }
    boolean performance = !input.elapsedSeconds.isEmpty() && input.maximumAcceptedSeconds > 0.0;
    double maximumElapsed = 0.0;
    for (Double elapsed : input.elapsedSeconds) {
      maximumElapsed = Math.max(maximumElapsed, elapsed.doubleValue());
    }
    performance &= maximumElapsed <= input.maximumAcceptedSeconds;
    boolean api = !input.apiBaselineFingerprint.isEmpty()
        && input.apiBaselineFingerprint.equals(input.apiCandidateFingerprint);
    boolean security = input.securityReviewSupplied && input.openHighSeveritySecurityFindings == 0;
    EngineeringReleaseQualityEvidence evidence = new EngineeringReleaseQualityEvidence(input.releaseId)
        .fullCiPassed(input.fullCiPassed).supportedJavaMatrixPassed(javaMatrix)
        .deterministicConvergencePassed(deterministic).performanceAcceptancePassed(performance)
        .apiCompatibilityPassed(api).serializationMigrationPassed(input.serializationMigrationPassed)
        .securityReviewPassed(security).evidenceReference(input.evidenceReference)
        .accountableReviewer(input.accountableReviewer);
    Map<String, Object> measurements = new LinkedHashMap<String, Object>();
    measurements.put("requiredJavaVersions", new ArrayList<String>(input.requiredJavaVersions));
    measurements.put("passedJavaVersions", new ArrayList<String>(input.passedJavaVersions));
    measurements.put("deterministicFingerprints", new ArrayList<String>(input.deterministicFingerprints));
    measurements.put("elapsedSeconds", new ArrayList<Double>(input.elapsedSeconds));
    measurements.put("maximumElapsedSeconds", Double.valueOf(maximumElapsed));
    measurements.put("maximumAcceptedSeconds", Double.valueOf(input.maximumAcceptedSeconds));
    measurements.put("apiBaselineFingerprint", input.apiBaselineFingerprint);
    measurements.put("apiCandidateFingerprint", input.apiCandidateFingerprint);
    measurements.put("openHighSeveritySecurityFindings", Integer.valueOf(input.openHighSeveritySecurityFindings));
    measurements.put("securityReviewSupplied", Boolean.valueOf(input.securityReviewSupplied));
    return new Result(evidence, measurements);
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
