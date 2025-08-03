package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * Provides methods for sizing control valves for liquids and gases according to standard equations.
 */
public class ControlValveSizing implements ControlValveSizingInterface, Serializable {


  ValveMechanicalDesign valveMechanicalDesign = null;
  private static final double KV_TO_CV_FACTOR = 1.156;
  double xT = 0.137;
  boolean allowChoked = true;

  public ControlValveSizing() {

  }

  public ControlValveSizing(ValveMechanicalDesign valveMechanicalDesign) {
    this.valveMechanicalDesign = valveMechanicalDesign;
  }

  // === Getters and Setters for Valve Parameters ===
  public double getxT() {
    return xT;
  }

  public void setxT(double xT) {
    this.xT = xT;
  }

  public boolean isAllowChoked() {
    return allowChoked;
  }

  public void setAllowChoked(boolean allowChoked) {
    this.allowChoked = allowChoked;
  }

  /**
   * Calculates the valve size based on the fluid properties and operating conditions.
   *
   * @return a map containing the calculated valve size and related parameters. If fullOutput is
   *         false, the map will be null.
   */
  public Map<String, Object> calcValveSize() {

    Map<String, Object> result = valveMechanicalDesign.fullOutput ? new HashMap<>() : null;
    double Cv = calcCv();
    result.put("choked", false);
    result.put("Kv", Cv);
    result.put("Cv", Kv_to_Cv(Cv));
    return result;
  }

  public double calcCv() {

    SystemInterface fluid = valveMechanicalDesign.getProcessEquipment().getFluid();

    Map<String, Object> result = valveMechanicalDesign.fullOutput ? new HashMap<>() : null;

    double density = fluid.getDensity("kg/m3");
    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      density = fluid.getDensity("kg/m3") / 1000.0;
    }

    return ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream()
        .getFlowRate("m3/hr")
        / Math.sqrt((((ThrottlingValve) valveMechanicalDesign.getProcessEquipment())
            .getInletStream().getPressure("bara")
            - ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getOutletStream()
                .getPressure("bara"))
            / density);
  }

  /**
   * Calculates the flow rate through a control valve based on the valve opening, Cv, and
   * inlet/outlet streams.
   *
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Calculated flow rate (units depend on phase type)
   */
  public double calculateFlowRateFromValveOpening(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateMolarFlow(Cv * valveOpening / 100.0, inletStream, outletStream);
  }

  public double calculateMolarFlow(double CvAdjusted, StreamInterface inStream,
      StreamInterface outStream) {
    // Convert ΔP from Pa to bar for consistency with Cv in m3/h/√bar

    double density = inStream.getFluid().getDensity("kg/m3");
    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      density = inStream.getFluid().getDensity("kg/m3") / 1000.0;
    }

    // Mass flow in kg/s
    double flow_m3_s = (CvAdjusted / 3600.0)
        * Math.sqrt((inStream.getPressure("bara") - outStream.getPressure("bara")) / density);

    return flow_m3_s;
  }

  /**
   * Calculates the required valve opening fraction for a given flow rate, Cv, and inlet/outlet
   * streams.
   *
   * @param Q Flow rate (units depend on phase type)
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Required valve opening fraction (0.0 - 1.0)
   */
  public double calculateValveOpeningFromFlowRate(double Q, double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return 100.0;
  }

  /**
   * Finds the outlet pressure for a given Cv, valve opening, and inlet stream.
   * 
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @return Outlet pressure (units depend on phase type)
   */
  public double findOutletPressureForFixedCv(double Cv, double valveOpening,
      StreamInterface inletStream) {
    return calculateOutletPressure(Cv * valveOpening / 100.0, inletStream);
  }

  public double calculateOutletPressure(double CvAdjusted, StreamInterface inStream) {
    // Fluid properties
    double density = inStream.getFluid().getDensity("kg/m3"); // kg/m³
    double densityKv = density; // for Kv formula
    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      densityKv = density / 1000.0; // use relative density for liquids
    }

    double molarMass = inStream.getFluid().getMolarMass("kg/mol"); // kg/mol

    // Known inlet pressure
    double P1_bar = inStream.getPressure("bara");

    // Calculate volumetric flow Q [m³/s] from molar flow
    double molarFlow = inStream.getFlowRate("mole/sec"); // mol/s
    double Q_m3_s = (molarFlow * molarMass) / density; // m³/s

    // Rearranged Kv equation to get ΔP [bar]
    double dP_bar = Math.pow((Q_m3_s * 3600.0) / CvAdjusted, 2) * densityKv;

    // Return outlet pressure [bar]
    return P1_bar - dP_bar;
  }

  private double Kv_to_Cv(double Kv) {
    return Kv * KV_TO_CV_FACTOR;
  }

  private double Cv_to_Kv(double Cv) {
    return Cv / KV_TO_CV_FACTOR;
  }

}
