package neqsim.process.equipment.expander;

import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.expander.TurboExpanderCompressorMechanicalDesign;
import neqsim.process.util.monitor.TurboExpanderCompressorResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * TurboExpanderCompressor models a coupled expander and compressor system with design and performance parameters,
 * polynomial curve fits for efficiency and head, and Newton-Raphson iteration for speed matching.
 * <p>
 * This class provides configuration for impeller, speed, efficiency, and curve fit parameters, and exposes all relevant
 * design and result values via getters/setters. The main run() method matches expander and compressor power using a
 * robust Newton-Raphson approach, updating all result fields and output streams.
 * </p>
 *
 * @author esol
 */
public class TurboExpanderCompressor extends Expander {
  private static final long serialVersionUID = 1001;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TurboExpanderCompressor.class);

  /** Coupled mechanical design for the combined expander-compressor unit. */
  private TurboExpanderCompressorMechanicalDesign tecMechanicalDesign;

  // --- Expander/Compressor Configuration ---
  /** Expander outlet pressure [bar abs]. */
  private double expanderOutPressure = 40.0;
  /** IGV opening (1.0 = 100% open, fraction of max area). */
  private double IGVopening = 1.0;
  /** Mechanical bearing loss power [W]. */
  private double bearingLossPower = 10.0;
  /** Matched expander speed [rpm]. */
  private double expanderSpeed = 1000.0;
  private double compressorSpeed = 1000.0;
  private double gearRatio = 1.0;

  // --- Process Streams ---
  StreamInterface compressorFeedStream = null;
  StreamInterface compressorOutletStream = null;
  StreamInterface expanderFeedStream = null;
  StreamInterface expanderOutletStream = null;

  // --- Design and Performance Parameters ---
  /** Impeller diameter [m]. */
  private double impellerDiameter = 0.424;
  /** Design speed [rpm]. */
  private double designSpeed = 6850.0;
  // /** Design isentropic efficiency (expander). */
  // private double designIsentropicEfficiency = 0.88;
  /** Design UC (velocity ratio, expander). */
  private double designUC = 0.7;
  /** UC ratio for expander (actual/ideal). */
  private double UCratioexpander = 0.7;
  /** UC ratio for compressor (actual/ideal). */
  private double UCratiocompressor = 0.7;
  /** QN ratio for expander (actual/ideal). */
  private double QNratioexpander = 0.7;
  /** QN ratio for compressor (actual/ideal). */
  private double QNratiocompressor = 0.7;
  /** Design Q/N (flow/speed ratio). */
  private double designQn = 0.03328;
  /** Design Q/N for the expander (flow/speed ratio). */
  private double designExpanderQn = 0.0;
  /** Actual Q/N (flow/speed ratio). */
  private double Qn = 0.03328;
  /** Maximum IGV area [mm^2] for the installed hardware. */
  private double maximumIGVArea = 1.637e4;
  /** Allowable IGV area increase factor (e.g. 1.14 for +14%). */
  private double igvAreaIncreaseFactor = 1.14;
  /** Flag indicating whether the enlarged IGV area is being used. */
  private boolean usingExpandedIGVArea = false;
  /** Current effective IGV area [mm^2]. */
  private double currentIGVArea = 0.0;
  /** Last calculated stage isentropic enthalpy drop [J/kg]. */
  private double lastStageEnthalpyDrop = 0.0;
  /** Compressor polytropic efficiency (design/actual). */
  private double compressorDesignPolytropicEfficiency = 0.81;
  private double compressorPolytropicEfficiency = 0.81;
  /** Compressor design polytropic head [kJ/kg]. */
  private double compressorDesignPolytropicHead = 20.47;
  /** Compressor actual polytropic head [kJ/kg]. */
  private double compressorPolytropicHead = 20.47;
  /** Expander isentropic efficiency (actual, result). */
  private double expanderIsentropicEfficiency = 1.0;
  private double expanderDesignIsentropicEfficiency = 1.0;
  /**
   * Flag indicating that the expander outlet temperature is specified. When {@code true} the base (design) isentropic
   * efficiency is back-calculated so the actual outlet temperature matches {@link #expanderOutTemperatureSpec}.
   */
  private boolean useOutTemperatureSpec = false;
  /** Target expander outlet temperature [K] used when {@link #useOutTemperatureSpec} is true. */
  private double expanderOutTemperatureSpec = 0.0;
  /** Expander shaft power [W]. */
  private double powerExpander = 0.0;
  /** Compressor shaft power [W]. */
  private double powerCompressor = 0.0;
  // --- Polynomial Curve Fit Parameters ---
  /** UC/efficiency curve fit parameter. */
  private double ucCurveA = 0.0;
  private double ucCurveH = 1.0;
  private double ucCurveK = 1.0;
  /** QN/efficiency curve fit parameter. */
  private double qnCurveA = 0.0;
  private double qnCurveH = 1.0;
  private double qnCurveK = 1.0;
  /** QN/head curve fit parameter. */
  private double qnHeadCurveA = 0.0;
  private double qnHeadCurveH = 1.0;
  private double qnHeadCurveK = 1.0;

  // --- Spline data for QN/head curve ---
  private double[] qnHeadCurveQnValues = null;
  private double[] qnHeadCurveHeadValues = null;

  // --- Spline data for QN/efficiency curve ---
  private double[] qnEffCurveQnValues = null;
  private double[] qnEffCurveEffValues = null;

  // --- P1: composition-aware 2-D expander performance map (optional) ---
  /** Optional Khader-style 2-D expander performance map. When set it overrides the UC parabola. */
  private ExpanderChartKhader expanderChart = null;

  // --- P2: IGV as a controllable degree of freedom ---
  /**
   * When {@code true} the supplied {@link #IGVopening} is treated as a fixed control input and the model no longer
   * recomputes the IGV opening from flow; an efficiency penalty curve is applied instead and the speed/power balance is
   * solved around the imposed IGV position.
   */
  private boolean igvControlMode = false;
  /** IGV positions (fraction of maximum area, 0..1) for the efficiency penalty curve. */
  private double[] igvPenaltyOpenings = null;
  /** Multiplicative efficiency penalty factors matching {@link #igvPenaltyOpenings} (0..1). */
  private double[] igvPenaltyFactors = null;

  // --- Nozzle swallowing capacity, JT bypass, shaft closure (all optional, defaults reproduce the legacy model) ---
  /** Lower bound of the IGV opening used by the capacity model (fraction). */
  private static final double MIN_IGV = 1.0e-3;
  /** When {@code true} the nozzle swallowing capacity m = k s sqrt(rho P) f(IGV) is evaluated in {@link #run(UUID)}. */
  private boolean nozzleCapacityEnabled = false;
  /** Nozzle capacity coefficient k [kg/s per sqrt(kg/m3 * Pa)] at fully open IGV. */
  private double nozzleCapacityCoefficient = 0.0;
  /** Exponent n of the power-law IGV flow function f(IGV) = IGV^n. */
  private double nozzleCapacityExponent = 1.0;
  /** Train scale factor s applied to the nozzle capacity. */
  private double nozzleCapacityScale = 1.0;
  /** Optional tabulated IGV openings of the flow function (null = power law). */
  private double[] nozzleIgvTable = null;
  /** Optional tabulated flow function values matching {@link #nozzleIgvTable}. */
  private double[] nozzleFlowFactorTable = null;
  /** When {@code true} flow above the nozzle capacity is routed through a JT valve. */
  private boolean jtBypassEnabled = false;
  /** Nozzle capacity at the current IGV [kg/s] (0 when the capacity model is off). */
  private double nozzleCapacity = 0.0;
  /** Nozzle capacity at fully open IGV [kg/s]. */
  private double nozzleCapacityFullOpen = 0.0;
  /** Feed flow divided by the capacity at the current IGV. */
  private double nozzleCapacityUtilisation = 0.0;
  /** Feed flow divided by the capacity at fully open IGV. */
  private double fullOpenCapacityUtilisation = 0.0;
  /** IGV opening required to pass the feed flow (above 1 means more than the fully open capacity). */
  private double requiredIgvOpening = 0.0;
  /** True when the expander is at its swallowing capacity (fully open in automatic IGV mode). */
  private boolean capacityLimited = false;
  /** Mass flow routed through the JT bypass valve [kg/s]. */
  private double jtBypassFlow = 0.0;
  /** Mass flow through the expander wheel [kg/s]. */
  private double expanderMassFlow = 0.0;
  /** Shaft closure factor k in k * P_expander = P_compressor + P_loss. */
  private double shaftClosureFactor = 1.0;
  /** Maximum allowed shaft speed [rpm] (also the upper clamp of the speed solver). */
  private double maximumSpeed = 9000.0;
  /** Minimum allowed shaft speed [rpm] (lower clamp of the speed solver). */
  private double minimumSpeed = 1000.0;
  /** Solver status of the speed matching: ok, speed_max or speed_min. */
  private String speedStatus = "ok";
  /** Mechanical (bearing) loss at the solved speed [W]. */
  private double bearingLossResult = 0.0;
  /** Shaft power residual closure * P_expander - P_compressor - P_loss [W]. */
  private double shaftPowerResidual = 0.0;
  /** Raw expander speed ratio U/C0 at the solved speed. */
  private double speedRatioUC0 = 0.0;
  /** Expander-wheel-only outlet temperature before mixing with the JT bypass stream [K]. */
  private double expanderWheelOutletTemperature = 0.0;
  /** When {@code true} the expander efficiency is eta = max(floor, etaMax - a (x - xOpt)^2) with x = U/C0. */
  private boolean expanderParabolaEnabled = false;
  private double expanderParabolaEtaMax = 0.0;
  private double expanderParabolaXopt = 0.0;
  private double expanderParabolaCurvature = 0.0;
  private double expanderParabolaFloor = 0.0;
  /** Compressor head polynomial H/(N/Nd)^2 = c2 phi^2 + c1 phi + c0 [kJ/kg], phi = Q * 60 / N (null = Q/N curve). */
  private double[] compressorHeadPolynomial = null;
  /** Compressor polytropic efficiency polynomial e2 phi^2 + e1 phi + e0 (null = Q/N curve). */
  private double[] compressorEfficiencyPolynomial = null;
  private double compressorEfficiencyScale = 1.0;
  private double compressorEfficiencyMin = 0.0;
  private double compressorEfficiencyMax = 1.0;

  /**
   * Construct a TurboExpanderCompressor with the specified name and inlet stream.
   *
   * @param name the name of the turbo expander compressor
   * @param inletStream the inlet stream for the expander
   */
  public TurboExpanderCompressor(String name, StreamInterface inletStream) {
    super(name, inletStream);
    expanderFeedStream = inletStream;
    expanderOutletStream = inletStream.clone(name + " exp outlet");
    compressorFeedStream = inletStream.clone(name + " comp feed");
    compressorOutletStream = inletStream.clone(name + " comp outlet");
    outStream = compressorOutletStream;
  }

  // --- Main Calculation ---
  /**
   * {@inheritDoc}
   *
   * <p>
   * Run the expander/compressor calculation, matching expander and compressor power using Newton-Raphson iteration.
   * Updates all result fields and output streams.
   * </p>
   */
  @Override
  public void run(UUID id) {
    double N = designSpeed; // initial guess for speed [rpm]
    double eta_p = 0.0;
    double W_compressor = 0.0;
    double W_expander = 0.0;
    double W_bearing = 0.0;
    double D = impellerDiameter;
    double eta_s_design = expanderDesignIsentropicEfficiency;
    double Hp_design = compressorDesignPolytropicHead;
    double eta_p_design = compressorDesignPolytropicEfficiency;
    double N_design = designSpeed;
    double m1 = expanderFeedStream.getFlowRate("kg/sec");
    double Q_comp = 0.0;
    double Q_exp = 0.0;
    double m_comp = 0.0;
    double eta_s = 0.0;
    double Hp = 0.0;
    double CF_eff_comp = 1.0;
    double CF_head_comp = 1.0;
    // Newton-Raphson method for speed matching
    int maxIter = 50;
    int minIter = 3;
    double dN = 10.0;

    int iter = 0;
    double Hp2 = 0.0;
    double eta_p2 = 0.0;
    double eta_s2 = 0.0;
    double uc = 0.0;
    double uc2 = 0.0;
    double qn_ratio = 0.0;
    double qn_ratio_exp = 1.0;
    double qn_ratio2 = 0.0;
    double qn_ratio_exp2 = 1.0;
    boolean applyExpanderQnCorrection = designExpanderQn > 0.0;
    double designQnExp = designExpanderQn;
    double h_s = 0.0;
    double CF_total_converged = 0.0;
    // When the outlet temperature is specified, compute the actual isentropic efficiency that
    // lands the fluid at the target outlet temperature. This value is held fixed while the speed
    // is matched, and the base (design) efficiency is back-calculated after convergence.
    double eta_s_required = -1.0;
    if (useOutTemperatureSpec) {
      eta_s_required = calcRequiredExpanderEfficiencyForOutletT(expanderOutTemperatureSpec);
    }

    // --- Loop-invariant expander thermodynamics (evaluated once) ---
    // The isentropic enthalpy drop h_s, the spouting velocity C and the expander/compressor
    // flow rates depend only on the feed streams and the fixed expander outlet pressure - not
    // on the shaft speed N that the Newton loop below solves for. Cloning and re-flashing the
    // feed on every iteration was therefore pure repeated work and dominated the run time of
    // this unit. Hoisting it out is mathematically identical.
    SystemInterface fluid2 = expanderFeedStream.getThermoSystem().clone();
    fluid2.initThermoProperties();
    double s1 = fluid2.getEntropy("kJ/kgK");
    double h_in = fluid2.getEnthalpy("kJ/kg");
    fluid2.setPressure(expanderOutPressure, "bara");
    ThermodynamicOperations flash = new ThermodynamicOperations(fluid2);
    flash.PSflash(s1, "kJ/kgK");
    fluid2.init(3);
    double h_out = fluid2.getEnthalpy("kJ/kg");
    h_s = (h_in - h_out) * 1000; // J/kg
    // sqrt() of a negative drop yields NaN, and NaN then passes every Math.max clamp and every
    // speed bound below, so the whole unit silently reports NaN efficiency, power and speed.
    if (!(h_s > 0.0)) {
      throw new RuntimeException(new neqsim.util.exception.InvalidOutputException(this, "run", "isentropicEnthalpyDrop",
          "is " + h_s + " J/kg. The expander outlet pressure (" + expanderOutPressure
              + " bara) must be below the inlet pressure (" + expanderFeedStream.getPressure("bara")
              + " bara) and the feed must be a valid single- or multi-phase state."));
    }
    final double C = Math.sqrt(2.0 * h_s);
    Q_exp = expanderFeedStream.getFluid().getFlowRate("m3/sec");
    m_comp = compressorFeedStream.getFluid().getFlowRate("kg/sec");
    Q_comp = compressorFeedStream.getFluid().getFlowRate("m3/sec");

    // Nozzle swallowing capacity and JT bypass (inactive unless enabled): the wheel only sees m_exp.
    double m_exp = m1;
    double m_jt = 0.0;
    nozzleCapacity = 0.0;
    nozzleCapacityFullOpen = 0.0;
    nozzleCapacityUtilisation = 0.0;
    fullOpenCapacityUtilisation = 0.0;
    requiredIgvOpening = 0.0;
    capacityLimited = false;
    if (nozzleCapacityEnabled) {
      double rhoIn = expanderFeedStream.getFluid().getDensity("kg/m3");
      double pInBara = expanderFeedStream.getPressure("bara");
      nozzleCapacityFullOpen = calcNozzleCapacity(1.0, rhoIn, pInBara);
      requiredIgvOpening = calcRequiredIgvOpening(m1, rhoIn, pInBara);
      double igvUsed = igvControlMode ? Math.max(MIN_IGV, Math.min(1.0, IGVopening))
          : Math.min(1.0, Math.max(MIN_IGV, requiredIgvOpening));
      nozzleCapacity = calcNozzleCapacity(igvUsed, rhoIn, pInBara);
      nozzleCapacityUtilisation = nozzleCapacity > 0.0 ? m1 / nozzleCapacity : 0.0;
      fullOpenCapacityUtilisation = nozzleCapacityFullOpen > 0.0 ? m1 / nozzleCapacityFullOpen : 0.0;
      capacityLimited = igvControlMode ? nozzleCapacityUtilisation > 1.0 + 1e-9 : (requiredIgvOpening >= 1.0 - 1e-9);
      if (jtBypassEnabled && m1 > nozzleCapacity) {
        m_exp = nozzleCapacity;
        m_jt = m1 - nozzleCapacity;
        if (m1 > 0.0) {
          Q_exp = Q_exp * m_exp / m1;
        }
      }
      IGVopening = igvUsed;
      currentIGVArea = IGVopening * maximumIGVArea;
      usingExpandedIGVArea = false;
    }
    jtBypassFlow = m_jt;
    expanderMassFlow = m_exp;
    double ucRawLast = 0.0;
    double W_bearingLast = 0.0;
    String speedStatusLocal = "ok";

    do {
      double U = Math.PI * D * N / 60.0;
      uc = U / C / designUC;
      double ucRaw = U / C;
      double CF_qn_exp = 1.0;
      if (applyExpanderQnCorrection && designQnExp > 0.0 && N > 0.0) {
        qn_ratio_exp = (Q_exp * 60.0 / N) / designQnExp;
        CF_qn_exp = getEfficiencyFromQN(qn_ratio_exp);
        if (CF_qn_exp < 0.0) {
          CF_qn_exp = 0.0;
        }
      } else {
        qn_ratio_exp = 1.0;
      }
      eta_s = computeExpanderEfficiency(ucRaw, uc, IGVopening, CF_qn_exp);
      double CF_total = eta_s_design > 1e-9 ? eta_s / eta_s_design : 0.0;
      if (CF_total < 0.0) {
        CF_total = 0.0;
      }
      CF_total_converged = CF_total;
      if (useOutTemperatureSpec && eta_s_required > 0.0) {
        eta_s = eta_s_required;
      }
      W_expander = m_exp * h_s * eta_s;
      qn_ratio = (Q_comp * 60.0 / N) / designQn;
      CF_eff_comp = getEfficiencyFromQN(qn_ratio);
      CF_eff_comp = Math.max(CF_eff_comp, 1e-6);
      CF_head_comp = getHeadFromQN(qn_ratio);
      CF_head_comp = Math.max(CF_head_comp, 1e-6);
      Hp = Hp_design * (N / N_design) * (N / N_design) * CF_head_comp;
      eta_p = CF_eff_comp * eta_p_design;
      if (compressorHeadPolynomial != null) {
        Hp = polynomialHead(N, Q_comp, N_design);
      }
      if (compressorEfficiencyPolynomial != null) {
        eta_p = polynomialEfficiency(Q_comp, N);
      }
      W_compressor = m_comp * Hp / eta_p * 1000.0;
      W_bearing = bearingLossPower * (N / N_design) * (N / N_design);

      W_bearingLast = W_bearing;
      ucRawLast = ucRaw;
      double fN = shaftClosureFactor * W_expander - (W_compressor + W_bearing);
      // Finite difference for derivative
      double N2 = N + dN;
      double U2 = Math.PI * D * N2 / 60.0;
      uc2 = U2 / C / designUC;
      double ucRaw2 = U2 / C;
      double CF_qn_exp2 = 1.0;
      if (applyExpanderQnCorrection && designQnExp > 0.0 && N2 > 0.0) {
        qn_ratio_exp2 = (Q_exp * 60.0 / N2) / designQnExp;
        CF_qn_exp2 = getEfficiencyFromQN(qn_ratio_exp2);
        if (CF_qn_exp2 < 0.0) {
          CF_qn_exp2 = 0.0;
        }
      } else {
        qn_ratio_exp2 = 1.0;
      }
      eta_s2 = computeExpanderEfficiency(ucRaw2, uc2, IGVopening, CF_qn_exp2);
      if (useOutTemperatureSpec && eta_s_required > 0.0) {
        eta_s2 = eta_s_required;
      }
      double W_expander2 = m_exp * h_s * eta_s2;
      qn_ratio2 = (Q_comp * 60.0 / N2) / designQn;
      double CF_eff_comp2 = getEfficiencyFromQN(qn_ratio2);
      CF_eff_comp2 = Math.max(CF_eff_comp2, 1e-6);
      // Head must be re-read at the perturbed speed, otherwise the finite difference below omits
      // the head-curve slope and the Newton step is taken on an inconsistent derivative.
      double CF_head_comp2 = getHeadFromQN(qn_ratio2);
      CF_head_comp2 = Math.max(CF_head_comp2, 1e-6);
      Hp2 = Hp_design * (N2 / N_design) * (N2 / N_design) * CF_head_comp2;
      eta_p2 = CF_eff_comp2 * eta_p_design;
      if (compressorHeadPolynomial != null) {
        Hp2 = polynomialHead(N2, Q_comp, N_design);
      }
      if (compressorEfficiencyPolynomial != null) {
        eta_p2 = polynomialEfficiency(Q_comp, N2);
      }
      double W_compressor2 = m_comp * Hp2 / eta_p2 * 1000.0;
      double W_bearing2 = bearingLossPower * (N2 / N_design) * (N2 / N_design);
      double fN2 = shaftClosureFactor * W_expander2 - (W_compressor2 + W_bearing2);
      double df_dN = (fN2 - fN) / dN;
      if (Math.abs(df_dN) < 1e-8) {
        dN += 10.0;
        N += 10.0;
      } else {
        N = N - (1.0 + iter) / (iter + 5) * fN / df_dN;
      }
      if (N > maximumSpeed) {
        N = maximumSpeed;
        speedStatusLocal = "speed_max";
        break;
      }
      if (N < minimumSpeed) {
        N = minimumSpeed;
        speedStatusLocal = "speed_min";
        break;
      }
      // System.out.println("speed: " + N + " iter: " + iter);
      iter++;
    } while (Math.abs(shaftClosureFactor * W_expander - (W_compressor + W_bearing)) * 100 > 1e-3 && iter < maxIter
        || iter < minIter);
    if (iter >= maxIter) {
      logger.warn("TurboExpanderCompressor {} did not converge in {} speed-matching iterations.", getName(), maxIter);
    }
    // System.out.println("speed: " + N + " iter: " + iter);
    expanderIsentropicEfficiency = eta_s;
    // When the outlet temperature is specified, back-calculate the base (design) isentropic
    // efficiency so that eta_s_design * CF_total reproduces the actual efficiency required to hit
    // the target outlet temperature.
    if (useOutTemperatureSpec && eta_s_required > 0.0 && CF_total_converged > 1e-9) {
      expanderDesignIsentropicEfficiency = eta_s_required / CF_total_converged;
    }
    compressorPolytropicHead = Hp;
    compressorPolytropicEfficiency = eta_p;
    expanderSpeed = N;
    setSpeed(N);
    setPower(W_expander);
    powerExpander = W_expander;
    powerCompressor = W_compressor;
    setUCratioexpander(uc);
    setQNratioexpander(qn_ratio_exp);
    if (nozzleCapacityEnabled) {
      lastStageEnthalpyDrop = h_s;
    } else if (!igvControlMode) {
      updateIGVState(h_s, m1, Q_exp);
    } else {
      lastStageEnthalpyDrop = h_s;
    }
    setQNratiocompressor(qn_ratio);
    setQn(N / 60.0 * Q_comp / designQn);
    speedStatus = speedStatusLocal;
    bearingLossResult = W_bearingLast;
    shaftPowerResidual = shaftClosureFactor * W_expander - (W_compressor + W_bearingLast);
    speedRatioUC0 = ucRawLast;

    if (m_jt > 0.0 && m_exp > 0.0) {
      runWithJtBypass(eta_s, m_exp, m_jt, h_in);
    } else {
      Expander expander = new Expander("tempExpander", expanderFeedStream);
      expander.setOutletPressure(getExpanderOutPressure());
      // The coupled turbo-expander model may require an actual stage efficiency above 1.0 to
      // reproduce a requested outlet temperature after the map-correction back-calculation.
      // Replay that internal efficiency directly on the temporary expander instead of using the
      // compressor-bound setter, which clamps at 1.0 for compressor robustness.
      expander.isentropicEfficiency = eta_s;
      expander.run();
      expanderOutletStream.setFluid(expander.getOutletStream().getFluid());
      expanderWheelOutletTemperature = expanderOutletStream.getTemperature();
    }

    Compressor tempCompressor = new Compressor("tempCompressor", compressorFeedStream);
    tempCompressor.setUsePolytropicCalc(true);
    tempCompressor.setPolytropicEfficiency(eta_p);
    tempCompressor.setPower(W_compressor);
    tempCompressor.setCalcPressureOut(true);
    tempCompressor.run();
    compressorOutletStream.setFluid(tempCompressor.getOutletStream().getFluid());
    setCalculationIdentifier(id);
  }

  /**
   * Calculate the actual isentropic efficiency required for the expander to reach a given outlet temperature at the
   * configured {@link #expanderOutPressure}.
   *
   * <p>
   * The efficiency is defined as the ratio of the actual enthalpy drop (from inlet to the target outlet temperature at
   * the outlet pressure) to the isentropic enthalpy drop (from inlet to the outlet pressure at constant entropy):
   * </p>
   *
   * <pre>
   * eta_s = (h_in - h_out_target) / (h_in - h_out_isentropic)
   * </pre>
   *
   * @param targetTemperature the desired expander outlet temperature [K]
   * @return the required actual isentropic efficiency (dimensionless), or {@code -1.0} if the isentropic enthalpy drop
   * is non-positive
   */
  private double calcRequiredExpanderEfficiencyForOutletT(double targetTemperature) {
    SystemInterface fluid = expanderFeedStream.getThermoSystem().clone();
    fluid.initThermoProperties();
    double s1 = fluid.getEntropy("kJ/kgK");
    double h_in = fluid.getEnthalpy("kJ/kg");
    fluid.setPressure(expanderOutPressure, "bara");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.PSflash(s1, "kJ/kgK");
    fluid.init(3);
    double h_out_isentropic = fluid.getEnthalpy("kJ/kg");
    double isentropicDrop = h_in - h_out_isentropic;
    if (isentropicDrop <= 0.0) {
      return -1.0;
    }
    fluid.setTemperature(targetTemperature);
    fluid.setPressure(expanderOutPressure, "bara");
    ops.TPflash();
    fluid.init(3);
    double h_out_target = fluid.getEnthalpy("kJ/kg");
    double actualDrop = h_in - h_out_target;
    return actualDrop / isentropicDrop;
  }

  /**
   * Run the expander wheel on the part of the feed that fits through the nozzle and mix its outlet with the part that
   * is throttled through the JT bypass valve (isenthalpic) at the expander outlet pressure.
   *
   * @param etaS actual expander isentropic efficiency (0..1)
   * @param mExp mass flow through the wheel [kg/s]
   * @param mJt mass flow through the JT bypass valve [kg/s]
   * @param hInKjPerKg feed specific enthalpy [kJ/kg]
   */
  private void runWithJtBypass(double etaS, double mExp, double mJt, double hInKjPerKg) {
    SystemInterface feedFluid = expanderFeedStream.getThermoSystem().clone();
    Stream wheelFeed = new Stream(getName() + " wheel feed", feedFluid.clone());
    wheelFeed.setFlowRate(mExp, "kg/sec");
    wheelFeed.run();
    Expander wheel = new Expander("tempExpander", wheelFeed);
    wheel.setOutletPressure(getExpanderOutPressure());
    wheel.isentropicEfficiency = etaS;
    wheel.run();
    SystemInterface wheelOut = wheel.getOutletStream().getThermoSystem();
    expanderWheelOutletTemperature = wheelOut.getTemperature();
    double hTotalJ = wheelOut.getEnthalpy() + hInKjPerKg * 1000.0 * mJt;
    SystemInterface mix = feedFluid.clone();
    mix.setPressure(getExpanderOutPressure(), "bara");
    mix.setTemperature(wheelOut.getTemperature());
    new ThermodynamicOperations(mix).PHflash(hTotalJ);
    mix.initProperties();
    expanderOutletStream.setFluid(mix);
  }

  /**
   * Evaluate the IGV flow function f(IGV) of the nozzle capacity, normalised to 1.0 at fully open IGV. A power law
   * IGV^n is used unless a table has been supplied with {@link #setNozzleCapacityModel(double, double[], double[])}.
   *
   * @param igv IGV opening (fraction 0..1)
   * @return the dimensionless flow function
   */
  public double calcNozzleFlowFunction(double igv) {
    double x = Math.max(MIN_IGV, Math.min(1.0, igv));
    if (nozzleIgvTable == null) {
      return Math.pow(x, nozzleCapacityExponent);
    }
    double full = interpolateTable(nozzleIgvTable, nozzleFlowFactorTable, 1.0);
    return full > 0.0 ? interpolateTable(nozzleIgvTable, nozzleFlowFactorTable, x) / full : 0.0;
  }

  /**
   * Nozzle swallowing capacity m = k s sqrt(rho P) f(IGV) for the supplied inlet state.
   *
   * @param igv IGV opening (fraction 0..1)
   * @param inletDensity expander inlet density [kg/m3]
   * @param inletPressureBara expander inlet pressure [bara]
   * @return the swallowing capacity [kg/s]
   */
  public double calcNozzleCapacity(double igv, double inletDensity, double inletPressureBara) {
    double rhoP = Math.max(inletDensity, 0.0) * Math.max(inletPressureBara, 0.0) * 1.0e5;
    return nozzleCapacityCoefficient * nozzleCapacityScale * Math.sqrt(rhoP) * calcNozzleFlowFunction(igv);
  }

  /**
   * IGV opening needed to pass a mass flow. A value above 1.0 means the flow exceeds the capacity of the fully open
   * nozzle.
   *
   * @param massFlow gas mass flow [kg/s]
   * @param inletDensity expander inlet density [kg/m3]
   * @param inletPressureBara expander inlet pressure [bara]
   * @return the required IGV opening (fraction, not clipped at 1.0)
   */
  public double calcRequiredIgvOpening(double massFlow, double inletDensity, double inletPressureBara) {
    double full = calcNozzleCapacity(1.0, inletDensity, inletPressureBara);
    if (!(full > 0.0)) {
      return 0.0;
    }
    double f = massFlow / full;
    if (nozzleIgvTable == null) {
      return Math.pow(f, 1.0 / nozzleCapacityExponent);
    }
    if (f >= 1.0) {
      return f;
    }
    double lo = MIN_IGV;
    double hi = 1.0;
    for (int i = 0; i < 60; i++) {
      double mid = 0.5 * (lo + hi);
      if (calcNozzleFlowFunction(mid) < f) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Linear interpolation with edge clamping in an ascending table.
   *
   * @param x ascending abscissa values
   * @param y ordinate values
   * @param v query value
   * @return interpolated ordinate
   */
  private static double interpolateTable(double[] x, double[] y, double v) {
    int n = x.length;
    if (v <= x[0]) {
      return y[0];
    }
    if (v >= x[n - 1]) {
      return y[n - 1];
    }
    for (int i = 0; i < n - 1; i++) {
      if (v >= x[i] && v <= x[i + 1]) {
        double t = (v - x[i]) / (x[i + 1] - x[i]);
        return y[i] + t * (y[i + 1] - y[i]);
      }
    }
    return y[n - 1];
  }

  /**
   * Compressor polytropic head from the polynomial map: H = (N/Nd)^2 (c2 phi^2 + c1 phi + c0) with phi = Q 60 / N.
   *
   * @param speed shaft speed [rpm]
   * @param volumeFlow compressor suction volume flow [m3/s]
   * @param speedDesign design speed [rpm]
   * @return polytropic head [kJ/kg]
   */
  private double polynomialHead(double speed, double volumeFlow, double speedDesign) {
    double phi = volumeFlow * 60.0 / speed;
    double[] c = compressorHeadPolynomial;
    return (c[0] * phi * phi + c[1] * phi + c[2]) * (speed / speedDesign) * (speed / speedDesign);
  }

  /**
   * Compressor polytropic efficiency from the polynomial map, scaled and clipped to the configured limits.
   *
   * @param volumeFlow compressor suction volume flow [m3/s]
   * @param speed shaft speed [rpm]
   * @return polytropic efficiency (0..1)
   */
  private double polynomialEfficiency(double volumeFlow, double speed) {
    double phi = volumeFlow * 60.0 / speed;
    double[] e = compressorEfficiencyPolynomial;
    double eta = (e[0] * phi * phi + e[1] * phi + e[2]) * compressorEfficiencyScale;
    return Math.min(Math.max(eta, compressorEfficiencyMin), compressorEfficiencyMax);
  }

  // --- Nozzle capacity / JT bypass / shaft closure configuration and results ---
  /**
   * Enable the nozzle swallowing capacity model m = k sqrt(rho P) IGV^n (k in kg/s per sqrt(kg/m3 * Pa), P in Pa). In
   * automatic IGV mode the IGV opens as far as needed; in {@link #setIgvControlMode(boolean) control mode} the supplied
   * IGV is held.
   *
   * @param coefficient capacity coefficient k at fully open IGV
   * @param exponent power-law exponent n of the IGV flow function
   */
  public void setNozzleCapacityModel(double coefficient, double exponent) {
    this.nozzleCapacityCoefficient = coefficient;
    this.nozzleCapacityExponent = exponent > 0.0 ? exponent : 1.0;
    this.nozzleIgvTable = null;
    this.nozzleFlowFactorTable = null;
    this.nozzleCapacityEnabled = coefficient > 0.0;
  }

  /**
   * Enable the nozzle swallowing capacity model with a fitted IGV flow function table. The table is normalised to the
   * value at the largest opening used (IGV = 1 is clamped to the last point).
   *
   * @param coefficient capacity coefficient k at fully open IGV
   * @param igvOpenings IGV openings (fraction), any order
   * @param flowFactors flow function values at those openings
   */
  public void setNozzleCapacityModel(double coefficient, double[] igvOpenings, double[] flowFactors) {
    if (igvOpenings == null || flowFactors == null || igvOpenings.length != flowFactors.length
        || igvOpenings.length < 2) {
      throw new IllegalArgumentException("IGV flow table needs at least two matching points");
    }
    int n = igvOpenings.length;
    double[][] pairs = new double[n][2];
    for (int i = 0; i < n; i++) {
      pairs[i][0] = igvOpenings[i];
      pairs[i][1] = flowFactors[i];
    }
    Arrays.sort(pairs, new java.util.Comparator<double[]>() {
      @Override
      public int compare(double[] a, double[] b) {
        return Double.compare(a[0], b[0]);
      }
    });
    nozzleIgvTable = new double[n];
    nozzleFlowFactorTable = new double[n];
    for (int i = 0; i < n; i++) {
      nozzleIgvTable[i] = pairs[i][0];
      nozzleFlowFactorTable[i] = pairs[i][1];
    }
    this.nozzleCapacityCoefficient = coefficient;
    this.nozzleCapacityEnabled = coefficient > 0.0;
  }

  /**
   * Set the train scale factor s of the nozzle capacity (for example 1.088 on train B).
   *
   * @param scale capacity scale factor
   */
  public void setNozzleCapacityScale(double scale) {
    this.nozzleCapacityScale = scale;
  }

  /**
   * Check whether the nozzle capacity model is active.
   *
   * @return {@code true} if the capacity model is enabled
   */
  public boolean isNozzleCapacityEnabled() {
    return nozzleCapacityEnabled;
  }

  /**
   * Switch the nozzle capacity model on or off without losing the parameters.
   *
   * @param enabled {@code true} to evaluate the capacity in run()
   */
  public void setNozzleCapacityEnabled(boolean enabled) {
    this.nozzleCapacityEnabled = enabled && nozzleCapacityCoefficient > 0.0;
  }

  /**
   * Route flow above the nozzle capacity through a JT valve instead of forcing it through the wheel. Needs the nozzle
   * capacity model.
   *
   * @param enabled {@code true} to bypass the surplus
   */
  public void setJtBypassEnabled(boolean enabled) {
    this.jtBypassEnabled = enabled;
  }

  /**
   * Check whether the JT bypass option is on.
   *
   * @return {@code true} if surplus flow is routed through the JT valve
   */
  public boolean isJtBypassEnabled() {
    return jtBypassEnabled;
  }

  /**
   * Nozzle swallowing capacity at the current IGV opening from the last run.
   *
   * @return capacity [kg/s], 0 when the capacity model is off
   */
  public double getNozzleCapacity() {
    return nozzleCapacity;
  }

  /**
   * Nozzle swallowing capacity at fully open IGV from the last run.
   *
   * @return capacity [kg/s]
   */
  public double getNozzleCapacityFullOpen() {
    return nozzleCapacityFullOpen;
  }

  /**
   * Feed flow divided by the capacity at the current IGV opening.
   *
   * @return utilisation (above 1 means the flow exceeds the capacity at this IGV)
   */
  public double getNozzleCapacityUtilisation() {
    return nozzleCapacityUtilisation;
  }

  /**
   * Feed flow divided by the capacity of the fully open nozzle.
   *
   * @return utilisation (above 1 means more gas than the fully open nozzle can swallow)
   */
  public double getFullOpenCapacityUtilisation() {
    return fullOpenCapacityUtilisation;
  }

  /**
   * IGV opening required for the feed flow in the last run.
   *
   * @return required IGV opening (fraction, above 1 means over capacity)
   */
  public double getRequiredIgvOpening() {
    return requiredIgvOpening;
  }

  /**
   * Check whether the expander is at its swallowing capacity (IGV fully open in automatic mode, flow above the capacity
   * at the held IGV in control mode).
   *
   * @return {@code true} if capacity limited
   */
  public boolean isCapacityLimited() {
    return capacityLimited;
  }

  /**
   * Mass flow routed through the JT bypass valve in the last run.
   *
   * @return bypass flow [kg/s]
   */
  public double getJtBypassFlow() {
    return jtBypassFlow;
  }

  /**
   * JT bypass flow as a fraction of the expander feed flow.
   *
   * @return bypass fraction (0..1)
   */
  public double getJtBypassFraction() {
    double total = jtBypassFlow + expanderMassFlow;
    return total > 0.0 ? jtBypassFlow / total : 0.0;
  }

  /**
   * Mass flow through the expander wheel in the last run.
   *
   * @return wheel flow [kg/s]
   */
  public double getExpanderMassFlow() {
    return expanderMassFlow;
  }

  /**
   * Expander wheel outlet temperature before mixing with the JT bypass stream.
   *
   * @param unit temperature unit ("K", "C", "F" or "R")
   * @return outlet temperature in the requested unit
   */
  public double getExpanderWheelOutletTemperature(String unit) {
    return new neqsim.util.unit.TemperatureUnit(expanderWheelOutletTemperature, "K").getValue(unit);
  }

  /**
   * Set the shaft closure factor k of the power balance k * P_expander = P_compressor + P_loss. A value above 1 absorbs
   * shaft work that is not modelled explicitly (seal gas, heat soak, mass flow bias).
   *
   * @param factor shaft closure factor (default 1.0)
   */
  public void setShaftClosureFactor(double factor) {
    this.shaftClosureFactor = factor;
  }

  /**
   * Get the shaft closure factor.
   *
   * @return shaft closure factor
   */
  public double getShaftClosureFactor() {
    return shaftClosureFactor;
  }

  /**
   * Set the mechanical (bearing) loss at design speed; the loss scales with (N / design speed)^2.
   *
   * @param bearingLossPower bearing loss at design speed [W]
   */
  public void setBearingLossPower(double bearingLossPower) {
    this.bearingLossPower = bearingLossPower;
  }

  /**
   * Set the speed window of the shaft speed solver.
   *
   * @param minimumSpeed lower speed clamp [rpm]
   * @param maximumSpeed upper speed clamp and reference of the speed utilisation [rpm]
   */
  public void setSpeedLimits(double minimumSpeed, double maximumSpeed) {
    this.minimumSpeed = minimumSpeed;
    this.maximumSpeed = maximumSpeed;
  }

  /**
   * Get the maximum shaft speed.
   *
   * @return maximum speed [rpm]
   */
  public double getMaximumSpeed() {
    return maximumSpeed;
  }

  /**
   * Get the minimum shaft speed.
   *
   * @return minimum speed [rpm]
   */
  public double getMinimumSpeed() {
    return minimumSpeed;
  }

  /**
   * Solved shaft speed divided by the maximum speed.
   *
   * @return speed utilisation (fraction)
   */
  public double getSpeedUtilisation() {
    return maximumSpeed > 0.0 ? expanderSpeed / maximumSpeed : 0.0;
  }

  /**
   * Status of the speed solver in the last run.
   *
   * @return "ok", "speed_max" or "speed_min"
   */
  public String getSpeedStatus() {
    return speedStatus;
  }

  /**
   * Mechanical (bearing) loss at the solved speed.
   *
   * @return loss [W]
   */
  public double getBearingLoss() {
    return bearingLossResult;
  }

  /**
   * Shaft power residual closure * P_expander - P_compressor - P_loss of the last run.
   *
   * @return residual [W]
   */
  public double getShaftPowerResidual() {
    return shaftPowerResidual;
  }

  /**
   * Raw expander speed ratio U/C0 (tip speed over spouting velocity) at the solved speed.
   *
   * @return U/C0
   */
  public double getSpeedRatioUC0() {
    return speedRatioUC0;
  }

  /**
   * Use an absolute expander efficiency map eta = max(floor, etaMax - a (x - xOpt)^2) of the speed ratio x = U/C0
   * instead of the design-efficiency scaled UC parabola. A 2-D chart set with
   * {@link #setExpanderChart(ExpanderChartKhader)} still takes precedence.
   *
   * @param etaMax peak isentropic efficiency
   * @param xOpt speed ratio U/C0 of the peak
   * @param curvature curvature a of the parabola
   * @param floor lower efficiency bound
   */
  public void setExpanderEfficiencyParabola(double etaMax, double xOpt, double curvature, double floor) {
    this.expanderParabolaEnabled = true;
    this.expanderParabolaEtaMax = etaMax;
    this.expanderParabolaXopt = xOpt;
    this.expanderParabolaCurvature = curvature;
    this.expanderParabolaFloor = floor;
  }

  /**
   * Use a polynomial recompressor head map H = (N/Nd)^2 (c2 phi^2 + c1 phi + c0) in kJ/kg with phi = Q 60 / N (Q in
   * m3/s) instead of the Q/N head curve.
   *
   * @param c2 quadratic coefficient
   * @param c1 linear coefficient
   * @param c0 constant coefficient
   */
  public void setCompressorHeadPolynomial(double c2, double c1, double c0) {
    this.compressorHeadPolynomial = new double[] {c2, c1, c0};
  }

  /**
   * Use a polynomial recompressor polytropic efficiency map eta = scale (e2 phi^2 + e1 phi + e0) clipped to [min, max]
   * instead of the Q/N efficiency curve.
   *
   * @param e2 quadratic coefficient
   * @param e1 linear coefficient
   * @param e0 constant coefficient
   * @param scale train scale of the map
   * @param min lower efficiency bound
   * @param max upper efficiency bound
   */
  public void setCompressorEfficiencyPolynomial(double e2, double e1, double e0, double scale, double min, double max) {
    this.compressorEfficiencyPolynomial = new double[] {e2, e1, e0};
    this.compressorEfficiencyScale = scale;
    this.compressorEfficiencyMin = min;
    this.compressorEfficiencyMax = max;
  }

  /**
   * Copy the performance map and capacity configuration of another unit (design data, curves, polynomials, nozzle
   * model, speed window, closure factor) so that a second train can start from the same machine and then override
   * train-specific scales.
   *
   * @param other the unit to copy the maps from
   */
  public void copyPerformanceMapFrom(TurboExpanderCompressor other) {
    impellerDiameter = other.impellerDiameter;
    designSpeed = other.designSpeed;
    designUC = other.designUC;
    designQn = other.designQn;
    designExpanderQn = other.designExpanderQn;
    maximumIGVArea = other.maximumIGVArea;
    compressorDesignPolytropicEfficiency = other.compressorDesignPolytropicEfficiency;
    compressorDesignPolytropicHead = other.compressorDesignPolytropicHead;
    expanderDesignIsentropicEfficiency = other.expanderDesignIsentropicEfficiency;
    bearingLossPower = other.bearingLossPower;
    ucCurveA = other.ucCurveA;
    ucCurveH = other.ucCurveH;
    ucCurveK = other.ucCurveK;
    qnEffCurveQnValues = copyOrNull(other.qnEffCurveQnValues);
    qnEffCurveEffValues = copyOrNull(other.qnEffCurveEffValues);
    qnHeadCurveQnValues = copyOrNull(other.qnHeadCurveQnValues);
    qnHeadCurveHeadValues = copyOrNull(other.qnHeadCurveHeadValues);
    igvPenaltyOpenings = copyOrNull(other.igvPenaltyOpenings);
    igvPenaltyFactors = copyOrNull(other.igvPenaltyFactors);
    expanderChart = other.expanderChart;
    nozzleCapacityEnabled = other.nozzleCapacityEnabled;
    nozzleCapacityCoefficient = other.nozzleCapacityCoefficient;
    nozzleCapacityExponent = other.nozzleCapacityExponent;
    nozzleCapacityScale = other.nozzleCapacityScale;
    nozzleIgvTable = copyOrNull(other.nozzleIgvTable);
    nozzleFlowFactorTable = copyOrNull(other.nozzleFlowFactorTable);
    jtBypassEnabled = other.jtBypassEnabled;
    shaftClosureFactor = other.shaftClosureFactor;
    maximumSpeed = other.maximumSpeed;
    minimumSpeed = other.minimumSpeed;
    expanderParabolaEnabled = other.expanderParabolaEnabled;
    expanderParabolaEtaMax = other.expanderParabolaEtaMax;
    expanderParabolaXopt = other.expanderParabolaXopt;
    expanderParabolaCurvature = other.expanderParabolaCurvature;
    expanderParabolaFloor = other.expanderParabolaFloor;
    compressorHeadPolynomial = copyOrNull(other.compressorHeadPolynomial);
    compressorEfficiencyPolynomial = copyOrNull(other.compressorEfficiencyPolynomial);
    compressorEfficiencyScale = other.compressorEfficiencyScale;
    compressorEfficiencyMin = other.compressorEfficiencyMin;
    compressorEfficiencyMax = other.compressorEfficiencyMax;
  }

  /**
   * Copy an array, preserving null.
   *
   * @param a array or null
   * @return copy or null
   */
  private static double[] copyOrNull(double[] a) {
    return a == null ? null : a.clone();
  }

  // --- Getters and Setters for all configuration and result fields ---
  /**
   * Getter for the field <code>compressorPolytropicHead</code>.
   *
   * @return a double
   */
  public double getCompressorPolytropicHead() {
    return compressorPolytropicHead;
  }

  /**
   * Get the UC ratio for the expander after calculation.
   *
   * @return the UC ratio (expander)
   */
  public double getUCratioexpander() {
    return UCratioexpander;
  }

  /**
   * Get the UC ratio for the compressor after calculation.
   *
   * @return the UC ratio (compressor)
   */
  public double getUCratiocompressor() {
    return UCratiocompressor;
  }

  /**
   * Get the QN ratio for the expander after calculation.
   *
   * @return the QN ratio (expander)
   */
  public double getQNratioexpander() {
    return QNratioexpander;
  }

  /**
   * Get the QN ratio for the compressor after calculation.
   *
   * @return the QN ratio (compressor)
   */
  public double getQNratiocompressor() {
    return QNratiocompressor;
  }

  /**
   * getQn.
   *
   * @return a double
   */
  public double getQn() {
    return Qn;
  }

  /**
   * setQn.
   *
   * @param qn a double
   */
  public void setQn(double qn) {
    Qn = qn;
  }

  /**
   * Getter for the field <code>powerExpander</code>.
   *
   * @return a double
   */
  public double getPowerExpander() {
    return powerExpander;
  }

  /**
   * Getter for the field <code>powerExpander</code> with unit conversion.
   *
   * @param unit the desired unit ("W", "kW" or "MW")
   * @return expander power in the requested unit
   */
  public double getPowerExpander(String unit) {
    double conversionFactor = 1.0;
    if (unit.equals("MW")) {
      conversionFactor = 1.0e-6;
    } else if (unit.equals("kW")) {
      conversionFactor = 1.0e-3;
    }
    return conversionFactor * getPowerExpander();
  }

  /**
   * Getter for the field <code>powerCompressor</code>.
   *
   * @return a double
   */
  public double getPowerCompressor() {
    return powerCompressor;
  }

  /**
   * Getter for the field <code>powerCompressor</code> with unit conversion.
   *
   * @param unit the desired unit ("W", "kW" or "MW")
   * @return compressor power in the requested unit
   */
  public double getPowerCompressor(String unit) {
    double conversionFactor = 1.0;
    if (unit.equals("MW")) {
      conversionFactor = 1.0e-6;
    } else if (unit.equals("kW")) {
      conversionFactor = 1.0e-3;
    }
    return conversionFactor * getPowerCompressor();
  }

  /**
   * Getter for the field <code>expanderIsentropicEfficiency</code>.
   *
   * @return a double
   */
  public double getExpanderIsentropicEfficiency() {
    return expanderIsentropicEfficiency;
  }

  /**
   * Setter for the field <code>expanderIsentropicEfficiency</code>.
   *
   * @param expanderIsentropicEfficiency a double
   */
  public void setExpanderIsentropicEfficiency(double expanderIsentropicEfficiency) {
    this.expanderIsentropicEfficiency = expanderIsentropicEfficiency;
  }

  /**
   * getDesignCompressorPolytropicEfficiency.
   *
   * @return a double
   */
  public double getDesignCompressorPolytropicEfficiency() {
    return compressorDesignPolytropicEfficiency;
  }

  /**
   * Setter for the field <code>compressorDesignPolytropicEfficiency</code>.
   *
   * @param compressorPolytropicEfficiency a double
   */
  public void setCompressorDesignPolytropicEfficiency(double compressorPolytropicEfficiency) {
    this.compressorDesignPolytropicEfficiency = compressorPolytropicEfficiency;
  }

  /**
   * Getter for the field <code>compressorDesignPolytropicHead</code>.
   *
   * @return a double
   */
  public double getCompressorDesignPolytropicHead() {
    return compressorDesignPolytropicHead;
  }

  /**
   * Setter for the field <code>compressorDesignPolytropicHead</code>.
   *
   * @param compressorDesignPolytropicHead a double
   */
  public void setCompressorDesignPolytropicHead(double compressorDesignPolytropicHead) {
    this.compressorDesignPolytropicHead = compressorDesignPolytropicHead;
  }

  /**
   * Fit a constrained parabola: efficiency = a*(uc - h)^2 + k, with vertex at (h, k) = (1, 1).
   *
   * @param ucValues array of uc values
   * @param efficiencyValues array of efficiency values
   */
  public void setUCcurve(double[] ucValues, double[] efficiencyValues) {
    double h = ucCurveH;
    double k = ucCurveK;
    int n = ucValues.length;
    if (n < 2) {
      return;
    }
    double num = 0.0;
    double denom = 0.0;
    for (int i = 0; i < n; i++) {
      double dx = ucValues[i] - h;
      double dx2 = dx * dx;
      num += (efficiencyValues[i] - k) * dx2;
      denom += dx2 * dx2;
    }
    if (denom != 0.0) {
      ucCurveA = num / denom;
    } else {
      ucCurveA = 0.0;
    }
  }

  /**
   * Evaluate the fitted UC curve at a given uc value.
   *
   * @param uc the uc value
   * @return the efficiency
   */
  public double getEfficiencyFromUC(double uc) {
    if (ucCurveA != 0.0) {
      return ucCurveA * (uc - ucCurveH) * (uc - ucCurveH) + ucCurveK;
    }
    // Default parabola when no curve has been fitted
    return -3.56 * (uc - 1) * (uc - 1) + 1;
  }

  /**
   * Compute the expander isentropic efficiency for the current operating point. When a 2-D {@link ExpanderChartKhader}
   * performance map (P1) has been supplied it is used directly; otherwise the fitted velocity-ratio parabola scaled by
   * the design efficiency is used. The Q/N correction factor and the IGV efficiency penalty (P2) are then applied.
   *
   * @param ucRaw the raw velocity ratio U/C (used for the 2-D map lookup)
   * @param ucNorm the normalised velocity ratio U/C/designUC (used for the legacy parabola)
   * @param igv the IGV opening (fraction of maximum area, 0..1)
   * @param qnCorrection the multiplicative Q/N efficiency correction factor
   * @return the isentropic efficiency (0..1)
   */
  private double computeExpanderEfficiency(double ucRaw, double ucNorm, double igv, double qnCorrection) {
    double baseEff;
    if (expanderChart != null && expanderChart.isMapDefined()) {
      baseEff = expanderChart.getEfficiency(ucRaw, igv);
    } else if (expanderParabolaEnabled) {
      double dx = ucRaw - expanderParabolaXopt;
      baseEff = Math.max(expanderParabolaFloor, expanderParabolaEtaMax - expanderParabolaCurvature * dx * dx);
    } else {
      double cf = getEfficiencyFromUC(ucNorm);
      if (cf < 0.0) {
        cf = 0.0;
      }
      baseEff = expanderDesignIsentropicEfficiency * cf;
    }
    baseEff *= qnCorrection;
    baseEff *= getIgvEfficiencyPenalty(igv);
    if (baseEff < 0.0) {
      baseEff = 0.0;
    }
    return baseEff;
  }

  /**
   * Evaluate the IGV efficiency penalty curve (P2) at a given IGV opening. The penalty is a multiplicative factor (1.0
   * = no loss). When no penalty curve has been configured the method returns 1.0, making the feature transparent.
   * Linear interpolation with edge clamping is used.
   *
   * @param igv the IGV opening (fraction of maximum area, 0..1)
   * @return the multiplicative efficiency penalty factor (0..1)
   */
  public double getIgvEfficiencyPenalty(double igv) {
    if (igvPenaltyOpenings == null || igvPenaltyFactors == null || igvPenaltyOpenings.length < 2) {
      return 1.0;
    }
    double[] x = igvPenaltyOpenings;
    double[] y = igvPenaltyFactors;
    int n = x.length;
    if (igv <= x[0]) {
      return y[0];
    }
    if (igv >= x[n - 1]) {
      return y[n - 1];
    }
    for (int i = 0; i < n - 1; i++) {
      if (igv >= x[i] && igv <= x[i + 1]) {
        double t = (igv - x[i]) / (x[i + 1] - x[i]);
        return y[i] + t * (y[i + 1] - y[i]);
      }
    }
    return y[n - 1];
  }

  /**
   * Set the IGV efficiency penalty curve (P2). The penalty is applied as a multiplicative factor to the expander
   * efficiency, allowing the IGV schedule fitted to OEM data to act as a validatable control law.
   *
   * @param openings array of IGV openings (fraction of maximum area, 0..1)
   * @param penaltyFactors matching multiplicative efficiency penalty factors (0..1)
   */
  public void setIgvEfficiencyPenaltyCurve(double[] openings, double[] penaltyFactors) {
    if (openings == null || penaltyFactors == null || openings.length != penaltyFactors.length || openings.length < 2) {
      this.igvPenaltyOpenings = null;
      this.igvPenaltyFactors = null;
      return;
    }
    int n = openings.length;
    double[][] pairs = new double[n][2];
    for (int i = 0; i < n; i++) {
      pairs[i][0] = openings[i];
      pairs[i][1] = penaltyFactors[i];
    }
    Arrays.sort(pairs, new java.util.Comparator<double[]>() {
      @Override
      public int compare(double[] a, double[] b) {
        return Double.compare(a[0], b[0]);
      }
    });
    this.igvPenaltyOpenings = new double[n];
    this.igvPenaltyFactors = new double[n];
    for (int i = 0; i < n; i++) {
      this.igvPenaltyOpenings[i] = pairs[i][0];
      this.igvPenaltyFactors[i] = pairs[i][1];
    }
  }

  /**
   * Returns whether the IGV is treated as a fixed control input (P2).
   *
   * @return {@code true} if IGV control mode is enabled
   */
  public boolean isIgvControlMode() {
    return igvControlMode;
  }

  /**
   * Enable or disable IGV control mode (P2). When enabled the supplied IGV opening is held fixed and acts as the
   * primary turndown actuator; the model solves the speed/power balance around it instead of recomputing the IGV
   * opening from flow.
   *
   * @param igvControlMode {@code true} to treat IGV as a fixed control input
   */
  public void setIgvControlMode(boolean igvControlMode) {
    this.igvControlMode = igvControlMode;
  }

  /**
   * Get the 2-D Khader-style expander performance map (P1).
   *
   * @return the expander chart, or {@code null} if none has been set
   */
  public ExpanderChartKhader getExpanderChart() {
    return expanderChart;
  }

  /**
   * Set the 2-D Khader-style expander performance map (P1). When set it overrides the velocity-ratio parabola and
   * provides composition-aware efficiency and head as a function of U/C and IGV.
   *
   * @param expanderChart the expander chart to use
   */
  public void setExpanderChart(ExpanderChartKhader expanderChart) {
    this.expanderChart = expanderChart;
  }

  /**
   * Fit a Q/N efficiency curve using cubic spline interpolation.
   *
   * @param qnValues array of Q/N values (does not need to be sorted)
   * @param efficiencyValues array of efficiency values
   */
  public void setQNEfficiencycurve(double[] qnValues, double[] efficiencyValues) {
    if (qnValues == null || efficiencyValues == null || qnValues.length < 2 || efficiencyValues.length < 2
        || qnValues.length != efficiencyValues.length) {
      qnEffCurveQnValues = null;
      qnEffCurveEffValues = null;
      return;
    }
    // Defensive copy and sort by qnValues
    int n = qnValues.length;
    double[][] pairs = new double[n][2];
    for (int i = 0; i < n; i++) {
      pairs[i][0] = qnValues[i];
      pairs[i][1] = efficiencyValues[i];
    }
    Arrays.sort(pairs, (a, b) -> Double.compare(a[0], b[0]));
    qnEffCurveQnValues = new double[n];
    qnEffCurveEffValues = new double[n];
    for (int i = 0; i < n; i++) {
      qnEffCurveQnValues[i] = pairs[i][0];
      qnEffCurveEffValues[i] = pairs[i][1];
    }
  }

  /**
   * Evaluate the fitted Q/N efficiency curve at a given qn value using cubic spline interpolation. Linear extrapolation
   * is used outside the data range.
   *
   * @param qn the Q/N value
   * @return the efficiency
   */
  public double getEfficiencyFromQN(double qn) {
    if (qnEffCurveQnValues == null || qnEffCurveEffValues == null || qnEffCurveQnValues.length < 2) {
      return 1.0;
    }
    double[] x = qnEffCurveQnValues;
    double[] y = qnEffCurveEffValues;
    int n = x.length;

    // Extrapolate left
    if (qn <= x[0]) {
      double slope = (y[1] - y[0]) / (x[1] - x[0]);
      return y[0] + slope * (qn - x[0]);
    }
    // Extrapolate right
    if (qn >= x[n - 1]) {
      double slope = (y[n - 1] - y[n - 2]) / (x[n - 1] - x[n - 2]);
      return y[n - 1] + slope * (qn - x[n - 1]);
    }
    // Spline interpolation (piecewise cubic Hermite, monotonic)
    int i = Arrays.binarySearch(x, qn);
    if (i < 0) {
      i = -i - 2;
    }
    if (i < 0) {
      i = 0;
    }
    if (i > n - 2) {
      i = n - 2;
    }
    double x0 = x[i];
    double x1 = x[i + 1];
    double y0 = y[i];
    double y1 = y[i + 1];
    double h = x1 - x0;
    double t = (qn - x0) / h;
    // Estimate tangents (finite difference)
    double m0 = (i == 0) ? (y1 - y0) / (x1 - x0) : (y1 - y[i - 1]) / (x1 - x[i - 1]);
    double m1 = (i == n - 2) ? (y1 - y0) / (x1 - x0) : (y[i + 2] - y0) / (x[i + 2] - x0);
    // Hermite basis
    double h00 = (1 + 2 * t) * (1 - t) * (1 - t);
    double h10 = t * (1 - t) * (1 - t);
    double h01 = t * t * (3 - 2 * t);
    double h11 = t * t * (t - 1);
    return h00 * y0 + h10 * h * m0 + h01 * y1 + h11 * h * m1;
  }

  /**
   * Fit a Q/N head curve using cubic spline interpolation.
   *
   * @param qnValues array of Q/N values (does not need to be sorted)
   * @param headValues array of head values
   */
  public void setQNHeadcurve(double[] qnValues, double[] headValues) {
    if (qnValues == null || headValues == null || qnValues.length < 2 || headValues.length < 2
        || qnValues.length != headValues.length) {
      qnHeadCurveQnValues = null;
      qnHeadCurveHeadValues = null;
      return;
    }
    // Defensive copy and sort by qnValues
    int n = qnValues.length;
    double[][] pairs = new double[n][2];
    for (int i = 0; i < n; i++) {
      pairs[i][0] = qnValues[i];
      pairs[i][1] = headValues[i];
    }
    Arrays.sort(pairs, (a, b) -> Double.compare(a[0], b[0]));
    qnHeadCurveQnValues = new double[n];
    qnHeadCurveHeadValues = new double[n];
    for (int i = 0; i < n; i++) {
      qnHeadCurveQnValues[i] = pairs[i][0];
      qnHeadCurveHeadValues[i] = pairs[i][1];
    }
  }

  /**
   * Evaluate the fitted Q/N head curve at a given qn value using cubic spline interpolation. Linear extrapolation is
   * used outside the data range.
   *
   * @param qn the Q/N value
   * @return the head
   */
  public double getHeadFromQN(double qn) {
    if (qnHeadCurveQnValues == null || qnHeadCurveHeadValues == null || qnHeadCurveQnValues.length < 2) {
      return 1.0;
    }
    double[] x = qnHeadCurveQnValues;
    double[] y = qnHeadCurveHeadValues;
    int n = x.length;

    // Extrapolate left
    if (qn <= x[0]) {
      double slope = (y[1] - y[0]) / (x[1] - x[0]);
      return y[0] + slope * (qn - x[0]);
    }
    // Extrapolate right
    if (qn >= x[n - 1]) {
      double slope = (y[n - 1] - y[n - 2]) / (x[n - 1] - x[n - 2]);
      return y[n - 1] + slope * (qn - x[n - 1]);
    }
    // Spline interpolation (piecewise cubic Hermite, monotonic)
    int i = Arrays.binarySearch(x, qn);
    if (i < 0) {
      i = -i - 2;
    }
    if (i < 0) {
      i = 0;
    }
    if (i > n - 2) {
      i = n - 2;
    }
    double x0 = x[i];
    double x1 = x[i + 1];
    double y0 = y[i];
    double y1 = y[i + 1];
    double h = x1 - x0;
    double t = (qn - x0) / h;
    // Estimate tangents (finite difference)
    double m0 = (i == 0) ? (y1 - y0) / (x1 - x0) : (y1 - y[i - 1]) / (x1 - x[i - 1]);
    double m1 = (i == n - 2) ? (y1 - y0) / (x1 - x0) : (y[i + 2] - y0) / (x[i + 2] - x0);
    // Hermite basis
    double h00 = (1 + 2 * t) * (1 - t) * (1 - t);
    double h10 = t * (1 - t) * (1 - t);
    double h01 = t * t * (3 - 2 * t);
    double h11 = t * t * (t - 1);
    return h00 * y0 + h10 * h * m0 + h01 * y1 + h11 * h * m1;
  }

  /**
   * Calculate the IGV opening based on the current IGV opening fraction.
   *
   * @return IGV opening (fraction of max area)
   */
  public double calcIGVOpening() {
    return IGVopening;
  }

  /**
   * Calculate the current IGV (Inlet Guide Vane) open area.
   *
   * @return the IGV open area in mm²
   */
  public double calcIGVOpenArea() {
    if (currentIGVArea > 0.0) {
      return currentIGVArea;
    }
    double massFlow = expanderFeedStream != null ? expanderFeedStream.getFluid().getFlowRate("kg/sec") : 0.0;
    double volumetricFlow = expanderFeedStream != null ? expanderFeedStream.getFluid().getFlowRate("m3/sec") : 0.0;
    IGVModelResult res = evaluateIGV(lastStageEnthalpyDrop, massFlow, volumetricFlow);
    return res.areaMm2;
  }

  /**
   * Calculate the IGV (Inlet Guide Vane) opening using the current flow conditions and last computed stage enthalpy
   * drop.
   *
   * @return IGV opening (fraction of max area, capped at 1.0)
   */
  public double calcIGVOpeningFromFlow() {
    double massFlow = expanderFeedStream != null ? expanderFeedStream.getFluid().getFlowRate("kg/sec") : 0.0;
    double volumetricFlow = expanderFeedStream != null ? expanderFeedStream.getFluid().getFlowRate("m3/sec") : 0.0;
    IGVModelResult res = evaluateIGV(lastStageEnthalpyDrop, massFlow, volumetricFlow);
    return res.opening;
  }

  private static final class IGVModelResult {
    private final double opening;
    private final double areaMm2;
    private final boolean expanded;

    private IGVModelResult(double opening, double areaMm2, boolean expanded) {
      this.opening = opening;
      this.areaMm2 = areaMm2;
      this.expanded = expanded;
    }
  }

  private IGVModelResult evaluateIGV(double stageDrop, double massFlow, double volumetricFlow) {
    double opening = 0.0;
    double areaMm2 = 0.0;
    boolean expanded = false;
    if (massFlow > 0.0 && stageDrop > 0.0) {
      double density = Double.NaN;
      if (expanderFeedStream != null) {
        density = expanderFeedStream.getFluid().getDensity("kg/m3");
      }
      if (!(density > 0.0) && volumetricFlow > 0.0) {
        density = massFlow / volumetricFlow;
      }
      if (density > 0.0) {
        double deltaHigv = 0.5 * stageDrop;
        if (deltaHigv <= 0.0) {
          deltaHigv = Math.max(stageDrop * 0.5, 1.0);
        }
        double velocity = Math.sqrt(2.0 * Math.max(deltaHigv, 1e-6));
        double requiredArea = massFlow / (density * velocity);
        double baseArea = maximumIGVArea / 1.0e6;
        double expandedArea = baseArea * igvAreaIncreaseFactor;
        double availableArea = baseArea;
        if (requiredArea > baseArea && expandedArea > baseArea) {
          availableArea = expandedArea;
          expanded = true;
        }
        if (availableArea > 0.0) {
          opening = requiredArea / availableArea;
        }
        if (!Double.isFinite(opening) || opening < 0.0) {
          opening = 0.0;
        }
        if (opening > 1.0) {
          opening = 1.0;
        }
        areaMm2 = opening * availableArea * 1.0e6;
        if (opening >= 1.0 && requiredArea > availableArea) {
          areaMm2 = availableArea * 1.0e6;
        }
      }
    }
    return new IGVModelResult(opening, areaMm2, expanded);
  }

  private void updateIGVState(double stageDrop, double massFlow, double volumetricFlow) {
    IGVModelResult res = evaluateIGV(stageDrop, massFlow, volumetricFlow);
    IGVopening = res.opening;
    currentIGVArea = res.areaMm2;
    usingExpandedIGVArea = res.expanded;
    lastStageEnthalpyDrop = stageDrop;
  }

  // --- Setters ---
  /**
   * Setter for the field <code>impellerDiameter</code>.
   *
   * @param impellerDiameter a double
   */
  public void setImpellerDiameter(double impellerDiameter) {
    this.impellerDiameter = impellerDiameter;
  }

  /**
   * Setter for the field <code>designSpeed</code>.
   *
   * @param designSpeed a double
   */
  public void setDesignSpeed(double designSpeed) {
    this.designSpeed = designSpeed;
  }

  /**
   * Setter for the field <code>designUC</code>.
   *
   * @param designUC a double
   */
  public void setDesignUC(double designUC) {
    this.designUC = designUC;
  }

  /**
   * Setter for the field <code>designQn</code>.
   *
   * @param designQn a double
   */
  public void setDesignQn(double designQn) {
    this.designQn = designQn;
  }

  public double getDesignExpanderQn() {
    return designExpanderQn;
  }

  public void setDesignExpanderQn(double designExpanderQn) {
    if (designExpanderQn <= 0.0) {
      this.designExpanderQn = 0.0;
    } else {
      this.designExpanderQn = designExpanderQn;
    }
  }

  /**
   * Setter for the field <code>maximumIGVArea</code>.
   *
   * @param maximumIGVArea a double
   */
  public void setMaximumIGVArea(double maximumIGVArea) {
    this.maximumIGVArea = maximumIGVArea;
    double activeArea = usingExpandedIGVArea ? maximumIGVArea * igvAreaIncreaseFactor : maximumIGVArea;
    currentIGVArea = IGVopening * activeArea;
  }

  public double getIgvAreaIncreaseFactor() {
    return igvAreaIncreaseFactor;
  }

  public void setIgvAreaIncreaseFactor(double igvAreaIncreaseFactor) {
    double factor = igvAreaIncreaseFactor < 1.0 ? 1.0 : igvAreaIncreaseFactor;
    this.igvAreaIncreaseFactor = factor;
    double activeArea = usingExpandedIGVArea ? maximumIGVArea * this.igvAreaIncreaseFactor : maximumIGVArea;
    currentIGVArea = IGVopening * activeArea;
  }

  // --- Getters ---
  /**
   * Getter for the field <code>impellerDiameter</code>.
   *
   * @return a double
   */
  public double getImpellerDiameter() {
    return impellerDiameter;
  }

  /**
   * Getter for the field <code>designSpeed</code>.
   *
   * @return a double
   */
  public double getDesignSpeed() {
    return designSpeed;
  }

  /**
   * Getter for the field <code>designUC</code>.
   *
   * @return a double
   */
  public double getDesignUC() {
    return designUC;
  }

  /**
   * Getter for the field <code>designQn</code>.
   *
   * @return a double
   */
  public double getDesignQn() {
    return designQn;
  }

  /**
   * Getter for the field <code>maximumIGVArea</code>.
   *
   * @return a double
   */
  public double getMaximumIGVArea() {
    return maximumIGVArea;
  }

  /**
   * getCompressorPolytropicEfficieny.
   *
   * @return a double
   */
  public double getCompressorPolytropicEfficieny() {
    return compressorPolytropicEfficiency;
  }

  /**
   * getCompressorDesingPolytropicHead.
   *
   * @return a double
   */
  public double getCompressorDesingPolytropicHead() {
    return compressorDesignPolytropicHead;
  }

  /**
   * getIGVopening.
   *
   * @return a double
   */
  public double getIGVopening() {
    return IGVopening;
  }

  /**
   * setIGVopening.
   *
   * @param iGVopening a double
   */
  public void setIGVopening(double iGVopening) {
    IGVopening = Math.max(0.0, Math.min(1.0, iGVopening));
    usingExpandedIGVArea = false;
    currentIGVArea = IGVopening * maximumIGVArea;
  }

  public boolean isUsingExpandedIGVArea() {
    return usingExpandedIGVArea;
  }

  public double getCurrentIGVArea() {
    return currentIGVArea;
  }

  /**
   * Set the expander outlet pressure (absolute, bar).
   *
   * @param expanderOutPressure the desired expander outlet pressure in bar abs
   */
  public void setExpanderOutPressure(double expanderOutPressure) {
    this.expanderOutPressure = expanderOutPressure;
  }

  /**
   * Set the compressor feed stream for the turbo expander-compressor system.
   *
   * @param compressorFeedStream the feed stream to use for the compressor
   */
  public void setCompressorFeedStream(StreamInterface compressorFeedStream) {
    this.compressorFeedStream = compressorFeedStream;
  }

  /**
   * Get the compressor outlet stream after calculation.
   *
   * @return the compressor outlet stream
   */
  public StreamInterface getCompressorOutletStream() {
    return compressorOutletStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getSpeed() {
    return expanderSpeed;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutletStream() {
    return compressorOutletStream;
  }

  /**
   * setUCratioexpander.
   *
   * @param UCratioexpander a double
   */
  public void setUCratioexpander(double UCratioexpander) {
    this.UCratioexpander = UCratioexpander;
  }

  /**
   * setUCratiocompressor.
   *
   * @param UCratiocompressor a double
   */
  public void setUCratiocompressor(double UCratiocompressor) {
    this.UCratiocompressor = UCratiocompressor;
  }

  /**
   * setQNratioexpander.
   *
   * @param QNratioexpander a double
   */
  public void setQNratioexpander(double QNratioexpander) {
    this.QNratioexpander = QNratioexpander;
  }

  /**
   * setQNratiocompressor.
   *
   * @param QNratiocompressor a double
   */
  public void setQNratiocompressor(double QNratiocompressor) {
    this.QNratiocompressor = QNratiocompressor;
  }

  /**
   * getSerialversionuid.
   *
   * @return a long
   */
  public static long getSerialversionuid() {
    return serialVersionUID;
  }

  /**
   * Getter for the field <code>expanderOutPressure</code>.
   *
   * @return a double
   */
  public double getExpanderOutPressure() {
    return expanderOutPressure;
  }

  /**
   * Getter for the field <code>bearingLossPower</code>.
   *
   * @return a double
   */
  public double getBearingLossPower() {
    return bearingLossPower;
  }

  /**
   * Getter for the field <code>expanderSpeed</code>.
   *
   * @return a double
   */
  public double getExpanderSpeed() {
    return expanderSpeed;
  }

  /**
   * Getter for the field <code>compressorSpeed</code>.
   *
   * @return a double
   */
  public double getCompressorSpeed() {
    return compressorSpeed;
  }

  /**
   * Getter for the field <code>gearRatio</code>.
   *
   * @return a double
   */
  public double getGearRatio() {
    return gearRatio;
  }

  /**
   * Getter for the field <code>compressorFeedStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getCompressorFeedStream() {
    return compressorFeedStream;
  }

  /**
   * Getter for the field <code>expanderFeedStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getExpanderFeedStream() {
    return expanderFeedStream;
  }

  /**
   * Getter for the field <code>expanderOutletStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getExpanderOutletStream() {
    return expanderOutletStream;
  }

  /**
   * Getter for the field <code>ucCurveA</code>.
   *
   * @return a double
   */
  public double getUcCurveA() {
    return ucCurveA;
  }

  /**
   * Getter for the field <code>ucCurveH</code>.
   *
   * @return a double
   */
  public double getUcCurveH() {
    return ucCurveH;
  }

  /**
   * Getter for the field <code>ucCurveK</code>.
   *
   * @return a double
   */
  public double getUcCurveK() {
    return ucCurveK;
  }

  /**
   * Getter for the field <code>qnCurveA</code>.
   *
   * @return a double
   */
  public double getQnCurveA() {
    return qnCurveA;
  }

  /**
   * Getter for the field <code>qnCurveH</code>.
   *
   * @return a double
   */
  public double getQnCurveH() {
    return qnCurveH;
  }

  /**
   * Getter for the field <code>qnCurveK</code>.
   *
   * @return a double
   */
  public double getQnCurveK() {
    return qnCurveK;
  }

  /**
   * Getter for the field <code>qnHeadCurveA</code>.
   *
   * @return a double
   */
  public double getQnHeadCurveA() {
    return qnHeadCurveA;
  }

  /**
   * Getter for the field <code>qnHeadCurveH</code>.
   *
   * @return a double
   */
  public double getQnHeadCurveH() {
    return qnHeadCurveH;
  }

  /**
   * Getter for the field <code>qnHeadCurveK</code>.
   *
   * @return a double
   */
  public double getQnHeadCurveK() {
    return qnHeadCurveK;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new TurboExpanderCompressorResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    TurboExpanderCompressorResponse res = new TurboExpanderCompressorResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /**
   * Getter for the field <code>compressorDesignPolytropicEfficiency</code>.
   *
   * @return a double
   */
  public double getCompressorDesignPolytropicEfficiency() {
    return compressorDesignPolytropicEfficiency;
  }

  /**
   * Getter for the field <code>compressorPolytropicEfficiency</code>.
   *
   * @return a double
   */
  public double getCompressorPolytropicEfficiency() {
    return compressorPolytropicEfficiency;
  }

  /**
   * Getter for the field <code>expanderDesignIsentropicEfficiency</code>.
   *
   * @return a double
   */
  public double getExpanderDesignIsentropicEfficiency() {
    return expanderDesignIsentropicEfficiency;
  }

  /**
   * Setter for the field <code>expanderDesignIsentropicEfficiency</code>.
   *
   * @param expanderDesignIsentropicEfficiency a double
   */
  public void setExpanderDesignIsentropicEfficiency(double expanderDesignIsentropicEfficiency) {
    this.expanderDesignIsentropicEfficiency = expanderDesignIsentropicEfficiency;
  }

  /**
   * Specify the desired expander outlet temperature. When set, the base (design) isentropic efficiency is automatically
   * back-calculated during {@link #run(UUID)} so that the actual expander outlet temperature matches this target at the
   * configured expander outlet pressure.
   *
   * @param temperature the target expander outlet temperature
   * @param unit the temperature unit ("K", "C", "F" or "R")
   */
  public void setExpanderOutTemperature(double temperature, String unit) {
    this.expanderOutTemperatureSpec = new neqsim.util.unit.TemperatureUnit(temperature, unit).getValue("K");
    this.useOutTemperatureSpec = true;
  }

  /**
   * Get the specified expander outlet temperature target.
   *
   * @param unit the temperature unit ("K", "C", "F" or "R")
   * @return the target expander outlet temperature in the requested unit
   */
  public double getExpanderOutTemperature(String unit) {
    return new neqsim.util.unit.TemperatureUnit(expanderOutTemperatureSpec, "K").getValue(unit);
  }

  /**
   * Check whether the expander outlet temperature is being used as a specification.
   *
   * @return {@code true} if the outlet temperature specification is active
   */
  public boolean isUseOutTemperatureSpec() {
    return useOutTemperatureSpec;
  }

  /**
   * Enable or disable the expander outlet temperature specification. When disabled, the expander uses the configured
   * design isentropic efficiency directly.
   *
   * @param useOutTemperatureSpec {@code true} to activate the outlet temperature specification
   */
  public void setUseOutTemperatureSpec(boolean useOutTemperatureSpec) {
    this.useOutTemperatureSpec = useOutTemperatureSpec;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double expanderBalance = 0.0;
    double compressorBalance = 0.0;

    if (expanderFeedStream != null && expanderOutletStream != null) {
      expanderBalance = expanderOutletStream.getFlowRate(unit) - expanderFeedStream.getFlowRate(unit);
    }

    if (compressorFeedStream != null && compressorOutletStream != null) {
      compressorBalance = compressorOutletStream.getFlowRate(unit) - compressorFeedStream.getFlowRate(unit);
    }

    return expanderBalance + compressorBalance;
  }

  /**
   * Get the coupled mechanical design for this turbo-expander-compressor.
   *
   * @return the coupled mechanical design
   */
  public TurboExpanderCompressorMechanicalDesign getTECMechanicalDesign() {
    return tecMechanicalDesign;
  }

  /**
   * Initialize the coupled mechanical design for this turbo-expander-compressor.
   */
  public void initTECMechanicalDesign() {
    tecMechanicalDesign = new TurboExpanderCompressorMechanicalDesign(this);
  }
}
