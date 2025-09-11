package neqsim.process.controllerdevice.structure;

import neqsim.process.controllerdevice.ControllerDeviceInterface;

/**
 * Cascade control structure where a primary controller sets the set-point of a secondary
 * controller. The output from the secondary controller becomes the final control signal applied to
 * the process.
 *
 * @author esol
 */
public class CascadeControllerStructure implements ControlStructureInterface {
  private static final long serialVersionUID = 1000;

  private final ControllerDeviceInterface primaryController;
  private final ControllerDeviceInterface secondaryController;
  private double output = 0.0;
  private boolean isActive = true;

  /**
   * Create a cascade control structure.
   *
   * @param primaryController outer loop controller
   * @param secondaryController inner loop controller
   */
  public CascadeControllerStructure(ControllerDeviceInterface primaryController,
      ControllerDeviceInterface secondaryController) {
    this.primaryController = primaryController;
    this.secondaryController = secondaryController;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt) {
    if (!isActive) {
      return;
    }

    // Run primary controller first
    primaryController.runTransient(primaryController.getResponse(), dt);

    // Use primary response as set-point for the secondary controller
    secondaryController.setControllerSetPoint(primaryController.getResponse());

    // Run secondary controller to obtain final output
    secondaryController.runTransient(secondaryController.getResponse(), dt);
    output = secondaryController.getResponse();
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
