package neqsim.process.controllerdevice.structure;

import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/**
 * Ratio control structure adjusting a controller set-point such that the controlled variable
 * maintains a specified ratio to a reference measurement.
 *
 * @author esol
 */
public class RatioControllerStructure implements ControlStructureInterface {
  private static final long serialVersionUID = 1000;

  private final ControllerDeviceInterface controller;
  private final MeasurementDeviceInterface ratioMeasurement;
  private double ratio = 1.0;
  private double output = 0.0;
  private boolean isActive = true;

  /**
   * Create a ratio controller structure.
   *
   * @param controller feedback controller acting on the manipulated variable
   * @param ratioMeasurement measurement providing the base for the ratio
   */
  public RatioControllerStructure(ControllerDeviceInterface controller,
      MeasurementDeviceInterface ratioMeasurement) {
    this.controller = controller;
    this.ratioMeasurement = ratioMeasurement;
  }

  /**
   * Set desired ratio between controlled variable and reference measurement.
   *
   * @param ratio desired ratio
   */
  public void setRatio(double ratio) {
    this.ratio = ratio;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt) {
    if (!isActive) {
      return;
    }

    double setpoint = ratio * ratioMeasurement.getMeasuredValue();
    controller.setControllerSetPoint(setpoint);
    controller.runTransient(controller.getResponse(), dt);
    output = controller.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public double getOutput() {
    return output;
  }

  /** {@inheritDoc} */
  @Override
  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isActive() {
    return isActive;
  }
}
