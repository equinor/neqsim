package neqsim.pvtsimulation.regression;

/**
 * Data point for Differential Liberation Expansion (DLE) experiment.
 *
 * @author ESOL
 * @version 1.0
 */
public class DLEDataPoint {
  private double pressure;
  private double rs;
  private double bo;
  private double oilDensity;
  private double temperature;
  private double gasGravity = Double.NaN;
  private double oilViscosity = Double.NaN;

  /**
   * Create a DLE data point.
   *
   * @param pressure pressure in bar
   * @param rs solution gas-oil ratio (Sm³/Sm³)
   * @param bo oil formation volume factor (m³/Sm³)
   * @param oilDensity oil density (kg/m³)
   * @param temperature temperature in K
   */
  public DLEDataPoint(double pressure, double rs, double bo, double oilDensity,
      double temperature) {
    this.pressure = pressure;
    this.rs = rs;
    this.bo = bo;
    this.oilDensity = oilDensity;
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
   * Get solution gas-oil ratio.
   *
   * @return Rs in Sm³/Sm³
   */
  public double getRs() {
    return rs;
  }

  /**
   * Set solution gas-oil ratio.
   *
   * @param rs Rs in Sm³/Sm³
   */
  public void setRs(double rs) {
    this.rs = rs;
  }

  /**
   * Get oil formation volume factor.
   *
   * @return Bo in m³/Sm³
   */
  public double getBo() {
    return bo;
  }

  /**
   * Set oil formation volume factor.
   *
   * @param bo Bo in m³/Sm³
   */
  public void setBo(double bo) {
    this.bo = bo;
  }

  /**
   * Get oil density.
   *
   * @return oil density in kg/m³
   */
  public double getOilDensity() {
    return oilDensity;
  }

  /**
   * Set oil density.
   *
   * @param oilDensity oil density in kg/m³
   */
  public void setOilDensity(double oilDensity) {
    this.oilDensity = oilDensity;
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
   * Get gas gravity.
   *
   * @return gas gravity (relative to air)
   */
  public double getGasGravity() {
    return gasGravity;
  }

  /**
   * Set gas gravity.
   *
   * @param gasGravity gas gravity (relative to air)
   */
  public void setGasGravity(double gasGravity) {
    this.gasGravity = gasGravity;
  }

  /**
   * Get oil viscosity.
   *
   * @return oil viscosity in cP
   */
  public double getOilViscosity() {
    return oilViscosity;
  }

  /**
   * Set oil viscosity.
   *
   * @param oilViscosity oil viscosity in cP
   */
  public void setOilViscosity(double oilViscosity) {
    this.oilViscosity = oilViscosity;
  }
}
