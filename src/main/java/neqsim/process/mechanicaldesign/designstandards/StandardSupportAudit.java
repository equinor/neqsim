package neqsim.process.mechanicaldesign.designstandards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.engineering.calculation.EquipmentDesignKernelRegistry;

/**
 * Produces an auditable view of the calculations behind standards catalogued by NeqSim.
 *
 * <p>
 * This class reports current implementation evidence only. It does not assess or reproduce the contents of the
 * published standards.
 * </p>
 */
public final class StandardSupportAudit {
  private static final String NO_CALCULATION = "None";

  private StandardSupportAudit() {
    // Utility class.
  }

  /**
   * Get implementation support for one standard.
   *
   * @param standardType standard to inspect
   * @return support description
   * @throws IllegalArgumentException if {@code standardType} is null
   */
  public static StandardSupport getSupport(StandardType standardType) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }

    String registryImplementation = StandardRegistry.getMappedImplementationClass(standardType).getSimpleName();

    switch (standardType) {
    case API_617:
      EquipmentDesignKernelRegistry.Lookup compressorImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, compressorImplementation.isImplemented(),
          registryImplementation, compressorImplementation.getImplementationClassName(),
          "Compressor-casing pressure containment, flange, nozzle-load allowance, and thermal-growth screening only; "
              + "rotor dynamics, package integration, and vendor conformity are not evaluated.");
    case API_610:
      EquipmentDesignKernelRegistry.Lookup pumpImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, pumpImplementation.isImplemented(),
          registryImplementation, pumpImplementation.getImplementationClassName(),
          "API 610 screening is connected through a pure engineering-workflow adapter; purchased-standard, project, "
              + "and vendor verification remain required.");
    case API_620:
    case API_625:
      return new StandardSupport(standardType, StandardSupportLevel.CATALOGUED, false, registryImplementation,
          NO_CALCULATION, "The registry maps this tank standard to a separator-oriented pressure-vessel class; "
              + "no edition-specific common calculation is implemented.");
    case ASME_VIII_DIV2:
      return new StandardSupport(standardType, StandardSupportLevel.CATALOGUED, false, registryImplementation,
          NO_CALCULATION, "No Division 2 pressure-vessel calculation is implemented; the legacy generic fallback is "
              + "blocked for this selection.");
    case API_661:
    case ISO_16812:
      return new StandardSupport(standardType, StandardSupportLevel.CATALOGUED, false, registryImplementation,
          NO_CALCULATION, "No standard-specific heat-exchanger mechanical calculation is connected.");
    case API_521:
      EquipmentDesignKernelRegistry.Lookup reliefImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, reliefImplementation.isImplemented(),
          registryImplementation, reliefImplementation.getImplementationClassName(),
          "Scenario aggregation, governing-case selection, relief-area sizing, and accumulated-pressure screening only; "
              + "scenario completeness, installation, and conformity require independent review.");
    case API_526:
      EquipmentDesignKernelRegistry.Lookup orificeImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, orificeImplementation.isImplemented(),
          registryImplementation, orificeImplementation.getImplementationClassName(),
          "Standard-orifice area selection only; valve pressure class, dimensions, materials, installation, and vendor "
              + "certification are not evaluated.");
    case API_2000:
      EquipmentDesignKernelRegistry.Lookup tankVentingImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING,
          tankVentingImplementation.isImplemented(), registryImplementation,
          tankVentingImplementation.getImplementationClassName(),
          "Caller-controlled normal movement/thermal demand aggregation, total emergency demand, rated-capacity "
              + "utilization, and tank pressure/vacuum limit screening only; API demand tables/equations, device "
              + "sizing, scenario derivation, roofs/refrigerated storage, installation, testing, and conformity "
              + "remain external.");
    case API_12J:
      EquipmentDesignKernelRegistry.Lookup separatorImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, separatorImplementation.isImplemented(),
          registryImplementation, separatorImplementation.getImplementationClassName(),
          "Gravity cut-diameter, K-factor, and liquid residence-time screening only; service applicability, vessel "
              + "construction, internals, and performance guarantees require independent review.");
    case NORSOK_M_506:
      EquipmentDesignKernelRegistry.Lookup corrosionImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, corrosionImplementation.isImplemented(),
          registryImplementation, corrosionImplementation.getImplementationClassName(),
          "Edition-aware adapter around the existing simplified CO2-corrosion model; wetting, water chemistry, "
              + "materials selection, localized corrosion, sour-service qualification, inhibitor availability, and "
              + "conformity assessment remain outside the calculation.");
    case ISO_5167_1:
      return new StandardSupport(standardType, StandardSupportLevel.CATALOGUED, false, registryImplementation,
          NO_CALCULATION, "The general principles and requirements are catalogued as the companion basis for "
              + "orifice-plate metering; no standalone Part 1 calculation is exposed.");
    case ISO_5167_2:
      EquipmentDesignKernelRegistry.Lookup meteringImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, meteringImplementation.isImplemented(),
          registryImplementation, meteringImplementation.getImplementationClassName(),
          "Single-phase, full-pipe, subsonic, non-pulsating concentric orifice-plate flow calculation only; "
              + "plate inspection, installation, tapping geometry evidence, uncertainty, calibration, and "
              + "custody-transfer acceptance remain outside the calculation.");
    case DNV_RP_C203:
      EquipmentDesignKernelRegistry.Lookup fatigueImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, fatigueImplementation.isImplemented(),
          registryImplementation, fatigueImplementation.getImplementationClassName(),
          "User-supplied controlled S-N curve, stress-range factors, spectrum bins, and Palmgren-Miner damage only; "
              + "stress derivation, curve/detail selection, thickness and environmental basis, SCFs, rainflow "
              + "counting, load combination, inspection planning, and conformity remain external.");
    case DNV_RP_F105:
      EquipmentDesignKernelRegistry.Lookup freeSpanImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, freeSpanImplementation.isImplemented(),
          registryImplementation, freeSpanImplementation.getImplementationClassName(),
          "Simply supported first-mode beam frequency and current/wave dimensionless screening with "
              + "caller-controlled response triggers only; soil/shoulder stiffness, multi-span interaction, response "
              + "models, direct-wave loading, ULS/FLS, fatigue, sensors, intervention, and conformity remain external.");
    case DNV_RP_F101:
      EquipmentDesignKernelRegistry.Lookup defectImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, defectImplementation.isImplemented(),
          registryImplementation, defectImplementation.getImplementationClassName(),
          "Isolated longitudinal metal-loss defect failure-pressure equation under internal pressure only, with "
              + "caller-controlled depth allowance and pressure factor; inspection uncertainty derivation, "
              + "interacting/complex defects, combined loading, cracking, growth, probabilistic calibration, "
              + "fitness-for-service acceptance, and DNV-ST-F101 design checks remain external.");
    case DNV_RP_F104:
      EquipmentDesignKernelRegistry.Lookup co2PipelineImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING,
          co2PipelineImplementation.isImplemented(), registryImplementation,
          co2PipelineImplementation.getImplementationClassName(),
          "Caller-controlled CO2/water specification margins and ordered pressure-temperature profile margins "
              + "against externally verified single-phase boundaries, MAOP, and temperature limits only; phase-model "
              + "qualification, DNV-ST-F101 design, fracture/decompression and crack arrest, materials, corrosion, "
              + "construction, safety, operation, requalification, and conformity remain external.");
    case DNV_RP_F110:
      EquipmentDesignKernelRegistry.Lookup globalBucklingImplementation = StandardRegistry
          .getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING,
          globalBucklingImplementation.isImplemented(), registryImplementation,
          globalBucklingImplementation.getImplementationClassName(),
          "Caller-controlled external-analysis compressive-force, longitudinal-strain, global-displacement, and "
              + "feed-in-length margins/utilizations only; effective-force derivation, critical buckling, Hobbs/FE "
              + "response, F114 soil models, triggers/sharing, local capacity, fatigue, ST-F101 checks, lifecycle, "
              + "and conformity remain external.");
    case DNV_RP_F114:
      EquipmentDesignKernelRegistry.Lookup pipeSoilImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, pipeSoilImplementation.isImplemented(),
          registryImplementation, pipeSoilImplementation.getImplementationClassName(),
          "Caller-controlled vertical, axial, and lateral demand/resistance margins and utilizations only; site "
              + "investigation, soil interpretation, penetration/burial response, load-displacement curves, cyclic "
              + "and time effects, uncertainty, design actions, F109/F110/F105/ST-F101 interfaces, and conformity "
              + "remain external.");
    case DNV_RP_F109:
      EquipmentDesignKernelRegistry.Lookup stabilityImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, stabilityImplementation.isImplemented(),
          registryImplementation, stabilityImplementation.getImplementationClassName(),
          "Vertical equilibrium, transparent absolute-static lateral stability, and externally supplied response "
              + "displacement checks only; generalized design tables, dynamic response generation, environmental "
              + "statistics, soil-model qualification, and conformity assessment remain external.");
    case DNV_ST_F101:
      EquipmentDesignKernelRegistry.Lookup pipelineImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, pipelineImplementation.isImplemented(),
          registryImplementation, pipelineImplementation.getImplementationClassName(),
          "Typed 2021 screening for containment, collapse, propagation buckling, load interaction, fatigue, pressure "
              + "cases, de-rating, safety class, ovality, fabrication route, and installation strain; clause-complete "
              + "conformity and engineering approval remain external.");
    default:
      return getCategorySupport(standardType, registryImplementation);
    }
  }

  /**
   * Get support descriptions for every catalogued standard.
   *
   * @return immutable list in {@link StandardType} declaration order
   */
  public static List<StandardSupport> getAllSupport() {
    List<StandardSupport> result = new ArrayList<StandardSupport>();
    for (StandardType standardType : StandardType.values()) {
      result.add(getSupport(standardType));
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Generate the Markdown table published in the mechanical-design standards guide.
   *
   * @return generated Markdown table
   */
  public static String generateMarkdownTable() {
    StringBuilder table = new StringBuilder();
    table.append("| Standard | Edition metadata | Lifecycle | Publisher source | Category | Registry factory "
        + "| Calculation path | Maturity | Current kernel | Boundary |\n");
    table.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");

    for (StandardSupport support : getAllSupport()) {
      StandardType standardType = support.getStandardType();
      StandardCatalogEntry catalogEntry = StandardCatalog.get(standardType);
      EquipmentDesignKernelRegistry.Lookup kernel = StandardRegistry.getDesignKernel(standardType);
      table.append("| ").append(escapeMarkdown(standardType.getCode())).append(" | ")
          .append(escapeMarkdown(standardType.getDefaultVersion())).append(" | ")
          .append(catalogEntry.getLifecycleStatus().name()).append(" | ").append(publisherLink(catalogEntry))
          .append(" | ").append(escapeMarkdown(standardType.getDesignStandardCategory())).append(" | ")
          .append(escapeMarkdown(support.getRegistryImplementation())).append(" | ")
          .append(escapeMarkdown(support.getCalculationImplementation())).append(" | ")
          .append(support.getSupportLevel().name()).append(" | ")
          .append(kernel.supports(StandardEdition.defaultEdition(standardType)) ? "yes" : "no").append(" | ")
          .append(escapeMarkdown(support.getLimitation())).append(" |\n");
    }
    return table.toString();
  }

  private static StandardSupport getCategorySupport(StandardType standardType, String registryImplementation) {
    StandardRequirementPackRegistry.Lookup packLookup = StandardRequirementPackRegistry.lookup(standardType);
    if (packLookup.isImplemented()) {
      StandardRequirementPack pack = packLookup.requirePack();
      int capabilityCount = pack.getCapabilities().size();
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, false, registryImplementation,
          "StandardRequirementPackRegistry (" + capabilityCount
              + (capabilityCount == 1 ? " capability)" : " capabilities)"),
          "Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not "
              + "a complete conformity assessment and is intentionally separate from the legacy factory.");
    }
    String category = standardType.getDesignStandardCategory();

    if ("pressure vessel design code".equals(category)) {
      return screening(standardType, registryImplementation,
          "Generic thin-wall separator screening only; edition-specific clauses and complete "
              + "vessel checks are not implemented.");
    }
    if ("separator process design".equals(category)) {
      return screening(standardType, registryImplementation,
          "Preliminary K-factor and sizing inputs only; standard-specific requirements are not "
              + "independently validated.");
    }
    if ("pipeline design codes".equals(category)) {
      return new StandardSupport(standardType, StandardSupportLevel.CATALOGUED, false, registryImplementation,
          NO_CALCULATION, "Catalogued pipeline selections fail closed because no edition-specific wall-thickness "
              + "calculation is connected.");
    }
    if ("compressor design codes".equals(category)) {
      return screening(standardType, registryImplementation,
          "Preliminary compressor-factor screening only; package and vendor requirements are not implemented.");
    }
    if ("material plate design codes".equals(category) || "material pipe design codes".equals(category)) {
      return screening(standardType, registryImplementation,
          "Material-property lookup only; material selection, qualification, and code acceptance are not implemented.");
    }

    return new StandardSupport(standardType, StandardSupportLevel.CATALOGUED, false, registryImplementation,
        NO_CALCULATION, "No category-specific calculation is connected.");
  }

  private static StandardSupport screening(StandardType standardType, String implementation, String limitation) {
    return new StandardSupport(standardType, StandardSupportLevel.SCREENING, true, implementation, implementation,
        limitation);
  }

  private static String escapeMarkdown(String value) {
    return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
  }

  private static String publisherLink(StandardCatalogEntry entry) {
    if (entry.getPublisherSourceUrl().isEmpty()) {
      return "unverified";
    }
    return "[publisher](" + entry.getPublisherSourceUrl() + ") (checked " + entry.getVerifiedOn() + ")";
  }
}
