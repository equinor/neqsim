package neqsim.process.controllerdevice.structure;

import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/**
 * Feed-forward control structure combining a feedback controller with a
 * feed-forward contribution based on a measured disturbance.
 */
public class FeedForwardControllerStructure implements ControlStructureInterface {
  private static final long serialVersionUID = 1000;

  private final ControllerDeviceInterface feedbackController;
  private final MeasurementDeviceInterface disturbanceMeasurement;
  private double feedForwardGain = 0.0;
  private double output = 0.0;
  private boolean isActive = true;

  /**
   * Create a feed-forward control structure.
   *
   * @param feedbackController    feedback controller
   * @param disturbanceMeasurement measurement providing disturbance signal
   */
  public FeedForwardControllerStructure(ControllerDeviceInterface feedbackController,
      MeasurementDeviceInterface disturbanceMeasurement) {
    this.feedbackController = feedbackController;
    this.disturbanceMeasurement = disturbanceMeasurement;
  }

  /**
   * Set gain for feed-forward contribution.
   *
   * @param gain feed-forward gain
   */
  public void setFeedForwardGain(double gain) {
    this.feedForwardGain = gain;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt) {
    if (!isActive) {
      return;
    }

    feedbackController.runTransient(feedbackController.getResponse(), dt);
    output = feedbackController.getResponse()
        + feedForwardGain * disturbanceMeasurement.getMeasuredValue();
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
