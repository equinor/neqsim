package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable isolated longitudinal metal-loss pressure-screening result. */
public final class DnvRpF101CorrodedPipelineAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String standardEdition;
  private final double assessmentDefectDepthM;
  private final double remainingWallThicknessM;
  private final double defectDepthToWallRatio;
  private final double normalizedDefectLength;
  private final double lengthCorrectionFactor;
  private final double uncorrodedFailurePressurePa;
  private final double defectPressureReductionFactor;
  private final double calculatedFailurePressurePa;
  private final double callerControlledPressureLimitPa;
  private final double assessedPressureDifferentialPa;
  private final double pressureUtilization;
  private final double pressureMarginPa;
  private final boolean withinCallerControlledPressureLimit;

  DnvRpF101CorrodedPipelineAssessment(DnvRpF101CorrodedPipelineScreeningKernel.Input input) {
    standardEdition = input.getEdition().getDisplayName();
    assessmentDefectDepthM = input.getMeasuredDefectDepthM() + input.getDefectDepthAllowanceM();
    remainingWallThicknessM = input.getAssessmentWallThicknessM() - assessmentDefectDepthM;
    defectDepthToWallRatio = assessmentDefectDepthM / input.getAssessmentWallThicknessM();
    normalizedDefectLength = input.getDefectAxialLengthM()
        / Math.sqrt(input.getSteelOuterDiameterM() * input.getAssessmentWallThicknessM());
    lengthCorrectionFactor = DnvRpF101CorrodedPipelineScreeningKernel.lengthCorrectionFactor(input);
    uncorrodedFailurePressurePa = 2.0 * input.getAssessmentWallThicknessM()
        * input.getCharacteristicUltimateTensileStrengthPa()
        / (input.getSteelOuterDiameterM() - input.getAssessmentWallThicknessM());
    defectPressureReductionFactor = (1.0 - defectDepthToWallRatio)
        / (1.0 - defectDepthToWallRatio / lengthCorrectionFactor);
    calculatedFailurePressurePa = uncorrodedFailurePressurePa * defectPressureReductionFactor;
    callerControlledPressureLimitPa = calculatedFailurePressurePa * input.getCallerControlledPressureFactor();
    assessedPressureDifferentialPa = input.getInternalPressurePaAbsolute() - input.getExternalPressurePaAbsolute();
    pressureUtilization = assessedPressureDifferentialPa / callerControlledPressureLimitPa;
    pressureMarginPa = callerControlledPressureLimitPa - assessedPressureDifferentialPa;
    withinCallerControlledPressureLimit = pressureUtilization <= 1.0;
  }

  /** @return explicit standard edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return measured depth plus caller-controlled allowance in m */
  public double getAssessmentDefectDepthM() {
    return assessmentDefectDepthM;
  }

  /** @return assessment wall thickness minus assessment defect depth in m */
  public double getRemainingWallThicknessM() {
    return remainingWallThicknessM;
  }

  /** @return assessment defect depth divided by assessment wall thickness */
  public double getDefectDepthToWallRatio() {
    return defectDepthToWallRatio;
  }

  /** @return axial defect length divided by square root of diameter times wall thickness */
  public double getNormalizedDefectLength() {
    return normalizedDefectLength;
  }

  /** @return longitudinal length correction factor */
  public double getLengthCorrectionFactor() {
    return lengthCorrectionFactor;
  }

  /** @return calculated failure pressure without metal loss in Pa */
  public double getUncorrodedFailurePressurePa() {
    return uncorrodedFailurePressurePa;
  }

  /** @return calculated metal-loss pressure reduction factor */
  public double getDefectPressureReductionFactor() {
    return defectPressureReductionFactor;
  }

  /** @return deterministic calculated defect failure pressure in Pa */
  public double getCalculatedFailurePressurePa() {
    return calculatedFailurePressurePa;
  }

  /** @return failure pressure multiplied by the caller-controlled pressure factor in Pa */
  public double getCallerControlledPressureLimitPa() {
    return callerControlledPressureLimitPa;
  }

  /** @return internal minus external assessed pressure in Pa */
  public double getAssessedPressureDifferentialPa() {
    return assessedPressureDifferentialPa;
  }

  /** @return assessed differential pressure divided by caller-controlled pressure limit */
  public double getPressureUtilization() {
    return pressureUtilization;
  }

  /** @return caller-controlled pressure limit minus assessed differential pressure in Pa */
  public double getPressureMarginPa() {
    return pressureMarginPa;
  }

  /** @return whether utilization is no greater than one against the caller-controlled factor */
  public boolean isWithinCallerControlledPressureLimit() {
    return withinCallerControlledPressureLimit;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("scope", "ISOLATED_LONGITUDINAL_METAL_LOSS_INTERNAL_PRESSURE_ONLY");
    result.put("assessmentDefectDepthM", Double.valueOf(assessmentDefectDepthM));
    result.put("remainingWallThicknessM", Double.valueOf(remainingWallThicknessM));
    result.put("defectDepthToWallRatio", Double.valueOf(defectDepthToWallRatio));
    result.put("normalizedDefectLength", Double.valueOf(normalizedDefectLength));
    result.put("lengthCorrectionFactor", Double.valueOf(lengthCorrectionFactor));
    result.put("uncorrodedFailurePressurePa", Double.valueOf(uncorrodedFailurePressurePa));
    result.put("defectPressureReductionFactor", Double.valueOf(defectPressureReductionFactor));
    result.put("calculatedFailurePressurePa", Double.valueOf(calculatedFailurePressurePa));
    result.put("callerControlledPressureLimitPa", Double.valueOf(callerControlledPressureLimitPa));
    result.put("assessedPressureDifferentialPa", Double.valueOf(assessedPressureDifferentialPa));
    result.put("pressureUtilization", Double.valueOf(pressureUtilization));
    result.put("pressureMarginPa", Double.valueOf(pressureMarginPa));
    result.put("withinCallerControlledPressureLimit", Boolean.valueOf(withinCallerControlledPressureLimit));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
