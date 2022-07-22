/*
 * WettedWallColumnDataObject.java
 *
 * Created on 1. februar 2001, 12:19
 */

package neqsim.statistics.experimentalSampleCreation.readDataFromFile.wettedWallColumnReader;

import java.sql.Time;
import neqsim.statistics.experimentalSampleCreation.readDataFromFile.DataObject;

/**
 * <p>
 * WettedWallColumnDataObject class.
 * </p>
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
   * <p>
   * Constructor for WettedWallColumnDataObject.
   * </p>
   */
  public WettedWallColumnDataObject() {}

  /**
   * <p>
   * Setter for the field <code>time</code>.
   * </p>
   *
   * @param time a {@link java.lang.String} object
   */
  public void setTime(String time) {
    this.time = Time.valueOf(time);
  }

  /**
   * <p>
   * Getter for the field <code>time</code>.
   * </p>
   *
   * @return a long
   */
  public long getTime() {
    return this.time.getTime();
  }

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   */
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * <p>
   * Getter for the field <code>pressure</code>.
   * </p>
   *
   * @return a double
   */
  public double getPressure() {
    return this.pressure;
  }

  /**
   * <p>
   * Setter for the field <code>inletGasTemperature</code>.
   * </p>
   *
   * @param temperature a double
   */
  public void setInletGasTemperature(double temperature) {
    this.inletGasTemperature = temperature;
  }

  /**
   * <p>
   * Getter for the field <code>inletGasTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getInletGasTemperature() {
    return this.inletGasTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>inletLiquidTemperature</code>.
   * </p>
   *
   * @param temperature a double
   */
  public void setInletLiquidTemperature(double temperature) {
    this.inletLiquidTemperature = temperature;
  }

  /**
   * <p>
   * Getter for the field <code>inletLiquidTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getInletLiquidTemperature() {
    return this.inletLiquidTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>outletGasTemperature</code>.
   * </p>
   *
   * @param temperature a double
   */
  public void setOutletGasTemperature(double temperature) {
    this.outletGasTemperature = temperature;
  }

  /**
   * <p>
   * Getter for the field <code>outletGasTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getOutletGasTemperature() {
    return this.outletGasTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>outletLiquidTemperature</code>.
   * </p>
   *
   * @param temperature a double
   */
  public void setOutletLiquidTemperature(double temperature) {
    this.outletLiquidTemperature = temperature;
  }

  /**
   * <p>
   * Getter for the field <code>outletLiquidTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getOutletLiquidTemperature() {
    return this.outletLiquidTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>columnWallTemperature</code>.
   * </p>
   *
   * @param temperature a double
   */
  public void setColumnWallTemperature(double temperature) {
    this.columnWallTemperature = temperature;
  }

  /**
   * <p>
   * Getter for the field <code>columnWallTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getColumnWallTemperature() {
    return this.columnWallTemperature;
  }

  /**
   * <p>
   * setInletTotalGasFlow.
   * </p>
   *
   * @param totalGasFlow a double
   */
  public void setInletTotalGasFlow(double totalGasFlow) {
    this.totalGasFlow = totalGasFlow;
  }

  /**
   * <p>
   * getInletTotalGasFlow.
   * </p>
   *
   * @return a double
   */
  public double getInletTotalGasFlow() {
    return this.totalGasFlow;
  }

  /**
   * <p>
   * Setter for the field <code>co2SupplyFlow</code>.
   * </p>
   *
   * @param co2Flow a double
   */
  public void setCo2SupplyFlow(double co2Flow) {
    this.co2SupplyFlow = co2Flow;
  }

  /**
   * <p>
   * Getter for the field <code>co2SupplyFlow</code>.
   * </p>
   *
   * @return a double
   */
  public double getCo2SupplyFlow() {
    return this.co2SupplyFlow;
  }

  /**
   * <p>
   * Setter for the field <code>inletLiquidFlow</code>.
   * </p>
   *
   * @param inletLiquidFlow a double
   */
  public void setInletLiquidFlow(double inletLiquidFlow) {
    this.inletLiquidFlow = inletLiquidFlow;
  }

  /**
   * <p>
   * Getter for the field <code>inletLiquidFlow</code>.
   * </p>
   *
   * @return a double
   */
  public double getInletLiquidFlow() {
    return this.inletLiquidFlow;
  }
}
