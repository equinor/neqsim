package neqsim.process.fielddevelopment.tieback.capacity;

import java.io.Serializable;

/**
 * Mapping between production-profile rates and an attached host process model stream.
 *
 * <p>
 * The tie-in planner uses this object when a {@code HostFacility} has an attached process model.
 * The configured stream is scaled to the base-plus-accepted-satellite load, the process is run, and
 * equipment capacity constraints are inspected. Conversion factors translate field-production units
 * into the flow unit used by the NeqSim stream.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class HostTieInPoint implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Process stream reference, for example "Host Feed" or "HP Sep.gasOut". */
  private final String processStreamReference;

  /** Flow unit used when setting the process stream rate. */
  private final String processRateUnit;

  /** Optional fixed base process rate in the process-rate unit. */
  private double baseProcessRate = Double.NaN;

  /** Process-rate-unit factor per MSm3/d gas. */
  private double gasToProcessRateFactor = 1.0;

  /** Process-rate-unit factor per bbl/d oil. */
  private double oilToProcessRateFactor = 0.0;

  /** Process-rate-unit factor per m3/d water. */
  private double waterToProcessRateFactor = 0.0;

  /** Process-rate-unit factor per m3/d total liquid. */
  private double liquidToProcessRateFactor = 0.0;

  /**
   * Creates a tie-in point for a process stream.
   *
   * @param processStreamReference stream reference resolved by {@code ProcessSystem}
   * @param processRateUnit flow unit used by the target stream
   */
  public HostTieInPoint(String processStreamReference, String processRateUnit) {
    this.processStreamReference = processStreamReference;
    this.processRateUnit =
        processRateUnit == null || processRateUnit.trim().isEmpty() ? "kg/hr" : processRateUnit;
  }

  /**
   * Gets the process stream reference.
   *
   * @return stream reference
   */
  public String getProcessStreamReference() {
    return processStreamReference;
  }

  /**
   * Gets the process rate unit.
   *
   * @return process rate unit
   */
  public String getProcessRateUnit() {
    return processRateUnit;
  }

  /**
   * Gets the fixed base process rate.
   *
   * @return fixed base rate, or NaN when the profile-derived rate should be used
   */
  public double getBaseProcessRate() {
    return baseProcessRate;
  }

  /**
   * Sets a fixed base process rate in the process-rate unit.
   *
   * @param baseProcessRate fixed base rate used before satellite addition
   * @return this tie-in point for method chaining
   */
  public HostTieInPoint setBaseProcessRate(double baseProcessRate) {
    this.baseProcessRate = baseProcessRate;
    return this;
  }

  /**
   * Sets the gas conversion factor.
   *
   * @param factor process-rate-unit increment per MSm3/d gas
   * @return this tie-in point for method chaining
   */
  public HostTieInPoint setGasToProcessRateFactor(double factor) {
    this.gasToProcessRateFactor = factor;
    return this;
  }

  /**
   * Sets the oil conversion factor.
   *
   * @param factor process-rate-unit increment per bbl/d oil
   * @return this tie-in point for method chaining
   */
  public HostTieInPoint setOilToProcessRateFactor(double factor) {
    this.oilToProcessRateFactor = factor;
    return this;
  }

  /**
   * Sets the water conversion factor.
   *
   * @param factor process-rate-unit increment per m3/d water
   * @return this tie-in point for method chaining
   */
  public HostTieInPoint setWaterToProcessRateFactor(double factor) {
    this.waterToProcessRateFactor = factor;
    return this;
  }

  /**
   * Sets the total-liquid conversion factor.
   *
   * @param factor process-rate-unit increment per m3/d total liquid
   * @return this tie-in point for method chaining
   */
  public HostTieInPoint setLiquidToProcessRateFactor(double factor) {
    this.liquidToProcessRateFactor = factor;
    return this;
  }

  /**
   * Converts a production load to a process stream rate increment.
   *
   * @param load production load to convert
   * @return process stream rate increment in {@link #getProcessRateUnit()}
   */
  public double toProcessRate(ProductionLoad load) {
    if (load == null) {
      return 0.0;
    }
    return load.getGasRateMSm3d() * gasToProcessRateFactor
        + load.getOilRateBopd() * oilToProcessRateFactor
        + load.getWaterRateM3d() * waterToProcessRateFactor
        + load.getTotalLiquidRateM3d() * liquidToProcessRateFactor;
  }
}
