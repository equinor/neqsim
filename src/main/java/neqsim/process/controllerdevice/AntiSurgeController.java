package neqsim.process.controllerdevice;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * AntiSurgeController is a transient anti-surge regulator (P3) that reads the distance to surge from a
 * {@link neqsim.process.equipment.compressor.Compressor} and drives a recycle
 * {@link neqsim.process.equipment.valve.ThrottlingValve} to keep the machine away from its surge line during trip,
 * blow-down, start-up and load-rejection transients.
 *
 * <p>
 * The controlled variable is the dimensionless surge margin returned by {@link Compressor#getDistanceToSurge()}
 * (operating flow / surge flow - 1). When the margin falls below the configured set point the controller opens the
 * recycle valve using a proportional-integral law; when the margin recovers the valve is closed again. The controller
 * integrates the existing NeqSim transient controller framework
 * ({@link neqsim.process.processmodel.ProcessSystem#runTransient}) and adds no new compressor physics.
 * </p>
 *
 * <p>
 * Output is the recycle valve opening in percent (0..100). The controller applies the opening directly to the recycle
 * valve each transient step, so attaching it through {@code valve.addController("anti-surge", controller)} or
 * {@code compressor.addController("anti-surge", controller)} is sufficient.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AntiSurgeController extends ControllerDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(AntiSurgeController.class);

  /** The compressor whose surge margin is being protected. */
  private Compressor compressor = null;

  /** The recycle valve used as the anti-surge actuator. */
  private ThrottlingValve recycleValve = null;

  /** Surge-margin set point (distance to surge, dimensionless). Default 0.10 = 10%. */
  private double surgeMarginSetPoint = 0.10;

  /** Proportional gain (percent opening per unit margin error). */
  private double proportionalGain = 400.0;

  /** Integral time constant in seconds. */
  private double integralTime = 20.0;

  /** Minimum valve opening in percent. */
  private double minOpening = 0.0;

  /** Maximum valve opening in percent. */
  private double maxOpening = 100.0;

  /** Current valve opening output in percent. */
  private double valveOpening = 0.0;

  /** Accumulated integral term. */
  private double integralState = 0.0;

  /** Last measured surge margin. */
  private double lastMargin = Double.NaN;

  /**
   * Default constructor.
   */
  public AntiSurgeController() {
    super("anti-surge controller");
  }

  /**
   * Constructs an AntiSurgeController with the given name.
   *
   * @param name the controller name
   */
  public AntiSurgeController(String name) {
    super(name);
  }

  /**
   * Constructs an AntiSurgeController wired to a compressor and recycle valve.
   *
   * @param name         the controller name
   * @param compressor   the compressor whose surge margin is protected
   * @param recycleValve the recycle valve actuator
   */
  public AntiSurgeController(String name, Compressor compressor, ThrottlingValve recycleValve) {
    super(name);
    this.compressor = compressor;
    this.recycleValve = recycleValve;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Reads the compressor distance to surge, computes the recycle valve opening with a proportional-integral anti-surge
   * law (reverse acting: low margin opens the valve), clamps the output and applies it to the recycle valve.
   * </p>
   */
  @Override
  public void runTransient(double initResponse, double dt, UUID id) {
    if (!isActive()) {
      valveOpening = initResponse;
      calcIdentifier = id;
      return;
    }
    if (compressor == null) {
      logger.warn("AntiSurgeController has no compressor configured; output held");
      calcIdentifier = id;
      return;
    }
    double margin = compressor.getDistanceToSurge();
    lastMargin = margin;
    if (!Double.isFinite(margin)) {
      // No surge data: hold the valve closed
      valveOpening = clamp(minOpening);
      applyToValve();
      calcIdentifier = id;
      return;
    }

    // Reverse acting: error is positive when the margin is below the set point.
    double error = surgeMarginSetPoint - margin;

    double proportional = proportionalGain * error;
    if (integralTime > 0.0) {
      integralState += proportionalGain / integralTime * error * dt;
    }
    double output = proportional + integralState;

    // Anti-windup clamping.
    if (output > maxOpening) {
      output = maxOpening;
      if (integralTime > 0.0) {
	integralState -= proportionalGain / integralTime * error * dt;
      }
    } else if (output < minOpening) {
      output = minOpening;
      if (integralTime > 0.0) {
	integralState -= proportionalGain / integralTime * error * dt;
      }
    }

    valveOpening = clamp(output);
    applyToValve();
    calcIdentifier = id;
  }

  /**
   * Apply the current valve opening to the recycle valve if one is configured.
   */
  private void applyToValve() {
    if (recycleValve != null) {
      recycleValve.setPercentValveOpening(valveOpening);
    }
  }

  /**
   * Clamp a candidate valve opening to the configured min/max range.
   *
   * @param value the candidate opening in percent
   * @return the clamped opening in percent
   */
  private double clamp(double value) {
    if (value < minOpening) {
      return minOpening;
    }
    if (value > maxOpening) {
      return maxOpening;
    }
    return value;
  }

  /** {@inheritDoc} */
  @Override
  public double getResponse() {
    return valveOpening;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue() {
    return Double.isNaN(lastMargin) && compressor != null ? compressor.getDistanceToSurge() : lastMargin;
  }

  /**
   * Get the current recycle valve opening output.
   *
   * @return the valve opening in percent (0..100)
   */
  public double getValveOpening() {
    return valveOpening;
  }

  /**
   * Get the compressor being protected.
   *
   * @return the compressor, or {@code null} if none is set
   */
  public Compressor getCompressor() {
    return compressor;
  }

  /**
   * Set the compressor whose surge margin is protected.
   *
   * @param compressor the compressor
   */
  public void setCompressor(Compressor compressor) {
    this.compressor = compressor;
  }

  /**
   * Get the recycle valve actuator.
   *
   * @return the recycle valve, or {@code null} if none is set
   */
  public ThrottlingValve getRecycleValve() {
    return recycleValve;
  }

  /**
   * Set the recycle valve actuator.
   *
   * @param recycleValve the recycle valve
   */
  public void setRecycleValve(ThrottlingValve recycleValve) {
    this.recycleValve = recycleValve;
  }

  /**
   * Get the surge-margin set point.
   *
   * @return the surge-margin set point (dimensionless distance to surge)
   */
  public double getSurgeMarginSetPoint() {
    return surgeMarginSetPoint;
  }

  /**
   * Set the surge-margin set point. The recycle valve starts to open when the compressor distance to surge falls below
   * this value.
   *
   * @param surgeMarginSetPoint the surge-margin set point (dimensionless, e.g. 0.10 for 10%)
   */
  public void setSurgeMarginSetPoint(double surgeMarginSetPoint) {
    this.surgeMarginSetPoint = surgeMarginSetPoint;
  }

  /**
   * Get the proportional gain.
   *
   * @return the proportional gain in percent opening per unit margin error
   */
  public double getProportionalGain() {
    return proportionalGain;
  }

  /**
   * Set the proportional gain.
   *
   * @param proportionalGain the proportional gain in percent opening per unit margin error
   */
  public void setProportionalGain(double proportionalGain) {
    this.proportionalGain = proportionalGain;
  }

  /**
   * Get the integral time constant.
   *
   * @return the integral time constant in seconds
   */
  public double getIntegralTime() {
    return integralTime;
  }

  /**
   * Set the integral time constant.
   *
   * @param integralTime the integral time constant in seconds (0 disables integral action)
   */
  public void setIntegralTime(double integralTime) {
    this.integralTime = integralTime;
  }

  /**
   * Set the allowable valve opening range.
   *
   * @param minOpening the minimum opening in percent
   * @param maxOpening the maximum opening in percent
   */
  public void setOpeningRange(double minOpening, double maxOpening) {
    this.minOpening = minOpening;
    this.maxOpening = maxOpening;
  }

  /**
   * Reset the controller integral state and output to fully closed.
   */
  public void reset() {
    this.integralState = 0.0;
    this.valveOpening = minOpening;
    this.lastMargin = Double.NaN;
  }
}
