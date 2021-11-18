/*
 * WettedWallColumnDataObject.java
 *
 * Created on 1. februar 2001, 12:19
 */

package neqsim.statistics.experimentalSampleCreation.readDataFromFile.wettedWallColumnReader;

import java.sql.Time;
import neqsim.statistics.experimentalSampleCreation.readDataFromFile.DataObject;

/**
 *
 * @author even solbraa
 * @version
 */
public class WettedWallColumnDataObject extends DataObject {
    private static final long serialVersionUID = 1000;

    double pressure = 0, inletGasTemperature = 0, outletGasTemperature = 0;
    double inletLiquidTemperature = 0, outletLiquidTemperature = 0, columnWallTemperature = 0;
    double totalGasFlow = 0, co2SupplyFlow = 0, inletLiquidFlow = 0;
    Time time = null;

    /** Creates new WettedWallColumnDataObject */
    public WettedWallColumnDataObject() {}

    public void setTime(String time) {
        this.time = Time.valueOf(time);
    }

    public long getTime() {
        return this.time.getTime();
    }

    public void setPressure(double pressure) {
        this.pressure = pressure;
    }

    public double getPressure() {
        return this.pressure;
    }

    public void setInletGasTemperature(double temperature) {
        this.inletGasTemperature = temperature;
    }

    public double getInletGasTemperature() {
        return this.inletGasTemperature;
    }

    public void setInletLiquidTemperature(double temperature) {
        this.inletLiquidTemperature = temperature;
    }

    public double getInletLiquidTemperature() {
        return this.inletLiquidTemperature;
    }

    public void setOutletGasTemperature(double temperature) {
        this.outletGasTemperature = temperature;
    }

    public double getOutletGasTemperature() {
        return this.outletGasTemperature;
    }

    public void setOutletLiquidTemperature(double temperature) {
        this.outletLiquidTemperature = temperature;
    }

    public double getOutletLiquidTemperature() {
        return this.outletLiquidTemperature;
    }

    public void setColumnWallTemperature(double temperature) {
        this.columnWallTemperature = temperature;
    }

    public double getColumnWallTemperature() {
        return this.columnWallTemperature;
    }

    public void setInletTotalGasFlow(double totalGasFlow) {
        this.totalGasFlow = totalGasFlow;
    }

    public double getInletTotalGasFlow() {
        return this.totalGasFlow;
    }

    public void setCo2SupplyFlow(double co2Flow) {
        this.co2SupplyFlow = co2Flow;
    }

    public double getCo2SupplyFlow() {
        return this.co2SupplyFlow;
    }

    public void setInletLiquidFlow(double inletLiquidFlow) {
        this.inletLiquidFlow = inletLiquidFlow;
    }

    public double getInletLiquidFlow() {
        return this.inletLiquidFlow;
    }
}
