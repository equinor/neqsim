package neqsim.process.safety.depressurization;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  private static final Logger logger = LogManager.getLogger(DynamicPsvSizingStudy.class);
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
   * @throws IllegalArgumentException if {@code fluid} is null or an argument is not positive
   */
  public DynamicPsvSizingStudy(SystemInterface fluid, double vesselVolume, double fireHeatInputW, double setPressurePa,
      double overpressureFraction, double backPressurePa) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    if (vesselVolume <= 0.0 || fireHeatInputW <= 0.0 || setPressurePa <= 0.0 || overpressureFraction <= 0.0
        || backPressurePa <= 0.0) {
      throw new IllegalArgumentException("dimensions, fire heat, pressures and overpressure must be positive");
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
    if (blowdownFraction <= 0.0 || blowdownFraction >= 1.0) {
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
    if (dischargeCoefficient <= 0.0) {
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
    if (latentHeat <= 0.0) {
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
    if (dt <= 0.0) {
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
    if (tMax <= 0.0) {
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

    double allowablePressurePa = setPressurePa * (1.0 + overpressureFraction);

    // --- Steady-state API 521 required area for the peak fire vapour rate ---
    double steadyReliefRate = fireHeatInputW / latentHeat;
    ReliefValveSizing.PSVSizingResult steady = ReliefValveSizing.calculateRequiredArea(steadyReliefRate, setPressurePa,
        overpressureFraction, backPressurePa, tRelief, mw, z, gamma, false, false);
    double steadyAreaM2 = steady.getRequiredArea();

    // --- Dynamic required area by bisection over orifice area ---
    double lo = steadyAreaM2 * 1.0e-3;
    double hi = steadyAreaM2 * 3.0;
    double peakAtHi = simulatePeakPressure(hi, tRelief, mw, gamma, z);
    if (peakAtHi > allowablePressurePa) {
      // Even a large orifice cannot hold the pressure; expand the upper bound once.
      hi = steadyAreaM2 * 10.0;
      peakAtHi = simulatePeakPressure(hi, tRelief, mw, gamma, z);
    }
    double dynamicAreaM2 = hi;
    double peakDynamicPa = peakAtHi;
    for (int iter = 0; iter < 40; iter++) {
      double mid = 0.5 * (lo + hi);
      double peak = simulatePeakPressure(mid, tRelief, mw, gamma, z);
      if (peak <= allowablePressurePa) {
        hi = mid;
        dynamicAreaM2 = mid;
        peakDynamicPa = peak;
      } else {
        lo = mid;
      }
      if ((hi - lo) <= 1.0e-9 * steadyAreaM2) {
        break;
      }
    }

    double oversizingRatio = dynamicAreaM2 > 0.0 ? steadyAreaM2 / dynamicAreaM2 : Double.NaN;

    SizingComparison result = new SizingComparison();
    result.steadyRequiredAreaM2 = steadyAreaM2;
    result.dynamicRequiredAreaM2 = dynamicAreaM2;
    result.oversizingRatio = oversizingRatio;
    result.allowablePressurePa = allowablePressurePa;
    result.peakDynamicPressurePa = peakDynamicPa;
    result.steadyReliefRateKgPerS = steadyReliefRate;
    logger.debug("steady area {} m2, dynamic area {} m2, oversizing {}", steadyAreaM2, dynamicAreaM2, oversizingRatio);
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
   * @return peak vessel pressure in Pa over the transient
   */
  private double simulatePeakPressure(double orificeAreaM2, double initialTempK, double mw, double gamma, double z) {
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
    while (time < maxTime && mass > 1.0e-6) {
      double p = n * z * GAS_CONSTANT * t / vesselVolume;
      if (p > peak) {
        peak = p;
      }
      psv.update(p);
      double mDot = psv.isOpen() ? psv.massFlowKgPerS(p, t, mw, gamma, z) : 0.0;
      double dm = Math.min(mDot * timeStep, mass);
      double uOld = mass * cvMass * t;
      double uNew = uOld + fireHeatInputW * timeStep - cpMass * t * dm;
      double massNew = mass - dm;
      if (massNew <= 1.0e-9) {
        break;
      }
      t = uNew / (massNew * cvMass);
      if (t <= 0.0 || Double.isNaN(t)) {
        break;
      }
      mass = massNew;
      n = mass / mw;
      time += timeStep;
    }
    return peak;
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
    public double steadyRequiredAreaM2;
    /** Dynamically required orifice area in m². */
    public double dynamicRequiredAreaM2;
    /**
     * Steady-to-dynamic oversizing ratio (greater than one indicates steady-state conservatism).
     */
    public double oversizingRatio;
    /** Allowable accumulated pressure in Pa. */
    public double allowablePressurePa;
    /** Peak vessel pressure in Pa with the dynamically required orifice. */
    public double peakDynamicPressurePa;
    /** Steady-state fire relieving rate in kg/s. */
    public double steadyReliefRateKgPerS;
  }
}
