package neqsim.process.safety.fire;

import java.io.Serializable;

/**
 * Passive fire protection (PFP) demand calculator per API 521 / NORSOK S-001.
 *
 * <p>
 * Screens whether an exposed steel wall reaches its critical (allowable) temperature before the required survival time
 * under fire exposure, and if not, estimates the conduction-limited PFP (e.g. intumescent epoxy) thickness required to
 * meet the survival time.
 *
 * <p>
 * Method (lumped steel wall):
 * <ul>
 * <li>Bare-steel time to critical temperature: {@code t_bare = m″·Cp·ΔT / q_abs}, with steel mass per area
 * {@code m″ = ρ_steel·thickness} and absorbed flux {@code q_abs = α·q_inc}</li>
 * <li>If {@code t_bare ≥ t_req}, no PFP is required</li>
 * <li>Otherwise, the allowable steady heat input to still reach ΔT only at {@code t_req} is
 * {@code q_allow = m″·Cp·ΔT / t_req}, and the conduction-limited PFP thickness is
 * {@code L = k_pfp·(T_fire − T_steel,avg) / q_allow}</li>
 * </ul>
 * The required rating is mapped from the fire type and survival time (jet → "J", pool/hydrocarbon → "H"; duration 60 or
 * 120 min → H60/H120/J60).
 *
 * <p>
 * <b>References:</b> API STD 521 §4.5 (fire heat input) and §5.20 (depressuring), NORSOK S-001 (Technical safety), ISO
 * 22899-1 (jet-fire resistance test), UL 1709 / H-class hydrocarbon fire curve.
 *
 * @author ESOL
 * @version 1.0
 */
public class PfpDemandCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Steel density [kg/m³]. */
  private static final double STEEL_DENSITY = 7850.0;
  /** Steel specific heat [J/(kg·K)] (representative mean to ~500 °C). */
  private static final double STEEL_CP = 490.0;

  /** Fire type for rating selection. */
  public enum FireType {
    /** Pool / open hydrocarbon fire (H-class, UL 1709 curve). */
    POOL,
    /** Jet fire (J-class, ISO 22899). */
    JET
  }

  /** PFP rating classification. */
  public enum PfpRating {
    /** No passive fire protection required. */
    NONE,
    /** Hydrocarbon (pool) fire, 60 min endurance. */
    H60,
    /** Hydrocarbon (pool) fire, 120 min endurance. */
    H120,
    /** Jet fire, 60 min endurance. */
    J60,
    /** Jet fire, 120 min endurance. */
    J120
  }

  private final double incidentHeatFluxWPerM2;
  private final double steelThicknessM;
  private double absorptivity = 0.9;
  private double initialTemperatureK = 288.15;
  private double criticalTemperatureK = 673.15;
  private double fireTemperatureK = 1173.15;
  private double pfpConductivityWPerMK = 0.2;
  private FireType fireType = FireType.POOL;

  /**
   * Construct a PFP demand calculator.
   *
   * @param incidentHeatFluxWPerM2 incident fire heat flux on the wall in W/m² (API 521: ~100 kW/m² pool, up to ~350
   * kW/m² jet)
   * @param steelThicknessM exposed steel wall thickness in m
   */
  public PfpDemandCalculator(double incidentHeatFluxWPerM2, double steelThicknessM) {
    if (incidentHeatFluxWPerM2 <= 0.0 || steelThicknessM <= 0.0) {
      throw new IllegalArgumentException("incident heat flux and steel thickness must be positive");
    }
    this.incidentHeatFluxWPerM2 = incidentHeatFluxWPerM2;
    this.steelThicknessM = steelThicknessM;
  }

  /**
   * Set the steel surface absorptivity (default 0.9).
   *
   * @param alpha absorptivity in (0,1]
   * @return this calculator for chaining
   */
  public PfpDemandCalculator setAbsorptivity(double alpha) {
    if (alpha <= 0.0 || alpha > 1.0) {
      throw new IllegalArgumentException("absorptivity must be in (0,1]");
    }
    this.absorptivity = alpha;
    return this;
  }

  /**
   * Set the initial wall temperature (default 288.15 K).
   *
   * @param tK initial temperature in K
   * @return this calculator for chaining
   */
  public PfpDemandCalculator setInitialTemperatureK(double tK) {
    this.initialTemperatureK = tK;
    return this;
  }

  /**
   * Set the critical (allowable) steel temperature (default 673.15 K ≈ 400 °C, common UTS-degradation limit).
   *
   * @param tK critical temperature in K
   * @return this calculator for chaining
   */
  public PfpDemandCalculator setCriticalTemperatureK(double tK) {
    this.criticalTemperatureK = tK;
    return this;
  }

  /**
   * Set the effective fire (flame) temperature used for the PFP conduction calc (default 1173.15 K ≈ 900 °C).
   *
   * @param tK fire temperature in K
   * @return this calculator for chaining
   */
  public PfpDemandCalculator setFireTemperatureK(double tK) {
    this.fireTemperatureK = tK;
    return this;
  }

  /**
   * Set the PFP material thermal conductivity (default 0.2 W/(m·K), representative intumescent epoxy).
   *
   * @param kWPerMK conductivity in W/(m·K)
   * @return this calculator for chaining
   */
  public PfpDemandCalculator setPfpConductivityWPerMK(double kWPerMK) {
    if (kWPerMK <= 0.0) {
      throw new IllegalArgumentException("PFP conductivity must be positive");
    }
    this.pfpConductivityWPerMK = kWPerMK;
    return this;
  }

  /**
   * Set the fire type (default POOL).
   *
   * @param fireType the fire type
   * @return this calculator for chaining
   */
  public PfpDemandCalculator setFireType(FireType fireType) {
    if (fireType == null) {
      throw new IllegalArgumentException("fireType must not be null");
    }
    this.fireType = fireType;
    return this;
  }

  /**
   * Steel mass per unit exposed area.
   *
   * @return steel mass per area in kg/m²
   */
  public double steelMassPerAreaKgPerM2() {
    return STEEL_DENSITY * steelThicknessM;
  }

  /**
   * Bare-steel time to reach the critical temperature under the incident fire flux.
   *
   * @return time to critical temperature in s
   */
  public double bareSteelTimeToCriticalS() {
    double deltaT = criticalTemperatureK - initialTemperatureK;
    double qAbs = absorptivity * incidentHeatFluxWPerM2;
    return steelMassPerAreaKgPerM2() * STEEL_CP * deltaT / qAbs;
  }

  /**
   * Evaluate the PFP demand for a required survival (fire endurance) time.
   *
   * @param requiredSurvivalTimeS the required survival time in s (e.g. 3600 for 60 min)
   * @return the PFP demand result
   */
  public PfpDemandResult evaluate(double requiredSurvivalTimeS) {
    if (requiredSurvivalTimeS <= 0.0) {
      throw new IllegalArgumentException("required survival time must be positive");
    }
    double tBare = bareSteelTimeToCriticalS();
    boolean pfpRequired = tBare < requiredSurvivalTimeS;

    double requiredThicknessM = 0.0;
    if (pfpRequired) {
      double deltaT = criticalTemperatureK - initialTemperatureK;
      double qAllow = steelMassPerAreaKgPerM2() * STEEL_CP * deltaT / requiredSurvivalTimeS;
      double steelAvg = 0.5 * (initialTemperatureK + criticalTemperatureK);
      double drivingTemp = Math.max(1.0, fireTemperatureK - steelAvg);
      requiredThicknessM = pfpConductivityWPerMK * drivingTemp / qAllow;
    }

    PfpRating rating = selectRating(pfpRequired, requiredSurvivalTimeS);

    return new PfpDemandResult(tBare, requiredSurvivalTimeS, pfpRequired, requiredThicknessM, rating);
  }

  /**
   * Select the PFP rating from the fire type and required survival time.
   *
   * @param pfpRequired whether PFP is required
   * @param requiredSurvivalTimeS required survival time in s
   * @return the PFP rating
   */
  private PfpRating selectRating(boolean pfpRequired, double requiredSurvivalTimeS) {
    if (!pfpRequired) {
      return PfpRating.NONE;
    }
    boolean longDuration = requiredSurvivalTimeS > 3600.0 + 1.0e-6;
    if (fireType == FireType.JET) {
      return longDuration ? PfpRating.J120 : PfpRating.J60;
    }
    return longDuration ? PfpRating.H120 : PfpRating.H60;
  }

  /**
   * Immutable result of a PFP demand evaluation.
   */
  public static class PfpDemandResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double bareSteelTimeToCriticalS;
    private final double requiredSurvivalTimeS;
    private final boolean pfpRequired;
    private final double requiredPfpThicknessM;
    private final PfpRating rating;

    PfpDemandResult(double bareSteelTimeToCriticalS, double requiredSurvivalTimeS, boolean pfpRequired,
        double requiredPfpThicknessM, PfpRating rating) {
      this.bareSteelTimeToCriticalS = bareSteelTimeToCriticalS;
      this.requiredSurvivalTimeS = requiredSurvivalTimeS;
      this.pfpRequired = pfpRequired;
      this.requiredPfpThicknessM = requiredPfpThicknessM;
      this.rating = rating;
    }

    /**
     * Bare-steel time to critical temperature in s.
     *
     * @return bare-steel time in s
     */
    public double getBareSteelTimeToCriticalS() {
      return bareSteelTimeToCriticalS;
    }

    /**
     * Required survival time in s.
     *
     * @return required survival time
     */
    public double getRequiredSurvivalTimeS() {
      return requiredSurvivalTimeS;
    }

    /**
     * Whether passive fire protection is required.
     *
     * @return true if PFP is required
     */
    public boolean isPfpRequired() {
      return pfpRequired;
    }

    /**
     * Required PFP thickness in m (0 if none required).
     *
     * @return required PFP thickness in m
     */
    public double getRequiredPfpThicknessM() {
      return requiredPfpThicknessM;
    }

    /**
     * Required PFP thickness in mm (0 if none required).
     *
     * @return required PFP thickness in mm
     */
    public double getRequiredPfpThicknessMm() {
      return requiredPfpThicknessM * 1000.0;
    }

    /**
     * The selected PFP rating.
     *
     * @return PFP rating
     */
    public PfpRating getRating() {
      return rating;
    }

    /**
     * Build a brief human-readable summary.
     *
     * @return summary string
     */
    public String summary() {
      StringBuilder sb = new StringBuilder();
      sb.append("PFP demand (API 521 / NORSOK S-001):\n");
      sb.append(String.format("  Bare-steel time to crit. : %.0f s (%.1f min)%n", bareSteelTimeToCriticalS,
          bareSteelTimeToCriticalS / 60.0));
      sb.append(String.format("  Required survival time    : %.0f s (%.1f min)%n", requiredSurvivalTimeS,
          requiredSurvivalTimeS / 60.0));
      sb.append("  PFP required             : ").append(pfpRequired).append('\n');
      if (pfpRequired) {
        sb.append(String.format("  Required PFP thickness    : %.1f mm%n", getRequiredPfpThicknessMm()));
      }
      sb.append("  Rating                   : ").append(rating).append('\n');
      return sb.toString();
    }
  }
}
