package neqsim.process.equipment.pump;

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
   * @param flow an array of type double
   * @param head an array of type double
   * @param efficiency an array of type double
   */
  public void addCurve(double speed, double[] flow, double[] head, double[] efficiency);

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

  /**
   * Get the flow rate at best efficiency point (BEP).
   *
   * @return flow rate at BEP in m³/hr
   */
  public double getBestEfficiencyFlowRate();

  /**
   * Calculate pump specific speed at BEP.
   *
   * @return specific speed (dimensionless)
   */
  public double getSpecificSpeed();

  /**
   * Check operating status at given flow and speed.
   *
   * @param flow flow rate in m³/hr
   * @param speed pump speed in rpm
   * @return operating status string
   */
  public String getOperatingStatus(double flow, double speed);

  /**
   * Set NPSH (Net Positive Suction Head) required curve.
   *
   * @param npshRequired 2D array of NPSH values [speed][flow] in meters
   */
  public void setNPSHCurve(double[][] npshRequired);

  /**
   * Get NPSH required at specified flow and speed.
   *
   * @param flow flow rate in m³/hr
   * @param speed pump speed in rpm
   * @return NPSH required in meters
   */
  public double getNPSHRequired(double flow, double speed);

  /**
   * Check if NPSH curve data is available.
   *
   * @return true if NPSH curve is available
   */
  public boolean hasNPSHCurve();

  /**
   * Get the reference density used for density correction.
   *
   * @return reference density in kg/m³, or -1.0 if not set
   */
  public double getReferenceDensity();

  /**
   * Set the reference density for density correction.
   *
   * <p>
   * Pump curves are typically measured with water (~998 kg/m³). When pumping fluids with different
   * densities, the head must be corrected: H_actual = H_chart × (ρ_chart / ρ_actual)
   * </p>
   *
   * @param referenceDensity reference fluid density in kg/m³ (use -1.0 to disable correction)
   */
  public void setReferenceDensity(double referenceDensity);

  /**
   * Check if density correction is enabled.
   *
   * @return true if reference density is set and correction will be applied
   */
  public boolean hasDensityCorrection();

  /**
   * Get density-corrected head for a given flow, speed, and actual fluid density.
   *
   * @param flow flow rate in m³/hr
   * @param speed pump speed in rpm
   * @param actualDensity actual fluid density in kg/m³
   * @return corrected head in the unit specified by getHeadUnit()
   */
  public double getCorrectedHead(double flow, double speed, double actualDensity);

  // ============= VISCOSITY CORRECTION METHODS =============

  /**
   * Calculate viscosity correction factors using the Hydraulic Institute (HI) method.
   *
   * @param viscosity kinematic viscosity in cSt (centistokes)
   * @param flowBEP flow at best efficiency point in m³/hr
   * @param headBEP head at best efficiency point in meters
   * @param speed pump speed in rpm
   */
  public void calculateViscosityCorrection(double viscosity, double flowBEP, double headBEP,
      double speed);

  /**
   * Get head with both viscosity and density corrections applied.
   *
   * @param flow flow rate in m³/hr
   * @param speed pump speed in rpm
   * @param actualDensity actual fluid density in kg/m³
   * @param actualViscosity actual kinematic viscosity in cSt
   * @return fully corrected head
   */
  public double getFullyCorrectedHead(double flow, double speed, double actualDensity,
      double actualViscosity);

  /**
   * Get efficiency with viscosity correction applied.
   *
   * @param flow flow rate in m³/hr
   * @param speed pump speed in rpm
   * @param actualViscosity actual kinematic viscosity in cSt
   * @return corrected efficiency in percent
   */
  public double getCorrectedEfficiency(double flow, double speed, double actualViscosity);

  /**
   * Set the reference viscosity for viscosity correction.
   *
   * @param referenceViscosity reference kinematic viscosity in cSt (typically 1.0 for water)
   */
  public void setReferenceViscosity(double referenceViscosity);

  /**
   * Get the reference viscosity.
   *
   * @return reference viscosity in cSt
   */
  public double getReferenceViscosity();

  /**
   * Enable or disable viscosity correction.
   *
   * @param useViscosityCorrection true to enable viscosity correction
   */
  public void setUseViscosityCorrection(boolean useViscosityCorrection);

  /**
   * Check if viscosity correction is enabled.
   *
   * @return true if viscosity correction is active
   */
  public boolean isUseViscosityCorrection();

  /**
   * Get the current flow correction factor (Cq).
   *
   * @return flow correction factor
   */
  public double getFlowCorrectionFactor();

  /**
   * Get the current head correction factor (Ch).
   *
   * @return head correction factor
   */
  public double getHeadCorrectionFactor();

  /**
   * Get the current efficiency correction factor (Cη).
   *
   * @return efficiency correction factor
   */
  public double getEfficiencyCorrectionFactor();
}
