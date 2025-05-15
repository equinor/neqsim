package neqsim.process.equipment.expander;

import java.util.UUID;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class TurboExpanderCompressor extends Expander {

  private static final long serialVersionUID = 1001;
  private double expanderOutPressure = 40.0; // bar
  private double IGVposition = 1.0; // 1.0 = 100% open
  private double bearingLossPower = 10.0; // W
  private double expanderSpeed = 1000.0; // rpm, stores the matched expander speed

  StreamInterface compressorFeedStream = null;
  StreamInterface compressorOutletStream = null;

  StreamInterface expanderFeedStream = null;
  StreamInterface expanderOutletStream = null;

  // --- Design and configuration parameters ---
  private double impellerDiameter = 0.424; // m
  private double designSpeed = 6850.0; // rpm
  private double designIsentropicEfficiency = 0.88;
  private double designUC = 0.7; // m/s
  private double UCratioexpander = 0.7; // m/s
  private double UCratiocompressor = 0.7; // m/s

  public double getUCratioexpander() {
    return UCratioexpander;
  }

  public void setUCratioexpander(double uCratioexpander) {
    UCratioexpander = uCratioexpander;
  }

  public double getUCratiocompressor() {
    return UCratiocompressor;
  }

  public void setUCratiocompressor(double uCratiocompressor) {
    UCratiocompressor = uCratiocompressor;
  }

  public double getQNratioexpander() {
    return QNratioexpander;
  }

  public void setQNratioexpander(double qNratioexpander) {
    QNratioexpander = qNratioexpander;
  }

  public double getQNratiocompressor() {
    return QNratiocompressor;
  }

  public void setQNratiocompressor(double qNratiocompressor) {
    QNratiocompressor = qNratiocompressor;
  }

  private double QNratioexpander = 0.7; // m/s
  private double QNratiocompressor = 0.7; // m/s
  private double designQn = 0.03328;

  public double getQn() {
    return Qn;
  }

  public void setQn(double qn) {
    Qn = qn;
  }

  private double Qn = 0.03328;
  private double maximumIGVArea = 1.637e4; // mm^2 or as required
  private double compressorPolytropicEfficiency = 0.81;
  private double compressorDesignPolytropicHead = 20.47; // kJ/kg
  private double compressorPolytropicHead = 20.47;

  public double getCompressorPolytropicHead() {
    return compressorPolytropicHead;
  }

  public void setCompressorPolytropicHead(double compressorPolytropicHead) {
    this.compressorPolytropicHead = compressorPolytropicHead;
  }

  private double expanderIsentropicEfficiency = 1.0;
  double powerExpander = 0.0; // W

  public double getPowerExpander() {
    return powerExpander;
  }

  public void setPowerExpander(double powerExpander) {
    this.powerExpander = powerExpander;
  }

  public double getPowerCompressor() {
    return powerCompressor;
  }

  public void setPowerCompressor(double powerCompressor) {
    this.powerCompressor = powerCompressor;
  }

  double powerCompressor = 0.0; // W


  // Stores the fitted 'a' parameter for the constrained parabola
  private double ucCurveA = 0.0;
  private double ucCurveH = 1.0; // vertex x (h)
  private double ucCurveK = 1.0; // vertex y (k)

  // Stores the fitted 'a' parameter for the constrained parabola for Q/N
  private double qnCurveA = 0.0;
  private double qnCurveH = 1.0; // vertex x (h)
  private double qnCurveK = 1.0; // vertex y (k)

  // Stores the fitted 'a' parameter for the constrained parabola for Q/N head
  // curve
  private double qnHeadCurveA = 0.0;
  private double qnHeadCurveH = 1.0; // vertex x (h)
  private double qnHeadCurveK = 1.0; // vertex y (k)

  /**
   * Constructs a TurboExpanderCompressor with the specified name and inlet stream.
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

  public StreamInterface getCompressorFeedStream() {
    return compressorFeedStream;
  }

  public StreamInterface getCompressorOutletStream() {
    return compressorOutletStream;
  }

  public StreamInterface getExpanderOutletStream() {
    return expanderOutletStream;
  }

  public void setCompressorFeedStream(StreamInterface compressorFeedStream) {
    this.compressorFeedStream = compressorFeedStream;
  }

  public void setIGVposition(double IGVposition) {
    this.IGVposition = IGVposition;
  }

  public void setExpanderOutPressure(double pressure) {
    this.expanderOutPressure = pressure;
  }

  @Override
  public void run(UUID id) {
    double N = designSpeed; // use designSpeed as initial guess
    final double N_max = 9000.0;
    final double N_min = 1000.0;
    double eta_p = 0.0;
    double W_compressor = 0.0;
    double W_expander = 0.0;
    double W_bearing = 0.0;
    double D = impellerDiameter; // m, impeller diameter
    double eta_s_design = designIsentropicEfficiency;
    double Hp_design = compressorDesignPolytropicHead;
    double eta_p_design = compressorPolytropicEfficiency;
    double N_design = designSpeed;
    double m1 = inStream.getFlowRate("kg/sec");
    double Q_comp = 0.0;
    double m_comp = 0.0;
    double eta_s = 0.0;
    double Hp = 0.0;
    double CF_eff_comp = 1.0;
    double CF_head_comp = 1.0;

    // Newton-Raphson method for speed matching
    int maxIter = 50;
    double dN = 1.0; // finite difference step for derivative
    int iter = 0;
    double Hp2;
    double eta_p2;
    double eta_s2;
    double uc;
    double uc2;
    double qn_ratio;
    double qn_ratio2;
    do {
      double outPress = expanderOutPressure;
      StreamInterface stream2 = inStream.clone();
      SystemInterface fluid2 = stream2.getThermoSystem();
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
      CF_head_comp = 1.0;
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
      iter++;
    } while (Math.abs(W_expander - (W_compressor + W_bearing)) * 100 > 1e-3 && iter < maxIter);
    if (iter >= maxIter) {
      System.out.println("Warning: TurboExpanderCompressor did not converge.");
    }
    expanderIsentropicEfficiency = eta_s2;
    compressorPolytropicHead = Hp2;
    compressorPolytropicEfficiency = eta_p2;
    // System.out.println("expanderIsentropicEfficiency: " + expanderIsentropicEfficiency);
    // System.out.println("compressorDesignPolytropicHead: " + compressorDesignPolytropicHead);
    // System.out.println("compressorPolytropicEfficiency: " + compressorPolytropicEfficiency);
    // System.out.println("error: " + Math.abs(W_expander - (W_compressor + W_bearing)));
    // System.out.println("iter: " + iter);
    // System.out.println("eta_s: " + eta_s);
    // System.out.println("CF_eff_comp: " + CF_eff_comp);
    // System.out.println("eta_p: " + eta_p);
    // System.out.println("Expander speed: " + N);
    // System.out.println("Expander power: " + (W_expander / 1000.0));
    // System.out.println("Compressor power: " + (W_compressor / 1000.0));
    expanderSpeed = N; // store matched speed
    setSpeed(N);
    setPower(W_expander);
    setPowerExpander(W_expander);
    setPowerCompressor(W_compressor);
    setUCratioexpander(uc);
    setUCratiocompressor(uc2);
    setQNratioexpander(qn_ratio);
    setQNratiocompressor(qn_ratio2);
    setQn(N / 60.0 * Q_comp / designQn);
    Compressor tempCompressor = new Compressor("tempCompressor", compressorFeedStream);
    tempCompressor.setUsePolytropicCalc(true);
    tempCompressor.setPolytropicEfficiency(eta_p);
    tempCompressor.setPower(W_compressor);
    tempCompressor.setCalcPressureOut(true);
    tempCompressor.run();
    // System.out.println("Outlet pressure: " + tempCompressor.getOutletPressure());

    compressorOutletStream.setFluid(tempCompressor.getOutletStream().getFluid());
    setCalculationIdentifier(id);
  }

  public double getExpanderIsentropicEfficiency() {
    return expanderIsentropicEfficiency;
  }

  public void setExpanderIsentropicEfficiency(double expanderIsentropicEfficiency) {
    this.expanderIsentropicEfficiency = expanderIsentropicEfficiency;
  }

  public double getCompressorPolytropicEfficiency() {
    return compressorPolytropicEfficiency;
  }

  public void setCompressorPolytropicEfficiency(double compressorPolytropicEfficiency) {
    this.compressorPolytropicEfficiency = compressorPolytropicEfficiency;
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
    return ucCurveA * (uc - ucCurveH) * (uc - ucCurveH) + ucCurveK;
  }

  /**
   * Fit a constrained parabola: efficiency = a*(qn - h)^2 + k, with vertex at (h, k) = (1, 1).
   *
   * @param qnValues array of Q/N values
   * @param efficiencyValues array of efficiency values
   */
  public void setQNEfficiencycurve(double[] qnValues, double[] efficiencyValues) {
    double h = qnCurveH;
    double k = qnCurveK;
    int n = qnValues.length;
    if (n < 2) {
      return;
    }
    double num = 0.0;
    double denom = 0.0;
    for (int i = 0; i < n; i++) {
      double dx = qnValues[i] - h;
      num += (efficiencyValues[i] - k) * (dx);
      denom += dx * dx;
    }
    if (denom != 0.0) {
      qnCurveA = num / denom;
    } else {
      qnCurveA = 0.0;
    }
  }

  /**
   * Evaluate the fitted Q/N curve at a given qn value.
   *
   * @param qn the Q/N value
   * @return the efficiency
   */
  public double getEfficiencyFromQN(double qn) {
    return qnCurveA * (qn - qnCurveH) * (qn - qnCurveH) + qnCurveK;
  }

  /**
   * Fit a constrained parabola: head = a*(qn - h)^2 + k, with vertex at (h, k) = (1, 1).
   *
   * @param qnValues array of Q/N values
   * @param headValues array of head values
   */
  public void setQNHeadcurve(double[] qnValues, double[] headValues) {
    double h = qnHeadCurveH;
    double k = qnHeadCurveK;
    int n = qnValues.length;
    if (n < 2) {
      return;
    }
    double num = 0.0;
    double denom = 0.0;
    for (int i = 0; i < n; i++) {
      double dx = qnValues[i] - h;
      num += (headValues[i] - k) * (dx);
      denom += dx * dx;
    }
    if (denom != 0.0) {
      qnHeadCurveA = num / denom;
    } else {
      qnHeadCurveA = 0.0;
    }
  }

  /**
   * Evaluate the fitted Q/N head curve at a given qn value.
   *
   * @param qn the Q/N value
   * @return the head
   */
  public double getHeadFromQN(double qn) {
    return qnHeadCurveA * (qn - qnHeadCurveH) * (qn - qnHeadCurveH) + qnHeadCurveK;
  }

  /**
   * Calculate the IGV opening based on the current IGV position and design speed.
   * 
   * @return IGV opening (fraction of max area)
   */
  public double calcIGVOpening() {
    return IGVposition;
  }

  // --- Setters ---
  public void setImpellerDiameter(double impellerDiameter) {
    this.impellerDiameter = impellerDiameter;
  }

  public void setDesignSpeed(double designSpeed) {
    this.designSpeed = designSpeed;
  }

  public void setDesignIsentropicEfficiency(double designIsentropicEfficiency) {
    this.designIsentropicEfficiency = designIsentropicEfficiency;
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

  public void setComprosserPolytropicEfficieny(double compressorPolytropicEfficiency) {
    this.compressorPolytropicEfficiency = compressorPolytropicEfficiency;
  }

  public void setCompressorDesingPolytropicHead(double compressorDesignPolytropicHead) {
    this.compressorDesignPolytropicHead = compressorDesignPolytropicHead;
  }

  // --- Getters ---
  public double getImpellerDiameter() {
    return impellerDiameter;
  }

  public double getDesignSpeed() {
    return designSpeed;
  }

  public double getDesignIsentropicEfficiency() {
    return designIsentropicEfficiency;
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

  public double getComprosserPolytropicEfficieny() {
    return compressorPolytropicEfficiency;
  }

  public double getCompressorDesingPolytropicHead() {
    return compressorDesignPolytropicHead;
  }

}
