package neqsim.process.safety.fire;

import java.io.Serializable;

/**
 * BLEVE (Boiling Liquid Expanding Vapor Explosion) consequence calculator.
 *
 * <p>
 * Computes fireball diameter, duration and surface emissive power for a sudden release of pressure- liquefied flammable
 * gas (LPG, butane, propane, etc.) per CCPS / TNO correlations:
 * <ul>
 * <li>D_fb = 5.8 · M^(1/3) (m, M in kg)</li>
 * <li>t_fb = 0.45 · M^(1/3) (s, M in kg)</li>
 * <li>SEP = (η · M · ΔHc) / (π · D² · t) (W/m²)</li>
 * <li>q(r) = SEP · F · τ (with point-source view factor F = D²/(4·r²) for r ≫ D)</li>
 * </ul>
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>CCPS — Guidelines for Vapor Cloud Explosion, Pressure Vessel Burst, BLEVE</li>
 * <li>TNO Green Book — CPR 14E §6 BLEVE / fireball</li>
 * <li>API 521 §4.6.4 — Fireballs / BLEVEs</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class BLEVECalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double fuelMassKg;
  private final double heatOfCombustionJPerKg;
  private final double radiativeFraction;
  private double humidityRH = 0.5;

  /**
   * Construct a BLEVE calculator.
   *
   * @param fuelMassKg total mass of vaporizing fuel in kg
   * @param heatOfCombustionJPerKg lower heating value, J/kg
   * @param radiativeFraction fraction of combustion energy radiated in [0,1] (typically 0.25–0.40)
   * @throws IllegalArgumentException if fuel mass or heat of combustion is non-positive, or the radiative fraction is
   * outside [0,1] or NaN
   */
  public BLEVECalculator(double fuelMassKg, double heatOfCombustionJPerKg, double radiativeFraction) {
    if (fuelMassKg <= 0.0 || heatOfCombustionJPerKg <= 0.0) {
      throw new IllegalArgumentException("fuelMass > 0 and heatOfCombustion > 0 required");
    }
    if (!(radiativeFraction >= 0.0 && radiativeFraction <= 1.0)) {
      throw new IllegalArgumentException("radiativeFraction must be in [0,1]");
    }
    this.fuelMassKg = fuelMassKg;
    this.heatOfCombustionJPerKg = heatOfCombustionJPerKg;
    this.radiativeFraction = radiativeFraction;
  }

  /**
   * Set ambient relative humidity (default 0.5).
   *
   * @param rh relative humidity 0..1
   * @return this calculator for chaining
   */
  public BLEVECalculator setHumidity(double rh) {
    this.humidityRH = Math.max(0.0, Math.min(1.0, rh));
    return this;
  }

  /**
   * Fireball diameter D = 5.8·M^(1/3) (m).
   *
   * @return diameter in m
   */
  public double fireballDiameterM() {
    return 5.8 * Math.cbrt(fuelMassKg);
  }

  /**
   * Fireball duration t = 0.45·M^(1/3) (s).
   *
   * @return duration in s
   */
  public double fireballDurationS() {
    return 0.45 * Math.cbrt(fuelMassKg);
  }

  /**
   * Fireball surface emissive power.
   *
   * @return SEP in W/m²
   */
  public double surfaceEmissivePowerWPerM2() {
    double D = fireballDiameterM();
    double t = fireballDurationS();
    double Q = radiativeFraction * fuelMassKg * heatOfCombustionJPerKg;
    return Q / (Math.PI * D * D * t);
  }

  /**
   * Atmospheric transmissivity (Wayne, simplified). Dry air has unit transmissivity in this approximation.
   *
   * @param distanceM distance to receiver in m
   * @return transmissivity τ ∈ [0,1]
   */
  public double transmissivity(double distanceM) {
    return JetFireModel.atmosphericTransmissivity(humidityRH, distanceM);
  }

  /**
   * Incident heat flux at receiver distance (point-source approximation).
   *
   * @param distanceM distance from fireball centre, m
   * @return incident heat flux in W/m²
   */
  public double incidentHeatFlux(double distanceM) {
    if (distanceM <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    double D = fireballDiameterM();
    double SEP = surfaceEmissivePowerWPerM2();
    double F = (D * D) / (4.0 * distanceM * distanceM);
    if (F > 1.0) {
      F = 1.0;
    }
    return SEP * F * transmissivity(distanceM);
  }

  /**
   * Find the outer distance of a target heat-flux contour by bisection. Searches from the fireball radius plus 0.1 m to
   * 5000 m.
   *
   * @param targetFluxWperM2 target heat flux in W/m²
   * @return distance in m, or {@code Double.NaN} if the target is not bracketed in the search range
   * @throws IllegalArgumentException if the target is non-positive or non-finite
   */
  public double distanceToFlux(double targetFluxWperM2) {
    return ConsequenceContour.findDistance(this::incidentHeatFlux, targetFluxWperM2, fireballDiameterM() / 2.0 + 0.1,
        5000.0);
  }
}
