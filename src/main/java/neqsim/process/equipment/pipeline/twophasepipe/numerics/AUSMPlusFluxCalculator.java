package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;

/**
 * AUSM+ (Advection Upstream Splitting Method Plus) flux calculator for two-fluid model.
 *
 * <p>
 * Implements the AUSM+ scheme of Liou (1996) adapted for multiphase flow. This scheme is
 * particularly well-suited for:
 * </p>
 * <ul>
 * <li>Large density ratios (gas/liquid ~100-1000x)</li>
 * <li>Low Mach number flows (typical in pipelines)</li>
 * <li>Robust handling of contact discontinuities</li>
 * </ul>
 *
 * <h2>AUSM+ Splitting</h2>
 * <p>
 * The convective flux F = ρu is split into:
 * </p>
 * <ul>
 * <li>Convective flux: F_conv = M_{1/2} * c_{1/2} * Φ (where Φ = ρ or ρu or ρE)</li>
 * <li>Pressure flux: P_{1/2}</li>
 * </ul>
 * <p>
 * The interface Mach number M_{1/2} = M⁺(M_L) + M⁻(M_R) determines upwinding.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Liou, M.S. (1996) - A sequel to AUSM: AUSM+, J. Comp. Physics 129:364-382</li>
 * <li>Liou, M.S. (2006) - A sequel to AUSM, Part II: AUSM+-up for all speeds</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AUSMPlusFluxCalculator implements Serializable {

  private static final long serialVersionUID = 1L;

  /** AUSM+ parameter α (typically 3/16 for AUSM+). */
  private double alpha = 3.0 / 16.0;

  /** AUSM+ parameter β (typically 1/8). */
  private double beta = 1.0 / 8.0;

  /** Minimum sound speed to avoid division by zero. */
  private double minSoundSpeed = 1.0;

  /**
   * State vector for one phase at a cell interface.
   */
  public static class PhaseState implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Density (kg/m³). */
    public double density;

    /** Velocity (m/s). */
    public double velocity;

    /** Pressure (Pa). */
    public double pressure;

    /** Sound speed (m/s). */
    public double soundSpeed;

    /** Specific enthalpy (J/kg). */
    public double enthalpy;

    /** Volume fraction (0-1). */
    public double holdup;

    /**
     * Constructor with all fields.
     *
     * @param density phase density (kg/m3)
     * @param velocity phase velocity (m/s)
     * @param pressure phase pressure (Pa)
     * @param soundSpeed speed of sound (m/s)
     * @param enthalpy specific enthalpy (J/kg)
     * @param holdup volume fraction (0-1)
     */
    public PhaseState(double density, double velocity, double pressure, double soundSpeed,
        double enthalpy, double holdup) {
      this.density = density;
      this.velocity = velocity;
      this.pressure = pressure;
      this.soundSpeed = soundSpeed;
      this.enthalpy = enthalpy;
      this.holdup = holdup;
    }

    /**
     * Default constructor.
     */
    public PhaseState() {}
  }

  /**
   * Flux vector for one phase.
   */
  public static class PhaseFlux implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Mass flux: α * ρ * v (kg/(m²·s)). */
    public double massFlux;

    /** Momentum flux: α * ρ * v² + α * P (Pa or N/m²). */
    public double momentumFlux;

    /** Energy flux: α * ρ * v * H (W/m² or J/(m²·s)). */
    public double energyFlux;

    /** Holdup flux: α * v (m/s). */
    public double holdupFlux;
  }

  /**
   * Combined flux result for both phases.
   */
  public static class TwoFluidFlux implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Gas phase fluxes. */
    public PhaseFlux gasFlux;

    /** Liquid phase fluxes. */
    public PhaseFlux liquidFlux;

    /** Interface Mach number (for diagnostics). */
    public double interfaceMach;

    /**
     * Constructor.
     */
    public TwoFluidFlux() {
      gasFlux = new PhaseFlux();
      liquidFlux = new PhaseFlux();
    }
  }

  /**
   * Constructor.
   */
  public AUSMPlusFluxCalculator() {}

  /**
   * Calculate AUSM+ flux for a single phase.
   *
   * @param left Left state
   * @param right Right state
   * @param area Cross-sectional area (m²)
   * @return Phase flux at interface
   */
  public PhaseFlux calcPhaseFlux(PhaseState left, PhaseState right, double area) {
    PhaseFlux flux = new PhaseFlux();

    // Handle zero holdup cases
    if (left.holdup < 1e-10 && right.holdup < 1e-10) {
      return flux;
    }

    // Interface sound speed (simple average)
    double cL = Math.max(left.soundSpeed, minSoundSpeed);
    double cR = Math.max(right.soundSpeed, minSoundSpeed);
    double cHalf = 0.5 * (cL + cR);

    // Mach numbers
    double ML = left.velocity / cHalf;
    double MR = right.velocity / cHalf;

    // Split Mach numbers using AUSM+ polynomials
    double Mplus = calcMachPlus(ML);
    double Mminus = calcMachMinus(MR);
    double Mhalf = Mplus + Mminus;

    // Split pressures
    double Pplus = calcPressurePlus(ML) * left.pressure * left.holdup;
    double Pminus = calcPressureMinus(MR) * right.pressure * right.holdup;
    double Phalf = Pplus + Pminus;

    // Upwind selection based on interface Mach number
    double rho, v, H, alpha;
    if (Mhalf >= 0) {
      rho = left.density;
      v = left.velocity;
      H = left.enthalpy;
      alpha = left.holdup;
    } else {
      rho = right.density;
      v = right.velocity;
      H = right.enthalpy;
      alpha = right.holdup;
    }

    // Convective mass flux
    double mDot = cHalf * Mhalf * alpha * rho;

    // Fluxes
    flux.massFlux = mDot * area;
    flux.momentumFlux = mDot * v * area + Phalf * area;
    flux.energyFlux = mDot * H * area;
    flux.holdupFlux = Mhalf >= 0 ? left.holdup * left.velocity : right.holdup * right.velocity;

    return flux;
  }

  /**
   * Calculate AUSM+ fluxes for both phases in two-fluid model.
   *
   * @param gasLeft Gas state on left of interface
   * @param gasRight Gas state on right of interface
   * @param liquidLeft Liquid state on left of interface
   * @param liquidRight Liquid state on right of interface
   * @param area Cross-sectional area (m²)
   * @return Combined flux for both phases
   */
  public TwoFluidFlux calcTwoFluidFlux(PhaseState gasLeft, PhaseState gasRight,
      PhaseState liquidLeft, PhaseState liquidRight, double area) {

    TwoFluidFlux result = new TwoFluidFlux();

    // Calculate flux for each phase independently
    result.gasFlux = calcPhaseFlux(gasLeft, gasRight, area);
    result.liquidFlux = calcPhaseFlux(liquidLeft, liquidRight, area);

    // Store interface Mach for diagnostics (use gas phase)
    double cHalf = 0.5 * (Math.max(gasLeft.soundSpeed, minSoundSpeed)
        + Math.max(gasRight.soundSpeed, minSoundSpeed));
    double ML = gasLeft.velocity / cHalf;
    double MR = gasRight.velocity / cHalf;
    result.interfaceMach = calcMachPlus(ML) + calcMachMinus(MR);

    return result;
  }

  /**
   * AUSM+ M⁺ splitting function.
   *
   * <p>
   * For |M| ≤ 1: M⁺ = ¼(M+1)² + β(M²-1)². For M &gt; 1: M⁺ = ½(M + |M|). For M &lt; -1: M⁺ = 0.
   * </p>
   *
   * @param M Mach number
   * @return M⁺ value
   */
  public double calcMachPlus(double M) {
    if (Math.abs(M) <= 1.0) {
      double term1 = 0.25 * (M + 1.0) * (M + 1.0);
      double term2 = beta * (M * M - 1.0) * (M * M - 1.0);
      return term1 + term2;
    } else {
      return 0.5 * (M + Math.abs(M));
    }
  }

  /**
   * AUSM+ M⁻ splitting function.
   *
   * <p>
   * For |M| ≤ 1: M⁻ = -¼(M-1)² - β(M²-1)². For M &lt; -1: M⁻ = ½(M - |M|). For M &gt; 1: M⁻ = 0.
   * </p>
   *
   * @param M Mach number
   * @return M⁻ value
   */
  public double calcMachMinus(double M) {
    if (Math.abs(M) <= 1.0) {
      double term1 = -0.25 * (M - 1.0) * (M - 1.0);
      double term2 = -beta * (M * M - 1.0) * (M * M - 1.0);
      return term1 + term2;
    } else {
      return 0.5 * (M - Math.abs(M));
    }
  }

  /**
   * AUSM+ P⁺ pressure splitting function.
   *
   * <p>
   * For |M| ≤ 1: P⁺ = ¼(M+1)²(2-M) + α*M*(M²-1)². For M &gt; 1: P⁺ = 1. For M &lt; -1: P⁺ = 0.
   * </p>
   *
   * @param M Mach number
   * @return P⁺ value
   */
  public double calcPressurePlus(double M) {
    if (Math.abs(M) <= 1.0) {
      double term1 = 0.25 * (M + 1.0) * (M + 1.0) * (2.0 - M);
      double term2 = alpha * M * (M * M - 1.0) * (M * M - 1.0);
      return term1 + term2;
    } else if (M > 1.0) {
      return 1.0;
    } else {
      return 0.0;
    }
  }

  /**
   * AUSM+ P⁻ pressure splitting function.
   *
   * <p>
   * For |M| ≤ 1: P⁻ = ¼(M-1)²(2+M) - α*M*(M²-1)². For M &lt; -1: P⁻ = 1. For M &gt; 1: P⁻ = 0.
   * </p>
   *
   * @param M Mach number
   * @return P⁻ value
   */
  public double calcPressureMinus(double M) {
    if (Math.abs(M) <= 1.0) {
      double term1 = 0.25 * (M - 1.0) * (M - 1.0) * (2.0 + M);
      double term2 = -alpha * M * (M * M - 1.0) * (M * M - 1.0);
      return term1 + term2;
    } else if (M < -1.0) {
      return 1.0;
    } else {
      return 0.0;
    }
  }

  /**
   * Calculate simple first-order upwind flux (for comparison/fallback).
   *
   * @param left Left state
   * @param right Right state
   * @param area Cross-sectional area (m²)
   * @return Phase flux at interface
   */
  public PhaseFlux calcUpwindFlux(PhaseState left, PhaseState right, double area) {
    PhaseFlux flux = new PhaseFlux();

    // Average velocity for upwind direction
    double vAvg = 0.5 * (left.velocity + right.velocity);

    PhaseState upwind = vAvg >= 0 ? left : right;

    flux.massFlux = upwind.holdup * upwind.density * upwind.velocity * area;
    flux.momentumFlux = upwind.holdup * upwind.density * upwind.velocity * upwind.velocity * area
        + upwind.holdup * upwind.pressure * area;
    flux.energyFlux = upwind.holdup * upwind.density * upwind.velocity * upwind.enthalpy * area;
    flux.holdupFlux = upwind.holdup * upwind.velocity;

    return flux;
  }

  /**
   * Calculate Rusanov (local Lax-Friedrichs) flux for robustness.
   *
   * <p>
   * More dissipative but unconditionally stable. Useful for startup.
   * </p>
   *
   * @param left Left state
   * @param right Right state
   * @param area Cross-sectional area (m²)
   * @return Phase flux at interface
   */
  public PhaseFlux calcRusanovFlux(PhaseState left, PhaseState right, double area) {
    PhaseFlux flux = new PhaseFlux();

    // Maximum wave speed
    double sMax = Math.max(Math.abs(left.velocity) + left.soundSpeed,
        Math.abs(right.velocity) + right.soundSpeed);

    // Conservative variables
    double UL_mass = left.holdup * left.density;
    double UR_mass = right.holdup * right.density;
    double UL_mom = left.holdup * left.density * left.velocity;
    double UR_mom = right.holdup * right.density * right.velocity;
    double UL_ene = left.holdup * left.density * left.enthalpy;
    double UR_ene = right.holdup * right.density * right.enthalpy;

    // Fluxes at left and right
    double FL_mass = UL_mom;
    double FR_mass = UR_mom;
    double FL_mom = UL_mom * left.velocity + left.holdup * left.pressure;
    double FR_mom = UR_mom * right.velocity + right.holdup * right.pressure;
    double FL_ene = UL_mom * left.enthalpy;
    double FR_ene = UR_mom * right.enthalpy;

    // Rusanov flux: F = 0.5*(F_L + F_R) - 0.5*s_max*(U_R - U_L)
    flux.massFlux = (0.5 * (FL_mass + FR_mass) - 0.5 * sMax * (UR_mass - UL_mass)) * area;
    flux.momentumFlux = (0.5 * (FL_mom + FR_mom) - 0.5 * sMax * (UR_mom - UL_mom)) * area;
    flux.energyFlux = (0.5 * (FL_ene + FR_ene) - 0.5 * sMax * (UR_ene - UL_ene)) * area;

    return flux;
  }

  /**
   * Get AUSM+ α parameter.
   *
   * @return α value
   */
  public double getAlpha() {
    return alpha;
  }

  /**
   * Set AUSM+ α parameter.
   *
   * @param alpha New α value (typically 3/16)
   */
  public void setAlpha(double alpha) {
    this.alpha = alpha;
  }

  /**
   * Get AUSM+ β parameter.
   *
   * @return β value
   */
  public double getBeta() {
    return beta;
  }

  /**
   * Set AUSM+ β parameter.
   *
   * @param beta New β value (typically 1/8)
   */
  public void setBeta(double beta) {
    this.beta = beta;
  }

  /**
   * Get minimum sound speed.
   *
   * @return Minimum sound speed (m/s)
   */
  public double getMinSoundSpeed() {
    return minSoundSpeed;
  }

  /**
   * Set minimum sound speed.
   *
   * @param minSoundSpeed Minimum sound speed (m/s)
   */
  public void setMinSoundSpeed(double minSoundSpeed) {
    this.minSoundSpeed = minSoundSpeed;
  }
}
