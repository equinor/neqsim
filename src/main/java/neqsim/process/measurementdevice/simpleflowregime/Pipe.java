package neqsim.process.measurementdevice.simpleflowregime;

/**
 * <p>
 * Pipe class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Pipe {
  private String name;
  private double internalDiameter = 0.05;
  private double leftLength = 167.0;
  private double rightLength = 7.7;
  private double angle = 2.0;
  final double pi = 3.1415926;

  // User Defined pipe parameters including pipe name (constructor):
  Pipe(String name, double internalDiameter, double leftLength, double rightLength, double angle) {
    this.setName(name);
    this.setInternalDiameter(internalDiameter);
    this.setLeftLength(leftLength);
    this.setRightLength(rightLength);
    this.setAngle(angle);
  }

  // Encapsulation: Get and Set Methods. This keyword referes to the current object
  // 1. Pipe name encapsulation
  /**
   * <p>
   * Setter for the field <code>name</code>.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * <p>
   * Getter for the field <code>name</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getName() {
    return name;
  }

  // 2. Pipe Internal Diameter encapsulation
  /**
   * <p>
   * Setter for the field <code>internalDiameter</code>.
   * </p>
   *
   * @param internalDiameter a double
   */
  public void setInternalDiameter(double internalDiameter) {
    this.internalDiameter = internalDiameter;
  }

  /**
   * <p>
   * Getter for the field <code>internalDiameter</code>.
   * </p>
   *
   * @return a double
   */
  public double getInternalDiameter() {
    return internalDiameter;
  }

  // 3. Pipe Internal Diameter encapsulation
  /**
   * <p>
   * Setter for the field <code>leftLength</code>.
   * </p>
   *
   * @param leftLength a double
   */
  public void setLeftLength(double leftLength) {
    this.leftLength = leftLength;
  }

  /**
   * <p>
   * Getter for the field <code>leftLength</code>.
   * </p>
   *
   * @return a double
   */
  public double getLeftLength() {
    return leftLength;
  }

  // 4. Pipe Right Length encapsulation
  /**
   * <p>
   * Setter for the field <code>rightLength</code>.
   * </p>
   *
   * @param rightLength a double
   */
  public void setRightLength(double rightLength) {
    this.rightLength = rightLength;
  }

  /**
   * <p>
   * Getter for the field <code>rightLength</code>.
   * </p>
   *
   * @return a double
   */
  public double getRightLength() {
    return rightLength;
  }

  // 4. Pipe Angle encapsulation
  /**
   * <p>
   * Setter for the field <code>angle</code>.
   * </p>
   *
   * @param angle a double
   */
  public void setAngle(double angle) {
    this.angle = angle;
  }

  /**
   * <p>
   * Getter for the field <code>angle</code>.
   * </p>
   *
   * @param unit Unit
   * @return Angle in unit. Defaults to Degree
   */
  public double getAngle(String unit) {
    if (unit.equals("Degree")) {
      return this.angle;
    } else if (unit.equals("Radian")) {
      return this.angle * pi / 180;
    }
    throw new RuntimeException(
        new neqsim.util.exception.InvalidInputException(this, "getAngle", "unit"));
  }

  /**
   * <p>
   * getArea.
   * </p>
   *
   * @return a double
   */
  public double getArea() {
    return pi * Math.pow(this.internalDiameter, 2) / 4;
  }
}
