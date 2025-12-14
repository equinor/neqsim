package neqsim.pvtsimulation.regression;

/**
 * Data point for separator test experiment.
 *
 * @author ESOL
 * @version 1.0
 */
public class SeparatorDataPoint {
  private double gor;
  private double bo;
  private double apiGravity;
  private double separatorPressure;
  private double separatorTemperature;
  private double reservoirTemperature;
  private double oilDensity = Double.NaN;
  private double gasGravity = Double.NaN;

  /**
   * Create a separator test data point.
   *
   * @param gor gas-oil ratio (Sm³/Sm³)
   * @param bo oil formation volume factor
   * @param apiGravity API gravity
   * @param separatorPressure separator pressure in bar
   * @param separatorTemperature separator temperature in K
   * @param reservoirTemperature reservoir temperature in K
   */
  public SeparatorDataPoint(double gor, double bo, double apiGravity, double separatorPressure,
      double separatorTemperature, double reservoirTemperature) {
    this.gor = gor;
    this.bo = bo;
    this.apiGravity = apiGravity;
    this.separatorPressure = separatorPressure;
    this.separatorTemperature = separatorTemperature;
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Get gas-oil ratio.
   *
   * @return GOR in Sm³/Sm³
   */
  public double getGor() {
    return gor;
  }

  /**
   * Set gas-oil ratio.
   *
   * @param gor GOR in Sm³/Sm³
   */
  public void setGor(double gor) {
    this.gor = gor;
  }

  /**
   * Get oil formation volume factor.
   *
   * @return Bo
   */
  public double getBo() {
    return bo;
  }

  /**
   * Set oil formation volume factor.
   *
   * @param bo Bo
   */
  public void setBo(double bo) {
    this.bo = bo;
  }

  /**
   * Get API gravity.
   *
   * @return API gravity
   */
  public double getApiGravity() {
    return apiGravity;
  }

  /**
   * Set API gravity.
   *
   * @param apiGravity API gravity
   */
  public void setApiGravity(double apiGravity) {
    this.apiGravity = apiGravity;
  }

  /**
   * Get separator pressure.
   *
   * @return separator pressure in bar
   */
  public double getSeparatorPressure() {
    return separatorPressure;
  }

  /**
   * Set separator pressure.
   *
   * @param separatorPressure separator pressure in bar
   */
  public void setSeparatorPressure(double separatorPressure) {
    this.separatorPressure = separatorPressure;
  }

  /**
   * Get separator temperature.
   *
   * @return separator temperature in K
   */
  public double getSeparatorTemperature() {
    return separatorTemperature;
  }

  /**
   * Set separator temperature.
   *
   * @param separatorTemperature separator temperature in K
   */
  public void setSeparatorTemperature(double separatorTemperature) {
    this.separatorTemperature = separatorTemperature;
  }

  /**
   * Get reservoir temperature.
   *
   * @return reservoir temperature in K
   */
  public double getReservoirTemperature() {
    return reservoirTemperature;
  }

  /**
   * Set reservoir temperature.
   *
   * @param reservoirTemperature reservoir temperature in K
   */
  public void setReservoirTemperature(double reservoirTemperature) {
    this.reservoirTemperature = reservoirTemperature;
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
}
