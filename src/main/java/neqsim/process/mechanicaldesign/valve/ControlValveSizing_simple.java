package neqsim.process.mechanicaldesign.valve;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * Provides methods for sizing control valves for liquids and gases according to standard equations.
 */
public class ControlValveSizing_simple extends ControlValveSizing {

  public ControlValveSizing_simple() {
    super();
  }

  public ControlValveSizing_simple(ValveMechanicalDesign valveMechanicalDesign) {
    super(valveMechanicalDesign);
  }

  public double calcKv() {

    SystemInterface fluid =
        ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream().getFluid();

    Map<String, Object> result = valveMechanicalDesign.fullOutput ? new HashMap<>() : null;

    double density = fluid.getDensity("kg/m3");
    double Y = 1.0;
    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      density = fluid.getDensity("kg/m3") / 1000.0;
    } else {
      double Fgamma = ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment())
          .getInletStream().getFluid().getPhase(0).getGamma2() / 1.40;
      double x = (((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream()
          .getPressure()
          - ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getOutletStream()
              .getPressure())
          / ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream()
              .getPressure();
      double xuse = Math.min(x, xT);
      Y = 1.0 - xuse / (3.0 * Fgamma * this.xT);
    }
    return ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream()
        .getFlowRate("m3/hr")
        / Math.sqrt((((ThrottlingValve) valveMechanicalDesign.getProcessEquipment())
            .getInletStream().getPressure("bara")
            - ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getOutletStream()
                .getPressure("bara"))
            / density)
        / Y;
  }

  /**
   * Calculates the flow rate through a control valve based on the valve opening, Kv, and
   * inlet/outlet streams.
   *
   * @param Kv Flow coefficient (for 100% opening)
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Calculated flow rate (units depend on phase type)
   */
  public double calculateFlowRateFromValveOpening(double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateMolarFlow(Kv * valveOpening / 100.0, inletStream, outletStream);
  }

  public double calculateMolarFlow(double KvAdjusted, StreamInterface inStream,
      StreamInterface outStream) {
    // Convert ΔP from Pa to bar for consistency with Kv in m3/h/√bar

    double density = inStream.getFluid().getDensity("kg/m3");
    double Y = 1.0;
    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      density = inStream.getFluid().getDensity("kg/m3") / 1000.0;
    } else {
      double Fgamma = ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment())
          .getInletStream().getFluid().getPhase(0).getGamma2() / 1.40;
      double x = (((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream()
          .getPressure()
          - ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getOutletStream()
              .getPressure())
          / ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream()
              .getPressure();
      double xuse = Math.min(x, xT);
      Y = 1.0 - xuse / (3.0 * Fgamma * this.xT);
    }

    // Mass flow in kg/s
    double flow_m3_s = (KvAdjusted / 3600.0) * Y
        * Math.sqrt((inStream.getPressure("bara") - outStream.getPressure("bara")) / density);

    return flow_m3_s;
  }

  /**
   * Calculates the required valve opening fraction for a given flow rate, Kv, and inlet/outlet
   * streams.
   *
   * @param Q Flow rate (units depend on phase type)
   * @param Kv Flow coefficient (for 100% opening)
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Required valve opening fraction (0.0 - 1.0)
   */
  public double calculateValveOpeningFromFlowRate(double Q, double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return 100.0;
  }

  /**
   * Finds the outlet pressure for a given Kv, valve opening, and inlet stream.
   * 
   * @param Kv Flow coefficient (for 100% opening)
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @return Outlet pressure (unit Pa)
   */
  public double findOutletPressureForFixedKv(double Kv, double valveOpening,
      StreamInterface inletStream) {
    return calculateOutletPressure(Kv * valveOpening / 100.0, inletStream);
  }

  public double calculateOutletPressure(double KvAdjusted, StreamInterface inStream) {
    // Fluid properties
    double density = inStream.getFluid().getDensity("kg/m3"); // kg/m³
    double molarMass = inStream.getFluid().getMolarMass("kg/mol"); // kg/mol
    double molarFlow = inStream.getFlowRate("mole/sec"); // mol/s
    double Q_m3_s = (molarFlow * molarMass) / density; // m³/s

    double Y = 1.0;
    if (!((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).isGasValve()) {
      density = inStream.getFluid().getDensity("kg/m3") / 1000.0;
    } else {
      double Fgamma = ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment())
          .getInletStream().getFluid().getPhase(0).getGamma2() / 1.40;
      double x = (((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream()
          .getPressure()
          - ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getOutletStream()
              .getPressure())
          / ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream()
              .getPressure();
      double xuse = Math.min(x, xT);
      Y = 1.0 - xuse / (3.0 * Fgamma * this.xT);
    }
    // Rearranged Kv equation to get ΔP [bar]
    double dP_bar = Math.pow((Q_m3_s * 3600.0) / (KvAdjusted * Y), 2) * density;

    // Return outlet pressure [Pa]
    return (inStream.getPressure("bara") - dP_bar) * 1e5;
  }

}
