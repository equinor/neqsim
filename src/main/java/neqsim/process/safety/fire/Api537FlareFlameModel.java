package neqsim.process.safety.fire;

import java.io.Serializable;

/**
 * API 537 / API 521 elevated-flare flame and radiation model with wind tilt and noise.
 *
 * <p>
 * Computes, for an elevated (stack) flare:
 * <ul>
 * <li>Flame length from the total heat release (Kent 1968 / API 521 §6 correlation, {@code L = 0.00326 · Q^0.478}, Q in
 * W, L in m)</li>
 * <li>Wind-tilt trajectory of the flame (Brzustowski &amp; Sommer): tilt from the resultant of the jet exit momentum
 * and the cross-wind, giving the flame-centre and flame-tip coordinates</li>
 * <li>Sterile-zone iso-flux radii for the API 521 §6 design levels (1.58 / 4.73 / 6.3 / 9.46 kW/m²) using a point
 * source located at the flame centre</li>
 * <li>Flare noise: sound power level (PWL) from acoustic efficiency and far-field sound pressure level (SPL) with
 * spherical spreading per API 537</li>
 * </ul>
 *
 * <p>
 * <b>References:</b> API STD 521 §6 (Flares — sizing, radiation and sterile area), API STD 537 (Flare Details for
 * General Refinery and Petrochemical Service — flame length, radiation, noise), Brzustowski T.A. &amp; Sommer E.C.
 * (1973), Kent G.R. (1968) Hydrocarbon Processing.
 *
 * @author ESOL
 * @version 1.0
 */
public class Api537FlareFlameModel implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Stefan-Boltzmann constant [W/(m²·K⁴)]. */
  private static final double SIGMA = 5.670374419e-8;

  /** API 521 §6 design radiation levels in W/m². */
  public static final double FLUX_1_58_KW = 1580.0;
  /** API 521 §6 4.73 kW/m² (permissible for emergency actions, ~3 min exposure). */
  public static final double FLUX_4_73_KW = 4730.0;
  /** API 521 §6 6.3 kW/m² (permissible for short exposure, escape). */
  public static final double FLUX_6_3_KW = 6300.0;
  /**
   * API 521 §6 9.46 kW/m² (permissible at equipment where emergency action lasting up to 30 s may be required).
   */
  public static final double FLUX_9_46_KW = 9460.0;

  private final double massFlowKgPerS;
  private final double heatOfCombustionJPerKg;
  private final double radiativeFraction;
  private final double exitVelocityMPerS;

  private double stackHeightM = 30.0;
  private double windSpeedMPerS = 0.0;
  private double transmissivity = 1.0;
  private double acousticEfficiency = 3.0e-6;

  /**
   * Construct an elevated-flare flame model.
   *
   * @param massFlowKgPerS flared mass flow in kg/s
   * @param heatOfCombustionJPerKg fuel lower heating value in J/kg
   * @param radiativeFraction fraction of combustion heat radiated (natural gas ≈ 0.20)
   * @param exitVelocityMPerS flare-tip gas exit velocity in m/s
   */
  public Api537FlareFlameModel(double massFlowKgPerS, double heatOfCombustionJPerKg, double radiativeFraction,
      double exitVelocityMPerS) {
    if (massFlowKgPerS < 0.0 || heatOfCombustionJPerKg <= 0.0) {
      throw new IllegalArgumentException("massFlow >= 0 and heatOfCombustion > 0 required");
    }
    if (radiativeFraction < 0.0 || radiativeFraction > 1.0) {
      throw new IllegalArgumentException("radiativeFraction must be in [0,1]");
    }
    if (exitVelocityMPerS <= 0.0) {
      throw new IllegalArgumentException("exitVelocity must be positive");
    }
    this.massFlowKgPerS = massFlowKgPerS;
    this.heatOfCombustionJPerKg = heatOfCombustionJPerKg;
    this.radiativeFraction = radiativeFraction;
    this.exitVelocityMPerS = exitVelocityMPerS;
  }

  /**
   * Set the flare-tip elevation above grade (default 30 m).
   *
   * @param stackHeightM stack height in m
   * @return this model for chaining
   */
  public Api537FlareFlameModel setStackHeightM(double stackHeightM) {
    this.stackHeightM = stackHeightM;
    return this;
  }

  /**
   * Set the cross-wind speed at the flare tip (default 0 m/s, i.e. vertical flame).
   *
   * @param windSpeedMPerS wind speed in m/s
   * @return this model for chaining
   */
  public Api537FlareFlameModel setWindSpeedMPerS(double windSpeedMPerS) {
    this.windSpeedMPerS = Math.max(0.0, windSpeedMPerS);
    return this;
  }

  /**
   * Set atmospheric transmissivity used in the radiation calculation (default 1.0).
   *
   * @param tau transmissivity in [0,1]
   * @return this model for chaining
   */
  public Api537FlareFlameModel setTransmissivity(double tau) {
    this.transmissivity = Math.max(0.0, Math.min(1.0, tau));
    return this;
  }

  /**
   * Set the acoustic efficiency (fraction of heat release converted to sound power, default 3e-6 per API 537).
   *
   * @param eta acoustic efficiency
   * @return this model for chaining
   */
  public Api537FlareFlameModel setAcousticEfficiency(double eta) {
    this.acousticEfficiency = Math.max(0.0, eta);
    return this;
  }

  /**
   * Total heat release Q = m_dot · ΔHc.
   *
   * @return total heat release in W
   */
  public double totalHeatReleaseW() {
    return massFlowKgPerS * heatOfCombustionJPerKg;
  }

  /**
   * Total radiative power Q_rad = η · Q.
   *
   * @return radiative power in W
   */
  public double totalRadiativePowerW() {
    return radiativeFraction * totalHeatReleaseW();
  }

  /**
   * Flame length from total heat release (Kent 1968 / API 521 §6): {@code L = 0.00326 · Q^0.478}.
   *
   * @return flame length in m
   */
  public double flameLengthM() {
    double q = totalHeatReleaseW();
    if (q <= 0.0) {
      return 0.0;
    }
    return 0.00326 * Math.pow(q, 0.478);
  }

  /**
   * Flame tilt angle from the vertical due to cross-wind (Brzustowski &amp; Sommer), from the resultant of the jet exit
   * velocity and the wind velocity.
   *
   * @return tilt angle from vertical in radians
   */
  public double flameTiltRad() {
    return Math.atan2(windSpeedMPerS, exitVelocityMPerS);
  }

  /**
   * Horizontal coordinate of the flame centre relative to the stack base (downwind positive).
   *
   * @return flame-centre horizontal offset in m
   */
  public double flameCenterHorizontalM() {
    return 0.5 * flameLengthM() * Math.sin(flameTiltRad());
  }

  /**
   * Vertical coordinate of the flame centre above grade.
   *
   * @return flame-centre height in m
   */
  public double flameCenterHeightM() {
    return stackHeightM + 0.5 * flameLengthM() * Math.cos(flameTiltRad());
  }

  /**
   * Horizontal coordinate of the flame tip relative to the stack base (downwind positive).
   *
   * @return flame-tip horizontal offset in m
   */
  public double flameTipHorizontalM() {
    return flameLengthM() * Math.sin(flameTiltRad());
  }

  /**
   * Vertical coordinate of the flame tip above grade.
   *
   * @return flame-tip height in m
   */
  public double flameTipHeightM() {
    return stackHeightM + flameLengthM() * Math.cos(flameTiltRad());
  }

  /**
   * Incident radiation heat flux at a grade-level receiver located a horizontal distance from the stack base, using a
   * point source at the flame centre.
   *
   * @param groundDistanceM horizontal distance from the stack base in m
   * @return incident heat flux in W/m²
   */
  public double heatFluxAtGroundDistance(double groundDistanceM) {
    double dx = groundDistanceM - flameCenterHorizontalM();
    double dz = flameCenterHeightM();
    double r2 = dx * dx + dz * dz;
    if (r2 < 1.0e-6) {
      r2 = 1.0e-6;
    }
    return transmissivity * totalRadiativePowerW() / (4.0 * Math.PI * r2);
  }

  /**
   * Sterile-zone radius (horizontal distance from the stack base, at grade) for a target incident heat flux. Returns 0
   * if the target flux is never reached even directly under the flame.
   *
   * @param targetFluxWPerM2 target incident heat flux in W/m²
   * @return horizontal sterile-zone radius in m
   */
  public double sterileZoneRadiusM(double targetFluxWPerM2) {
    if (targetFluxWPerM2 <= 0.0) {
      throw new IllegalArgumentException("target flux must be positive");
    }
    double dz = flameCenterHeightM();
    double rSphere = Math.sqrt(transmissivity * totalRadiativePowerW() / (4.0 * Math.PI * targetFluxWPerM2));
    double xc = flameCenterHorizontalM();
    // Convert slant radius to a grade-level horizontal distance from the stack base.
    if (rSphere <= dz) {
      return 0.0;
    }
    double dx = Math.sqrt(rSphere * rSphere - dz * dz);
    return Math.max(0.0, xc + dx);
  }

  /**
   * Acoustic sound power level (PWL) of the flare per API 537.
   *
   * @return sound power level in dB (re 1e-12 W)
   */
  public double soundPowerLevelDb() {
    double acousticPowerW = acousticEfficiency * totalHeatReleaseW();
    if (acousticPowerW <= 0.0) {
      return 0.0;
    }
    return 10.0 * Math.log10(acousticPowerW / 1.0e-12);
  }

  /**
   * Far-field sound pressure level (SPL) at a distance from the flame, assuming spherical spreading per API 537:
   * {@code SPL = PWL − 20·log10(d) − 11}.
   *
   * @param distanceM distance from the flame in m
   * @return sound pressure level in dB (re 20 µPa)
   */
  public double soundPressureLevelDb(double distanceM) {
    double d = Math.max(distanceM, 1.0);
    return soundPowerLevelDb() - 20.0 * Math.log10(d) - 11.0;
  }

  /**
   * Equivalent black-body flame surface temperature for reference (radiative power over flame surface).
   *
   * @return flame surface temperature in K
   */
  public double equivalentFlameTemperatureK() {
    double l = flameLengthM();
    double surface = Math.PI * Math.max(l * 0.12, 0.1) * l;
    if (surface <= 0.0) {
      return 0.0;
    }
    double emissivePower = totalRadiativePowerW() / surface;
    return Math.pow(emissivePower / SIGMA, 0.25);
  }

  /**
   * Build a brief human-readable summary.
   *
   * @return summary string
   */
  public String summary() {
    StringBuilder sb = new StringBuilder();
    sb.append("API 537 flare flame model:\n");
    sb.append(String.format("  Heat release      : %.3e W%n", totalHeatReleaseW()));
    sb.append(String.format("  Flame length      : %.1f m%n", flameLengthM()));
    sb.append(String.format("  Flame tilt        : %.1f deg from vertical%n", Math.toDegrees(flameTiltRad())));
    sb.append(String.format("  Flame tip         : (%.1f, %.1f) m%n", flameTipHorizontalM(), flameTipHeightM()));
    sb.append(String.format("  Sterile 4.73 kW/m²: %.1f m%n", sterileZoneRadiusM(FLUX_4_73_KW)));
    sb.append(String.format("  Sterile 1.58 kW/m²: %.1f m%n", sterileZoneRadiusM(FLUX_1_58_KW)));
    sb.append(String.format("  PWL               : %.1f dB%n", soundPowerLevelDb()));
    return sb.toString();
  }
}
