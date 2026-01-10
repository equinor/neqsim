package neqsim.process.fielddevelopment.reservoir;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Transient well model with time-dependent IPR.
 *
 * <p>
 * This class models transient pressure behavior in production and injection wells, including:
 * </p>
 * <ul>
 * <li><b>Pressure Buildup:</b> Shut-in pressure recovery analysis</li>
 * <li><b>Pressure Drawdown:</b> Flowing pressure decline during production</li>
 * <li><b>Superposition:</b> Effect of multiple rate changes</li>
 * <li><b>Radius of Investigation:</b> Time-dependent drainage area</li>
 * <li><b>Boundary Effects:</b> Transition from infinite-acting to boundary-dominated</li>
 * </ul>
 *
 * <h2>Mathematical Basis</h2>
 * <p>
 * The line-source solution for radial flow in an infinite-acting reservoir:
 * </p>
 * <p>
 * <code>ΔP = (q·μ)/(4πkh) × Ei(-r²/(4·η·t))</code>
 * </p>
 * <p>
 * where η = k/(φ·μ·ct) is the hydraulic diffusivity. For the wellbore (r=rw), using the logarithmic
 * approximation:
 * </p>
 * <p>
 * <code>P_wf = P_i - (q·μ)/(4πkh) × [ln(4·η·t/r_w²) - γ + 2S]</code>
 * </p>
 *
 * <h2>Superposition Principle</h2>
 * <p>
 * For multiple rate changes at times t₁, t₂, ... tₙ with rates q₁, q₂, ... qₙ:
 * </p>
 * <p>
 * <code>ΔP(t) = Σ (qᵢ - qᵢ₋₁) × P_D(t - tᵢ)</code>
 * </p>
 * <p>
 * This allows modeling complex production histories including shut-ins and rate changes.
 * </p>
 *
 * <h2>Usage Example - Drawdown Analysis</h2>
 * 
 * <pre>{@code
 * TransientWellModel well = new TransientWellModel();
 * well.setReservoirPressure(250.0, "bara");
 * well.setPermeability(100.0, "mD");
 * well.setFormationThickness(30.0, "m");
 * well.setPorosity(0.20);
 * well.setTotalCompressibility(1.5e-4, "1/bar");
 * well.setFluidViscosity(0.5, "cP");
 * well.setWellboreRadius(0.1, "m");
 * well.setSkinFactor(2.0);
 * 
 * // Calculate flowing pressure after 10 hours at 1000 Sm3/day
 * DrawdownResult result = well.calculateDrawdown(1000.0, 10.0);
 * System.out.println("Pwf after 10 hours: " + result.flowingPressure + " bara");
 * }</pre>
 *
 * <h2>Usage Example - Buildup Analysis (Horner Method)</h2>
 * 
 * <pre>{@code
 * // Well flowed at 1500 Sm3/day for 100 hours before shut-in
 * well.addRateChange(0.0, 1500.0); // Start production
 * well.addRateChange(100.0, 0.0); // Shut-in
 * 
 * BuildupResult buildup = well.calculateBuildup(24.0); // After 24 hrs shut-in
 * System.out.println("Pws: " + buildup.shutInPressure + " bara");
 * System.out.println("Permeability from slope: " + buildup.permeabilityFromSlope + " mD");
 * System.out.println("Skin from intercept: " + buildup.skinFromIntercept);
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see InjectionWellModel
 */
public class TransientWellModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  // Physical constants
  private static final double EULER_GAMMA = 0.5772156649;
  private static final double PI = Math.PI;

  /**
   * Well type.
   */
  public enum WellType {
    /** Oil producer. */
    OIL_PRODUCER,
    /** Gas producer. */
    GAS_PRODUCER,
    /** Water injector. */
    WATER_INJECTOR,
    /** Gas injector. */
    GAS_INJECTOR
  }

  /**
   * Boundary type for late-time effects.
   */
  public enum BoundaryType {
    /** Infinite-acting reservoir. */
    INFINITE,
    /** No-flow outer boundary (sealed). */
    NO_FLOW,
    /** Constant pressure outer boundary (aquifer). */
    CONSTANT_PRESSURE
  }

  // Well type
  private WellType wellType = WellType.OIL_PRODUCER;
  private BoundaryType boundaryType = BoundaryType.INFINITE;

  // Reservoir parameters
  private double initialPressure = 250.0; // bara
  private double currentPressure = 250.0; // bara (updated with depletion)
  private double permeability = 100.0; // mD
  private double formationThickness = 30.0; // m
  private double porosity = 0.20; // fraction
  private double totalCompressibility = 1.5e-4; // 1/bar
  private double drainageRadius = 500.0; // m
  private double reservoirTemperature = 363.15; // K

  // Well parameters
  private double wellboreRadius = 0.1; // m
  private double skinFactor = 0.0;
  private double wellboreStorage = 0.0; // m³/bar (wellbore storage coefficient)

  // Fluid properties
  private double fluidViscosity = 0.5; // cP
  private double fluidDensity = 800.0; // kg/m³
  private double formationVolumeFactor = 1.2; // reservoir m³/std m³

  // Rate history for superposition
  private List<RateChange> rateHistory = new ArrayList<>();

  // Derived properties (calculated)
  private double hydraulicDiffusivity; // m²/s
  private double transmissibility; // m³/(bar·s)

  /**
   * Rate change event for superposition.
   */
  public static class RateChange implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Time of rate change (hours). */
    public double time;

    /** Rate after change (Sm³/day). */
    public double rate;

    /**
     * Creates a rate change event.
     *
     * @param time time of change (hours from start)
     * @param rate rate after change (Sm³/day)
     */
    public RateChange(double time, double rate) {
      this.time = time;
      this.rate = rate;
    }
  }

  /**
   * Result of drawdown calculation.
   */
  public static class DrawdownResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Time (hours). */
    public double time;

    /** Flow rate (Sm³/day). */
    public double flowRate;

    /** Initial reservoir pressure (bara). */
    public double initialPressure;

    /** Flowing bottomhole pressure (bara). */
    public double flowingPressure;

    /** Pressure drawdown (bar). */
    public double drawdown;

    /** Radius of investigation (m). */
    public double radiusOfInvestigation;

    /** Whether in infinite-acting period. */
    public boolean infiniteActing;

    /** Productivity index (Sm³/day/bar). */
    public double productivityIndex;

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Drawdown Analysis Result\n");
      sb.append("========================\n");
      sb.append(String.format("Time: %.1f hours%n", time));
      sb.append(String.format("Rate: %.0f Sm³/day%n", flowRate));
      sb.append(String.format("Pi: %.1f bara%n", initialPressure));
      sb.append(String.format("Pwf: %.1f bara%n", flowingPressure));
      sb.append(String.format("Drawdown: %.1f bar%n", drawdown));
      sb.append(String.format("Rinv: %.0f m%n", radiusOfInvestigation));
      sb.append(String.format("Infinite-acting: %s%n", infiniteActing));
      sb.append(String.format("PI: %.2f Sm³/day/bar%n", productivityIndex));
      return sb.toString();
    }
  }

  /**
   * Result of buildup calculation.
   */
  public static class BuildupResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Shut-in time (hours). */
    public double shutInTime;

    /** Producing time before shut-in (hours). */
    public double producingTime;

    /** Rate before shut-in (Sm³/day). */
    public double rateBeforeShutIn;

    /** Shut-in pressure (bara). */
    public double shutInPressure;

    /** Initial reservoir pressure (bara). */
    public double initialPressure;

    /** Pressure recovery (bar). */
    public double pressureRecovery;

    /** Permeability from Horner slope (mD). */
    public double permeabilityFromSlope;

    /** Skin factor from intercept. */
    public double skinFromIntercept;

    /** Extrapolated pressure P* (bara). */
    public double extrapolatedPressure;

    /** Horner slope (bar/log cycle). */
    public double hornerSlope;

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Buildup Analysis Result\n");
      sb.append("=======================\n");
      sb.append(String.format("Producing time: %.1f hours%n", producingTime));
      sb.append(String.format("Shut-in time: %.1f hours%n", shutInTime));
      sb.append(String.format("Rate before shut-in: %.0f Sm³/day%n", rateBeforeShutIn));
      sb.append(String.format("Pws: %.1f bara%n", shutInPressure));
      sb.append(String.format("P*: %.1f bara%n", extrapolatedPressure));
      sb.append(String.format("Horner slope: %.2f bar/log cycle%n", hornerSlope));
      sb.append(String.format("k (from slope): %.1f mD%n", permeabilityFromSlope));
      sb.append(String.format("S (from intercept): %.1f%n", skinFromIntercept));
      return sb.toString();
    }
  }

  /**
   * Transient pressure profile point.
   */
  public static class PressurePoint implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Time (hours). */
    public double time;

    /** Pressure (bara). */
    public double pressure;

    /** Rate at this time (Sm³/day). */
    public double rate;

    /**
     * Constructor.
     *
     * @param time time in hours
     * @param pressure pressure in bara
     * @param rate rate in Sm³/day
     */
    public PressurePoint(double time, double pressure, double rate) {
      this.time = time;
      this.pressure = pressure;
      this.rate = rate;
    }
  }

  /**
   * Creates a new transient well model with default parameters.
   */
  public TransientWellModel() {
    updateDerivedProperties();
  }

  /**
   * Updates derived properties from input parameters.
   */
  private void updateDerivedProperties() {
    // Convert permeability to m² (from mD)
    double kSI = permeability * 9.869233e-16; // m²

    // Convert viscosity to Pa·s (from cP)
    double muSI = fluidViscosity * 1e-3; // Pa·s

    // Hydraulic diffusivity η = k / (φ·μ·ct)
    // ct in 1/Pa (convert from 1/bar)
    double ctSI = totalCompressibility / 1e5; // 1/Pa
    hydraulicDiffusivity = kSI / (porosity * muSI * ctSI); // m²/s

    // Transmissibility kh/μ
    // Result in m³/(Pa·s), convert to m³/(bar·s)
    transmissibility = kSI * formationThickness / muSI * 1e5; // m³/(bar·s)
  }

  // ============================================================================
  // CALCULATION METHODS
  // ============================================================================

  /**
   * Calculates drawdown pressure at a given time.
   *
   * @param rate production rate (Sm³/day)
   * @param timeHours time since start of production (hours)
   * @return drawdown result
   */
  public DrawdownResult calculateDrawdown(double rate, double timeHours) {
    updateDerivedProperties();

    DrawdownResult result = new DrawdownResult();
    result.time = timeHours;
    result.flowRate = rate;
    result.initialPressure = initialPressure;

    // Convert time to seconds
    double timeSec = timeHours * 3600.0;

    // Calculate dimensionless time
    double tD = dimensionlessTime(timeSec);

    // Calculate radius of investigation
    result.radiusOfInvestigation = calculateRadiusOfInvestigation(timeSec);

    // Check if infinite-acting
    result.infiniteActing = result.radiusOfInvestigation < drainageRadius * 0.5;

    // Calculate pressure drop using line-source solution
    double deltaP = calculatePressureDrop(rate, timeSec);

    result.flowingPressure = initialPressure - deltaP;
    result.drawdown = deltaP;
    result.productivityIndex = rate / deltaP;

    return result;
  }

  /**
   * Calculates buildup pressure after shut-in.
   *
   * @param shutInTimeHours time since shut-in (hours)
   * @return buildup result
   */
  public BuildupResult calculateBuildup(double shutInTimeHours) {
    if (rateHistory.isEmpty()) {
      throw new IllegalStateException("No rate history for buildup analysis");
    }

    updateDerivedProperties();

    BuildupResult result = new BuildupResult();
    result.shutInTime = shutInTimeHours;

    // Find last production rate and producing time
    double producingTime = 0;
    double lastRate = 0;
    double shutInStartTime = 0;

    for (int i = 0; i < rateHistory.size(); i++) {
      RateChange rc = rateHistory.get(i);
      if (rc.rate == 0 && lastRate > 0) {
        // This is the shut-in
        shutInStartTime = rc.time;
        break;
      }
      if (rc.rate > 0 && lastRate == 0) {
        // Start of production
        producingTime = 0;
      }
      lastRate = rc.rate;
    }

    // Find last non-zero rate
    for (int i = rateHistory.size() - 1; i >= 0; i--) {
      RateChange rc = rateHistory.get(i);
      if (rc.rate > 0) {
        result.rateBeforeShutIn = rc.rate;
        if (shutInStartTime > rc.time) {
          producingTime = shutInStartTime - rc.time;
        }
        break;
      }
    }

    result.producingTime = producingTime > 0 ? producingTime : 100; // Default 100 hrs

    // Calculate shut-in pressure using superposition
    double pws = calculatePressureWithSuperposition(shutInStartTime + shutInTimeHours);
    result.shutInPressure = pws;
    result.initialPressure = initialPressure;
    result.pressureRecovery = pws - calculatePressureWithSuperposition(shutInStartTime);

    // Horner analysis
    // slope m = q·μ·B / (4πkh) × 2.303 = q·B / (4π × transmissibility) × 2.303
    double qReservoir = result.rateBeforeShutIn * formationVolumeFactor / 86400.0; // m³/s
    double m = qReservoir / (4 * PI * transmissibility) * 2.303; // bar/log cycle
    result.hornerSlope = m;

    // Permeability from slope: k = q·μ·B / (4πmh)
    // m in bar/log cycle, h in m
    double kCalc = qReservoir * fluidViscosity * 1e-3 * formationVolumeFactor
        / (4 * PI * m / 1e5 * formationThickness);
    result.permeabilityFromSlope = kCalc / 9.869233e-16; // Convert to mD

    // Extrapolated pressure P* (Horner time → 1)
    // P* = Pws(Δt→∞) + m × log((tp+Δt)/Δt) as Δt→∞ → P* ≈ Pi
    result.extrapolatedPressure = initialPressure;

    // Skin from intercept
    // S = 1.151 × [(P1hr - Pwf) / m - log(k/(φμctrw²)) + 3.23]
    // Simplified: use pressure at 1 hour shut-in
    double p1hr = calculatePressureWithSuperposition(shutInStartTime + 1.0);
    double pwfAtShutIn = calculatePressureWithSuperposition(shutInStartTime);
    if (m > 0) {
      double logTerm = Math.log10(permeability * 9.869233e-16 / (porosity * fluidViscosity * 1e-3
          * totalCompressibility / 1e5 * wellboreRadius * wellboreRadius)) - 3.23;
      result.skinFromIntercept = 1.151 * ((p1hr - pwfAtShutIn) / m - logTerm);
    } else {
      result.skinFromIntercept = skinFactor;
    }

    return result;
  }

  /**
   * Calculates pressure at a given time using superposition of all rate changes.
   *
   * @param timeHours time (hours)
   * @return pressure (bara)
   */
  public double calculatePressureWithSuperposition(double timeHours) {
    updateDerivedProperties();

    if (rateHistory.isEmpty()) {
      return initialPressure;
    }

    double pressure = initialPressure;
    double previousRate = 0;

    for (RateChange rc : rateHistory) {
      if (rc.time >= timeHours) {
        break;
      }

      double deltaRate = rc.rate - previousRate;
      double deltaTime = (timeHours - rc.time) * 3600.0; // Convert to seconds

      if (deltaTime > 0 && deltaRate != 0) {
        pressure -= calculatePressureDrop(deltaRate, deltaTime);
      }

      previousRate = rc.rate;
    }

    return pressure;
  }

  /**
   * Generates a transient pressure profile.
   *
   * @param timePointsHours array of time points (hours)
   * @return list of pressure points
   */
  public List<PressurePoint> generatePressureProfile(double[] timePointsHours) {
    List<PressurePoint> profile = new ArrayList<>();

    for (double t : timePointsHours) {
      double p = calculatePressureWithSuperposition(t);
      double rate = getRateAtTime(t);
      profile.add(new PressurePoint(t, p, rate));
    }

    return profile;
  }

  /**
   * Generates logarithmically spaced time points for analysis.
   *
   * @param startHours start time (hours)
   * @param endHours end time (hours)
   * @param numPoints number of points
   * @return array of time points
   */
  public double[] generateLogTimePoints(double startHours, double endHours, int numPoints) {
    double[] times = new double[numPoints];
    double logStart = Math.log10(Math.max(startHours, 0.001));
    double logEnd = Math.log10(endHours);
    double logStep = (logEnd - logStart) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      times[i] = Math.pow(10, logStart + i * logStep);
    }
    return times;
  }

  // ============================================================================
  // HELPER CALCULATION METHODS
  // ============================================================================

  /**
   * Calculates pressure drop using line-source solution.
   *
   * <p>
   * Uses the dimensionless pressure function with logarithmic approximation for late time:
   * </p>
   * <p>
   * <code>p_D = 0.5 × [ln(t_D) + 0.80907 + 2S]</code> for t_D > 25
   * </p>
   * <p>
   * Or the exponential integral for early time:
   * </p>
   * <p>
   * <code>p_D = -0.5 × Ei(-1/(4t_D)) + S</code> for t_D ≤ 25
   * </p>
   * <p>
   * The dimensional pressure drop is then:
   * </p>
   * <p>
   * <code>ΔP = q_res / (2πT) × p_D</code>
   * </p>
   * <p>
   * where T = kh/μ is the transmissibility.
   * </p>
   *
   * @param rate rate (Sm³/day)
   * @param timeSec time (seconds)
   * @return pressure drop (bar)
   */
  private double calculatePressureDrop(double rate, double timeSec) {
    // Convert rate to reservoir conditions (m³/s)
    double qReservoir = rate * formationVolumeFactor / 86400.0; // m³/s

    // Dimensionless time tD = k·t / (φ·μ·ct·rw²)
    double tD = dimensionlessTime(timeSec);

    // Dimensionless pressure pD using logarithmic approximation (valid for tD > 25)
    double pD;
    if (tD > 25) {
      pD = 0.5 * (Math.log(tD) + 0.80907 + 2 * skinFactor);
    } else {
      // Use Ei function for early time
      double x = 1.0 / (4.0 * tD);
      pD = -0.5 * exponentialIntegral(-x) + skinFactor;
    }

    // Convert to dimensional pressure drop
    // ΔP = qμ/(2πkh) × pD
    double deltaP = qReservoir / (2 * PI * transmissibility) * pD;

    return deltaP;
  }

  /**
   * Calculates dimensionless time.
   *
   * <p>
   * Dimensionless time is defined as:
   * </p>
   * <p>
   * <code>t_D = η·t / r_w² = (k·t) / (φ·μ·c_t·r_w²)</code>
   * </p>
   * <p>
   * where η is the hydraulic diffusivity. This parameter controls the rate of pressure propagation
   * through the reservoir.
   * </p>
   *
   * @param timeSec time (seconds)
   * @return dimensionless time t_D (dimensionless)
   */
  private double dimensionlessTime(double timeSec) {
    // tD = η·t / rw²
    return hydraulicDiffusivity * timeSec / (wellboreRadius * wellboreRadius);
  }

  /**
   * Calculates radius of investigation.
   *
   * <p>
   * The radius of investigation represents how far the pressure transient has propagated:
   * </p>
   * <p>
   * <code>r_inv = √(4·η·t)</code>
   * </p>
   * <p>
   * This is based on the distance at which the pressure disturbance is approximately 1% of the
   * wellbore value. When r_inv approaches the drainage radius, the well transitions from
   * infinite-acting to boundary-dominated flow.
   * </p>
   *
   * @param timeSec time (seconds)
   * @return radius of investigation (m)
   */
  private double calculateRadiusOfInvestigation(double timeSec) {
    // rinv = √(4·η·t)
    return Math.sqrt(4.0 * hydraulicDiffusivity * timeSec);
  }

  /**
   * Exponential integral function Ei(-x).
   *
   * <p>
   * The exponential integral is defined as:
   * </p>
   * <p>
   * <code>Ei(x) = ∫_{-∞}^{x} (e^t / t) dt</code>
   * </p>
   * <p>
   * For negative arguments (transient flow), this is computed using:
   * </p>
   * <ul>
   * <li><b>Small |x| (< 1):</b> Series expansion: Ei(-x) = -γ - ln(x) + x - x²/(2·2!) + x³/(3·3!) -
   * ...</li>
   * <li><b>Large |x| (≥ 1):</b> Asymptotic expansion: Ei(-x) ≈ -(e^(-x)/x) × [1 - 1/x + 2!/x² -
   * ...]</li>
   * </ul>
   * <p>
   * where γ = 0.5772... is the Euler-Mascheroni constant.
   * </p>
   *
   * @param x argument (should be negative for transient flow)
   * @return Ei(x) value
   */
  private double exponentialIntegral(double x) {
    if (x >= 0) {
      return Double.POSITIVE_INFINITY;
    }

    double absX = -x;
    if (absX < 1) {
      // Series expansion for small x
      double sum = -EULER_GAMMA - Math.log(absX);
      double term = absX;
      for (int n = 1; n <= 50; n++) {
        sum += term / n;
        term *= -absX / (n + 1);
        if (Math.abs(term / n) < 1e-15) {
          break;
        }
      }
      return sum;
    } else {
      // Asymptotic expansion for large x
      double sum = 0;
      double term = 1;
      for (int n = 1; n <= 50; n++) {
        double newTerm = term * n / absX;
        if (newTerm > term) {
          break;
        }
        sum += term;
        term = newTerm;
      }
      return -Math.exp(-absX) / absX * sum;
    }
  }

  /**
   * Gets rate at a given time from rate history.
   *
   * @param timeHours time (hours)
   * @return rate (Sm³/day)
   */
  private double getRateAtTime(double timeHours) {
    double rate = 0;
    for (RateChange rc : rateHistory) {
      if (rc.time <= timeHours) {
        rate = rc.rate;
      } else {
        break;
      }
    }
    return rate;
  }

  // ============================================================================
  // CONFIGURATION METHODS
  // ============================================================================

  /**
   * Adds a rate change to the history.
   *
   * @param timeHours time of change (hours)
   * @param rate rate after change (Sm³/day)
   * @return this for chaining
   */
  public TransientWellModel addRateChange(double timeHours, double rate) {
    rateHistory.add(new RateChange(timeHours, rate));
    // Sort by time
    rateHistory.sort((a, b) -> Double.compare(a.time, b.time));
    return this;
  }

  /**
   * Clears the rate history.
   *
   * @return this for chaining
   */
  public TransientWellModel clearRateHistory() {
    rateHistory.clear();
    return this;
  }

  /**
   * Sets reservoir initial pressure.
   *
   * @param pressure pressure
   * @param unit unit (bara, psia)
   * @return this for chaining
   */
  public TransientWellModel setReservoirPressure(double pressure, String unit) {
    if (unit.equalsIgnoreCase("psia") || unit.equalsIgnoreCase("psi")) {
      this.initialPressure = pressure / 14.504;
    } else {
      this.initialPressure = pressure;
    }
    this.currentPressure = this.initialPressure;
    return this;
  }

  /**
   * Sets formation permeability.
   *
   * @param permeability permeability
   * @param unit unit (mD, D)
   * @return this for chaining
   */
  public TransientWellModel setPermeability(double permeability, String unit) {
    if (unit.equalsIgnoreCase("D")) {
      this.permeability = permeability * 1000.0;
    } else {
      this.permeability = permeability;
    }
    updateDerivedProperties();
    return this;
  }

  /**
   * Sets formation thickness.
   *
   * @param thickness thickness
   * @param unit unit (m, ft)
   * @return this for chaining
   */
  public TransientWellModel setFormationThickness(double thickness, String unit) {
    if (unit.equalsIgnoreCase("ft")) {
      this.formationThickness = thickness * 0.3048;
    } else {
      this.formationThickness = thickness;
    }
    updateDerivedProperties();
    return this;
  }

  /**
   * Sets porosity.
   *
   * @param porosity porosity (fraction 0-1)
   * @return this for chaining
   */
  public TransientWellModel setPorosity(double porosity) {
    this.porosity = porosity;
    updateDerivedProperties();
    return this;
  }

  /**
   * Sets total compressibility.
   *
   * @param compressibility compressibility
   * @param unit unit (1/bar, 1/psi)
   * @return this for chaining
   */
  public TransientWellModel setTotalCompressibility(double compressibility, String unit) {
    if (unit.equalsIgnoreCase("1/psi")) {
      this.totalCompressibility = compressibility * 14.504;
    } else {
      this.totalCompressibility = compressibility;
    }
    updateDerivedProperties();
    return this;
  }

  /**
   * Sets fluid viscosity.
   *
   * @param viscosity viscosity
   * @param unit unit (cP, mPa.s)
   * @return this for chaining
   */
  public TransientWellModel setFluidViscosity(double viscosity, String unit) {
    this.fluidViscosity = viscosity; // cP = mPa.s
    updateDerivedProperties();
    return this;
  }

  /**
   * Sets wellbore radius.
   *
   * @param radius radius
   * @param unit unit (m, ft, in)
   * @return this for chaining
   */
  public TransientWellModel setWellboreRadius(double radius, String unit) {
    if (unit.equalsIgnoreCase("ft")) {
      this.wellboreRadius = radius * 0.3048;
    } else if (unit.equalsIgnoreCase("in")) {
      this.wellboreRadius = radius * 0.0254;
    } else {
      this.wellboreRadius = radius;
    }
    updateDerivedProperties();
    return this;
  }

  /**
   * Sets skin factor.
   *
   * @param skin skin factor (dimensionless)
   * @return this for chaining
   */
  public TransientWellModel setSkinFactor(double skin) {
    this.skinFactor = skin;
    return this;
  }

  /**
   * Sets formation volume factor.
   *
   * @param bFactor B (reservoir volume / surface volume)
   * @return this for chaining
   */
  public TransientWellModel setFormationVolumeFactor(double bFactor) {
    this.formationVolumeFactor = bFactor;
    return this;
  }

  /**
   * Sets drainage radius.
   *
   * @param radius radius
   * @param unit unit (m, ft)
   * @return this for chaining
   */
  public TransientWellModel setDrainageRadius(double radius, String unit) {
    if (unit.equalsIgnoreCase("ft")) {
      this.drainageRadius = radius * 0.3048;
    } else {
      this.drainageRadius = radius;
    }
    return this;
  }

  /**
   * Sets well type.
   *
   * @param type well type
   * @return this for chaining
   */
  public TransientWellModel setWellType(WellType type) {
    this.wellType = type;
    return this;
  }

  /**
   * Sets boundary type.
   *
   * @param type boundary type
   * @return this for chaining
   */
  public TransientWellModel setBoundaryType(BoundaryType type) {
    this.boundaryType = type;
    return this;
  }

  // ============================================================================
  // GETTERS
  // ============================================================================

  /**
   * Gets the hydraulic diffusivity.
   *
   * @return diffusivity (m²/s)
   */
  public double getHydraulicDiffusivity() {
    return hydraulicDiffusivity;
  }

  /**
   * Gets the transmissibility.
   *
   * @return transmissibility (m³/(bar·s))
   */
  public double getTransmissibility() {
    return transmissibility;
  }

  /**
   * Gets the rate history.
   *
   * @return list of rate changes
   */
  public List<RateChange> getRateHistory() {
    return new ArrayList<>(rateHistory);
  }

  /**
   * Gets the initial reservoir pressure.
   *
   * @return pressure (bara)
   */
  public double getInitialPressure() {
    return initialPressure;
  }
}
