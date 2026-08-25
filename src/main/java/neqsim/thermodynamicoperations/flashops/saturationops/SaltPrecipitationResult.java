package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.Serializable;

/**
 * Immutable result from precipitating one pure salt to aqueous activity equilibrium.
 *
 * <p>
 * The dissolved thermodynamic system contains the residual fluid. The precipitated amount reported here completes the
 * material ledger; it is not inserted as a NeqSim solid phase.
 * </p>
 */
public final class SaltPrecipitationResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String saltName;
  private final double precipitatedMoles;
  private final double precipitatedMassGrams;
  private final double initialSaturationRatio;
  private final double finalSaturationRatio;
  private final double maximumIonBalanceResidualMoles;

  SaltPrecipitationResult(String saltName, double precipitatedMoles, double precipitatedMassGrams,
      double initialSaturationRatio, double finalSaturationRatio, double maximumIonBalanceResidualMoles) {
    this.saltName = saltName;
    this.precipitatedMoles = precipitatedMoles;
    this.precipitatedMassGrams = precipitatedMassGrams;
    this.initialSaturationRatio = initialSaturationRatio;
    this.finalSaturationRatio = finalSaturationRatio;
    this.maximumIonBalanceResidualMoles = maximumIonBalanceResidualMoles;
  }

  /** @return salt name from the COMPSALT database */
  public String getSaltName() {
    return saltName;
  }

  /** @return precipitated formula-unit amount in mol */
  public double getPrecipitatedMoles() {
    return precipitatedMoles;
  }

  /** @return precipitated pure-solid mass in g */
  public double getPrecipitatedMassGrams() {
    return precipitatedMassGrams;
  }

  /** @return aqueous saturation ratio before precipitation */
  public double getInitialSaturationRatio() {
    return initialSaturationRatio;
  }

  /** @return aqueous saturation ratio after precipitation */
  public double getFinalSaturationRatio() {
    return finalSaturationRatio;
  }

  /** @return maximum absolute ion ledger residual in mol */
  public double getMaximumIonBalanceResidualMoles() {
    return maximumIonBalanceResidualMoles;
  }

  /** @return whether a positive pure-solid amount was produced */
  public boolean hasPrecipitatedSolid() {
    return precipitatedMoles > 0.0;
  }

  /**
   * Returns the pure-phase complementarity violation in log10 saturation-ratio units.
   *
   * @return {@code abs(log10(SR))} for present solid, or {@code max(log10(SR), 0)} for absent solid
   */
  public double getComplementarityViolation() {
    double logarithmicSaturation = Math.log10(finalSaturationRatio);
    return hasPrecipitatedSolid() ? Math.abs(logarithmicSaturation) : Math.max(logarithmicSaturation, 0.0);
  }
}
