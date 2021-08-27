package neqsim.processSimulation.processEquipment.pump;

public interface PumpChartInterface extends Cloneable {

    /**
     * This method is used add a curve to the CompressorChart object
     *
     * @param the speed that the curves are valid for, array with flow values,
     *            arrays with head and efficiency values at the given flow values
     */
    public void addCurve(double speed, double[] flow, double[] head, double[] efficiency);

    /**
     * This method is used add a set of curves to the CompressorChart object
     *
     * @param conditions the chart is valid for( chartConditions = {MW, P_inlet,
     *                   T_inlet, Z_inlet}), array of speeds for the different
     *                   curves and double arrays holding the set of curves
     */
    public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
            double[][] polyEff);

    /**
     * Get method for polytropic head from reference curves.
     *
     * @param  flow [m3/h], speed in [rpm].
     * @return      polytropic head in unit [getHeadUnit]
     */
    public double getHead(double flow, double speed);

    /**
     * Get method for efficiency from reference curves.
     *
     * @param  flow [m3/h], speed in [rpm].
     * @return      efficiency [%].
     */
    public double getEfficiency(double flow, double speed);

    /**
     * Set method for the reference conditions of the compressor chart
     *
     * @param molecular weight, inlet temperature, inlet pressure, Z-factor
     */
    public void setReferenceConditions(double refMW, double refTemperature, double refPressure, double refZ);

    /**
     * Checks if set to use compressor chart for compressor calculations (chart is
     * set for compressor)
     */
    public boolean isUsePumpChart();

    /**
     * Set compressor calculations to use compressor chart
     * 
     * @param true/false flag
     */
    public void setUsePumpChart(boolean useCompressorChart);

    /**
     * get the selected unit of head
     * 
     * @return unit of head
     */
    public String getHeadUnit();

    /**
     * set unit of head
     * 
     * @param unit of head
     */
    public void setHeadUnit(String headUnit);

    /**
     * get method for kappa setting. true = real kappa is used, false = ideal kappa
     * is used
     * 
     * @return true/false flag
     */
    public boolean useRealKappa();

    /**
     * set method for kappa setting. true = real kappa is used, false = ideal kappa
     * is used
     * 
     * @param true/false flag
     */
    public void setUseRealKappa(boolean useRealKappa);

    public int getSpeed(double flow, double head);

    public void plot();

}
