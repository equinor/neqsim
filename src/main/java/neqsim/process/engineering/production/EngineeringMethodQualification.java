package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Controlled maturity and applicability record for one engineering calculation method version. */
public final class EngineeringMethodQualification implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Level {
    SCREENING, BENCHMARKED, INDEPENDENTLY_VERIFIED, PROJECT_QUALIFIED
  }

  /** Controlled purposes for which a method version may be applied. */
  public enum IntendedUse {
    SCREENING, CONCEPT_SELECTION, PRELIMINARY_ENGINEERING, FEED_SUPPORT, OPERATIONS_ADVISORY
  }

  /** Declared scalar benchmark acceptance criterion for a method output. */
  public static final class AcceptanceCriterion implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String metric;
    private final String unit;
    private final double absoluteTolerance;
    private final double relativeTolerance;

    AcceptanceCriterion(String metric, String unit, double absoluteTolerance, double relativeTolerance) {
      this.metric = text(metric, "metric");
      this.unit = text(unit, "unit");
      if (!Double.isFinite(absoluteTolerance) || !Double.isFinite(relativeTolerance) || absoluteTolerance < 0.0
          || relativeTolerance < 0.0) {
        throw new IllegalArgumentException("acceptance tolerances must be finite and non-negative");
      }
      this.absoluteTolerance = absoluteTolerance;
      this.relativeTolerance = relativeTolerance;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("metric", metric);
      result.put("unit", unit);
      result.put("absoluteTolerance", Double.valueOf(absoluteTolerance));
      result.put("relativeTolerance", Double.valueOf(relativeTolerance));
      return result;
    }
  }

  private final String methodId;
  private final String version;
  private final Level level;
  private final List<String> standardReferences = new ArrayList<String>();
  private final List<String> applicabilityLimits = new ArrayList<String>();
  private final List<String> evidenceReferences = new ArrayList<String>();
  private final Set<IntendedUse> qualifiedUses = EnumSet.noneOf(IntendedUse.class);
  private final List<AcceptanceCriterion> acceptanceCriteria = new ArrayList<AcceptanceCriterion>();
  private EngineeringMethodApplicabilityEnvelope applicabilityEnvelope;
  private String approvalRecord = "";

  public EngineeringMethodQualification(String methodId, String version, Level level) {
    this.methodId = text(methodId, "methodId");
    this.version = text(version, "version");
    if (level == null) {
      throw new IllegalArgumentException("level must not be null");
    }
    this.level = level;
  }

  public EngineeringMethodQualification addStandardReference(String value) {
    addUnique(standardReferences, value, "standardReference");
    return this;
  }

  public EngineeringMethodQualification addApplicabilityLimit(String value) {
    addUnique(applicabilityLimits, value, "applicabilityLimit");
    return this;
  }

  public EngineeringMethodQualification addEvidenceReference(String value) {
    addUnique(evidenceReferences, value, "evidenceReference");
    return this;
  }

  /** Attaches the machine-evaluable service envelope governed by this qualification. */
  public EngineeringMethodQualification applicabilityEnvelope(EngineeringMethodApplicabilityEnvelope value) {
    if (value == null) {
      throw new IllegalArgumentException("applicabilityEnvelope must not be null");
    }
    applicabilityEnvelope = value;
    return this;
  }

  /** Adds an intended use explicitly covered by the controlled qualification. */
  public EngineeringMethodQualification qualifyFor(IntendedUse value) {
    if (value == null) {
      throw new IllegalArgumentException("intendedUse must not be null");
    }
    qualifiedUses.add(value);
    return this;
  }

  /** Adds the acceptance tolerance that controlled benchmark cases must demonstrate. */
  public EngineeringMethodQualification addAcceptanceCriterion(String metric, String unit, double absoluteTolerance,
      double relativeTolerance) {
    acceptanceCriteria.add(new AcceptanceCriterion(metric, unit, absoluteTolerance, relativeTolerance));
    return this;
  }

  public EngineeringMethodQualification approve(String value) {
    approvalRecord = text(value, "approvalRecord");
    return this;
  }

  public String getMethodId() {
    return methodId;
  }

  public String getMethodKey() {
    return methodId + "@" + version;
  }

  public Set<IntendedUse> getQualifiedUses() {
    return Collections.unmodifiableSet(qualifiedUses);
  }

  public EngineeringMethodApplicabilityEnvelope getApplicabilityEnvelope() {
    return applicabilityEnvelope;
  }

  public boolean isProjectQualified() {
    return level == Level.PROJECT_QUALIFIED && !standardReferences.isEmpty() && !applicabilityLimits.isEmpty()
        && !evidenceReferences.isEmpty() && !approvalRecord.isEmpty();
  }

  /** @return true when the legacy record also contains complete machine-evaluable industrial controls */
  public boolean isIndustrialQualificationComplete() {
    return isProjectQualified() && applicabilityEnvelope != null && applicabilityEnvelope.isComplete()
        && !qualifiedUses.isEmpty() && !acceptanceCriteria.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("methodId", methodId);
    result.put("version", version);
    result.put("level", level.name());
    result.put("standardReferences", new ArrayList<String>(standardReferences));
    result.put("applicabilityLimits", new ArrayList<String>(applicabilityLimits));
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    List<String> uses = new ArrayList<String>();
    for (IntendedUse use : qualifiedUses) {
      uses.add(use.name());
    }
    result.put("qualifiedUses", uses);
    List<Map<String, Object>> criteria = new ArrayList<Map<String, Object>>();
    for (AcceptanceCriterion criterion : acceptanceCriteria) {
      criteria.add(criterion.toMap());
    }
    result.put("acceptanceCriteria", criteria);
    result.put("applicabilityEnvelope", applicabilityEnvelope == null ? null : applicabilityEnvelope.toMap());
    result.put("approvalRecord", approvalRecord);
    result.put("projectQualified", Boolean.valueOf(isProjectQualified()));
    result.put("industrialQualificationComplete", Boolean.valueOf(isIndustrialQualificationComplete()));
    return result;
  }

  private static void addUnique(List<String> target, String value, String field) {
    String item = text(value, field);
    if (!target.contains(item)) {
      target.add(item);
    }
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
