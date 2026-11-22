package neqsim.process.util.scenario;

import com.google.gson.Gson;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.process.util.scenario.CompressorAutomationStudy.CaseDefinition;
import neqsim.process.util.scenario.CompressorAutomationStudy.Profile;

/**
 * Immutable, complete acceptance policy for the public compressor automation scenarios.
 *
 * <p>
 * The policy owns only acceptance bounds, event-relative deadlines, evaluation modes and required/optional status. It
 * cannot change the physical model, initiating event, observation implementation, criterion identity or unit. This
 * separation permits policy review and revision without silently changing the simulated trajectory.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class CompressorAutomationAcceptancePolicy implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String SCHEMA_VERSION = "neqsim.compressor_automation_acceptance_policy.v1";
  private static final Gson GSON = new Gson();
  private final List<ProfilePolicy> profiles;
  private final Map<Profile, ProfilePolicy> byProfile;
  private final String fingerprint;

  /**
   * Defines whether a deadline is a fixed event-relative duration or the complete remaining case horizon.
   *
   * @author NeqSim
   * @version 1.0
   */
  public enum DeadlineBasis {
    /** Use the declared number of seconds after the initiating event. */
    SECONDS_AFTER_EVENT,
    /** Resolve the deadline to case duration minus initiating-event time. */
    CASE_HORIZON
  }

  /**
   * Immutable acceptance settings for one stable observation identity.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class CriterionPolicy implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String unit;
    private final Double lowerLimit;
    private final Double upperLimit;
    private final DeadlineBasis deadlineBasis;
    private final Double deadlineSeconds;
    private final DynamicScenarioCriterion.EvaluationMode evaluationMode;
    private final boolean required;

    /**
     * Creates explicit settings for one stable compressor observation.
     *
     * @param id stable criterion identifier
     * @param unit exact observation unit
     * @param lowerLimit optional finite lower bound
     * @param upperLimit optional finite upper bound
     * @param deadlineBasis event-relative deadline basis
     * @param deadlineSeconds fixed seconds for {@link DeadlineBasis#SECONDS_AFTER_EVENT}, otherwise null
     * @param evaluationMode reach or maintained-bound evaluation
     * @param required whether failure contributes to the scenario verdict
     * @throws IllegalArgumentException if identity, bounds, deadline or mode is invalid
     */
    public CriterionPolicy(String id, String unit, Double lowerLimit, Double upperLimit, DeadlineBasis deadlineBasis,
        Double deadlineSeconds, DynamicScenarioCriterion.EvaluationMode evaluationMode, boolean required) {
      this.id = requireText(id, "criterion id");
      this.unit = requireText(unit, "criterion unit");
      validateBound(lowerLimit, "lowerLimit");
      validateBound(upperLimit, "upperLimit");
      if (lowerLimit == null && upperLimit == null) {
        throw new IllegalArgumentException("At least one acceptance limit is required for " + id);
      }
      if (lowerLimit != null && upperLimit != null && lowerLimit.doubleValue() > upperLimit.doubleValue()) {
        throw new IllegalArgumentException("Lower limit must not exceed upper limit for " + id);
      }
      if (deadlineBasis == null || evaluationMode == null) {
        throw new IllegalArgumentException("Deadline basis and evaluation mode are required for " + id);
      }
      if (deadlineBasis == DeadlineBasis.SECONDS_AFTER_EVENT) {
        if (deadlineSeconds == null || !Double.isFinite(deadlineSeconds.doubleValue())
            || deadlineSeconds.doubleValue() < 0.0) {
          throw new IllegalArgumentException("A finite non-negative event-relative deadline is required for " + id);
        }
      } else if (deadlineSeconds != null) {
        throw new IllegalArgumentException("CASE_HORIZON must not carry a hidden fixed deadline for " + id);
      }
      this.lowerLimit = lowerLimit;
      this.upperLimit = upperLimit;
      this.deadlineBasis = deadlineBasis;
      this.deadlineSeconds = deadlineSeconds;
      this.evaluationMode = evaluationMode;
      this.required = required;
    }

    /** @return stable criterion identifier */
    public String getId() {
      return id;
    }

    /** @return exact observation unit */
    public String getUnit() {
      return unit;
    }

    /** @return optional lower acceptance bound */
    public Double getLowerLimit() {
      return lowerLimit;
    }

    /** @return optional upper acceptance bound */
    public Double getUpperLimit() {
      return upperLimit;
    }

    /** @return explicit deadline basis */
    public DeadlineBasis getDeadlineBasis() {
      return deadlineBasis;
    }

    /** @return fixed event-relative seconds, or null for the case horizon */
    public Double getDeadlineSeconds() {
      return deadlineSeconds;
    }

    /** @return reach or maintained-bound evaluation mode */
    public DynamicScenarioCriterion.EvaluationMode getEvaluationMode() {
      return evaluationMode;
    }

    /** @return whether this criterion contributes to the required scenario verdict */
    public boolean isRequired() {
      return required;
    }

    /**
     * Returns a copy with revised explicit bounds.
     *
     * @param lower optional finite lower bound
     * @param upper optional finite upper bound
     * @return revised immutable criterion
     */
    public CriterionPolicy withAcceptanceRange(Double lower, Double upper) {
      return new CriterionPolicy(id, unit, lower, upper, deadlineBasis, deadlineSeconds, evaluationMode, required);
    }

    /**
     * Returns a copy with a fixed event-relative deadline.
     *
     * @param seconds finite non-negative seconds after the initiating event
     * @return revised immutable criterion
     */
    public CriterionPolicy withDeadlineSeconds(double seconds) {
      return new CriterionPolicy(id, unit, lowerLimit, upperLimit, DeadlineBasis.SECONDS_AFTER_EVENT,
          Double.valueOf(seconds), evaluationMode, required);
    }

    /**
     * Returns a copy using the complete declared case horizon.
     *
     * @return revised immutable criterion
     */
    public CriterionPolicy withCaseHorizonDeadline() {
      return new CriterionPolicy(id, unit, lowerLimit, upperLimit, DeadlineBasis.CASE_HORIZON, null, evaluationMode,
          required);
    }

    /**
     * Returns a copy with revised required/optional status.
     *
     * @param value true when failure shall contribute to the scenario verdict
     * @return revised immutable criterion
     */
    public CriterionPolicy withRequired(boolean value) {
      return new CriterionPolicy(id, unit, lowerLimit, upperLimit, deadlineBasis, deadlineSeconds, evaluationMode,
          value);
    }

    /**
     * Returns a copy with a revised explicit evaluation mode.
     *
     * @param value reach or maintained-bound mode
     * @return revised immutable criterion
     */
    public CriterionPolicy withEvaluationMode(DynamicScenarioCriterion.EvaluationMode value) {
      return new CriterionPolicy(id, unit, lowerLimit, upperLimit, deadlineBasis, deadlineSeconds, value, required);
    }

    /**
     * Resolves the declared deadline against one concrete scenario timing contract.
     *
     * @param definition concrete case timing
     * @return event-relative deadline in seconds
     * @throws IllegalArgumentException if a fixed deadline exceeds the available horizon
     */
    double resolveDeadline(CaseDefinition definition) {
      double horizon = definition.getDurationSeconds() - definition.getEventSeconds();
      double resolved = deadlineBasis == DeadlineBasis.CASE_HORIZON ? horizon : deadlineSeconds.doubleValue();
      if (resolved > horizon + 1.0e-12) {
        throw new IllegalArgumentException(
            "Criterion " + id + " deadline " + resolved + " s exceeds case horizon " + horizon + " s");
      }
      if (evaluationMode == DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS && resolved <= 0.0) {
        throw new IllegalArgumentException("Maintained criterion " + id + " requires a positive resolved deadline");
      }
      return resolved;
    }

    /**
     * Returns a detached ordered representation for audit and fingerprinting.
     *
     * @return strict JSON-shaped policy map
     */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("unit", unit);
      result.put("lowerLimit", lowerLimit);
      result.put("upperLimit", upperLimit);
      result.put("deadlineBasis", deadlineBasis.name());
      result.put("deadlineSeconds", deadlineSeconds);
      result.put("evaluationMode", evaluationMode.name());
      result.put("required", Boolean.valueOf(required));
      return result;
    }
  }

  /**
   * Complete ordered criterion policy for one public compressor profile.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class ProfilePolicy implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Profile profile;
    private final List<CriterionPolicy> criteria;

    /**
     * Creates one complete profile policy.
     *
     * @param profile public compressor profile
     * @param criteria ordered complete criterion list
     * @throws IllegalArgumentException if profile, identity, unit, coverage or order is invalid
     */
    public ProfilePolicy(Profile profile, List<CriterionPolicy> criteria) {
      if (profile == null || criteria == null) {
        throw new IllegalArgumentException("Profile and criterion policies are required");
      }
      List<String> expected = expectedIds(profile);
      List<CriterionPolicy> copy = new ArrayList<CriterionPolicy>(criteria);
      if (copy.size() != expected.size()) {
        throw new IllegalArgumentException("Profile " + profile + " requires criteria " + expected);
      }
      Set<String> seen = new LinkedHashSet<String>();
      for (int index = 0; index < copy.size(); index++) {
        CriterionPolicy criterion = copy.get(index);
        if (criterion == null || !seen.add(criterion.getId())) {
          throw new IllegalArgumentException("Profile " + profile + " contains a null or duplicate criterion");
        }
        if (!expected.get(index).equals(criterion.getId())) {
          throw new IllegalArgumentException("Profile " + profile + " criterion order must be " + expected);
        }
        String unit = expectedUnit(criterion.getId());
        if (!unit.equals(criterion.getUnit())) {
          throw new IllegalArgumentException(
              "Criterion " + criterion.getId() + " requires unit " + unit + ", not " + criterion.getUnit());
        }
      }
      this.profile = profile;
      this.criteria = Collections.unmodifiableList(copy);
    }

    /** @return public compressor profile */
    public Profile getProfile() {
      return profile;
    }

    /** @return complete stable ordered criterion policies */
    public List<CriterionPolicy> getCriteria() {
      return criteria;
    }

    /**
     * Returns a detached ordered representation.
     *
     * @return strict JSON-shaped profile policy
     */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("profile", profile.name());
      List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
      for (CriterionPolicy criterion : criteria) {
        values.add(criterion.toMap());
      }
      result.put("criteria", values);
      return result;
    }
  }

  /**
   * Creates a complete immutable policy from exactly one ordered entry for every public profile.
   *
   * @param profilePolicies complete profile policies
   * @throws IllegalArgumentException if any profile is missing, duplicated or out of enum order
   */
  public CompressorAutomationAcceptancePolicy(List<ProfilePolicy> profilePolicies) {
    if (profilePolicies == null || profilePolicies.size() != Profile.values().length) {
      throw new IllegalArgumentException("Exactly one policy for every public compressor profile is required");
    }
    List<ProfilePolicy> copy = new ArrayList<ProfilePolicy>(profilePolicies);
    Map<Profile, ProfilePolicy> indexed = new LinkedHashMap<Profile, ProfilePolicy>();
    for (int index = 0; index < copy.size(); index++) {
      ProfilePolicy policy = copy.get(index);
      Profile expected = Profile.values()[index];
      if (policy == null || policy.getProfile() != expected || indexed.put(policy.getProfile(), policy) != null) {
        throw new IllegalArgumentException("Profile policies must be complete, unique and ordered by Profile enum");
      }
    }
    profiles = Collections.unmodifiableList(copy);
    byProfile = Collections.unmodifiableMap(indexed);
    fingerprint = sha256(GSON.toJson(toMapWithoutFingerprint()));
  }

  /**
   * Returns the legacy-equivalent, explicit policy for every public compressor profile.
   *
   * @return complete default policy
   */
  public static CompressorAutomationAcceptancePolicy defaultPolicy() {
    List<ProfilePolicy> result = new ArrayList<ProfilePolicy>();
    for (Profile profile : Profile.values()) {
      List<CriterionPolicy> criteria = new ArrayList<CriterionPolicy>();
      criteria.add(horizon("DISCHARGE_ENVELOPE", "bara", 1.0, 68.0, true));
      if (isControlCase(profile)) {
        criteria.add(horizon("SURGE_MARGIN", "fraction", 0.0, null, true));
        criteria.add(horizon("NO_UNWANTED_TRIP", "boolean", 1.0, 1.0, true));
        if (profile != Profile.NORMAL) {
          criteria.add(elapsed("RECYCLE_FLOW", "kg/sec", 0.01, null, 5.0, true));
        }
      } else {
        criteria.add(elapsed("SHAFT_STOP", "rpm", 0.0, 1.0, 4.0, true));
        criteria.add(elapsed("INLET_ISOLATED", "percent", 0.0, 0.1, 2.0, true));
        criteria.add(elapsed("EXPORT_ISOLATED", "percent", 0.0, 0.1, 2.0, true));
        criteria.add(elapsed("RECYCLE_FAIL_OPEN", "percent", 99.9, 100.0, 2.0, true));
        criteria.add(horizon("HOLD_FINAL_ELEMENTS", "boolean", 1.0, 1.0, true));
      }
      criteria.add(horizon("GAS_CV_APPLICABILITY", "boolean", 1.0, 1.0, true));
      criteria.add(horizon("SUCTION_ENERGY", "boolean", 1.0, 1.0, false));
      criteria.add(horizon("DISCHARGE_ENERGY", "boolean", 1.0, 1.0, false));
      result.add(new ProfilePolicy(profile, criteria));
    }
    return new CompressorAutomationAcceptancePolicy(result);
  }

  /**
   * Returns a revised complete policy after replacing existing identities in one profile.
   *
   * @param profile profile owning the criterion identities
   * @param overrides one or more complete replacement settings
   * @return revised immutable complete policy
   * @throws IllegalArgumentException for unknown identities, duplicate overrides or unit mismatches
   */
  public CompressorAutomationAcceptancePolicy withOverrides(Profile profile, CriterionPolicy... overrides) {
    if (profile == null || overrides == null || overrides.length == 0) {
      throw new IllegalArgumentException("A profile and at least one override are required");
    }
    ProfilePolicy current = byProfile.get(profile);
    Map<String, CriterionPolicy> replacements = new LinkedHashMap<String, CriterionPolicy>();
    for (CriterionPolicy override : overrides) {
      if (override == null || replacements.put(override.getId(), override) != null) {
        throw new IllegalArgumentException("Acceptance-policy overrides must be nonnull and unique");
      }
      boolean known = false;
      for (CriterionPolicy existing : current.getCriteria()) {
        known |= existing.getId().equals(override.getId());
      }
      if (!known) {
        throw new IllegalArgumentException("Unknown criterion " + override.getId() + " for profile " + profile);
      }
    }
    List<ProfilePolicy> revised = new ArrayList<ProfilePolicy>();
    for (ProfilePolicy candidate : profiles) {
      if (candidate.getProfile() != profile) {
        revised.add(candidate);
        continue;
      }
      List<CriterionPolicy> criteria = new ArrayList<CriterionPolicy>();
      for (CriterionPolicy existing : candidate.getCriteria()) {
        CriterionPolicy replacement = replacements.get(existing.getId());
        criteria.add(replacement == null ? existing : replacement);
      }
      revised.add(new ProfilePolicy(profile, criteria));
    }
    return new CompressorAutomationAcceptancePolicy(revised);
  }

  /**
   * Returns one stable criterion policy.
   *
   * @param profile owning public profile
   * @param criterionId stable criterion identifier
   * @return immutable criterion policy
   * @throws IllegalArgumentException when the profile does not own the identity
   */
  public CriterionPolicy getCriterion(Profile profile, String criterionId) {
    ProfilePolicy policy = byProfile.get(profile);
    if (policy != null) {
      for (CriterionPolicy criterion : policy.getCriteria()) {
        if (criterion.getId().equals(criterionId)) {
          return criterion;
        }
      }
    }
    throw new IllegalArgumentException("Unknown criterion " + criterionId + " for profile " + profile);
  }

  /**
   * Returns the complete profile policy.
   *
   * @param profile public compressor profile
   * @return immutable complete profile policy
   */
  public ProfilePolicy getProfilePolicy(Profile profile) {
    ProfilePolicy result = byProfile.get(profile);
    if (result == null) {
      throw new IllegalArgumentException("Unknown compressor profile: " + profile);
    }
    return result;
  }

  /**
   * Resolves every criterion deadline for one concrete case without executing or mutating the process model.
   *
   * @param definition concrete case profile and timing
   * @throws IllegalArgumentException if the case is null or a declared deadline exceeds its available horizon
   */
  public void validateForCase(CaseDefinition definition) {
    if (definition == null) {
      throw new IllegalArgumentException("A compressor case definition is required");
    }
    for (CriterionPolicy criterion : getProfilePolicy(definition.getProfile()).getCriteria()) {
      criterion.resolveDeadline(definition);
    }
  }

  /** @return canonical SHA-256 fingerprint of the complete ordered policy */
  public String getFingerprint() {
    return fingerprint;
  }

  /**
   * Returns the complete detached policy representation.
   *
   * @return strict JSON-shaped map including its canonical fingerprint
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = toMapWithoutFingerprint();
    result.put("fingerprintSha256", fingerprint);
    return result;
  }

  /**
   * Returns the canonical fingerprint material without a recursive fingerprint field.
   *
   * @return ordered strict JSON-shaped map
   */
  private Map<String, Object> toMapWithoutFingerprint() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("policyScope", "ACCEPTANCE_ONLY_NO_PHYSICAL_MODEL_OR_EVENT_MUTATION");
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    for (ProfilePolicy policy : profiles) {
      values.add(policy.toMap());
    }
    result.put("profiles", values);
    return result;
  }

  /**
   * Creates a complete-case-horizon maintained-bound criterion.
   *
   * @param id stable identity
   * @param unit exact unit
   * @param lower optional lower bound
   * @param upper optional upper bound
   * @param required required/optional status
   * @return immutable criterion
   */
  private static CriterionPolicy horizon(String id, String unit, Double lower, Double upper, boolean required) {
    return new CriterionPolicy(id, unit, lower, upper, DeadlineBasis.CASE_HORIZON, null,
        DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS, required);
  }

  /**
   * Creates a fixed event-relative reach criterion.
   *
   * @param id stable identity
   * @param unit exact unit
   * @param lower optional lower bound
   * @param upper optional upper bound
   * @param deadlineSeconds finite event-relative deadline
   * @param required required/optional status
   * @return immutable criterion
   */
  private static CriterionPolicy elapsed(String id, String unit, Double lower, Double upper, double deadlineSeconds,
      boolean required) {
    return new CriterionPolicy(id, unit, lower, upper, DeadlineBasis.SECONDS_AFTER_EVENT,
        Double.valueOf(deadlineSeconds), DynamicScenarioCriterion.EvaluationMode.REACH_BY_DEADLINE, required);
  }

  /**
   * Returns stable expected criterion order for one profile.
   *
   * @param profile public compressor profile
   * @return complete ordered identities
   */
  private static List<String> expectedIds(Profile profile) {
    List<String> result = new ArrayList<String>();
    result.add("DISCHARGE_ENVELOPE");
    if (isControlCase(profile)) {
      result.add("SURGE_MARGIN");
      result.add("NO_UNWANTED_TRIP");
      if (profile != Profile.NORMAL) {
        result.add("RECYCLE_FLOW");
      }
    } else {
      result.addAll(
          Arrays.asList("SHAFT_STOP", "INLET_ISOLATED", "EXPORT_ISOLATED", "RECYCLE_FAIL_OPEN", "HOLD_FINAL_ELEMENTS"));
    }
    result.addAll(Arrays.asList("GAS_CV_APPLICABILITY", "SUCTION_ENERGY", "DISCHARGE_ENERGY"));
    return result;
  }

  /**
   * Returns whether a profile exercises running BPCS control rather than commanded shutdown.
   *
   * @param profile public compressor profile
   * @return true for running-control cases
   */
  private static boolean isControlCase(Profile profile) {
    return profile == Profile.NORMAL || profile == Profile.LOAD_REJECTION || profile == Profile.SLOW_RECYCLE
        || profile == Profile.STUCK_RECYCLE || profile == Profile.FT_BIASED_HIGH || profile == Profile.FT_FROZEN
        || profile == Profile.BLOCKED_OUTLET;
  }

  /**
   * Returns the fixed observation unit for a stable criterion identity.
   *
   * @param id stable criterion identity
   * @return exact unit string
   */
  private static String expectedUnit(String id) {
    if ("DISCHARGE_ENVELOPE".equals(id)) {
      return "bara";
    }
    if ("SURGE_MARGIN".equals(id)) {
      return "fraction";
    }
    if ("RECYCLE_FLOW".equals(id)) {
      return "kg/sec";
    }
    if ("SHAFT_STOP".equals(id)) {
      return "rpm";
    }
    if ("INLET_ISOLATED".equals(id) || "EXPORT_ISOLATED".equals(id) || "RECYCLE_FAIL_OPEN".equals(id)) {
      return "percent";
    }
    if ("NO_UNWANTED_TRIP".equals(id) || "HOLD_FINAL_ELEMENTS".equals(id) || "GAS_CV_APPLICABILITY".equals(id)
        || "SUCTION_ENERGY".equals(id) || "DISCHARGE_ENERGY".equals(id)) {
      return "boolean";
    }
    throw new IllegalArgumentException("Unknown compressor acceptance criterion: " + id);
  }

  /**
   * Validates an optional bound.
   *
   * @param value optional bound
   * @param field field name
   */
  private static void validateBound(Double value, String field) {
    if (value != null && !Double.isFinite(value.doubleValue())) {
      throw new IllegalArgumentException(field + " must be finite when present");
    }
  }

  /**
   * Requires nonblank canonical text.
   *
   * @param value candidate text
   * @param field field name
   * @return trimmed text
   */
  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  /**
   * Computes a lower-case SHA-256 digest.
   *
   * @param value canonical UTF-8 fingerprint material
   * @return lower-case hexadecimal digest
   */
  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : digest) {
        result.append(String.format("%02x", Integer.valueOf(item & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is required by every supported Java runtime", failure);
    }
  }
}
