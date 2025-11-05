package neqsim.process.measurementdevice;

import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Push Button instrument for manual activation of equipment (e.g., ESD blowdown valves).
 * 
 * <p>
 * A push button is a simple binary instrument that can be in one of two states: pushed (active) or
 * not pushed (inactive). It is typically used to manually trigger emergency shutdown (ESD) systems,
 * blowdown valves, or other safety-critical equipment.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Binary state: active (pushed) or inactive (not pushed)</li>
 * <li>Can be linked to BlowdownValve for direct activation</li>
 * <li>Manual activation and reset capability</li>
 * <li>Measured value: 1.0 when pushed, 0.0 when not pushed</li>
 * <li>Supports alarm configuration for activation logging</li>
 * </ul>
 * 
 * <p>
 * Typical usage with blowdown valve:
 * 
 * <pre>
 * // Create blowdown valve
 * BlowdownValve bdValve = new BlowdownValve("BD-101", blowdownStream);
 * 
 * // Create push button linked to BD valve
 * PushButton esdButton = new PushButton("ESD-PB-101", bdValve);
 * 
 * // In emergency situation, operator pushes button
 * esdButton.push(); // Activates linked BD valve
 * 
 * // Check button state
 * if (esdButton.isPushed()) {
 *   System.out.println("ESD button is pushed - blowdown active");
 * }
 * 
 * // After emergency is resolved, reset button
 * esdButton.reset(); // Does NOT reset the BD valve - requires separate reset
 * </pre>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PushButton extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Indicates if button is currently pushed (active). */
  private boolean isPushed = false;

  /** Optional blowdown valve that this button controls. */
  private BlowdownValve linkedBlowdownValve = null;

  /** Flag to enable/disable automatic valve activation on push. */
  private boolean autoActivateValve = true;

  /**
   * Constructor for PushButton.
   *
   * @param name name of push button
   */
  public PushButton(String name) {
    super(name, "binary"); // Unit is "binary" (0 or 1)
    setMaximumValue(1.0);
    setMinimumValue(0.0);
  }

  /**
   * Constructor for PushButton with linked BlowdownValve.
   *
   * @param name name of push button
   * @param blowdownValve blowdown valve to control
   */
  public PushButton(String name, BlowdownValve blowdownValve) {
    this(name);
    this.linkedBlowdownValve = blowdownValve;
  }

  /**
   * Links this push button to a blowdown valve.
   * 
   * <p>
   * When the button is pushed, it will automatically activate the linked blowdown valve (if
   * autoActivateValve is true).
   * </p>
   *
   * @param blowdownValve blowdown valve to control
   */
  public void linkToBlowdownValve(BlowdownValve blowdownValve) {
    this.linkedBlowdownValve = blowdownValve;
  }

  /**
   * Gets the linked blowdown valve.
   *
   * @return linked blowdown valve, or null if not linked
   */
  public BlowdownValve getLinkedBlowdownValve() {
    return linkedBlowdownValve;
  }

  /**
   * Pushes the button, activating it.
   * 
   * <p>
   * If a blowdown valve is linked and auto-activation is enabled, this will also activate the
   * valve.
   * </p>
   */
  public void push() {
    isPushed = true;

    // Activate linked blowdown valve if configured
    if (linkedBlowdownValve != null && autoActivateValve) {
      linkedBlowdownValve.activate();
    }
  }

  /**
   * Resets the button to inactive (not pushed) state.
   * 
   * <p>
   * Note: This does NOT reset the linked blowdown valve. The valve must be reset separately for
   * safety reasons - button reset only indicates operator acknowledgment, not system reset.
   * </p>
   */
  public void reset() {
    isPushed = false;
  }

  /**
   * Checks if button is currently pushed.
   *
   * @return true if button is pushed (active)
   */
  public boolean isPushed() {
    return isPushed;
  }

  /**
   * Sets whether the button should automatically activate the linked valve when pushed.
   *
   * @param autoActivate true to enable auto-activation, false to disable
   */
  public void setAutoActivateValve(boolean autoActivate) {
    this.autoActivateValve = autoActivate;
  }

  /**
   * Checks if auto-activation is enabled.
   *
   * @return true if button will automatically activate linked valve when pushed
   */
  public boolean isAutoActivateValve() {
    return autoActivateValve;
  }

  /**
   * Gets the measured value of the push button.
   * 
   * <p>
   * Returns 1.0 if button is pushed (active), 0.0 if not pushed (inactive).
   * </p>
   *
   * @return 1.0 if pushed, 0.0 if not pushed
   */
  @Override
  public double getMeasuredValue() {
    return isPushed ? 1.0 : 0.0;
  }

  /**
   * Gets the measured value in the specified unit.
   * 
   * <p>
   * Push button only supports "binary" unit. Returns 1.0 if pushed, 0.0 if not pushed.
   * </p>
   *
   * @param unit engineering unit (only "binary" or "" supported)
   * @return 1.0 if pushed, 0.0 if not pushed
   */
  @Override
  public double getMeasuredValue(String unit) {
    if (unit == null || unit.isEmpty() || unit.equalsIgnoreCase("binary")) {
      return getMeasuredValue();
    }
    throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
        "getMeasuredValue", "unit", "PushButton only supports 'binary' unit"));
  }

  /**
   * Displays the current state of the push button.
   */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("Push Button: " + getName());
    System.out.println("  State: " + (isPushed ? "PUSHED (ACTIVE)" : "NOT PUSHED (INACTIVE)"));
    System.out.println("  Measured Value: " + getMeasuredValue());
    if (linkedBlowdownValve != null) {
      System.out.println("  Linked BD Valve: " + linkedBlowdownValve.getName());
      System.out.println("  BD Valve Activated: " + linkedBlowdownValve.isActivated());
    }
  }

  /**
   * Gets a string representation of the push button state.
   *
   * @return string describing button state
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getName()).append(" [Push Button] - ");
    sb.append("State: ").append(isPushed ? "PUSHED" : "NOT PUSHED");
    if (linkedBlowdownValve != null) {
      sb.append(", Linked to: ").append(linkedBlowdownValve.getName());
      sb.append(" (").append(linkedBlowdownValve.isActivated() ? "ACTIVATED" : "NOT ACTIVATED")
          .append(")");
    }
    return sb.toString();
  }
}
