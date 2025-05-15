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
  private double gearRatio = 1.0;
  private double expanderSpeed = 1000.0; // rpm, initial guess
  private double compressorSpeed = 1000.0;

  private double expanderIsentropicEfficiencyDesign = 0.85;
  private double expanderEfficiency = 0.85;

  private double expanderEfficiencyDesign = 0.85;
  private double compressorPolytropicEfficiency = 0.85;
  private double compressorPolytropicHead = 20.47; // kJ/kg

  StreamInterface compressorFeedStream = null;
  StreamInterface otletStream = null;

  public StreamInterface getCompressorFeedStream() {
    return compressorFeedStream;
  }

  public void setCompressorFeedStream(StreamInterface compressorFeedStream) {
    this.compressorFeedStream = compressorFeedStream;
  }

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

  public TurboExpanderCompressor(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  public void setIGVposition(double IGVposition) {
    this.IGVposition = IGVposition;
  }

  public void setCompressorRequiredPower(double power) {
    this.compressorRequiredPower = power;
  }

  public void setExpanderOutPressure(double pressure) {
    this.expanderOutPressure = pressure;
  }

  @Override
  public void run(UUID id) {
    // Java implementation of the find_speed logic (simplified, using available neqsim API)
    // Assumes: inStream is the expander inlet stream
    // Design parameters and curve fits should be set before calling run()
    double N = expanderSpeed; // initial guess for speed (rpm)
    double N_max = 9000.0;
    double N_min = 1000.0;
    boolean plus = true;
    double D = 424.8 / 1000.0; // m, impeller diameter
    double uc_design = ucCurveH; // usually 1.0
    double qn_design = qnCurveH; // usually 1.0
    double eta_s_design = expanderIsentropicEfficiencyDesign;
    double Hp_design = compressorPolytropicHead;
    double eta_p_design = compressorPolytropicEfficiency;
    double N_design = expanderSpeed; // or set externally
    double W_bearing_design = bearingLossPower;
    double m1 = inStream.getFlowRate("kg/sec");
    double outPress = expanderOutPressure;
    // Clone the inlet stream and fluid
    StreamInterface stream2 = inStream.clone();
    SystemInterface fluid2 = stream2.getThermoSystem();
    fluid2.initProperties();
    double h_in = fluid2.getEnthalpy("kJ/kg");
    double s1 = fluid2.getEntropy("kJ/kgK");
    double T1 = fluid2.getTemperature("C");
    double P1 = fluid2.getPressure("bara");

    // Isentropic flash to find isentropic enthalpy change
    fluid2.setPressure(outPress, "bara");

    ThermodynamicOperations flash = new ThermodynamicOperations(fluid2);
    flash.PSflash(s1, "kJ/kgK");

    fluid2.init(3);
    double h_out = fluid2.getEnthalpy("kJ/kg");
    double T2s = fluid2.getTemperature("C");
    double h_s = (h_in - h_out) * 1000.0; // J/kg

    // Main iteration to match expander and compressor power
    double W_expander = 0.0, W_compressor = 0.0, W_bearing = 0.0;
    double Q_comp = 0.0, m_comp = 0.0, eta_s = 0.0, eta_p = 0.0, Hp = 0.0;
    double CF_eff_comp = 1.0, CF_head_comp = 1.0;
    int iter = 0;
    N = expanderSpeed;
    while (iter < 50 && N < N_max && N > N_min) {
      double U = Math.PI * D * N / 60.0;
      double C = Math.sqrt(2.0 * h_s);
      double uc = U / C / uc_design;
      double CF = getEfficiencyFromUC(uc); // use fitted UC curve
      eta_s = eta_s_design * CF;

      // Expander simulation (simplified)
      // Here, you would use a real neqsim Expander object and set efficiency, etc.
      W_expander = m1 * h_s * eta_s; // simplified, real code should use Expander class

      // Simulate compressor side (simplified)
      // Assume Q_comp and m_comp are proportional to m1 for this example

      m_comp = compressorFeedStream.getFluid().getFlowRate("kg/sec");
      Q_comp = compressorFeedStream.getFluid().getFlowRate("m3/sec");

      double qn_ratio = (Q_comp * 60.0 / N) / qn_design;
      CF_eff_comp = getEfficiencyFromQN(qn_ratio);
      CF_head_comp = 1.0; // or use a head curve if available
      Hp = Hp_design * (N / N_design) * (N / N_design) * CF_head_comp;
      eta_p = CF_eff_comp * eta_p_design;
      W_compressor = m_comp * Hp / eta_p;
      W_bearing = W_bearing_design * (N / N_design) * (N / N_design);

      double error = W_expander - (W_compressor + W_bearing);
      if (Math.abs(error) < 500.0)
        break;
      if (plus) {
        N += 10.0;
      } else {
        N -= 10.0;
      }
      iter++;
    }
    // Store results in class fields if needed
    this.expanderSpeed = N;
    this.compressorSpeed = N;

    Compressor tempCOmpressor = new Compressor("tempCompressor", compressorFeedStream);
    tempCOmpressor.setPolytropicEfficiency(eta_p);
    tempCOmpressor.setPower(W_compressor);
    tempCOmpressor.setCalcPressureOut(true);
    tempCOmpressor.run();

    setOutletStream(tempCOmpressor.getOutletStream());


    // Optionally, set output stream state here
    setCalculationIdentifier(id);
  }

  private double applyIGVpressureDrop(double Pin) {
    // Simple Cv-like pressure drop: linear loss based on opening
    double k = 0.05; // tuning parameter
    return Pin * (1.0 - k * (1.0 - IGVposition));
  }

  private double correctEfficiency(double speed) {
    // Placeholder correction: simple linear relation
    double baseSpeed = 10000.0;
    double corr = 1.0 - 0.00001 * Math.abs(speed - baseSpeed);
    return expanderEfficiencyDesign * corr;
  }

  public double getTurboSpeed() {
    return turboSpeed;
  }

  // Add this method to the TurboExpanderCompressor class

  public void setDesignParameters(double designSpeed, double designIsentropicEfficiency,
      double designUC, double bearingloww, double impeller_diamater,
      double compressor_polytropicEfficiency, double compressor_polytropichead) {
    // TODO: Implement the logic to set these parameters in the class
    // For now, you can assign them to fields or just leave this as a stub
  }

  /**
   * Fit a constrained parabola: efficiency = a*(uc - h)^2 + k, with vertex at (h, k) = (1, 1)
   * 
   * @param ucValues array of uc values
   * @param efficiencyValues array of efficiency values
   */
  public void setUCcurve(double[] ucValues, double[] efficiencyValues) {
    // Fit only parameter 'a' for y = a*(x-h)^2 + k, with h=1, k=1
    double h = ucCurveH;
    double k = ucCurveK;
    int n = ucValues.length;
    if (n < 2)
      return;
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
   * Fit a constrained parabola: efficiency = a*(qn - h)^2 + k, with vertex at (h, k) = (1, 1)
   * 
   * @param qnValues array of Q/N values
   * @param efficiencyValues array of efficiency values
   */
  public void setQNEfficiencycurve(double[] qnValues, double[] efficiencyValues) {
    double h = qnCurveH;
    double k = qnCurveK;
    int n = qnValues.length;
    if (n < 2)
      return;
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
   * Fit a constrained parabola: head = a*(qn - h)^2 + k, with vertex at (h, k) = (1, 1)
   * 
   * @param qnValues array of Q/N values
   * @param headValues array of head values
   */
  public void setQNHeadcurve(double[] qnValues, double[] headValues) {
    double h = qnHeadCurveH;
    double k = qnHeadCurveK;
    int n = qnValues.length;
    if (n < 2)
      return;
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

  public double calcIGVOpening() {
    // Calculate the IGV opening based on the current speed and design speed
    double opening = (expanderSpeed / expanderSpeed) * IGVposition;
    return opening;
  }

}
