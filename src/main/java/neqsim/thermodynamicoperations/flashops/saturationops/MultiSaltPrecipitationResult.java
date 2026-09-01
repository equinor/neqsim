package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable diagnostics and pure-solid ledger from simultaneous mineral equilibration. */
public final class MultiSaltPrecipitationResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Map<String, SaltPrecipitationResult> mineralResults;
  private final int equilibriumUpdates;
  private final double maximumComplementarityViolation;
  private final double maximumComponentBalanceResidualMoles;
  private final double maximumNormalizedBalanceResidual;

  MultiSaltPrecipitationResult(Map<String, SaltPrecipitationResult> mineralResults, int equilibriumUpdates,
      double maximumComplementarityViolation, double maximumComponentBalanceResidualMoles,
      double maximumNormalizedBalanceResidual) {
    this.mineralResults = Collections
        .unmodifiableMap(new LinkedHashMap<String, SaltPrecipitationResult>(mineralResults));
    this.equilibriumUpdates = equilibriumUpdates;
    this.maximumComplementarityViolation = maximumComplementarityViolation;
    this.maximumComponentBalanceResidualMoles = maximumComponentBalanceResidualMoles;
    this.maximumNormalizedBalanceResidual = maximumNormalizedBalanceResidual;
  }

  /**
   * Returns an immutable defensive copy of the per-mineral solid ledger.
   *
   * @return mineral-name keyed precipitation results in deterministic order
   */
  public Map<String, SaltPrecipitationResult> getMineralResults() {
    return Collections.unmodifiableMap(new LinkedHashMap<String, SaltPrecipitationResult>(mineralResults));
  }

  /**
   * Returns one mineral result.
   *
   * @param mineralName COMPSALT mineral name
   * @return immutable mineral result
   * @throws IllegalArgumentException if the mineral was not requested
   */
  public SaltPrecipitationResult getMineralResult(String mineralName) {
    SaltPrecipitationResult result = mineralResults.get(mineralName);
    if (result == null) {
      throw new IllegalArgumentException("Mineral was not included in the equilibrium: " + mineralName);
    }
    return result;
  }

  /** @return number of precipitation or dissolution updates */
  public int getEquilibriumUpdates() {
    return equilibriumUpdates;
  }

  /** @return maximum pure-mineral complementarity violation in log10 saturation-ratio units */
  public double getMaximumComplementarityViolation() {
    return maximumComplementarityViolation;
  }

  /** @return maximum absolute component or elemental ledger residual in mol */
  public double getMaximumComponentBalanceResidualMoles() {
    return maximumComponentBalanceResidualMoles;
  }

  /**
   * Returns the maximum balance residual divided by its quantity-specific acceptance tolerance.
   *
   * <p>
   * Values at or below one pass. Non-reactive component and element ledgers use an absolute {@code 1e-10 mol}
   * tolerance. Reactive element ledgers additionally allow {@code 1e-8} of the corresponding elemental inventory to
   * accommodate the chemical-equilibrium solver's numerical closure without weakening trace-element checks.
   * </p>
   *
   * @return maximum dimensionless normalized component or element balance residual
   */
  public double getMaximumNormalizedBalanceResidual() {
    return maximumNormalizedBalanceResidual;
  }

  /**
   * Returns the total pure-solid formula mass represented by the solid ledger.
   *
   * <p>
   * The formula mass includes crystallization water when the COMPSALT row explicitly encodes it.
   * </p>
   *
   * @return total COMPSALT ion-formula mass in g
   */
  public double getTotalPrecipitatedMassGrams() {
    double totalMass = 0.0;
    for (SaltPrecipitationResult result : mineralResults.values()) {
      totalMass += result.getPrecipitatedMassGrams();
    }
    return totalMass;
  }
}
