package neqsim.process.safety.selfheating;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Result of a {@link PorousMediaSelfHeatingAnalyzer} Frank-Kamenetskii criticality screening.
 *
 * <p>
 * Reports the dimensionless criticality parameter against its shape-dependent critical value, together with the two
 * engineering answers that follow from it: the <b>critical boundary temperature</b> for the given body size, and the
 * <b>critical size</b> for the given boundary temperature. The second is often the more useful, because insulation
 * thickness is usually easier to change than process temperature.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PorousMediaSelfHeatingResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final SelfHeatingGeometry geometry;
  private final double characteristicDimensionM;
  private final double effectiveConductivityWPerMK;
  private final double activationEnergyJPerMol;
  private final double volumetricPreFactorWPerM3;
  private final double boundaryTemperatureK;
  private final double delta;
  private final double deltaCrit;
  private final double deltaRatio;
  private final double criticalTemperatureK;
  private final double criticalDimensionM;
  private final double temperatureMarginK;
  private final double dimensionMarginM;
  private final double fkTemperatureScaleK;
  private final SelfHeatingVerdict verdict;
  private final List<String> warnings;

  /**
   * Construct a self-heating criticality result.
   *
   * @param geometry the body shape used
   * @param characteristicDimensionM characteristic half-dimension in m
   * @param effectiveConductivityWPerMK effective thermal conductivity in W/(m K)
   * @param activationEnergyJPerMol apparent activation energy in J/mol
   * @param volumetricPreFactorWPerM3 volumetric heat-release pre-exponential factor in W/m3
   * @param boundaryTemperatureK boundary temperature in K
   * @param delta the Frank-Kamenetskii criticality parameter, dimensionless
   * @param deltaCrit the critical value of delta for the shape, dimensionless
   * @param deltaRatio ratio delta / deltaCrit, dimensionless
   * @param criticalTemperatureK critical boundary temperature for the given size in K, NaN if not bracketed
   * @param criticalDimensionM critical half-dimension at the given boundary temperature in m
   * @param temperatureMarginK critical temperature minus boundary temperature in K, positive when safe
   * @param dimensionMarginM critical dimension minus actual dimension in m, positive when safe
   * @param fkTemperatureScaleK the Frank-Kamenetskii temperature scale R*Ta^2/E in K
   * @param verdict the screening verdict
   * @param warnings list of warnings and configuration notes; may be null
   */
  public PorousMediaSelfHeatingResult(SelfHeatingGeometry geometry, double characteristicDimensionM,
      double effectiveConductivityWPerMK, double activationEnergyJPerMol, double volumetricPreFactorWPerM3,
      double boundaryTemperatureK, double delta, double deltaCrit, double deltaRatio, double criticalTemperatureK,
      double criticalDimensionM, double temperatureMarginK, double dimensionMarginM, double fkTemperatureScaleK,
      SelfHeatingVerdict verdict, List<String> warnings) {
    this.geometry = geometry;
    this.characteristicDimensionM = characteristicDimensionM;
    this.effectiveConductivityWPerMK = effectiveConductivityWPerMK;
    this.activationEnergyJPerMol = activationEnergyJPerMol;
    this.volumetricPreFactorWPerM3 = volumetricPreFactorWPerM3;
    this.boundaryTemperatureK = boundaryTemperatureK;
    this.delta = delta;
    this.deltaCrit = deltaCrit;
    this.deltaRatio = deltaRatio;
    this.criticalTemperatureK = criticalTemperatureK;
    this.criticalDimensionM = criticalDimensionM;
    this.temperatureMarginK = temperatureMarginK;
    this.dimensionMarginM = dimensionMarginM;
    this.fkTemperatureScaleK = fkTemperatureScaleK;
    this.verdict = verdict;
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Gets the body shape used in the analysis.
   *
   * @return the geometry
   */
  public SelfHeatingGeometry getGeometry() {
    return geometry;
  }

  /**
   * Gets the characteristic half-dimension of the body.
   *
   * @return characteristic dimension in m
   */
  public double getCharacteristicDimensionM() {
    return characteristicDimensionM;
  }

  /**
   * Gets the effective thermal conductivity of the wetted porous medium.
   *
   * @return effective thermal conductivity in W/(m K)
   */
  public double getEffectiveConductivityWPerMK() {
    return effectiveConductivityWPerMK;
  }

  /**
   * Gets the apparent activation energy of the self-heating reaction.
   *
   * @return activation energy in J/mol
   */
  public double getActivationEnergyJPerMol() {
    return activationEnergyJPerMol;
  }

  /**
   * Gets the volumetric heat-release pre-exponential factor.
   *
   * @return volumetric heat-release pre-exponential factor in W/m3
   */
  public double getVolumetricPreFactorWPerM3() {
    return volumetricPreFactorWPerM3;
  }

  /**
   * Gets the boundary temperature the body is held at.
   *
   * @return boundary temperature in K
   */
  public double getBoundaryTemperatureK() {
    return boundaryTemperatureK;
  }

  /**
   * Gets the Frank-Kamenetskii criticality parameter.
   *
   * @return delta, dimensionless
   */
  public double getDelta() {
    return delta;
  }

  /**
   * Gets the critical value of the criticality parameter for the body shape.
   *
   * @return critical delta, dimensionless
   */
  public double getDeltaCrit() {
    return deltaCrit;
  }

  /**
   * Gets the ratio of the criticality parameter to its critical value. Values above 1 indicate that no steady state
   * exists.
   *
   * @return delta / deltaCrit, dimensionless
   */
  public double getDeltaRatio() {
    return deltaRatio;
  }

  /**
   * Gets the critical boundary temperature above which the body of the given size self-ignites.
   *
   * @return critical boundary temperature in K, or NaN if it could not be bracketed
   */
  public double getCriticalTemperatureK() {
    return criticalTemperatureK;
  }

  /**
   * Gets the critical half-dimension above which the body at the given boundary temperature self-ignites.
   *
   * @return critical half-dimension in m
   */
  public double getCriticalDimensionM() {
    return criticalDimensionM;
  }

  /**
   * Gets the margin between the critical boundary temperature and the actual boundary temperature.
   *
   * @return temperature margin in K, positive when subcritical, or NaN if the critical temperature is unknown
   */
  public double getTemperatureMarginK() {
    return temperatureMarginK;
  }

  /**
   * Gets the margin between the critical half-dimension and the actual half-dimension.
   *
   * @return dimension margin in m, positive when subcritical
   */
  public double getDimensionMarginM() {
    return dimensionMarginM;
  }

  /**
   * Gets the Frank-Kamenetskii temperature scale {@code R * Ta^2 / E}, the natural scale of self-heating temperature
   * excess at the boundary temperature.
   *
   * @return temperature scale in K
   */
  public double getFkTemperatureScaleK() {
    return fkTemperatureScaleK;
  }

  /**
   * Gets the screening verdict.
   *
   * @return the verdict
   */
  public SelfHeatingVerdict getVerdict() {
    return verdict;
  }

  /**
   * Reports whether the body is predicted to self-ignite.
   *
   * @return true if the verdict is {@link SelfHeatingVerdict#SELF_IGNITION}
   */
  public boolean isSelfIgnitionPredicted() {
    return verdict == SelfHeatingVerdict.SELF_IGNITION;
  }

  /**
   * Gets the warnings and configuration notes recorded during the analysis.
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
