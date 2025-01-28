package neqsim.process.equipment.diffpressure;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

public class Orifice extends TwoPortEquipment {
  private static final long serialVersionUID = 1L;
  private String name;
  private StreamInterface inputstream;
  private StreamInterface outputstream;
  private Double dp;
  private Double diameter;
  private Double diameter_outer;
  private Double C;
  private double orificeDiameter;
  private double pressureUpstream;
  private double pressureDownstream;
  private double dischargeCoefficient;

  public Orifice(String name) {
    super(name);
  }

  public Orifice(String name, double diameter, double orificeDiameter, double pressureUpstream,
      double pressureDownstream, double dischargeCoefficient) {
    super(name);
    this.diameter = diameter;
    this.orificeDiameter = orificeDiameter;
    this.pressureUpstream = pressureUpstream;
    this.pressureDownstream = pressureDownstream;
    this.dischargeCoefficient = dischargeCoefficient;
  }

  public void setInputStream(StreamInterface stream) {
    this.inputstream = stream;
    this.outputstream = (StreamInterface) stream.clone();
  }

  public StreamInterface getOutputStream() {
    return outputstream;
  }

  public void setOrificeParameters(Double diameter, Double diameter_outer, Double C) {
    this.diameter = diameter;
    this.diameter_outer = diameter_outer;
    this.C = C;
  }

  public Double calc_dp() {
    double beta = orificeDiameter / diameter;
    double beta2 = beta * beta;
    double beta4 = beta2 * beta2;
    double dP = pressureUpstream - pressureDownstream;

    double deltaW = (Math.sqrt(1.0 - beta4 * (1.0 - dischargeCoefficient * dischargeCoefficient))
        - dischargeCoefficient * beta2)
        / (Math.sqrt(1.0 - beta4 * (1.0 - dischargeCoefficient * dischargeCoefficient))
            + dischargeCoefficient * beta2)
        * dP;

    return deltaW;
  }

  /**
   * Calculates the orifice discharge coefficient using the Reader-Harris Gallagher method.
   * 
   * @param D Upstream internal pipe diameter, in meters.
   * @param Do Diameter of orifice at flow conditions, in meters.
   * @param rho Density of fluid at P1, in kg/m^3.
   * @param mu Viscosity of fluid at P1, in Pa*s.
   * @param m Mass flow rate of fluid through the orifice, in kg/s.
   * @param taps Tap type ("corner", "flange", "D", or "D/2").
   * @return Discharge coefficient of the orifice.
   */
  public static double calculateDischargeCoefficient(double D, double Do, double rho, double mu,
      double m, String taps) {
    double A_pipe = 0.25 * Math.PI * D * D;
    double v = m / (A_pipe * rho);
    double Re_D = rho * v * D / mu;
    double beta = Do / D;
    double beta2 = beta * beta;
    double beta4 = beta2 * beta2;
    double beta8 = beta4 * beta4;

    double L1, L2_prime;
    if ("corner".equalsIgnoreCase(taps)) {
      L1 = 0.0;
      L2_prime = 0.0;
    } else if ("flange".equalsIgnoreCase(taps)) {
      L1 = L2_prime = 0.0254 / D;
    } else if ("D".equalsIgnoreCase(taps) || "D/2".equalsIgnoreCase(taps)) {
      L1 = 1.0;
      L2_prime = 0.47;
    } else {
      throw new IllegalArgumentException("Unsupported tap type: " + taps);
    }

    double A = Math.pow(19000 * beta / Re_D, 0.8);
    double M2_prime = 2.0 * L2_prime / (1.0 - beta);

    double deltaCUpstream = ((0.043 + 0.08 * Math.exp(-10 * L1) - 0.123 * Math.exp(-7 * L1))
        * (1.0 - 0.11 * A) * beta4 / (1.0 - beta4));

    double deltaCDownstream =
        -0.031 * (M2_prime - 0.8 * Math.pow(M2_prime, 1.1)) * Math.pow(beta, 1.3);
    double C_inf_C_s =
        0.5961 + 0.0261 * beta2 - 0.216 * beta8 + 0.000521 * Math.pow(1e6 * beta / Re_D, 0.7)
            + (0.0188 + 0.0063 * A) * Math.pow(beta, 3.5) * Math.pow(1e6 / Re_D, 0.3);

    return C_inf_C_s + deltaCUpstream + deltaCDownstream;
  }

  /**
   * Calculates the expansibility factor for orifice plate calculations.
   * 
   * @param D Upstream internal pipe diameter, in meters.
   * @param Do Diameter of orifice at flow conditions, in meters.
   * @param P1 Static pressure of fluid upstream, in Pa.
   * @param P2 Static pressure of fluid downstream, in Pa.
   * @param k Isentropic exponent of fluid.
   * @return Expansibility factor (1 for incompressible fluids).
   */
  public static double calculateExpansibility(double D, double Do, double P1, double P2, double k) {
    double beta = Do / D;
    double beta4 = Math.pow(beta, 4);
    return 1.0 - (0.351 + beta4 * (0.93 * beta4 + 0.256)) * (1.0 - Math.pow(P2 / P1, 1.0 / k));
  }

  /**
   * Calculates the non-recoverable pressure drop across the orifice plate.
   * 
   * @param D Upstream internal pipe diameter, in meters.
   * @param Do Diameter of orifice at flow conditions, in meters.
   * @param P1 Static pressure of fluid upstream, in Pa.
   * @param P2 Static pressure of fluid downstream, in Pa.
   * @param C Discharge coefficient.
   * @return Non-recoverable pressure drop, in Pa.
   */
  public static double calculatePressureDrop(double D, double Do, double P1, double P2, double C) {
    double beta = Do / D;
    double beta2 = beta * beta;
    double beta4 = beta2 * beta2;
    double dP = P1 - P2;
    double deltaW = (Math.sqrt(1.0 - beta4 * (1.0 - C * C)) - C * beta2)
        / (Math.sqrt(1.0 - beta4 * (1.0 - C * C)) + C * beta2) * dP;
    return deltaW;
  }

  /**
   * Calculates the diameter ratio (beta) of the orifice plate.
   * 
   * @param D Upstream internal pipe diameter, in meters.
   * @param Do Diameter of orifice at flow conditions, in meters.
   * @return Diameter ratio (beta).
   */
  public static double calculateBetaRatio(double D, double Do) {
    return Do / D;
  }

  @Override
  public void run(UUID uuid) {
    if (inputstream != null && outputstream != null) {
      double newPressure = inputstream.getPressure("bara") - calc_dp();
      SystemInterface outfluid = (SystemInterface) inStream.clone();
      outfluid.setPressure(newPressure);
      outStream.setFluid(outfluid);
      outStream.run();
    }
  }

}
