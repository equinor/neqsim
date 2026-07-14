package neqsim.process.equipment.heatexchanger;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Coolant boiling-margin helper for gas coolers and heat exchangers.
 *
 * <p>
 * On a tempered-water / cooling-medium loop the coolant must stay sub-cooled (above its boiling pressure) relative to
 * the hottest surface it contacts, i.e. the hot process-side inlet temperature. This helper computes the coolant
 * saturation temperature at a given coolant pressure and the resulting sub-cooling margin against a hot-side
 * temperature, and flags a boiling risk. It is a screening aid: it evaluates a thermodynamic bubble-point on a clone of
 * the supplied coolant fluid and never modifies the caller's fluid.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class CoolantBoilingMargin {
  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(CoolantBoilingMargin.class);

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private CoolantBoilingMargin() {
  }

  /**
   * Immutable result of a coolant boiling-margin evaluation.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Coolant pressure used for the evaluation [bara]. */
    private final double coolantPressureBara;
    /** Coolant saturation (boiling) temperature at the coolant pressure [C]. */
    private final double saturationTemperatureC;
    /** Hot-side contact temperature the coolant is screened against [C]. */
    private final double hotSideTemperatureC;
    /** Sub-cooling margin = saturationTemperature - hotSideTemperature [C]. */
    private final double subcoolingMarginC;
    /** Minimum acceptable sub-cooling margin used for the {@code withinMargin} flag [C]. */
    private final double minimumMarginC;

    /**
     * Constructor for Result.
     *
     * @param coolantPressureBara coolant pressure used for the evaluation [bara]
     * @param saturationTemperatureC coolant saturation temperature at that pressure [C] (may be {@link Double#NaN} if
     * the bubble-point flash did not converge)
     * @param hotSideTemperatureC hot-side contact temperature [C]
     * @param minimumMarginC minimum acceptable sub-cooling margin [C]
     */
    public Result(double coolantPressureBara, double saturationTemperatureC, double hotSideTemperatureC,
        double minimumMarginC) {
      this.coolantPressureBara = coolantPressureBara;
      this.saturationTemperatureC = saturationTemperatureC;
      this.hotSideTemperatureC = hotSideTemperatureC;
      this.subcoolingMarginC = saturationTemperatureC - hotSideTemperatureC;
      this.minimumMarginC = minimumMarginC;
    }

    /**
     * Getter for the coolant pressure used.
     *
     * @return coolant pressure [bara]
     */
    public double getCoolantPressureBara() {
      return coolantPressureBara;
    }

    /**
     * Getter for the coolant saturation temperature.
     *
     * @return saturation (boiling) temperature [C], or {@link Double#NaN} if not converged
     */
    public double getSaturationTemperatureC() {
      return saturationTemperatureC;
    }

    /**
     * Getter for the hot-side temperature.
     *
     * @return hot-side contact temperature [C]
     */
    public double getHotSideTemperatureC() {
      return hotSideTemperatureC;
    }

    /**
     * Getter for the sub-cooling margin.
     *
     * @return sub-cooling margin = saturationTemperature - hotSideTemperature [C]
     */
    public double getSubcoolingMarginC() {
      return subcoolingMarginC;
    }

    /**
     * Getter for the minimum acceptable margin.
     *
     * @return minimum acceptable sub-cooling margin [C]
     */
    public double getMinimumMarginC() {
      return minimumMarginC;
    }

    /**
     * Indicates whether the coolant would boil at the evaluated pressure, i.e. its saturation temperature is at or
     * below the hot-side temperature.
     *
     * @return true if the sub-cooling margin is not strictly positive
     */
    public boolean isBoiling() {
      return !Double.isNaN(subcoolingMarginC) && subcoolingMarginC <= 0.0;
    }

    /**
     * Indicates whether the sub-cooling margin meets the requested minimum margin.
     *
     * @return true if the margin is greater than or equal to the minimum acceptable margin
     */
    public boolean isWithinMargin() {
      return !Double.isNaN(subcoolingMarginC) && subcoolingMarginC >= minimumMarginC;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return String.format(
          "CoolantBoilingMargin[P=%.3f bara, Tsat=%.2f C, hotSide=%.2f C, margin=%.2f C, "
              + "boiling=%b, withinMargin=%b]",
          coolantPressureBara, saturationTemperatureC, hotSideTemperatureC, subcoolingMarginC, isBoiling(),
          isWithinMargin());
    }
  }

  /**
   * Evaluate the coolant boiling margin at a given coolant pressure against a hot-side temperature, using a zero
   * minimum-margin criterion.
   *
   * @param coolant coolant fluid (e.g. water / tempered cooling medium); not modified
   * @param coolantPressureBara coolant pressure to evaluate [bara]; must be positive
   * @param hotSideTemperatureC hot process-side contact temperature [C]
   * @return the boiling-margin result
   * @throws IllegalArgumentException if coolant is null or the pressure is not positive
   */
  public static Result evaluate(SystemInterface coolant, double coolantPressureBara, double hotSideTemperatureC) {
    return evaluate(coolant, coolantPressureBara, hotSideTemperatureC, 0.0);
  }

  /**
   * Evaluate the coolant boiling margin at a given coolant pressure against a hot-side temperature.
   *
   * <p>
   * The supplied coolant fluid is cloned; its pressure is set to {@code coolantPressureBara} and a bubble-point
   * temperature flash gives the coolant saturation temperature. The sub-cooling margin is the saturation temperature
   * minus the hot-side temperature. If the bubble-point flash does not converge the saturation temperature (and margin)
   * are {@link Double#NaN}.
   * </p>
   *
   * @param coolant coolant fluid (e.g. water / tempered cooling medium); not modified
   * @param coolantPressureBara coolant pressure to evaluate [bara]; must be positive
   * @param hotSideTemperatureC hot process-side contact temperature [C]
   * @param minimumMarginC minimum acceptable sub-cooling margin [C]; used for the withinMargin flag
   * @return the boiling-margin result
   * @throws IllegalArgumentException if coolant is null or the pressure is not positive
   */
  public static Result evaluate(SystemInterface coolant, double coolantPressureBara, double hotSideTemperatureC,
      double minimumMarginC) {
    if (coolant == null) {
      throw new IllegalArgumentException("coolant fluid cannot be null");
    }
    if (!(coolantPressureBara > 0.0)) {
      throw new IllegalArgumentException("coolantPressureBara must be positive");
    }
    SystemInterface fluid = coolant.clone();
    fluid.setPressure(coolantPressureBara, "bara");
    double saturationTemperatureC;
    try {
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.bubblePointTemperatureFlash();
      saturationTemperatureC = fluid.getTemperature("C");
      if (Double.isNaN(saturationTemperatureC)) {
        logger.warn("bubble point temperature flash returned NaN at {} bara", coolantPressureBara);
      }
    } catch (Exception ex) {
      logger.warn("bubble point temperature flash failed at {} bara: {}", coolantPressureBara, ex.getMessage());
      saturationTemperatureC = Double.NaN;
    }
    return new Result(coolantPressureBara, saturationTemperatureC, hotSideTemperatureC, minimumMarginC);
  }
}
