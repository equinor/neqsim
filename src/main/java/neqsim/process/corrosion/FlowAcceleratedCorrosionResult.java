package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Result of a {@link FlowAcceleratedCorrosion} screening calculation.
 *
 * <p>
 * The susceptibility index is meaningful only in comparison with another case. The most useful output for an
 * investigation is usually {@link #getDominantFactor()}, which names the lever that actually controls the outcome, and
 * {@link #ratioTo(FlowAcceleratedCorrosionResult)}, which quantifies the effect of a proposed change.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class FlowAcceleratedCorrosionResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double velocityMs;
  private final double hydraulicDiameterM;
  private final double temperatureC;
  private final double inSituPH;
  private final FacGeometry geometry;
  private final double chromiumMassPercent;
  private final double reynolds;
  private final double schmidt;
  private final double sherwood;
  private final double massTransferCoefficientMs;
  private final double wallShearStressPa;
  private final double temperatureFactor;
  private final double phFactor;
  private final double geometryFactor;
  private final double chromiumFactor;
  private final double susceptibilityIndex;
  private final List<String> warnings;

  /**
   * Construct a FAC screening result.
   *
   * @param velocityMs bulk velocity in m/s
   * @param hydraulicDiameterM hydraulic diameter in m
   * @param temperatureC local temperature in C
   * @param inSituPH in-situ pH at operating temperature
   * @param geometry local geometry class
   * @param chromiumMassPercent chromium content of the steel in wt%
   * @param reynolds Reynolds number, dimensionless
   * @param schmidt Schmidt number, dimensionless
   * @param sherwood Sherwood number, dimensionless
   * @param massTransferCoefficientMs mass-transfer coefficient in m/s
   * @param wallShearStressPa wall shear stress in Pa
   * @param temperatureFactor temperature factor, dimensionless
   * @param phFactor pH factor, dimensionless
   * @param geometryFactor geometry enhancement factor, dimensionless
   * @param chromiumFactor chromium factor, dimensionless
   * @param susceptibilityIndex the combined screening index
   * @param warnings list of warnings; may be null
   */
  public FlowAcceleratedCorrosionResult(double velocityMs, double hydraulicDiameterM, double temperatureC,
      double inSituPH, FacGeometry geometry, double chromiumMassPercent, double reynolds, double schmidt,
      double sherwood, double massTransferCoefficientMs, double wallShearStressPa, double temperatureFactor,
      double phFactor, double geometryFactor, double chromiumFactor, double susceptibilityIndex,
      List<String> warnings) {
    this.velocityMs = velocityMs;
    this.hydraulicDiameterM = hydraulicDiameterM;
    this.temperatureC = temperatureC;
    this.inSituPH = inSituPH;
    this.geometry = geometry;
    this.chromiumMassPercent = chromiumMassPercent;
    this.reynolds = reynolds;
    this.schmidt = schmidt;
    this.sherwood = sherwood;
    this.massTransferCoefficientMs = massTransferCoefficientMs;
    this.wallShearStressPa = wallShearStressPa;
    this.temperatureFactor = temperatureFactor;
    this.phFactor = phFactor;
    this.geometryFactor = geometryFactor;
    this.chromiumFactor = chromiumFactor;
    this.susceptibilityIndex = susceptibilityIndex;
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Gets the bulk velocity.
   *
   * @return velocity in m/s
   */
  public double getVelocityMs() {
    return velocityMs;
  }

  /**
   * Gets the hydraulic diameter.
   *
   * @return hydraulic diameter in m
   */
  public double getHydraulicDiameterM() {
    return hydraulicDiameterM;
  }

  /**
   * Gets the local temperature.
   *
   * @return temperature in C
   */
  public double getTemperatureC() {
    return temperatureC;
  }

  /**
   * Gets the in-situ pH used.
   *
   * @return in-situ pH at operating temperature
   */
  public double getInSituPH() {
    return inSituPH;
  }

  /**
   * Gets the local geometry class.
   *
   * @return the geometry
   */
  public FacGeometry getGeometry() {
    return geometry;
  }

  /**
   * Gets the chromium content of the steel.
   *
   * @return chromium content in wt%
   */
  public double getChromiumMassPercent() {
    return chromiumMassPercent;
  }

  /**
   * Gets the Reynolds number.
   *
   * @return Reynolds number, dimensionless
   */
  public double getReynolds() {
    return reynolds;
  }

  /**
   * Gets the Schmidt number.
   *
   * @return Schmidt number, dimensionless
   */
  public double getSchmidt() {
    return schmidt;
  }

  /**
   * Gets the Sherwood number.
   *
   * @return Sherwood number, dimensionless
   */
  public double getSherwood() {
    return sherwood;
  }

  /**
   * Gets the mass-transfer coefficient, the rate-limiting transport property for FAC.
   *
   * @return mass-transfer coefficient in m/s
   */
  public double getMassTransferCoefficientMs() {
    return massTransferCoefficientMs;
  }

  /**
   * Gets the wall shear stress. Reported for context: shear scales roughly with the square of velocity, so a small
   * velocity exceedance is a much larger shear exceedance.
   *
   * @return wall shear stress in Pa
   */
  public double getWallShearStressPa() {
    return wallShearStressPa;
  }

  /**
   * Gets the temperature factor.
   *
   * @return temperature factor, dimensionless, maximum 1 at the solubility peak
   */
  public double getTemperatureFactor() {
    return temperatureFactor;
  }

  /**
   * Gets the pH factor.
   *
   * @return pH factor, dimensionless
   */
  public double getPhFactor() {
    return phFactor;
  }

  /**
   * Gets the geometry enhancement factor.
   *
   * @return geometry factor, dimensionless
   */
  public double getGeometryFactor() {
    return geometryFactor;
  }

  /**
   * Gets the chromium factor.
   *
   * @return chromium factor, dimensionless
   */
  public double getChromiumFactor() {
    return chromiumFactor;
  }

  /**
   * Gets the combined screening susceptibility index. Only ratios between cases are meaningful.
   *
   * @return the susceptibility index
   */
  public double getSusceptibilityIndex() {
    return susceptibilityIndex;
  }

  /**
   * Gets the ratio of this susceptibility index to another case, quantifying the effect of a change.
   *
   * @param other the case to compare against; must not be null and must have a positive index
   * @return this index divided by the other index
   * @throws IllegalArgumentException if the comparison case is null or has a non-positive index
   */
  public double ratioTo(FlowAcceleratedCorrosionResult other) {
    if (other == null) {
      throw new IllegalArgumentException("Comparison result must not be null");
    }
    if (!(other.getSusceptibilityIndex() > 0.0)) {
      throw new IllegalArgumentException("Comparison result must have a positive susceptibility index");
    }
    return susceptibilityIndex / other.getSusceptibilityIndex();
  }

  /**
   * Identifies which of the four dimensionless factors deviates furthest from unity, and is therefore the lever with
   * the most leverage on the outcome.
   *
   * @return the name of the dominant factor: "temperature", "pH", "geometry" or "chromium"
   */
  public String getDominantFactor() {
    double bestScore = Math.abs(Math.log(temperatureFactor));
    String best = "temperature";
    if (Math.abs(Math.log(phFactor)) > bestScore) {
      bestScore = Math.abs(Math.log(phFactor));
      best = "pH";
    }
    if (Math.abs(Math.log(geometryFactor)) > bestScore) {
      bestScore = Math.abs(Math.log(geometryFactor));
      best = "geometry";
    }
    if (Math.abs(Math.log(chromiumFactor)) > bestScore) {
      best = "chromium";
    }
    return best;
  }

  /**
   * Gets the warnings recorded during the calculation.
   *
   * @return an unmodifiable list of warnings; never null
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Serialise this result to pretty-printed JSON.
   *
   * @return a JSON representation of the result
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(this);
  }
}
