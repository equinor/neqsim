package neqsim.process.measurementdevice.simpleflowregime;

/**
 * Pipe class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public final class Pipe {
  private String name;
  private double internalDiameter = 0.05;
  private double leftLength = 167.0;
  private double rightLength = 7.7;
  private double angle = 2.0;

  /**
   * Create a flowline-riser geometry.
   *
   * @param name pipe name
   * @param internalDiameter internal diameter in m (same for flowline and riser)
   * @param leftLength length of the flowline upstream of the riser base in m
   * @param rightLength height of the vertical riser in m
   * @param angle downward inclination of the flowline towards the riser base in degrees
   */
  public Pipe(String name, double internalDiameter, double leftLength, double rightLength, double angle) {
    this.setName(name);
    this.setInternalDiameter(internalDiameter);
    this.setLeftLength(leftLength);
    this.setRightLength(rightLength);
    this.setAngle(angle);
  }

  // Encapsulation: Get and Set Methods. This keyword referes to the current object
  // 1. Pipe name encapsulation
  /**
   * Setter for the field <code>name</code>.
   *
   * @param name a {@link java.lang.String} object
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Getter for the field <code>name</code>.
   *
   * @return a {@link java.lang.String} object
   */
  public String getName() {
    return name;
  }

  // 2. Pipe Internal Diameter encapsulation
  /**
   * Setter for the field <code>internalDiameter</code>.
   *
   * @param internalDiameter a double
   */
  public void setInternalDiameter(double internalDiameter) {
    requirePositive(internalDiameter, "internalDiameter");
    this.internalDiameter = internalDiameter;
  }

  /**
   * Getter for the field <code>internalDiameter</code>.
   *
   * @return a double
   */
  public double getInternalDiameter() {
    return internalDiameter;
  }

  // 3. Pipe Internal Diameter encapsulation
  /**
   * Setter for the field <code>leftLength</code>.
   *
   * @param leftLength a double
   */
  public void setLeftLength(double leftLength) {
    requirePositive(leftLength, "leftLength");
    this.leftLength = leftLength;
  }

  /**
   * Getter for the field <code>leftLength</code>.
   *
   * @return a double
   */
  public double getLeftLength() {
    return leftLength;
  }

  // 4. Pipe Right Length encapsulation
  /**
   * Setter for the field <code>rightLength</code>.
   *
   * @param rightLength a double
   */
  public void setRightLength(double rightLength) {
    requirePositive(rightLength, "rightLength");
    this.rightLength = rightLength;
  }

  /**
   * Getter for the field <code>rightLength</code>.
   *
   * @return a double
   */
  public double getRightLength() {
    return rightLength;
  }

  // 4. Pipe Angle encapsulation
  /**
   * Setter for the field <code>angle</code>.
   *
   * @param angle a double
   */
  public void setAngle(double angle) {
    this.angle = angle;
  }

  /**
   * Getter for the field <code>angle</code>.
   *
   * @param unit Unit
   * @return Angle in unit. Defaults to Degree
   */
  public double getAngle(String unit) {
    if (unit.equals("Degree")) {
      return this.angle;
    } else if (unit.equals("Radian")) {
      return Math.toRadians(this.angle);
    }
    throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getAngle", "unit"));
  }

  /**
   * getArea.
   *
   * @return a double
   */
  public double getArea() {
    return Math.PI * Math.pow(this.internalDiameter, 2) / 4;
  }

  private static void requirePositive(double value, String name) {
    if (!(value > 0.0) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be finite and > 0");
    }
  }
}
