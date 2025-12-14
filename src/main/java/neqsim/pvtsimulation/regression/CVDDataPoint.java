package neqsim.pvtsimulation.regression;

/**
 * Data point for Constant Volume Depletion (CVD) experiment.
 *
 * @author ESOL
 * @version 1.0
 */
public class CVDDataPoint {
  private double pressure;
  private double liquidDropout;
  private double zFactor;
  private double temperature;
  private double[] gasComposition;
  private double cumulativeMolesProduced = Double.NaN;

  /**
   * Create a CVD data point.
   *
   * @param pressure pressure in bar
   * @param liquidDropout liquid dropout volume %
   * @param zFactor gas compressibility factor
   * @param temperature temperature in K
   */
  public CVDDataPoint(double pressure, double liquidDropout, double zFactor, double temperature) {
    this.pressure = pressure;
    this.liquidDropout = liquidDropout;
    this.zFactor = zFactor;
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
   * Get liquid dropout.
   *
   * @return liquid dropout volume %
   */
  public double getLiquidDropout() {
    return liquidDropout;
  }

  /**
   * Set liquid dropout.
   *
   * @param liquidDropout liquid dropout volume %
   */
  public void setLiquidDropout(double liquidDropout) {
    this.liquidDropout = liquidDropout;
  }

  /**
   * Get gas compressibility factor.
   *
   * @return Z-factor
   */
  public double getZFactor() {
    return zFactor;
  }

  /**
   * Set gas compressibility factor.
   *
   * @param zFactor Z-factor
   */
  public void setZFactor(double zFactor) {
    this.zFactor = zFactor;
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
   * Get produced gas composition.
   *
   * @return gas composition mole fractions
   */
  public double[] getGasComposition() {
    return gasComposition;
  }

  /**
   * Set produced gas composition.
   *
   * @param gasComposition gas composition mole fractions
   */
  public void setGasComposition(double[] gasComposition) {
    this.gasComposition = gasComposition;
  }

  /**
   * Get cumulative moles produced.
   *
   * @return cumulative moles produced %
   */
  public double getCumulativeMolesProduced() {
    return cumulativeMolesProduced;
  }

  /**
   * Set cumulative moles produced.
   *
   * @param cumulativeMolesProduced cumulative moles produced %
   */
  public void setCumulativeMolesProduced(double cumulativeMolesProduced) {
    this.cumulativeMolesProduced = cumulativeMolesProduced;
  }
}
