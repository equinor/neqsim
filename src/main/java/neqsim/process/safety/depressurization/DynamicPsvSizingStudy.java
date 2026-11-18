package neqsim.process.safety.depressurization;

import java.io.Serializable;
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Dynamic pressure-safety-valve (PSV) sizing study for a fire-exposed gas-filled vessel.
 *
 * <p>
 * Steady-state API STD 521 fire sizing assumes the relief device passes the peak fire vapour-generation rate
 * continuously at the accumulated relieving pressure. In reality a spring-loaded PSV on a gas system cycles (pops open
 * at the set pressure, reseats at the blowdown pressure), and the vessel inventory and the fire heat input both change
 * with time. A transient simulation that resolves this PSV cycling typically shows that a smaller orifice keeps the
 * peak accumulated pressure within the allowable limit than the steady-state equation requires, i.e. the steady-state
 * method is conservative. This study quantifies that conservatism as an oversizing ratio.
 * </p>
 *
 * <p>
 * The study couples a lumped real-gas vessel energy and mass balance, a cycling {@link PsvValveModel}, and a constant
 * fire heat input. The dynamically required orifice area is found by bisection as the smallest area that keeps the peak
 * vessel pressure at or below the allowable accumulated pressure. It is then compared against the steady-state API 521
 * area from {@link ReliefValveSizing#calculateRequiredArea}.
 * </p>
 *
 * <p>
 * This is a screening-grade comparison: the cycling dynamics use ideal-gas energy bookkeeping seeded from a single
 * NeqSim flash for molar mass, heat-capacity ratio and compressibility.
 * </p>
 *
 * <p>
 * <b>References:</b> API STD 521 7th ed. §4.4 and §5.15; Speirs and Kirby PSV dynamic-sizing studies.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class DynamicPsvSizingStudy implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double GAS_CONSTANT = 8.314;

  private final SystemInterface fluid;
  private final double vesselVolume;
  private final double fireHeatInputW;
  private final double setPressurePa;
  private final double overpressureFraction;
  private final double backPressurePa;

  private double blowdownFraction = 0.1;
  private double dischargeCoefficient = 0.975;
  private double latentHeat = 350000.0;
  private double timeStep = 0.5;
  private double maxTime = 1200.0;
  private double initialPressureFraction = 0.9;

  /**
   * Constructs a dynamic PSV sizing study.
   *
   * @param fluid gas fluid in the vessel; mixing rule must already be set
   * @param vesselVolume internal vessel volume in m³; must be positive
   * @param fireHeatInputW fire heat input to the fluid in W; must be positive
   * @param setPressurePa PSV set pressure in Pa absolute; must be positive
   * @param overpressureFraction allowable accumulation fraction (0.21 for fire); must be positive
   * @param backPressurePa downstream absolute pressure in Pa; must be positive
   * @throws IllegalArgumentException for a null fluid, nonfinite or nonpositive inputs, or back pressure at or above
   * set pressure
   */
  public DynamicPsvSizingStudy(SystemInterface fluid, double vesselVolume, double fireHeatInputW, double setPressurePa,
      double overpressureFraction, double backPressurePa) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    if (!Double.isFinite(vesselVolume) || !Double.isFinite(fireHeatInputW) || !Double.isFinite(setPressurePa)
        || !Double.isFinite(overpressureFraction) || !Double.isFinite(backPressurePa) || vesselVolume <= 0.0
        || fireHeatInputW <= 0.0 || setPressurePa <= 0.0 || overpressureFraction <= 0.0 || backPressurePa <= 0.0
        || backPressurePa >= setPressurePa) {
      throw new IllegalArgumentException("Expected finite positive inputs and back pressure below set pressure");
    }
    this.fluid = fluid;
    this.vesselVolume = vesselVolume;
    this.fireHeatInputW = fireHeatInputW;
    this.setPressurePa = setPressurePa;
    this.overpressureFraction = overpressureFraction;
    this.backPressurePa = backPressurePa;
  }

  /**
   * Sets the PSV blowdown fraction (reseat margin below the set pressure).
   *
   * @param blowdownFraction blowdown fraction in (0, 1)
   * @return this study for chaining
   * @throws IllegalArgumentException if out of range
   */
  public DynamicPsvSizingStudy setBlowdownFraction(double blowdownFraction) {
    if (!Double.isFinite(blowdownFraction) || blowdownFraction <= 0.0 || blowdownFraction >= 1.0) {
      throw new IllegalArgumentException("blowdownFraction must be between 0 and 1");
    }
    this.blowdownFraction = blowdownFraction;
    return this;
  }

  /**
   * Sets the PSV discharge coefficient.
   *
   * @param dischargeCoefficient discharge coefficient; must be positive
   * @return this study for chaining
   * @throws IllegalArgumentException if not positive
   */
  public DynamicPsvSizingStudy setDischargeCoefficient(double dischargeCoefficient) {
    if (!Double.isFinite(dischargeCoefficient) || dischargeCoefficient <= 0.0) {
      throw new IllegalArgumentException("dischargeCoefficient must be positive");
    }
    this.dischargeCoefficient = dischargeCoefficient;
    return this;
  }

  /**
   * Sets the latent heat of vaporization used for the steady-state fire relieving rate.
   *
   * @param latentHeat latent heat in J/kg; must be positive
   * @return this study for chaining
   * @throws IllegalArgumentException if not positive
   */
  public DynamicPsvSizingStudy setLatentHeat(double latentHeat) {
    if (!Double.isFinite(latentHeat) || latentHeat <= 0.0) {
      throw new IllegalArgumentException("latentHeat must be positive");
    }
    this.latentHeat = latentHeat;
    return this;
  }

  /**
   * Sets the transient integration time step.
   *
   * @param dt time step in s; must be positive
   * @return this study for chaining
   * @throws IllegalArgumentException if not positive
   */
  public DynamicPsvSizingStudy setTimeStep(double dt) {
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("dt must be positive");
    }
    this.timeStep = dt;
    return this;
  }

  /**
   * Sets the maximum simulation time.
   *
   * @param tMax maximum time in s; must be positive
   * @return this study for chaining
   * @throws IllegalArgumentException if not positive
   */
  public DynamicPsvSizingStudy setMaxTime(double tMax) {
    if (!Double.isFinite(tMax) || tMax <= 0.0) {
      throw new IllegalArgumentException("tMax must be positive");
    }
    this.maxTime = tMax;
    return this;
  }

  /**
   * Runs the steady-versus-dynamic sizing comparison.
   *
   * @return a {@link SizingComparison} with the steady-state and dynamic required orifice areas and the oversizing
   * ratio
   */
  public SizingComparison run() {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    double tRelief = fluid.getTemperature();
    double mw = fluid.getMolarMass();
    double cpMol = fluid.getCp("J/molK");
    double cvMol = fluid.getCv("J/molK");
    double gamma = cvMol > 0.0 ? cpMol / cvMol : 1.3;
    if (gamma <= 1.0) {
      gamma = 1.3;
    }
    double rho = fluid.getDensity("kg/m3");
    double z = (rho > 0.0 && mw > 0.0) ? setPressurePa * mw / (rho * GAS_CONSTANT * tRelief) : 1.0;
    if (z <= 0.0 || Double.isNaN(z)) {
      z = 1.0;
    }

    double allowablePressurePa = ReliefValveSizing.relievingPressurePa(setPressurePa, overpressureFraction);

    // --- Steady-state API 521 required area for the peak fire vapour rate ---
    double steadyReliefRate = fireHeatInputW / latentHeat;
    SizingComparison result = new SizingComparison();
    result.allowablePressurePa = allowablePressurePa;
    result.steadyReliefRateKgPerS = steadyReliefRate;
    result.timeStepSeconds = timeStep;
    if (!Double.isFinite(allowablePressurePa) || !Double.isFinite(backPressurePa)
        || backPressurePa >= allowablePressurePa) {
      result.terminationReason = "No positive forward pressure differential at the allowable limit";
      return result;
    }
    ReliefValveSizing.PSVSizingResult steady = ReliefValveSizing.calculateRequiredArea(steadyReliefRate, setPressurePa,
        overpressureFraction, backPressurePa, tRelief, mw, z, gamma, false, false);
    double steadyAreaM2 = steady.getRequiredArea();

    result.steadyRequiredAreaM2 = steadyAreaM2;

    // Locate a passing trajectory before refining. A large trial can empty the inventory
    // in one integration step even when a smaller valve is numerically well resolved.
    double lower = steadyAreaM2 * 1.0e-3;
    double upper = steadyAreaM2 * 1.0e-3;
    double maximumArea = steadyAreaM2 * 10.0;
    if (!Double.isFinite(maximumArea) || upper <= 0.0) {
      result.terminationReason = "Invalid steady area for dynamic search";
      return result;
    }
    Trial incomplete = null;
    Trial trial = simulatePeakPressure(upper, tRelief, mw, gamma, z);
    while (!trial.passes(allowablePressurePa)) {
      if (trial.peakPressurePa > allowablePressurePa) {
        lower = upper;
      } else {
        incomplete = trial;
      }
      if (upper >= maximumArea) {
        if (incomplete != null) {
          result.recordTrial(incomplete);
        } else {
          result.status = SizingStatus.NO_FEASIBLE_AREA_IN_SEARCH;
          result.recordTrial(trial);
        }
        return result;
      }
      upper = Math.min(2.0 * upper, maximumArea);
      trial = simulatePeakPressure(upper, tRelief, mw, gamma, z);
    }

    Trial passing = trial;
    for (int iter = 0; iter < 40 && upper - lower > 1.0e-9 * steadyAreaM2; iter++) {
      double mid = 0.5 * (lower + upper);
      Trial candidate = simulatePeakPressure(mid, tRelief, mw, gamma, z);
      if (candidate.passes(allowablePressurePa)) {
        upper = mid;
        passing = candidate;
      } else if (candidate.peakPressurePa > allowablePressurePa) {
        lower = mid;
      } else {
        result.recordTrial(candidate);
        return result;
      }
    }
    result.recordTrial(passing);
    result.status = SizingStatus.FEASIBLE;
    result.dynamicRequiredAreaM2 = passing.areaM2;
    result.oversizingRatio = steadyAreaM2 / passing.areaM2;
    return result;
  }

  /**
   * Simulates the PSV-cycling fire transient for a candidate orifice and returns the peak vessel pressure.
   *
   * @param orificeAreaM2 candidate PSV orifice area in m²
   * @param initialTempK initial gas temperature in K
   * @param mw molar mass in kg/mol
   * @param gamma heat-capacity ratio
   * @param z compressibility factor
   * @return peak pressure, elapsed time and completion evidence for this area
   */
  private Trial simulatePeakPressure(double orificeAreaM2, double initialTempK, double mw, double gamma, double z) {
    PsvValveModel psv = new PsvValveModel(setPressurePa, blowdownFraction, dischargeCoefficient, orificeAreaM2,
        backPressurePa);
    double cvMass = (GAS_CONSTANT / mw) / (gamma - 1.0);
    double cpMass = gamma * cvMass;
    double t = initialTempK;
    double pStart = setPressurePa * initialPressureFraction;
    double n = pStart * vesselVolume / (z * GAS_CONSTANT * t);
    double mass = n * mw;
    double peak = pStart;
    double time = 0.0;
    while (time < maxTime) {
      double step = Math.min(timeStep, maxTime - time);
      if (mass <= 1.0e-6 || time + step <= time) {
        return new Trial(orificeAreaM2, peak, time, "Inventory or timestep resolution exhausted");
      }
      double p = n * z * GAS_CONSTANT * t / vesselVolume;
      psv.update(p);
      double mDot = psv.massFlowKgPerS(p, t, mw, gamma, z);
      double dm = mDot * step;
      double massNew = mass - dm;
      if (massNew <= 1.0e-9) {
        return new Trial(orificeAreaM2, peak, time, "Discharge exhausted available inventory");
      }
      double uNew = mass * cvMass * t + fireHeatInputW * step - cpMass * t * dm;
      t = uNew / (massNew * cvMass);
      mass = massNew;
      n = mass / mw;
      double endPressure = n * z * GAS_CONSTANT * t / vesselVolume;
      if (!Double.isFinite(endPressure) || endPressure <= 0.0) {
        return new Trial(orificeAreaM2, peak, time, "Invalid thermodynamic state");
      }
      time += step;
      peak = Math.max(peak, endPressure);
    }
    return new Trial(orificeAreaM2, peak, time, null);
  }

  /** Outcome of one area trial. A null interruption means the full horizon was evaluated. */
  private static final class Trial {
    final double areaM2;
    final double peakPressurePa;
    final double elapsedSeconds;
    final String interruption;

    /**
     * Retains the last valid state of a candidate simulation.
     *
     * @param areaM2 tested area in square metres
     * @param peakPressurePa largest observed pressure in Pa
     * @param elapsedSeconds elapsed simulated seconds
     * @param interruption reason for early termination, or null at the specified horizon
     */
    Trial(double areaM2, double peakPressurePa, double elapsedSeconds, String interruption) {
      this.areaM2 = areaM2;
      this.peakPressurePa = peakPressurePa;
      this.elapsedSeconds = elapsedSeconds;
      this.interruption = interruption;
    }

    /**
     * Evaluates the full-horizon pressure criterion.
     *
     * @param limitPa allowable peak pressure in Pa
     * @return whether the trajectory completed within the pressure limit
     */
    boolean passes(double limitPa) {
      return interruption == null && peakPressurePa <= limitPa;
    }
  }

  /** Qualification of the bounded area search at the configured timestep and horizon. */
  public enum SizingStatus {
    /** A complete candidate stayed within the pressure limit. */
    FEASIBLE,
    /** All sampled candidates exceeded the limit within the bounded search. */
    NO_FEASIBLE_AREA_IN_SEARCH,
    /** A numerical interruption or invalid search input prevented a sizing result. */
    INCOMPLETE
  }

  /**
   * Result of a steady-versus-dynamic PSV sizing comparison.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class SizingComparison implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Steady-state API 521 required orifice area in m². */
    public double steadyRequiredAreaM2 = Double.NaN;
    /** Dynamically required orifice area in m². */
    public double dynamicRequiredAreaM2 = Double.NaN;
    /**
     * Steady-to-dynamic oversizing ratio (greater than one indicates steady-state conservatism).
     */
    public double oversizingRatio = Double.NaN;
    /** Allowable accumulated pressure in Pa. */
    public double allowablePressurePa;
    /** Maximum observed pressure in Pa for the reported trial, including partial trials. */
    public double peakDynamicPressurePa = Double.NaN;
    /** Steady-state fire relieving rate in kg/s. */
    public double steadyReliefRateKgPerS;
    /** Search outcome. Historical serialized results without this field are unverified. */
    public SizingStatus status = SizingStatus.INCOMPLETE;
    /** Tested area associated with the reported peak, in m². */
    public double attemptedAreaM2 = Double.NaN;
    /** Nominal integration timestep in seconds. */
    public double timeStepSeconds;
    /** Time reached by the reported trial in seconds. */
    public double elapsedSeconds;
    /** Reason the reported trajectory stopped. */
    public String terminationReason;

    /**
     * Copies evidence from the candidate associated with the search outcome.
     *
     * @param trial reported trajectory
     */
    private void recordTrial(Trial trial) {
      attemptedAreaM2 = trial.areaM2;
      peakDynamicPressurePa = trial.peakPressurePa;
      elapsedSeconds = trial.elapsedSeconds;
      terminationReason = trial.interruption == null ? "Reached simulation horizon" : trial.interruption;
    }
  }
}
