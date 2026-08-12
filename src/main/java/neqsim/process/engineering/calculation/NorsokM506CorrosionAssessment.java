package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.corrosion.NorsokM506CorrosionRate;

/** Immutable snapshot of a NORSOK M-506 CO2-corrosion screening calculation. */
public final class NorsokM506CorrosionAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String standardEdition;
  private final double co2PartialPressureBar;
  private final double co2FugacityBar;
  private final double effectivePH;
  private final double baselineCorrosionRateMmPerYear;
  private final double correctedCorrosionRateMmPerYear;
  private final double scalingTemperatureC;
  private final double wallShearStressPa;
  private final double phCorrectionFactor;
  private final double scaleCorrectionFactor;
  private final double flowCorrectionFactor;
  private final double glycolCorrectionFactor;
  private final double projectedUniformWallLossMm;
  private final boolean calculatedPHUsed;
  private final boolean feCO3FilmExtensionUsed;

  NorsokM506CorrosionAssessment(NorsokM506CorrosionDesignKernel.Input input, NorsokM506CorrosionRate model) {
    standardEdition = input.getEdition().getDisplayName();
    co2PartialPressureBar = input.getTotalPressureBara() * input.getCO2MoleFraction();
    co2FugacityBar = model.getCO2FugacityBar();
    effectivePH = model.getEffectivePH();
    baselineCorrosionRateMmPerYear = model.getBaselineCorrosionRate();
    correctedCorrosionRateMmPerYear = model.getCorrectedCorrosionRate();
    scalingTemperatureC = model.getScalingTemperatureC();
    wallShearStressPa = model.getWallShearStressPa();
    phCorrectionFactor = model.getPHCorrectionFactor();
    scaleCorrectionFactor = model.getScaleCorrectionFactor();
    flowCorrectionFactor = model.getFlowCorrectionFactor();
    glycolCorrectionFactor = model.getGlycolCorrectionFactor();
    projectedUniformWallLossMm = correctedCorrosionRateMmPerYear * input.getExposureYears();
    calculatedPHUsed = Double.isNaN(input.getActualPH());
    feCO3FilmExtensionUsed = input.getFeCO3SaturationRatio() > 0.0;
  }

  /** @return explicit standard edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return CO2 partial pressure in bar */
  public double getCO2PartialPressureBar() {
    return co2PartialPressureBar;
  }

  /** @return calculated CO2 fugacity in bar */
  public double getCO2FugacityBar() {
    return co2FugacityBar;
  }

  /** @return pH used by the corrosion calculation */
  public double getEffectivePH() {
    return effectivePH;
  }

  /** @return uncorrected corrosion rate in mm/year */
  public double getBaselineCorrosionRateMmPerYear() {
    return baselineCorrosionRateMmPerYear;
  }

  /** @return corrected corrosion rate in mm/year */
  public double getCorrectedCorrosionRateMmPerYear() {
    return correctedCorrosionRateMmPerYear;
  }

  /** @return calculated FeCO3 scaling temperature in degrees Celsius */
  public double getScalingTemperatureC() {
    return scalingTemperatureC;
  }

  /** @return calculated wall shear stress in pascals */
  public double getWallShearStressPa() {
    return wallShearStressPa;
  }

  /** @return pH correction factor */
  public double getPHCorrectionFactor() {
    return phCorrectionFactor;
  }

  /** @return scale correction factor */
  public double getScaleCorrectionFactor() {
    return scaleCorrectionFactor;
  }

  /** @return flow correction factor */
  public double getFlowCorrectionFactor() {
    return flowCorrectionFactor;
  }

  /** @return glycol correction factor */
  public double getGlycolCorrectionFactor() {
    return glycolCorrectionFactor;
  }

  /**
   * Get the nominal uniform wall loss over the requested exposure.
   *
   * <p>
   * This value is rate multiplied by time. It is not a code corrosion allowance or an acceptance decision.
   * </p>
   *
   * @return projected uniform wall loss in millimetres
   */
  public double getProjectedUniformWallLossMm() {
    return projectedUniformWallLossMm;
  }

  /** @return whether the legacy internal pH estimate was used */
  public boolean isCalculatedPHUsed() {
    return calculatedPHUsed;
  }

  /** @return whether the optional NeqSim FeCO3 saturation-ratio extension was used */
  public boolean isFeCO3FilmExtensionUsed() {
    return feCO3FilmExtensionUsed;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("co2PartialPressureBar", Double.valueOf(co2PartialPressureBar));
    result.put("co2FugacityBar", Double.valueOf(co2FugacityBar));
    result.put("effectivePH", Double.valueOf(effectivePH));
    result.put("baselineCorrosionRateMmPerYear", Double.valueOf(baselineCorrosionRateMmPerYear));
    result.put("correctedCorrosionRateMmPerYear", Double.valueOf(correctedCorrosionRateMmPerYear));
    result.put("scalingTemperatureC", Double.valueOf(scalingTemperatureC));
    result.put("wallShearStressPa", Double.valueOf(wallShearStressPa));
    result.put("phCorrectionFactor", Double.valueOf(phCorrectionFactor));
    result.put("scaleCorrectionFactor", Double.valueOf(scaleCorrectionFactor));
    result.put("flowCorrectionFactor", Double.valueOf(flowCorrectionFactor));
    result.put("glycolCorrectionFactor", Double.valueOf(glycolCorrectionFactor));
    result.put("projectedUniformWallLossMm", Double.valueOf(projectedUniformWallLossMm));
    result.put("calculatedPHUsed", Boolean.valueOf(calculatedPHUsed));
    result.put("feCO3FilmExtensionUsed", Boolean.valueOf(feCO3FilmExtensionUsed));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
