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
  private double designQn = 0.03328;
  private double maximumIGVArea = 1.637e4; // mm^2 or as required
  private double compressorPolytropicEfficiency = 0.81;
  private double compressorDesignPolytropicHead = 20.47; // kJ/kg

  // Stores the fitted 'a' parameter for the constrained parabola
  private double ucCurveA = 0.0;
  private double ucCurveH = 1.0; // vertex x (h)
  private double ucCurveK = 1.0; // vertex y (k)

  // Stores the fitted 'a' parameter for the constrained parabola for Q/N
  private double qnCurveA = 0.0;
  private double qnCurveH = 1.0; // vertex x (h)
  private double qnCurveK = 1.0; // vertex y (k)

  // Stores the fitted 'a' parameter for the constrained parabola for Q/N head curve
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
    expanderFeedStream = inletStream.clone(name + " exp inlet");
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
    int iter = 0;
    boolean plus = true;
    double eta_p = 0.0;
    double W_compressor = 0.0;

    double W_expander = 0.0;
    double W_bearing = 0.0;
    while (iter < 50 && N < N_max && N > N_min) {
      double D = impellerDiameter; // m, impeller diameter

      double eta_s_design = designIsentropicEfficiency;
      double Hp_design = compressorDesignPolytropicHead;
      double eta_p_design = compressorPolytropicEfficiency;
      double N_design = designSpeed;
      double W_bearing_design = bearingLossPower;
      double m1 = inStream.getFlowRate("kg/sec");
      double outPress = expanderOutPressure;
      // Clone the inlet stream and fluid
      StreamInterface stream2 = inStream.clone();
      SystemInterface fluid2 = stream2.getThermoSystem();
      fluid2.initProperties();
      double h_in = fluid2.getEnthalpy("kJ/kg");
      double s1 = fluid2.getEntropy("kJ/kgK");
      // Isentropic flash to find isentropic enthalpy change
      fluid2.setPressure(outPress, "bara");
      ThermodynamicOperations flash = new ThermodynamicOperations(fluid2);
      flash.PSflash(s1, "kJ/kgK");
      fluid2.init(3);
      double h_out = fluid2.getEnthalpy("kJ/kg");
      double h_s = (h_in - h_out) * 1000.0; // J/kg

      double Q_comp = 0.0;
      double m_comp = 0.0;
      double eta_s = 0.0;
      double Hp = 0.0;
      double CF_eff_comp = 1.0;
      double CF_head_comp = 1.0;
      double U = Math.PI * D * N / 60.0;
      double C = Math.sqrt(2.0 * h_s);
      double uc = U / C / designUC;
      double CF = getEfficiencyFromUC(uc); // use fitted UC curve
      eta_s = eta_s_design * CF;
      // Expander simulation (simplified)
      W_expander = m1 * h_s * eta_s; // simplified, real code should use Expander class
      // Simulate compressor side (simplified)
      m_comp = compressorFeedStream.getFluid().getFlowRate("kg/sec");
      Q_comp = compressorFeedStream.getFluid().getFlowRate("m3/sec");
      double qn_ratio = (Q_comp * 60.0 / N) / designQn;
      CF_eff_comp = getEfficiencyFromQN(qn_ratio);
      CF_head_comp = 1.0; // or use a head curve if available
      Hp = Hp_design * (N / N_design) * (N / N_design) * CF_head_comp;
      eta_p = CF_eff_comp * eta_p_design;
      W_compressor = m_comp * Hp / eta_p * 1000;
      W_bearing = W_bearing_design * (N / N_design) * (N / N_design);
      double error = W_expander - (W_compressor + W_bearing);

      if (error > 0.0) {
        plus = true;
      } else {
        plus = false;
      }
      if (Math.abs(error) < 500.0) {
        break;
      }
      if (plus) {
        N += 10.0;
      } else {
        N -= 10.0;
      }
      iter++;
      System.out.println("Expander speed: " + N);
      System.out.println("Expander power: " + (W_expander / 1000.0));
      System.out.println("Compressor power: " + (W_compressor / 1000.0));
    }
    System.out.println("Expander speed: " + N);
    System.out.println("Expander power: " + (W_expander / 1000.0));
    System.out.println("Compressor power: " + (W_compressor / 1000.0));
    expanderSpeed = N; // store matched speed
    Compressor tempCompressor = new Compressor("tempCompressor", compressorFeedStream);
    tempCompressor.setPolytropicEfficiency(eta_p);
    tempCompressor.setPower(W_compressor);
    tempCompressor.setCalcPressureOut(true);
    tempCompressor.run();
    System.out.println("Outlet pressure: " + tempCompressor.getOutletPressure());

    setOutletStream(tempCompressor.getOutletStream());
    setCalculationIdentifier(id);
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
