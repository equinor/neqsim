package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * Provides methods for sizing control valves for liquids and gases according to standard equations.
 *
 * @author esol
 */
public class ControlValveSizing implements ControlValveSizingInterface, Serializable {

  ValveMechanicalDesign valveMechanicalDesign = null;


  /**
   * <p>
   * Getter for the field <code>valveMechanicalDesign</code>.
   * </p>
   *
   * @return a {@link neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign} object
   */
  public ValveMechanicalDesign getValveMechanicalDesign() {
    return valveMechanicalDesign;
  }

  private static final double KV_TO_CV_FACTOR = 1.156;
  private static final double SECONDS_PER_HOUR = 3600.0;
  private static final int MAX_BISECTION_ITERATIONS = 100;
  private static final double MAX_VALVE_OPENING_PERCENTAGE = 100.0;

  double xT = 0.137;
  boolean allowChoked = true;

  /**
   * <p>
   * Constructor for ControlValveSizing.
   * </p>
   */
  public ControlValveSizing() {}

  /**
   * <p>
   * Constructor for ControlValveSizing.
   * </p>
   *
   * @param valveMechanicalDesign a
   *        {@link neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign} object
   */
  public ControlValveSizing(ValveMechanicalDesign valveMechanicalDesign) {
    this.valveMechanicalDesign = valveMechanicalDesign;
  }

  // === Getters and Setters for Valve Parameters ===
  /**
   * <p>
   * Getter for the field <code>xT</code>.
   * </p>
   *
   * @return a double
   */
  public double getxT() {
    return xT;
  }

  /** {@inheritDoc} */
  public void setxT(double xT) {
    this.xT = xT;
  }

  /**
   * <p>
   * isAllowChoked.
   * </p>
   *
   * @return a boolean
   */
  public boolean isAllowChoked() {
    return allowChoked;
  }

  /** {@inheritDoc} */
  public void setAllowChoked(boolean allowChoked) {
    this.allowChoked = allowChoked;
  }

  /** {@inheritDoc} */
  public Map<String, Object> calcValveSize(double percentOpening) {
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    Map<String, Object> result = valveMechanicalDesign.fullOutput ? new HashMap<>() : null;
    double Kv = calcKv(percentOpening);

    // Check for choked flow in gas valves
    boolean choked = false;
    if (valve.isGasValve()) {
      double P1 = valve.getInletStream().getPressure("bara");
      double P2 = valve.getOutletStream().getPressure("bara");
      double x = (P1 - P2) / P1;
      double gamma = valve.getInletStream().getFluid().getGamma2();
      double Fgamma = gamma / 1.40;
      choked = x >= Fgamma * xT;
    }

    result.put("choked", choked);
    result.put("Kv", Kv);
    result.put("Cv", Kv_to_Cv(Kv));
    result.put("Cg", Kv_to_Cv(Kv) * 1360);
    return result;
  }

  /**
   * <p>
   * calcKv.
   * </p>
   *
   * <p>
   * Calculates valve flow coefficient Kv using simplified formulas:
   * </p>
   * <ul>
   * <li>For liquids: Kv = Q / sqrt(deltaP / SG) where SG = rho/1000</li>
   * <li>For gases: Uses simplified IEC 60534 formula with expansion factor</li>
   * </ul>
   *
   * @param percentOpening valve opening percentage (0-100)
   * @return the calculated Kv value
   */
  public double calcKv(double percentOpening) {

    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    SystemInterface fluid = valve.getInletStream().getFluid();

    fluid.initPhysicalProperties("density");

    double P1 = valve.getInletStream().getPressure("bara");
    double P2 = valve.getOutletStream().getPressure("bara");
    double deltaP = P1 - P2;
    double openingFactor =
        valveMechanicalDesign.getValveCharacterizationMethod().getOpeningFactor(percentOpening);

    double Kv;

    if (!valve.isGasValve()) {
      // Liquid formula: Kv = Q * sqrt(SG / deltaP)
      // where SG = specific gravity = rho / 1000
      double density = fluid.getDensity("kg/m3") / 1000.0; // Convert to SG
      double flowM3hr = valve.getInletStream().getFlowRate("m3/hr");
      Kv = flowM3hr / Math.sqrt(deltaP / density);
    } else {
      // Simplified gas formula based on IEC 60534:
      // Kv = Q / (N9 * P1 * Y) * sqrt(M * T * Z / x)
      // N9 = 24.6 for actual flow in m3/h, P in kPa, T in K
      double N9 = 24.6; // IEC 60534 constant for actual m3/h, P in kPa, T in K
      // Use actual volumetric flow at inlet conditions (same as IEC 60534)
      double flowM3sec = fluid.getFlowRate("m3/sec");
      double flowM3hr = flowM3sec * 3600.0;
      double T = fluid.getTemperature(); // Kelvin
      double MW = fluid.getMolarMass("gr/mol");
      double Z = fluid.getZ();
      double gamma = fluid.getGamma2();

      // P1 in kPa (IEC 60534 uses kPa)
      double P1_kPa = P1 * 100.0;
      double P2_kPa = P2 * 100.0;
      double dP_kPa = P1_kPa - P2_kPa;
      double x = dP_kPa / P1_kPa; // pressure ratio
      double Fgamma = gamma / 1.40;
      double xChoked = Fgamma * xT;

      // Check for choked flow
      boolean choked = x >= xChoked;
      double xEffective = x;
      if (choked && allowChoked) {
        xEffective = xChoked; // Limit to choked condition
      }

      // Expansion factor Y (simplified)
      double Y = Math.max(1.0 - xEffective / (3.0 * Fgamma * xT), 2.0 / 3.0);

      // Gas Kv formula (IEC 60534 simplified)
      // For choked flow, use xT*Fgamma instead of x
      if (choked && allowChoked) {
        Kv = flowM3hr / (N9 * P1_kPa * Y) * Math.sqrt(MW * T * Z / (xT * Fgamma));
      } else {
        Kv = flowM3hr / (N9 * P1_kPa * Y) * Math.sqrt(MW * T * Z / x);
      }
    }

    return Kv / openingFactor;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Calculates the flow rate through a control valve based on the valve opening, Kv, and
   * inlet/outlet streams.
   * </p>
   */
  public double calculateFlowRateFromValveOpening(double actualKv, StreamInterface inletStream,
      StreamInterface outletStream) {
    return calculateMolarFlow(actualKv, inletStream, outletStream);
  }

  /**
   * <p>
   * calculateMolarFlow.
   * </p>
   *
   * <p>
   * Calculates flow rate from valve Kv using IEC 60534 formulas.
   * </p>
   *
   * @param actualKv the valve flow coefficient at the current opening
   * @param inStream inlet stream
   * @param outStream outlet stream
   * @return volumetric flow rate in m3/s
   */
  public double calculateMolarFlow(double actualKv, StreamInterface inStream,
      StreamInterface outStream) {
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    SystemInterface fluid = inStream.getFluid();

    double P1 = inStream.getPressure("bara");
    double P2 = outStream.getPressure("bara");
    double deltaP = P1 - P2;

    double flow_m3_s;

    if (!valve.isGasValve()) {
      // Liquid formula: Q = Kv * sqrt(deltaP / SG)
      double density = fluid.getDensity("kg/m3") / 1000.0; // Convert to SG
      double flow_m3_hr = actualKv * Math.sqrt(deltaP / density);
      flow_m3_s = flow_m3_hr / 3600.0;
    } else {
      // IEC 60534 gas formula (inverse of calcKv):
      // Q = Kv * N9 * P1 * Y / sqrt(M * T * Z / x)
      double N9 = 24.6;
      double T = fluid.getTemperature();
      double MW = fluid.getMolarMass("gr/mol");
      double Z = fluid.getZ();
      double gamma = fluid.getGamma2();

      double P1_kPa = P1 * 100.0;
      double P2_kPa = P2 * 100.0;
      double dP_kPa = P1_kPa - P2_kPa;
      double x = dP_kPa / P1_kPa;
      double Fgamma = gamma / 1.40;
      double xChoked = Fgamma * xT;

      boolean choked = x >= xChoked;
      double xEffective = choked && allowChoked ? xChoked : x;

      double Y = Math.max(1.0 - xEffective / (3.0 * Fgamma * xT), 2.0 / 3.0);

      double denominator;
      if (choked && allowChoked) {
        denominator = Math.sqrt(MW * T * Z / (xT * Fgamma));
      } else {
        denominator = Math.sqrt(MW * T * Z / x);
      }

      double flow_m3_hr = actualKv * N9 * P1_kPa * Y / denominator;
      flow_m3_s = flow_m3_hr / 3600.0;
    }

    return flow_m3_s;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Calculates the required valve opening fraction for a given flow rate, Kv, and inlet/outlet
   * streams.
   * </p>
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
   * {@inheritDoc}
   * <p>
   * Finds the outlet pressure for a given Kv, valve opening, and inlet stream.
   * </p>
   */
  public double findOutletPressureForFixedKv(double actualKv, StreamInterface inletStream) {
    return calculateOutletPressure(actualKv, inletStream);
  }

  /**
   * <p>
   * calculateOutletPressure.
   * </p>
   *
   * <p>
   * Calculates outlet pressure for a given Kv and inlet conditions using IEC 60534 formulas. Uses
   * bisection search for gas valves due to the complex relationship between Kv and pressure.
   * </p>
   *
   * @param KvAdjusted the adjusted Kv (at current valve opening)
   * @param inStream inlet stream
   * @return outlet pressure in Pa
   */
  public double calculateOutletPressure(double KvAdjusted, StreamInterface inStream) {
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    SystemInterface fluid = inStream.getFluid();

    double P1 = inStream.getPressure("bara");
    double Q_m3_s = fluid.getFlowRate("m3/sec");

    if (!valve.isGasValve()) {
      // Liquid formula: P2 = P1 - (Q / Kv)^2 / SG
      double density = fluid.getDensity("kg/m3") / 1000.0; // Convert to SG
      double Q_m3_hr = Q_m3_s * 3600.0;
      double dP_bar = Math.pow(Q_m3_hr / KvAdjusted, 2) * density;
      return (P1 - dP_bar) * 1e5;
    } else {
      // Gas valve: use bisection to find P2 such that calcKv gives the target Kv
      // This is needed because the gas Kv formula is not easily invertible
      double N9 = 24.6;
      double T = fluid.getTemperature();
      double MW = fluid.getMolarMass("gr/mol");
      double Z = fluid.getZ();
      double gamma = fluid.getGamma2();
      double Fgamma = gamma / 1.40;
      double Q_m3_hr = Q_m3_s * 3600.0;

      // Bisection search for P2
      double P2_low = 0.1; // bara
      double P2_high = P1 - 0.001; // Just below P1
      double P2_mid = (P2_low + P2_high) / 2.0;
      double tolerance = 1e-6;
      int maxIter = 100;

      for (int i = 0; i < maxIter; i++) {
        P2_mid = (P2_low + P2_high) / 2.0;

        // Calculate Kv at this P2 using IEC 60534 formula
        double P1_kPa = P1 * 100.0;
        double P2_kPa = P2_mid * 100.0;
        double dP_kPa = P1_kPa - P2_kPa;
        double x = dP_kPa / P1_kPa;
        double xChoked = Fgamma * xT;

        boolean choked = x >= xChoked;
        double xEffective = choked && allowChoked ? xChoked : x;
        double Y = Math.max(1.0 - xEffective / (3.0 * Fgamma * xT), 2.0 / 3.0);

        double calcKv;
        if (choked && allowChoked) {
          calcKv = Q_m3_hr / (N9 * P1_kPa * Y) * Math.sqrt(MW * T * Z / (xT * Fgamma));
        } else {
          calcKv = Q_m3_hr / (N9 * P1_kPa * Y) * Math.sqrt(MW * T * Z / x);
        }

        if (Math.abs(calcKv - KvAdjusted) / KvAdjusted < tolerance) {
          break;
        }

        // If calculated Kv is too high, we need lower P2 (more pressure drop)
        // Higher Kv means the valve is "too small" for current conditions
        // If calculated Kv is too low, we need higher P2 (less pressure drop)
        if (calcKv > KvAdjusted) {
          P2_high = P2_mid;
        } else {
          P2_low = P2_mid;
        }
      }

      return P2_mid * 1e5; // Convert bara to Pa
    }
  }

  private double Kv_to_Cv(double Kv) {
    return Kv * KV_TO_CV_FACTOR;
  }

  private double Cv_to_Kv(double Cv) {
    return Cv / KV_TO_CV_FACTOR;
  }
}
