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

  // Added missing constants
  private static final double SECONDS_PER_HOUR = 3600.0;
  private static final double MAX_VALVE_OPENING_PERCENTAGE = 100.0;

  public ValveMechanicalDesign getValveMechanicalDesign() {
    return valveMechanicalDesign;
  }

  private static final double KV_TO_CV_FACTOR = 1.156;
  private static final int MAX_BISECTION_ITERATIONS = 100;

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
  public Map<String, Object> calcValveSize(double percentOpening) {

    Map<String, Object> result = valveMechanicalDesign.fullOutput ? new HashMap<>() : null;
    double Kv = calcKv(percentOpening);
    result.put("choked", false);
    result.put("Kv", Kv);
    result.put("Cv", Kv_to_Cv(Kv));
    result.put("Cg", Kv_to_Cv(Kv) * 1360);
    return result;
  }

  public double calcKv(double percentOpening) {

    SystemInterface fluid =
        ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream().getFluid();

    Map<String, Object> result = valveMechanicalDesign.fullOutput ? new HashMap<>() : null;

    double density = fluid.getDensity("kg/m3");
    double Y = 1.0;

    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      density = fluid.getDensity("kg/m3") / 1000.0;
    }


    return ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream()
        .getFlowRate("m3/hr")
        / Math.sqrt((((ThrottlingValve) valveMechanicalDesign.getProcessEquipment())
            .getInletStream().getPressure("bara")
            - ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getOutletStream()
                .getPressure("bara"))
            / density)
        / valveMechanicalDesign.getValveCharacterizationMethod().getOpeningFactor(percentOpening);
  }

  /**
   * Calculates the flow rate through a control valve based on the valve opening, Kv, and
   * inlet/outlet streams.
   *
   * @param actualKv Flow coefficient (for 100% opening)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Calculated flow rate (units depend on phase type)
   */
  public double calculateFlowRateFromValveOpening(double actualKv, StreamInterface inletStream,
      StreamInterface outletStream) {
    return calculateMolarFlow(actualKv, inletStream, outletStream);
  }

  public double calculateMolarFlow(double actualKv, StreamInterface inStream,
      StreamInterface outStream) {
    // Convert ΔP from Pa to bar for consistency with Kv in m3/h/√bar

    double density = inStream.getFluid().getDensity("kg/m3");
    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      density = inStream.getFluid().getDensity("kg/m3") / 1000.0;
    }

    // Mass flow in kg/s
    double flow_m3_s = (actualKv / 3600.0)
        * Math.sqrt((inStream.getPressure("bara") - outStream.getPressure("bara")) / density);

    return flow_m3_s;
  }

  /**
   * Calculates the required valve opening fraction for a given flow rate, Kv, and inlet/outlet
   * streams.
   *
   * @param Q Flow rate (units depend on phase type)
   * @param actualKv Flow coefficient (for 100% opening)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Required valve opening fraction (0.0 - 1.0)
   */
  public double calculateValveOpeningFromFlowRate(double Q, double actualKv,
      StreamInterface inletStream, StreamInterface outletStream) {
    if (actualKv <= 0) {
      return 0.0;
    }

    // Determine fluid density [kg/m3] for gas and relative density for liquids
    double density = inletStream.getFluid().getDensity("kg/m3");
    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      density = density / 1000.0; // use relative density for liquids
    }

    // Pressure drop across the valve [bar]
    double dP = inletStream.getPressure("bara") - outletStream.getPressure("bara");
    if (dP <= 0) {
      return 0.0;
    }

    // Convert requested flow to m3/h to match Kv units
    double Q_m3h = Q * SECONDS_PER_HOUR;

    // Required Kv for the requested flow
    double requiredKv = Q_m3h / Math.sqrt(dP / density);

    // Opening factor relative to full-open Kv
    double requiredOpeningFactor = requiredKv / actualKv;
    requiredOpeningFactor = Math.max(0.0, Math.min(1.0, requiredOpeningFactor));

    // Map opening factor to percent opening using valve characteristic
    double low = 0.0;
    double high = MAX_VALVE_OPENING_PERCENTAGE;
    double percentOpening = 0.0;
    for (int i = 0; i < MAX_BISECTION_ITERATIONS; i++) {
      percentOpening = (low + high) / 2.0;
      double factor =
          valveMechanicalDesign.getValveCharacterizationMethod().getOpeningFactor(percentOpening);
      if (factor < requiredOpeningFactor) {
        low = percentOpening;
      } else {
        high = percentOpening;
      }
    }

    return percentOpening;
  }

  /**
   * Finds the outlet pressure for a given Kv, valve opening, and inlet stream.
   * 
   * @param actualKv Flow coefficient (for 100% opening)
   * @param inletStream Inlet stream to the valve
   * @return Outlet pressure (unit Pa)
   */
  public double findOutletPressureForFixedKv(double actualKv, StreamInterface inletStream) {
    return calculateOutletPressure(actualKv, inletStream);
  }

  public double calculateOutletPressure(double KvAdjusted, StreamInterface inStream) {
    // Fluid properties
    double density = inStream.getFluid().getDensity("kg/m3"); // kg/m³
    double molarMass = inStream.getFluid().getMolarMass("kg/mol"); // kg/mol
    double molarFlow = inStream.getFlowRate("mole/sec"); // mol/s
    double Q_m3_s = (molarFlow * molarMass) / density; // m³/s
    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      density = density / 1000.0; // use relative density for liquids
    }

    // Rearranged Kv equation to get ΔP [bar]
    double dP_bar = Math.pow((Q_m3_s * 3600.0) / KvAdjusted, 2) * density;

    // Return outlet pressure [Pa]
    return (inStream.getPressure("bara") - dP_bar) * 1e5;
  }

  private double Kv_to_Cv(double Kv) {
    return Kv * KV_TO_CV_FACTOR;
  }

  private double Cv_to_Kv(double Cv) {
    return Cv / KV_TO_CV_FACTOR;
  }

}
