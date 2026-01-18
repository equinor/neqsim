package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Operating envelope for compressor performance tracking.
 *
 * <p>
 * This class defines and tracks the safe operating region for a compressor including:
 * </p>
 * <ul>
 * <li>Surge line - minimum stable flow at each speed</li>
 * <li>Stonewall line - maximum flow (choked flow) at each speed</li>
 * <li>Speed limits - minimum and maximum operating speeds</li>
 * <li>Power limits - driver power constraints</li>
 * <li>Head limits - maximum and minimum head at each speed</li>
 * </ul>
 *
 * <h3>Envelope Definition</h3>
 * <p>
 * The operating envelope is typically defined by arrays of flow and head values at different
 * speeds. The surge line represents the minimum flow below which the compressor becomes unstable.
 * The stonewall (choke) line represents the maximum flow where the compressor can no longer
 * increase flow regardless of head.
 * </p>
 *
 * <h3>Example Usage</h3>
 * 
 * <pre>
 * OperatingEnvelope envelope = new OperatingEnvelope();
 * envelope.setSurgeLine(surgeFlows, surgeHeads, surgeSpeeds);
 * envelope.setStonewallLine(stonewallFlows, stonewallHeads, stonewallSpeeds);
 * envelope.setMinSpeed(7000);
 * envelope.setMaxSpeed(10500);
 * 
 * boolean inEnvelope = envelope.isWithinEnvelope(currentFlow, currentHead, currentSpeed);
 * double surgeMargin = envelope.getSurgeMargin(currentFlow, currentHead, currentSpeed);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class OperatingEnvelope implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Surge line flow points (m3/h). */
  private double[] surgeFlows;

  /** Surge line head points (kJ/kg). */
  private double[] surgeHeads;

  /** Surge line speed points (RPM). */
  private double[] surgeSpeeds;

  /** Stonewall line flow points (m3/h). */
  private double[] stonewallFlows;

  /** Stonewall line head points (kJ/kg). */
  private double[] stonewallHeads;

  /** Stonewall line speed points (RPM). */
  private double[] stonewallSpeeds;

  /** Minimum operating speed (RPM). */
  private double minSpeed = 7000.0;

  /** Maximum operating speed (RPM). */
  private double maxSpeed = 10500.0;

  /** Rated speed (RPM). */
  private double ratedSpeed = 10000.0;

  /** Maximum power (kW). */
  private double maxPower = Double.MAX_VALUE;

  /** Minimum power for stable operation (kW). */
  private double minPower = 0.0;

  /** Maximum head (kJ/kg). */
  private double maxHead = Double.MAX_VALUE;

  /** Maximum discharge temperature (Kelvin). */
  private double maxDischargeTemperature = 473.15;

  /** Use polynomial fit for surge line. */
  private boolean usePolynomialFit = false;

  /** Surge line polynomial coefficients (head = a0 + a1*Q + a2*Q^2 + ...). */
  private double[] surgePolynomialCoeffs;

  /** Stonewall polynomial coefficients. */
  private double[] stonewallPolynomialCoeffs;

  /**
   * Default constructor.
   */
  public OperatingEnvelope() {}

  /**
   * Constructor with speed limits.
   *
   * @param minSpeed minimum operating speed in RPM
   * @param maxSpeed maximum operating speed in RPM
   */
  public OperatingEnvelope(double minSpeed, double maxSpeed) {
    this.minSpeed = minSpeed;
    this.maxSpeed = maxSpeed;
  }

  /**
   * Sets the surge line from discrete points.
   *
   * @param flows flow values in m3/h
   * @param heads head values in kJ/kg
   * @param speeds speed values in RPM
   */
  public void setSurgeLine(double[] flows, double[] heads, double[] speeds) {
    this.surgeFlows = flows.clone();
    this.surgeHeads = heads.clone();
    this.surgeSpeeds = speeds.clone();
  }

  /**
   * Sets the surge line from a polynomial.
   *
   * <p>
   * Head at surge = a0 + a1*Q + a2*Q^2 + ...
   * </p>
   *
   * @param coefficients polynomial coefficients
   */
  public void setSurgeLinePolynomial(double[] coefficients) {
    this.surgePolynomialCoeffs = coefficients.clone();
    this.usePolynomialFit = true;
  }

  /**
   * Sets the stonewall line from discrete points.
   *
   * @param flows flow values in m3/h
   * @param heads head values in kJ/kg
   * @param speeds speed values in RPM
   */
  public void setStonewallLine(double[] flows, double[] heads, double[] speeds) {
    this.stonewallFlows = flows.clone();
    this.stonewallHeads = heads.clone();
    this.stonewallSpeeds = speeds.clone();
  }

  /**
   * Sets the stonewall line from a polynomial.
   *
   * @param coefficients polynomial coefficients
   */
  public void setStonewallLinePolynomial(double[] coefficients) {
    this.stonewallPolynomialCoeffs = coefficients.clone();
  }

  /**
   * Checks if an operating point is within the envelope.
   *
   * @param flow actual flow in m3/h
   * @param head actual head in kJ/kg
   * @param speed actual speed in RPM
   * @return true if within envelope
   */
  public boolean isWithinEnvelope(double flow, double head, double speed) {
    // Check speed limits
    if (speed < minSpeed || speed > maxSpeed) {
      return false;
    }

    // Check surge (minimum flow at this head)
    double surgeFlow = getSurgeFlowAtHead(head, speed);
    if (flow < surgeFlow) {
      return false;
    }

    // Check stonewall (maximum flow at this head)
    double stonewallFlow = getStonewallFlowAtHead(head, speed);
    if (flow > stonewallFlow) {
      return false;
    }

    // Check head limit
    if (head > maxHead) {
      return false;
    }

    return true;
  }

  /**
   * Gets the surge margin for an operating point.
   *
   * <p>
   * Surge margin = (Q_actual - Q_surge) / Q_surge
   * </p>
   *
   * @param flow actual flow in m3/h
   * @param head actual head in kJ/kg
   * @param speed actual speed in RPM
   * @return surge margin as fraction (positive = safe, negative = in surge)
   */
  public double getSurgeMargin(double flow, double head, double speed) {
    double surgeFlow = getSurgeFlowAtHead(head, speed);
    if (surgeFlow <= 0) {
      return 1.0; // No surge data, assume safe
    }
    return (flow - surgeFlow) / surgeFlow;
  }

  /**
   * Gets the stonewall margin for an operating point.
   *
   * <p>
   * Stonewall margin = (Q_stonewall - Q_actual) / Q_stonewall
   * </p>
   *
   * @param flow actual flow in m3/h
   * @param head actual head in kJ/kg
   * @param speed actual speed in RPM
   * @return stonewall margin as fraction (positive = safe, negative = choked)
   */
  public double getStonewallMargin(double flow, double head, double speed) {
    double stonewallFlow = getStonewallFlowAtHead(head, speed);
    if (stonewallFlow <= 0) {
      return 1.0; // No stonewall data, assume safe
    }
    return (stonewallFlow - flow) / stonewallFlow;
  }

  /**
   * Gets the surge flow at a given head and speed.
   *
   * @param head polytropic head in kJ/kg
   * @param speed shaft speed in RPM
   * @return surge flow in m3/h
   */
  public double getSurgeFlowAtHead(double head, double speed) {
    if (usePolynomialFit && surgePolynomialCoeffs != null) {
      return getSurgeFlowFromPolynomial(head);
    }

    if (surgeFlows == null || surgeFlows.length == 0) {
      return 0.0; // No surge data
    }

    // Scale to current speed (affinity laws: Q ~ N, H ~ N^2)
    double speedRatio = speed / ratedSpeed;
    double scaledHead = head / (speedRatio * speedRatio);

    // Interpolate from surge curve
    return interpolateSurgeFlow(scaledHead) * speedRatio;
  }

  /**
   * Gets the stonewall flow at a given head and speed.
   *
   * @param head polytropic head in kJ/kg
   * @param speed shaft speed in RPM
   * @return stonewall flow in m3/h
   */
  public double getStonewallFlowAtHead(double head, double speed) {
    if (stonewallPolynomialCoeffs != null) {
      return getStonewallFlowFromPolynomial(head);
    }

    if (stonewallFlows == null || stonewallFlows.length == 0) {
      return Double.MAX_VALUE; // No stonewall data
    }

    // Scale to current speed
    double speedRatio = speed / ratedSpeed;
    double scaledHead = head / (speedRatio * speedRatio);

    return interpolateStonewallFlow(scaledHead) * speedRatio;
  }

  /**
   * Gets the distance to the nearest envelope boundary.
   *
   * @param flow actual flow in m3/h
   * @param head actual head in kJ/kg
   * @param speed actual speed in RPM
   * @return distance as fraction (positive = inside, negative = outside)
   */
  public double getDistanceToEnvelope(double flow, double head, double speed) {
    double surgeMargin = getSurgeMargin(flow, head, speed);
    double stonewallMargin = getStonewallMargin(flow, head, speed);
    double speedMargin = getSpeedMargin(speed);

    return Math.min(Math.min(surgeMargin, stonewallMargin), speedMargin);
  }

  /**
   * Gets the speed margin.
   *
   * @param speed actual speed in RPM
   * @return margin as fraction of operating range
   */
  public double getSpeedMargin(double speed) {
    double range = maxSpeed - minSpeed;
    if (range <= 0) {
      return 0.0;
    }

    if (speed < minSpeed) {
      return (speed - minSpeed) / range;
    }
    if (speed > maxSpeed) {
      return (maxSpeed - speed) / range;
    }

    // Distance to nearest limit
    double distToMin = speed - minSpeed;
    double distToMax = maxSpeed - speed;
    return Math.min(distToMin, distToMax) / range;
  }

  /**
   * Gets the limiting constraint at an operating point.
   *
   * @param flow actual flow in m3/h
   * @param head actual head in kJ/kg
   * @param speed actual speed in RPM
   * @return string describing the limiting constraint, or null if within envelope
   */
  public String getLimitingConstraint(double flow, double head, double speed) {
    List<String> violations = new ArrayList<String>();

    if (speed < minSpeed) {
      violations.add("Speed below minimum (" + minSpeed + " RPM)");
    }
    if (speed > maxSpeed) {
      violations.add("Speed above maximum (" + maxSpeed + " RPM)");
    }

    double surgeMargin = getSurgeMargin(flow, head, speed);
    if (surgeMargin < 0) {
      violations.add("Surge (margin: " + String.format("%.1f%%", surgeMargin * 100) + ")");
    }

    double stonewallMargin = getStonewallMargin(flow, head, speed);
    if (stonewallMargin < 0) {
      violations.add("Stonewall (margin: " + String.format("%.1f%%", stonewallMargin * 100) + ")");
    }

    if (violations.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < violations.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(violations.get(i));
    }
    return sb.toString();
  }

  /**
   * Interpolates surge flow from the surge curve.
   */
  private double interpolateSurgeFlow(double head) {
    if (surgeHeads == null || surgeHeads.length == 0) {
      return 0.0;
    }

    // Find bracketing points
    for (int i = 0; i < surgeHeads.length - 1; i++) {
      if (head >= surgeHeads[i + 1] && head <= surgeHeads[i]) {
        // Linear interpolation
        double fraction = (head - surgeHeads[i + 1]) / (surgeHeads[i] - surgeHeads[i + 1]);
        return surgeFlows[i + 1] + fraction * (surgeFlows[i] - surgeFlows[i + 1]);
      }
    }

    // Extrapolate if outside range
    if (head > surgeHeads[0]) {
      return surgeFlows[0];
    }
    return surgeFlows[surgeFlows.length - 1];
  }

  /**
   * Interpolates stonewall flow from the stonewall curve.
   */
  private double interpolateStonewallFlow(double head) {
    if (stonewallHeads == null || stonewallHeads.length == 0) {
      return Double.MAX_VALUE;
    }

    // Find bracketing points
    for (int i = 0; i < stonewallHeads.length - 1; i++) {
      if (head >= stonewallHeads[i + 1] && head <= stonewallHeads[i]) {
        double fraction =
            (head - stonewallHeads[i + 1]) / (stonewallHeads[i] - stonewallHeads[i + 1]);
        return stonewallFlows[i + 1] + fraction * (stonewallFlows[i] - stonewallFlows[i + 1]);
      }
    }

    if (head > stonewallHeads[0]) {
      return stonewallFlows[0];
    }
    return stonewallFlows[stonewallFlows.length - 1];
  }

  /**
   * Gets surge flow from polynomial fit.
   */
  private double getSurgeFlowFromPolynomial(double head) {
    if (surgePolynomialCoeffs == null) {
      return 0.0;
    }
    // Inverse: given head, find flow
    // This requires solving the polynomial - simplified here
    double flow = surgePolynomialCoeffs[0];
    if (surgePolynomialCoeffs.length > 1) {
      flow += surgePolynomialCoeffs[1] * head;
    }
    return Math.max(0.0, flow);
  }

  /**
   * Gets stonewall flow from polynomial fit.
   */
  private double getStonewallFlowFromPolynomial(double head) {
    if (stonewallPolynomialCoeffs == null) {
      return Double.MAX_VALUE;
    }
    double flow = stonewallPolynomialCoeffs[0];
    if (stonewallPolynomialCoeffs.length > 1) {
      flow += stonewallPolynomialCoeffs[1] * head;
    }
    return flow;
  }

  // Getters and setters

  /**
   * Gets the minimum speed.
   *
   * @return minimum speed in RPM
   */
  public double getMinSpeed() {
    return minSpeed;
  }

  /**
   * Sets the minimum speed.
   *
   * @param speed minimum speed in RPM
   */
  public void setMinSpeed(double speed) {
    this.minSpeed = speed;
  }

  /**
   * Gets the maximum speed.
   *
   * @return maximum speed in RPM
   */
  public double getMaxSpeed() {
    return maxSpeed;
  }

  /**
   * Sets the maximum speed.
   *
   * @param speed maximum speed in RPM
   */
  public void setMaxSpeed(double speed) {
    this.maxSpeed = speed;
  }

  /**
   * Gets the rated speed.
   *
   * @return rated speed in RPM
   */
  public double getRatedSpeed() {
    return ratedSpeed;
  }

  /**
   * Sets the rated speed.
   *
   * @param speed rated speed in RPM
   */
  public void setRatedSpeed(double speed) {
    this.ratedSpeed = speed;
  }

  /**
   * Gets the maximum power.
   *
   * @return max power in kW
   */
  public double getMaxPower() {
    return maxPower;
  }

  /**
   * Sets the maximum power.
   *
   * @param power max power in kW
   */
  public void setMaxPower(double power) {
    this.maxPower = power;
  }

  /**
   * Gets the minimum power.
   *
   * @return min power in kW
   */
  public double getMinPower() {
    return minPower;
  }

  /**
   * Sets the minimum power.
   *
   * @param power min power in kW
   */
  public void setMinPower(double power) {
    this.minPower = power;
  }

  /**
   * Gets the maximum head.
   *
   * @return max head in kJ/kg
   */
  public double getMaxHead() {
    return maxHead;
  }

  /**
   * Sets the maximum head.
   *
   * @param head max head in kJ/kg
   */
  public void setMaxHead(double head) {
    this.maxHead = head;
  }

  /**
   * Gets the maximum discharge temperature.
   *
   * @return max discharge temp in Kelvin
   */
  public double getMaxDischargeTemperature() {
    return maxDischargeTemperature;
  }

  /**
   * Sets the maximum discharge temperature.
   *
   * @param temp max discharge temp in Kelvin
   */
  public void setMaxDischargeTemperature(double temp) {
    this.maxDischargeTemperature = temp;
  }

  /**
   * Gets the surge flow array.
   *
   * @return surge flow array
   */
  public double[] getSurgeFlows() {
    return surgeFlows != null ? surgeFlows.clone() : null;
  }

  /**
   * Gets the surge head array.
   *
   * @return surge head array
   */
  public double[] getSurgeHeads() {
    return surgeHeads != null ? surgeHeads.clone() : null;
  }

  /**
   * Gets the stonewall flow array.
   *
   * @return stonewall flow array
   */
  public double[] getStonewallFlows() {
    return stonewallFlows != null ? stonewallFlows.clone() : null;
  }

  /**
   * Gets the stonewall head array.
   *
   * @return stonewall head array
   */
  public double[] getStonewallHeads() {
    return stonewallHeads != null ? stonewallHeads.clone() : null;
  }
}
