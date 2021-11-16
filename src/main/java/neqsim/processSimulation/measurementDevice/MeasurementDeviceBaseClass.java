/*
 * ProcessEquipmentBaseClass.java
 *
 * Created on 6. juni 2006, 15:12
 *
 * To change this template, choose Tools | Template Manager and open the template in the editor.
 */
package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.measurementDevice.online.OnlineSignal;

/**
 *
 * @author ESOL
 */
public abstract class MeasurementDeviceBaseClass implements MeasurementDeviceInterface {
    private static final long serialVersionUID = 1000;

    /**
     * 
     * @return the onlineSignal
     */
    @Override
    public OnlineSignal getOnlineSignal() {
        return onlineSignal;
    }

    /**
     * @param onlineSignal the onlineSignal to set
     */
    public void setOnlineSignal(OnlineSignal onlineSignal) {
        this.onlineSignal = onlineSignal;
    }

    /**
     * @return the isOnlineSignal
     */
    @Override
    public boolean isOnlineSignal() {
        return isOnlineSignal;
    }

    /**
     * @param isOnlineSignal the isOnlineSignal to set
     */
    public void setIsOnlineSignal(boolean isOnlineSignal, String plantName, String transmitterame) {
        this.isOnlineSignal = isOnlineSignal;
        onlineSignal = new OnlineSignal(plantName, transmitterame);
    }

    protected String name = "default";
    protected String unit = "-";
    private double maximumValue = 1.0;
    private double minimumValue = 0.0;
    private boolean logging = false;
    private OnlineSignal onlineSignal = null;
    private boolean isOnlineSignal = false;
    private double onlineMeasurementValue = 0.0;
    private String onlineMeasurementValueUnit = "";
    private boolean conditionAnalysis = true;
    private String conditionAnalysisMessage = "";
    private double conditionAnalysisMaxDeviation = 0.0;

    /**
     * Creates a new instance of ProcessEquipmentBaseClass
     */
    public MeasurementDeviceBaseClass() {}

    @Override
    public void displayResult() {}

    ;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUnit() {
        return unit;
    }

    @Override
    public void setName(String nameset) {
        name = nameset;
    }

    @Override
    public double getMeasuredValue() {
        return 0.0;
    }

    @Override
    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Override
    public double getMaximumValue() {
        return maximumValue;
    }

    @Override
    public void setMaximumValue(double maximumValue) {
        this.maximumValue = maximumValue;
    }

    @Override
    public double getMinimumValue() {
        return minimumValue;
    }

    @Override
    public void setMinimumValue(double minimumValue) {
        this.minimumValue = minimumValue;
    }

    @Override
    public double getMeasuredPercentValue() {
        return (getMeasuredValue() - minimumValue) / (maximumValue - minimumValue) * 100;
    }

    @Override
    public boolean isLogging() {
        return logging;
    }

    @Override
    public void setLogging(boolean logging) {
        this.logging = logging;
    }

    @Override
    public double getOnlineValue() {
        return getOnlineSignal().getValue();
    }

    @Override
    public double getMeasuredValue(String unit) {
        return 0.0;
    }

    public void setOnlineMeasurementValue(double value, String unit) {
        onlineMeasurementValue = value;
        onlineMeasurementValueUnit = unit;
    }

    public double getOnlineMeasurementValue() {
        return onlineMeasurementValue;
    }

    public boolean doConditionAnalysis() {
        return conditionAnalysis;
    }

    public void setConditionAnalysis(boolean conditionMonitor) {
        this.conditionAnalysis = conditionMonitor;
    }

    public void runConditionAnalysis() {
        if (Math.abs(getMeasuredValue(onlineMeasurementValueUnit)
                - onlineMeasurementValue) < getConditionAnalysisMaxDeviation()) {
            conditionAnalysisMessage = "ok";
        } else {
            conditionAnalysisMessage = "fail";
        }
    }

    public String getConditionAnalysisMessage() {
        return conditionAnalysisMessage;
    }

    public void setQualityCheckMessage(String conditionAnalysisMessage) {
        this.conditionAnalysisMessage = conditionAnalysisMessage;
    }

    public double getConditionAnalysisMaxDeviation() {
        return conditionAnalysisMaxDeviation;
    }

    public void setConditionAnalysisMaxDeviation(double conditionAnalysisMaxDeviation) {
        this.conditionAnalysisMaxDeviation = conditionAnalysisMaxDeviation;
    }
}
