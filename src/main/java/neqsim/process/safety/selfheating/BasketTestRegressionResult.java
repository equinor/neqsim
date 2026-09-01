package neqsim.process.safety.selfheating;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Result of a {@link BasketTestRegression} least-squares fit of hot-storage test data.
 *
 * <p>
 * Carries the fitted Arrhenius parameters and the quality of the fit. Use {@link #createAnalyzer} to feed the fitted
 * parameters straight into a full-scale criticality screening, which is the normal reason for doing the regression:
 * laboratory baskets are small, and the question is always whether the same material is safe at plant scale.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BasketTestRegressionResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final int pointCount;
  private final double slope;
  private final double intercept;
  private final double rSquared;
  private final double activationEnergyJPerMol;
  private final double volumetricPreFactorWPerM3;
  private final double effectiveConductivityWPerMK;
  private final boolean conductivityProvided;
  private final List<String> warnings;

  /**
   * Construct a basket-test regression result.
   *
   * @param pointCount number of observations used in the fit
   * @param slope fitted slope of ln(deltaCrit*Tc^2/r^2) versus 1/Tc, equal to -E/R, in K
   * @param intercept fitted intercept, equal to ln(E*P/(lambda*R)), dimensionless
   * @param rSquared coefficient of determination, or NaN if undefined
   * @param activationEnergyJPerMol fitted apparent activation energy in J/mol
   * @param volumetricPreFactorWPerM3 fitted volumetric heat-release pre-exponential factor in W/m3, NaN if the
   * effective thermal conductivity was not supplied
   * @param effectiveConductivityWPerMK effective thermal conductivity used in W/(m K)
   * @param conductivityProvided true if an effective thermal conductivity was supplied
   * @param warnings list of warnings; may be null
   */
  public BasketTestRegressionResult(int pointCount, double slope, double intercept, double rSquared,
      double activationEnergyJPerMol, double volumetricPreFactorWPerM3, double effectiveConductivityWPerMK,
      boolean conductivityProvided, List<String> warnings) {
    this.pointCount = pointCount;
    this.slope = slope;
    this.intercept = intercept;
    this.rSquared = rSquared;
    this.activationEnergyJPerMol = activationEnergyJPerMol;
    this.volumetricPreFactorWPerM3 = volumetricPreFactorWPerM3;
    this.effectiveConductivityWPerMK = effectiveConductivityWPerMK;
    this.conductivityProvided = conductivityProvided;
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Gets the number of observations used in the fit.
   *
   * @return number of basket-test points
   */
  public int getPointCount() {
    return pointCount;
  }

  /**
   * Gets the fitted slope of the linearised criticality relation.
   *
   * @return slope, equal to -E/R, in K
   */
  public double getSlope() {
    return slope;
  }

  /**
   * Gets the fitted intercept of the linearised criticality relation.
   *
   * @return intercept, equal to ln(E*P/(lambda*R)), dimensionless
   */
  public double getIntercept() {
    return intercept;
  }

  /**
   * Gets the coefficient of determination of the fit.
   *
   * @return R-squared, or NaN if undefined
   */
  public double getRSquared() {
    return rSquared;
  }

  /**
   * Gets the fitted apparent activation energy.
   *
   * @return activation energy in J/mol
   */
  public double getActivationEnergyJPerMol() {
    return activationEnergyJPerMol;
  }

  /**
   * Gets the fitted volumetric heat-release pre-exponential factor.
   *
   * @return volumetric heat-release pre-exponential factor in W/m3, or NaN if the effective thermal conductivity was
   * not supplied
   */
  public double getVolumetricPreFactorWPerM3() {
    return volumetricPreFactorWPerM3;
  }

  /**
   * Gets the effective thermal conductivity used to split the intercept.
   *
   * @return effective thermal conductivity in W/(m K), or NaN if not supplied
   */
  public double getEffectiveConductivityWPerMK() {
    return effectiveConductivityWPerMK;
  }

  /**
   * Reports whether an effective thermal conductivity was supplied.
   *
   * @return true if the volumetric heat-release pre-factor could be resolved
   */
  public boolean isConductivityProvided() {
    return conductivityProvided;
  }

  /**
   * Gets the warnings recorded during the fit.
   *
   * @return an unmodifiable list of warnings; never null
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Build a criticality analyzer pre-loaded with the fitted kinetic parameters, for screening a full-scale body.
   *
   * @param geometry shape of the full-scale body; must not be null
   * @param characteristicDimension characteristic half-dimension of the full-scale body; must be positive
   * @param dimensionUnit length unit of the dimension ("m", "cm", "mm" or "in")
   * @param boundaryTemperature boundary temperature the full-scale body is held at
   * @param temperatureUnit temperature unit ("K" or "C")
   * @return a configured analyzer ready for {@link PorousMediaSelfHeatingAnalyzer#analyze()}
   * @throws IllegalStateException if the fit did not resolve usable kinetic parameters
   */
  public PorousMediaSelfHeatingAnalyzer createAnalyzer(SelfHeatingGeometry geometry, double characteristicDimension,
      String dimensionUnit, double boundaryTemperature, String temperatureUnit) {
    if (!(activationEnergyJPerMol > 0.0) || Double.isNaN(volumetricPreFactorWPerM3)
        || !(volumetricPreFactorWPerM3 > 0.0)) {
      throw new IllegalStateException(
          "Regression did not resolve usable kinetic parameters; supply an effective thermal conductivity and check "
              + "that the fitted activation energy is positive");
    }
    PorousMediaSelfHeatingAnalyzer analyzer = new PorousMediaSelfHeatingAnalyzer();
    analyzer.setGeometry(geometry);
    analyzer.setCharacteristicDimension(characteristicDimension, dimensionUnit);
    analyzer.setEffectiveThermalConductivity(effectiveConductivityWPerMK);
    analyzer.setActivationEnergy(activationEnergyJPerMol, "J/mol");
    analyzer.setVolumetricHeatReleasePreFactor(volumetricPreFactorWPerM3);
    analyzer.setBoundaryTemperature(boundaryTemperature, temperatureUnit);
    return analyzer;
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
