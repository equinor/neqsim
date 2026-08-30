package neqsim.process.operations;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.util.validation.ValidationResult;
import neqsim.util.validation.ValidationResult.ValidationIssue;

/**
 * Binds plant-agnostic operational evidence needed to qualify a dynamic controller study.
 *
 * <p>
 * The binding captures command and independent feedback evidence for each actuator together with controller mode,
 * active-count, routing, permissive, and fallback-availability evidence. Each signal carries a timestamp, quality, and
 * opaque provenance identifier. Source-system clients remain responsible for retrieving data and translating private
 * tags into logical names; this class has no dependency on historian, DCS, Seeq, or maintenance-system APIs.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ControllerEvidenceBinding implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Stable JSON contract version returned by {@link #toReadinessJson(long)}. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Evidence quality states accepted by controller-study readiness validation. */
  public enum EvidenceQuality {
    /** Original source value passed all source quality checks. */
    GOOD,
    /** Source explicitly marked a substituted value as fit for use. */
    SUBSTITUTED_GOOD,
    /** Source quality is uncertain and requires review. */
    QUESTIONABLE,
    /** Source marked the value as invalid or unusable. */
    BAD,
    /** No source quality status was available. */
    UNKNOWN
  }

  /**
   * Immutable evidence for one logical operating signal.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class SignalEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String logicalTag;
    private final String value;
    private final String engineeringUnit;
    private final long timestampMillis;
    private final EvidenceQuality quality;
    private final String provenanceId;

    /**
     * Creates signal evidence.
     *
     * @param logicalTag public logical signal name, not a private source-system tag
     * @param value source value represented as text
     * @param engineeringUnit source engineering unit or state/count qualifier
     * @param timestampMillis source timestamp in epoch milliseconds
     * @param quality source quality state
     * @param provenanceId opaque document, dataset, or retrieval-record identifier; never a credential
     * @throws IllegalArgumentException if logicalTag is null or empty
     */
    public SignalEvidence(String logicalTag, String value, String engineeringUnit, long timestampMillis,
        EvidenceQuality quality, String provenanceId) {
      this.logicalTag = requireText(logicalTag, "logicalTag");
      this.value = clean(value);
      this.engineeringUnit = clean(engineeringUnit);
      this.timestampMillis = timestampMillis;
      this.quality = quality == null ? EvidenceQuality.UNKNOWN : quality;
      this.provenanceId = clean(provenanceId);
    }

    /**
     * Returns the public logical signal name.
     *
     * @return logical signal name
     */
    public String getLogicalTag() {
      return logicalTag;
    }

    /**
     * Returns the source value as text.
     *
     * @return source value, or an empty string when unavailable
     */
    public String getValue() {
      return value;
    }

    /**
     * Returns the source engineering unit or state/count qualifier.
     *
     * @return engineering unit, or an empty string when unavailable
     */
    public String getEngineeringUnit() {
      return engineeringUnit;
    }

    /**
     * Returns the source timestamp.
     *
     * @return epoch timestamp in milliseconds
     */
    public long getTimestampMillis() {
      return timestampMillis;
    }

    /**
     * Returns the source quality state.
     *
     * @return evidence quality
     */
    public EvidenceQuality getQuality() {
      return quality;
    }

    /**
     * Returns the opaque provenance identifier.
     *
     * @return provenance identifier, or an empty string when unavailable
     */
    public String getProvenanceId() {
      return provenanceId;
    }
  }

  /**
   * Command and independently identified feedback evidence for one actuator.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class ActuatorEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String actuatorId;
    private final SignalEvidence command;
    private final SignalEvidence feedback;

    /**
     * Creates actuator evidence.
     *
     * @param actuatorId public actuator identifier
     * @param command commanded position or output evidence
     * @param feedback independently identified applied-position or output feedback evidence
     * @throws IllegalArgumentException if actuatorId is null or empty
     */
    public ActuatorEvidence(String actuatorId, SignalEvidence command, SignalEvidence feedback) {
      this.actuatorId = requireText(actuatorId, "actuatorId");
      this.command = command;
      this.feedback = feedback;
    }

    /**
     * Returns the public actuator identifier.
     *
     * @return actuator identifier
     */
    public String getActuatorId() {
      return actuatorId;
    }

    /**
     * Returns command evidence.
     *
     * @return command evidence, or null when missing
     */
    public SignalEvidence getCommand() {
      return command;
    }

    /**
     * Returns independent feedback evidence.
     *
     * @return feedback evidence, or null when missing
     */
    public SignalEvidence getFeedback() {
      return feedback;
    }
  }

  private final String controllerId;
  private final List<ActuatorEvidence> actuators;
  private final SignalEvidence mode;
  private final SignalEvidence activeCount;
  private final List<SignalEvidence> routing;
  private final SignalEvidence permissive;
  private final SignalEvidence fallbackAvailable;
  private final long maxAgeMillis;
  private final boolean substitutedGoodAllowed;

  /**
   * Creates an immutable binding from a builder.
   *
   * @param builder configured builder
   */
  private ControllerEvidenceBinding(Builder builder) {
    controllerId = requireText(builder.controllerId, "controllerId");
    actuators = Collections.unmodifiableList(new ArrayList<ActuatorEvidence>(builder.actuators));
    mode = builder.mode;
    activeCount = builder.activeCount;
    routing = Collections.unmodifiableList(new ArrayList<SignalEvidence>(builder.routing));
    permissive = builder.permissive;
    fallbackAvailable = builder.fallbackAvailable;
    maxAgeMillis = builder.maxAgeMillis;
    substitutedGoodAllowed = builder.substitutedGoodAllowed;
  }

  /**
   * Starts a builder for a controller evidence binding.
   *
   * @param controllerId stable public controller or application identifier
   * @return new builder
   */
  public static Builder builder(String controllerId) {
    return new Builder(controllerId);
  }

  /**
   * Returns the controller or application identifier.
   *
   * @return controller identifier
   */
  public String getControllerId() {
    return controllerId;
  }

  /**
   * Returns actuator evidence in insertion order.
   *
   * @return unmodifiable actuator evidence list
   */
  public List<ActuatorEvidence> getActuators() {
    return actuators;
  }

  /**
   * Returns controller mode evidence.
   *
   * @return mode evidence, or null when missing
   */
  public SignalEvidence getMode() {
    return mode;
  }

  /**
   * Returns reported active-count evidence.
   *
   * @return active-count evidence, or null when missing
   */
  public SignalEvidence getActiveCount() {
    return activeCount;
  }

  /**
   * Returns route-active evidence in insertion order.
   *
   * @return unmodifiable routing evidence list
   */
  public List<SignalEvidence> getRouting() {
    return routing;
  }

  /**
   * Returns permissive evidence.
   *
   * @return permissive evidence, or null when missing
   */
  public SignalEvidence getPermissive() {
    return permissive;
  }

  /**
   * Returns fallback-availability evidence.
   *
   * @return fallback evidence, or null when missing
   */
  public SignalEvidence getFallbackAvailable() {
    return fallbackAvailable;
  }

  /**
   * Returns the maximum permitted evidence age.
   *
   * @return maximum age in milliseconds
   */
  public long getMaxAgeMillis() {
    return maxAgeMillis;
  }

  /**
   * Reports whether substituted-good evidence is accepted.
   *
   * @return true when substituted-good source values may pass readiness validation
   */
  public boolean isSubstitutedGoodAllowed() {
    return substitutedGoodAllowed;
  }

  /**
   * Validates evidence completeness, independence, quality, freshness, and cross-signal consistency.
   *
   * <p>
   * A false permissive is valid evidence of an inhibited controller state. Fallback evidence instead represents
   * availability, so a false fallback value blocks readiness. The method only qualifies data readiness; it does not
   * authorize controller activation or assess functional-safety compliance.
   * </p>
   *
   * @param evaluationTimestampMillis study evaluation time in epoch milliseconds
   * @return validation result with remediation for every blocking finding
   */
  public ValidationResult validate(long evaluationTimestampMillis) {
    ValidationResult result = new ValidationResult("ControllerEvidenceBinding:" + controllerId);
    if (evaluationTimestampMillis <= 0L) {
      result.addError("timestamp", "Evaluation timestamp must be positive.",
          "Provide the epoch-millisecond time at which evidence readiness is evaluated.");
    }
    if (actuators.isEmpty()) {
      result.addError("actuator", "No actuator command/feedback evidence is configured.",
          "Add at least one actuator with independently identified command and feedback signals.");
    }
    for (ActuatorEvidence actuator : actuators) {
      validateActuator(actuator, evaluationTimestampMillis, result);
    }

    validateRequiredSignal("mode", mode, evaluationTimestampMillis, result);
    validateRequiredSignal("active-count", activeCount, evaluationTimestampMillis, result);
    validateRequiredSignal("permissive", permissive, evaluationTimestampMillis, result);
    validateRequiredSignal("fallback", fallbackAvailable, evaluationTimestampMillis, result);

    Integer reportedActiveCount = parseInteger(activeCount, "active-count", result);
    int routedActiveCount = validateRouting(evaluationTimestampMillis, result);
    if (reportedActiveCount != null && reportedActiveCount.intValue() != routedActiveCount) {
      result.addError("active-count",
          "Reported active count " + reportedActiveCount + " disagrees with " + routedActiveCount
              + " active routing states.",
          "Reconcile the active-count signal with independently captured route-active states.");
    }

    parseBoolean(permissive, "permissive", result);
    Boolean hasFallback = parseBoolean(fallbackAvailable, "fallback", result);
    if (Boolean.FALSE.equals(hasFallback)) {
      result.addError("fallback", "Fallback is reported unavailable.",
          "Define and verify a safe fallback before using the evidence for controller activation studies.");
    }
    return result;
  }

  /**
   * Returns schema-versioned, machine-readable readiness findings.
   *
   * @param evaluationTimestampMillis study evaluation time in epoch milliseconds
   * @return JSON containing readiness status and validation findings
   */
  public String toReadinessJson(long evaluationTimestampMillis) {
    ValidationResult validation = validate(evaluationTimestampMillis);
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("controllerId", controllerId);
    root.addProperty("evaluationTimestampMillis", evaluationTimestampMillis);
    root.addProperty("ready", validation.isReady());
    JsonArray findings = new JsonArray();
    for (ValidationIssue issue : validation.getIssues()) {
      JsonObject finding = new JsonObject();
      finding.addProperty("severity", issue.getSeverity().name());
      finding.addProperty("category", issue.getCategory());
      finding.addProperty("message", issue.getMessage());
      finding.addProperty("remediation", issue.getRemediation());
      findings.add(finding);
    }
    root.add("findings", findings);
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }

  /**
   * Validates command and feedback evidence for one actuator.
   *
   * @param actuator actuator evidence to validate
   * @param evaluationTimestampMillis study evaluation time in epoch milliseconds
   * @param result validation result to update
   */
  private void validateActuator(ActuatorEvidence actuator, long evaluationTimestampMillis, ValidationResult result) {
    if (actuator == null) {
      result.addError("actuator", "Actuator evidence is null.", "Remove null entries or provide actuator evidence.");
      return;
    }
    SignalEvidence command = actuator.getCommand();
    SignalEvidence feedback = actuator.getFeedback();
    validateRequiredSignal("command:" + actuator.getActuatorId(), command, evaluationTimestampMillis, result);
    validateRequiredSignal("feedback:" + actuator.getActuatorId(), feedback, evaluationTimestampMillis, result);
    parseDouble(command, "command:" + actuator.getActuatorId(), result);
    parseDouble(feedback, "feedback:" + actuator.getActuatorId(), result);
    if (command != null && feedback != null && command.getLogicalTag().equals(feedback.getLogicalTag())) {
      result.addError("feedback", "Actuator " + actuator.getActuatorId() + " uses the command signal as feedback.",
          "Bind an independently identified applied-position or output feedback signal.");
    }
    if (command != null && feedback != null && !command.getEngineeringUnit().isEmpty()
        && !feedback.getEngineeringUnit().isEmpty()
        && !command.getEngineeringUnit().equalsIgnoreCase(feedback.getEngineeringUnit())) {
      result.addError("unit",
          "Actuator " + actuator.getActuatorId() + " command unit " + command.getEngineeringUnit()
              + " disagrees with feedback unit " + feedback.getEngineeringUnit() + ".",
          "Bind command and applied-position feedback in the same engineering unit.");
    }
  }

  /**
   * Validates all routing states and returns the number reported active.
   *
   * @param evaluationTimestampMillis study evaluation time in epoch milliseconds
   * @param result validation result to update
   * @return number of routes whose evidence value is true
   */
  private int validateRouting(long evaluationTimestampMillis, ValidationResult result) {
    if (routing.isEmpty()) {
      result.addError("routing", "No routing-state evidence is configured.",
          "Add one boolean route-active signal for each relevant production or test route.");
      return 0;
    }
    int activeRoutes = 0;
    for (SignalEvidence route : routing) {
      validateRequiredSignal("routing", route, evaluationTimestampMillis, result);
      Boolean active = parseBoolean(route, "routing", result);
      if (Boolean.TRUE.equals(active)) {
        activeRoutes++;
      }
    }
    return activeRoutes;
  }

  /**
   * Validates one required signal's presence, quality, provenance, and timestamp.
   *
   * @param category finding category
   * @param signal signal evidence
   * @param evaluationTimestampMillis study evaluation time in epoch milliseconds
   * @param result validation result to update
   */
  private void validateRequiredSignal(String category, SignalEvidence signal, long evaluationTimestampMillis,
      ValidationResult result) {
    if (signal == null) {
      result.addError(category, "Required " + category + " evidence is missing.",
          "Bind the logical signal, source timestamp, quality, and provenance identifier.");
      return;
    }
    if (signal.getValue().isEmpty()) {
      result.addError(category, "Signal " + signal.getLogicalTag() + " has no value.",
          "Provide the source value captured for the study window.");
    }
    if (signal.getEngineeringUnit().isEmpty()) {
      result.addError("unit", "Signal " + signal.getLogicalTag() + " has no engineering unit or qualifier.",
          "Provide the source engineering unit, or use a documented state/count qualifier for discrete signals.");
    }
    if (signal.getProvenanceId().isEmpty()) {
      result.addError("provenance", "Signal " + signal.getLogicalTag() + " has no provenance identifier.",
          "Provide an opaque dataset, document, or retrieval-record identifier without credentials.");
    }
    if (signal.getTimestampMillis() <= 0L) {
      result.addError("timestamp", "Signal " + signal.getLogicalTag() + " has no valid timestamp.",
          "Provide the source timestamp in epoch milliseconds.");
    } else if (evaluationTimestampMillis > 0L && signal.getTimestampMillis() > evaluationTimestampMillis) {
      result.addError("timestamp", "Signal " + signal.getLogicalTag() + " is timestamped after the evaluation time.",
          "Synchronize source clocks and use evidence available at the evaluation time.");
    } else if (evaluationTimestampMillis > 0L
        && evaluationTimestampMillis - signal.getTimestampMillis() > maxAgeMillis) {
      result.addError("stale", "Signal " + signal.getLogicalTag() + " exceeds the maximum evidence age.",
          "Refresh the signal or increase the age limit only with a documented sampling basis.");
    }
    if (signal.getQuality() == EvidenceQuality.GOOD) {
      return;
    }
    if (signal.getQuality() == EvidenceQuality.SUBSTITUTED_GOOD && substitutedGoodAllowed) {
      return;
    }
    result.addError("quality",
        "Signal " + signal.getLogicalTag() + " has unacceptable quality " + signal.getQuality() + ".",
        signal.getQuality() == EvidenceQuality.SUBSTITUTED_GOOD
            ? "Enable substituted-good evidence only when the study policy explicitly permits it."
            : "Use a good-quality source value or exclude the affected study interval.");
  }

  /**
   * Parses a finite numeric signal value.
   *
   * @param signal signal evidence
   * @param category finding category
   * @param result validation result to update
   * @return parsed value, or null when unavailable or invalid
   */
  private Double parseDouble(SignalEvidence signal, String category, ValidationResult result) {
    if (signal == null || signal.getValue().isEmpty()) {
      return null;
    }
    try {
      double value = Double.parseDouble(signal.getValue());
      if (!Double.isFinite(value)) {
        throw new NumberFormatException("non-finite");
      }
      return Double.valueOf(value);
    } catch (NumberFormatException ex) {
      result.addError(category, "Signal " + signal.getLogicalTag() + " is not a finite numeric value.",
          "Provide command and feedback values as finite numbers using a common engineering unit.");
      return null;
    }
  }

  /**
   * Parses a non-negative integer signal value.
   *
   * @param signal signal evidence
   * @param category finding category
   * @param result validation result to update
   * @return parsed integer, or null when unavailable or invalid
   */
  private Integer parseInteger(SignalEvidence signal, String category, ValidationResult result) {
    if (signal == null || signal.getValue().isEmpty()) {
      return null;
    }
    try {
      int value = Integer.parseInt(signal.getValue());
      if (value < 0) {
        throw new NumberFormatException("negative");
      }
      return Integer.valueOf(value);
    } catch (NumberFormatException ex) {
      result.addError(category, "Signal " + signal.getLogicalTag() + " is not a non-negative integer.",
          "Provide the active controller or route count as a non-negative integer.");
      return null;
    }
  }

  /**
   * Parses a strict boolean signal value.
   *
   * @param signal signal evidence
   * @param category finding category
   * @param result validation result to update
   * @return parsed state, or null when unavailable or invalid
   */
  private Boolean parseBoolean(SignalEvidence signal, String category, ValidationResult result) {
    if (signal == null || signal.getValue().isEmpty()) {
      return null;
    }
    String value = signal.getValue().trim();
    if ("true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value)
        || "active".equalsIgnoreCase(value)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(value) || "0".equals(value) || "off".equalsIgnoreCase(value)
        || "inactive".equalsIgnoreCase(value)) {
      return Boolean.FALSE;
    }
    result.addError(category, "Signal " + signal.getLogicalTag() + " is not a recognized boolean state.",
        "Use true/false, 1/0, on/off, or active/inactive.");
    return null;
  }

  /**
   * Cleans nullable text.
   *
   * @param text text to clean
   * @return trimmed text or an empty string
   */
  private static String clean(String text) {
    return text == null ? "" : text.trim();
  }

  /**
   * Requires non-empty text.
   *
   * @param text text to validate
   * @param fieldName field name for the exception message
   * @return trimmed text
   * @throws IllegalArgumentException if text is null or empty
   */
  private static String requireText(String text, String fieldName) {
    String value = clean(text);
    if (value.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be empty");
    }
    return value;
  }

  /**
   * Builder for immutable controller evidence bindings.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class Builder {
    private final String controllerId;
    private final List<ActuatorEvidence> actuators = new ArrayList<ActuatorEvidence>();
    private SignalEvidence mode;
    private SignalEvidence activeCount;
    private final List<SignalEvidence> routing = new ArrayList<SignalEvidence>();
    private SignalEvidence permissive;
    private SignalEvidence fallbackAvailable;
    private long maxAgeMillis = 60000L;
    private boolean substitutedGoodAllowed;

    /**
     * Creates a builder.
     *
     * @param controllerId stable public controller or application identifier
     */
    private Builder(String controllerId) {
      this.controllerId = controllerId;
    }

    /**
     * Adds command and feedback evidence for one actuator.
     *
     * @param actuatorId public actuator identifier
     * @param command commanded position or output evidence
     * @param feedback independently identified applied-position or output feedback evidence
     * @return this builder
     */
    public Builder addActuator(String actuatorId, SignalEvidence command, SignalEvidence feedback) {
      actuators.add(new ActuatorEvidence(actuatorId, command, feedback));
      return this;
    }

    /**
     * Sets controller-mode evidence.
     *
     * @param mode controller mode evidence
     * @return this builder
     */
    public Builder mode(SignalEvidence mode) {
      this.mode = mode;
      return this;
    }

    /**
     * Sets reported active-count evidence.
     *
     * @param activeCount non-negative integer active-count evidence
     * @return this builder
     */
    public Builder activeCount(SignalEvidence activeCount) {
      this.activeCount = activeCount;
      return this;
    }

    /**
     * Adds one route-active state used to reconcile the reported active count.
     *
     * @param routeActive boolean route-active evidence
     * @return this builder
     */
    public Builder addRouting(SignalEvidence routeActive) {
      routing.add(routeActive);
      return this;
    }

    /**
     * Sets controller permissive evidence.
     *
     * @param permissive boolean permissive evidence
     * @return this builder
     */
    public Builder permissive(SignalEvidence permissive) {
      this.permissive = permissive;
      return this;
    }

    /**
     * Sets fallback-availability evidence.
     *
     * @param fallbackAvailable boolean fallback-availability evidence
     * @return this builder
     */
    public Builder fallbackAvailable(SignalEvidence fallbackAvailable) {
      this.fallbackAvailable = fallbackAvailable;
      return this;
    }

    /**
     * Sets the maximum permitted age for every evidence signal.
     *
     * @param maxAgeMillis positive maximum age in milliseconds
     * @return this builder
     * @throws IllegalArgumentException if maxAgeMillis is not positive
     */
    public Builder maxAgeMillis(long maxAgeMillis) {
      if (maxAgeMillis <= 0L) {
        throw new IllegalArgumentException("maxAgeMillis must be positive");
      }
      this.maxAgeMillis = maxAgeMillis;
      return this;
    }

    /**
     * Configures whether source values marked substituted-good may pass validation.
     *
     * @param substitutedGoodAllowed true only when the study policy permits substituted-good evidence
     * @return this builder
     */
    public Builder allowSubstitutedGood(boolean substitutedGoodAllowed) {
      this.substitutedGoodAllowed = substitutedGoodAllowed;
      return this;
    }

    /**
     * Builds the immutable binding.
     *
     * @return controller evidence binding
     */
    public ControllerEvidenceBinding build() {
      return new ControllerEvidenceBinding(this);
    }
  }
}