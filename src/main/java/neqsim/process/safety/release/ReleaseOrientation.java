package neqsim.process.safety.release;

/**
 * Enumeration of release orientations for leak and rupture scenarios.
 *
 * <p>
 * The release orientation affects:
 * <ul>
 * <li>Jet trajectory and dispersion</li>
 * <li>Pool formation for liquid releases</li>
 * <li>Impingement on nearby equipment</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public enum ReleaseOrientation {

  /** Horizontal release - typical for pipe and flange leaks. */
  HORIZONTAL(0.0, "Horizontal release"),

  /** Vertical upward release - typical for vent releases. */
  VERTICAL_UP(90.0, "Vertical upward release"),

  /** Vertical downward release - typical for drain leaks. */
  VERTICAL_DOWN(-90.0, "Vertical downward release"),

  /** 45 degrees upward. */
  ANGLED_UP_45(45.0, "45° upward release"),

  /** 45 degrees downward. */
  ANGLED_DOWN_45(-45.0, "45° downward release");

  private final double angle; // degrees from horizontal
  private final String description;

  ReleaseOrientation(double angle, String description) {
    this.angle = angle;
    this.description = description;
  }

  /**
   * Gets the release angle from horizontal.
   *
   * @return angle in degrees (-90 to +90)
   */
  public double getAngle() {
    return angle;
  }

  /**
   * Gets the description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Checks if release is predominantly horizontal.
   *
   * @return true if angle is within ±30° of horizontal
   */
  public boolean isHorizontal() {
    return Math.abs(angle) <= 30.0;
  }

  /**
   * Checks if release is predominantly vertical.
   *
   * @return true if angle is within 30° of vertical
   */
  public boolean isVertical() {
    return Math.abs(angle) >= 60.0;
  }

  @Override
  public String toString() {
    return description;
  }
}
