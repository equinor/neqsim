package neqsim.process.fielddevelopment.screening;

import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.process.fielddevelopment.concept.WellsInput;
import neqsim.process.fielddevelopment.facility.FacilityConfig;

/**
 * Safety screener for rapid assessment of safety-related design requirements.
 *
 * <p>
 * Estimates key safety parameters for concept-level assessment:
 * <ul>
 * <li>Blowdown time estimation</li>
 * <li>Minimum design temperature (JT cooling)</li>
 * <li>Hydrocarbon inventory</li>
 * <li>PSV sizing requirements</li>
 * <li>H2S/toxic gas considerations</li>
 * <li>Fire case scenarios</li>
 * </ul>
 *
 * <p>
 * These are screening-level estimates to identify concepts that may require detailed safety
 * analysis or have inherent safety challenges.
 *
 * @author ESOL
 * @version 1.0
 */
public class SafetyScreener {
  // Blowdown constants
  private static final double BLOWDOWN_TARGET_MINUTES = 15.0;
  private static final double TYPICAL_ORIFICE_FACTOR = 0.005; // Fraction of volume per minute

  // Temperature thresholds
  private static final double LOW_TEMP_THRESHOLD_C = -46.0; // Low temp carbon steel limit

  // Pressure thresholds
  private static final double HIGH_PRESSURE_BARA = 200.0;

  // H2S thresholds
  private static final double H2S_TOXIC_THRESHOLD_PPM = 10.0;

  /**
   * Creates a new safety screener.
   */
  public SafetyScreener() {
    // Default constructor
  }

  /**
   * Performs safety screening for a field concept with facility configuration.
   *
   * @param concept field concept
   * @param facilityConfig facility configuration
   * @return safety report
   */
  public SafetyReport screen(FieldConcept concept, FacilityConfig facilityConfig) {
    SafetyReport.Builder builder = SafetyReport.builder();

    ReservoirInput reservoir = concept.getReservoir();
    WellsInput wells = concept.getWells();
    InfrastructureInput infra = concept.getInfrastructure();

    // Determine operating pressure
    double operatingPressure = wells != null ? wells.getTubeheadPressure() : 100.0;
    double exportPressure = infra != null ? infra.getExportPressure() : 180.0;
    double maxPressure = Math.max(operatingPressure, exportPressure);

    builder.highPressure(maxPressure > HIGH_PRESSURE_BARA);

    // H2S presence
    double h2sPpm = reservoir != null ? reservoir.getH2SPercent() * 10000 : 0;
    builder.h2sPresent(h2sPpm > H2S_TOXIC_THRESHOLD_PPM);

    // Estimate inventory (very rough)
    double inventoryTonnes = estimateInventory(concept, facilityConfig);
    builder.inventory(inventoryTonnes);

    // Estimate blowdown time
    double blowdownMinutes = estimateBlowdownTime(inventoryTonnes, maxPressure);
    builder.blowdownTime(blowdownMinutes);

    // Estimate minimum metal temperature (JT cooling)
    double minMetalTemp = estimateMinMetalTemp(maxPressure);
    builder.minimumMetalTemp(minMetalTemp);

    // Estimate PSV capacity
    double psvCapacity = estimatePSVCapacity(concept, maxPressure);
    builder.psvCapacity(psvCapacity);

    // Manned vs unmanned
    boolean manned = infra != null
        && infra.getProcessingLocation() != InfrastructureInput.ProcessingLocation.SUBSEA;
    builder.mannedFacility(manned);

    // Determine overall safety level
    SafetyReport.SafetyLevel level =
        determineSafetyLevel(builder, blowdownMinutes, minMetalTemp, h2sPpm, maxPressure, manned);
    builder.overallLevel(level);

    // Add requirements based on findings
    addRequirements(builder, level, h2sPpm, maxPressure, minMetalTemp, blowdownMinutes);

    // Add scenario frequencies (placeholder values)
    addScenarios(builder);

    return builder.build();
  }

  /**
   * Performs safety screening without facility details.
   *
   * @param concept field concept
   * @return safety report
   */
  public SafetyReport quickScreen(FieldConcept concept) {
    // Create a minimal facility config for screening
    return screen(concept, null);
  }

  private double estimateInventory(FieldConcept concept, FacilityConfig facilityConfig) {
    // Very rough inventory estimation based on production rate and facility complexity
    double baseInventory = 50.0; // tonnes base

    if (concept.getWells() != null) {
      double ratePerWell = concept.getWells().getRatePerWellSm3d();
      int wellCount = concept.getWells().getProducerCount();
      // Scale with production rate
      baseInventory += (ratePerWell * wellCount / 1e6) * 20.0;
    }

    if (facilityConfig != null) {
      // Add inventory for compression
      if (facilityConfig.hasCompression()) {
        baseInventory += facilityConfig.getTotalCompressionStages() * 15.0;
      }
      // Add for separation stages
      baseInventory += facilityConfig.getBlockCount() * 5.0;
    }

    return baseInventory;
  }

  private double estimateBlowdownTime(double inventoryTonnes, double pressureBara) {
    // Simplified blowdown estimation
    // Real calculation requires detailed vessel sizing and relief capacity
    double normalizedInventory = inventoryTonnes / 100.0;
    double pressureFactor = pressureBara / 100.0;

    // Assume properly sized blowdown - scale with inventory and pressure
    return 8.0 + (normalizedInventory * 4.0) + (pressureFactor * 2.0);
  }

  private double estimateMinMetalTemp(double pressureBara) {
    // JT cooling estimation - very simplified
    // Full blowdown from high pressure can reach very low temps
    if (pressureBara > 200) {
      return -60.0;
    } else if (pressureBara > 100) {
      return -50.0;
    } else if (pressureBara > 50) {
      return -40.0;
    } else {
      return -20.0;
    }
  }

  private double estimatePSVCapacity(FieldConcept concept, double pressureBara) {
    // Rough PSV sizing based on fire case
    // Real calculation requires wetted area and relief conditions
    double baseCapacity = 10000.0; // kg/hr base

    if (concept.getWells() != null) {
      double rate = concept.getWells().getRatePerWellSm3d() * concept.getWells().getProducerCount();
      // Scale with rate
      baseCapacity += (rate / 1e6) * 5000.0;
    }

    // Higher pressure = larger relief
    baseCapacity *= (pressureBara / 100.0);

    return baseCapacity;
  }

  private SafetyReport.SafetyLevel determineSafetyLevel(SafetyReport.Builder builder,
      double blowdownMinutes, double minMetalTemp, double h2sPpm, double maxPressure,
      boolean manned) {
    boolean hasIssues = false;
    boolean hasMajorIssues = false;

    // Check blowdown
    if (blowdownMinutes > BLOWDOWN_TARGET_MINUTES) {
      hasIssues = true;
      if (blowdownMinutes > BLOWDOWN_TARGET_MINUTES * 1.5) {
        hasMajorIssues = true;
      }
    }

    // Check minimum temperature
    if (minMetalTemp < LOW_TEMP_THRESHOLD_C) {
      hasIssues = true;
    }

    // Check H2S
    if (h2sPpm > H2S_TOXIC_THRESHOLD_PPM) {
      hasIssues = true;
      if (h2sPpm > 100) {
        hasMajorIssues = true;
      }
    }

    // Check pressure
    if (maxPressure > HIGH_PRESSURE_BARA) {
      hasIssues = true;
    }

    // Manned facilities have higher requirements
    if (manned && hasIssues) {
      hasMajorIssues = true;
    }

    if (hasMajorIssues) {
      return SafetyReport.SafetyLevel.HIGH;
    } else if (hasIssues) {
      return SafetyReport.SafetyLevel.ENHANCED;
    } else {
      return SafetyReport.SafetyLevel.STANDARD;
    }
  }

  private void addRequirements(SafetyReport.Builder builder, SafetyReport.SafetyLevel level,
      double h2sPpm, double maxPressure, double minMetalTemp, double blowdownMinutes) {
    if (h2sPpm > H2S_TOXIC_THRESHOLD_PPM) {
      builder.addRequirement("h2s_detection", "H2S detection and alarm system required");
      builder.addRequirement("h2s_ppe", "H2S personal protective equipment");
      if (h2sPpm > 100) {
        builder.addRequirement("h2s_escape", "Escape and rescue planning for H2S");
      }
    }

    if (maxPressure > HIGH_PRESSURE_BARA) {
      builder.addRequirement("high_pressure",
          "High pressure design - enhanced inspection and testing");
    }

    if (minMetalTemp < LOW_TEMP_THRESHOLD_C) {
      builder.addRequirement("low_temp_materials",
          "Low temperature materials required (impact tested)");
    }

    if (blowdownMinutes > BLOWDOWN_TARGET_MINUTES) {
      builder.addRequirement("blowdown_study",
          "Detailed blowdown study required - consider segmentation or larger BDV");
    }

    if (level == SafetyReport.SafetyLevel.HIGH) {
      builder.addRequirement("safety_case", "Comprehensive safety case with QRA recommended");
    }
  }

  private void addScenarios(SafetyReport.Builder builder) {
    // Placeholder scenario frequencies per year
    builder.addScenario("process_leak", 1e-3);
    builder.addScenario("pipeline_leak", 1e-4);
    builder.addScenario("vessel_rupture", 1e-6);
    builder.addScenario("fire", 1e-3);
    builder.addScenario("blowout", 1e-5);
  }
}
