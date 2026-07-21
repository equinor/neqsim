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
    case API_650:
    case API_620:
      return new StandardSupport(standardType, StandardSupportLevel.CATALOGUED, false, registryImplementation,
          NO_CALCULATION, "The registry maps this tank standard to a separator-oriented pressure-vessel class; "
              + "no tank-code calculation is implemented.");
    case API_660:
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
    case API_12J:
      EquipmentDesignKernelRegistry.Lookup separatorImplementation = StandardRegistry.getDesignKernel(standardType);
      return new StandardSupport(standardType, StandardSupportLevel.SCREENING, separatorImplementation.isImplemented(),
          registryImplementation, separatorImplementation.getImplementationClassName(),
          "Gravity cut-diameter, K-factor, and liquid residence-time screening only; service applicability, vessel "
              + "construction, internals, and performance guarantees require independent review.");
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
    table.append("| Standard | Edition metadata | Category | Registry factory | Calculation path "
        + "| Maturity | Boundary |\n");
    table.append("| --- | --- | --- | --- | --- | --- | --- |\n");

    for (StandardSupport support : getAllSupport()) {
      StandardType standardType = support.getStandardType();
      table.append("| ").append(escapeMarkdown(standardType.getCode())).append(" | ")
          .append(escapeMarkdown(standardType.getDefaultVersion())).append(" | ")
          .append(escapeMarkdown(standardType.getDesignStandardCategory())).append(" | ")
          .append(escapeMarkdown(support.getRegistryImplementation())).append(" | ")
          .append(escapeMarkdown(support.getCalculationImplementation())).append(" | ")
          .append(support.getSupportLevel().name()).append(" | ").append(escapeMarkdown(support.getLimitation()))
          .append(" |\n");
    }
    return table.toString();
  }

  private static StandardSupport getCategorySupport(StandardType standardType, String registryImplementation) {
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
      return screening(standardType, registryImplementation,
          "Preliminary category screening with fixed fallback values; not a complete "
              + "edition-specific wall-thickness calculation.");
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
}
