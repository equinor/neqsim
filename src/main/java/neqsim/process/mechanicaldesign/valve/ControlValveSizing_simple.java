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
public class ControlValveSizing_simple implements ControlValveSizingInterface, Serializable {


  ValveMechanicalDesign valveMechanicalDesign = null;
  private static final double KV_TO_CV_FACTOR = 1.156;
  double xT = 0.137;
  double N9 = 24.6; // IEC constant for gas
  boolean allowChoked = true;

  public ControlValveSizing_simple() {

  }

  public ControlValveSizing_simple(ValveMechanicalDesign valveMechanicalDesign) {
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
    double Kv = calcKv();
    result.put("Kv", Kv);
    result.put("Cv", Kv_to_Cv(Kv));
    return result;
  }

  /**
   * Calculates a theoretical flow coefficient using simplified formulas that assume NON-CHOKED,
   * turbulent flow in a valve that matches the pipe size.
   * 
   * WARNING: This method is a simplified approximation and will produce INACCURATE results if the
   * flow is actually choked, laminar, or if piping reducers are present. It deliberately ignores
   * the physics of choked flow. For a full, accurate calculation, use the methods in the
   * ControlValveSizing_IEC_60534_full class.
   *
   * @return A theoretical, non-choked Kv value.
   */
  public double calcKv() {
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    StreamInterface inletStream = valve.getInletStream();
    StreamInterface outletStream = valve.getOutletStream();
    SystemInterface fluid = inletStream.getFluid();

    double p1_bar = inletStream.getPressure("bara");
    double p2_bar = outletStream.getPressure("bara");

    if (p1_bar <= p2_bar) {
      return Double.POSITIVE_INFINITY; // Avoid division by zero or sqrt of negative
    }
    double deltaP_bar = p1_bar - p2_bar;

    if (valve.isGasValve()) {
      // --- THEORETICAL NON-CHOKED GAS CALCULATION ---
      double Q_m3hr = inletStream.getFlowRate("m3/hr");
      double P1_Pa = inletStream.getPressure("Pa");
      double P2_Pa = outletStream.getPressure("Pa");

      // Required gas properties for the IEC formula
      double T = fluid.getTemperature("K");
      double MW = fluid.getMolarMass("gr/mol");
      double gamma = fluid.getGamma2();
      double Z = fluid.getZ();

      // The IEC non-choked gas formula requires these factors:
      double x = (P1_Pa - P2_Pa) / P1_Pa;
      // Assuming access to the valve's xT factor is needed for the Y calculation

      double Fgamma = gamma / 1.40;
      double Y = 1.0 - x / (3.0 * Fgamma * xT); // Simplified Y, not clamped at 2/3

      // Using the standard IEC 60534 non-choked formula
      // N9 is the IEC constant for gas: 24.6
      // P1 must be in kPa for the formula
      double P1_kPa = P1_Pa / 1000.0;

      double Kv = Q_m3hr / (N9 * P1_kPa * Y) * Math.sqrt((MW * T * Z) / x);
      return Kv;

    } else {
      // --- SIMPLIFIED NON-CHOKED LIQUID CALCULATION ---
      double flow_m3hr = inletStream.getFlowRate("m3/hr");

      // Density must be relative density (Specific Gravity) for this formula
      double specificGravity = fluid.getDensity("kg/m3") / 1000.0;

      // This is the classic incompressible flow formula
      return flow_m3hr / Math.sqrt(deltaP_bar / specificGravity);
    }
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

  /**
   * Calculates the flow rate from a given adjusted Kv value and stream conditions. This method uses
   * simplified, non-choked formulas consistent with the calcKv() method.
   *
   * @param KvAdjusted The effective flow coefficient (e.g., Kv_max * %opening).
   * @param inStream The inlet stream to the valve.
   * @param outStream The outlet stream from the valve.
   * @return The calculated flow rate in [m³/s].
   */
  public double calculateMolarFlow(double KvAdjusted, StreamInterface inStream,
      StreamInterface outStream) {

    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    SystemInterface fluid = inStream.getFluid();

    double p1_bar = inStream.getPressure("bara");
    double p2_bar = outStream.getPressure("bara");

    if (p1_bar <= p2_bar) {
      return 0.0; // No flow if no pressure drop
    }
    double deltaP_bar = p1_bar - p2_bar;

    if (valve.isGasValve()) {
      // --- CORRECT, CONSISTENT GAS FLOW CALCULATION (NON-CHOKED) ---
      double P1_Pa = inStream.getPressure("Pa");

      // Required gas properties for the IEC formula
      double T = fluid.getTemperature("K");
      double MW = fluid.getMolarMass("gr/mol");
      double gamma = fluid.getGamma2();
      double Z = fluid.getZ();

      // The IEC non-choked gas formula factors
      double x = deltaP_bar * 1e5 / P1_Pa;
      double xT = this.getxT(); // Get xT from the sizing object
      double Fgamma = gamma / 1.40;
      double Y = 1.0 - x / (3.0 * Fgamma * xT); // Simplified Y, not clamped at 2/3

      // Rearranged IEC 60534 non-choked gas formula to solve for Q_m3hr
      // Q_m3hr = Kv * (N9 * P1_kPa * Y) / sqrt((MW * T * Z) / x)
      double P1_kPa = P1_Pa / 1000.0;

      double flow_m3hr = KvAdjusted * (N9 * P1_kPa * Y) / Math.sqrt((MW * T * Z) / x);

      // Convert flow from m³/h to m³/s for the return value
      return flow_m3hr / 3600.0;

    } else {
      // --- CORRECT, SIMPLIFIED LIQUID CALCULATION ---
      // Density must be relative density (Specific Gravity) for this formula
      double specificGravity = fluid.getDensity("kg/m3") / 1000.0;

      // Rearranged classic incompressible flow formula: Q = Kv * sqrt(ΔP / SG)
      double flow_m3hr = KvAdjusted * Math.sqrt(deltaP_bar / specificGravity);

      // Convert flow from m³/h to m³/s for the return value
      return flow_m3hr / 3600.0;
    }
  }

  /**
   * Calculates the required valve opening percentage for a given flow rate, using simplified,
   * non-choked formulas. This method is consistent with the calcKv() method.
   *
   * @param Q The desired volumetric flow rate in [m³/s].
   * @param Kv The maximum flow coefficient (Cv) of the valve at 100% opening.
   * @param valveOpening This parameter is unused and maintained for signature compatibility.
   * @param inletStream The stream entering the valve.
   * @param outletStream The stream leaving the valve (used for outlet pressure).
   * @return Required valve opening as a percentage (0-100).
   */
  public double calculateValveOpeningFromFlowRate(double Q, double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {

    // If the desired flow is zero or less, the valve should be closed.
    if (Q <= 0) {
      return 0.0;
    }

    // To calculate the required Kv for the desired flow 'Q', we can use our existing
    // calcKv() method. However, calcKv() uses the flow rate currently set on the
    // inlet stream object. We must temporarily set it to our desired flow 'Q' to
    // perform the calculation.

    // 1. Store the original flow rate to avoid side effects.
    double originalFlowRate = inletStream.getFlowRate("m3/sec");

    try {
      // 2. Set the stream to the desired flow rate 'Q' for the calculation.
      inletStream.setFlowRate(Q, "m3/sec");

      // 3. Calculate the Kv required for this specific flow rate.
      // This call uses the exact same logic as your manual Kv check, ensuring consistency.
      double requiredKv = this.calcKv();

      // 4. The valve opening is the ratio of the required Kv to the valve's maximum Kv.
      double calculatedOpening = (requiredKv / Kv) * 100.0;

      // 5. Clamp the result between 0 and 100%. If the opening is > 100%, it means
      // the valve is too small for the requested flow rate.
      return Math.max(0.0, Math.min(100.0, calculatedOpening));

    } finally {
      // 6. IMPORTANT: Always restore the original flow rate to the stream object.
      inletStream.setFlowRate(originalFlowRate, "m3/sec");
    }
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

  /**
   * Finds the outlet pressure for a given effective Kv and inlet stream conditions. This method
   * uses simplified, non-choked formulas consistent with the calcKv() method.
   *
   * @param KvAdjusted The effective flow coefficient (e.g., Kv_max * %opening).
   * @param inStream The inlet stream to the valve.
   * @return The calculated outlet pressure in [Pa].
   */
  public double calculateOutletPressure(double KvAdjusted, StreamInterface inStream) {
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();

    if (valve.isGasValve()) {
      // --- CORRECT, CONSISTENT GAS OUTLET PRESSURE CALCULATION ---
      // For gas, we must use a numerical search as the equation cannot be solved directly for P2.
      return calculateOutletPressureGas_simplified(KvAdjusted, inStream);
    } else {
      // --- CORRECT, SIMPLIFIED LIQUID OUTLET PRESSURE CALCULATION ---
      // Fluid properties
      double density = inStream.getFluid().getDensity("kg/m3"); // kg/m³
      // Use relative density (Specific Gravity) for the liquid Kv formula
      double specificGravity = density / 1000.0;

      // Known inlet pressure
      double P1_bar = inStream.getPressure("bara");

      // Get volumetric flow Q in [m³/h]
      double flow_m3hr = inStream.getFlowRate("m3/hr");

      // Rearranged Kv equation to solve for ΔP [bar]: ΔP = (Q / Kv)² * SG
      double dP_bar = Math.pow(flow_m3hr / KvAdjusted, 2) * specificGravity;

      // Return outlet pressure in [Pa]
      return (P1_bar - dP_bar) * 1e5;
    }
  }

  /**
   * Private helper to find the gas outlet pressure using a bisection search. This ensures the
   * calculation is the inverse of the simplified gas calcKv method.
   *
   * @param KvAdjusted The effective flow coefficient.
   * @param inStream The inlet stream.
   * @return The outlet pressure in [Pa].
   */
  private double calculateOutletPressureGas_simplified(double KvAdjusted,
      StreamInterface inStream) {
    double P1_Pa = inStream.getPressure("Pa");
    double Q_m3s = inStream.getFlowRate("m3/sec");
    SystemInterface fluid = inStream.getFluid();

    // Set search bounds for the outlet pressure P2
    double P2_low_Pa = 1.0; // Lower bound, near vacuum
    double P2_high_Pa = P1_Pa - 1e-4; // Upper bound, just below P1
    double P2_mid_Pa = P1_Pa;

    int maxIter = 50;
    double tolerance = 1e-4; // Pa tolerance

    for (int i = 0; i < maxIter; i++) {
      P2_mid_Pa = 0.5 * (P2_low_Pa + P2_high_Pa);

      // For the guessed P2 (P2_mid_Pa), what Kv would be required to pass the flow Q?
      // We use the same non-choked gas logic from calcKv() here.
      double requiredKv;

      // --- Inline logic from calcKv() ---
      double T = fluid.getTemperature("K");
      double MW = fluid.getMolarMass("gr/mol");
      double gamma = fluid.getGamma2();
      double Z = fluid.getZ();
      double x = (P1_Pa - P2_mid_Pa) / P1_Pa;

      if (x <= 0) { // No pressure drop, requires infinite Kv
        requiredKv = Double.POSITIVE_INFINITY;
      } else {
        double xT = this.getxT();
        double Fgamma = gamma / 1.40;
        double Y = 1.0 - x / (3.0 * Fgamma * xT);
        double P1_kPa = P1_Pa / 1000.0;
        double Q_m3hr = Q_m3s * 3600.0;
        requiredKv = Q_m3hr / (N9 * P1_kPa * Y) * Math.sqrt((MW * T * Z) / x);
      }
      // --- End inline logic ---

      // Bisection logic:
      // If the required Kv is smaller than the actual valve's Kv, it means our
      // guessed pressure drop is too large. To reduce the drop, we must increase P2.
      if (requiredKv < KvAdjusted) {
        P2_low_Pa = P2_mid_Pa;
      } else {
        // Otherwise, the pressure drop is too small, so we must decrease P2.
        P2_high_Pa = P2_mid_Pa;
      }

      // Check for convergence
      if (Math.abs(P2_high_Pa - P2_low_Pa) < tolerance) {
        break;
      }
    }
    return P2_mid_Pa;
  }

  private double Kv_to_Cv(double Kv) {
    return Kv * KV_TO_CV_FACTOR;
  }

  private double Cv_to_Kv(double Cv) {
    return Cv / KV_TO_CV_FACTOR;
  }

}
