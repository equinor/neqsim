package neqsim.process.fielddevelopment.tieback;

import java.io.Serializable;
import java.util.List;
import neqsim.process.fielddevelopment.evaluation.BottleneckAnalyzer;
import neqsim.process.fielddevelopment.evaluation.BottleneckAnalyzer.BottleneckResult;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Represents an existing host facility with available capacity for tie-backs.
 *
 * <p>
 * A host facility is an existing offshore installation (platform, FPSO, or onshore terminal) that
 * can receive production from satellite fields. This class captures the key parameters needed for
 * tie-back screening:
 * </p>
 * <ul>
 * <li><b>Location</b>: Geographic coordinates for distance calculations</li>
 * <li><b>Capacity</b>: Processing limits for gas, oil, water, and liquids</li>
 * <li><b>Utilization</b>: Current production levels relative to capacity</li>
 * <li><b>Tie-in requirements</b>: Pressure and temperature constraints</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Create a platform with spare gas capacity
 * HostFacility platform = HostFacility.builder("Troll A").location(60.6, 3.7).waterDepth(330)
 *     .gasCapacity(40.0, "MSm3/d").gasUtilization(0.85).minTieInPressure(80).maxTieInPressure(150)
 *     .build();
 *
 * // Check spare capacity
 * double spareGas = platform.getSpareGasCapacity(); // ~6 MSm3/d
 *
 * // Check if discovery can be accommodated
 * if (platform.canAcceptGasRate(2.0)) {
 *   System.out.println("Tieback feasible");
 * }
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see TiebackAnalyzer
 * @see TiebackOption
 */
public class HostFacility implements Serializable {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // IDENTIFICATION
  // ============================================================================

  private String name;
  private String operator;
  private FacilityType type;

  // ============================================================================
  // LOCATION
  // ============================================================================

  /** Latitude in decimal degrees (positive = North). */
  private double latitude;

  /** Longitude in decimal degrees (positive = East). */
  private double longitude;

  /** Water depth in meters. */
  private double waterDepthM;

  // ============================================================================
  // CAPACITY - GAS (MSm3/d)
  // ============================================================================

  /** Maximum gas processing capacity in MSm3/d. */
  private double gasCapacityMSm3d;

  /** Current gas utilization (0-1). */
  private double gasUtilization;

  // ============================================================================
  // CAPACITY - OIL (bbl/d)
  // ============================================================================

  /** Maximum oil processing capacity in bbl/d. */
  private double oilCapacityBopd;

  /** Current oil utilization (0-1). */
  private double oilUtilization;

  // ============================================================================
  // CAPACITY - WATER (m3/d)
  // ============================================================================

  /** Maximum water handling capacity in m3/d. */
  private double waterCapacityM3d;

  /** Current water utilization (0-1). */
  private double waterUtilization;

  // ============================================================================
  // CAPACITY - LIQUIDS (m3/d)
  // ============================================================================

  /** Maximum total liquid capacity in m3/d (oil + water + condensate). */
  private double liquidCapacityM3d;

  /** Current liquid utilization (0-1). */
  private double liquidUtilization;

  // ============================================================================
  // TIE-IN CONSTRAINTS
  // ============================================================================

  /** Minimum tie-in pressure in bara. */
  private double minTieInPressureBara;

  /** Maximum tie-in pressure in bara. */
  private double maxTieInPressureBara;

  /** Maximum tie-in temperature in Celsius. */
  private double maxTieInTemperatureC;

  // ============================================================================
  // ASSOCIATED PROCESS MODEL (OPTIONAL)
  // ============================================================================

  /** Optional detailed process model for capacity analysis. */
  private transient ProcessSystem processSystem;

  // ============================================================================
  // ENUMS
  // ============================================================================

  /**
   * Type of host facility.
   */
  public enum FacilityType {
    /** Fixed platform. */
    PLATFORM,

    /** Floating Production Storage and Offloading. */
    FPSO,

    /** Tension Leg Platform. */
    TLP,

    /** Semi-submersible platform. */
    SEMI_SUB,

    /** Onshore processing terminal. */
    ONSHORE_TERMINAL,

    /** Subsea hub (minimal processing). */
    SUBSEA_HUB
  }

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new host facility with the specified name.
   *
   * @param name facility name
   */
  public HostFacility(String name) {
    this.name = name;
    this.type = FacilityType.PLATFORM;
    this.gasUtilization = 0.0;
    this.oilUtilization = 0.0;
    this.waterUtilization = 0.0;
    this.liquidUtilization = 0.0;
    this.minTieInPressureBara = 50.0;
    this.maxTieInPressureBara = 200.0;
    this.maxTieInTemperatureC = 80.0;
  }

  /**
   * Creates a builder for constructing a HostFacility.
   *
   * @param name facility name
   * @return new builder instance
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  // ============================================================================
  // CAPACITY METHODS
  // ============================================================================

  /**
   * Gets spare gas capacity.
   *
   * @return spare gas capacity in MSm3/d
   */
  public double getSpareGasCapacity() {
    return gasCapacityMSm3d * (1.0 - gasUtilization);
  }

  /**
   * Gets spare oil capacity.
   *
   * @return spare oil capacity in bbl/d
   */
  public double getSpareOilCapacity() {
    return oilCapacityBopd * (1.0 - oilUtilization);
  }

  /**
   * Gets spare water capacity.
   *
   * @return spare water capacity in m3/d
   */
  public double getSpareWaterCapacity() {
    return waterCapacityM3d * (1.0 - waterUtilization);
  }

  /**
   * Gets spare liquid capacity.
   *
   * @return spare liquid capacity in m3/d
   */
  public double getSpareLiquidCapacity() {
    return liquidCapacityM3d * (1.0 - liquidUtilization);
  }

  /**
   * Checks if the facility can accept additional gas production.
   *
   * @param additionalRateMSm3d additional gas rate in MSm3/d
   * @return true if capacity is available
   */
  public boolean canAcceptGasRate(double additionalRateMSm3d) {
    return additionalRateMSm3d <= getSpareGasCapacity();
  }

  /**
   * Checks if the facility can accept additional oil production.
   *
   * @param additionalRateBopd additional oil rate in bbl/d
   * @return true if capacity is available
   */
  public boolean canAcceptOilRate(double additionalRateBopd) {
    return additionalRateBopd <= getSpareOilCapacity();
  }

  /**
   * Assesses whether the host can accept additional production.
   *
   * <p>
   * The method first checks the explicit nameplate spare capacities. If a detailed
   * {@link ProcessSystem} is attached, it also runs {@link BottleneckAnalyzer} and flags active
   * process bottlenecks that could make the nameplate spare capacity misleading.
   * </p>
   *
   * @param additionalGasMSm3d additional gas rate in MSm3/d
   * @param additionalOilBopd additional oil rate in bbl/d
   * @param additionalWaterM3d additional produced water rate in m3/d
   * @param additionalLiquidM3d additional total liquid rate in m3/d
   * @return host capacity report with pass/fail status and bottleneck summary
   */
  public HostCapacityReport assessCapacity(double additionalGasMSm3d, double additionalOilBopd,
      double additionalWaterM3d, double additionalLiquidM3d) {
    boolean gasOk = additionalGasMSm3d <= getSpareGasCapacity();
    boolean oilOk = additionalOilBopd <= getSpareOilCapacity();
    boolean waterOk = waterCapacityM3d <= 0.0 || additionalWaterM3d <= getSpareWaterCapacity();
    boolean liquidOk = liquidCapacityM3d <= 0.0 || additionalLiquidM3d <= getSpareLiquidCapacity();

    boolean processModelUsed = false;
    boolean processOk = true;
    String primaryBottleneckName = null;
    double primaryBottleneckUtilization = 0.0;
    int activeBottleneckCount = 0;
    String processMessage = "No process model attached";

    if (processSystem != null) {
      processModelUsed = true;
      try {
        processSystem.run();
        BottleneckAnalyzer analyzer = new BottleneckAnalyzer(processSystem);
        List<BottleneckResult> active = analyzer.getActiveBottlenecks();
        activeBottleneckCount = active.size();
        BottleneckResult primary = analyzer.getPrimaryBottleneck();
        if (primary != null) {
          primaryBottleneckName = primary.getEquipmentName();
          primaryBottleneckUtilization = primary.getUtilization();
          processOk = primary.getUtilization() < 0.95;
          processMessage = String.format("Primary process bottleneck %s at %.0f%% utilization",
              primary.getEquipmentName(), primary.getUtilization() * 100.0);
        } else {
          processMessage = "Process model has no recognized bottlenecking equipment";
        }
      } catch (Exception e) {
        processOk = false;
        processMessage = "Process model capacity check failed: " + e.getMessage();
      }
    }

    boolean capacityAvailable = gasOk && oilOk && waterOk && liquidOk && processOk;
    String summary = buildCapacitySummary(additionalGasMSm3d, additionalOilBopd, additionalWaterM3d,
        additionalLiquidM3d, gasOk, oilOk, waterOk, liquidOk, processMessage);

    return new HostCapacityReport(name, capacityAvailable, gasOk, oilOk, waterOk, liquidOk,
        processOk, processModelUsed, additionalGasMSm3d, getSpareGasCapacity(), additionalOilBopd,
        getSpareOilCapacity(), additionalWaterM3d, getSpareWaterCapacity(), additionalLiquidM3d,
        getSpareLiquidCapacity(), primaryBottleneckName, primaryBottleneckUtilization,
        activeBottleneckCount, summary);
  }

  /**
   * Builds a concise capacity summary string.
   *
   * @param additionalGasMSm3d additional gas rate in MSm3/d
   * @param additionalOilBopd additional oil rate in bbl/d
   * @param additionalWaterM3d additional produced water rate in m3/d
   * @param additionalLiquidM3d additional total liquid rate in m3/d
   * @param gasOk true if gas capacity passes
   * @param oilOk true if oil capacity passes
   * @param waterOk true if water capacity passes
   * @param liquidOk true if liquid capacity passes
   * @param processMessage process-model bottleneck message
   * @return formatted capacity summary
   */
  private String buildCapacitySummary(double additionalGasMSm3d, double additionalOilBopd,
      double additionalWaterM3d, double additionalLiquidM3d, boolean gasOk, boolean oilOk,
      boolean waterOk, boolean liquidOk, String processMessage) {
    StringBuilder summary = new StringBuilder();
    summary.append("Capacity: ");
    summary.append(String.format("Gas %.2f/%.2f MSm3/d %s; ", additionalGasMSm3d,
        getSpareGasCapacity(), gasOk ? "OK" : "LIMIT"));
    summary.append(String.format("Oil %.0f/%.0f bbl/d %s; ", additionalOilBopd,
        getSpareOilCapacity(), oilOk ? "OK" : "LIMIT"));
    if (waterCapacityM3d > 0.0) {
      summary.append(String.format("Water %.0f/%.0f m3/d %s; ", additionalWaterM3d,
          getSpareWaterCapacity(), waterOk ? "OK" : "LIMIT"));
    }
    if (liquidCapacityM3d > 0.0) {
      summary.append(String.format("Liquid %.0f/%.0f m3/d %s; ", additionalLiquidM3d,
          getSpareLiquidCapacity(), liquidOk ? "OK" : "LIMIT"));
    }
    summary.append(processMessage);
    return summary.toString();
  }

  // ============================================================================
  // DISTANCE CALCULATION
  // ============================================================================

  /**
   * Calculates the great-circle distance to another location.
   *
   * @param targetLatitude target latitude in degrees
   * @param targetLongitude target longitude in degrees
   * @return distance in kilometers
   */
  public double distanceToKm(double targetLatitude, double targetLongitude) {
    // Haversine formula
    double earthRadiusKm = 6371.0;

    double dLat = Math.toRadians(targetLatitude - latitude);
    double dLon = Math.toRadians(targetLongitude - longitude);

    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(latitude))
        * Math.cos(Math.toRadians(targetLatitude)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);

    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return earthRadiusKm * c;
  }

  /**
   * Checks if tie-in pressure is within acceptable range.
   *
   * @param arrivalPressureBara expected arrival pressure at host
   * @return true if pressure is acceptable
   */
  public boolean isPressureAcceptable(double arrivalPressureBara) {
    return arrivalPressureBara >= minTieInPressureBara
        && arrivalPressureBara <= maxTieInPressureBara;
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Gets the facility name.
   *
   * @return facility name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the facility name.
   *
   * @param name facility name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the operator name.
   *
   * @return operator name
   */
  public String getOperator() {
    return operator;
  }

  /**
   * Sets the operator name.
   *
   * @param operator operator name
   */
  public void setOperator(String operator) {
    this.operator = operator;
  }

  /**
   * Gets the facility type.
   *
   * @return facility type
   */
  public FacilityType getType() {
    return type;
  }

  /**
   * Sets the facility type.
   *
   * @param type facility type
   */
  public void setType(FacilityType type) {
    this.type = type;
  }

  /**
   * Gets the latitude.
   *
   * @return latitude in degrees
   */
  public double getLatitude() {
    return latitude;
  }

  /**
   * Sets the latitude.
   *
   * @param latitude latitude in degrees
   */
  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  /**
   * Gets the longitude.
   *
   * @return longitude in degrees
   */
  public double getLongitude() {
    return longitude;
  }

  /**
   * Sets the longitude.
   *
   * @param longitude longitude in degrees
   */
  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }

  /**
   * Gets the water depth.
   *
   * @return water depth in meters
   */
  public double getWaterDepthM() {
    return waterDepthM;
  }

  /**
   * Sets the water depth.
   *
   * @param waterDepthM water depth in meters
   */
  public void setWaterDepthM(double waterDepthM) {
    this.waterDepthM = waterDepthM;
  }

  /**
   * Gets the gas capacity.
   *
   * @return gas capacity in MSm3/d
   */
  public double getGasCapacityMSm3d() {
    return gasCapacityMSm3d;
  }

  /**
   * Sets the gas capacity.
   *
   * @param gasCapacityMSm3d gas capacity in MSm3/d
   */
  public void setGasCapacityMSm3d(double gasCapacityMSm3d) {
    this.gasCapacityMSm3d = gasCapacityMSm3d;
  }

  /**
   * Gets the gas utilization.
   *
   * @return gas utilization (0-1)
   */
  public double getGasUtilization() {
    return gasUtilization;
  }

  /**
   * Sets the gas utilization.
   *
   * @param gasUtilization gas utilization (0-1)
   */
  public void setGasUtilization(double gasUtilization) {
    this.gasUtilization = Math.max(0, Math.min(1, gasUtilization));
  }

  /**
   * Gets the oil capacity.
   *
   * @return oil capacity in bbl/d
   */
  public double getOilCapacityBopd() {
    return oilCapacityBopd;
  }

  /**
   * Sets the oil capacity.
   *
   * @param oilCapacityBopd oil capacity in bbl/d
   */
  public void setOilCapacityBopd(double oilCapacityBopd) {
    this.oilCapacityBopd = oilCapacityBopd;
  }

  /**
   * Gets the oil utilization.
   *
   * @return oil utilization (0-1)
   */
  public double getOilUtilization() {
    return oilUtilization;
  }

  /**
   * Sets the oil utilization.
   *
   * @param oilUtilization oil utilization (0-1)
   */
  public void setOilUtilization(double oilUtilization) {
    this.oilUtilization = Math.max(0, Math.min(1, oilUtilization));
  }

  /**
   * Gets the minimum tie-in pressure.
   *
   * @return minimum tie-in pressure in bara
   */
  public double getMinTieInPressureBara() {
    return minTieInPressureBara;
  }

  /**
   * Sets the minimum tie-in pressure.
   *
   * @param minTieInPressureBara minimum tie-in pressure in bara
   */
  public void setMinTieInPressureBara(double minTieInPressureBara) {
    this.minTieInPressureBara = minTieInPressureBara;
  }

  /**
   * Gets the maximum tie-in pressure.
   *
   * @return maximum tie-in pressure in bara
   */
  public double getMaxTieInPressureBara() {
    return maxTieInPressureBara;
  }

  /**
   * Sets the maximum tie-in pressure.
   *
   * @param maxTieInPressureBara maximum tie-in pressure in bara
   */
  public void setMaxTieInPressureBara(double maxTieInPressureBara) {
    this.maxTieInPressureBara = maxTieInPressureBara;
  }

  /**
   * Gets the water capacity.
   *
   * @return water capacity in m3/d
   */
  public double getWaterCapacityM3d() {
    return waterCapacityM3d;
  }

  /**
   * Sets the water capacity.
   *
   * @param waterCapacityM3d water capacity in m3/d
   */
  public void setWaterCapacityM3d(double waterCapacityM3d) {
    this.waterCapacityM3d = waterCapacityM3d;
  }

  /**
   * Gets the water utilization.
   *
   * @return water utilization (0-1)
   */
  public double getWaterUtilization() {
    return waterUtilization;
  }

  /**
   * Sets the water utilization.
   *
   * @param waterUtilization water utilization (0-1)
   */
  public void setWaterUtilization(double waterUtilization) {
    this.waterUtilization = Math.max(0, Math.min(1, waterUtilization));
  }

  /**
   * Gets the associated process system.
   *
   * @return process system or null
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Sets the associated process system.
   *
   * @param processSystem process system for detailed analysis
   */
  public void setProcessSystem(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Gets the total liquid capacity.
   *
   * @return liquid capacity in m3/d
   */
  public double getLiquidCapacityM3d() {
    return liquidCapacityM3d;
  }

  /**
   * Sets the total liquid capacity.
   *
   * @param liquidCapacityM3d liquid capacity in m3/d
   */
  public void setLiquidCapacityM3d(double liquidCapacityM3d) {
    this.liquidCapacityM3d = liquidCapacityM3d;
  }

  /**
   * Gets the total liquid utilization.
   *
   * @return liquid utilization as a fraction from 0 to 1
   */
  public double getLiquidUtilization() {
    return liquidUtilization;
  }

  /**
   * Sets the total liquid utilization.
   *
   * @param liquidUtilization liquid utilization as a fraction from 0 to 1
   */
  public void setLiquidUtilization(double liquidUtilization) {
    this.liquidUtilization = Math.max(0, Math.min(1, liquidUtilization));
  }

  /**
   * Report from host capacity assessment.
   */
  public static final class HostCapacityReport implements Serializable {
    private static final long serialVersionUID = 1001L;

    private final String hostName;
    private final boolean capacityAvailable;
    private final boolean gasCapacityAvailable;
    private final boolean oilCapacityAvailable;
    private final boolean waterCapacityAvailable;
    private final boolean liquidCapacityAvailable;
    private final boolean processCapacityAvailable;
    private final boolean processModelUsed;
    private final double requiredGasMSm3d;
    private final double spareGasMSm3d;
    private final double requiredOilBopd;
    private final double spareOilBopd;
    private final double requiredWaterM3d;
    private final double spareWaterM3d;
    private final double requiredLiquidM3d;
    private final double spareLiquidM3d;
    private final String primaryBottleneckName;
    private final double primaryBottleneckUtilization;
    private final int activeBottleneckCount;
    private final String summary;

    /**
     * Creates a host capacity report.
     *
     * @param hostName host name
     * @param capacityAvailable true if all capacity checks pass
     * @param gasCapacityAvailable true if gas capacity passes
     * @param oilCapacityAvailable true if oil capacity passes
     * @param waterCapacityAvailable true if water capacity passes
     * @param liquidCapacityAvailable true if liquid capacity passes
     * @param processCapacityAvailable true if process model has acceptable bottleneck utilization
     * @param processModelUsed true if an attached process model was checked
     * @param requiredGasMSm3d required gas rate in MSm3/d
     * @param spareGasMSm3d spare gas capacity in MSm3/d
     * @param requiredOilBopd required oil rate in bbl/d
     * @param spareOilBopd spare oil capacity in bbl/d
     * @param requiredWaterM3d required water rate in m3/d
     * @param spareWaterM3d spare water capacity in m3/d
     * @param requiredLiquidM3d required liquid rate in m3/d
     * @param spareLiquidM3d spare liquid capacity in m3/d
     * @param primaryBottleneckName primary bottleneck equipment name
     * @param primaryBottleneckUtilization primary bottleneck utilization fraction
     * @param activeBottleneckCount number of active bottlenecks above threshold
     * @param summary concise text summary
     */
    private HostCapacityReport(String hostName, boolean capacityAvailable,
        boolean gasCapacityAvailable, boolean oilCapacityAvailable, boolean waterCapacityAvailable,
        boolean liquidCapacityAvailable, boolean processCapacityAvailable, boolean processModelUsed,
        double requiredGasMSm3d, double spareGasMSm3d, double requiredOilBopd, double spareOilBopd,
        double requiredWaterM3d, double spareWaterM3d, double requiredLiquidM3d,
        double spareLiquidM3d, String primaryBottleneckName, double primaryBottleneckUtilization,
        int activeBottleneckCount, String summary) {
      this.hostName = hostName;
      this.capacityAvailable = capacityAvailable;
      this.gasCapacityAvailable = gasCapacityAvailable;
      this.oilCapacityAvailable = oilCapacityAvailable;
      this.waterCapacityAvailable = waterCapacityAvailable;
      this.liquidCapacityAvailable = liquidCapacityAvailable;
      this.processCapacityAvailable = processCapacityAvailable;
      this.processModelUsed = processModelUsed;
      this.requiredGasMSm3d = requiredGasMSm3d;
      this.spareGasMSm3d = spareGasMSm3d;
      this.requiredOilBopd = requiredOilBopd;
      this.spareOilBopd = spareOilBopd;
      this.requiredWaterM3d = requiredWaterM3d;
      this.spareWaterM3d = spareWaterM3d;
      this.requiredLiquidM3d = requiredLiquidM3d;
      this.spareLiquidM3d = spareLiquidM3d;
      this.primaryBottleneckName = primaryBottleneckName;
      this.primaryBottleneckUtilization = primaryBottleneckUtilization;
      this.activeBottleneckCount = activeBottleneckCount;
      this.summary = summary;
    }

    /**
     * Gets the host name.
     *
     * @return host name
     */
    public String getHostName() {
      return hostName;
    }

    /**
     * Checks whether all capacity checks pass.
     *
     * @return true if capacity is available
     */
    public boolean isCapacityAvailable() {
      return capacityAvailable;
    }

    /**
     * Checks whether gas capacity passes.
     *
     * @return true if gas capacity passes
     */
    public boolean isGasCapacityAvailable() {
      return gasCapacityAvailable;
    }

    /**
     * Checks whether oil capacity passes.
     *
     * @return true if oil capacity passes
     */
    public boolean isOilCapacityAvailable() {
      return oilCapacityAvailable;
    }

    /**
     * Checks whether water capacity passes.
     *
     * @return true if water capacity passes
     */
    public boolean isWaterCapacityAvailable() {
      return waterCapacityAvailable;
    }

    /**
     * Checks whether total liquid capacity passes.
     *
     * @return true if liquid capacity passes
     */
    public boolean isLiquidCapacityAvailable() {
      return liquidCapacityAvailable;
    }

    /**
     * Checks whether process-model capacity passes.
     *
     * @return true if process-model capacity passes
     */
    public boolean isProcessCapacityAvailable() {
      return processCapacityAvailable;
    }

    /**
     * Checks whether a process model was used.
     *
     * @return true if a process model was checked
     */
    public boolean isProcessModelUsed() {
      return processModelUsed;
    }

    /**
     * Gets the required gas rate.
     *
     * @return required gas rate in MSm3/d
     */
    public double getRequiredGasMSm3d() {
      return requiredGasMSm3d;
    }

    /**
     * Gets the spare gas capacity.
     *
     * @return spare gas capacity in MSm3/d
     */
    public double getSpareGasMSm3d() {
      return spareGasMSm3d;
    }

    /**
     * Gets the required oil rate.
     *
     * @return required oil rate in bbl/d
     */
    public double getRequiredOilBopd() {
      return requiredOilBopd;
    }

    /**
     * Gets the spare oil capacity.
     *
     * @return spare oil capacity in bbl/d
     */
    public double getSpareOilBopd() {
      return spareOilBopd;
    }

    /**
     * Gets the required water rate.
     *
     * @return required water rate in m3/d
     */
    public double getRequiredWaterM3d() {
      return requiredWaterM3d;
    }

    /**
     * Gets the spare water capacity.
     *
     * @return spare water capacity in m3/d
     */
    public double getSpareWaterM3d() {
      return spareWaterM3d;
    }

    /**
     * Gets the required total liquid rate.
     *
     * @return required liquid rate in m3/d
     */
    public double getRequiredLiquidM3d() {
      return requiredLiquidM3d;
    }

    /**
     * Gets the spare total liquid capacity.
     *
     * @return spare liquid capacity in m3/d
     */
    public double getSpareLiquidM3d() {
      return spareLiquidM3d;
    }

    /**
     * Gets the primary bottleneck name.
     *
     * @return primary bottleneck name, or null if none was found
     */
    public String getPrimaryBottleneckName() {
      return primaryBottleneckName;
    }

    /**
     * Gets the primary bottleneck utilization.
     *
     * @return utilization as a fraction from 0 to 1
     */
    public double getPrimaryBottleneckUtilization() {
      return primaryBottleneckUtilization;
    }

    /**
     * Gets the active bottleneck count.
     *
     * @return number of active bottlenecks above threshold
     */
    public int getActiveBottleneckCount() {
      return activeBottleneckCount;
    }

    /**
     * Gets the summary string.
     *
     * @return capacity summary
     */
    public String getSummary() {
      return summary;
    }
  }

  @Override
  public String toString() {
    return String.format(
        "HostFacility[%s, type=%s, pos=(%.2f, %.2f), depth=%.0fm, "
            + "spareGas=%.1f MSm3/d, spareOil=%.0f bopd]",
        name, type, latitude, longitude, waterDepthM, getSpareGasCapacity(), getSpareOilCapacity());
  }

  // ============================================================================
  // BUILDER
  // ============================================================================

  /**
   * Builder for HostFacility.
   */
  public static final class Builder {
    private final HostFacility facility;

    private Builder(String name) {
      this.facility = new HostFacility(name);
    }

    /**
     * Sets the operator.
     *
     * @param operator operator name
     * @return this builder
     */
    public Builder operator(String operator) {
      facility.setOperator(operator);
      return this;
    }

    /**
     * Sets the facility type.
     *
     * @param type facility type
     * @return this builder
     */
    public Builder type(FacilityType type) {
      facility.setType(type);
      return this;
    }

    /**
     * Sets the location.
     *
     * @param latitude latitude in degrees
     * @param longitude longitude in degrees
     * @return this builder
     */
    public Builder location(double latitude, double longitude) {
      facility.setLatitude(latitude);
      facility.setLongitude(longitude);
      return this;
    }

    /**
     * Sets the water depth.
     *
     * @param waterDepthM water depth in meters
     * @return this builder
     */
    public Builder waterDepth(double waterDepthM) {
      facility.setWaterDepthM(waterDepthM);
      return this;
    }

    /**
     * Sets the gas capacity.
     *
     * @param capacityMSm3d gas capacity in MSm3/d
     * @return this builder
     */
    public Builder gasCapacity(double capacityMSm3d) {
      facility.setGasCapacityMSm3d(capacityMSm3d);
      return this;
    }

    /**
     * Sets the gas utilization.
     *
     * @param utilization utilization (0-1)
     * @return this builder
     */
    public Builder gasUtilization(double utilization) {
      facility.setGasUtilization(utilization);
      return this;
    }

    /**
     * Sets the spare gas capacity directly.
     *
     * @param spareCapacityMSm3d spare gas capacity in MSm3/d
     * @return this builder
     */
    public Builder spareGasCapacity(double spareCapacityMSm3d) {
      // Infer capacity from spare (assume no current production)
      facility.setGasCapacityMSm3d(spareCapacityMSm3d);
      facility.setGasUtilization(0.0);
      return this;
    }

    /**
     * Sets the oil capacity.
     *
     * @param capacityBopd oil capacity in bbl/d
     * @return this builder
     */
    public Builder oilCapacity(double capacityBopd) {
      facility.setOilCapacityBopd(capacityBopd);
      return this;
    }

    /**
     * Sets the oil utilization.
     *
     * @param utilization utilization (0-1)
     * @return this builder
     */
    public Builder oilUtilization(double utilization) {
      facility.setOilUtilization(utilization);
      return this;
    }

    /**
     * Sets the spare oil capacity directly.
     *
     * @param spareCapacityBopd spare oil capacity in bbl/d
     * @return this builder
     */
    public Builder spareOilCapacity(double spareCapacityBopd) {
      facility.setOilCapacityBopd(spareCapacityBopd);
      facility.setOilUtilization(0.0);
      return this;
    }

    /**
     * Sets the water capacity.
     *
     * @param capacityM3d water capacity in m3/d
     * @return this builder
     */
    public Builder waterCapacity(double capacityM3d) {
      facility.setWaterCapacityM3d(capacityM3d);
      return this;
    }

    /**
     * Sets the liquid capacity.
     *
     * @param capacityM3d liquid capacity in m3/d
     * @return this builder
     */
    public Builder liquidCapacity(double capacityM3d) {
      facility.setLiquidCapacityM3d(capacityM3d);
      return this;
    }

    /**
     * Sets the associated process system.
     *
     * @param processSystem process system for detailed capacity analysis
     * @return this builder
     */
    public Builder processSystem(ProcessSystem processSystem) {
      facility.setProcessSystem(processSystem);
      return this;
    }

    /**
     * Sets the minimum tie-in pressure.
     *
     * @param pressureBara minimum pressure in bara
     * @return this builder
     */
    public Builder minTieInPressure(double pressureBara) {
      facility.setMinTieInPressureBara(pressureBara);
      return this;
    }

    /**
     * Sets the maximum tie-in pressure.
     *
     * @param pressureBara maximum pressure in bara
     * @return this builder
     */
    public Builder maxTieInPressure(double pressureBara) {
      facility.setMaxTieInPressureBara(pressureBara);
      return this;
    }

    /**
     * Builds the HostFacility.
     *
     * @return configured HostFacility
     */
    public HostFacility build() {
      return facility;
    }
  }
}
