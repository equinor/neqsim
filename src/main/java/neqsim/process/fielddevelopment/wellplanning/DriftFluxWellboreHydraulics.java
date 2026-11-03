package neqsim.process.fielddevelopment.wellplanning;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Mechanistic drift-flux hydraulics for vertical and deviated production tubing with flow-regime, liquid-loading and
 * erosional-velocity output.
 *
 * <p>
 * The pressure traverse is integrated segment by segment with NeqSim flashes at the local pressure and temperature. The
 * gradient is gravity plus wall friction. Liquid holdup comes from the drift-flux relation alpha = v_sg / (C0 v_m +
 * v_d) with Hasan and Kabir style closures:
 * </p>
 * <ul>
 * <li>bubble flow (alpha up to 0.25): C0 = 1.2, v_d = 1.53 (g sigma d_rho / rho_L^2)^0.25 sqrt(sin theta)</li>
 * <li>slug flow (alpha below 0.52): C0 = 1.2, Taylor bubble v_d = (0.35 sin theta + 0.54 cos theta) sqrt(g D d_rho /
 * rho_L)</li>
 * <li>churn flow: C0 = 1.15 with the Taylor bubble drift velocity</li>
 * <li>annular flow (v_sg at or above the Turner/Wallis film-reversal velocity 3.1 (sigma g d_rho / rho_G^2)^0.25 and
 * beyond the slug limit): Wallis void fraction alpha = (1 + X^0.8)^-0.378 with the Lockhart-Martinelli parameter X</li>
 * </ul>
 * <p>
 * Liquid loading onset is flagged when v_sg falls below the Turner critical velocity. The erosional velocity is the API
 * RP 14E value v_e = C / sqrt(rho_ns) with C = 100 (lb/ft3)^0.5 ft/s by default.
 * </p>
 * <p>
 * Limitations: screening-level; acceleration pressure drop, emulsion viscosity, liquid entrainment fractions and
 * stratified flow in near-horizontal sections are not modelled (a warning is issued below 10 degrees from horizontal).
 * Validation is limited to analytic limits (single-phase Darcy-Weisbach, static column) and comparison with Beggs and
 * Brill; it is not calibrated to field data.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class DriftFluxWellboreHydraulics implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double G = 9.80665;
  private static final double C_API_FIELD_TO_SI = 0.3048 * Math.sqrt(16.01846);

  /** Flow regime classification of a tubing segment. */
  public enum FlowRegime {
    /** Gas only. */
    SINGLE_PHASE_GAS,
    /** Liquid only. */
    SINGLE_PHASE_LIQUID,
    /** Bubble flow. */
    BUBBLE,
    /** Slug (Taylor bubble) flow. */
    SLUG,
    /** Churn flow. */
    CHURN,
    /** Annular flow (gas core with liquid film). */
    ANNULAR
  }

  private SystemInterface fluid;
  private double massFlowKgPerS = 10.0;
  private double diameterM = 0.1;
  private double roughnessM = 4.5e-5;
  private double[] md = {0.0, 2000.0};
  private double[] tvd = {0.0, 2000.0};
  private double[] tempTvd = null;
  private double[] tempK = null;
  private WellboreThermalModel thermalModel = null;
  private double turnerCoefficient = 3.1;
  private double erosionalCField = 100.0;
  private boolean multiPhaseCheck = false;

  /**
   * Creates a hydraulics model.
   *
   * @param fluid fluid with components and mixing rule already set; it is cloned for each flash
   */
  public DriftFluxWellboreHydraulics(SystemInterface fluid) {
    this.fluid = fluid.clone();
  }

  /**
   * Sets the total mass flow of the wellstream.
   *
   * @param kgPerS mass flow in kg/s
   * @return this model
   */
  public DriftFluxWellboreHydraulics setMassFlowRate(double kgPerS) {
    this.massFlowKgPerS = kgPerS;
    return this;
  }

  /**
   * Gets the mass flow of the wellstream.
   *
   * @return mass flow in kg/s
   */
  public double getMassFlowRate() {
    return massFlowKgPerS;
  }

  /**
   * Sets the tubing inner diameter and wall roughness.
   *
   * @param diameterM inner diameter in m
   * @param roughnessM absolute roughness in m
   * @return this model
   */
  public DriftFluxWellboreHydraulics setTubing(double diameterM, double roughnessM) {
    this.diameterM = diameterM;
    this.roughnessM = roughnessM;
    return this;
  }

  /**
   * Sets a straight well of constant inclination.
   *
   * @param measuredDepthM measured depth in m
   * @param inclinationFromVerticalDeg inclination from vertical in degrees (0 = vertical)
   * @param segments number of segments
   * @return this model
   */
  public DriftFluxWellboreHydraulics setStraightWell(double measuredDepthM, double inclinationFromVerticalDeg,
      int segments) {
    int n = Math.max(1, segments);
    md = new double[n + 1];
    tvd = new double[n + 1];
    double cosInc = Math.cos(Math.toRadians(inclinationFromVerticalDeg));
    for (int i = 0; i <= n; i++) {
      md[i] = measuredDepthM * i / n;
      tvd[i] = md[i] * cosInc;
    }
    return this;
  }

  /**
   * Sets a deviation survey from the wellhead downward.
   *
   * @param measuredDepthsM increasing measured depths in m starting at the wellhead
   * @param trueVerticalDepthsM true vertical depths in m (non-decreasing)
   * @return this model
   * @throws IllegalArgumentException if the arrays are inconsistent
   */
  public DriftFluxWellboreHydraulics setSurvey(double[] measuredDepthsM, double[] trueVerticalDepthsM) {
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
   * Sets a linear temperature profile in true vertical depth.
   *
   * @param wellheadTemperatureC wellhead temperature in degrees Celsius
   * @param bottomholeTemperatureC bottom-hole temperature in degrees Celsius
   * @return this model
   */
  public DriftFluxWellboreHydraulics setLinearTemperature(double wellheadTemperatureC, double bottomholeTemperatureC) {
    this.tempTvd = new double[] {0.0, 1.0};
    this.tempK = new double[] {wellheadTemperatureC + 273.15, bottomholeTemperatureC + 273.15};
    this.thermalModel = null;
    return this;
  }

  /**
   * Sets a tabulated temperature profile.
   *
   * @param tvdM increasing true vertical depths in m
   * @param temperaturesC temperatures in degrees Celsius
   * @return this model
   */
  public DriftFluxWellboreHydraulics setTemperatureProfile(double[] tvdM, double[] temperaturesC) {
    this.tempTvd = tvdM.clone();
    this.tempK = new double[temperaturesC.length];
    for (int i = 0; i < tempK.length; i++) {
      tempK[i] = temperaturesC[i] + 273.15;
    }
    this.thermalModel = null;
    return this;
  }

  /**
   * Uses a Ramey-type thermal model for the temperature profile.
   *
   * @param model thermal model giving temperature versus true vertical depth
   * @return this model
   */
  public DriftFluxWellboreHydraulics setThermalModel(WellboreThermalModel model) {
    this.thermalModel = model;
    this.tempTvd = null;
    this.tempK = null;
    return this;
  }

  /**
   * Sets the Turner coefficient (3.1 theoretical droplet/film value; multiply by 1.2 for the field-adjusted value).
   *
   * @param coefficient coefficient in v = c (sigma g d_rho / rho_G^2)^0.25
   * @return this model
   */
  public DriftFluxWellboreHydraulics setTurnerCoefficient(double coefficient) {
    this.turnerCoefficient = coefficient;
    return this;
  }

  /**
   * Sets the API RP 14E C factor in field units.
   *
   * @param cField C factor in ft/s (lb/ft3)^0.5, typically 100 for continuous and 125 for intermittent service
   * @return this model
   */
  public DriftFluxWellboreHydraulics setErosionalCFactor(double cField) {
    this.erosionalCField = cField;
    return this;
  }

  /**
   * Enables the NeqSim multiphase check (needed when free water and hydrocarbon liquid coexist).
   *
   * @param enabled true to enable
   * @return this model
   */
  public DriftFluxWellboreHydraulics setMultiPhaseCheck(boolean enabled) {
    this.multiPhaseCheck = enabled;
    return this;
  }

  /**
   * Returns the temperature at a true vertical depth.
   *
   * @param tvdM true vertical depth in m
   * @return temperature in K
   */
  private double temperatureAt(double tvdM) {
    if (thermalModel != null) {
      return thermalModel.temperatureAt(tvdM);
    }
    if (tempK == null) {
      return 288.15 + 0.03 * tvdM;
    }
    if (tempK.length == 2 && tempTvd[1] == 1.0) {
      double total = tvd[tvd.length - 1];
      double frac = total > 0.0 ? tvdM / total : 0.0;
      return tempK[0] + (tempK[1] - tempK[0]) * frac;
    }
    if (tvdM <= tempTvd[0]) {
      return tempK[0];
    }
    for (int i = 1; i < tempTvd.length; i++) {
      if (tvdM <= tempTvd[i]) {
        double f = (tvdM - tempTvd[i - 1]) / (tempTvd[i] - tempTvd[i - 1]);
        return tempK[i - 1] + f * (tempK[i] - tempK[i - 1]);
      }
    }
    return tempK[tempK.length - 1];
  }

  /**
   * Local state and gradient at one pressure, temperature and inclination.
   */
  private static final class Local implements Serializable {
    private static final long serialVersionUID = 1000L;
    double gradientPaPerM;
    double hydrostaticPaPerM;
    double frictionPaPerM;
    double liquidHoldup;
    double voidFraction;
    FlowRegime regime;
    double vsg;
    double vsl;
    double rhoMixture;
    double rhoNoSlip;
    double turnerRatio = Double.NaN;
    double erosionalRatio;
  }

  /**
   * Evaluates the gradient at given conditions.
   *
   * @param pBar pressure in bara
   * @param tK temperature in K
   * @param thetaRad inclination from horizontal in radians
   * @return local state
   */
  private Local evaluate(double pBar, double tK, double thetaRad) {
    SystemInterface f = fluid.clone();
    f.setTotalFlowRate(massFlowKgPerS, "kg/sec");
    f.setPressure(pBar);
    f.setTemperature(tK);
    f.setMultiPhaseCheck(multiPhaseCheck);
    ThermodynamicOperations ops = new ThermodynamicOperations(f);
    ops.TPflash();
    f.initProperties();

    double area = Math.PI * diameterM * diameterM / 4.0;
    double mG = 0.0;
    double mL = 0.0;
    double volL = 0.0;
    double muLNum = 0.0;
    double rhoG = 0.0;
    double muG = 0.0;
    int gasIdx = -1;
    int liqIdx = -1;
    for (int i = 0; i < f.getNumberOfPhases(); i++) {
      PhaseInterface ph = f.getPhase(i);
      PhaseType type = ph.getType();
      if (type == PhaseType.HYDRATE || type == PhaseType.SOLID || type == PhaseType.WAX) {
        continue;
      }
      double m = ph.getFlowRate("kg/sec");
      if (type == PhaseType.GAS) {
        mG += m;
        rhoG = ph.getDensity("kg/m3");
        muG = ph.getViscosity("kg/msec");
        gasIdx = i;
      } else {
        double rho = ph.getDensity("kg/m3");
        mL += m;
        volL += m / rho;
        muLNum += m / rho * ph.getViscosity("kg/msec");
        if (liqIdx < 0) {
          liqIdx = i;
        }
      }
    }
    Local s = new Local();
    double sinT = Math.sin(thetaRad);
    double cosT = Math.cos(thetaRad);
    double rhoL = mL > 0.0 ? mL / volL : 0.0;
    double muL = mL > 0.0 ? muLNum / volL : 0.0;
    s.vsg = mG / (rhoG > 0.0 ? rhoG : 1.0) / area;
    s.vsl = mL > 0.0 ? mL / rhoL / area : 0.0;
    double vm = s.vsg + s.vsl;
    double alpha;
    if (mL <= 0.0 || volL <= 0.0) {
      s.regime = FlowRegime.SINGLE_PHASE_GAS;
      alpha = 1.0;
      rhoL = 0.0;
      muL = 0.0;
    } else if (mG <= 0.0) {
      s.regime = FlowRegime.SINGLE_PHASE_LIQUID;
      alpha = 0.0;
      rhoG = 0.0;
      muG = 0.0;
    } else {
      double sigma = 0.02;
      try {
        f.calcInterfaceProperties();
        double st = f.getInterfacialTension(gasIdx, liqIdx);
        if (st > 1.0e-4 && st < 0.2) {
          sigma = st;
        }
      } catch (RuntimeException e) {
        sigma = 0.02;
      }
      double dRho = Math.max(rhoL - rhoG, 1.0);
      double vTurner = turnerCoefficient * Math.pow(sigma * G * dRho / (rhoG * rhoG), 0.25);
      s.turnerRatio = s.vsg / vTurner;
      double vdBubble = 1.53 * Math.pow(G * sigma * dRho / (rhoL * rhoL), 0.25) * Math.sqrt(Math.max(sinT, 0.0));
      double vdTaylor = (0.35 * sinT + 0.54 * cosT) * Math.sqrt(G * diameterM * dRho / rhoL);
      double alphaBubble = s.vsg / (1.2 * vm + vdBubble);
      double alphaSlug = s.vsg / (1.2 * vm + vdTaylor);
      if (alphaBubble <= 0.25) {
        s.regime = FlowRegime.BUBBLE;
        alpha = alphaBubble;
      } else if (alphaSlug < 0.52) {
        s.regime = FlowRegime.SLUG;
        alpha = alphaSlug;
      } else if (s.vsg >= vTurner) {
        s.regime = FlowRegime.ANNULAR;
        double x = Math.pow(mL / mG, 0.9) * Math.sqrt(rhoG / rhoL) * Math.pow(muL / Math.max(muG, 1.0e-9), 0.1);
        alpha = Math.pow(1.0 + Math.pow(x, 0.8), -0.378);
      } else {
        s.regime = FlowRegime.CHURN;
        alpha = s.vsg / (1.15 * vm + vdTaylor);
      }
      alpha = Math.min(Math.max(alpha, 0.0), 1.0);
    }
    s.voidFraction = alpha;
    s.liquidHoldup = 1.0 - alpha;
    s.rhoMixture = rhoL * (1.0 - alpha) + rhoG * alpha;
    double lambdaL = vm > 0.0 ? s.vsl / vm : 0.0;
    s.rhoNoSlip = rhoL * lambdaL + rhoG * (1.0 - lambdaL);
    double muM;
    if (s.regime == FlowRegime.SINGLE_PHASE_GAS) {
      muM = muG;
    } else if (s.regime == FlowRegime.SINGLE_PHASE_LIQUID) {
      muM = muL;
    } else {
      muM = Math.pow(muL, 1.0 - alpha) * Math.pow(muG, alpha);
    }
    double re = s.rhoMixture * vm * diameterM / Math.max(muM, 1.0e-12);
    double fr = frictionFactor(re, roughnessM / diameterM);
    s.hydrostaticPaPerM = s.rhoMixture * G * sinT;
    s.frictionPaPerM = fr * s.rhoMixture * vm * vm / (2.0 * diameterM);
    s.gradientPaPerM = s.hydrostaticPaPerM + s.frictionPaPerM;
    double ve = erosionalCField * C_API_FIELD_TO_SI / Math.sqrt(Math.max(s.rhoNoSlip, 1.0e-6));
    s.erosionalRatio = vm / ve;
    return s;
  }

  /**
   * Darcy friction factor (laminar 64/Re, turbulent Haaland).
   *
   * @param re Reynolds number
   * @param relRough relative roughness
   * @return Darcy friction factor
   */
  static double frictionFactor(double re, double relRough) {
    if (re < 2300.0) {
      return 64.0 / Math.max(re, 1.0);
    }
    double term = Math.pow(relRough / 3.7, 1.11) + 6.9 / re;
    double fTurb = Math.pow(-1.8 * Math.log10(term), -2.0);
    if (re < 4000.0) {
      double fLam = 64.0 / 2300.0;
      return fLam + (fTurb - fLam) * (re - 2300.0) / 1700.0;
    }
    return fTurb;
  }

  /**
   * Computes bottom-hole pressure for a given wellhead pressure (VLP point).
   *
   * @param wellheadPressureBara wellhead pressure in bara
   * @return result including the full traverse
   * @throws IllegalStateException if the traverse pressure falls below 0.1 bara
   */
  public Result calculateFromWellhead(double wellheadPressureBara) {
    return march(wellheadPressureBara, true);
  }

  /**
   * Computes wellhead pressure for a given bottom-hole pressure.
   *
   * @param bottomholePressureBara bottom-hole pressure in bara
   * @return result including the full traverse
   * @throws IllegalStateException if the traverse pressure falls below 0.1 bara
   */
  public Result calculateFromBottomhole(double bottomholePressureBara) {
    return march(bottomholePressureBara, false);
  }

  /**
   * Marches along the survey.
   *
   * @param pStart start pressure in bara
   * @param fromWellhead true to march downward from the wellhead
   * @return result
   */
  private Result march(double pStart, boolean fromWellhead) {
    int nSeg = md.length - 1;
    Result r = new Result(nSeg);
    double p = pStart;
    r.pressureBara[fromWellhead ? 0 : nSeg] = p;
    double totalHydro = 0.0;
    double totalFric = 0.0;
    for (int k = 0; k < nSeg; k++) {
      int i = fromWellhead ? k : nSeg - 1 - k;
      double dL = md[i + 1] - md[i];
      double dZ = tvd[i + 1] - tvd[i];
      double theta = Math.asin(Math.min(1.0, Math.max(0.0, dZ / dL)));
      double tMid = temperatureAt(0.5 * (tvd[i] + tvd[i + 1]));
      double pEnd = p;
      Local s = null;
      for (int it = 0; it < 3; it++) {
        double pMid = 0.5 * (p + pEnd);
        s = evaluate(pMid, tMid, theta);
        double dp = s.gradientPaPerM * dL / 1.0e5;
        pEnd = fromWellhead ? p + dp : p - dp;
        if (pEnd < 0.1) {
          throw new IllegalStateException("Traverse pressure fell below 0.1 bara at measured depth " + md[i]
              + " m; reduce rate or raise the boundary pressure");
        }
      }
      totalHydro += s.hydrostaticPaPerM * dL / 1.0e5;
      totalFric += s.frictionPaPerM * dL / 1.0e5;
      r.store(i, s, theta, tMid);
      r.pressureBara[fromWellhead ? i + 1 : i] = pEnd;
      p = pEnd;
    }
    r.finish(md, tvd, totalHydro, totalFric);
    return r;
  }

  /**
   * Result of a drift-flux traverse.
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double[] pressureBara;
    private double[] mdM;
    private double[] tvdM;
    private final double[] temperatureK;
    private final double[] holdup;
    private final double[] voidFraction;
    private final double[] vsg;
    private final double[] vsl;
    private final double[] mixtureDensity;
    private final double[] turnerRatio;
    private final double[] erosionalRatio;
    private final FlowRegime[] regime;
    private final double[] inclinationDeg;
    private double hydrostaticDropBar;
    private double frictionDropBar;
    private final List<String> warnings = new ArrayList<String>();

    /**
     * Creates a result container.
     *
     * @param nSeg number of segments
     */
    Result(int nSeg) {
      pressureBara = new double[nSeg + 1];
      temperatureK = new double[nSeg];
      holdup = new double[nSeg];
      voidFraction = new double[nSeg];
      vsg = new double[nSeg];
      vsl = new double[nSeg];
      mixtureDensity = new double[nSeg];
      turnerRatio = new double[nSeg];
      erosionalRatio = new double[nSeg];
      regime = new FlowRegime[nSeg];
      inclinationDeg = new double[nSeg];
    }

    /**
     * Stores segment values.
     *
     * @param i segment index
     * @param s local state
     * @param theta inclination from horizontal in radians
     * @param tK segment temperature in K
     */
    void store(int i, Local s, double theta, double tK) {
      holdup[i] = s.liquidHoldup;
      voidFraction[i] = s.voidFraction;
      vsg[i] = s.vsg;
      vsl[i] = s.vsl;
      mixtureDensity[i] = s.rhoMixture;
      turnerRatio[i] = s.turnerRatio;
      erosionalRatio[i] = s.erosionalRatio;
      regime[i] = s.regime;
      inclinationDeg[i] = Math.toDegrees(theta);
      temperatureK[i] = tK;
    }

    /**
     * Completes the result.
     *
     * @param md measured depths
     * @param tvd true vertical depths
     * @param hydro hydrostatic drop in bar
     * @param fric friction drop in bar
     */
    void finish(double[] md, double[] tvd, double hydro, double fric) {
      this.mdM = md.clone();
      this.tvdM = tvd.clone();
      this.hydrostaticDropBar = hydro;
      this.frictionDropBar = fric;
      boolean low = false;
      for (int i = 0; i < regime.length; i++) {
        if (inclinationDeg[i] < 10.0) {
          low = true;
        }
      }
      if (low) {
        warnings.add("Section below 10 degrees from horizontal: drift-flux closures are not valid for stratified flow");
      }
      if (isLiquidLoadingRisk()) {
        warnings.add("Gas velocity below Turner critical velocity: liquid loading risk");
      }
      if (getMaxErosionalRatio() > 1.0) {
        warnings.add("Mixture velocity above API RP 14E erosional velocity");
      }
    }

    /**
     * Gets the pressure at each survey node (wellhead first).
     *
     * @return pressures in bara
     */
    public double[] getPressureBara() {
      return pressureBara.clone();
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
     * Gets the total pressure drop between bottom hole and wellhead.
     *
     * @return pressure drop in bar
     */
    public double getTotalPressureDropBar() {
      return getBottomholePressureBara() - getWellheadPressureBara();
    }

    /**
     * Gets the hydrostatic (gravity) component of the pressure drop.
     *
     * @return pressure drop in bar
     */
    public double getHydrostaticDropBar() {
      return hydrostaticDropBar;
    }

    /**
     * Gets the friction component of the pressure drop.
     *
     * @return pressure drop in bar
     */
    public double getFrictionDropBar() {
      return frictionDropBar;
    }

    /**
     * Gets measured depth of the survey nodes.
     *
     * @return depths in m
     */
    public double[] getMeasuredDepthM() {
      return mdM.clone();
    }

    /**
     * Gets true vertical depth of the survey nodes.
     *
     * @return depths in m
     */
    public double[] getTrueVerticalDepthM() {
      return tvdM.clone();
    }

    /**
     * Gets segment temperatures.
     *
     * @return temperatures in K
     */
    public double[] getSegmentTemperatureK() {
      return temperatureK.clone();
    }

    /**
     * Gets segment liquid holdup.
     *
     * @return in-situ liquid volume fraction
     */
    public double[] getLiquidHoldup() {
      return holdup.clone();
    }

    /**
     * Gets segment void fractions.
     *
     * @return in-situ gas volume fraction
     */
    public double[] getVoidFraction() {
      return voidFraction.clone();
    }

    /**
     * Gets segment superficial gas velocity.
     *
     * @return velocity in m/s
     */
    public double[] getSuperficialGasVelocity() {
      return vsg.clone();
    }

    /**
     * Gets segment superficial liquid velocity.
     *
     * @return velocity in m/s
     */
    public double[] getSuperficialLiquidVelocity() {
      return vsl.clone();
    }

    /**
     * Gets segment slip mixture density.
     *
     * @return density in kg/m3
     */
    public double[] getMixtureDensity() {
      return mixtureDensity.clone();
    }

    /**
     * Gets segment flow regimes.
     *
     * @return regimes
     */
    public FlowRegime[] getFlowRegime() {
      return regime.clone();
    }

    /**
     * Gets segment ratio of superficial gas velocity to Turner critical velocity (NaN for single-phase segments).
     *
     * @return ratios; below 1 indicates liquid loading
     */
    public double[] getTurnerRatio() {
      return turnerRatio.clone();
    }

    /**
     * Gets the minimum Turner ratio over two-phase segments.
     *
     * @return minimum ratio, or NaN if no two-phase segment exists
     */
    public double getMinTurnerRatio() {
      double min = Double.NaN;
      for (double v : turnerRatio) {
        if (!Double.isNaN(v) && (Double.isNaN(min) || v < min)) {
          min = v;
        }
      }
      return min;
    }

    /**
     * Checks for liquid loading risk.
     *
     * @return true if any two-phase segment has superficial gas velocity below the Turner velocity
     */
    public boolean isLiquidLoadingRisk() {
      double m = getMinTurnerRatio();
      return !Double.isNaN(m) && m < 1.0;
    }

    /**
     * Gets the maximum ratio of mixture velocity to API RP 14E erosional velocity.
     *
     * @return maximum ratio; above 1 exceeds the limit
     */
    public double getMaxErosionalRatio() {
      double max = 0.0;
      for (double v : erosionalRatio) {
        max = Math.max(max, v);
      }
      return max;
    }

    /**
     * Counts segments per flow regime.
     *
     * @return map from regime to segment count
     */
    public Map<FlowRegime, Integer> getRegimeCounts() {
      Map<FlowRegime, Integer> m = new EnumMap<FlowRegime, Integer>(FlowRegime.class);
      for (FlowRegime fr : regime) {
        Integer c = m.get(fr);
        m.put(fr, c == null ? 1 : c + 1);
      }
      return m;
    }

    /**
     * Gets warnings generated for the traverse.
     *
     * @return list of warnings
     */
    public List<String> getWarnings() {
      return new ArrayList<String>(warnings);
    }
  }
}
