package neqsim.processSimulation.processEquipment.pump;

/**
 * <p>
 * PumpChartInterface interface.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public interface PumpChartInterface extends Cloneable {
  /**
   * This method is used add a curve to the CompressorChart object.
   *
   * @param speed a double
   * @param flow an array of {@link double} objects
   * @param head an array of {@link double} objects
   * @param efficiency an array of {@link double} objects
   */
  public void addCurve(double speed, double[] flow, double[] head, double[] efficiency);

  /**
   * This method is used add a set of curves to the CompressorChart object.
   *
   * @param chartConditions an array of {@link double} objects
   * @param speed an array of {@link double} objects
   * @param flow an array of {@link double} objects
   * @param head an array of {@link double} objects
   * @param polyEff an array of {@link double} objects
   */
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] polyEff);

  /**
   * Get method for polytropic head from reference curves.
   *
   * @param flow [m3/h], speed in [rpm].
   * @param speed a double
   * @return polytropic head in unit [getHeadUnit]
   */
  public double getHead(double flow, double speed);

  /**
   * Get method for efficiency from reference curves.
   *
   * @param flow [m3/h], speed in [rpm].
   * @param speed a double
   * @return efficiency [%].
   */
  public double getEfficiency(double flow, double speed);

  /**
   * Set method for the reference conditions of the compressor chart.
   *
   * @param refMW a double
   * @param refTemperature a double
   * @param refPressure a double
   * @param refZ a double
   */
  public void setReferenceConditions(double refMW, double refTemperature, double refPressure,
      double refZ);

  /**
   * Checks if set to use compressor chart for compressor calculations (chart is set for
   * compressor).
   *
   * @return a boolean
   */
  public boolean isUsePumpChart();

  /**
   * Set compressor calculations to use compressor chart.
   *
   * @param useCompressorChart a boolean
   */
  public void setUsePumpChart(boolean useCompressorChart);

  /**
   * Get the selected unit of head.
   *
   * @return unit of head
   */
  public String getHeadUnit();

  /**
   * Set unit of head.
   *
   * @param headUnit a {@link java.lang.String} object
   */
  public void setHeadUnit(String headUnit);

  /**
   * get method for kappa setting. true = real kappa is used, false = ideal kappa is used
   *
   * @return true/false flag
   */
  public boolean useRealKappa();

  /**
   * set method for kappa setting. true = real kappa is used, false = ideal kappa is used
   *
   * @param useRealKappa a boolean
   */
  public void setUseRealKappa(boolean useRealKappa);

  /**
   * <p>
   * getSpeed.
   * </p>
   *
   * @param flow a double
   * @param head a double
   * @return a int
   */
  public int getSpeed(double flow, double head);

  /**
   * <p>
   * plot.
   * </p>
   */
  public void plot();
}
