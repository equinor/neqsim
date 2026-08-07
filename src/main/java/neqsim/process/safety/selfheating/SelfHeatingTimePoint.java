package neqsim.process.safety.selfheating;

import java.io.Serializable;

/**
 * A single sample of the transient temperature history produced by {@link SelfHeatingInductionSolver}.
 *
 * @author ESOL
 * @version 1.0
 */
public class SelfHeatingTimePoint implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double timeS;
  private final double centreTemperatureK;
  private final double maxTemperatureK;

  /**
   * Construct a history sample.
   *
   * @param timeS elapsed time in s
   * @param centreTemperatureK temperature at the centre of the body in K
   * @param maxTemperatureK peak temperature anywhere in the body in K
   */
  public SelfHeatingTimePoint(double timeS, double centreTemperatureK, double maxTemperatureK) {
    this.timeS = timeS;
    this.centreTemperatureK = centreTemperatureK;
    this.maxTemperatureK = maxTemperatureK;
  }

  /**
   * Gets the elapsed time.
   *
   * @return time in s
   */
  public double getTimeS() {
    return timeS;
  }

  /**
   * Gets the centre temperature.
   *
   * @return centre temperature in K
   */
  public double getCentreTemperatureK() {
    return centreTemperatureK;
  }

  /**
   * Gets the peak temperature anywhere in the body.
   *
   * @return peak temperature in K
   */
  public double getMaxTemperatureK() {
    return maxTemperatureK;
  }
}
