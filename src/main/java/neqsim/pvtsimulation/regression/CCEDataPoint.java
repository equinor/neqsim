package neqsim.pvtsimulation.regression;

/**
 * Data point for Constant Composition Expansion (CCE) experiment.
 *
 * @author ESOL
 * @version 1.0
 */
public class CCEDataPoint {
  private double pressure;
  private double relativeVolume;
  private double temperature;
  private double yFactor = Double.NaN;
  private double compressibility = Double.NaN;

  /**
   * Create a CCE data point.
   *
   * @param pressure pressure in bar
   * @param relativeVolume relative volume V/Vsat
   * @param temperature temperature in K
   */
  public CCEDataPoint(double pressure, double relativeVolume, double temperature) {
    this.pressure = pressure;
    this.relativeVolume = relativeVolume;
    this.temperature = temperature;
  }

  /**
   * Get pressure.
   *
   * @return pressure in bar
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Set pressure.
   *
   * @param pressure pressure in bar
   */
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * Get relative volume.
   *
   * @return relative volume V/Vsat
   */
  public double getRelativeVolume() {
    return relativeVolume;
  }

  /**
   * Set relative volume.
   *
   * @param relativeVolume relative volume V/Vsat
   */
  public void setRelativeVolume(double relativeVolume) {
    this.relativeVolume = relativeVolume;
  }

  /**
   * Get temperature.
   *
   * @return temperature in K
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Set temperature.
   *
   * @param temperature temperature in K
   */
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /**
   * Get Y-factor (gas phase compressibility above saturation).
   *
   * @return Y-factor
   */
  public double getYFactor() {
    return yFactor;
  }

  /**
   * Set Y-factor.
   *
   * @param yFactor Y-factor
   */
  public void setYFactor(double yFactor) {
    this.yFactor = yFactor;
  }

  /**
   * Get isothermal compressibility.
   *
   * @return compressibility in 1/bar
   */
  public double getCompressibility() {
    return compressibility;
  }

  /**
   * Set isothermal compressibility.
   *
   * @param compressibility compressibility in 1/bar
   */
  public void setCompressibility(double compressibility) {
    this.compressibility = compressibility;
  }
}
