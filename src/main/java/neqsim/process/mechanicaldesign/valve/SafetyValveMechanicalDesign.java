package neqsim.process.mechanicaldesign.valve;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.SafetyValve;

/**
 * Mechanical design for safety valves based on API 520 gas sizing.
 *
 * @author esol
 */
public class SafetyValveMechanicalDesign extends ValveMechanicalDesign {
  private static final long serialVersionUID = 1L;
  private double orificeArea = 0.0; // m^2

  /**
   * <p>
   * Constructor for SafetyValveMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public SafetyValveMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /**
   * Calculates the required orifice area for gas/vapor service according to API 520.
   *
   * @param massFlow mass flow rate at relieving conditions [kg/s]
   * @param relievingPressure absolute relieving pressure [Pa]
   * @param relievingTemperature relieving temperature [K]
   * @param z gas compressibility factor [-]
   * @param molecularWeight molecular weight [kg/mol]
   * @param k heat capacity ratio (Cp/Cv) [-]
   * @param kd discharge coefficient [-]
   * @param kb back pressure correction factor [-]
   * @param kw installation correction factor [-]
   * @return required flow area [m^2]
   */
  public double calcGasOrificeAreaAPI520(double massFlow, double relievingPressure,
      double relievingTemperature, double z, double molecularWeight, double k, double kd, double kb,
      double kw) {
    double R = 8.314; // J/(mol K)
    double C = Math.sqrt(k) * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (2.0 * (k - 1.0)));
    double numerator = massFlow * Math.sqrt(z * R * relievingTemperature / molecularWeight);
    double denominator = kd * kb * kw * relievingPressure * C;
    return numerator / denominator;
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    SafetyValve valve = (SafetyValve) getProcessEquipment();
    StreamInterface inlet = valve.getInletStream();

    double massFlow = inlet.getThermoSystem().getFlowRate("kg/sec");
    double relievingPressure = valve.getPressureSpec() * 1e5; // convert bar to Pa
    double relievingTemperature = inlet.getTemperature();
    double z = inlet.getThermoSystem().getZ();
    double mw = inlet.getThermoSystem().getMolarMass(); // kg/mol
    double k = inlet.getThermoSystem().getGamma();

    double kd = 0.975;
    double kb = 1.0;
    double kw = 1.0;

    orificeArea = calcGasOrificeAreaAPI520(massFlow, relievingPressure, relievingTemperature, z, mw,
        k, kd, kb, kw);
  }

  /**
   * Returns the calculated orifice area.
   *
   * @return area [m^2]
   */
  public double getOrificeArea() {
    return orificeArea;
  }
}
