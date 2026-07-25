package neqsim.process.equipment.stream;

/**
 * Shared rotating shaft that balances mechanical power producers and loads.
 *
 * <p>
 * Producers add positive contributions with {@link #setGeneratedPower(String, double)}, while compressors, pumps, and
 * other loads add positive demands with {@link #setConsumedPower(String, double)}. The inherited net bus power is
 * positive when generation exceeds demand and negative when the shaft is under-powered.
 *
 * @author NeqSim
 * @version 1.0
 */
public class MechanicalShaft extends EnergyBus {
  private static final long serialVersionUID = 1000L;

  private double speed = 0.0;
  private double momentOfInertia = 0.0;
  private double mechanicalEfficiency = 1.0;

  /** Creates an unnamed shaft. */
  public MechanicalShaft() {
    this("");
  }

  /**
   * Creates a named shaft.
   *
   * @param name shaft name
   */
  public MechanicalShaft(String name) {
    super(name, EnergyType.SHAFT_WORK);
  }

  /**
   * Sets generated shaft power in watts.
   *
   * @param participant producer name
   * @param power generated power in W
   */
  public void setGeneratedPower(String participant, double power) {
    setContribution("producer:" + participant, Math.abs(power) * mechanicalEfficiency);
  }

  /**
   * Sets consumed shaft power in watts.
   *
   * @param participant load name
   * @param power consumed power in W
   */
  public void setConsumedPower(String participant, double power) {
    setContribution("load:" + participant, -Math.abs(power));
  }

  /**
   * Gets shaft speed.
   *
   * @return speed in rpm
   */
  public double getSpeed() {
    return speed;
  }

  /**
   * Sets shaft speed.
   *
   * @param speed speed in rpm
   */
  public void setSpeed(double speed) {
    if (!Double.isFinite(speed) || speed < 0.0) {
      throw new IllegalArgumentException("Shaft speed must be finite and non-negative");
    }
    this.speed = speed;
  }

  /**
   * Gets rotating moment of inertia.
   *
   * @return moment of inertia in kg m2
   */
  public double getMomentOfInertia() {
    return momentOfInertia;
  }

  /**
   * Sets rotating moment of inertia.
   *
   * @param momentOfInertia moment of inertia in kg m2
   */
  public void setMomentOfInertia(double momentOfInertia) {
    if (!Double.isFinite(momentOfInertia) || momentOfInertia < 0.0) {
      throw new IllegalArgumentException("Shaft moment of inertia must be finite and non-negative");
    }
    this.momentOfInertia = momentOfInertia;
  }

  /**
   * Gets mechanical transfer efficiency.
   *
   * @return efficiency in the range (0, 1]
   */
  public double getMechanicalEfficiency() {
    return mechanicalEfficiency;
  }

  /**
   * Sets mechanical transfer efficiency applied to generated power.
   *
   * @param mechanicalEfficiency efficiency in the range (0, 1]
   */
  public void setMechanicalEfficiency(double mechanicalEfficiency) {
    if (!Double.isFinite(mechanicalEfficiency) || mechanicalEfficiency <= 0.0 || mechanicalEfficiency > 1.0) {
      throw new IllegalArgumentException("Shaft efficiency must be in (0, 1]");
    }
    this.mechanicalEfficiency = mechanicalEfficiency;
  }
}
