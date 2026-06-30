package neqsim.process.util.fire;

/**
 * Screening-level fire exposure presets for blowdown, depressurization and fire-rupture studies.
 *
 * <p>
 * Each preset bundles the three boundary-condition parameters needed by the generalized Stefan-Boltzmann fire heat-load
 * model (see {@link FireHeatLoadCalculator}):
 * <ul>
 * <li>an effective radiating flame temperature {@code T_rf} [K],</li>
 * <li>an effective flame emissivity {@code epsilon_f} [-], and</li>
 * <li>a convective film coefficient {@code h_f} [W/(m^2.K)] between the flame and the exposed surface.</li>
 * </ul>
 *
 * <p>
 * The absorbed heat flux toward an exposed surface at temperature {@code T_s} is
 *
 * <p>
 * {@code q = epsilon_f * sigma * (T_rf^4 - T_s^4) + h_f * (T_rf - T_s)} [W/m^2]
 *
 * <p>
 * The radiative term already subtracts the surface re-radiation {@code sigma * T_s^4}, so the absorbed flux decreases
 * as the exposed wall heats up. This is the physically correct behaviour for a fire boundary condition and avoids the
 * over-prediction of a fixed constant flux.
 *
 * <p>
 * The parameter values are screening-level defaults aligned with the Scandpower / NORSOK style fire loads (Hekkelstrand
 * and Skulstad, "Guidelines for the Protection of Pressurised Systems Exposed to Fire", 2004) and API STD 521. The
 * {@code PEAK} presets represent localized flame impingement (used for the most exposed element), while the
 * {@code BACKGROUND} presets represent the global average load on the vessel. Project-specific fire loads should
 * replace these defaults when available.
 *
 * @author ESOL
 * @version 1.0
 */
public enum FirePreset {
  /**
   * Hydrocarbon pool fire, localized peak load. Nominal absorbed flux of the order of 100 kW/m^2 onto a cold surface.
   */
  POOL_FIRE_PEAK("Pool fire (peak)", FireKind.POOL, 1100.0, 0.9, 30.0),

  /**
   * Hydrocarbon pool fire, global background load. Nominal absorbed flux of the order of 60 kW/m^2 onto a cold surface.
   */
  POOL_FIRE_BACKGROUND("Pool fire (background)", FireKind.POOL, 950.0, 0.9, 30.0),

  /**
   * Hydrocarbon jet fire, localized peak (impingement) load. Nominal absorbed flux of the order of 250 kW/m^2 onto a
   * cold surface, reflecting the high convective contribution of an impinging jet.
   */
  JET_FIRE_PEAK("Jet fire (peak)", FireKind.JET, 1300.0, 1.0, 100.0),

  /**
   * Hydrocarbon jet fire, global background load. Nominal absorbed flux of the order of 100 kW/m^2 onto a cold surface.
   */
  JET_FIRE_BACKGROUND("Jet fire (background)", FireKind.JET, 1100.0, 0.9, 50.0);

  /** Reference cold surface temperature used to report the nominal absorbed flux [K]. */
  private static final double REFERENCE_SURFACE_TEMPERATURE_K = 288.15;

  /** Classification of the fire type. */
  public enum FireKind {
    /** Confined or unconfined hydrocarbon pool fire. */
    POOL,
    /** High-momentum hydrocarbon jet fire. */
    JET
  }

  private final String displayName;
  private final FireKind kind;
  private final double flameTemperatureK;
  private final double flameEmissivity;
  private final double convectiveCoefficient;

  /**
   * Constructs a fire preset.
   *
   * @param displayName human-readable preset name
   * @param kind fire classification
   * @param flameTemperatureK effective radiating flame temperature in K
   * @param flameEmissivity effective flame emissivity from 0 to 1
   * @param convectiveCoefficient convective film coefficient in W/(m^2.K)
   */
  FirePreset(String displayName, FireKind kind, double flameTemperatureK, double flameEmissivity,
      double convectiveCoefficient) {
    this.displayName = displayName;
    this.kind = kind;
    this.flameTemperatureK = flameTemperatureK;
    this.flameEmissivity = flameEmissivity;
    this.convectiveCoefficient = convectiveCoefficient;
  }

  /**
   * Human-readable preset name.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Fire classification.
   *
   * @return the fire kind
   */
  public FireKind getKind() {
    return kind;
  }

  /**
   * Effective radiating flame temperature.
   *
   * @return flame temperature in K
   */
  public double getFlameTemperatureK() {
    return flameTemperatureK;
  }

  /**
   * Effective flame emissivity.
   *
   * @return emissivity from 0 to 1
   */
  public double getFlameEmissivity() {
    return flameEmissivity;
  }

  /**
   * Convective film coefficient between flame and exposed surface.
   *
   * @return convective coefficient in W/(m^2.K)
   */
  public double getConvectiveCoefficient() {
    return convectiveCoefficient;
  }

  /**
   * Absorbed heat flux toward an exposed surface, including flame radiation, convection and surface re-radiation.
   *
   * @param surfaceTemperatureK current exposed surface temperature in K; must be positive
   * @return absorbed heat flux in W/m^2 (clamped to be non-negative)
   * @throws IllegalArgumentException if {@code surfaceTemperatureK} is not positive
   */
  public double incidentHeatFlux(double surfaceTemperatureK) {
    if (surfaceTemperatureK <= 0.0) {
      throw new IllegalArgumentException("surfaceTemperatureK must be positive");
    }
    double radiative = FireHeatLoadCalculator.generalizedStefanBoltzmannHeatFlux(flameEmissivity, 1.0,
        flameTemperatureK, surfaceTemperatureK);
    double convective = convectiveCoefficient * (flameTemperatureK - surfaceTemperatureK);
    double total = radiative + convective;
    return total > 0.0 ? total : 0.0;
  }

  /**
   * Nominal absorbed heat flux onto a cold reference surface (288.15 K). Useful as a single screening number for
   * comparison against published fire-load tables.
   *
   * @return nominal absorbed heat flux in W/m^2
   */
  public double nominalAbsorbedFluxWPerM2() {
    return incidentHeatFlux(REFERENCE_SURFACE_TEMPERATURE_K);
  }
}
