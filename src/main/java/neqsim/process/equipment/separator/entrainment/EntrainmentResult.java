package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;

/**
 * Immutable result of an entrainment / carry-over calculation produced by an
 * {@link EnhancedEntrainmentProvider}. This is the agreed schema across all
 * providers — both the public built-in 7-stage chain and any private plug-in
 * (such as the EQN π-number model) populate the same fields so downstream
 * consumers (process simulation, plant historians, reports) do not need to
 * know which provider produced the numbers.
 *
 * <p>
 * Units are SI:
 * <ul>
 *   <li>Carry-over rates: kg/h</li>
 *   <li>Confidence band: kg/h (one-sigma uncertainty estimate on the
 *       carry-over rate, when the provider can supply one)</li>
 * </ul>
 *
 * <p>
 * Fields that a provider cannot supply are returned as {@link Double#NaN}.
 * In particular, the built-in 7-stage chain returns NaN for
 * {@link #getConfidenceBandKgPerHr()}; providers that have a documented
 * uncertainty model (e.g. the π-number plug-in scaled against the EQN
 * test envelope) return a finite number.
 *
 * <p>
 * <b>Droplet-size data.</b> The unified result intentionally does not
 * carry droplet-size distribution or d50, because empirical large-scale
 * correlations (such as the EQN π-number plug-in, which uses
 * Efficiency = f(π)) do not produce them. Providers that compute droplet
 * size internally (e.g. the built-in 7-stage chain) expose it through
 * their own API, not through this cross-provider result schema.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class EntrainmentResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String providerId;
  private final String providerVersion;
  private final double oilInGasKgPerHr;
  private final double waterInGasKgPerHr;
  private final double gasInLiquidKgPerHr;
  private final double confidenceBandKgPerHr;

  /**
   * Constructs an immutable entrainment result.
   *
   * @param providerId stable identifier of the provider that produced this
   *        result, e.g. {@code "neqsim-7stage"} or {@code "eqn-pi-v1"};
   *        must not be null
   * @param providerVersion version string of the provider, e.g.
   *        {@code "1.0.0"}; must not be null
   * @param oilInGasKgPerHr oil carry-over into the gas outlet [kg/h], or
   *        {@link Double#NaN} if not computed
   * @param waterInGasKgPerHr water carry-over into the gas outlet [kg/h], or
   *        {@link Double#NaN} if not computed
   * @param gasInLiquidKgPerHr gas carry-under into the liquid outlet(s)
   *        [kg/h], or {@link Double#NaN} if not computed
   * @param confidenceBandKgPerHr one-sigma uncertainty estimate on the
   *        carry-over [kg/h], or {@link Double#NaN} if the provider cannot
   *        supply one
   */
  public EntrainmentResult(String providerId, String providerVersion, double oilInGasKgPerHr,
      double waterInGasKgPerHr, double gasInLiquidKgPerHr,
      double confidenceBandKgPerHr) {
    if (providerId == null) {
      throw new IllegalArgumentException("providerId must not be null");
    }
    if (providerVersion == null) {
      throw new IllegalArgumentException("providerVersion must not be null");
    }
    this.providerId = providerId;
    this.providerVersion = providerVersion;
    this.oilInGasKgPerHr = oilInGasKgPerHr;
    this.waterInGasKgPerHr = waterInGasKgPerHr;
    this.gasInLiquidKgPerHr = gasInLiquidKgPerHr;
    this.confidenceBandKgPerHr = confidenceBandKgPerHr;
  }

  /**
   * Returns the stable identifier of the provider that produced this result.
   *
   * @return the provider id (never null)
   */
  public String getProviderId() {
    return providerId;
  }

  /**
   * Returns the version of the provider that produced this result.
   *
   * @return the provider version (never null)
   */
  public String getProviderVersion() {
    return providerVersion;
  }

  /**
   * Returns the oil carry-over into the gas outlet.
   *
   * @return oil carry-over [kg/h], or {@link Double#NaN} if not computed
   */
  public double getOilInGasKgPerHr() {
    return oilInGasKgPerHr;
  }

  /**
   * Returns the water carry-over into the gas outlet.
   *
   * @return water carry-over [kg/h], or {@link Double#NaN} if not computed
   */
  public double getWaterInGasKgPerHr() {
    return waterInGasKgPerHr;
  }

  /**
   * Returns the gas carry-under into the liquid outlet(s).
   *
   * @return gas carry-under [kg/h], or {@link Double#NaN} if not computed
   */
  public double getGasInLiquidKgPerHr() {
    return gasInLiquidKgPerHr;
  }

  /**
   * Returns the one-sigma confidence band on the carry-over rate.
   *
   * @return confidence band [kg/h], or {@link Double#NaN} if the provider
   *         cannot supply one (e.g. the built-in 7-stage chain)
   */
  public double getConfidenceBandKgPerHr() {
    return confidenceBandKgPerHr;
  }

  @Override
  public String toString() {
    return "EntrainmentResult{" + "provider=" + providerId + "@" + providerVersion
        + ", oilInGas=" + oilInGasKgPerHr + " kg/h" + ", waterInGas=" + waterInGasKgPerHr
        + " kg/h" + ", gasInLiquid=" + gasInLiquidKgPerHr + " kg/h" + ", confidence="
        + confidenceBandKgPerHr + " kg/h" + "}";
  }
}
