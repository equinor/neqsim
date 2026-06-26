package neqsim.process.safety.pump;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.pump.PumpDeadheadAnalyzer.DeadheadVerdict;

/**
 * Result of a {@link PumpDeadheadAnalyzer} pump deadhead and minimum-flow screening.
 *
 * <p>
 * Holds the estimated shut-off (deadhead) discharge pressure, the comparison against the protected pressure rating and
 * the minimum-flow recirculation temperature rise that supports the "No Flow" HAZOP cause for centrifugal pumps.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PumpDeadheadResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double suctionPressureBara;
  private final double normalDischargePressureBara;
  private final double shutoffHeadRatio;
  private final double ratedDifferentialBara;
  private final double deadheadPressureBara;
  private final double protectedPressureRatingBara;
  private final boolean ratingDataProvided;
  private final double pressureMarginBara;
  private final boolean pressureRatingExceeded;
  private final double shutoffHeadM;
  private final double liquidDensityKgPerM3;
  private final double specificHeatJPerKgK;
  private final double minimumFlowEfficiency;
  private final double recirculationTempRiseK;
  private final boolean tempRiseDataAvailable;
  private final double maxAllowableTempRiseK;
  private final boolean tempRiseExceeded;
  private final DeadheadVerdict verdict;
  private final List<String> warnings;

  /**
   * Construct a pump deadhead result.
   *
   * @param suctionPressureBara pump suction pressure in bara
   * @param normalDischargePressureBara normal (rated) discharge pressure in bara
   * @param shutoffHeadRatio shut-off head as a multiple of rated differential head
   * @param ratedDifferentialBara rated differential pressure in bar
   * @param deadheadPressureBara estimated deadhead (shut-off) discharge pressure in bara
   * @param protectedPressureRatingBara protected system or casing pressure rating in bara; NaN when not supplied
   * @param ratingDataProvided true if a protected pressure rating was supplied
   * @param pressureMarginBara rating minus deadhead pressure in bar; negative when exceeded, NaN when no rating
   * supplied
   * @param pressureRatingExceeded true if the deadhead pressure exceeds the protected rating
   * @param shutoffHeadM shut-off differential head in metres of liquid; NaN when density unknown
   * @param liquidDensityKgPerM3 liquid density used in kg/m3; NaN when not available
   * @param specificHeatJPerKgK liquid specific heat used in J/(kg K); NaN when not available
   * @param minimumFlowEfficiency pump efficiency assumed at minimum continuous flow
   * @param recirculationTempRiseK minimum-flow recirculation temperature rise in K; NaN when properties unavailable
   * @param tempRiseDataAvailable true if the temperature rise could be evaluated
   * @param maxAllowableTempRiseK maximum allowable temperature rise in K; NaN when not supplied
   * @param tempRiseExceeded true if the recirculation temperature rise exceeds the allowable
   * @param verdict the screening verdict
   * @param warnings list of warnings; never null
   */
  public PumpDeadheadResult(double suctionPressureBara, double normalDischargePressureBara, double shutoffHeadRatio,
      double ratedDifferentialBara, double deadheadPressureBara, double protectedPressureRatingBara,
      boolean ratingDataProvided, double pressureMarginBara, boolean pressureRatingExceeded, double shutoffHeadM,
      double liquidDensityKgPerM3, double specificHeatJPerKgK, double minimumFlowEfficiency,
      double recirculationTempRiseK, boolean tempRiseDataAvailable, double maxAllowableTempRiseK,
      boolean tempRiseExceeded, DeadheadVerdict verdict, List<String> warnings) {
    this.suctionPressureBara = suctionPressureBara;
    this.normalDischargePressureBara = normalDischargePressureBara;
    this.shutoffHeadRatio = shutoffHeadRatio;
    this.ratedDifferentialBara = ratedDifferentialBara;
    this.deadheadPressureBara = deadheadPressureBara;
    this.protectedPressureRatingBara = protectedPressureRatingBara;
    this.ratingDataProvided = ratingDataProvided;
    this.pressureMarginBara = pressureMarginBara;
    this.pressureRatingExceeded = pressureRatingExceeded;
    this.shutoffHeadM = shutoffHeadM;
    this.liquidDensityKgPerM3 = liquidDensityKgPerM3;
    this.specificHeatJPerKgK = specificHeatJPerKgK;
    this.minimumFlowEfficiency = minimumFlowEfficiency;
    this.recirculationTempRiseK = recirculationTempRiseK;
    this.tempRiseDataAvailable = tempRiseDataAvailable;
    this.maxAllowableTempRiseK = maxAllowableTempRiseK;
    this.tempRiseExceeded = tempRiseExceeded;
    this.verdict = verdict;
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Gets the pump suction pressure.
   *
   * @return suction pressure in bara
   */
  public double getSuctionPressureBara() {
    return suctionPressureBara;
  }

  /**
   * Gets the normal (rated) discharge pressure.
   *
   * @return normal discharge pressure in bara
   */
  public double getNormalDischargePressureBara() {
    return normalDischargePressureBara;
  }

  /**
   * Gets the shut-off head ratio used.
   *
   * @return shut-off head as a multiple of rated differential head
   */
  public double getShutoffHeadRatio() {
    return shutoffHeadRatio;
  }

  /**
   * Gets the rated differential pressure.
   *
   * @return rated differential pressure in bar
   */
  public double getRatedDifferentialBara() {
    return ratedDifferentialBara;
  }

  /**
   * Gets the estimated deadhead (shut-off) discharge pressure.
   *
   * @return deadhead pressure in bara
   */
  public double getDeadheadPressureBara() {
    return deadheadPressureBara;
  }

  /**
   * Gets the protected system or casing pressure rating.
   *
   * @return protected pressure rating in bara; NaN when not supplied
   */
  public double getProtectedPressureRatingBara() {
    return protectedPressureRatingBara;
  }

  /**
   * Indicates whether a protected pressure rating was supplied.
   *
   * @return true if rating data supplied
   */
  public boolean isRatingDataProvided() {
    return ratingDataProvided;
  }

  /**
   * Gets the margin between the protected rating and the deadhead pressure.
   *
   * @return margin in bar; negative when exceeded, NaN when no rating supplied
   */
  public double getPressureMarginBara() {
    return pressureMarginBara;
  }

  /**
   * Indicates whether the deadhead pressure exceeds the protected rating.
   *
   * @return true if the rating is exceeded
   */
  public boolean isPressureRatingExceeded() {
    return pressureRatingExceeded;
  }

  /**
   * Gets the shut-off differential head in metres of liquid.
   *
   * @return shut-off head in m; NaN when density unknown
   */
  public double getShutoffHeadM() {
    return shutoffHeadM;
  }

  /**
   * Gets the liquid density used.
   *
   * @return density in kg/m3; NaN when not available
   */
  public double getLiquidDensityKgPerM3() {
    return liquidDensityKgPerM3;
  }

  /**
   * Gets the liquid specific heat used.
   *
   * @return specific heat in J/(kg K); NaN when not available
   */
  public double getSpecificHeatJPerKgK() {
    return specificHeatJPerKgK;
  }

  /**
   * Gets the pump efficiency assumed at minimum continuous flow.
   *
   * @return minimum-flow efficiency (0,1]
   */
  public double getMinimumFlowEfficiency() {
    return minimumFlowEfficiency;
  }

  /**
   * Gets the minimum-flow recirculation temperature rise.
   *
   * @return temperature rise in K; NaN when properties unavailable
   */
  public double getRecirculationTempRiseK() {
    return recirculationTempRiseK;
  }

  /**
   * Indicates whether the recirculation temperature rise could be evaluated.
   *
   * @return true if temperature rise data is available
   */
  public boolean isTempRiseDataAvailable() {
    return tempRiseDataAvailable;
  }

  /**
   * Gets the maximum allowable temperature rise.
   *
   * @return allowable temperature rise in K; NaN when not supplied
   */
  public double getMaxAllowableTempRiseK() {
    return maxAllowableTempRiseK;
  }

  /**
   * Indicates whether the recirculation temperature rise exceeds the allowable.
   *
   * @return true if the temperature rise is excessive
   */
  public boolean isTempRiseExceeded() {
    return tempRiseExceeded;
  }

  /**
   * Gets the screening verdict.
   *
   * @return the deadhead verdict
   */
  public DeadheadVerdict getVerdict() {
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
