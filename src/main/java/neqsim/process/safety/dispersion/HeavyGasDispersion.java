package neqsim.process.safety.dispersion;

import java.io.Serializable;

/**
 * Britter–McQuaid empirical correlation for continuous dense-gas (heavy) releases.
 *
 * <p>
 * Provides the maximum downwind distance at which the centerline concentration drops to a target
 * dilution ratio C/Co for steady, ground-level continuous releases of denser-than-air gases.
 * Derived from the workbook charts in CCPS Yellow Book / Britter &amp; McQuaid (1988).
 *
 * <p>
 * Use for neutrally buoyant or dense releases (e.g. LPG, propane, CO₂, chlorine) in flat terrain.
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>Britter R.E., McQuaid J. (1988) — Workbook on the dispersion of dense gases. HSE 17/1988</li>
 * <li>CCPS — Guidelines for Vapor Cloud Dispersion Models, 2nd Ed.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class HeavyGasDispersion implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double volumetricRateM3PerS;
  private final double sourceDensityKgPerM3;
  private final double airDensityKgPerM3;
  private final double windSpeedMperS;

  /**
   * Construct a Britter–McQuaid heavy-gas dispersion calculator.
   *
   * @param volumetricRateM3PerS continuous release volumetric flow at source conditions, m³/s
   * @param sourceDensityKgPerM3 source gas density at release conditions, kg/m³
   * @param airDensityKgPerM3 ambient air density, kg/m³ (≈1.20 at 20°C, sea level)
   * @param windSpeedMperS reference wind speed at 10 m, m/s
   */
  public HeavyGasDispersion(double volumetricRateM3PerS, double sourceDensityKgPerM3,
      double airDensityKgPerM3, double windSpeedMperS) {
    if (volumetricRateM3PerS <= 0.0 || sourceDensityKgPerM3 <= 0.0
        || airDensityKgPerM3 <= 0.0 || windSpeedMperS <= 0.0) {
      throw new IllegalArgumentException("All inputs to HeavyGasDispersion must be positive");
    }
    this.volumetricRateM3PerS = volumetricRateM3PerS;
    this.sourceDensityKgPerM3 = sourceDensityKgPerM3;
    this.airDensityKgPerM3 = airDensityKgPerM3;
    this.windSpeedMperS = windSpeedMperS;
  }

  /**
   * Initial buoyancy parameter g0 = g (ρ - ρa) / ρa.
   *
   * @return reduced gravity in m/s²
   */
  public double reducedGravity() {
    return 9.81 * (sourceDensityKgPerM3 - airDensityKgPerM3) / airDensityKgPerM3;
  }

  /**
   * Britter–McQuaid characteristic dimensionless parameter
   * α = (g0² · q / U⁵)^(1/5).
   *
   * @return characteristic parameter (dimensionless)
   */
  public double britterParameter() {
    double g0 = Math.abs(reducedGravity());
    double q = volumetricRateM3PerS;
    double U = windSpeedMperS;
    return Math.pow(g0 * g0 * q / Math.pow(U, 5), 0.2);
  }

  /**
   * Estimate the downwind centerline distance to a target concentration ratio C/Co,
   * where Co is the source volumetric concentration (= 1.0 for pure gas).
   *
   * <p>
   * Implements piece-wise log-log fit of the Britter–McQuaid workbook nomograms
   * (continuous, ground-level, no jet/buoyancy correction).
   *
   * @param concRatio target concentration ratio C/Co in (0, 1)
   * @return downwind distance in m
   */
  public double distanceToConcentrationRatio(double concRatio) {
    if (concRatio <= 0.0 || concRatio >= 1.0) {
      throw new IllegalArgumentException("concRatio must be in (0, 1)");
    }
    double alpha = britterParameter();
    // Britter-McQuaid nomogram fit (continuous): D/L = K * (Co/C)^m
    // with characteristic length L = (q/U)^(1/2)
    double L = Math.sqrt(volumetricRateM3PerS / windSpeedMperS);
    // Log10(C/Co) in [-3 ... -0.5]
    double logC = Math.log10(concRatio);
    // Coefficients tuned to BM continuous nomogram (alpha-band 0.04 ... 1)
    double a = -0.85;
    double b = -0.25 * Math.log10(Math.max(alpha, 0.01));
    double logDoverL = a * logC + b;
    return L * Math.pow(10.0, logDoverL);
  }
}
