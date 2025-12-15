package neqsim.process.fielddevelopment.concept;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Main field concept definition for rapid field development screening.
 *
 * <p>
 * A FieldConcept encapsulates all the high-level inputs needed to screen a development option:
 * <ul>
 * <li>Reservoir fluid characteristics</li>
 * <li>Well configuration</li>
 * <li>Infrastructure and distances</li>
 * </ul>
 *
 * <p>
 * This concept-first modeling approach enables rapid iteration during early field development
 * phases (concept selection, FEED) where decisions must be made with limited data but need physical
 * consistency.
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * FieldConcept concept = FieldConcept.builder("Marginal Gas Tieback")
 *     .reservoir(ReservoirInput.richGas().gor(1200).co2Percent(2.5).waterCut(0.1).build())
 *     .wells(WellsInput.builder().producerCount(4).thp(120).ratePerWell(1.5e6, "Sm3/d").build())
 *     .infrastructure(InfrastructureInput.builder().tiebackLength(35).waterDepth(350).build())
 *     .build();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ReservoirInput
 * @see WellsInput
 * @see InfrastructureInput
 */
public final class FieldConcept implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String id;
  private final String name;
  private final String description;
  private final ReservoirInput reservoir;
  private final WellsInput wells;
  private final InfrastructureInput infrastructure;

  private FieldConcept(Builder builder) {
    this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
    this.name = builder.name;
    this.description = builder.description;
    this.reservoir = builder.reservoir;
    this.wells = builder.wells;
    this.infrastructure = builder.infrastructure;
  }

  /**
   * Creates a new builder for FieldConcept.
   *
   * @param name concept name
   * @return new builder instance
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Creates a simple gas tieback concept with defaults.
   *
   * @param name concept name
   * @param tiebackKm tieback distance in km
   * @param wellCount number of wells
   * @param ratePerWellMSm3d rate per well in MSm3/d
   * @return configured FieldConcept
   */
  public static FieldConcept gasTieback(String name, double tiebackKm, int wellCount,
      double ratePerWellMSm3d) {
    return builder(name).reservoir(ReservoirInput.richGas().build())
        .wells(WellsInput.builder().producerCount(wellCount)
            .ratePerWell(ratePerWellMSm3d * 1e6, "Sm3/d").build())
        .infrastructure(InfrastructureInput.builder().tiebackLength(tiebackKm)
            .processingLocation(InfrastructureInput.ProcessingLocation.HOST_PLATFORM).build())
        .build();
  }

  /**
   * Creates a simple oil development concept with defaults.
   *
   * @param name concept name
   * @param wellCount number of wells
   * @param ratePerWellBopd rate per well in bopd
   * @param waterCut water cut fraction
   * @return configured FieldConcept
   */
  public static FieldConcept oilDevelopment(String name, int wellCount, double ratePerWellBopd,
      double waterCut) {
    return builder(name).reservoir(ReservoirInput.blackOil().waterCut(waterCut).build())
        .wells(WellsInput.builder().producerCount(wellCount).ratePerWell(ratePerWellBopd, "bbl/d")
            .build())
        .infrastructure(InfrastructureInput.builder()
            .processingLocation(InfrastructureInput.ProcessingLocation.NEW_PLATFORM)
            .exportType(InfrastructureInput.ExportType.STABILIZED_OIL).build())
        .build();
  }

  /**
   * Creates a simple gas tieback concept with default parameters.
   *
   * @param name concept name
   * @return configured FieldConcept
   */
  public static FieldConcept gasTieback(String name) {
    return gasTieback(name, 25.0, 4, 1.0);
  }

  /**
   * Creates a simple oil development concept with default parameters.
   *
   * @param name concept name
   * @return configured FieldConcept
   */
  public static FieldConcept oilDevelopment(String name) {
    return oilDevelopment(name, 6, 5000, 0.1);
  }

  // Getters

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public ReservoirInput getReservoir() {
    return reservoir;
  }

  public WellsInput getWells() {
    return wells;
  }

  public InfrastructureInput getInfrastructure() {
    return infrastructure;
  }

  // Convenience accessors

  /**
   * Gets the total production rate from all wells.
   *
   * @return total rate in the well's rate unit
   */
  public double getTotalProductionRate() {
    return wells.getTotalRate();
  }

  /**
   * Gets the production rate unit.
   *
   * @return rate unit string
   */
  public String getProductionRateUnit() {
    return wells.getRateUnit();
  }

  /**
   * Checks if this concept requires CO2 removal.
   *
   * @return true if CO2 > 2%
   */
  public boolean needsCO2Removal() {
    return reservoir.getCo2Percent() > 2.0;
  }

  /**
   * Checks if this concept requires H2S treatment.
   *
   * @return true if H2S &gt; 0.5%
   */
  public boolean needsH2STreatment() {
    return reservoir.isSour();
  }

  /**
   * Checks if this concept requires H2S removal.
   *
   * @return true if H2S &gt; 50 ppm (0.005%)
   */
  public boolean needsH2SRemoval() {
    return reservoir.getH2SPercent() > 0.005;
  }

  /**
   * Checks if this concept has water injection.
   *
   * @return true if water injection wells present
   */
  public boolean hasWaterInjection() {
    return wells.getInjectorCount() > 0;
  }

  /**
   * Checks if this concept requires dehydration.
   *
   * @return true if exporting gas that requires dehydration
   */
  public boolean needsDehydration() {
    // Check export type first
    InfrastructureInput.ExportType exportType = infrastructure.getExportType();

    // Oil exports don't require gas dehydration
    if (exportType == InfrastructureInput.ExportType.STABILIZED_OIL) {
      return false;
    }

    // Gas exports typically require dehydration for pipeline/sales specs
    if (exportType == InfrastructureInput.ExportType.DRY_GAS) {
      return true;
    }
    if (exportType == InfrastructureInput.ExportType.WET_GAS) {
      return true; // Wet gas tiebacks typically require dehydration at host
    }

    // For LNG, check reservoir type
    ReservoirInput.FluidType fluidType = reservoir.getFluidType();
    return fluidType == ReservoirInput.FluidType.LEAN_GAS
        || fluidType == ReservoirInput.FluidType.RICH_GAS
        || fluidType == ReservoirInput.FluidType.GAS_CONDENSATE;
  }

  /**
   * Checks if this is a subsea tieback concept.
   *
   * @return true if subsea
   */
  public boolean isSubseaTieback() {
    return wells.isSubsea() && infrastructure
        .getProcessingLocation() == InfrastructureInput.ProcessingLocation.HOST_PLATFORM;
  }

  /**
   * Gets the water depth in meters.
   *
   * @return water depth
   */
  public double getWaterDepth() {
    return infrastructure.getWaterDepth();
  }

  /**
   * Gets the tieback length in km.
   *
   * @return tieback length
   */
  public double getTiebackLength() {
    return infrastructure.getTiebackLength();
  }

  /**
   * Generates a summary string for this concept.
   *
   * @return summary description
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ").append(name).append(" ===\n");
    sb.append("Reservoir: ").append(reservoir.getFluidType());
    sb.append(", CO2=").append(String.format("%.1f%%", reservoir.getCo2Percent()));
    sb.append(", WC=").append(String.format("%.0f%%", reservoir.getWaterCut() * 100));
    sb.append("\n");
    sb.append("Wells: ").append(wells.getProducerCount()).append(" producers");
    sb.append(" @ ").append(String.format("%.1f", wells.getRatePerWell() / 1e6)).append(" MSm3/d");
    sb.append(", THP=").append(String.format("%.0f", wells.getThp())).append(" bara");
    sb.append("\n");
    sb.append("Infrastructure: ").append(String.format("%.0f", infrastructure.getTiebackLength()))
        .append(" km tieback");
    sb.append(", ").append(String.format("%.0f", infrastructure.getWaterDepth()))
        .append(" m depth");
    sb.append(", ").append(infrastructure.getPowerSupply());
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("FieldConcept[%s: %s, %d wells, %.0f km tieback]", name,
        reservoir.getFluidType(), wells.getProducerCount(), infrastructure.getTiebackLength());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof FieldConcept))
      return false;
    FieldConcept that = (FieldConcept) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /**
   * Builder for FieldConcept.
   */
  public static final class Builder {
    private String id;
    private final String name;
    private String description = "";
    private ReservoirInput reservoir;
    private WellsInput wells;
    private InfrastructureInput infrastructure;

    private Builder(String name) {
      this.name = Objects.requireNonNull(name, "Concept name is required");
    }

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder reservoir(ReservoirInput reservoir) {
      this.reservoir = Objects.requireNonNull(reservoir);
      return this;
    }

    public Builder wells(WellsInput wells) {
      this.wells = Objects.requireNonNull(wells);
      return this;
    }

    public Builder infrastructure(InfrastructureInput infrastructure) {
      this.infrastructure = Objects.requireNonNull(infrastructure);
      return this;
    }

    public FieldConcept build() {
      if (reservoir == null) {
        reservoir = ReservoirInput.richGas().build();
      }
      if (wells == null) {
        wells = WellsInput.builder().build();
      }
      if (infrastructure == null) {
        infrastructure = InfrastructureInput.builder().build();
      }
      return new FieldConcept(this);
    }
  }
}
