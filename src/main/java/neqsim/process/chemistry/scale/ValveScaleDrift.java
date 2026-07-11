package neqsim.process.chemistry.scale;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * Couples scale/solids deposition kinetics to the effective flow coefficient of a {@link ThrottlingValve}, so that
 * gradual trim plugging can be simulated as a measurable drift in valve opening.
 *
 * <p>
 * A valve that fouls does not fail suddenly: a deposit layer grows on the trim, the open flow area shrinks, the
 * effective flow coefficient {@code Kv} drops, and the level/pressure controller opens the valve further to hold its
 * setpoint. The observable signature is therefore a slow upward <em>drift</em> of the valve opening until it pins at
 * 100% and control is lost. This class turns a deposit growth rate (mm/yr) into the {@code foulingFraction} consumed by
 * {@link ThrottlingValve#setFoulingFraction(double)}, and predicts the drift.
 *
 * <p>
 * Deposition model: a uniform radial deposit of thickness {@code t} on a circular trim port of diameter {@code d0}
 * reduces the open flow area to {@code A(t)/A0 = ((d0 - 2t)/d0)^2} for {@code t < d0/2}. Because a control valve's flow
 * coefficient is proportional to its discharge area, the area-loss fraction is used directly as the valve fouling
 * fraction: {@code foulingFraction = 1 - A(t)/A0}.
 *
 * <p>
 * The deposit growth rate can be supplied directly with {@link #setGrowthRateMmPerYear(double)} or taken from a
 * {@link ScaleKinetics} result via {@link #setKinetics(ScaleKinetics)} (its effective, rate-controlling growth rate is
 * used). This is a screening-level integrity model (NACE TM0374, NORSOK M-001 informational) intended for relative
 * ranking and time-to-plug estimation, not an absolute mass-balance prediction.
 *
 * <p>
 * Typical usage inside a transient study:
 *
 * <pre>{@code
 * ValveScaleDrift drift = new ValveScaleDrift(levelValve);
 * drift.setPortDiameter(0.05); // 50 mm trim port
 * drift.setKinetics(scaleKinetics); // effective growth rate from ScaleKinetics
 * for (int day = 0; day < 60; day++) {
 *   drift.advance(1.0); // grow deposit one day, update valve foulingFraction
 *   process.run(); // controller compensates → opening drifts up
 * }
 * double daysToPin = drift.predictTimeToPinDays(cleanOpeningPercent);
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ScaleKinetics
 * @see ScaleDepositionAccumulator
 * @see ThrottlingValve#setFoulingFraction(double)
 */
public class ValveScaleDrift implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Seconds per year, used to convert growth rate and time steps. */
  private static final double DAYS_PER_YEAR = 365.25;

  /** The valve whose effective flow coefficient is driven by the deposit. */
  private final transient ThrottlingValve valve;

  /** Trim port (or seat) diameter [m] on which the deposit grows. */
  private double portDiameterM = 0.0;

  /** Deposit growth rate (radial wall recession of the open area) [mm/yr]. */
  private double growthRateMmPerYear = 0.0;

  /** Accumulated deposit thickness [mm]. */
  private double depositThicknessMm = 0.0;

  /** Elapsed simulated time [days]. */
  private double elapsedDays = 0.0;

  /**
   * Constructs a valve scale-drift coupling bound to a throttling valve.
   *
   * @param valve the valve whose effective flow coefficient is reduced by deposition
   */
  public ValveScaleDrift(ThrottlingValve valve) {
    this.valve = valve;
  }

  /**
   * Sets the trim port (or seat) diameter on which the deposit grows.
   *
   * @param portDiameterM port diameter [m]; must be positive
   * @return this for chaining
   */
  public ValveScaleDrift setPortDiameter(double portDiameterM) {
    this.portDiameterM = portDiameterM;
    return this;
  }

  /**
   * Sets the deposit growth rate directly.
   *
   * @param growthRateMmPerYear radial deposit growth rate [mm/yr]; negative values are clamped to zero
   * @return this for chaining
   */
  public ValveScaleDrift setGrowthRateMmPerYear(double growthRateMmPerYear) {
    this.growthRateMmPerYear = Math.max(0.0, growthRateMmPerYear);
    return this;
  }

  /**
   * Sets the deposit growth rate from a scale-kinetics result, using its effective (rate-controlling) growth rate.
   *
   * @param kinetics an evaluated or evaluatable {@link ScaleKinetics} object
   * @return this for chaining
   */
  public ValveScaleDrift setKinetics(ScaleKinetics kinetics) {
    this.growthRateMmPerYear = Math.max(0.0, kinetics.getEffectiveGrowthRateMmYr());
    return this;
  }

  /**
   * Advances the deposit by a time step, updates the accumulated thickness, and applies the resulting fouling fraction
   * to the bound valve.
   *
   * @param days time step [days]; negative values are ignored
   * @return the fouling fraction applied to the valve after this step
   */
  public double advance(double days) {
    if (days > 0.0) {
      depositThicknessMm += growthRateMmPerYear * (days / DAYS_PER_YEAR);
      elapsedDays += days;
    }
    double fouling = getFoulingFraction();
    valve.setFoulingFraction(fouling);
    return fouling;
  }

  /**
   * Returns the open flow-area fraction remaining for the current deposit thickness.
   *
   * @return open-area fraction in the range [0, 1]
   */
  public double getOpenAreaFraction() {
    if (portDiameterM <= 0.0) {
      return 1.0;
    }
    double d0Mm = portDiameterM * 1000.0;
    double openDiameterMm = d0Mm - 2.0 * depositThicknessMm;
    if (openDiameterMm <= 0.0) {
      return 0.0;
    }
    double ratio = openDiameterMm / d0Mm;
    return ratio * ratio;
  }

  /**
   * Returns the current fouling fraction (loss of effective flow coefficient) from the accumulated deposit.
   *
   * @return fouling fraction in the range [0, 1)
   */
  public double getFoulingFraction() {
    double fouling = 1.0 - getOpenAreaFraction();
    if (fouling < 0.0) {
      return 0.0;
    }
    if (fouling >= 1.0) {
      return 0.999999;
    }
    return fouling;
  }

  /**
   * Returns the accumulated deposit thickness.
   *
   * @return deposit thickness [mm]
   */
  public double getDepositThicknessMm() {
    return depositThicknessMm;
  }

  /**
   * Returns the elapsed simulated time.
   *
   * @return elapsed time [days]
   */
  public double getElapsedDays() {
    return elapsedDays;
  }

  /**
   * Returns the effective (fouled) flow coefficient of the bound valve for the current deposit.
   *
   * @return effective flow coefficient Kv (SI)
   */
  public double getEffectiveKv() {
    return valve.getKv() * getOpenAreaFraction();
  }

  /**
   * Estimates the time for the deposit to fully close the trim port (no open flow area).
   *
   * @return time to full plugging [days], or positive infinity if the port never closes (zero growth rate or unset
   * diameter)
   */
  public double getTimeToPlugDays() {
    if (growthRateMmPerYear <= 0.0 || portDiameterM <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    double halfPortMm = portDiameterM * 1000.0 / 2.0;
    double remainingMm = Math.max(0.0, halfPortMm - depositThicknessMm);
    return remainingMm / growthRateMmPerYear * DAYS_PER_YEAR;
  }

  /**
   * Predicts the valve opening required to pass the same flow as a clean valve at a given opening, using a
   * linear-characteristic approximation. As the deposit grows, the opening required to hold the setpoint increases as
   * {@code cleanOpeningPercent / (1 - foulingFraction)}, capped at 100%.
   *
   * @param cleanOpeningPercent the opening [%] the clean valve held to pass the required flow
   * @return the fouled-valve opening [%] required to hold the same flow, capped at 100
   */
  public double predictOpeningPercent(double cleanOpeningPercent) {
    double openArea = getOpenAreaFraction();
    if (openArea <= 0.0) {
      return 100.0;
    }
    double required = cleanOpeningPercent / openArea;
    return Math.min(100.0, required);
  }

  /**
   * Predicts the time for the valve opening to drift up to 100% (loss of controllability) for a valve holding a given
   * clean-valve opening, using a linear-characteristic approximation.
   *
   * <p>
   * The opening pins at 100% once the open-area fraction falls below {@code cleanOpeningPercent / 100}. For a uniform
   * radial deposit this happens at deposit thickness {@code t_pin = (d0/2) * (1 - sqrt(cleanOpeningPercent/100))}.
   *
   * @param cleanOpeningPercent the opening [%] the clean valve held to pass the required flow (0 &lt; value &le; 100)
   * @return time until the opening reaches 100% [days], or positive infinity if it never pins (zero growth rate, unset
   * diameter, or a clean opening already at/above 100%)
   */
  public double predictTimeToPinDays(double cleanOpeningPercent) {
    if (growthRateMmPerYear <= 0.0 || portDiameterM <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    if (cleanOpeningPercent >= 100.0) {
      return 0.0;
    }
    if (cleanOpeningPercent <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    double halfPortMm = portDiameterM * 1000.0 / 2.0;
    double pinThicknessMm = halfPortMm * (1.0 - Math.sqrt(cleanOpeningPercent / 100.0));
    double remainingMm = pinThicknessMm - depositThicknessMm;
    if (remainingMm <= 0.0) {
      return 0.0;
    }
    return remainingMm / growthRateMmPerYear * DAYS_PER_YEAR;
  }

  /**
   * Resets the accumulated deposit and elapsed time, and clears the fouling fraction on the bound valve.
   */
  public void reset() {
    depositThicknessMm = 0.0;
    elapsedDays = 0.0;
    valve.setFoulingFraction(0.0);
  }

  /**
   * Serialises the current drift state to a JSON string.
   *
   * @return a pretty-printed JSON representation
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("valveName", valve.getName());
    map.put("portDiameterM", portDiameterM);
    map.put("growthRateMmPerYear", growthRateMmPerYear);
    map.put("elapsedDays", elapsedDays);
    map.put("depositThicknessMm", depositThicknessMm);
    map.put("openAreaFraction", getOpenAreaFraction());
    map.put("foulingFraction", getFoulingFraction());
    map.put("effectiveKv", getEffectiveKv());
    map.put("timeToPlugDays", getTimeToPlugDays());
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(map);
  }
}
