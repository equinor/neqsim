/*
 * TimeSeries.java
 *
 * Created on 18. juni 2001, 19:24
 */

package neqsim.fluidmechanics.util.timeseries;

import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TimeSeries class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TimeSeries implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected double[] timeSeries, outletMolarFlowRate, outletMolarFlowRates;
  protected SystemInterface[] inletThermoSystem;
  protected SystemInterface[] thermoSystems;
  protected int numberOfTimeStepsInInterval;
  protected double[] times, timeSteps;

  /**
   * <p>
   * Constructor for TimeSeries.
   * </p>
   */
  public TimeSeries() {
    this.timeSeries = new double[1];
  }

  /**
   * <p>
   * Setter for the field <code>times</code>.
   * </p>
   *
   * @param times an array of type double
   */
  public void setTimes(double[] times) {
    this.timeSeries = times;
  }

  /**
   * <p>
   * setInletThermoSystems.
   * </p>
   *
   * @param inletThermoSystem an array of {@link neqsim.thermo.system.SystemInterface} objects
   */
  public void setInletThermoSystems(SystemInterface[] inletThermoSystem) {
    this.inletThermoSystem = inletThermoSystem;
  }

  /**
   * <p>
   * Setter for the field <code>outletMolarFlowRate</code>.
   * </p>
   *
   * @param outletMolarFlowRate an array of type double
   */
  public void setOutletMolarFlowRate(double[] outletMolarFlowRate) {
    this.outletMolarFlowRate = outletMolarFlowRate;
  }

  /**
   * <p>
   * Getter for the field <code>outletMolarFlowRates</code>.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getOutletMolarFlowRates() {
    return this.outletMolarFlowRates;
  }

  /**
   * <p>
   * Setter for the field <code>numberOfTimeStepsInInterval</code>.
   * </p>
   *
   * @param numberOfTimeStepsInInterval a int
   */
  public void setNumberOfTimeStepsInInterval(int numberOfTimeStepsInInterval) {
    this.numberOfTimeStepsInInterval = numberOfTimeStepsInInterval;
  }

  /**
   * <p>
   * init.
   * </p>
   *
   * @param flowSystem a {@link neqsim.fluidmechanics.flowsystem.FlowSystemInterface} object
   */
  public void init(FlowSystemInterface flowSystem) {
    int p = 0;
    thermoSystems = new SystemInterface[(timeSeries.length - 1) * numberOfTimeStepsInInterval];
    outletMolarFlowRates = new double[(timeSeries.length - 1) * numberOfTimeStepsInInterval];
    timeSteps = new double[(timeSeries.length - 1) * numberOfTimeStepsInInterval];
    times = new double[(timeSeries.length - 1) * numberOfTimeStepsInInterval];

    // System.out.println("times " + inletThermoSystem.length);
    double temp = 0;
    for (int k = 0; k < timeSeries.length - 1; k++) {
      double stepLength = (timeSeries[k + 1] - timeSeries[k]) / numberOfTimeStepsInInterval;
      for (int i = 0; i < numberOfTimeStepsInInterval; i++) {
        timeSteps[p] = stepLength;
        temp += stepLength;
        times[p] = temp;
        if (outletMolarFlowRate != null) {
          outletMolarFlowRates[p] = outletMolarFlowRate[k];
        }
        thermoSystems[p++] = inletThermoSystem[k].clone();
      }
    }
  }

  /**
   * <p>
   * getThermoSystem.
   * </p>
   *
   * @return an array of {@link neqsim.thermo.system.SystemInterface} objects
   */
  public SystemInterface[] getThermoSystem() {
    return thermoSystems;
  }

  /**
   * <p>
   * getTimeStep.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getTimeStep() {
    return timeSteps;
  }

  /**
   * <p>
   * getTime.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getTime() {
    return times;
  }

  /**
   * <p>
   * getTime.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getTime(int i) {
    return times[i];
  }
}
