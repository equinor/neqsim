package neqsim.process.equipment.valve;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign;

/**
 * <p>
 * SafetyValve class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SafetyValve extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double pressureSpec = 10.0;
  private double fullOpenPressure = 10.0;

  /**
   * Constructor for SafetyValve.
   *
   * @param name name of valve
   */
  public SafetyValve(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for SafetyValve.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.Stream} object
   */
  public SafetyValve(String name, StreamInterface inletStream) {
    super(name, inletStream);
    valveMechanicalDesign = new SafetyValveMechanicalDesign(this);
  }

  @Override
  public void initMechanicalDesign() {
    valveMechanicalDesign = new SafetyValveMechanicalDesign(this);
  }

  /**
   * <p>
   * Getter for the field <code>pressureSpec</code>.
   * </p>
   *
   * @return the pressureSpec
   */
  public double getPressureSpec() {
    return pressureSpec;
  }

  /**
   * <p>
   * Setter for the field <code>pressureSpec</code>.
   * </p>
   *
   * @param pressureSpec the pressureSpec to set
   */
  public void setPressureSpec(double pressureSpec) {
    this.pressureSpec = pressureSpec;
  }

  /**
   * <p>
   * Getter for the field <code>fullOpenPressure</code>.
   * </p>
   *
   * @return the fullOpenPressure
   */
  public double getFullOpenPressure() {
    return fullOpenPressure;
  }

  /**
   * <p>
   * Setter for the field <code>fullOpenPressure</code>.
   * </p>
   *
   * @param fullOpenPressure the fullOpenPressure to set
   */
  public void setFullOpenPressure(double fullOpenPressure) {
    this.fullOpenPressure = fullOpenPressure;
  }
}
