package neqsim.process.util.monitor;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;

/**
 * <p>
 * SeparatorResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SeparatorResponse extends BaseResponse {
  public Double gasLoadFactor;
  public StreamResponse feed, gas, liquid, oil, water;

  // Performance metrics per Equinor TR3500 and API 12J
  public PerformanceMetrics performanceMetrics;

  /**
   * Performance metrics for separator rating studies.
   */
  public static class PerformanceMetrics {
    /** K-value (Souders-Brown factor) at HLL in m/s. */
    public Double kValueAtHLL;
    /** K-value limit per TR3500 in m/s. */
    public Double kValueLimit;
    /** Whether K-value is within limit. */
    public Boolean kValueWithinLimit;

    /** Droplet cut size at HLL in micrometers. */
    public Double dropletCutSizeMicrons;
    /** Droplet cut size limit per TR3500 in micrometers. */
    public Double dropletCutSizeLimit;
    /** Whether droplet cut size is within limit. */
    public Boolean dropletCutSizeWithinLimit;

    /** Inlet nozzle momentum flux in Pa. */
    public Double inletMomentumFluxPa;
    /** Inlet momentum limit in Pa. */
    public Double inletMomentumLimit;
    /** Whether inlet momentum is within limit. */
    public Boolean inletMomentumWithinLimit;

    /** Oil retention time in minutes. */
    public Double oilRetentionTimeMinutes;
    /** Minimum oil retention time per API 12J in minutes. */
    public Double minOilRetentionTime;
    /** Whether oil retention time meets minimum. */
    public Boolean oilRetentionTimeAboveMinimum;

    /** Water retention time in minutes. */
    public Double waterRetentionTimeMinutes;
    /** Minimum water retention time per API 12J in minutes. */
    public Double minWaterRetentionTime;
    /** Whether water retention time meets minimum. */
    public Boolean waterRetentionTimeAboveMinimum;

    /** Whether all performance parameters are within limits. */
    public Boolean allWithinLimits;
  }

  /**
   * <p>
   * Constructor for SeparatorResponse.
   * </p>
   *
   * @param inputSeparator a
   *                       {@link neqsim.process.equipment.separator.ThreePhaseSeparator}
   *                       object
   */
  public SeparatorResponse(ThreePhaseSeparator inputSeparator) {
    super(inputSeparator);
    tagName = inputSeparator.getTagName();
    gasLoadFactor = inputSeparator.getGasLoadFactor();

    feed = new StreamResponse(inputSeparator.getFeedStream());
    if (inputSeparator.getThermoSystem().hasPhaseType("aqueous")) {
      water = new StreamResponse(inputSeparator.getWaterOutStream());
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("oil")) {
      oil = new StreamResponse(inputSeparator.getOilOutStream());
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("gas")) {
      gas = new StreamResponse(inputSeparator.getGasOutStream());
    }

    // Populate performance metrics
    populatePerformanceMetrics(inputSeparator);
  }

  /**
   * <p>
   * Constructor for SeparatorResponse.
   * </p>
   *
   * @param inputSeparator a {@link neqsim.process.equipment.separator.Separator}
   *                       object
   */
  public SeparatorResponse(Separator inputSeparator) {
    name = inputSeparator.getName();
    gasLoadFactor = inputSeparator.getGasLoadFactor();
    feed = new StreamResponse(inputSeparator.getFeedStream());
    if (inputSeparator.getThermoSystem().hasPhaseType("aqueous")
        || inputSeparator.getThermoSystem().hasPhaseType("liquid")
        || inputSeparator.getThermoSystem().hasPhaseType("oil")) {
      liquid = new StreamResponse(inputSeparator.getLiquidOutStream());
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("gas")) {
      gas = new StreamResponse(inputSeparator.getGasOutStream());
    }

    // Populate performance metrics
    populatePerformanceMetrics(inputSeparator);
  }

  /**
   * Populate performance metrics from separator.
   *
   * @param separator the separator to get metrics from
   */
  private void populatePerformanceMetrics(Separator separator) {
    performanceMetrics = new PerformanceMetrics();

    try {
      // K-value
      performanceMetrics.kValueAtHLL = separator.calcKValueAtHLL();
      performanceMetrics.kValueLimit = Separator.DEFAULT_K_VALUE_LIMIT;
      performanceMetrics.kValueWithinLimit = separator.isKValueWithinLimit();

      // Droplet cut size
      double cutSizeMeters = separator.calcDropletCutSizeAtHLL();
      performanceMetrics.dropletCutSizeMicrons = cutSizeMeters * 1e6;
      performanceMetrics.dropletCutSizeLimit = Separator.DEFAULT_DROPLET_CUTSIZE_LIMIT * 1e6;
      performanceMetrics.dropletCutSizeWithinLimit = separator.isDropletCutSizeWithinLimit();

      // Inlet momentum
      performanceMetrics.inletMomentumFluxPa = separator.calcInletMomentumFlux();
      performanceMetrics.inletMomentumLimit = Separator.DEFAULT_INLET_MOMENTUM_LIMIT;
      performanceMetrics.inletMomentumWithinLimit = separator.isInletMomentumWithinLimit();

      // Oil retention time
      double oilRetention = separator.calcOilRetentionTime();
      performanceMetrics.oilRetentionTimeMinutes = Double.isInfinite(oilRetention) ? null : oilRetention;
      performanceMetrics.minOilRetentionTime = Separator.DEFAULT_MIN_OIL_RETENTION_TIME;
      performanceMetrics.oilRetentionTimeAboveMinimum = separator.isOilRetentionTimeAboveMinimum();

      // Water retention time
      double waterRetention = separator.calcWaterRetentionTime();
      performanceMetrics.waterRetentionTimeMinutes = Double.isInfinite(waterRetention) ? null : waterRetention;
      performanceMetrics.minWaterRetentionTime = Separator.DEFAULT_MIN_WATER_RETENTION_TIME;
      performanceMetrics.waterRetentionTimeAboveMinimum = separator.isWaterRetentionTimeAboveMinimum();

      // Overall status
      performanceMetrics.allWithinLimits = separator.isWithinAllLimits();
    } catch (Exception e) {
      // If any calculation fails, leave metrics as null
    }
  }
}
