/*
 * TimeSeries.java
 *
 * Created on 18. juni 2001, 19:24
 */

package neqsim.fluidmechanics.util.timeseries;

import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * TimeSeries class for managing time-varying boundary conditions in transient pipe flow simulations.
 *
 * <p>
 * This class supports three types of outlet boundary conditions:
 * </p>
 * <ul>
 * <li>{@link OutletBoundaryType#PRESSURE} - Specified outlet pressure (default)</li>
 * <li>{@link OutletBoundaryType#FLOW} - Specified outlet flow rate/velocity</li>
 * <li>{@link OutletBoundaryType#CLOSED} - Zero flow at outlet (blocked pipe)</li>
 * </ul>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TimeSeries implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Enum for outlet boundary condition types.
   */
  public enum OutletBoundaryType {
    /** Outlet pressure is specified (velocity computed). */
    PRESSURE,
    /** Outlet flow/velocity is specified (pressure computed). */
    FLOW,
    /** Outlet is closed (zero velocity boundary). */
    CLOSED
  }

  protected double[] timeSeries, outletMolarFlowRate, outletMolarFlowRates;
  protected SystemInterface[] inletThermoSystem;
  protected SystemInterface[] thermoSystems;
  protected int numberOfTimeStepsInInterval;
  protected double[] times, timeSteps;

  /** Outlet boundary condition type. */
  protected OutletBoundaryType outletBoundaryType = OutletBoundaryType.PRESSURE;

  /** Outlet velocities for each time interval (used when outletBoundaryType == FLOW). */
  protected double[] outletVelocity;

  /** Expanded outlet velocities for each time step. */
  protected double[] outletVelocities;

  /** Outlet pressures for each time interval (used when outletBoundaryType == PRESSURE). */
  protected double[] outletPressure;

  /** Expanded outlet pressures for each time step. */
  protected double[] outletPressures;

  /**
   * Constructor for TimeSeries.
   */
  public TimeSeries() {
    this.timeSeries = new double[1];
  }

  /**
   * Setter for the field <code>times</code>.
   *
   * @param times an array of type double
   */
  public void setTimes(double[] times) {
    this.timeSeries = times;
  }

  /**
   * setInletThermoSystems.
   *
   * @param inletThermoSystem an array of {@link neqsim.thermo.system.SystemInterface} objects
   */
  public void setInletThermoSystems(SystemInterface[] inletThermoSystem) {
    this.inletThermoSystem = inletThermoSystem;
  }

  /**
   * Setter for the field <code>outletMolarFlowRate</code>.
   *
   * @param outletMolarFlowRate an array of type double
   */
  public void setOutletMolarFlowRate(double[] outletMolarFlowRate) {
    this.outletMolarFlowRate = outletMolarFlowRate;
  }

  /**
   * Getter for the field <code>outletMolarFlowRates</code>.
   *
   * @return an array of type double
   */
  public double[] getOutletMolarFlowRates() {
    return this.outletMolarFlowRates;
  }

  /**
   * Setter for the field <code>numberOfTimeStepsInInterval</code>.
   *
   * @param numberOfTimeStepsInInterval a int
   */
  public void setNumberOfTimeStepsInInterval(int numberOfTimeStepsInInterval) {
    this.numberOfTimeStepsInInterval = numberOfTimeStepsInInterval;
  }

  /**
   * init.
   *
   * @param flowSystem a {@link neqsim.fluidmechanics.flowsystem.FlowSystemInterface} object
   */
  public void init(FlowSystemInterface flowSystem) {
    int totalSteps = (timeSeries.length - 1) * numberOfTimeStepsInInterval;
    int p = 0;
    thermoSystems = new SystemInterface[totalSteps];
    outletMolarFlowRates = new double[totalSteps];
    timeSteps = new double[totalSteps];
    times = new double[totalSteps];
    outletVelocities = new double[totalSteps];
    outletPressures = new double[totalSteps];

    // Get default outlet pressure from initial flow system state
    double defaultOutletPressure = flowSystem.getNode(flowSystem.getTotalNumberOfNodes() - 1).getBulkSystem()
        .getPressure();

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
        thermoSystems[p] = inletThermoSystem[k].clone();

        // Handle outlet velocity
        if (outletBoundaryType == OutletBoundaryType.CLOSED) {
          outletVelocities[p] = 0.0;
        } else if (outletVelocity != null && k < outletVelocity.length) {
          outletVelocities[p] = outletVelocity[k];
        } else {
          outletVelocities[p] = Double.NaN; // Not specified
        }

        // Handle outlet pressure
        if (outletPressure != null && k < outletPressure.length) {
          outletPressures[p] = outletPressure[k];
        } else {
          outletPressures[p] = defaultOutletPressure;
        }

        p++;
      }
    }
  }

  /**
   * getThermoSystem.
   *
   * @return an array of {@link neqsim.thermo.system.SystemInterface} objects
   */
  public SystemInterface[] getThermoSystem() {
    return thermoSystems;
  }

  /**
   * getTimeStep.
   *
   * @return an array of type double
   */
  public double[] getTimeStep() {
    return timeSteps;
  }

  /**
   * getTime.
   *
   * @return an array of type double
   */
  public double[] getTime() {
    return times;
  }

  /**
   * getTime.
   *
   * @param i a int
   * @return a double
   */
  public double getTime(int i) {
    return times[i];
  }

  /**
   * Getter for the outlet boundary type.
   *
   * @return the outlet boundary type
   */
  public OutletBoundaryType getOutletBoundaryType() {
    return outletBoundaryType;
  }

  /**
   * Setter for the outlet boundary type.
   *
   * @param outletBoundaryType the outlet boundary type to set
   */
  public void setOutletBoundaryType(OutletBoundaryType outletBoundaryType) {
    this.outletBoundaryType = outletBoundaryType;
  }

  /**
   * Sets the outlet as closed (blocked pipe, zero velocity boundary).
   */
  public void setOutletClosed() {
    this.outletBoundaryType = OutletBoundaryType.CLOSED;
  }

  /**
   * Sets controlled outlet velocities for each time interval.
   *
   * @param outletVelocity velocity values for each time interval in m/s
   */
  public void setOutletVelocity(double[] outletVelocity) {
    this.outletVelocity = outletVelocity;
    this.outletBoundaryType = OutletBoundaryType.FLOW;
  }

  /**
   * Gets the outlet velocity for a specific time step.
   *
   * @param timeStep the time step index
   * @return the outlet velocity in m/s, or NaN if not specified
   */
  public double getOutletVelocity(int timeStep) {
    if (outletVelocities == null || timeStep >= outletVelocities.length) {
      return Double.NaN;
    }
    return outletVelocities[timeStep];
  }

  /**
   * Gets the expanded outlet velocities array.
   *
   * @return array of outlet velocities for each time step
   */
  public double[] getOutletVelocities() {
    return outletVelocities;
  }

  /**
   * Sets outlet pressures for each time interval (for pressure-controlled boundary).
   *
   * @param outletPressure pressure values for each time interval in bar
   */
  public void setOutletPressure(double[] outletPressure) {
    this.outletPressure = outletPressure;
    this.outletBoundaryType = OutletBoundaryType.PRESSURE;
  }

  /**
   * Gets the outlet pressure for a specific time step.
   *
   * @param timeStep the time step index
   * @return the outlet pressure in bar
   */
  public double getOutletPressure(int timeStep) {
    if (outletPressures == null || timeStep >= outletPressures.length) {
      return Double.NaN;
    }
    return outletPressures[timeStep];
  }

  /**
   * Gets the expanded outlet pressures array.
   *
   * @return array of outlet pressures for each time step
   */
  public double[] getOutletPressures() {
    return outletPressures;
  }

  /**
   * Checks if the outlet is closed.
   *
   * @return true if the outlet is closed, false otherwise
   */
  public boolean isOutletClosed() {
    return outletBoundaryType == OutletBoundaryType.CLOSED;
  }

  /**
   * Checks if outlet flow is controlled.
   *
   * @return true if outlet flow is specified, false otherwise
   */
  public boolean isOutletFlowControlled() {
    return outletBoundaryType == OutletBoundaryType.FLOW;
  }

  /**
   * Checks if outlet pressure is controlled.
   *
   * @return true if outlet pressure is specified, false otherwise
   */
  public boolean isOutletPressureControlled() {
    return outletBoundaryType == OutletBoundaryType.PRESSURE;
  }
}
