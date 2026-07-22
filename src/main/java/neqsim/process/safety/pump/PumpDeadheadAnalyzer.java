package neqsim.process.safety.pump;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Screening calculator for centrifugal-pump deadhead overpressure and minimum-flow recirculation temperature rise.
 *
 * <p>
 * When a pump discharge is blocked in ("No Flow") the pump develops its shut-off head, which for a centrifugal machine
 * is typically 110-130% of the rated differential head. The resulting deadhead discharge pressure is compared against
 * the protected system or casing pressure rating. In addition, when the pump runs against a closed or near-closed
 * discharge, the absorbed power heats the trapped or minimum-flow liquid; the recirculation temperature rise is
 * estimated from the shut-off head and an assumed low efficiency at minimum flow.
 * </p>
 *
 * <p>
 * This is a screening tool: pump curves, NPSH and detailed thermal inventory effects are not modelled. Liquid density
 * and specific heat may be supplied directly or derived from a NeqSim fluid evaluated at the suction conditions.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PumpDeadheadAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(PumpDeadheadAnalyzer.class);

  /** Gravitational acceleration in m/s2. */
  private static final double GRAVITY = 9.80665;

  /** Default shut-off head as a multiple of rated differential head. */
  private static final double DEFAULT_SHUTOFF_RATIO = 1.20;

  /** Default pump efficiency assumed at minimum continuous flow. */
  private static final double DEFAULT_MIN_FLOW_EFFICIENCY = 0.30;

  /** Verdict of a pump deadhead screening. */
  public enum DeadheadVerdict {
    /** No protected pressure rating was supplied. */
    NO_RATING,
    /** Deadhead pressure stays within the protected pressure rating. */
    WITHIN_RATING,
    /** Deadhead pressure exceeds the protected pressure rating. */
    EXCEEDS_RATING
  }

  private double suctionPressureBara = Double.NaN;
  private double suctionTemperatureK = Double.NaN;
  private double normalDischargePressureBara = Double.NaN;
  private double shutoffHeadRatio = DEFAULT_SHUTOFF_RATIO;
  private double protectedPressureRatingBara = Double.NaN;
  private boolean ratingDataProvided = false;
  private double liquidDensityKgPerM3 = Double.NaN;
  private double specificHeatJPerKgK = Double.NaN;
  private double minimumFlowEfficiency = DEFAULT_MIN_FLOW_EFFICIENCY;
  private double maxAllowableTempRiseK = Double.NaN;
  private boolean maxTempRiseProvided = false;
  private SystemInterface fluid = null;

  /**
   * Creates an empty pump deadhead analyzer with default shut-off head ratio 1.20 and default minimum-flow efficiency
   * 0.30.
   */
  public PumpDeadheadAnalyzer() {
  }

  /**
   * Sets the pump suction pressure.
   *
   * @param pressure suction pressure value; must be non-negative
   * @param unit pressure unit accepted by {@link neqsim.util.unit.PressureUnit}
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setSuctionPressure(double pressure, String unit) {
    this.suctionPressureBara = new neqsim.util.unit.PressureUnit(pressure, unit).getValue("bara");
    return this;
  }

  /**
   * Sets the suction temperature used when deriving fluid properties.
   *
   * @param temperature suction temperature value; must be above absolute zero
   * @param unit temperature unit accepted by {@link neqsim.util.unit.TemperatureUnit}
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setSuctionTemperature(double temperature, String unit) {
    this.suctionTemperatureK = new neqsim.util.unit.TemperatureUnit(temperature, unit).getValue("K");
    return this;
  }

  /**
   * Sets the normal (rated) discharge pressure that defines the rated differential head.
   *
   * @param pressure normal discharge pressure value; must exceed the suction pressure
   * @param unit pressure unit accepted by {@link neqsim.util.unit.PressureUnit}
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setNormalDischargePressure(double pressure, String unit) {
    this.normalDischargePressureBara = new neqsim.util.unit.PressureUnit(pressure, unit).getValue("bara");
    return this;
  }

  /**
   * Sets the shut-off head ratio (deadhead head divided by rated differential head).
   *
   * @param ratio shut-off head ratio; must be at least 1.0 (typically 1.1-1.3 for centrifugal pumps)
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setShutoffHeadRatio(double ratio) {
    if (ratio < 1.0) {
      throw new IllegalArgumentException("shut-off head ratio must be at least 1.0");
    }
    this.shutoffHeadRatio = ratio;
    return this;
  }

  /**
   * Sets the protected system or casing pressure rating to compare the deadhead pressure against.
   *
   * @param pressure protected pressure rating value; must be positive
   * @param unit pressure unit accepted by {@link neqsim.util.unit.PressureUnit}
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setProtectedPressureRating(double pressure, String unit) {
    double bara = new neqsim.util.unit.PressureUnit(pressure, unit).getValue("bara");
    if (bara <= 0.0) {
      throw new IllegalArgumentException("protected pressure rating must be positive");
    }
    this.protectedPressureRatingBara = bara;
    this.ratingDataProvided = true;
    return this;
  }

  /**
   * Sets the liquid density directly.
   *
   * @param density liquid density value; must be positive
   * @param unit density unit, currently "kg/m3"
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setLiquidDensity(double density, String unit) {
    if (density <= 0.0) {
      throw new IllegalArgumentException("liquid density must be positive");
    }
    if ("kg/m3".equalsIgnoreCase(unit) || "kg/m^3".equalsIgnoreCase(unit)) {
      this.liquidDensityKgPerM3 = density;
    } else {
      throw new IllegalArgumentException("Unsupported density unit: " + unit);
    }
    return this;
  }

  /**
   * Sets the liquid specific heat directly.
   *
   * @param specificHeat specific heat value; must be positive
   * @param unit specific-heat unit, one of "J/kgK" or "kJ/kgK"
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setSpecificHeat(double specificHeat, String unit) {
    if (specificHeat <= 0.0) {
      throw new IllegalArgumentException("specific heat must be positive");
    }
    if ("J/kgK".equalsIgnoreCase(unit) || "J/kg/K".equalsIgnoreCase(unit)) {
      this.specificHeatJPerKgK = specificHeat;
    } else if ("kJ/kgK".equalsIgnoreCase(unit) || "kJ/kg/K".equalsIgnoreCase(unit)) {
      this.specificHeatJPerKgK = specificHeat * 1000.0;
    } else {
      throw new IllegalArgumentException("Unsupported specific-heat unit: " + unit);
    }
    return this;
  }

  /**
   * Sets the pump efficiency assumed at minimum continuous flow used for the recirculation temperature rise.
   *
   * @param efficiency minimum-flow efficiency; must be in the range (0,1]
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setMinimumFlowEfficiency(double efficiency) {
    if (efficiency <= 0.0 || efficiency > 1.0) {
      throw new IllegalArgumentException("minimum-flow efficiency must be in (0,1]");
    }
    this.minimumFlowEfficiency = efficiency;
    return this;
  }

  /**
   * Sets the maximum allowable recirculation temperature rise.
   *
   * @param temperatureRise allowable temperature rise magnitude; must be positive
   * @param unit temperature-difference unit, one of "K" or "C"
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setMaxAllowableTemperatureRise(double temperatureRise, String unit) {
    if (temperatureRise <= 0.0) {
      throw new IllegalArgumentException("maximum allowable temperature rise must be positive");
    }
    if ("K".equalsIgnoreCase(unit) || "C".equalsIgnoreCase(unit) || "degC".equalsIgnoreCase(unit)) {
      this.maxAllowableTempRiseK = temperatureRise;
    } else {
      throw new IllegalArgumentException("Unsupported temperature-difference unit: " + unit);
    }
    this.maxTempRiseProvided = true;
    return this;
  }

  /**
   * Sets a NeqSim fluid from which liquid density and specific heat are derived at the suction conditions when they are
   * not supplied directly.
   *
   * @param fluid the pumped liquid; must not be null
   * @return this analyzer for chaining
   */
  public PumpDeadheadAnalyzer setFluid(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    this.fluid = fluid;
    return this;
  }

  /**
   * Runs the pump deadhead and minimum-flow screening.
   *
   * @return an immutable {@link PumpDeadheadResult}
   * @throws IllegalStateException if mandatory inputs are missing or non-physical
   */
  public PumpDeadheadResult analyze() {
    List<String> warnings = new ArrayList<String>();
    validateInputs();

    double ratedDifferentialBara = normalDischargePressureBara - suctionPressureBara;
    double shutoffDifferentialBara = shutoffHeadRatio * ratedDifferentialBara;
    double deadheadPressureBara = suctionPressureBara + shutoffDifferentialBara;

    double margin = Double.NaN;
    boolean exceeded = false;
    DeadheadVerdict verdict;
    if (!ratingDataProvided) {
      verdict = DeadheadVerdict.NO_RATING;
      warnings.add("No protected pressure rating supplied; verdict limited to deadhead pressure estimate.");
    } else {
      margin = protectedPressureRatingBara - deadheadPressureBara;
      exceeded = deadheadPressureBara > protectedPressureRatingBara;
      verdict = exceeded ? DeadheadVerdict.EXCEEDS_RATING : DeadheadVerdict.WITHIN_RATING;
    }

    double density = resolveDensity(warnings);
    double cp = resolveSpecificHeat(warnings);

    double shutoffHeadM = Double.NaN;
    double tempRiseK = Double.NaN;
    boolean tempRiseAvailable = false;
    if (!Double.isNaN(density) && !Double.isNaN(cp)) {
      double shutoffDpPa = shutoffDifferentialBara * 1.0e5;
      shutoffHeadM = shutoffDpPa / (density * GRAVITY);
      tempRiseK = shutoffDpPa * (1.0 - minimumFlowEfficiency) / (density * minimumFlowEfficiency * cp);
      tempRiseAvailable = true;
    } else {
      warnings
          .add("Liquid density and/or specific heat unavailable; recirculation temperature rise not" + " evaluated.");
    }

    boolean tempRiseExceeded = false;
    if (maxTempRiseProvided && tempRiseAvailable) {
      tempRiseExceeded = tempRiseK > maxAllowableTempRiseK;
    }

    logger.info("Pump deadhead screening: deadhead {} bara, verdict {}, temp rise {} K", deadheadPressureBara, verdict,
        tempRiseK);

    return new PumpDeadheadResult(suctionPressureBara, normalDischargePressureBara, shutoffHeadRatio,
        ratedDifferentialBara, deadheadPressureBara, protectedPressureRatingBara, ratingDataProvided, margin, exceeded,
        shutoffHeadM, density, cp, minimumFlowEfficiency, tempRiseK, tempRiseAvailable, maxAllowableTempRiseK,
        tempRiseExceeded, verdict, warnings);
  }

  /**
   * Validates that the mandatory inputs are present and physically consistent.
   *
   * @throws IllegalStateException if a mandatory input is missing or non-physical
   */
  private void validateInputs() {
    if (Double.isNaN(suctionPressureBara) || suctionPressureBara < 0.0) {
      throw new IllegalStateException("suction pressure must be set and non-negative");
    }
    if (Double.isNaN(normalDischargePressureBara)) {
      throw new IllegalStateException("normal discharge pressure must be set");
    }
    if (normalDischargePressureBara <= suctionPressureBara) {
      throw new IllegalStateException("normal discharge pressure must be above the suction pressure");
    }
  }

  /**
   * Resolves the liquid density, preferring an explicit value and falling back to a NeqSim fluid.
   *
   * @param warnings list to append warnings to
   * @return the density to use in kg/m3, or NaN when it cannot be determined
   */
  private double resolveDensity(List<String> warnings) {
    if (!Double.isNaN(liquidDensityKgPerM3)) {
      return liquidDensityKgPerM3;
    }
    if (fluid == null) {
      return Double.NaN;
    }
    SystemInterface state = evaluateFluid(warnings);
    if (state == null) {
      return Double.NaN;
    }
    try {
      double rho = state.getDensity("kg/m3");
      if (Double.isNaN(rho) || rho <= 0.0) {
        warnings.add("Fluid density non-physical; recirculation temperature rise not evaluated.");
        return Double.NaN;
      }
      return rho;
    } catch (Exception ex) {
      warnings.add("Failed to evaluate density from fluid (" + ex.getMessage() + ").");
      return Double.NaN;
    }
  }

  /**
   * Resolves the liquid specific heat, preferring an explicit value and falling back to a NeqSim fluid.
   *
   * @param warnings list to append warnings to
   * @return the specific heat to use in J/(kg K), or NaN when it cannot be determined
   */
  private double resolveSpecificHeat(List<String> warnings) {
    if (!Double.isNaN(specificHeatJPerKgK)) {
      return specificHeatJPerKgK;
    }
    if (fluid == null) {
      return Double.NaN;
    }
    SystemInterface state = evaluateFluid(warnings);
    if (state == null) {
      return Double.NaN;
    }
    try {
      double cp = state.getCp("J/kgK");
      if (Double.isNaN(cp) || cp <= 0.0) {
        warnings.add("Fluid specific heat non-physical; recirculation temperature rise not evaluated.");
        return Double.NaN;
      }
      return cp;
    } catch (Exception ex) {
      warnings.add("Failed to evaluate specific heat from fluid (" + ex.getMessage() + ").");
      return Double.NaN;
    }
  }

  /**
   * Flashes a clone of the supplied fluid at the suction conditions.
   *
   * @param warnings list to append warnings to
   * @return the flashed fluid clone, or null if the suction temperature is missing or the flash fails
   */
  private SystemInterface evaluateFluid(List<String> warnings) {
    if (Double.isNaN(suctionTemperatureK) || suctionTemperatureK <= 0.0) {
      warnings.add("Suction temperature not set; cannot derive properties from fluid.");
      return null;
    }
    try {
      SystemInterface state = fluid.clone();
      state.setPressure(suctionPressureBara, "bara");
      state.setTemperature(suctionTemperatureK);
      new neqsim.thermodynamicoperations.ThermodynamicOperations(state).TPflash();
      state.initProperties();
      return state;
    } catch (Exception ex) {
      warnings.add("Failed to flash fluid at suction conditions (" + ex.getMessage() + ").");
      return null;
    }
  }
}
