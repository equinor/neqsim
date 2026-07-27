package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.mechanicaldesign.compressor.CompressorCasingDesignCalculator;

/** Immutable engineering-workflow snapshot of an API 617 compressor-casing screening calculation. */
public final class Api617CompressorAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String standardEdition;
  private final String materialGrade;
  private final double requiredWallThicknessMm;
  private final double minimumWallThicknessMm;
  private final double selectedWallThicknessMm;
  private final double mawpMPa;
  private final double stressRatio;
  private final double hydroTestPressureMPa;
  private final boolean hydroTestPasses;
  private final int flangeClass;
  private final double flangeRatingBarg;
  private final boolean flangeRatingPasses;
  private final double suctionNozzleAllowableForceN;
  private final double dischargeNozzleAllowableForceN;
  private final double differentialExpansionMm;
  private final boolean thermalGrowthPasses;
  private final boolean splitLineBoltsPass;
  private final String naceStatus;
  private final List<String> appliedStandards;
  private final List<String> designIssues;

  private Api617CompressorAssessment(String standardEdition, CompressorCasingDesignCalculator calculator) {
    this.standardEdition = standardEdition;
    materialGrade = calculator.getMaterialGrade();
    requiredWallThicknessMm = calculator.getRequiredWallThicknessMm();
    minimumWallThicknessMm = calculator.getMinimumWallThicknessMm();
    selectedWallThicknessMm = calculator.getSelectedWallThicknessMm();
    mawpMPa = calculator.getMawpMPa();
    stressRatio = calculator.getStressRatio();
    hydroTestPressureMPa = calculator.getHydroTestPressureMPa();
    hydroTestPasses = calculator.isHydroTestAcceptable();
    flangeClass = calculator.getFlangeClass();
    flangeRatingBarg = calculator.getFlangeRatingBarg();
    flangeRatingPasses = calculator.isFlangeRatingAdequate();
    suctionNozzleAllowableForceN = calculator.getSuctionNozzleAllowableForceN();
    dischargeNozzleAllowableForceN = calculator.getDischargeNozzleAllowableForceN();
    differentialExpansionMm = calculator.getDifferentialExpansionMm();
    thermalGrowthPasses = calculator.isThermalGrowthAcceptable();
    splitLineBoltsPass = calculator.isSplitLineBoltsAdequate();
    naceStatus = calculator.getNaceComplianceStatus();
    appliedStandards = Collections.unmodifiableList(new ArrayList<String>(calculator.getAppliedStandards()));
    designIssues = Collections.unmodifiableList(new ArrayList<String>(calculator.getDesignIssues()));
  }

  static Api617CompressorAssessment from(String edition, CompressorCasingDesignCalculator calculator) {
    return new Api617CompressorAssessment(edition, calculator);
  }

  /** @return explicit API 617 edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return selected material grade */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /** @return pressure-only required wall thickness in millimetres */
  public double getRequiredWallThicknessMm() {
    return requiredWallThicknessMm;
  }

  /** @return minimum wall thickness including corrosion allowance in millimetres */
  public double getMinimumWallThicknessMm() {
    return minimumWallThicknessMm;
  }

  /** @return selected nominal wall thickness in millimetres */
  public double getSelectedWallThicknessMm() {
    return selectedWallThicknessMm;
  }

  /** @return calculated maximum allowable working pressure in MPa */
  public double getMawpMPa() {
    return mawpMPa;
  }

  /** @return hoop stress divided by allowable stress */
  public double getStressRatio() {
    return stressRatio;
  }

  /** @return hydrostatic test pressure in MPa */
  public double getHydroTestPressureMPa() {
    return hydroTestPressureMPa;
  }

  /** @return whether the implemented hydrostatic stress screen passes */
  public boolean isHydroTestPassing() {
    return hydroTestPasses;
  }

  /** @return selected flange pressure class */
  public int getFlangeClass() {
    return flangeClass;
  }

  /** @return selected flange pressure rating in barg */
  public double getFlangeRatingBarg() {
    return flangeRatingBarg;
  }

  /** @return whether the selected flange rating screen passes */
  public boolean isFlangeRatingPassing() {
    return flangeRatingPasses;
  }

  /** @return API 617 suction-nozzle allowable force in newtons */
  public double getSuctionNozzleAllowableForceN() {
    return suctionNozzleAllowableForceN;
  }

  /** @return API 617 discharge-nozzle allowable force in newtons */
  public double getDischargeNozzleAllowableForceN() {
    return dischargeNozzleAllowableForceN;
  }

  /** @return casing-to-rotor differential expansion in millimetres */
  public double getDifferentialExpansionMm() {
    return differentialExpansionMm;
  }

  /** @return whether the implemented differential-expansion screen passes */
  public boolean isThermalGrowthPassing() {
    return thermalGrowthPasses;
  }

  /** @return whether the implemented split-line-bolt screen passes */
  public boolean areSplitLineBoltsPassing() {
    return splitLineBoltsPass;
  }

  /** @return NACE material-screen status */
  public String getNaceStatus() {
    return naceStatus;
  }

  /** @return immutable standards claimed by the legacy calculation */
  public List<String> getAppliedStandards() {
    return appliedStandards;
  }

  /** @return immutable calculation findings */
  public List<String> getDesignIssues() {
    return designIssues;
  }

  /** @return whether every implemented mechanical screen passes */
  public boolean areAllScreeningChecksPassing() {
    return hydroTestPasses && flangeRatingPasses && thermalGrowthPasses && splitLineBoltsPass && designIssues.isEmpty();
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("materialGrade", materialGrade);
    result.put("requiredWallThicknessMm", Double.valueOf(requiredWallThicknessMm));
    result.put("minimumWallThicknessMm", Double.valueOf(minimumWallThicknessMm));
    result.put("selectedWallThicknessMm", Double.valueOf(selectedWallThicknessMm));
    result.put("mawpMPa", Double.valueOf(mawpMPa));
    result.put("stressRatio", Double.valueOf(stressRatio));
    result.put("hydroTestPressureMPa", Double.valueOf(hydroTestPressureMPa));
    result.put("hydroTestPasses", Boolean.valueOf(hydroTestPasses));
    result.put("flangeClass", Integer.valueOf(flangeClass));
    result.put("flangeRatingBarg", Double.valueOf(flangeRatingBarg));
    result.put("flangeRatingPasses", Boolean.valueOf(flangeRatingPasses));
    result.put("suctionNozzleAllowableForceN", Double.valueOf(suctionNozzleAllowableForceN));
    result.put("dischargeNozzleAllowableForceN", Double.valueOf(dischargeNozzleAllowableForceN));
    result.put("differentialExpansionMm", Double.valueOf(differentialExpansionMm));
    result.put("thermalGrowthPasses", Boolean.valueOf(thermalGrowthPasses));
    result.put("splitLineBoltsPass", Boolean.valueOf(splitLineBoltsPass));
    result.put("naceStatus", naceStatus);
    result.put("appliedStandards", new ArrayList<String>(appliedStandards));
    result.put("designIssues", new ArrayList<String>(designIssues));
    result.put("allScreeningChecksPass", Boolean.valueOf(areAllScreeningChecksPassing()));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
