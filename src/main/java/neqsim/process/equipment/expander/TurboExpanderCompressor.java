package neqsim.process.equipment.expander;

import java.util.Arrays;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.TurboExpanderCompressorResponse;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * TurboExpanderCompressor models a coupled expander and compressor system with design and
 * performance parameters, polynomial curve fits for efficiency and head, and Newton-Raphson
 * iteration for speed matching.
 * <p>
 * This class provides configuration for impeller, speed, efficiency, and curve fit parameters, and
 * exposes all relevant design and result values via getters/setters. The main run() method matches
 * expander and compressor power using a robust Newton-Raphson approach, updating all result fields
 * and output streams.
 * </p>
 */
public class TurboExpanderCompressor extends Expander {

  private static final long serialVersionUID = 1001;

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
  /** Design isentropic efficiency (expander). */
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
  /** Actual Q/N (flow/speed ratio). */
  private double Qn = 0.03328;
  /** Maximum IGV area [mm^2]. */
  private double maximumIGVArea = 1.637e4;
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
   * Run the expander/compressor calculation, matching expander and compressor power using
   * Newton-Raphson iteration. Updates all result fields and output streams.
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    double N = designSpeed; // initial guess for speed [rpm]
    final double N_max = 9000.0;
    final double N_min = 1000.0;
    double eta_p = 0.0, W_compressor = 0.0, W_expander = 0.0, W_bearing = 0.0;
    double D = impellerDiameter;
    double eta_s_design = expanderDesignIsentropicEfficiency;
    double Hp_design = compressorDesignPolytropicHead;
    double eta_p_design = compressorDesignPolytropicEfficiency;
    double N_design = designSpeed;
    double m1 = expanderFeedStream.getFlowRate("kg/sec");
    double Q_comp = 0.0, m_comp = 0.0, eta_s = 0.0, Hp = 0.0, CF_eff_comp = 1.0, CF_head_comp = 1.0;
    // Newton-Raphson method for speed matching
    int maxIter = 50;
    double dN = 10.0;

    int iter = 0;
    double Hp2 = 0.0, eta_p2 = 0.0, eta_s2 = 0.0, uc = 0.0, uc2 = 0.0, qn_ratio = 0.0,
        qn_ratio2 = 0.0;
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
      double h_s = (h_in - h_out) * 1000; // J/kg
      double U = Math.PI * D * N / 60.0;
      double C = Math.sqrt(2.0 * h_s);
      uc = U / C / designUC;
      double CF = getEfficiencyFromUC(uc);
      eta_s = eta_s_design * CF;
      W_expander = m1 * h_s * eta_s;
      m_comp = compressorFeedStream.getFluid().getFlowRate("kg/sec");
      Q_comp = compressorFeedStream.getFluid().getFlowRate("m3/sec");
      qn_ratio = (Q_comp * 60.0 / N) / designQn;
      CF_eff_comp = getEfficiencyFromQN(qn_ratio);
      CF_head_comp = getHeadFromQN(qn_ratio);
      Hp = Hp_design * (N / N_design) * (N / N_design) * CF_head_comp;
      eta_p = CF_eff_comp * eta_p_design;
      W_compressor = m_comp * Hp / eta_p * 1000.0;
      W_bearing = bearingLossPower * (N / N_design) * (N / N_design);

      double fN = W_expander - (W_compressor + W_bearing);
      // Finite difference for derivative
      double N2 = N + dN;
      double U2 = Math.PI * D * N2 / 60.0;
      uc2 = U2 / C / designUC;
      double CF2 = getEfficiencyFromUC(uc2);
      eta_s2 = eta_s_design * CF2;
      double W_expander2 = m1 * h_s * eta_s2;
      qn_ratio2 = (Q_comp * 60.0 / N2) / designQn;
      double CF_eff_comp2 = getEfficiencyFromQN(qn_ratio2);
      Hp2 = Hp_design * (N2 / N_design) * (N2 / N_design) * CF_head_comp;
      eta_p2 = CF_eff_comp2 * eta_p_design;
      double W_compressor2 = m_comp * Hp2 / eta_p2 * 1000.0;
      double W_bearing2 = bearingLossPower * (N2 / N_design) * (N2 / N_design);
      double fN2 = W_expander2 - (W_compressor2 + W_bearing2);
      double df_dN = (fN2 - fN) / dN;
      if (Math.abs(df_dN) < 1e-8) {
        N += 10.0;
      } else {
        N = N - fN / df_dN;
      }
      if (N > N_max) {
        N = N_max;
      }
      if (N < N_min) {
        N = N_min;
      }
      // System.out.println("speed: " + N + " iter: " + iter);
      iter++;
    } while (Math.abs(W_expander - (W_compressor + W_bearing)) * 100 > 1e-3 && iter < maxIter);
    if (iter >= maxIter) {
      System.out.println("Warning: TurboExpanderCompressor did not converge.");
    }

    expanderIsentropicEfficiency = eta_s;
    compressorPolytropicHead = Hp;
    compressorPolytropicEfficiency = eta_p;
    expanderSpeed = N;
    setSpeed(N);
    setPower(W_expander);
    powerExpander = W_expander;
    powerCompressor = W_compressor;
    setUCratioexpander(uc);
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

  // --- Getters and Setters for all configuration and result fields ---
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

  public double getQn() {
    return Qn;
  }

  public void setQn(double qn) {
    Qn = qn;
  }

  public double getPowerExpander() {
    return powerExpander;
  }

  public double getPowerCompressor() {
    return powerCompressor;
  }

  public double getExpanderIsentropicEfficiency() {
    return expanderIsentropicEfficiency;
  }

  public void setExpanderIsentropicEfficiency(double expanderIsentropicEfficiency) {
    this.expanderIsentropicEfficiency = expanderIsentropicEfficiency;
  }

  public double getDesignCompressorPolytropicEfficiency() {
    return compressorDesignPolytropicEfficiency;
  }

  public void setCompressorDesignPolytropicEfficiency(double compressorPolytropicEfficiency) {
    this.compressorDesignPolytropicEfficiency = compressorPolytropicEfficiency;
  }

  public double getCompressorDesignPolytropicHead() {
    return compressorDesignPolytropicHead;
  }

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
      num += (efficiencyValues[i] - k) * (dx);
      denom += dx * dx;
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
    // return ucCurveA * (uc - ucCurveH) * (uc - ucCurveH) + ucCurveK;
    return -3.56 * (uc - 1) * (uc - 1) + 1;
  }

  /**
   * Fit a Q/N efficiency curve using cubic spline interpolation.
   *
   * @param qnValues array of Q/N values (does not need to be sorted)
   * @param efficiencyValues array of efficiency values
   */
  public void setQNEfficiencycurve(double[] qnValues, double[] efficiencyValues) {
    if (qnValues == null || efficiencyValues == null || qnValues.length < 2
        || efficiencyValues.length < 2 || qnValues.length != efficiencyValues.length) {
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
   * Evaluate the fitted Q/N efficiency curve at a given qn value using cubic spline interpolation.
   * Linear extrapolation is used outside the data range.
   *
   * @param qn the Q/N value
   * @return the efficiency
   */
  public double getEfficiencyFromQN(double qn) {
    if (qnEffCurveQnValues == null || qnEffCurveEffValues == null
        || qnEffCurveQnValues.length < 2) {
      return 0.0;
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
    if (i < 0)
      i = 0;
    if (i > n - 2)
      i = n - 2;
    double x0 = x[i], x1 = x[i + 1];
    double y0 = y[i], y1 = y[i + 1];
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
   * Evaluate the fitted Q/N head curve at a given qn value using cubic spline interpolation. Linear
   * extrapolation is used outside the data range.
   *
   * @param qn the Q/N value
   * @return the head
   */
  public double getHeadFromQN(double qn) {
    if (qnHeadCurveQnValues == null || qnHeadCurveHeadValues == null
        || qnHeadCurveQnValues.length < 2) {
      return 0.0;
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
    if (i < 0)
      i = 0;
    if (i > n - 2)
      i = n - 2;
    double x0 = x[i], x1 = x[i + 1];
    double y0 = y[i], y1 = y[i + 1];
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
    return IGVopening * maximumIGVArea;
  }

  /**
   * Calculate the IGV (Inlet Guide Vane) opening as the ratio of the volumetric flow into the
   * expander to the maximum IGV area (assuming unit velocity for simplicity).
   *
   * @return IGV opening (fraction of max area, capped at 1.0)
   */
  public double calcIGVOpeningFromFlow() {
    // Volumetric flow into expander in m3/s
    double volumetricFlow =
        expanderFeedStream != null ? expanderFeedStream.getFluid().getFlowRate("m3/sec") : 0.0;
    // Maximum IGV area in m2 (convert from mm2)
    double maxArea = maximumIGVArea / 1.0e6;
    // Assume a reference velocity (e.g., 1 m/s) for area calculation
    double referenceVelocity = 1.0; // m/s
    double requiredArea = volumetricFlow / referenceVelocity;
    double opening = maxArea > 0.0 ? requiredArea / maxArea : 0.0;
    // Cap at 1.0 (cannot open more than max area)s
    return Math.min(opening, 1.0);
  }

  // --- Setters ---
  public void setImpellerDiameter(double impellerDiameter) {
    this.impellerDiameter = impellerDiameter;
  }

  public void setDesignSpeed(double designSpeed) {
    this.designSpeed = designSpeed;
  }

  public void setDesignUC(double designUC) {
    this.designUC = designUC;
  }

  public void setDesignQn(double designQn) {
    this.designQn = designQn;
  }

  public void setMaximumIGVArea(double maximumIGVArea) {
    this.maximumIGVArea = maximumIGVArea;
  }

  // --- Getters ---
  public double getImpellerDiameter() {
    return impellerDiameter;
  }

  public double getDesignSpeed() {
    return designSpeed;
  }

  public double getDesignUC() {
    return designUC;
  }

  public double getDesignQn() {
    return designQn;
  }

  public double getMaximumIGVArea() {
    return maximumIGVArea;
  }

  public double getCompressorPolytropicEfficieny() {
    return compressorPolytropicEfficiency;
  }

  public double getCompressorDesingPolytropicHead() {
    return compressorDesignPolytropicHead;
  }

  public double getIGVopening() {

    return IGVopening;
  }

  public void setIGVopening(double iGVopening) {
    IGVopening = iGVopening;
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

  /**
   * Get the current speed (expander speed) after calculation.
   *
   * @return the matched expander speed [rpm]
   */
  public double getSpeed() {
    return expanderSpeed;
  }

  /**
   * Get the outlet stream (for compatibility with Stream constructor in test).
   *
   * @return the compressor outlet stream
   */
  public StreamInterface getOutletStream() {
    return compressorOutletStream;
  }

  public void setUCratioexpander(double UCratioexpander) {
    this.UCratioexpander = UCratioexpander;
  }

  public void setUCratiocompressor(double UCratiocompressor) {
    this.UCratiocompressor = UCratiocompressor;
  }

  public void setQNratioexpander(double QNratioexpander) {
    this.QNratioexpander = QNratioexpander;
  }

  public void setQNratiocompressor(double QNratiocompressor) {
    this.QNratiocompressor = QNratiocompressor;
  }

  public static long getSerialversionuid() {
    return serialVersionUID;
  }

  public double getExpanderOutPressure() {
    return expanderOutPressure;
  }

  public double getBearingLossPower() {
    return bearingLossPower;
  }

  public double getExpanderSpeed() {
    return expanderSpeed;
  }

  public double getCompressorSpeed() {
    return compressorSpeed;
  }

  public double getGearRatio() {
    return gearRatio;
  }

  public StreamInterface getCompressorFeedStream() {
    return compressorFeedStream;
  }

  public StreamInterface getExpanderFeedStream() {
    return expanderFeedStream;
  }

  public StreamInterface getExpanderOutletStream() {
    return expanderOutletStream;
  }

  public double getUcCurveA() {
    return ucCurveA;
  }

  public double getUcCurveH() {
    return ucCurveH;
  }

  public double getUcCurveK() {
    return ucCurveK;
  }

  public double getQnCurveA() {
    return qnCurveA;
  }

  public double getQnCurveH() {
    return qnCurveH;
  }

  public double getQnCurveK() {
    return qnCurveK;
  }

  public double getQnHeadCurveA() {
    return qnHeadCurveA;
  }

  public double getQnHeadCurveH() {
    return qnHeadCurveH;
  }

  public double getQnHeadCurveK() {
    return qnHeadCurveK;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new TurboExpanderCompressorResponse(this));
  }

  public double getCompressorDesignPolytropicEfficiency() {
    return compressorDesignPolytropicEfficiency;
  }

  public double getCompressorPolytropicEfficiency() {
    return compressorPolytropicEfficiency;
  }


  public double getExpanderDesignIsentropicEfficiency() {
    return expanderDesignIsentropicEfficiency;
  }

  public void setExpanderDesignIsentropicEfficiency(double expanderDesignIsentropicEfficiency) {
    this.expanderDesignIsentropicEfficiency = expanderDesignIsentropicEfficiency;
  }


}
