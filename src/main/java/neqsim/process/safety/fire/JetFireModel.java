package neqsim.process.safety.fire;

import java.io.Serializable;

/**
 * Point-source thermal radiation model for jet fires from horizontal/vertical gas releases.
 *
 * <p>
 * Implements the API 521 §4.6 / Mudan &amp; Croce point-source method:
 * <ul>
 * <li>Total radiative heat = η · m_dot · ΔHc, where η is the radiative fraction</li>
 * <li>Incident heat flux at distance r = (η · m_dot · ΔHc · τ) / (4 π r²)</li>
 * <li>τ is atmospheric transmissivity (Wayne 1991 simplified)</li>
 * </ul>
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>API STD 521, §4.6 Flares — Radiation</li>
 * <li>Chamberlain D. (1987) — Developments in design methods for predicting thermal radiation
 * from flares. Chem. Eng. Res. Des. 65, 299–309</li>
 * <li>CCPS Yellow Book — Thermal radiation models</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class JetFireModel implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double massFlowKgPerS;
  private final double heatOfCombustionJPerKg;
  private final double radiativeFraction;
  private double humidityRH = 0.5; // 0..1

  /**
   * Construct a jet fire radiation model.
   *
   * @param massFlowKgPerS release rate burning at the jet, kg/s
   * @param heatOfCombustionJPerKg lower heating value of fuel, J/kg
   * @param radiativeFraction fraction of combustion heat radiated (0.10 – 0.40 typical;
   *        natural gas ≈ 0.20)
   */
  public JetFireModel(double massFlowKgPerS, double heatOfCombustionJPerKg,
      double radiativeFraction) {
    if (massFlowKgPerS < 0.0 || heatOfCombustionJPerKg <= 0.0) {
      throw new IllegalArgumentException("massFlow >= 0 and heatOfCombustion > 0 required");
    }
    if (radiativeFraction < 0.0 || radiativeFraction > 1.0) {
      throw new IllegalArgumentException("radiativeFraction must be in [0,1]");
    }
    this.massFlowKgPerS = massFlowKgPerS;
    this.heatOfCombustionJPerKg = heatOfCombustionJPerKg;
    this.radiativeFraction = radiativeFraction;
  }

  /**
   * Set ambient relative humidity (default 0.5).
   *
   * @param rh relative humidity 0..1
   * @return this model for chaining
   */
  public JetFireModel setHumidity(double rh) {
    this.humidityRH = Math.max(0.0, Math.min(1.0, rh));
    return this;
  }

  /**
   * Total radiative power Q_rad = η · m_dot · ΔHc.
   *
   * @return radiative power in W
   */
  public double totalRadiativePowerW() {
    return radiativeFraction * massFlowKgPerS * heatOfCombustionJPerKg;
  }

  /**
   * Atmospheric transmissivity (Wayne 1991): simplified function of distance and humidity.
   *
   * @param distanceM distance from flame center in m
   * @return transmissivity τ in [0,1]
   */
  public double transmissivity(double distanceM) {
    double pw = humidityRH * 1700.0; // partial pressure water (Pa) at 20°C, sat ≈ 2340
    double xpw = pw * Math.max(distanceM, 1.0);
    double tau = 1.006 - 0.01171 * Math.log10(xpw)
        - 0.02368 * Math.pow(Math.log10(xpw), 2);
    if (tau < 0.0) {
      tau = 0.0;
    }
    if (tau > 1.0) {
      tau = 1.0;
    }
    return tau;
  }

  /**
   * Incident radiant heat flux at receiver distance (point-source).
   *
   * @param distanceM distance from flame to receiver in m
   * @return incident heat flux in W/m²
   */
  public double incidentHeatFlux(double distanceM) {
    if (distanceM <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    double tau = transmissivity(distanceM);
    return totalRadiativePowerW() * tau / (4.0 * Math.PI * distanceM * distanceM);
  }

  /**
   * Distance to a target heat-flux contour (e.g. 4 kW/m² public exposure, 12.5 kW/m² escalation).
   *
   * @param targetFluxWperM2 target heat flux in W/m² (e.g. 4000, 12500, 37500)
   * @return distance in m
   */
  public double distanceToFlux(double targetFluxWperM2) {
    if (targetFluxWperM2 <= 0.0) {
      throw new IllegalArgumentException("targetFlux must be positive");
    }
    // Inverse-square first guess (no transmissivity)
    double q = totalRadiativePowerW();
    double r = Math.sqrt(q / (4.0 * Math.PI * targetFluxWperM2));
    // Iterate a few times to include transmissivity
    for (int i = 0; i < 25; i++) {
      double tau = transmissivity(r);
      double rNew = Math.sqrt(q * tau / (4.0 * Math.PI * targetFluxWperM2));
      if (Math.abs(rNew - r) < 0.01) {
        r = rNew;
        break;
      }
      r = rNew;
    }
    return r;
  }
}
