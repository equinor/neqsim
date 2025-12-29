package neqsim.process.equipment.compressor;

/**
 * <p>
 * CompressorChartInterface interface.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public interface CompressorChartInterface extends Cloneable {
  /**
   * This method is used add a curve to the CompressorChart object.
   *
   * @param speed a double
   * @param flow an array of type double
   * @param head an array of type double
   * @param polytropicEfficiency an array of type double
   */
  public void addCurve(double speed, double[] flow, double[] head, double[] polytropicEfficiency);

  /**
   * This method is used add a curve to the CompressorChart object.
   *
   * @param speed a double
   * @param flowHead an array of type double
   * @param head an array of type double
   * @param flowPolytropicEfficiency an array of type double
   * @param polytropicEfficiency an array of type double
   */
  public void addCurve(double speed, double[] flowHead, double[] head,
      double[] flowPolytropicEfficiency, double[] polytropicEfficiency);

  /**
   * This method is used add a set of curves to the CompressorChart object.
   *
   * @param chartConditions an array of type double
   * @param speed an array of type double
   * @param flow an array of type double
   * @param head an array of type double
   * @param polyEff an array of type double
   */
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] polyEff);

  /**
   * This method is used add a set of curves to the CompressorChart object.
   *
   * @param chartConditions an array of type double
   * @param speed an array of type double
   * @param flow an array of type double
   * @param head an array of type double
   * @param flowPolyEff an array of type double
   * @param polyEff an array of type double
   */
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] flowPolyEff, double[][] polyEff);

  /**
   * Get method for polytropic head from reference curves.
   *
   * @param flow [m3/h], speed in [rpm].
   * @param speed a double
   * @return polytropic head in unit [getHeadUnit]
   */
  public double getPolytropicHead(double flow, double speed);

  /**
   * Get method for polytropic efficiency from reference curves.
   *
   * @param flow [m3/h], speed in [rpm].
   * @param speed a double
   * @return polytropic efficiency [%].
   */
  public double getPolytropicEfficiency(double flow, double speed);

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
  public boolean isUseCompressorChart();

  /**
   * Set compressor calculations to use compressor chart.
   *
   * @param useCompressorChart a boolean
   */
  public void setUseCompressorChart(boolean useCompressorChart);

  /**
   * get the selected unit of head.
   *
   * @return unit of head
   */
  public String getHeadUnit();

  /**
   * set unit of head.
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
   * getSurgeCurve.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.compressor.SafeSplineSurgeCurve} object
   */
  public SafeSplineSurgeCurve getSurgeCurve();

  /**
   * <p>
   * setSurgeCurve.
   * </p>
   *
   * @param surgeCurve a {@link neqsim.process.equipment.compressor.SafeSplineSurgeCurve} object
   */
  public void setSurgeCurve(SafeSplineSurgeCurve surgeCurve);

  /**
   * <p>
   * getStoneWallCurve.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.compressor.StoneWallCurve} object
   */
  public StoneWallCurve getStoneWallCurve();

  /**
   * <p>
   * setStoneWallCurve.
   * </p>
   *
   * @param stoneWallCurve a {@link neqsim.process.equipment.compressor.StoneWallCurve} object
   */
  public void setStoneWallCurve(StoneWallCurve stoneWallCurve);

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

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /** {@inheritDoc} */
  @Override
  public int hashCode();

  /**
   * <p>
   * getFlow.
   * </p>
   *
   * @param head a double
   * @param speed a double
   * @param guessFlow a double
   * @return a double
   */
  public double getFlow(double head, double speed, double guessFlow);

  /**
   * <p>
   * Getter for the field <code>minSpeedCurve</code>.
   * </p>
   *
   * @return a double
   */
  public double getMinSpeedCurve();

  /**
   * <p>
   * generateSurgeCurve.
   * </p>
   */
  public void generateSurgeCurve();

  /**
   * <p>
   * generateStoneWallCurve.
   * </p>
   */
  public void generateStoneWallCurve();

  /**
   * <p>
   * Get the surge flow (minimum flow) at a specific speed.
   * </p>
   *
   * @param speed The compressor speed in RPM
   * @return The surge flow (minimum flow) at the specified speed in m3/hr, or Double.NaN if no
   *         curves exist
   */
  public double getSurgeFlowAtSpeed(double speed);

  /**
   * <p>
   * Get the surge head (polytropic head at minimum flow) at a specific speed.
   * </p>
   *
   * @param speed The compressor speed in RPM
   * @return The surge head at the specified speed in kJ/kg or meter (depending on headUnit), or
   *         Double.NaN if no curves exist
   */
  public double getSurgeHeadAtSpeed(double speed);

  /**
   * <p>
   * Get the stone wall flow (maximum flow) at a specific speed.
   * </p>
   *
   * @param speed The compressor speed in RPM
   * @return The stone wall flow (maximum flow) at the specified speed in m3/hr, or Double.NaN if no
   *         curves exist
   */
  public double getStoneWallFlowAtSpeed(double speed);

  /**
   * <p>
   * Get the stone wall head (polytropic head at maximum flow) at a specific speed.
   * </p>
   *
   * @param speed The compressor speed in RPM
   * @return The stone wall head at the specified speed in kJ/kg or meter (depending on headUnit),
   *         or Double.NaN if no curves exist
   */
  public double getStoneWallHeadAtSpeed(double speed);

  /**
   * Set the inlet stream reference for automatic MW detection.
   *
   * <p>
   * For charts that support MW interpolation (e.g., CompressorChartMWInterpolation), this allows
   * the chart to automatically update the operating molecular weight from the inlet stream's fluid
   * during calculations.
   * </p>
   *
   * @param stream the inlet stream
   */
  public default void setInletStream(neqsim.process.equipment.stream.StreamInterface stream) {
    // Default implementation does nothing - override in charts that support MW interpolation
  }

  /**
   * Get the inlet stream reference.
   *
   * @return the inlet stream, or null if not set
   */
  public default neqsim.process.equipment.stream.StreamInterface getInletStream() {
    return null;
  }

  /**
   * Set the current operating molecular weight for charts that support MW interpolation.
   *
   * @param mw the molecular weight in g/mol
   */
  public default void setOperatingMW(double mw) {
    // Default implementation does nothing - override in charts that support MW interpolation
  }

  /**
   * Get the current operating molecular weight.
   *
   * @return the molecular weight in g/mol, or NaN if not set
   */
  public default double getOperatingMW() {
    return Double.NaN;
  }
}
