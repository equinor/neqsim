package neqsim.process.equipment.diffpressure;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Orifice class.
 * </p>
 *
 * @author ESOL
 */
public class Orifice extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private Double dp;
  private Double diameter;
  private Double diameter_outer;
  private Double C;
  private double orificeDiameter;
  private double pressureUpstream;
  private double pressureDownstream;
  private double dischargeCoefficient;

  /**
   * <p>
   * Constructor for Orifice.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Orifice(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Orifice.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param diameter a double
   * @param orificeDiameter a double
   * @param pressureUpstream a double
   * @param pressureDownstream a double
   * @param dischargeCoefficient a double
   */
  public Orifice(String name, double diameter, double orificeDiameter, double pressureUpstream,
      double pressureDownstream, double dischargeCoefficient) {
    super(name);
    this.diameter = diameter;
    this.orificeDiameter = orificeDiameter;
    this.pressureUpstream = pressureUpstream;
    this.pressureDownstream = pressureDownstream;
    this.dischargeCoefficient = dischargeCoefficient;
  }

  /**
   * <p>
   * setOrificeParameters.
   * </p>
   *
   * @param diameter a {@link java.lang.Double} object
   * @param diameter_outer a {@link java.lang.Double} object
   * @param C a {@link java.lang.Double} object
   */
  public void setOrificeParameters(Double diameter, Double diameter_outer, Double C) {
    this.diameter = diameter;
    this.diameter_outer = diameter_outer;
    this.C = C;
  }

  /**
   * <p>
   * calc_dp.
   * </p>
   *
   * @return a {@link java.lang.Double} object
   */
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

    double L1;
    double L2_prime;
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

  /**
   * Calculates the mass flow rate through an orifice plate using the ISO 5167 formulation.
   *
   * <p>
   * Inputs and output are all in SI units. The method iterates the Reader-Harris/Gallagher
   * discharge coefficient until convergence.
   * </p>
   *
   * @param D upstream internal pipe diameter in meters
   * @param Do orifice diameter in meters
   * @param P1 upstream static pressure in Pa
   * @param P2 downstream static pressure in Pa
   * @param rho fluid density in kg/m3 at P1
   * @param mu fluid viscosity in Pa*s at P1
   * @param k isentropic exponent of the fluid
   * @param taps pressure tap type ("corner", "flange", "D", or "D/2")
   * @return mass flow rate in kg/s
   */
  public static double calculateMassFlowRate(double D, double Do, double P1, double P2, double rho,
      double mu, double k, String taps) {
    final int MAX_ITERATIONS = 50;
    double m = 1.0;
    for (int i = 0; i < MAX_ITERATIONS; i++) {
      double C = calculateDischargeCoefficient(D, Do, rho, mu, m, taps);
      double epsilon = calculateExpansibility(D, Do, P1, P2, k);
      double beta = calculateBetaRatio(D, Do);
      double beta2 = beta * beta;
      double mCalc = 0.25 * Math.PI * Do * Do * C * epsilon
          * Math.sqrt(2.0 * rho * (P1 - P2) / (1.0 - beta2 * beta2));
      if (Math.abs(mCalc - m) / m < 1e-8) {
        break;
      }
      m = mCalc;
    }
    return m;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID uuid) {
    if (inStream != null && outStream != null) {
      double newPressure = inStream.getPressure("bara") - calc_dp();
      SystemInterface outfluid = (SystemInterface) inStream.clone();
      outfluid.setPressure(newPressure);
      outStream.setFluid(outfluid);
      outStream.run();
    }
  }

  /**
   * Run transient simulation for the orifice.
   *
   * @param dt Time step in seconds
   * @param id Unique identifier for this run
   */
  public void runTransient(double dt, UUID id) {
    // For orifice, transient behavior is quasi-steady (no accumulation)
    // Just run steady-state calculation
    SystemInterface thermoSystem = inStream.getThermoSystem().clone();

    // Handle zero or very low flow cases
    double flowRate = thermoSystem.getFlowRate("mole/sec");
    if (flowRate < 1e-10) {
      // For negligible flow, just set outlet to inlet conditions
      outStream.setFluid(thermoSystem);
      return;
    }

    thermoSystem.init(3);

    // Get inlet pressure from stream
    double P1 = inStream.getPressure("bara");

    // Get downstream pressure - use stored value if available, otherwise from outlet stream
    double P2 = (pressureDownstream > 0.0) ? pressureDownstream : outStream.getPressure("bara");

    // In dynamic/transient mode: Calculate flow based on pressure difference
    // (Similar to valve behavior - flow is determined by ΔP, not by upstream conditions)
    if (diameter != null && orificeDiameter > 0.0 && dischargeCoefficient > 0.0 && P1 > P2) {
      double beta = orificeDiameter / diameter;
      double beta2 = beta * beta;
      double beta4 = beta2 * beta2;

      // Get fluid properties at inlet conditions
      double rho = thermoSystem.getDensity("kg/m3");
      double k = thermoSystem.getGamma();

      // Available pressure drop
      double availableDeltaP_bara = P1 - P2;
      double P1_Pa = P1 * 1e5;
      double P2_Pa = P2 * 1e5;
      double dP_Pa = availableDeltaP_bara * 1e5;

      double C = dischargeCoefficient;
      double epsilon = calculateExpansibility(diameter, orificeDiameter, P1_Pa, P2_Pa, k);
      double A_orifice = 0.25 * Math.PI * orificeDiameter * orificeDiameter;

      // Calculate actual mass flow through orifice based on ISO 5167
      // m = A * C * ε * sqrt(2 * ρ * ΔP / (1 - β⁴))
      double calculatedFlow_kgs =
          A_orifice * C * epsilon * Math.sqrt(2.0 * rho * dP_Pa / (1.0 - beta4));

      // In dynamic mode, the orifice DETERMINES the flow (not just limits it)
      // Set this as the actual flow through the orifice
      thermoSystem.setTotalFlowRate(calculatedFlow_kgs, "kg/sec");
      inStream.getFluid().setTotalFlowRate(calculatedFlow_kgs, "kg/sec");
    }

    // Set outlet pressure
    thermoSystem.setPressure(P2, "bara");
    outStream.setFluid(thermoSystem);
    inStream.run();


  }
}
