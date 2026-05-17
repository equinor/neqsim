package neqsim.process.controllerdevice.structure;

import neqsim.process.controllerdevice.ControllerDeviceInterface;

/**
 * Override (selector) control structure that selects the output of one of two controllers using a
 * HIGH-select or LOW-select strategy. This is commonly used for safety overrides where a
 * temperature or pressure controller can take over from a flow controller when limits are
 * approached.
 *
 * <p>
 * In {@link SelectionType#HIGH_SELECT} mode, the controller with the highest output wins. In
 * {@link SelectionType#LOW_SELECT} mode, the controller with the lowest output wins.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class OverrideControllerStructure implements ControlStructureInterface {
  private static final long serialVersionUID = 1000;

  /**
   * Selection strategy for the override structure.
   */
  public enum SelectionType {
    /** Select the controller output with the highest value. */
    HIGH_SELECT,
    /** Select the controller output with the lowest value. */
    LOW_SELECT
  }

  private final ControllerDeviceInterface primaryController;
  private final ControllerDeviceInterface overrideController;
  private final SelectionType selectionType;
  private double output = 0.0;
  private boolean overrideActive = false;
  private boolean isActive = true;

  /**
   * Create an override control structure.
   *
   * @param primaryController the normal-operation controller
   * @param overrideController the safety/override controller
   * @param selectionType HIGH_SELECT or LOW_SELECT
   */
  public OverrideControllerStructure(ControllerDeviceInterface primaryController,
      ControllerDeviceInterface overrideController, SelectionType selectionType) {
    this.primaryController = primaryController;
    this.overrideController = overrideController;
    this.selectionType = selectionType;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt) {
    if (!isActive) {
      return;
    }
    primaryController.runTransient(primaryController.getResponse(), dt);
    overrideController.runTransient(overrideController.getResponse(), dt);

    double primaryOut = primaryController.getResponse();
    double overrideOut = overrideController.getResponse();

    if (selectionType == SelectionType.HIGH_SELECT) {
      overrideActive = overrideOut > primaryOut;
      output = Math.max(primaryOut, overrideOut);
    } else {
      overrideActive = overrideOut < primaryOut;
      output = Math.min(primaryOut, overrideOut);
    }
  }

  /**
   * Check whether the override controller is currently dominating the output.
   *
   * @return {@code true} if the override controller output was selected, {@code false} if the
   *         primary controller is in control
   */
  public boolean isOverrideActive() {
    return overrideActive;
  }

  /**
   * Get the selection type of this override structure.
   *
   * @return the selection type
   */
  public SelectionType getSelectionType() {
    return selectionType;
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
