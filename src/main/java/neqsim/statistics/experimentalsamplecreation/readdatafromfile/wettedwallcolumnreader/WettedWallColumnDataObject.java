/*
 * WettedWallColumnDataObject.java
 *
 * Created on 1. februar 2001, 12:19
 */

package neqsim.statistics.experimentalsamplecreation.readdatafromfile.wettedwallcolumnreader;

import java.sql.Time;
import neqsim.statistics.experimentalsamplecreation.readdatafromfile.DataObject;

/**
 * WettedWallColumnDataObject class.
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class WettedWallColumnDataObject extends DataObject {
  double pressure = 0;
  double inletGasTemperature = 0;
  double outletGasTemperature = 0;
  double inletLiquidTemperature = 0;
  double outletLiquidTemperature = 0;
  double columnWallTemperature = 0;
  double totalGasFlow = 0;
  double co2SupplyFlow = 0;
  double inletLiquidFlow = 0;
  Time time = null;

  /**
   * Constructor for WettedWallColumnDataObject.
   */
  public WettedWallColumnDataObject() {
  }

  /**
   * Setter for the field <code>time</code>.
   *
   * @param time a {@link java.lang.String} object
   */
  public void setTime(String time) {
    this.time = Time.valueOf(time);
  }

  /**
   * Getter for the field <code>time</code>.
   *
   * @return a long
   */
  public long getTime() {
    return this.time.getTime();
  }

  /**
   * Setter for the field <code>pressure</code>.
   *
   * @param pressure a double
   */
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * Getter for the field <code>pressure</code>.
   *
   * @return a double
   */
  public double getPressure() {
    return this.pressure;
  }

  /**
   * Setter for the field <code>inletGasTemperature</code>.
   *
   * @param temperature a double
   */
  public void setInletGasTemperature(double temperature) {
    this.inletGasTemperature = temperature;
  }

  /**
   * Getter for the field <code>inletGasTemperature</code>.
   *
   * @return a double
   */
  public double getInletGasTemperature() {
    return this.inletGasTemperature;
  }

  /**
   * Setter for the field <code>inletLiquidTemperature</code>.
   *
   * @param temperature a double
   */
  public void setInletLiquidTemperature(double temperature) {
    this.inletLiquidTemperature = temperature;
  }

  /**
   * Getter for the field <code>inletLiquidTemperature</code>.
   *
   * @return a double
   */
  public double getInletLiquidTemperature() {
    return this.inletLiquidTemperature;
  }

  /**
   * Setter for the field <code>outletGasTemperature</code>.
   *
   * @param temperature a double
   */
  public void setOutletGasTemperature(double temperature) {
    this.outletGasTemperature = temperature;
  }

  /**
   * Getter for the field <code>outletGasTemperature</code>.
   *
   * @return a double
   */
  public double getOutletGasTemperature() {
    return this.outletGasTemperature;
  }

  /**
   * Setter for the field <code>outletLiquidTemperature</code>.
   *
   * @param temperature a double
   */
  public void setOutletLiquidTemperature(double temperature) {
    this.outletLiquidTemperature = temperature;
  }

  /**
   * Getter for the field <code>outletLiquidTemperature</code>.
   *
   * @return a double
   */
  public double getOutletLiquidTemperature() {
    return this.outletLiquidTemperature;
  }

  /**
   * Setter for the field <code>columnWallTemperature</code>.
   *
   * @param temperature a double
   */
  public void setColumnWallTemperature(double temperature) {
    this.columnWallTemperature = temperature;
  }

  /**
   * Getter for the field <code>columnWallTemperature</code>.
   *
   * @return a double
   */
  public double getColumnWallTemperature() {
    return this.columnWallTemperature;
  }

  /**
   * setInletTotalGasFlow.
   *
   * @param totalGasFlow a double
   */
  public void setInletTotalGasFlow(double totalGasFlow) {
    this.totalGasFlow = totalGasFlow;
  }

  /**
   * getInletTotalGasFlow.
   *
   * @return a double
   */
  public double getInletTotalGasFlow() {
    return this.totalGasFlow;
  }

  /**
   * Setter for the field <code>co2SupplyFlow</code>.
   *
   * @param co2Flow a double
   */
  public void setCo2SupplyFlow(double co2Flow) {
    this.co2SupplyFlow = co2Flow;
  }

  /**
   * Getter for the field <code>co2SupplyFlow</code>.
   *
   * @return a double
   */
  public double getCo2SupplyFlow() {
    return this.co2SupplyFlow;
  }

  /**
   * Setter for the field <code>inletLiquidFlow</code>.
   *
   * @param inletLiquidFlow a double
   */
  public void setInletLiquidFlow(double inletLiquidFlow) {
    this.inletLiquidFlow = inletLiquidFlow;
  }

  /**
   * Getter for the field <code>inletLiquidFlow</code>.
   *
   * @return a double
   */
  public double getInletLiquidFlow() {
    return this.inletLiquidFlow;
  }
}
