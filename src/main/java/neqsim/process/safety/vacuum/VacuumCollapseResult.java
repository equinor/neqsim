package neqsim.process.safety.vacuum;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.vacuum.VacuumCollapseAnalyzer.CoolingPoint;
import neqsim.process.safety.vacuum.VacuumCollapseAnalyzer.VacuumVerdict;

/**
 * Result of a {@link VacuumCollapseAnalyzer} constant-volume cooling screening.
 *
 * <p>
 * Summarises the minimum internal pressure reached on cooldown, the vacuum depth relative to atmospheric pressure, the
 * margin to the vessel external-pressure (vacuum) rating, the verdict, and a screening-level make-up (pad-gas /
 * vacuum-breaker) requirement to restore a chosen set point. The full constant-volume cooling curve is retained for
 * plotting and traceability.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class VacuumCollapseResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double initialPressureBara;
  private final double initialTemperatureK;
  private final double coldEndTemperatureK;
  private final double finalPressureBara;
  private final double minimumPressureBara;
  private final double minimumPressureTemperatureK;
  private final double atmosphericPressureBara;
  private final double externalRatingBara;
  private final boolean ratingProvided;
  private final double vacuumDepthBar;
  private final double marginToRatingBar;
  private final boolean vacuumPresent;
  private final boolean withinRating;
  private final VacuumVerdict verdict;
  private final double makeupSetpointBara;
  private final double makeupGasMoles;
  private final double makeupGasMassKg;
  private final double makeupGasMolarMass;
  private final List<CoolingPoint> coolingCurve;
  private final List<String> warnings;

  /**
   * Construct a vacuum-collapse result.
   *
   * @param initialPressureBara initial blocked-in pressure in bara
   * @param initialTemperatureK initial blocked-in temperature in K
   * @param coldEndTemperatureK cold-end temperature in K
   * @param finalPressureBara internal pressure at the cold-end temperature in bara
   * @param minimumPressureBara minimum internal pressure over the cooling sweep in bara
   * @param minimumPressureTemperatureK temperature at which the minimum pressure occurs in K
   * @param atmosphericPressureBara atmospheric reference pressure in bara
   * @param externalRatingBara vessel external-pressure (vacuum) rating in bara
   * @param ratingProvided true if the external-pressure rating was explicitly supplied
   * @param vacuumDepthBar vacuum depth below atmospheric in bar (clamped at 0)
   * @param marginToRatingBar minimum internal pressure minus the external rating in bar
   * @param vacuumPresent true if a vacuum develops
   * @param withinRating true if the minimum pressure stays at or above the external rating
   * @param verdict the screening verdict
   * @param makeupSetpointBara make-up / pad-gas set point in bara
   * @param makeupGasMoles screening make-up gas requirement in mol
   * @param makeupGasMassKg screening make-up gas requirement in kg
   * @param makeupGasMolarMass make-up gas molar mass used in kg/mol
   * @param coolingCurve constant-volume cooling curve points
   * @param warnings list of warnings; never null
   */
  public VacuumCollapseResult(double initialPressureBara, double initialTemperatureK, double coldEndTemperatureK,
      double finalPressureBara, double minimumPressureBara, double minimumPressureTemperatureK,
      double atmosphericPressureBara, double externalRatingBara, boolean ratingProvided, double vacuumDepthBar,
      double marginToRatingBar, boolean vacuumPresent, boolean withinRating, VacuumVerdict verdict,
      double makeupSetpointBara, double makeupGasMoles, double makeupGasMassKg, double makeupGasMolarMass,
      List<CoolingPoint> coolingCurve, List<String> warnings) {
    this.initialPressureBara = initialPressureBara;
    this.initialTemperatureK = initialTemperatureK;
    this.coldEndTemperatureK = coldEndTemperatureK;
    this.finalPressureBara = finalPressureBara;
    this.minimumPressureBara = minimumPressureBara;
    this.minimumPressureTemperatureK = minimumPressureTemperatureK;
    this.atmosphericPressureBara = atmosphericPressureBara;
    this.externalRatingBara = externalRatingBara;
    this.ratingProvided = ratingProvided;
    this.vacuumDepthBar = vacuumDepthBar;
    this.marginToRatingBar = marginToRatingBar;
    this.vacuumPresent = vacuumPresent;
    this.withinRating = withinRating;
    this.verdict = verdict;
    this.makeupSetpointBara = makeupSetpointBara;
    this.makeupGasMoles = makeupGasMoles;
    this.makeupGasMassKg = makeupGasMassKg;
    this.makeupGasMolarMass = makeupGasMolarMass;
    this.coolingCurve = coolingCurve != null ? coolingCurve : new ArrayList<CoolingPoint>();
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Gets the initial blocked-in pressure.
   *
   * @return initial pressure in bara
   */
  public double getInitialPressureBara() {
    return initialPressureBara;
  }

  /**
   * Gets the initial blocked-in temperature.
   *
   * @return initial temperature in K
   */
  public double getInitialTemperatureK() {
    return initialTemperatureK;
  }

  /**
   * Gets the cold-end temperature.
   *
   * @return cold-end temperature in K
   */
  public double getColdEndTemperatureK() {
    return coldEndTemperatureK;
  }

  /**
   * Gets the internal pressure at the cold-end temperature.
   *
   * @return final pressure in bara
   */
  public double getFinalPressureBara() {
    return finalPressureBara;
  }

  /**
   * Gets the minimum internal pressure over the cooling sweep.
   *
   * @return minimum pressure in bara
   */
  public double getMinimumPressureBara() {
    return minimumPressureBara;
  }

  /**
   * Gets the temperature at which the minimum pressure occurs.
   *
   * @return temperature at minimum pressure in K
   */
  public double getMinimumPressureTemperatureK() {
    return minimumPressureTemperatureK;
  }

  /**
   * Gets the atmospheric reference pressure.
   *
   * @return atmospheric pressure in bara
   */
  public double getAtmosphericPressureBara() {
    return atmosphericPressureBara;
  }

  /**
   * Gets the vessel external-pressure (vacuum) rating.
   *
   * @return external-pressure rating in bara
   */
  public double getExternalRatingBara() {
    return externalRatingBara;
  }

  /**
   * Indicates whether an external-pressure rating was explicitly supplied.
   *
   * @return true if a rating was supplied
   */
  public boolean isRatingProvided() {
    return ratingProvided;
  }

  /**
   * Gets the vacuum depth below atmospheric pressure.
   *
   * @return vacuum depth in bar (clamped at 0)
   */
  public double getVacuumDepthBar() {
    return vacuumDepthBar;
  }

  /**
   * Gets the margin between the minimum internal pressure and the external rating.
   *
   * @return margin in bar; negative when the rating is exceeded
   */
  public double getMarginToRatingBar() {
    return marginToRatingBar;
  }

  /**
   * Indicates whether a vacuum develops.
   *
   * @return true if the minimum pressure is below atmospheric
   */
  public boolean isVacuumPresent() {
    return vacuumPresent;
  }

  /**
   * Indicates whether the minimum pressure stays within the external rating.
   *
   * @return true if the minimum pressure is at or above the external rating
   */
  public boolean isWithinRating() {
    return withinRating;
  }

  /**
   * Gets the screening verdict.
   *
   * @return the vacuum verdict
   */
  public VacuumVerdict getVerdict() {
    return verdict;
  }

  /**
   * Gets the make-up / pad-gas set point.
   *
   * @return make-up set point in bara
   */
  public double getMakeupSetpointBara() {
    return makeupSetpointBara;
  }

  /**
   * Gets the screening make-up gas requirement.
   *
   * @return make-up gas in mol
   */
  public double getMakeupGasMoles() {
    return makeupGasMoles;
  }

  /**
   * Gets the screening make-up gas requirement by mass.
   *
   * @return make-up gas in kg
   */
  public double getMakeupGasMassKg() {
    return makeupGasMassKg;
  }

  /**
   * Gets the make-up gas molar mass used in the screening estimate.
   *
   * @return make-up gas molar mass in kg/mol
   */
  public double getMakeupGasMolarMass() {
    return makeupGasMolarMass;
  }

  /**
   * Gets the constant-volume cooling curve.
   *
   * @return list of cooling-curve points
   */
  public List<CoolingPoint> getCoolingCurve() {
    return coolingCurve;
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
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
