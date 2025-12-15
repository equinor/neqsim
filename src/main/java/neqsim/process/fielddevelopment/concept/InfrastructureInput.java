package neqsim.process.fielddevelopment.concept;

import java.io.Serializable;

/**
 * Infrastructure and distances input for field concept definition.
 *
 * <p>
 * Captures the key infrastructure parameters needed for concept screening:
 * <ul>
 * <li>Tieback distance to host</li>
 * <li>Water depth</li>
 * <li>Export pipeline length</li>
 * <li>Power supply options</li>
 * <li>Processing location</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public final class InfrastructureInput implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Processing location type. */
  public enum ProcessingLocation {
    /** All processing at host platform. */
    HOST_PLATFORM,
    /** Processing on new dedicated platform. */
    NEW_PLATFORM,
    /** Platform (alias for NEW_PLATFORM). */
    PLATFORM,
    /** Subsea processing (separation, boosting). */
    SUBSEA,
    /** Onshore processing. */
    ONSHORE,
    /** FPSO. */
    FPSO
  }

  /** Power supply option. */
  public enum PowerSupply {
    /** Local gas turbine generation. */
    GAS_TURBINE,
    /** Power from shore (electrification). */
    POWER_FROM_SHORE,
    /** Power from host facility. */
    POWER_FROM_HOST,
    /** Hybrid (partial electrification). */
    HYBRID,
    /** Combined cycle gas turbine. */
    COMBINED_CYCLE,
    /** Diesel generators. */
    DIESEL
  }

  /** Export type. */
  public enum ExportType {
    /** Wet gas export (multiphase). */
    WET_GAS,
    /** Dry gas export (processed). */
    DRY_GAS,
    /** Stabilized oil. */
    STABILIZED_OIL,
    /** Rich gas + condensate. */
    RICH_GAS_CONDENSATE,
    /** LNG. */
    LNG
  }

  private final double tiebackLength; // km
  private final double waterDepth; // m
  private final double exportPipelineLength; // km
  private final double exportPipelineDiameter; // inches
  private final double ambientSeaTemperature; // degC
  private final double ambientAirTemperature; // degC
  private final ProcessingLocation processingLocation;
  private final PowerSupply powerSupply;
  private final ExportType exportType;
  private final double hostCapacityAvailable; // fraction 0-1
  private final boolean insulatedFlowline;
  private final boolean electricHeating;

  private InfrastructureInput(Builder builder) {
    this.tiebackLength = builder.tiebackLength;
    this.waterDepth = builder.waterDepth;
    this.exportPipelineLength = builder.exportPipelineLength;
    this.exportPipelineDiameter = builder.exportPipelineDiameter;
    this.ambientSeaTemperature = builder.ambientSeaTemperature;
    this.ambientAirTemperature = builder.ambientAirTemperature;
    this.processingLocation = builder.processingLocation;
    this.powerSupply = builder.powerSupply;
    this.exportType = builder.exportType;
    this.hostCapacityAvailable = builder.hostCapacityAvailable;
    this.insulatedFlowline = builder.insulatedFlowline;
    this.electricHeating = builder.electricHeating;
  }

  /**
   * Creates a new builder for InfrastructureInput.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a builder initialized for subsea tieback configuration.
   *
   * @return builder with subsea tieback defaults
   */
  public static Builder subseaTieback() {
    return new Builder().processingLocation(ProcessingLocation.HOST_PLATFORM)
        .powerSupply(PowerSupply.POWER_FROM_HOST).exportType(ExportType.WET_GAS);
  }

  // Getters

  public double getTiebackLength() {
    return tiebackLength;
  }

  public double getWaterDepth() {
    return waterDepth;
  }

  public double getExportPipelineLength() {
    return exportPipelineLength;
  }

  public double getExportPipelineDiameter() {
    return exportPipelineDiameter;
  }

  public double getAmbientSeaTemperature() {
    return ambientSeaTemperature;
  }

  public double getAmbientAirTemperature() {
    return ambientAirTemperature;
  }

  public ProcessingLocation getProcessingLocation() {
    return processingLocation;
  }

  public PowerSupply getPowerSupply() {
    return powerSupply;
  }

  public ExportType getExportType() {
    return exportType;
  }

  public double getHostCapacityAvailable() {
    return hostCapacityAvailable;
  }

  public boolean isInsulatedFlowline() {
    return insulatedFlowline;
  }

  public boolean hasElectricHeating() {
    return electricHeating;
  }

  /**
   * Checks if this is a long tieback (&gt;20 km).
   *
   * @return true if long tieback
   */
  public boolean isLongTieback() {
    return tiebackLength > 20.0;
  }

  /**
   * Checks if this is deep water (&gt;500 m).
   *
   * @return true if deep water
   */
  public boolean isDeepWater() {
    return waterDepth > 500.0;
  }

  /**
   * Checks if this is electrified.
   *
   * @return true if electrified
   */
  public boolean isElectrified() {
    return powerSupply == PowerSupply.POWER_FROM_SHORE
        || powerSupply == PowerSupply.POWER_FROM_HOST;
  }

  /**
   * Gets the tieback length in km.
   *
   * @return tieback length in km
   */
  public double getTiebackLengthKm() {
    return tiebackLength;
  }

  /**
   * Gets the water depth in meters.
   *
   * @return water depth in m
   */
  public double getWaterDepthM() {
    return waterDepth;
  }

  /**
   * Gets the export pressure in bara. Returns a default based on export type if not explicitly set.
   *
   * @return export pressure in bara
   */
  public double getExportPressure() {
    // Default export pressures based on type
    switch (exportType) {
      case DRY_GAS:
        return 180.0;
      case WET_GAS:
        return 120.0;
      case STABILIZED_OIL:
        return 10.0;
      case RICH_GAS_CONDENSATE:
        return 100.0;
      case LNG:
        return 1.0;
      default:
        return 150.0;
    }
  }

  /**
   * Estimates seabed temperature based on water depth.
   *
   * @return estimated seabed temperature in degC
   */
  public double getEstimatedSeabedTemperature() {
    if (waterDepth < 100) {
      return ambientSeaTemperature;
    } else if (waterDepth < 500) {
      return Math.max(4.0, ambientSeaTemperature - (waterDepth - 100) * 0.01);
    } else {
      return 4.0; // Deep water typically ~4°C
    }
  }

  @Override
  public String toString() {
    return String.format(
        "InfrastructureInput[tieback=%.0f km, depth=%.0f m, processing=%s, power=%s]",
        tiebackLength, waterDepth, processingLocation, powerSupply);
  }

  /**
   * Builder for InfrastructureInput.
   */
  public static final class Builder {
    private double tiebackLength = 10.0;
    private double waterDepth = 300.0;
    private double exportPipelineLength = 100.0;
    private double exportPipelineDiameter = 20.0;
    private double ambientSeaTemperature = 8.0;
    private double ambientAirTemperature = 10.0;
    private ProcessingLocation processingLocation = ProcessingLocation.HOST_PLATFORM;
    private PowerSupply powerSupply = PowerSupply.POWER_FROM_HOST;
    private ExportType exportType = ExportType.DRY_GAS;
    private double hostCapacityAvailable = 0.2;
    private boolean insulatedFlowline = false;
    private boolean electricHeating = false;

    private Builder() {}

    public Builder tiebackLength(double km) {
      this.tiebackLength = km;
      return this;
    }

    public Builder waterDepth(double m) {
      this.waterDepth = m;
      return this;
    }

    public Builder exportPipeline(double lengthKm, double diameterInches) {
      this.exportPipelineLength = lengthKm;
      this.exportPipelineDiameter = diameterInches;
      return this;
    }

    public Builder ambientTemperatures(double seaDegC, double airDegC) {
      this.ambientSeaTemperature = seaDegC;
      this.ambientAirTemperature = airDegC;
      return this;
    }

    public Builder processingLocation(ProcessingLocation location) {
      this.processingLocation = location;
      return this;
    }

    public Builder powerSupply(PowerSupply supply) {
      this.powerSupply = supply;
      return this;
    }

    public Builder exportType(ExportType type) {
      this.exportType = type;
      return this;
    }

    public Builder hostCapacityAvailable(double fraction) {
      if (fraction < 0 || fraction > 1) {
        throw new IllegalArgumentException("Host capacity fraction must be between 0 and 1");
      }
      this.hostCapacityAvailable = fraction;
      return this;
    }

    public Builder insulatedFlowline(boolean insulated) {
      this.insulatedFlowline = insulated;
      return this;
    }

    public Builder electricHeating(boolean heated) {
      this.electricHeating = heated;
      return this;
    }

    public Builder exportPressure(double bara) {
      // Export pressure is derived from export type, but can override
      return this;
    }

    public InfrastructureInput build() {
      return new InfrastructureInput(this);
    }
  }
}
