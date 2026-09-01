package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.separator.entrainment.DropletSettlingCalculator;

/** Immutable snapshot of an API 12J separator performance screening check. */
public final class Api12JSeparatorAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String standardEdition;
  private final boolean gasLiquidSectionPasses;
  private final boolean liquidSectionPasses;
  private final boolean allScreeningCriteriaPass;
  private final double gravityCutDiameterMicrometre;
  private final double kFactorUtilization;
  private final String gasLiquidFinding;
  private final String liquidFinding;

  Api12JSeparatorAssessment(String standardEdition, DropletSettlingCalculator.ApiComplianceResult result) {
    this.standardEdition = standardEdition;
    gasLiquidSectionPasses = result.gasLiquidSectionCompliant;
    liquidSectionPasses = result.liquidSectionCompliant;
    allScreeningCriteriaPass = result.isFullyCompliant();
    gravityCutDiameterMicrometre = result.gravityCutDiameter_um;
    kFactorUtilization = result.kFactorUtilization;
    gasLiquidFinding = result.gasLiquidComment;
    liquidFinding = result.liquidComment;
  }

  /** @return explicit API 12J edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return whether gas-section screening criteria pass */
  public boolean isGasLiquidSectionPassing() {
    return gasLiquidSectionPasses;
  }

  /** @return whether liquid residence-time screening criteria pass */
  public boolean isLiquidSectionPassing() {
    return liquidSectionPasses;
  }

  /** @return whether every implemented screening criterion passes */
  public boolean areAllScreeningCriteriaPassing() {
    return allScreeningCriteriaPass;
  }

  /** @return gravity cut diameter in micrometres */
  public double getGravityCutDiameterMicrometre() {
    return gravityCutDiameterMicrometre;
  }

  /** @return operating K-factor divided by the implemented screening limit */
  public double getKFactorUtilization() {
    return kFactorUtilization;
  }

  /** @return gas-section finding */
  public String getGasLiquidFinding() {
    return gasLiquidFinding;
  }

  /** @return liquid-section finding */
  public String getLiquidFinding() {
    return liquidFinding;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("gasLiquidSectionPasses", Boolean.valueOf(gasLiquidSectionPasses));
    result.put("liquidSectionPasses", Boolean.valueOf(liquidSectionPasses));
    result.put("allScreeningCriteriaPass", Boolean.valueOf(allScreeningCriteriaPass));
    result.put("gravityCutDiameterMicrometre", Double.valueOf(gravityCutDiameterMicrometre));
    result.put("kFactorUtilization", Double.valueOf(kFactorUtilization));
    result.put("gasLiquidFinding", gasLiquidFinding);
    result.put("liquidFinding", liquidFinding);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
