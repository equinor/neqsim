package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable DNV-RP-F109 on-bottom stability screening assessment. */
public final class DnvRpF109OnBottomStabilityAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Transparent intermediate results for one load case. */
  public static final class CaseResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String caseId;
    private final double normalCurrentVelocityMPerS;
    private final double normalWaveVelocityMPerS;
    private final double normalWaveAccelerationMPerS2;
    private final double dragLoadNPerM;
    private final double inertiaLoadNPerM;
    private final double liftLoadNPerM;
    private final double factoredHorizontalDemandNPerM;
    private final double lateralResistanceNPerM;
    private final double requiredSubmergedWeightNPerM;
    private final double specificGravity;

    CaseResult(String caseId, double normalCurrentVelocityMPerS, double normalWaveVelocityMPerS,
        double normalWaveAccelerationMPerS2, double dragLoadNPerM, double inertiaLoadNPerM, double liftLoadNPerM,
        double factoredHorizontalDemandNPerM, double lateralResistanceNPerM, double requiredSubmergedWeightNPerM,
        double specificGravity) {
      this.caseId = caseId;
      this.normalCurrentVelocityMPerS = normalCurrentVelocityMPerS;
      this.normalWaveVelocityMPerS = normalWaveVelocityMPerS;
      this.normalWaveAccelerationMPerS2 = normalWaveAccelerationMPerS2;
      this.dragLoadNPerM = dragLoadNPerM;
      this.inertiaLoadNPerM = inertiaLoadNPerM;
      this.liftLoadNPerM = liftLoadNPerM;
      this.factoredHorizontalDemandNPerM = factoredHorizontalDemandNPerM;
      this.lateralResistanceNPerM = lateralResistanceNPerM;
      this.requiredSubmergedWeightNPerM = requiredSubmergedWeightNPerM;
      this.specificGravity = specificGravity;
    }

    /** @return load-case identifier */
    public String getCaseId() {
      return caseId;
    }

    /** @return current velocity component normal to the asset in m/s */
    public double getNormalCurrentVelocityMPerS() {
      return normalCurrentVelocityMPerS;
    }

    /** @return wave velocity component normal to the asset in m/s */
    public double getNormalWaveVelocityMPerS() {
      return normalWaveVelocityMPerS;
    }

    /** @return wave acceleration component normal to the asset in m/s2 */
    public double getNormalWaveAccelerationMPerS2() {
      return normalWaveAccelerationMPerS2;
    }

    /** @return reduced drag load in N/m */
    public double getDragLoadNPerM() {
      return dragLoadNPerM;
    }

    /** @return reduced inertia load in N/m */
    public double getInertiaLoadNPerM() {
      return inertiaLoadNPerM;
    }

    /** @return reduced lift load in N/m */
    public double getLiftLoadNPerM() {
      return liftLoadNPerM;
    }

    /** @return factored horizontal demand in N/m */
    public double getFactoredHorizontalDemandNPerM() {
      return factoredHorizontalDemandNPerM;
    }

    /** @return available lateral resistance in N/m */
    public double getLateralResistanceNPerM() {
      return lateralResistanceNPerM;
    }

    /**
     * Gets the static-screening submerged weight requirement.
     *
     * @return required submerged weight in N/m, or NaN for an external displacement route
     */
    public double getRequiredSubmergedWeightNPerM() {
      return requiredSubmergedWeightNPerM;
    }

    /** @return specific gravity calculated from submerged weight and displaced-water buoyancy */
    public double getSpecificGravity() {
      return specificGravity;
    }

    /** @return immutable intermediate-result map */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("caseId", caseId);
      result.put("normalCurrentVelocityMPerS", Double.valueOf(normalCurrentVelocityMPerS));
      result.put("normalWaveVelocityMPerS", Double.valueOf(normalWaveVelocityMPerS));
      result.put("normalWaveAccelerationMPerS2", Double.valueOf(normalWaveAccelerationMPerS2));
      result.put("dragLoadNPerM", Double.valueOf(dragLoadNPerM));
      result.put("inertiaLoadNPerM", Double.valueOf(inertiaLoadNPerM));
      result.put("liftLoadNPerM", Double.valueOf(liftLoadNPerM));
      result.put("factoredHorizontalDemandNPerM", Double.valueOf(factoredHorizontalDemandNPerM));
      result.put("lateralResistanceNPerM", Double.valueOf(lateralResistanceNPerM));
      result.put("requiredSubmergedWeightNPerM", Double.valueOf(requiredSubmergedWeightNPerM));
      result.put("specificGravity", Double.valueOf(specificGravity));
      return Collections.unmodifiableMap(result);
    }
  }

  private final List<CaseResult> caseResults;
  private final List<DnvRpF109StabilityCheck> checks;
  private final DnvRpF109StabilityCheck governingCheck;

  DnvRpF109OnBottomStabilityAssessment(List<CaseResult> caseResults, List<DnvRpF109StabilityCheck> checks) {
    this.caseResults = Collections.unmodifiableList(new ArrayList<CaseResult>(caseResults));
    this.checks = Collections.unmodifiableList(new ArrayList<DnvRpF109StabilityCheck>(checks));
    DnvRpF109StabilityCheck governing = null;
    for (DnvRpF109StabilityCheck check : checks) {
      if (governing == null || check.getUtilization() > governing.getUtilization()) {
        governing = check;
      }
    }
    governingCheck = governing;
  }

  /** @return immutable intermediate results in input order */
  public List<CaseResult> getCaseResults() {
    return caseResults;
  }

  /** @return immutable limit-state checks in input order */
  public List<DnvRpF109StabilityCheck> getChecks() {
    return checks;
  }

  /** @return highest-utilization check */
  public DnvRpF109StabilityCheck getGoverningCheck() {
    return governingCheck;
  }

  /** @return whether every implemented screening check passes */
  public boolean areAllScreeningChecksPassing() {
    for (DnvRpF109StabilityCheck check : checks) {
      if (check.getVerdict() == DnvRpF109StabilityCheck.Verdict.FAIL) {
        return false;
      }
    }
    return true;
  }

  /** @return always true because a screening result cannot approve an engineering design */
  public boolean isEngineeringApprovalRequired() {
    return true;
  }

  /** @return complete result map */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> caseMaps = new ArrayList<Map<String, Object>>();
    for (CaseResult caseResult : caseResults) {
      caseMaps.add(caseResult.toMap());
    }
    List<Map<String, Object>> checkMaps = new ArrayList<Map<String, Object>>();
    for (DnvRpF109StabilityCheck check : checks) {
      checkMaps.add(check.toMap());
    }
    result.put("caseResults", caseMaps);
    result.put("checks", checkMaps);
    result.put("governingCheck", governingCheck == null ? null : governingCheck.toMap());
    result.put("allScreeningChecksPassing", Boolean.valueOf(areAllScreeningChecksPassing()));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return Collections.unmodifiableMap(result);
  }
}
