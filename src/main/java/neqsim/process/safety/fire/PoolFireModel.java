package neqsim.process.safety.fire;

import java.io.Serializable;

/**
 * Pool-fire thermal radiation model for liquid hydrocarbon fires (API 521 / Mudan).
 *
 * <p>
 * Computes total burn rate, flame height (Thomas correlation), surface emissive power (SEP), and view-factor-weighted
 * incident heat flux for a prescribed pool diameter and mass burning rate per unit area.
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>API STD 521, §4.6 Pool fires</li>
 * <li>Mudan K.S. (1984) — Thermal radiation hazards from hydrocarbon pool fires. Prog. Energy Combust. Sci. 10,
 * 59–80</li>
 * <li>Thomas P.H. (1963) — The size of flames from natural fires. 9th Symp. Combustion</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class PoolFireModel implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double poolDiameterM;
  private final double burnRateKgPerM2PerS;
  private double airDensityKgPerM3;

  /**
   * Construct a pool-fire model.
   *
   * @param poolDiameterM equivalent circular pool diameter in m
   * @param burnRateKgPerM2PerS mass burning rate per unit area, kg/(m²·s) (typical 0.04 LNG, 0.055 gasoline, 0.078
   * crude oil)
   * @throws IllegalArgumentException if the diameter or burning rate is non-positive
   */
  public PoolFireModel(double poolDiameterM, double burnRateKgPerM2PerS) {
    this(requirePositive(poolDiameterM, "Pool diameter"), requirePositive(burnRateKgPerM2PerS, "Burning rate"), 1.20);
  }

  /**
   * Initialize a pool-fire model after checking its prescribed inputs.
   *
   * @param poolDiameterM equivalent circular pool diameter in m
   * @param burnRateKgPerM2PerS mass burning rate per unit area, kg/(m²·s)
   * @param airDensityKgPerM3 ambient air density in kg/m³
   */
  private PoolFireModel(double poolDiameterM, double burnRateKgPerM2PerS, double airDensityKgPerM3) {
    this.poolDiameterM = poolDiameterM;
    this.burnRateKgPerM2PerS = burnRateKgPerM2PerS;
    this.airDensityKgPerM3 = airDensityKgPerM3;
  }

  /**
   * Check a prescribed pool parameter before creating the model.
   *
   * @param value parameter value
   * @param name parameter name for the diagnostic
   * @return checked parameter value
   * @throws IllegalArgumentException if the value is non-positive
   */
  private static double requirePositive(double value, String name) {
    if (value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  /**
   * Construct a pool-fire model using the legacy signature. Mudan's smoke-corrected surface emissive power is a
   * function of the pool diameter.
   *
   * @param poolDiameterM equivalent circular pool diameter in m
   * @param burnRateKgPerM2PerS mass burning rate per unit area, kg/(m²·s)
   * @param heatOfCombustionJPerKg unused legacy parameter, J/kg
   * @param liquidDensityKgPerM3 unused legacy parameter, kg/m³
   * @param radiativeFraction unused legacy parameter
   * @throws IllegalArgumentException if the diameter or burning rate is non-positive
   * @deprecated Use {@link #PoolFireModel(double, double)} with the two inputs used by the correlation.
   */
  @Deprecated
  public PoolFireModel(double poolDiameterM, double burnRateKgPerM2PerS, double heatOfCombustionJPerKg,
      double liquidDensityKgPerM3, double radiativeFraction) {
    this(poolDiameterM, burnRateKgPerM2PerS);
  }

  /**
   * Set ambient air density (default 1.20 kg/m³ at 20°C, sea level).
   *
   * @param rhoAir air density in kg/m³
   * @return this model for chaining
   */
  public PoolFireModel setAirDensity(double rhoAir) {
    this.airDensityKgPerM3 = rhoAir;
    return this;
  }

  /**
   * Total mass-burning rate over the pool (kg/s).
   *
   * @return total burn rate in kg/s
   */
  public double totalBurnRateKgPerS() {
    double area = 0.25 * Math.PI * poolDiameterM * poolDiameterM;
    return burnRateKgPerM2PerS * area;
  }

  /**
   * Flame height (Thomas correlation, no wind): H/D = 42 · [m" / (ρa · √(g·D))]^0.61.
   *
   * @return flame height in m
   */
  public double flameHeightM() {
    double g = 9.81;
    double term = burnRateKgPerM2PerS / (airDensityKgPerM3 * Math.sqrt(g * poolDiameterM));
    return poolDiameterM * 42.0 * Math.pow(term, 0.61);
  }

  /**
   * Surface emissive power (SEP) using Mudan smoke-corrected correlation: SEP = SEP_max · exp(-s·D) + SEP_smoke · (1 −
   * exp(-s·D)).
   *
   * @return SEP in W/m²
   */
  public double surfaceEmissivePowerWPerM2() {
    double sepMax = 140000.0; // 140 kW/m² for clean flames
    double sepSmoke = 20000.0; // 20 kW/m² smoke-blocked
    double s = 0.12;
    double f = Math.exp(-s * poolDiameterM);
    return sepMax * f + sepSmoke * (1.0 - f);
  }

  /**
   * Approximate cylindrical-flame view factor at horizontal distance x from flame axis, receiver at ground level,
   * normal-to-flame.
   *
   * @param distanceM horizontal distance from pool centre in m
   * @return view factor (dimensionless, 0..1)
   */
  public double viewFactorVertical(double distanceM) {
    double H = flameHeightM();
    double R = poolDiameterM / 2.0;
    double h = H / R;
    double s = Math.max(distanceM, R + 0.001) / R;
    // Mudan vertical-cylinder closed-form approximation
    double A = (h * h + s * s + 1.0) / (2.0 * s);
    double F = (1.0 / Math.PI)
        * (Math.atan(h / Math.sqrt(s * s - 1.0)) - (A - (s * s + 1.0) / (2.0 * s)) / Math.sqrt(A * A - 1.0));
    if (F < 0.0) {
      F = 0.0;
    }
    if (F > 1.0) {
      F = 1.0;
    }
    return F;
  }

  /**
   * Incident heat flux at horizontal distance from pool, ground-level receiver.
   *
   * @param distanceM distance from pool centre in m
   * @return heat flux in W/m²
   */
  public double incidentHeatFlux(double distanceM) {
    return surfaceEmissivePowerWPerM2() * viewFactorVertical(distanceM);
  }

  /**
   * Find the outer distance of a target heat-flux contour by bisection. Searches from the pool radius plus 0.1 m to
   * 1000 m.
   *
   * @param targetFluxWperM2 target heat flux in W/m²
   * @return distance in m, or {@code Double.NaN} if the target is not bracketed in the search range
   * @throws IllegalArgumentException if the target is non-positive or non-finite
   */
  public double distanceToFlux(double targetFluxWperM2) {
    return ConsequenceContour.findDistance(this::incidentHeatFlux, targetFluxWperM2, poolDiameterM / 2.0 + 0.1, 1000.0);
  }
}
