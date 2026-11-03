package neqsim.process.fielddevelopment.wellplanning;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Hydraulics and injectivity of water or gas injection wells with NeqSim fluid properties, a temperature profile, a
 * fracture-pressure limit with thermal stress reduction, and a matrix-injectivity operating point.
 *
 * <p>
 * The tubing traverse is integrated downward from the wellhead (or upward from the bottom hole) with a flash at each
 * segment, so density changes with pressure and temperature (important for gas and CO2 injection) are included: dP/dL =
 * rho g sin(theta) - f rho v^2 / (2 D) in the direction of flow. Two-phase segments are treated as a no-slip
 * homogeneous mixture and flagged. The matrix injectivity is steady-state radial Darcy flow, q = 2 pi k h (P_bh -
 * P_res) / (mu (ln(r_e/r_w) - 0.75 + s)), with the viscosity and density at bottom-hole conditions. The fracture limit
 * is P_frac = gradient x TVD minus the thermal stress reduction alpha E dT / (1 - nu) (upper bound, full cooling of the
 * rock).
 * </p>
 * <p>
 * Limitations: screening; no formation-fluid displacement, relative permeability, plugging or skin growth, no fracture
 * propagation or fracture-injection (Hall plot) modelling, no hydrate or scale compatibility (use the hydrate and scale
 * classes), and the thermal stress reduction is an upper bound. Validation is by analytic limits (static column,
 * Darcy-Weisbach, Darcy radial flow) only.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class InjectionWellHydraulics implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double G = 9.80665;

  private final SystemInterface fluid;
  private double massFlowKgPerS = 20.0;
  private double diameterM = 0.1;
  private double roughnessM = 4.5e-5;
  private double[] md = {0.0, 2000.0};
  private double[] tvd = {0.0, 2000.0};
  private double wellheadTemperatureK = 288.15;
  private double bottomholeTemperatureK = 288.15;
  private WellboreThermalModel thermalModel = null;
  private double fractureGradientBarPerM = 0.0;
  private double formationTemperatureBottomK = Double.NaN;
  private double rockExpansionPerK = 1.0e-5;
  private double rockYoungGPa = 15.0;
  private double rockPoisson = 0.25;
  private double reservoirPressureBara = 200.0;
  private double permeabilityM2 = 100.0 * 9.869233e-16;
  private double thicknessM = 30.0;
  private double drainageRadiusM = 300.0;
  private double wellboreRadiusM = 0.108;
  private double skin = 0.0;
  private double reservoirViscosityPaS = Double.NaN;

  /**
   * Creates an injection hydraulics model.
   *
   * @param injectionFluid fluid with components and mixing rule set (for example CPA water or SRK gas); cloned
   */
  public InjectionWellHydraulics(SystemInterface injectionFluid) {
    this.fluid = injectionFluid.clone();
  }

  /**
   * Sets the mass injection rate.
   *
   * @param kgPerS rate in kg/s
   * @return this model
   */
  public InjectionWellHydraulics setMassFlowRate(double kgPerS) {
    this.massFlowKgPerS = kgPerS;
    return this;
  }

  /**
   * Sets the tubing.
   *
   * @param diameterM inner diameter in m
   * @param roughnessM absolute roughness in m
   * @return this model
   */
  public InjectionWellHydraulics setTubing(double diameterM, double roughnessM) {
    this.diameterM = diameterM;
    this.roughnessM = roughnessM;
    return this;
  }

  /**
   * Sets a deviation survey from the wellhead downward.
   *
   * @param measuredDepthsM increasing measured depths in m starting at zero
   * @param trueVerticalDepthsM true vertical depths in m
   * @return this model
   * @throws IllegalArgumentException if the survey is inconsistent
   */
  public InjectionWellHydraulics setSurvey(double[] measuredDepthsM, double[] trueVerticalDepthsM) {
    if (measuredDepthsM.length != trueVerticalDepthsM.length || measuredDepthsM.length < 2) {
      throw new IllegalArgumentException("survey arrays must have equal length of at least 2");
    }
    for (int i = 1; i < measuredDepthsM.length; i++) {
      if (measuredDepthsM[i] <= measuredDepthsM[i - 1] || trueVerticalDepthsM[i] < trueVerticalDepthsM[i - 1]) {
        throw new IllegalArgumentException("measured depth must increase and TVD must not decrease");
      }
    }
    this.md = measuredDepthsM.clone();
    this.tvd = trueVerticalDepthsM.clone();
    return this;
  }

  /**
   * Sets a straight well divided into segments.
   *
   * @param measuredDepthM measured depth in m
   * @param inclinationFromVerticalDeg inclination from vertical in degrees
   * @param segments number of segments
   * @return this model
   */
  public InjectionWellHydraulics setStraightWell(double measuredDepthM, double inclinationFromVerticalDeg,
      int segments) {
    int n = Math.max(1, segments);
    double[] m = new double[n + 1];
    double[] t = new double[n + 1];
    double c = Math.cos(Math.toRadians(inclinationFromVerticalDeg));
    for (int i = 0; i <= n; i++) {
      m[i] = measuredDepthM * i / n;
      t[i] = m[i] * c;
    }
    return setSurvey(m, t);
  }

  /**
   * Sets a linear temperature profile (injection temperature at surface, warming with depth).
   *
   * @param wellheadTemperatureC injection temperature at the wellhead in degrees Celsius
   * @param bottomholeTemperatureC temperature of the fluid at the bottom in degrees Celsius
   * @return this model
   */
  public InjectionWellHydraulics setLinearTemperature(double wellheadTemperatureC, double bottomholeTemperatureC) {
    this.wellheadTemperatureK = wellheadTemperatureC + 273.15;
    this.bottomholeTemperatureK = bottomholeTemperatureC + 273.15;
    this.thermalModel = null;
    return this;
  }

  /**
   * Uses a Ramey-type thermal model (injection direction) for the temperature profile.
   *
   * @param model thermal model
   * @return this model
   */
  public InjectionWellHydraulics setThermalModel(WellboreThermalModel model) {
    this.thermalModel = model;
    return this;
  }

  /**
   * Sets the fracture gradient and rock thermoelastic data.
   *
   * @param fractureGradientBarPerM fracture gradient in bar per m TVD
   * @param formationTemperatureBottomC undisturbed formation temperature at the injection depth in degrees Celsius
   * @param rockExpansionPerK linear thermal expansion coefficient of the rock in 1/K
   * @param rockYoungGPa rock Young modulus in GPa
   * @param rockPoisson Poisson ratio of the rock
   * @return this model
   */
  public InjectionWellHydraulics setFractureData(double fractureGradientBarPerM, double formationTemperatureBottomC,
      double rockExpansionPerK, double rockYoungGPa, double rockPoisson) {
    this.fractureGradientBarPerM = fractureGradientBarPerM;
    this.formationTemperatureBottomK = formationTemperatureBottomC + 273.15;
    this.rockExpansionPerK = rockExpansionPerK;
    this.rockYoungGPa = rockYoungGPa;
    this.rockPoisson = rockPoisson;
    return this;
  }

  /**
   * Sets the reservoir data for matrix injectivity.
   *
   * @param reservoirPressureBara reservoir pressure in bara
   * @param permeabilityMd permeability in mD
   * @param thicknessM net thickness in m
   * @param drainageRadiusM drainage radius in m
   * @param wellboreRadiusM wellbore radius in m
   * @param skin skin factor
   * @return this model
   */
  public InjectionWellHydraulics setReservoir(double reservoirPressureBara, double permeabilityMd, double thicknessM,
      double drainageRadiusM, double wellboreRadiusM, double skin) {
    this.reservoirPressureBara = reservoirPressureBara;
    this.permeabilityM2 = permeabilityMd * 9.869233e-16;
    this.thicknessM = thicknessM;
    this.drainageRadiusM = drainageRadiusM;
    this.wellboreRadiusM = wellboreRadiusM;
    this.skin = skin;
    return this;
  }

  /**
   * Overrides the viscosity used in the matrix injectivity (for example the formation-water viscosity at reservoir
   * temperature). By default the injected fluid viscosity at bottom-hole conditions is used.
   *
   * @param viscosityPaS viscosity in Pa s
   * @return this model
   */
  public InjectionWellHydraulics setReservoirViscosity(double viscosityPaS) {
    this.reservoirViscosityPaS = viscosityPaS;
    return this;
  }

  /**
   * Returns the injection temperature at a true vertical depth.
   *
   * @param tvdM depth in m
   * @return temperature in K
   */
  private double temperatureAt(double tvdM) {
    if (thermalModel != null) {
      return thermalModel.temperatureAt(tvdM);
    }
    double total = tvd[tvd.length - 1];
    double f = total > 0.0 ? tvdM / total : 0.0;
    return wellheadTemperatureK + (bottomholeTemperatureK - wellheadTemperatureK) * f;
  }

  /** Local properties. */
  private static final class Local {
    double rho;
    double mu;
    int phases;
  }

  /**
   * Flashes the fluid at a state.
   *
   * @param pBar pressure in bara
   * @param tK temperature in K
   * @return local properties
   */
  private Local props(double pBar, double tK) {
    SystemInterface f = fluid.clone();
    f.setPressure(pBar);
    f.setTemperature(tK);
    f.setMultiPhaseCheck(true);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    Local l = new Local();
    l.phases = f.getNumberOfPhases();
    if (l.phases == 1) {
      PhaseInterface ph = f.getPhase(0);
      l.rho = ph.getDensity("kg/m3");
      l.mu = ph.getViscosity("kg/msec");
    } else {
      double vol = 0.0;
      double mass = 0.0;
      double muMass = 0.0;
      for (int i = 0; i < l.phases; i++) {
        PhaseInterface ph = f.getPhase(i);
        double m = ph.getNumberOfMolesInPhase() * ph.getMolarMass();
        mass += m;
        vol += m / ph.getDensity("kg/m3");
        muMass += m * ph.getViscosity("kg/msec");
      }
      l.rho = mass / vol;
      l.mu = muMass / mass;
    }
    return l;
  }

  /**
   * Computes bottom-hole pressure for a given wellhead injection pressure.
   *
   * @param wellheadPressureBara injection pressure at the wellhead in bara
   * @return traverse result
   */
  public Result calculateFromWellhead(double wellheadPressureBara) {
    return march(wellheadPressureBara, true);
  }

  /**
   * Computes the wellhead pressure needed for a bottom-hole pressure.
   *
   * @param bottomholePressureBara bottom-hole flowing pressure in bara
   * @return traverse result
   */
  public Result calculateFromBottomhole(double bottomholePressureBara) {
    return march(bottomholePressureBara, false);
  }

  /**
   * Marches along the survey.
   *
   * @param pStart start pressure in bara
   * @param downward true to march from wellhead to bottom
   * @return result
   */
  private Result march(double pStart, boolean downward) {
    int n = md.length - 1;
    Result r = new Result();
    r.pressureBara = new double[n + 1];
    r.temperatureK = new double[n + 1];
    r.densityKgPerM3 = new double[n];
    r.phaseCount = new int[n];
    r.pressureBara[downward ? 0 : n] = pStart;
    double p = pStart;
    double area = Math.PI * diameterM * diameterM / 4.0;
    for (int k = 0; k < n; k++) {
      int i = downward ? k : n - 1 - k;
      double dL = md[i + 1] - md[i];
      double dZ = tvd[i + 1] - tvd[i];
      double sinT = Math.min(1.0, Math.max(0.0, dZ / dL));
      double tMid = temperatureAt(0.5 * (tvd[i] + tvd[i + 1]));
      double pEnd = p;
      Local l = null;
      double gravity = 0.0;
      double fric = 0.0;
      for (int it = 0; it < 3; it++) {
        l = props(0.5 * (p + pEnd), tMid);
        double v = massFlowKgPerS / (l.rho * area);
        double re = l.rho * v * diameterM / l.mu;
        double f = DriftFluxWellboreHydraulics.frictionFactor(re, roughnessM / diameterM);
        gravity = l.rho * G * sinT * dL / 1.0e5;
        fric = f * l.rho * v * v / (2.0 * diameterM) * dL / 1.0e5;
        pEnd = Math.max(0.5, downward ? p + gravity - fric : p - gravity + fric);
      }
      r.gravityGainBar += gravity;
      r.frictionLossBar += fric;
      r.densityKgPerM3[i] = l.rho;
      r.phaseCount[i] = l.phases;
      r.pressureBara[downward ? i + 1 : i] = pEnd;
      p = pEnd;
    }
    for (int i = 0; i <= n; i++) {
      r.temperatureK[i] = temperatureAt(tvd[i]);
    }
    r.fracturePressureBara = getEffectiveFracturePressureBara(r.temperatureK[n]);
    for (int c : r.phaseCount) {
      if (c > 1) {
        r.twoPhase = true;
      }
    }
    return r;
  }

  /**
   * Returns the effective fracture pressure at the bottom with thermal stress reduction.
   *
   * @param bottomInjectionTemperatureK injection fluid temperature at the bottom in K
   * @return fracture pressure in bara, or NaN if no fracture gradient was set
   */
  public double getEffectiveFracturePressureBara(double bottomInjectionTemperatureK) {
    if (fractureGradientBarPerM <= 0.0) {
      return Double.NaN;
    }
    double pFrac = fractureGradientBarPerM * tvd[tvd.length - 1];
    double dT = Double.isNaN(formationTemperatureBottomK) ? 0.0
        : Math.max(0.0, formationTemperatureBottomK - bottomInjectionTemperatureK);
    double reductionBar = rockExpansionPerK * rockYoungGPa * 1.0e9 / (1.0 - rockPoisson) * dT / 1.0e5;
    return pFrac - reductionBar;
  }

  /**
   * Returns the matrix (Darcy) injection mass rate at a bottom-hole pressure.
   *
   * @param bottomholePressureBara bottom-hole pressure in bara
   * @return mass rate in kg/s (zero if not above reservoir pressure)
   */
  public double matrixMassRateKgPerS(double bottomholePressureBara) {
    if (bottomholePressureBara <= reservoirPressureBara) {
      return 0.0;
    }
    Local l = props(bottomholePressureBara, temperatureAt(tvd[tvd.length - 1]));
    double mu = Double.isNaN(reservoirViscosityPaS) ? l.mu : reservoirViscosityPaS;
    double qRes = 2.0 * Math.PI * permeabilityM2 * thicknessM * (bottomholePressureBara - reservoirPressureBara) * 1.0e5
        / (mu * (Math.log(drainageRadiusM / wellboreRadiusM) - 0.75 + skin));
    return qRes * l.rho;
  }

  /**
   * Finds the matrix operating point at a wellhead pressure: the rate at which the tubing bottom-hole pressure equals
   * the pressure demanded by the reservoir; the rate is capped at the fracture pressure.
   *
   * @param wellheadPressureBara wellhead injection pressure in bara
   * @return operating point
   */
  public OperatingPoint findOperatingPoint(double wellheadPressureBara) {
    double saved = massFlowKgPerS;
    double lo = 1.0e-3;
    double hi = 500.0;
    OperatingPoint op = new OperatingPoint();
    try {
      for (int i = 0; i < 40; i++) {
        double mid = 0.5 * (lo + hi);
        massFlowKgPerS = mid;
        double bhpTubing = calculateFromWellhead(wellheadPressureBara).getBottomholePressureBara();
        double matrix = matrixMassRateKgPerS(bhpTubing);
        if (matrix > mid) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
      massFlowKgPerS = 0.5 * (lo + hi);
      Result r = calculateFromWellhead(wellheadPressureBara);
      op.massRateKgPerS = massFlowKgPerS;
      op.bottomholePressureBara = r.getBottomholePressureBara();
      op.fracturePressureBara = r.fracturePressureBara;
      op.fractureLimited = !Double.isNaN(r.fracturePressureBara) && op.bottomholePressureBara > r.fracturePressureBara;
      if (op.fractureLimited) {
        op.cappedMassRateKgPerS = matrixMassRateKgPerS(r.fracturePressureBara);
      } else {
        op.cappedMassRateKgPerS = op.massRateKgPerS;
      }
    } finally {
      massFlowKgPerS = saved;
    }
    return op;
  }

  /** Result of a tubing traverse. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private double[] pressureBara;
    private double[] temperatureK;
    private double[] densityKgPerM3;
    private int[] phaseCount;
    private double gravityGainBar;
    private double frictionLossBar;
    private double fracturePressureBara = Double.NaN;
    private boolean twoPhase;

    /**
     * Gets node pressures (wellhead first).
     *
     * @return pressures in bara
     */
    public double[] getPressureBara() {
      return pressureBara.clone();
    }

    /**
     * Gets node temperatures.
     *
     * @return temperatures in K
     */
    public double[] getTemperatureK() {
      return temperatureK.clone();
    }

    /**
     * Gets segment densities.
     *
     * @return densities in kg/m3
     */
    public double[] getDensityKgPerM3() {
      return densityKgPerM3.clone();
    }

    /**
     * Gets the number of phases in each segment.
     *
     * @return phase counts
     */
    public int[] getPhaseCount() {
      return phaseCount.clone();
    }

    /**
     * Gets the wellhead pressure.
     *
     * @return pressure in bara
     */
    public double getWellheadPressureBara() {
      return pressureBara[0];
    }

    /**
     * Gets the bottom-hole pressure.
     *
     * @return pressure in bara
     */
    public double getBottomholePressureBara() {
      return pressureBara[pressureBara.length - 1];
    }

    /**
     * Gets the hydrostatic pressure gain.
     *
     * @return pressure in bar
     */
    public double getGravityGainBar() {
      return gravityGainBar;
    }

    /**
     * Gets the friction pressure loss.
     *
     * @return pressure in bar
     */
    public double getFrictionLossBar() {
      return frictionLossBar;
    }

    /**
     * Gets the effective fracture pressure at the bottom.
     *
     * @return pressure in bara, NaN if no fracture data were set
     */
    public double getFracturePressureBara() {
      return fracturePressureBara;
    }

    /**
     * Gets the margin from the bottom-hole pressure to the effective fracture pressure.
     *
     * @return margin in bar (positive is below fracture pressure), NaN if no fracture data were set
     */
    public double getFractureMarginBar() {
      return fracturePressureBara - getBottomholePressureBara();
    }

    /**
     * Checks whether any segment was two-phase.
     *
     * @return true if a two-phase segment occurred (homogeneous treatment applied)
     */
    public boolean isTwoPhase() {
      return twoPhase;
    }
  }

  /** Matrix operating point of an injector. */
  public static final class OperatingPoint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private double massRateKgPerS;
    private double bottomholePressureBara;
    private double fracturePressureBara = Double.NaN;
    private boolean fractureLimited;
    private double cappedMassRateKgPerS;

    /**
     * Gets the matrix-flow mass rate at the wellhead pressure.
     *
     * @return rate in kg/s
     */
    public double getMassRateKgPerS() {
      return massRateKgPerS;
    }

    /**
     * Gets the bottom-hole pressure at the operating point.
     *
     * @return pressure in bara
     */
    public double getBottomholePressureBara() {
      return bottomholePressureBara;
    }

    /**
     * Gets the effective fracture pressure.
     *
     * @return pressure in bara, NaN if not set
     */
    public double getFracturePressureBara() {
      return fracturePressureBara;
    }

    /**
     * Checks whether the matrix operating point would exceed the fracture pressure.
     *
     * @return true if fracturing is predicted
     */
    public boolean isFractureLimited() {
      return fractureLimited;
    }

    /**
     * Gets the matrix rate capped at the fracture pressure.
     *
     * @return rate in kg/s
     */
    public double getCappedMassRateKgPerS() {
      return cappedMassRateKgPerS;
    }

    /**
     * Lists human-readable warnings for the operating point.
     *
     * @return list of warnings
     */
    public List<String> getWarnings() {
      List<String> w = new ArrayList<String>();
      if (fractureLimited) {
        w.add("Matrix operating point exceeds the effective fracture pressure; rate capped at " + cappedMassRateKgPerS
            + " kg/s");
      }
      return w;
    }
  }
}
