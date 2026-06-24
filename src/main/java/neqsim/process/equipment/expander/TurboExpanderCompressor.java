package neqsim.process.equipment.expander;

import java.util.Arrays;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.compressor.Compressor;
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
    final double N_max = 9000.0;
    final double N_min = 1000.0;
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
    do {
      double outPress = expanderOutPressure;
      SystemInterface fluid2 = expanderFeedStream.getThermoSystem().clone();
      fluid2.initThermoProperties();
      double s1 = fluid2.getEntropy("kJ/kgK");
      double h_in = fluid2.getEnthalpy("kJ/kg");
      fluid2.setPressure(outPress, "bara");
      ThermodynamicOperations flash = new ThermodynamicOperations(fluid2);
      flash.PSflash(s1, "kJ/kgK");
      fluid2.init(3);
      double h_out = fluid2.getEnthalpy("kJ/kg");
      h_s = (h_in - h_out) * 1000; // J/kg
      double U = Math.PI * D * N / 60.0;
      double C = Math.sqrt(2.0 * h_s);
      uc = U / C / designUC;
      double ucRaw = U / C;
      Q_exp = expanderFeedStream.getFluid().getFlowRate("m3/sec");
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
      W_expander = m1 * h_s * eta_s;
      m_comp = compressorFeedStream.getFluid().getFlowRate("kg/sec");
      Q_comp = compressorFeedStream.getFluid().getFlowRate("m3/sec");
      qn_ratio = (Q_comp * 60.0 / N) / designQn;
      CF_eff_comp = getEfficiencyFromQN(qn_ratio);
      CF_eff_comp = Math.max(CF_eff_comp, 1e-6);
      CF_head_comp = getHeadFromQN(qn_ratio);
      CF_head_comp = Math.max(CF_head_comp, 1e-6);
      Hp = Hp_design * (N / N_design) * (N / N_design) * CF_head_comp;
      eta_p = CF_eff_comp * eta_p_design;
      W_compressor = m_comp * Hp / eta_p * 1000.0;
      W_bearing = bearingLossPower * (N / N_design) * (N / N_design);

      double fN = W_expander - (W_compressor + W_bearing);
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
      double W_expander2 = m1 * h_s * eta_s2;
      qn_ratio2 = (Q_comp * 60.0 / N2) / designQn;
      double CF_eff_comp2 = getEfficiencyFromQN(qn_ratio2);
      CF_eff_comp2 = Math.max(CF_eff_comp2, 1e-6);
      Hp2 = Hp_design * (N2 / N_design) * (N2 / N_design) * CF_head_comp;
      eta_p2 = CF_eff_comp2 * eta_p_design;
      double W_compressor2 = m_comp * Hp2 / eta_p2 * 1000.0;
      double W_bearing2 = bearingLossPower * (N2 / N_design) * (N2 / N_design);
      double fN2 = W_expander2 - (W_compressor2 + W_bearing2);
      double df_dN = (fN2 - fN) / dN;
      if (Math.abs(df_dN) < 1e-8) {
	dN += 10.0;
	N += 10.0;
      } else {
	N = N - (1.0 + iter) / (iter + 5) * fN / df_dN;
      }
      if (N > N_max) {
	N = N_max;
	break;
      }
      if (N < N_min) {
	N = N_min;
	break;
      }
      // System.out.println("speed: " + N + " iter: " + iter);
      iter++;
    } while (Math.abs(W_expander - (W_compressor + W_bearing)) * 100 > 1e-3 && iter < maxIter || iter < minIter);
    if (iter >= maxIter) {
      System.out.println("Warning: TurboExpanderCompressor did not converge.");
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
    if (!igvControlMode) {
      updateIGVState(h_s, m1, Q_exp);
    } else {
      lastStageEnthalpyDrop = h_s;
    }
    setQNratiocompressor(qn_ratio);
    setQn(N / 60.0 * Q_comp / designQn);

    Expander expander = new Expander("tempExpander", expanderFeedStream);
    expander.setOutletPressure(getExpanderOutPressure());
    expander.setIsentropicEfficiency(eta_s);
    expander.run();
    expanderOutletStream.setFluid(expander.getOutletStream().getFluid());

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
