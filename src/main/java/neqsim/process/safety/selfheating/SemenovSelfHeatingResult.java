package neqsim.process.safety.selfheating;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Result of a {@link SemenovSelfHeatingAnalyzer} criticality screening.
 *
 * @author ESOL
 * @version 1.0
 */
public class SemenovSelfHeatingResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double volumeM3;
  private final double surfaceAreaM2;
  private final double heatTransferCoefficientWPerM2K;
  private final double activationEnergyJPerMol;
  private final double volumetricPreFactorWPerM3;
  private final double ambientTemperatureK;
  private final double psi;
  private final double psiCrit;
  private final double psiRatio;
  private final double criticalTemperatureK;
  private final double temperatureMarginK;
  private final double criticalTemperatureRiseK;
  private final SelfHeatingVerdict verdict;
  private final List<String> warnings;

  /**
   * Construct a Semenov self-heating result.
   *
   * @param volumeM3 reacting volume in m3
   * @param surfaceAreaM2 heat-loss surface area in m2
   * @param heatTransferCoefficientWPerM2K external heat-transfer coefficient in W/(m2 K)
   * @param activationEnergyJPerMol apparent activation energy in J/mol
   * @param volumetricPreFactorWPerM3 volumetric heat-release pre-exponential factor in W/m3
   * @param ambientTemperatureK ambient temperature in K
   * @param psi the Semenov criticality parameter, dimensionless
   * @param psiCrit the critical Semenov parameter (1/e), dimensionless
   * @param psiRatio ratio psi / psiCrit, dimensionless
   * @param criticalTemperatureK critical ambient temperature in K, NaN if not bracketed
   * @param temperatureMarginK critical temperature minus ambient temperature in K, positive when safe
   * @param criticalTemperatureRiseK steady self-heating excess at criticality R*Ta^2/E in K
   * @param verdict the screening verdict
   * @param warnings list of warnings; may be null
   */
  public SemenovSelfHeatingResult(double volumeM3, double surfaceAreaM2, double heatTransferCoefficientWPerM2K,
      double activationEnergyJPerMol, double volumetricPreFactorWPerM3, double ambientTemperatureK, double psi,
      double psiCrit, double psiRatio, double criticalTemperatureK, double temperatureMarginK,
      double criticalTemperatureRiseK, SelfHeatingVerdict verdict, List<String> warnings) {
    this.volumeM3 = volumeM3;
    this.surfaceAreaM2 = surfaceAreaM2;
    this.heatTransferCoefficientWPerM2K = heatTransferCoefficientWPerM2K;
    this.activationEnergyJPerMol = activationEnergyJPerMol;
    this.volumetricPreFactorWPerM3 = volumetricPreFactorWPerM3;
    this.ambientTemperatureK = ambientTemperatureK;
    this.psi = psi;
    this.psiCrit = psiCrit;
    this.psiRatio = psiRatio;
    this.criticalTemperatureK = criticalTemperatureK;
    this.temperatureMarginK = temperatureMarginK;
    this.criticalTemperatureRiseK = criticalTemperatureRiseK;
    this.verdict = verdict;
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Gets the reacting volume.
   *
   * @return volume in m3
   */
  public double getVolumeM3() {
    return volumeM3;
  }

  /**
   * Gets the heat-loss surface area.
   *
   * @return surface area in m2
   */
  public double getSurfaceAreaM2() {
    return surfaceAreaM2;
  }

  /**
   * Gets the external heat-transfer coefficient.
   *
   * @return heat-transfer coefficient in W/(m2 K)
   */
  public double getHeatTransferCoefficientWPerM2K() {
    return heatTransferCoefficientWPerM2K;
  }

  /**
   * Gets the apparent activation energy.
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
   * Gets the ambient temperature.
   *
   * @return ambient temperature in K
   */
  public double getAmbientTemperatureK() {
    return ambientTemperatureK;
  }

  /**
   * Gets the Semenov criticality parameter.
   *
   * @return psi, dimensionless
   */
  public double getPsi() {
    return psi;
  }

  /**
   * Gets the critical Semenov parameter.
   *
   * @return critical psi (1/e), dimensionless
   */
  public double getPsiCrit() {
    return psiCrit;
  }

  /**
   * Gets the ratio of the Semenov parameter to its critical value. Values above 1 indicate that no steady state exists.
   *
   * @return psi / psiCrit, dimensionless
   */
  public double getPsiRatio() {
    return psiRatio;
  }

  /**
   * Gets the critical ambient temperature above which the body self-ignites.
   *
   * @return critical ambient temperature in K, or NaN if it could not be bracketed
   */
  public double getCriticalTemperatureK() {
    return criticalTemperatureK;
  }

  /**
   * Gets the margin between the critical ambient temperature and the actual ambient temperature.
   *
   * @return temperature margin in K, positive when subcritical, or NaN if the critical temperature is unknown
   */
  public double getTemperatureMarginK() {
    return temperatureMarginK;
  }

  /**
   * Gets the steady self-heating temperature excess at criticality, {@code R * Ta^2 / E}. This is how much hotter than
   * its surroundings the body runs at the point of criticality, and is typically small enough to escape notice.
   *
   * @return critical temperature rise in K
   */
  public double getCriticalTemperatureRiseK() {
    return criticalTemperatureRiseK;
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
   * Gets the warnings recorded during the analysis.
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
