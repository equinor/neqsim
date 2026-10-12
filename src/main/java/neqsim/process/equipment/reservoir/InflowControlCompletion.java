package neqsim.process.equipment.reservoir;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.reservoir.InflowControlDevice.Mixture;

/**
 * Multi-zone well completion with inflow control devices, for screening how an ICD, AICD, AICV or DAR changes the
 * produced oil, water and gas.
 *
 * <p>
 * Each zone has a linear productivity index on the in-situ total flow and a fixed in-situ phase split (water and gas
 * volume fractions). The zones share one drawdown between the reservoir and the tubing. A device in series with a zone
 * takes a share of that drawdown that grows with the flow and, for an AICD, AICV or DAR, with the water or gas fraction
 * of the zone. The flow of each zone solves {@code q / PI + dP_device(q) = drawdown}.
 * </p>
 *
 * <p>
 * The key screening output is {@link #compareAtSameOil(double, double)}: the device is given the extra drawdown that
 * restores the oil rate of the bare completion, and the water and gas reductions at that oil rate are reported. The
 * phase split of a zone is an input and does not change with drawdown (no coning, no relative-permeability feedback),
 * so the result is an upper bound of the benefit for a zone whose water or gas comes from drawdown-driven coning.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class InflowControlCompletion implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** One productive interval of the completion. */
  public static final class Zone implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Zone name. */
    private final String name;
    /** Productivity index on the in-situ total flow [m3/d per bar]. */
    private final double productivityIndex;
    /** Water volume fraction of the in-situ flow [-]. */
    private final double waterFraction;
    /** Gas volume fraction of the in-situ flow [-]. */
    private final double gasFraction;

    /**
     * Creates a zone.
     *
     * @param name zone name
     * @param productivityIndex productivity index on the in-situ total flow [m3/d per bar]
     * @param waterFraction water volume fraction of the in-situ flow [-]
     * @param gasFraction gas volume fraction of the in-situ flow [-]
     */
    public Zone(String name, double productivityIndex, double waterFraction, double gasFraction) {
      this.name = name;
      this.productivityIndex = productivityIndex;
      this.waterFraction = waterFraction;
      this.gasFraction = gasFraction;
    }

    /**
     * Zone name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Productivity index.
     *
     * @return in-situ productivity index [m3/d per bar]
     */
    public double getProductivityIndex() {
      return productivityIndex;
    }

    /**
     * Water volume fraction.
     *
     * @return water fraction [-]
     */
    public double getWaterFraction() {
      return waterFraction;
    }

    /**
     * Gas volume fraction.
     *
     * @return gas fraction [-]
     */
    public double getGasFraction() {
      return gasFraction;
    }
  }

  /** Flow result of a completion at one drawdown. */
  public static final class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Drawdown [bar]. */
    private final double drawdownBar;
    /** Whether the device was in service. */
    private final boolean deviceInService;
    /** In-situ flow per zone [m3/d]. */
    private final double[] zoneFlow;
    /** Pressure drop over the device per zone [bar]. */
    private final double[] zoneDeviceDp;
    /** Standard oil rate [Sm3/d]. */
    private final double oilRate;
    /** Standard water rate [Sm3/d]. */
    private final double waterRate;
    /** Standard gas rate [Sm3/d]. */
    private final double gasRate;

    /**
     * Creates a result.
     *
     * @param drawdownBar drawdown [bar]
     * @param deviceInService whether the device was in service
     * @param zoneFlow in-situ flow per zone [m3/d]
     * @param zoneDeviceDp device pressure drop per zone [bar]
     * @param oilRate standard oil rate [Sm3/d]
     * @param waterRate standard water rate [Sm3/d]
     * @param gasRate standard gas rate [Sm3/d]
     */
    Result(double drawdownBar, boolean deviceInService, double[] zoneFlow, double[] zoneDeviceDp, double oilRate,
        double waterRate, double gasRate) {
      this.drawdownBar = drawdownBar;
      this.deviceInService = deviceInService;
      this.zoneFlow = zoneFlow.clone();
      this.zoneDeviceDp = zoneDeviceDp.clone();
      this.oilRate = oilRate;
      this.waterRate = waterRate;
      this.gasRate = gasRate;
    }

    /**
     * Drawdown.
     *
     * @return drawdown [bar]
     */
    public double getDrawdownBar() {
      return drawdownBar;
    }

    /**
     * Whether the device was in service.
     *
     * @return true when the device was applied
     */
    public boolean isDeviceInService() {
      return deviceInService;
    }

    /**
     * Standard oil rate.
     *
     * @return oil rate [Sm3/d]
     */
    public double getOilRate() {
      return oilRate;
    }

    /**
     * Standard water rate.
     *
     * @return water rate [Sm3/d]
     */
    public double getWaterRate() {
      return waterRate;
    }

    /**
     * Standard gas rate.
     *
     * @return gas rate [Sm3/d]
     */
    public double getGasRate() {
      return gasRate;
    }

    /**
     * Water cut at standard conditions.
     *
     * @return water cut [-], zero when nothing flows
     */
    public double getWaterCut() {
      double liquid = oilRate + waterRate;
      return liquid > 0.0 ? waterRate / liquid : 0.0;
    }

    /**
     * Gas-oil ratio at standard conditions.
     *
     * @return GOR [Sm3/Sm3], zero when no oil flows
     */
    public double getGor() {
      return oilRate > 0.0 ? gasRate / oilRate : 0.0;
    }

    /**
     * In-situ flow of one zone.
     *
     * @param zone zone index
     * @return in-situ flow [m3/d]
     */
    public double getZoneFlow(int zone) {
      return zoneFlow[zone];
    }

    /**
     * Device pressure drop of one zone.
     *
     * @param zone zone index
     * @return pressure drop [bar]
     */
    public double getZoneDeviceDp(int zone) {
      return zoneDeviceDp[zone];
    }
  }

  /** Bare completion against a completion with the device at equal oil rate. */
  public static final class Comparison implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Bare completion result. */
    private final Result bare;
    /** Result with the device at the drawdown that restores the oil rate. */
    private final Result withDevice;
    /** Whether the oil rate of the bare completion was reached within the drawdown limit. */
    private final boolean oilRateReached;

    /**
     * Creates a comparison.
     *
     * @param bare bare completion result
     * @param withDevice result with device
     * @param oilRateReached whether the oil rate was reached
     */
    Comparison(Result bare, Result withDevice, boolean oilRateReached) {
      this.bare = bare;
      this.withDevice = withDevice;
      this.oilRateReached = oilRateReached;
    }

    /**
     * Bare completion result.
     *
     * @return result without device
     */
    public Result getBare() {
      return bare;
    }

    /**
     * Result with device.
     *
     * @return result with device
     */
    public Result getWithDevice() {
      return withDevice;
    }

    /**
     * Whether the oil rate of the bare completion was reached within the drawdown limit.
     *
     * @return true when reached
     */
    public boolean isOilRateReached() {
      return oilRateReached;
    }

    /**
     * Extra drawdown the device needs for the same oil rate.
     *
     * @return extra drawdown [bar]
     */
    public double getExtraDrawdownBar() {
      return withDevice.getDrawdownBar() - bare.getDrawdownBar();
    }

    /**
     * Reduction of the water rate at the same oil rate.
     *
     * @return fraction between 0 and 1 (negative if water increases), zero when the bare case has no water
     */
    public double getWaterReduction() {
      return bare.getWaterRate() > 0.0 ? 1.0 - withDevice.getWaterRate() / bare.getWaterRate() : 0.0;
    }

    /**
     * Reduction of the gas rate at the same oil rate.
     *
     * @return fraction between 0 and 1 (negative if gas increases), zero when the bare case has no gas
     */
    public double getGasReduction() {
      return bare.getGasRate() > 0.0 ? 1.0 - withDevice.getGasRate() / bare.getGasRate() : 0.0;
    }
  }

  /** Completion name. */
  private final String name;
  /** Productive zones. */
  private final List<Zone> zones = new ArrayList<Zone>();
  /** Device applied to every zone, or null for a bare completion. */
  private InflowControlDevice device;
  /** Oil density [kg/m3]. */
  private double oilDensity = 800.0;
  /** Water density [kg/m3]. */
  private double waterDensity = 1030.0;
  /** Gas density at downhole conditions [kg/m3]. */
  private double gasDensity = 150.0;
  /** Oil viscosity [Pa s]. */
  private double oilViscosity = 0.002;
  /** Water viscosity [Pa s]. */
  private double waterViscosity = 0.0005;
  /** Gas viscosity [Pa s]. */
  private double gasViscosity = 2.0e-5;
  /** Oil formation volume factor [m3/Sm3]. */
  private double bo = 1.0;
  /** Water formation volume factor [m3/Sm3]. */
  private double bw = 1.0;
  /** Gas formation volume factor [m3/Sm3]. */
  private double bg = 1.0;

  /**
   * Creates an empty completion.
   *
   * @param name completion name
   */
  public InflowControlCompletion(String name) {
    this.name = name;
  }

  /**
   * Completion name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Adds a productive zone.
   *
   * @param zoneName zone name
   * @param productivityIndexM3DayBar productivity index on the in-situ total flow [m3/d per bar]
   * @param waterFraction water volume fraction of the in-situ flow [-]
   * @param gasFraction gas volume fraction of the in-situ flow [-]
   * @return this completion
   * @throws IllegalArgumentException if the productivity index is not positive or the fractions are inconsistent
   */
  public InflowControlCompletion addZone(String zoneName, double productivityIndexM3DayBar, double waterFraction,
      double gasFraction) {
    if (productivityIndexM3DayBar <= 0.0) {
      throw new IllegalArgumentException("productivity index must be positive");
    }
    if (waterFraction < 0.0 || gasFraction < 0.0 || waterFraction + gasFraction > 1.0 + 1.0e-12) {
      throw new IllegalArgumentException("fractions must be non-negative and sum to at most one");
    }
    zones.add(new Zone(zoneName, productivityIndexM3DayBar, waterFraction, gasFraction));
    return this;
  }

  /**
   * Sets the phase densities and viscosities at downhole conditions.
   *
   * @param oilDensity oil density [kg/m3]
   * @param waterDensity water density [kg/m3]
   * @param gasDensity gas density [kg/m3]
   * @param oilViscosity oil viscosity [Pa s]
   * @param waterViscosity water viscosity [Pa s]
   * @param gasViscosity gas viscosity [Pa s]
   * @return this completion
   */
  public InflowControlCompletion setFluidProperties(double oilDensity, double waterDensity, double gasDensity,
      double oilViscosity, double waterViscosity, double gasViscosity) {
    this.oilDensity = oilDensity;
    this.waterDensity = waterDensity;
    this.gasDensity = gasDensity;
    this.oilViscosity = oilViscosity;
    this.waterViscosity = waterViscosity;
    this.gasViscosity = gasViscosity;
    return this;
  }

  /**
   * Sets the formation volume factors used to convert in-situ flow to standard rates.
   *
   * @param bo oil formation volume factor [m3/Sm3]
   * @param bw water formation volume factor [m3/Sm3]
   * @param bg gas formation volume factor [m3/Sm3]
   * @return this completion
   * @throws IllegalArgumentException if a factor is not positive
   */
  public InflowControlCompletion setFormationVolumeFactors(double bo, double bw, double bg) {
    if (bo <= 0.0 || bw <= 0.0 || bg <= 0.0) {
      throw new IllegalArgumentException("formation volume factors must be positive");
    }
    this.bo = bo;
    this.bw = bw;
    this.bg = bg;
    return this;
  }

  /**
   * Sets the device applied to every zone.
   *
   * @param device inflow control device, or null for a bare completion
   * @return this completion
   */
  public InflowControlCompletion setDevice(InflowControlDevice device) {
    this.device = device;
    return this;
  }

  /**
   * Number of zones.
   *
   * @return zone count
   */
  public int getNumberOfZones() {
    return zones.size();
  }

  /**
   * Flow of the completion at a given drawdown.
   *
   * @param drawdownBar drawdown between reservoir and tubing [bar]
   * @param withDevice true to apply the device; false for the bare completion
   * @return flow result
   * @throws IllegalStateException if there is no zone, or the device is requested but not set
   */
  public Result solve(double drawdownBar, boolean withDevice) {
    if (zones.isEmpty()) {
      throw new IllegalStateException("completion has no zones");
    }
    if (withDevice && device == null) {
      throw new IllegalStateException("no device set");
    }
    int n = zones.size();
    double[] flow = new double[n];
    double[] dp = new double[n];
    double oil = 0.0;
    double water = 0.0;
    double gas = 0.0;
    for (int i = 0; i < n; i++) {
      Zone z = zones.get(i);
      Mixture m = new Mixture(oilDensity, waterDensity, gasDensity, oilViscosity, waterViscosity, gasViscosity,
          z.getWaterFraction(), z.getGasFraction());
      double q;
      if (drawdownBar <= 0.0) {
        q = 0.0;
      } else if (!withDevice) {
        q = z.getProductivityIndex() * drawdownBar;
      } else {
        q = solveZone(z, m, drawdownBar);
      }
      flow[i] = q;
      dp[i] = withDevice ? device.pressureDropBar(q, m) : 0.0;
      oil += q * m.getOilFraction() / bo;
      water += q * m.getWaterFraction() / bw;
      gas += q * m.getGasFraction() / bg;
    }
    return new Result(drawdownBar, withDevice, flow, dp, oil, water, gas);
  }

  /**
   * Solves {@code q / PI + dP_device(q) = drawdown} for one zone.
   *
   * @param zone zone
   * @param mixture in-situ mixture of the zone
   * @param drawdownBar drawdown [bar]
   * @return in-situ zone flow [m3/d]
   */
  private double solveZone(Zone zone, Mixture mixture, double drawdownBar) {
    double lo = 0.0;
    double hi = zone.getProductivityIndex() * drawdownBar;
    for (int i = 0; i < 100; i++) {
      double mid = 0.5 * (lo + hi);
      double residual = mid / zone.getProductivityIndex() + device.pressureDropBar(mid, mixture) - drawdownBar;
      if (residual < 0.0) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Drawdown that gives a target standard oil rate.
   *
   * @param targetOilRate target oil rate [Sm3/d]
   * @param withDevice true to apply the device
   * @param maxDrawdownBar upper limit of the drawdown [bar]
   * @return drawdown [bar], or {@code Double.NaN} when the target is not reached at the limit
   * @throws IllegalArgumentException if the target or the limit is not positive
   */
  public double drawdownForOilRate(double targetOilRate, boolean withDevice, double maxDrawdownBar) {
    if (targetOilRate <= 0.0 || maxDrawdownBar <= 0.0) {
      throw new IllegalArgumentException("target oil rate and drawdown limit must be positive");
    }
    if (solve(maxDrawdownBar, withDevice).getOilRate() < targetOilRate) {
      return Double.NaN;
    }
    double lo = 0.0;
    double hi = maxDrawdownBar;
    for (int i = 0; i < 100; i++) {
      double mid = 0.5 * (lo + hi);
      if (solve(mid, withDevice).getOilRate() < targetOilRate) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Compares the bare completion at a drawdown with the device at the drawdown that restores the same oil rate.
   *
   * @param bareDrawdownBar drawdown of the bare completion [bar]
   * @param maxDrawdownBar largest drawdown the device case may use [bar]
   * @return comparison; when the oil rate cannot be reached the device result is evaluated at the limit
   * @throws IllegalStateException if no device is set or the bare completion produces no oil
   */
  public Comparison compareAtSameOil(double bareDrawdownBar, double maxDrawdownBar) {
    if (device == null) {
      throw new IllegalStateException("no device set");
    }
    Result bare = solve(bareDrawdownBar, false);
    if (bare.getOilRate() <= 0.0) {
      throw new IllegalStateException("bare completion produces no oil at this drawdown");
    }
    double dd = drawdownForOilRate(bare.getOilRate(), true, maxDrawdownBar);
    boolean reached = !Double.isNaN(dd);
    Result with = solve(reached ? dd : maxDrawdownBar, true);
    return new Comparison(bare, with, reached);
  }
}
