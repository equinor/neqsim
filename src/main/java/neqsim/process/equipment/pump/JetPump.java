package neqsim.process.equipment.pump;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Hydraulic jet pump (eductor) artificial-lift model.
 *
 * <p>
 * A jet pump has no moving downhole parts: a high-pressure power fluid is accelerated through a nozzle, entrains the
 * produced (suction) fluid in a throat, and the combined stream recovers pressure in a diffuser. This class implements
 * the classic dimensionless performance relationship (Cunningham / Gosline-O'Brien) used by PROSPER and Pipesim to size
 * jet pumps.
 * </p>
 *
 * <p>
 * With area ratio R = A_nozzle / A_throat and operating flow ratio M = q_suction / q_nozzle, the dimensionless head
 * ratio H = (p_d - p_s) / (p_n - p_d) follows
 * </p>
 *
 * <p>
 * H = [2R + (1-2R)(RM/(1-R))&sup2; - (1+K_td)R&sup2;(1+M)&sup2;] / [(1+K_n) - 2R - (1-2R)(RM/(1-R))&sup2; +
 * (1+K_td)R&sup2;(1+M)&sup2;]
 * </p>
 *
 * <p>
 * from which the discharge pressure p_d = (p_s + H&middot;p_n) / (1 + H) and the pump efficiency &eta; = M&middot;H are
 * obtained. The model treats the device as a pressure booster: in {@link #run(UUID)} the inlet stream is boosted to the
 * computed discharge pressure.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see Pump
 */
public class JetPump extends Pump {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(JetPump.class);

  private double areaRatio = 0.25; // R = A_nozzle / A_throat
  private double powerFluidPressure = 200.0; // bara
  private double nozzleArea = 1.0e-4; // m2
  private double nozzleDischargeCoeff = 0.97;
  private double nozzleLossCoeff = 0.03; // K_n
  private double throatDiffuserLossCoeff = 0.20; // K_td
  private double operatingFlowRatio = 1.0; // M = q_suction / q_nozzle
  private double powerFluidDensity = 1000.0; // kg/m3

  // Operating state
  private double headRatio = 0.0;
  private double dischargePressure = 0.0;
  private double efficiency = 0.0;
  private double producedRate = 0.0; // m3/s

  /**
   * Constructor for JetPump.
   *
   * @param name name of jet pump
   */
  public JetPump(String name) {
    super(name);
  }

  /**
   * Constructor for JetPump.
   *
   * @param name        name of jet pump
   * @param inletStream suction (produced fluid) inlet stream
   */
  public JetPump(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the nozzle-to-throat area ratio R.
   *
   * @param areaRatio area ratio (0 &lt; R &lt; 1)
   */
  public void setAreaRatio(double areaRatio) {
    this.areaRatio = areaRatio;
  }

  /**
   * Sets the power-fluid (nozzle) pressure.
   *
   * @param powerFluidPressureBara power-fluid pressure in bara
   */
  public void setPowerFluidPressure(double powerFluidPressureBara) {
    this.powerFluidPressure = powerFluidPressureBara;
  }

  /**
   * Sets the nozzle flow area.
   *
   * @param nozzleAreaM2 nozzle area in m2
   */
  public void setNozzleArea(double nozzleAreaM2) {
    this.nozzleArea = nozzleAreaM2;
  }

  /**
   * Sets the operating flow ratio M = q_suction / q_nozzle.
   *
   * @param operatingFlowRatio dimensionless flow ratio
   */
  public void setOperatingFlowRatio(double operatingFlowRatio) {
    this.operatingFlowRatio = operatingFlowRatio;
  }

  /**
   * Sets the power-fluid density used for nozzle flow.
   *
   * @param densityKgPerM3 power-fluid density in kg/m3
   */
  public void setPowerFluidDensity(double densityKgPerM3) {
    this.powerFluidDensity = densityKgPerM3;
  }

  /**
   * Computes the dimensionless head ratio at a given flow ratio.
   *
   * @param m operating flow ratio M = q_suction / q_nozzle
   * @return head ratio H = (p_d - p_s) / (p_n - p_d)
   */
  public double headRatioAt(double m) {
    double r = areaRatio;
    double term = (r * m) / (1.0 - r);
    double termSq = term * term;
    double tail = (1.0 + throatDiffuserLossCoeff) * r * r * (1.0 + m) * (1.0 + m);
    double numerator = 2.0 * r + (1.0 - 2.0 * r) * termSq - tail;
    double denominator = (1.0 + nozzleLossCoeff) - 2.0 * r - (1.0 - 2.0 * r) * termSq + tail;
    if (Math.abs(denominator) < 1.0e-12) {
      return 0.0;
    }
    return numerator / denominator;
  }

  /**
   * Returns the most recent head ratio.
   *
   * @return head ratio
   */
  public double getHeadRatio() {
    return headRatio;
  }

  /**
   * Returns the most recent discharge pressure.
   *
   * @return discharge pressure in bara
   */
  public double getDischargePressure() {
    return dischargePressure;
  }

  /**
   * Returns the most recent jet-pump efficiency.
   *
   * @return efficiency (fraction)
   */
  public double getEfficiency() {
    return efficiency;
  }

  /**
   * Returns the most recent produced (suction) volumetric rate.
   *
   * @param unit "m3/sec" or "m3/day"
   * @return produced rate in the requested unit
   */
  public double getProducedRate(String unit) {
    if ("m3/day".equals(unit)) {
      return producedRate * 86400.0;
    }
    return producedRate;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Computes jet-pump performance from the current configuration and boosts the inlet stream to the computed discharge
   * pressure.
   * </p>
   */
  @Override
  public void run(UUID id) {
    double suctionPressure = getInletStream().getPressure("bara");
    headRatio = headRatioAt(operatingFlowRatio);
    if (headRatio < 0.0) {
      headRatio = 0.0;
    }
    dischargePressure = (suctionPressure + headRatio * powerFluidPressure) / (1.0 + headRatio);
    if (dischargePressure < suctionPressure) {
      dischargePressure = suctionPressure;
    }
    efficiency = operatingFlowRatio * headRatio;
    // Nozzle (power-fluid) flow from Bernoulli through the nozzle pressure drop.
    double dpNozzle = Math.max(0.0, (powerFluidPressure - suctionPressure) * 1.0e5);
    double nozzleFlow = nozzleDischargeCoeff * nozzleArea * Math.sqrt(2.0 * dpNozzle / powerFluidDensity);
    producedRate = operatingFlowRatio * nozzleFlow;
    setOutletPressure(dischargePressure, "bara");
    super.run(id);
  }
}
