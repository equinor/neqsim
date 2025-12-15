package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.facility.BlockConfig;
import neqsim.process.fielddevelopment.facility.BlockType;
import neqsim.process.fielddevelopment.facility.FacilityConfig;

/**
 * Emissions tracker for concept-level CO2 intensity estimation.
 *
 * <p>
 * Estimates greenhouse gas emissions based on:
 * <ul>
 * <li>Power consumption (compression, pumping, etc.)</li>
 * <li>Flaring and venting</li>
 * <li>Fugitive emissions</li>
 * <li>CO2 from produced fluids (if vented)</li>
 * </ul>
 *
 * <p>
 * Provides CO2 intensity metrics (kg CO2e per boe) for concept comparison.
 *
 * @author ESOL
 * @version 1.0
 */
public class EmissionsTracker {

  // Emission factors
  private static final double GAS_TURBINE_KG_CO2_PER_MWH = 500.0;
  private static final double GRID_POWER_KG_CO2_PER_MWH = 50.0; // Nordic grid
  private static final double FLARE_EFFICIENCY = 0.98;
  private static final double FLARE_KG_CO2_PER_SM3 = 2.75; // CO2 from CH4 combustion
  private static final double FUGITIVE_PERCENT = 0.01; // 0.01% fugitive emissions (modern facility)
  private static final double METHANE_GWP = 25.0; // 100-year GWP

  // Power estimates (MW)
  private static final double COMPRESSION_MW_PER_STAGE = 5.0;
  private static final double TEG_REGEN_MW = 1.0;
  private static final double MEG_REGEN_MW = 2.0;
  private static final double CO2_REMOVAL_MW_PER_PERCENT = 1.0;
  private static final double BASE_FACILITY_MW = 2.0;

  /**
   * Creates a new emissions tracker.
   */
  public EmissionsTracker() {
    // Default constructor
  }

  /**
   * Estimates emissions for a concept with facility configuration.
   *
   * @param concept field concept
   * @param facilityConfig facility configuration
   * @return emissions report
   */
  public EmissionsReport estimate(FieldConcept concept, FacilityConfig facilityConfig) {
    EmissionsReport.Builder builder = EmissionsReport.builder();

    // Estimate power consumption
    double totalPowerMW = estimatePowerConsumption(concept, facilityConfig);
    builder.totalPowerMW(totalPowerMW);

    // Determine power source and calculate power emissions
    InfrastructureInput.PowerSupply powerSupply =
        concept.getInfrastructure() != null ? concept.getInfrastructure().getPowerSupply()
            : InfrastructureInput.PowerSupply.GAS_TURBINE;

    double emissionFactor = getEmissionFactor(powerSupply);
    builder.powerSource(powerSupply.name());

    // Annual power emissions (assuming 8000 operating hours)
    double annualPowerEmissionsTonnes = totalPowerMW * 8000.0 * emissionFactor / 1000.0;
    builder.powerEmissionsTonnesPerYear(annualPowerEmissionsTonnes);
    builder.addEmissionSource("power_generation", annualPowerEmissionsTonnes);

    // Estimate flaring emissions
    double flaringEmissions = estimateFlaringEmissions(concept, facilityConfig);
    builder.flaringEmissionsTonnesPerYear(flaringEmissions);
    builder.addEmissionSource("flaring", flaringEmissions);

    // Estimate fugitive emissions
    double productionSm3d = getProductionRate(concept);
    double fugitiveEmissions = estimateFugitiveEmissions(productionSm3d);
    builder.fugitiveEmissionsTonnesPerYear(fugitiveEmissions);
    builder.addEmissionSource("fugitive", fugitiveEmissions);

    // Vented CO2 (if CO2 removal without injection)
    double ventedCO2 = estimateVentedCO2(concept, facilityConfig);
    builder.ventedCO2TonnesPerYear(ventedCO2);
    if (ventedCO2 > 0) {
      builder.addEmissionSource("vented_co2", ventedCO2);
    }

    // Total emissions
    double totalEmissions =
        annualPowerEmissionsTonnes + flaringEmissions + fugitiveEmissions + ventedCO2;
    builder.totalEmissionsTonnesPerYear(totalEmissions);

    // Calculate intensity
    double annualProductionBoe = getAnnualProductionBoe(concept);
    if (annualProductionBoe > 0) {
      double intensity = totalEmissions * 1000.0 / annualProductionBoe; // kg CO2e per boe
      builder.intensityKgCO2PerBoe(intensity);
    }

    return builder.build();
  }

  /**
   * Quick emissions estimate without detailed facility config.
   *
   * @param concept field concept
   * @return emissions report
   */
  public EmissionsReport quickEstimate(FieldConcept concept) {
    return estimate(concept, null);
  }

  private double estimatePowerConsumption(FieldConcept concept, FacilityConfig facilityConfig) {
    double power = BASE_FACILITY_MW;

    if (facilityConfig != null) {
      // Compression
      for (BlockConfig block : facilityConfig.getBlocksOfType(BlockType.COMPRESSION)) {
        int stages = block.getIntParameter("stages", 1);
        power += stages * COMPRESSION_MW_PER_STAGE;
      }

      // TEG dehydration
      if (facilityConfig.hasBlock(BlockType.TEG_DEHYDRATION)) {
        power += TEG_REGEN_MW;
      }

      // MEG regeneration
      if (facilityConfig.hasBlock(BlockType.MEG_REGENERATION)) {
        power += MEG_REGEN_MW;
      }

      // CO2 removal
      if (facilityConfig.hasCo2Removal() && concept.getReservoir() != null) {
        power += concept.getReservoir().getCo2Percent() * CO2_REMOVAL_MW_PER_PERCENT;
      }
    } else {
      // Estimate based on concept alone
      if (concept.needsCO2Removal()) {
        power += 5.0;
      }
      if (concept.needsDehydration()) {
        power += 2.0;
      }
      if (concept.getWells() != null) {
        double rate =
            concept.getWells().getRatePerWellSm3d() * concept.getWells().getProducerCount();
        // Assume ~2 stages compression for most cases
        power += (rate / 1e6) * 3.0;
      }
    }

    return power;
  }

  private double getEmissionFactor(InfrastructureInput.PowerSupply powerSupply) {
    switch (powerSupply) {
      case POWER_FROM_SHORE:
        return GRID_POWER_KG_CO2_PER_MWH;
      case GAS_TURBINE:
        return GAS_TURBINE_KG_CO2_PER_MWH;
      case COMBINED_CYCLE:
        return GAS_TURBINE_KG_CO2_PER_MWH * 0.7; // More efficient
      case DIESEL:
        return GAS_TURBINE_KG_CO2_PER_MWH * 1.2; // Less efficient
      default:
        return GAS_TURBINE_KG_CO2_PER_MWH;
    }
  }

  private double estimateFlaringEmissions(FieldConcept concept, FacilityConfig facilityConfig) {
    // Estimate routine flaring + pilot
    double productionSm3d = getProductionRate(concept);

    // Assume 0.1% routine flaring + pilot
    double flaredSm3d = productionSm3d * 0.001;
    double annualFlaredSm3 = flaredSm3d * 365.0;

    return annualFlaredSm3 * FLARE_KG_CO2_PER_SM3 / 1000.0; // tonnes
  }

  private double estimateFugitiveEmissions(double productionSm3d) {
    // Fugitive methane emissions
    double fugitiveSm3d = productionSm3d * FUGITIVE_PERCENT / 100.0;
    double annualFugitiveSm3 = fugitiveSm3d * 365.0;

    // Convert to CO2e (methane density ~0.7 kg/m3)
    double methaneKg = annualFugitiveSm3 * 0.7;
    return methaneKg * METHANE_GWP / 1000.0; // tonnes CO2e
  }

  private double estimateVentedCO2(FieldConcept concept, FacilityConfig facilityConfig) {
    // If CO2 removal without CCS, the CO2 is vented
    if (concept.getReservoir() == null || !concept.needsCO2Removal()) {
      return 0.0;
    }

    double co2Percent = concept.getReservoir().getCo2Percent();
    double productionSm3d = getProductionRate(concept);

    // CO2 removed and vented
    double co2Sm3d = productionSm3d * co2Percent / 100.0;
    double annualCO2Sm3 = co2Sm3d * 365.0;

    // CO2 density ~1.98 kg/m3 at standard conditions
    return annualCO2Sm3 * 1.98 / 1000.0; // tonnes
  }

  private double getProductionRate(FieldConcept concept) {
    if (concept.getWells() != null) {
      return concept.getWells().getRatePerWellSm3d() * concept.getWells().getProducerCount();
    }
    return 1e6; // Default 1 MSm3/d
  }

  private double getAnnualProductionBoe(FieldConcept concept) {
    double sm3d = getProductionRate(concept);
    // Gas: ~6000 Sm3 per boe
    // Assume mostly gas for this estimate
    double boepd = sm3d / 6000.0;
    return boepd * 365.0;
  }

  /**
   * Emissions report from screening.
   */
  public static final class EmissionsReport implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double totalPowerMW;
    private final String powerSource;
    private final double powerEmissionsTonnesPerYear;
    private final double flaringEmissionsTonnesPerYear;
    private final double fugitiveEmissionsTonnesPerYear;
    private final double ventedCO2TonnesPerYear;
    private final double totalEmissionsTonnesPerYear;
    private final double intensityKgCO2PerBoe;
    private final Map<String, Double> emissionSources;

    private EmissionsReport(Builder builder) {
      this.totalPowerMW = builder.totalPowerMW;
      this.powerSource = builder.powerSource;
      this.powerEmissionsTonnesPerYear = builder.powerEmissionsTonnesPerYear;
      this.flaringEmissionsTonnesPerYear = builder.flaringEmissionsTonnesPerYear;
      this.fugitiveEmissionsTonnesPerYear = builder.fugitiveEmissionsTonnesPerYear;
      this.ventedCO2TonnesPerYear = builder.ventedCO2TonnesPerYear;
      this.totalEmissionsTonnesPerYear = builder.totalEmissionsTonnesPerYear;
      this.intensityKgCO2PerBoe = builder.intensityKgCO2PerBoe;
      this.emissionSources = new LinkedHashMap<>(builder.emissionSources);
    }

    public static Builder builder() {
      return new Builder();
    }

    // Getters
    public double getTotalPowerMW() {
      return totalPowerMW;
    }

    public String getPowerSource() {
      return powerSource;
    }

    public double getPowerEmissionsTonnesPerYear() {
      return powerEmissionsTonnesPerYear;
    }

    public double getFlaringEmissionsTonnesPerYear() {
      return flaringEmissionsTonnesPerYear;
    }

    public double getFugitiveEmissionsTonnesPerYear() {
      return fugitiveEmissionsTonnesPerYear;
    }

    public double getVentedCO2TonnesPerYear() {
      return ventedCO2TonnesPerYear;
    }

    public double getTotalEmissionsTonnesPerYear() {
      return totalEmissionsTonnesPerYear;
    }

    public double getIntensityKgCO2PerBoe() {
      return intensityKgCO2PerBoe;
    }

    public Map<String, Double> getEmissionSources() {
      return new LinkedHashMap<>(emissionSources);
    }

    /**
     * Classifies the intensity as low/medium/high.
     *
     * @return intensity classification
     */
    public String getIntensityClass() {
      if (intensityKgCO2PerBoe < 10) {
        return "LOW";
      } else if (intensityKgCO2PerBoe < 25) {
        return "MEDIUM";
      } else {
        return "HIGH";
      }
    }

    /**
     * Gets summary suitable for reporting.
     *
     * @return summary string
     */
    public String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("Emissions Assessment:\n");
      sb.append("  Power: ").append(String.format("%.1f", totalPowerMW)).append(" MW (")
          .append(powerSource).append(")\n");
      sb.append("  Annual emissions: ").append(String.format("%.0f", totalEmissionsTonnesPerYear))
          .append(" tonnes CO2e\n");
      sb.append("    - Power: ").append(String.format("%.0f", powerEmissionsTonnesPerYear))
          .append(" t\n");
      sb.append("    - Flaring: ").append(String.format("%.0f", flaringEmissionsTonnesPerYear))
          .append(" t\n");
      sb.append("    - Fugitive: ").append(String.format("%.0f", fugitiveEmissionsTonnesPerYear))
          .append(" t\n");
      if (ventedCO2TonnesPerYear > 0) {
        sb.append("    - Vented CO2: ").append(String.format("%.0f", ventedCO2TonnesPerYear))
            .append(" t\n");
      }
      sb.append("  Intensity: ").append(String.format("%.1f", intensityKgCO2PerBoe))
          .append(" kg CO2e/boe (").append(getIntensityClass()).append(")\n");
      return sb.toString();
    }

    @Override
    public String toString() {
      return String.format("EmissionsReport[%.0f t/yr, %.1f kg/boe (%s)]",
          totalEmissionsTonnesPerYear, intensityKgCO2PerBoe, getIntensityClass());
    }

    /**
     * Builder for EmissionsReport.
     */
    public static final class Builder {
      private double totalPowerMW;
      private String powerSource = "UNKNOWN";
      private double powerEmissionsTonnesPerYear;
      private double flaringEmissionsTonnesPerYear;
      private double fugitiveEmissionsTonnesPerYear;
      private double ventedCO2TonnesPerYear;
      private double totalEmissionsTonnesPerYear;
      private double intensityKgCO2PerBoe;
      private final Map<String, Double> emissionSources = new LinkedHashMap<>();

      public Builder totalPowerMW(double mw) {
        this.totalPowerMW = mw;
        return this;
      }

      public Builder powerSource(String source) {
        this.powerSource = source;
        return this;
      }

      public Builder powerEmissionsTonnesPerYear(double tonnes) {
        this.powerEmissionsTonnesPerYear = tonnes;
        return this;
      }

      public Builder flaringEmissionsTonnesPerYear(double tonnes) {
        this.flaringEmissionsTonnesPerYear = tonnes;
        return this;
      }

      public Builder fugitiveEmissionsTonnesPerYear(double tonnes) {
        this.fugitiveEmissionsTonnesPerYear = tonnes;
        return this;
      }

      public Builder ventedCO2TonnesPerYear(double tonnes) {
        this.ventedCO2TonnesPerYear = tonnes;
        return this;
      }

      public Builder totalEmissionsTonnesPerYear(double tonnes) {
        this.totalEmissionsTonnesPerYear = tonnes;
        return this;
      }

      public Builder intensityKgCO2PerBoe(double intensity) {
        this.intensityKgCO2PerBoe = intensity;
        return this;
      }

      public Builder addEmissionSource(String source, double tonnes) {
        this.emissionSources.put(source, tonnes);
        return this;
      }

      public EmissionsReport build() {
        return new EmissionsReport(this);
      }
    }
  }
}
