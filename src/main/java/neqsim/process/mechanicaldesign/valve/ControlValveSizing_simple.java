package neqsim.process.mechanicaldesign.valve;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * Provides simplified methods for sizing production chokes and control valves.
 *
 * <p>
 * This class implements production choke sizing formulas commonly used in oil and gas production.
 * For gas flow, it uses a modified Perkins/Sachdeva approach with proper compressibility handling.
 * For liquid flow, it uses the standard Kv formula.
 * </p>
 *
 * <p>
 * The gas sizing formula is:
 * </p>
 * 
 * <pre>
 * Cv = Q_std / (27.66 * P1 * Y * sqrt(1 / (MW * T * Z)))
 * </pre>
 * 
 * <p>
 * where Q_std is standard volumetric flow (Sm³/h), P1 is inlet pressure (bara), Y is expansion
 * factor, MW is molecular weight (g/mol), T is temperature (K), and Z is compressibility factor.
 * </p>
 *
 * @author esol
 */
public class ControlValveSizing_simple extends ControlValveSizing {

  /** Discharge coefficient for production chokes (typical value). */
  private double Cd = 0.85;

  /**
   * <p>
   * Constructor for ControlValveSizing_simple.
   * </p>
   */
  public ControlValveSizing_simple() {
    super();
  }

  /**
   * <p>
   * Constructor for ControlValveSizing_simple.
   * </p>
   *
   * @param valveMechanicalDesign a
   *        {@link neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign} object
   */
  public ControlValveSizing_simple(ValveMechanicalDesign valveMechanicalDesign) {
    super(valveMechanicalDesign);
  }

  /**
   * Get the discharge coefficient.
   *
   * @return discharge coefficient Cd
   */
  public double getCd() {
    return Cd;
  }

  /**
   * Set the discharge coefficient.
   *
   * @param Cd discharge coefficient (typically 0.6-0.95)
   */
  public void setCd(double Cd) {
    this.Cd = Cd;
  }

  /** {@inheritDoc} */
  @Override
  public double calcKv(double percentOpening) {
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    SystemInterface fluid = valve.getInletStream().getFluid();

    double P1 = valve.getInletStream().getPressure("bara");
    double P2 = valve.getOutletStream().getPressure("bara");
    double deltaP = P1 - P2;
    double openingFactor =
        valveMechanicalDesign.getValveCharacterizationMethod().getOpeningFactor(percentOpening);

    double Kv;

    if (!valve.isGasValve()) {
      // Liquid: standard Kv formula
      // Kv = Q * sqrt(SG / deltaP) where SG = rho/1000
      double density = fluid.getDensity("kg/m3") / 1000.0;
      double flowM3hr = fluid.getFlowRate("m3/sec") * 3600.0;
      Kv = flowM3hr / Math.sqrt(deltaP / density);
    } else {
      // Gas: Production choke sizing using IEC 60534-like formula
      // This is a simplified version suitable for production chokes
      // Kv = Q / (N9 * P1 * Y) * sqrt(MW * T * Z / x)
      double N9 = 24.6; // IEC 60534 constant
      double flowM3hr = fluid.getFlowRate("m3/sec") * 3600.0;
      double T = fluid.getTemperature();
      double MW = fluid.getMolarMass("gr/mol");
      double Z = fluid.getZ();
      double gamma = fluid.getGamma2();

      double P1_kPa = P1 * 100.0;
      double x = deltaP / P1;
      double Fgamma = gamma / 1.40;
      double xChoked = Fgamma * xT;

      boolean choked = x >= xChoked;
      double xEffective = choked && allowChoked ? xChoked : x;
      double Y = Math.max(1.0 - xEffective / (3.0 * Fgamma * xT), 2.0 / 3.0);

      if (choked && allowChoked) {
        Kv = flowM3hr / (N9 * P1_kPa * Y) * Math.sqrt(MW * T * Z / (xT * Fgamma));
      } else {
        Kv = flowM3hr / (N9 * P1_kPa * Y) * Math.sqrt(MW * T * Z / x);
      }

      // Apply discharge coefficient for production chokes
      Kv = Kv / Cd;
    }

    return Kv / openingFactor;
  }

  /**
   * Calculates the flow rate through a control valve based on the valve opening, Kv, and
   * inlet/outlet streams.
   *
   * @param Kv Flow coefficient (for 100% opening)
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Calculated flow rate in m³/s
   */
  public double calculateFlowRateFromValveOpening(double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateMolarFlow(Kv * valveOpening / 100.0, inletStream, outletStream);
  }

  /** {@inheritDoc} */
  @Override
  public double calculateMolarFlow(double KvAdjusted, StreamInterface inStream,
      StreamInterface outStream) {
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    SystemInterface fluid = inStream.getFluid();

    double P1 = inStream.getPressure("bara");
    double P2 = outStream.getPressure("bara");
    double deltaP = P1 - P2;

    double flow_m3_s;

    if (!valve.isGasValve()) {
      // Liquid: Q = Kv * sqrt(deltaP / SG)
      double density = fluid.getDensity("kg/m3") / 1000.0;
      double flow_m3_hr = KvAdjusted * Math.sqrt(deltaP / density);
      flow_m3_s = flow_m3_hr / 3600.0;
    } else {
      // Gas: Inverse of calcKv formula
      double N9 = 24.6;
      double T = fluid.getTemperature();
      double MW = fluid.getMolarMass("gr/mol");
      double Z = fluid.getZ();
      double gamma = fluid.getGamma2();

      double P1_kPa = P1 * 100.0;
      double x = deltaP / P1;
      double Fgamma = gamma / 1.40;
      double xChoked = Fgamma * xT;

      boolean choked = x >= xChoked;
      double xEffective = choked && allowChoked ? xChoked : x;
      double Y = Math.max(1.0 - xEffective / (3.0 * Fgamma * xT), 2.0 / 3.0);

      // Apply discharge coefficient
      double effectiveKv = KvAdjusted * Cd;

      double denominator;
      if (choked && allowChoked) {
        denominator = Math.sqrt(MW * T * Z / (xT * Fgamma));
      } else {
        denominator = Math.sqrt(MW * T * Z / x);
      }

      double flow_m3_hr = effectiveKv * N9 * P1_kPa * Y / denominator;
      flow_m3_s = flow_m3_hr / 3600.0;
    }

    return flow_m3_s;
  }

  /**
   * Calculates the required valve opening percentage for a given flow rate.
   *
   * <p>
   * This method inverts the flow calculation to determine what valve opening percentage is needed
   * to achieve the specified flow rate Q, given the valve's Kv and the inlet/outlet conditions.
   * </p>
   *
   * @param Q Desired volumetric flow rate [m³/s]
   * @param Kv Flow coefficient at 100% opening
   * @param valveOpening Current valve opening percentage (not used in calculation)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Required valve opening percentage (0-100)
   */
  public double calculateValveOpeningFromFlowRate(double Q, double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    SystemInterface fluid = inletStream.getFluid();

    double P1 = inletStream.getPressure("bara");
    double P2 = outletStream.getPressure("bara");
    double deltaP = P1 - P2;

    if (deltaP <= 0.0) {
      return 0.0;
    }

    double Q_m3h = Q * 3600.0;
    double requiredKvAdjusted;

    if (!valve.isGasValve()) {
      // Liquid
      double density = fluid.getDensity("kg/m3") / 1000.0;
      requiredKvAdjusted = Q_m3h / Math.sqrt(deltaP / density);
    } else {
      // Gas: invert the IEC 60534 formula
      double N9 = 24.6;
      double T = fluid.getTemperature();
      double MW = fluid.getMolarMass("gr/mol");
      double Z = fluid.getZ();
      double gamma = fluid.getGamma2();

      double P1_kPa = P1 * 100.0;
      double x = deltaP / P1;
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

      requiredKvAdjusted = Q_m3h / (N9 * P1_kPa * Y) * denominator / Cd;
    }

    double openingPercent = (requiredKvAdjusted / Kv) * 100.0;
    return Math.max(0.0, Math.min(100.0, openingPercent));
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

  /** {@inheritDoc} */
  @Override
  public double calculateOutletPressure(double KvAdjusted, StreamInterface inStream) {
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    SystemInterface fluid = inStream.getFluid();

    double P1 = inStream.getPressure("bara");
    double Q_m3_s = fluid.getFlowRate("m3/sec");

    if (!valve.isGasValve()) {
      // Liquid: P2 = P1 - (Q / Kv)^2 / SG
      double density = fluid.getDensity("kg/m3") / 1000.0;
      double Q_m3_hr = Q_m3_s * 3600.0;
      double dP_bar = Math.pow(Q_m3_hr / KvAdjusted, 2) * density;
      return (P1 - dP_bar) * 1e5;
    } else {
      // Gas: use bisection to find P2
      double N9 = 24.6;
      double T = fluid.getTemperature();
      double MW = fluid.getMolarMass("gr/mol");
      double Z = fluid.getZ();
      double gamma = fluid.getGamma2();
      double Fgamma = gamma / 1.40;
      double Q_m3_hr = Q_m3_s * 3600.0;

      double P2_low = 0.1;
      double P2_high = P1 - 0.001;
      double P2_mid = (P2_low + P2_high) / 2.0;
      double tolerance = 1e-6;
      int maxIter = 100;

      for (int i = 0; i < maxIter; i++) {
        P2_mid = (P2_low + P2_high) / 2.0;

        double P1_kPa = P1 * 100.0;
        double dP_kPa = (P1 - P2_mid) * 100.0;
        double x = dP_kPa / P1_kPa;
        double xChoked = Fgamma * xT;

        boolean choked = x >= xChoked;
        double xEffective = choked && allowChoked ? xChoked : x;
        double Y = Math.max(1.0 - xEffective / (3.0 * Fgamma * xT), 2.0 / 3.0);

        double calcKv;
        if (choked && allowChoked) {
          calcKv = Q_m3_hr / (N9 * P1_kPa * Y) * Math.sqrt(MW * T * Z / (xT * Fgamma)) / Cd;
        } else {
          calcKv = Q_m3_hr / (N9 * P1_kPa * Y) * Math.sqrt(MW * T * Z / x) / Cd;
        }

        if (Math.abs(calcKv - KvAdjusted) / KvAdjusted < tolerance) {
          break;
        }

        if (calcKv > KvAdjusted) {
          P2_high = P2_mid;
        } else {
          P2_low = P2_mid;
        }
      }

      return P2_mid * 1e5;
    }
  }
}
