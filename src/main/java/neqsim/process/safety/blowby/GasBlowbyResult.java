package neqsim.process.safety.blowby;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.blowby.GasBlowbyAnalyzer.BlowbyVerdict;

/**
 * Result of a {@link GasBlowbyAnalyzer} HP-to-LP gas blowby screening.
 *
 * <p>
 * Holds the choked-flow status, the blowby mass and standard-volume rates through the fully open restriction, the
 * downstream relief capacity and the screening verdict that compares the two.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class GasBlowbyResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final boolean choked;
  private final double criticalPressureRatio;
  private final double actualPressureRatio;
  private final double blowbyMassRateKgPerHr;
  private final double blowbyStdVolRateSm3PerHr;
  private final double restrictionAreaM2;
  private final double dischargeCoefficient;
  private final double specificHeatRatio;
  private final double molarMassKgPerMol;
  private final double reliefCapacityKgPerHr;
  private final boolean reliefDataProvided;
  private final double reliefMarginKgPerHr;
  private final boolean reliefAdequate;
  private final BlowbyVerdict verdict;
  private final List<String> warnings;

  /**
   * Construct a gas blowby result.
   *
   * @param choked true if the flow through the restriction is critical (choked)
   * @param criticalPressureRatio critical downstream/upstream pressure ratio for choking
   * @param actualPressureRatio actual downstream/upstream pressure ratio
   * @param blowbyMassRateKgPerHr blowby mass rate through the fully open restriction in kg/h
   * @param blowbyStdVolRateSm3PerHr blowby standard volumetric rate in Sm3/h
   * @param restrictionAreaM2 effective restriction area in m2
   * @param dischargeCoefficient discharge coefficient used
   * @param specificHeatRatio specific-heat ratio (Cp/Cv) used
   * @param molarMassKgPerMol molar mass used in kg/mol
   * @param reliefCapacityKgPerHr downstream relief capacity in kg/h
   * @param reliefDataProvided true if a downstream relief capacity was supplied
   * @param reliefMarginKgPerHr relief capacity minus blowby rate in kg/h; NaN when no relief data supplied
   * @param reliefAdequate true if the downstream relief capacity exceeds the blowby rate
   * @param verdict the screening verdict
   * @param warnings list of warnings; never null
   */
  public GasBlowbyResult(boolean choked, double criticalPressureRatio, double actualPressureRatio,
      double blowbyMassRateKgPerHr, double blowbyStdVolRateSm3PerHr, double restrictionAreaM2,
      double dischargeCoefficient, double specificHeatRatio, double molarMassKgPerMol, double reliefCapacityKgPerHr,
      boolean reliefDataProvided, double reliefMarginKgPerHr, boolean reliefAdequate, BlowbyVerdict verdict,
      List<String> warnings) {
    this.choked = choked;
    this.criticalPressureRatio = criticalPressureRatio;
    this.actualPressureRatio = actualPressureRatio;
    this.blowbyMassRateKgPerHr = blowbyMassRateKgPerHr;
    this.blowbyStdVolRateSm3PerHr = blowbyStdVolRateSm3PerHr;
    this.restrictionAreaM2 = restrictionAreaM2;
    this.dischargeCoefficient = dischargeCoefficient;
    this.specificHeatRatio = specificHeatRatio;
    this.molarMassKgPerMol = molarMassKgPerMol;
    this.reliefCapacityKgPerHr = reliefCapacityKgPerHr;
    this.reliefDataProvided = reliefDataProvided;
    this.reliefMarginKgPerHr = reliefMarginKgPerHr;
    this.reliefAdequate = reliefAdequate;
    this.verdict = verdict;
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Indicates whether the flow through the restriction is choked.
   *
   * @return true if choked (critical) flow
   */
  public boolean isChoked() {
    return choked;
  }

  /**
   * Gets the critical downstream/upstream pressure ratio.
   *
   * @return critical pressure ratio
   */
  public double getCriticalPressureRatio() {
    return criticalPressureRatio;
  }

  /**
   * Gets the actual downstream/upstream pressure ratio.
   *
   * @return actual pressure ratio
   */
  public double getActualPressureRatio() {
    return actualPressureRatio;
  }

  /**
   * Gets the blowby mass rate through the fully open restriction.
   *
   * @return blowby mass rate in kg/h
   */
  public double getBlowbyMassRateKgPerHr() {
    return blowbyMassRateKgPerHr;
  }

  /**
   * Gets the blowby standard volumetric rate.
   *
   * @return blowby rate in Sm3/h
   */
  public double getBlowbyStdVolRateSm3PerHr() {
    return blowbyStdVolRateSm3PerHr;
  }

  /**
   * Gets the effective restriction area.
   *
   * @return restriction area in m2
   */
  public double getRestrictionAreaM2() {
    return restrictionAreaM2;
  }

  /**
   * Gets the discharge coefficient used.
   *
   * @return discharge coefficient
   */
  public double getDischargeCoefficient() {
    return dischargeCoefficient;
  }

  /**
   * Gets the specific-heat ratio used.
   *
   * @return specific-heat ratio (Cp/Cv)
   */
  public double getSpecificHeatRatio() {
    return specificHeatRatio;
  }

  /**
   * Gets the molar mass used.
   *
   * @return molar mass in kg/mol
   */
  public double getMolarMassKgPerMol() {
    return molarMassKgPerMol;
  }

  /**
   * Gets the downstream relief capacity.
   *
   * @return relief capacity in kg/h
   */
  public double getReliefCapacityKgPerHr() {
    return reliefCapacityKgPerHr;
  }

  /**
   * Indicates whether a downstream relief capacity was supplied.
   *
   * @return true if relief data supplied
   */
  public boolean isReliefDataProvided() {
    return reliefDataProvided;
  }

  /**
   * Gets the margin between downstream relief capacity and blowby rate.
   *
   * @return margin in kg/h; negative when relief is insufficient, NaN when no relief data supplied
   */
  public double getReliefMarginKgPerHr() {
    return reliefMarginKgPerHr;
  }

  /**
   * Indicates whether the downstream relief capacity covers the blowby rate.
   *
   * @return true if relief is adequate
   */
  public boolean isReliefAdequate() {
    return reliefAdequate;
  }

  /**
   * Gets the screening verdict.
   *
   * @return the blowby verdict
   */
  public BlowbyVerdict getVerdict() {
    return verdict;
  }

  /**
   * Gets the warnings raised during the screening.
   *
   * @return list of warnings; never null
   */
  public List<String> getWarnings() {
    return warnings;
  }

  /**
   * Serializes this result to a human-readable JSON string.
   *
   * @return JSON representation of this result
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
