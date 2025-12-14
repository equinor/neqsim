package neqsim.thermo.characterization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Validation report for fluid characterization operations.
 *
 * <p>
 * This class captures key properties before and after characterization to verify that the operation
 * preserves mass, maintains reasonable property values, and achieves the desired component
 * structure.
 *
 * @author ESOL
 */
public class CharacterizationValidationReport {

  private final String sourceFluidName;
  private final String referenceFluidName;
  private final int sourcePseudoComponentCount;
  private final int referencePseudoComponentCount;
  private final int resultPseudoComponentCount;

  private final double sourceTotalMoles;
  private final double sourceTotalMass;
  private final double sourceAverageMW;

  private final double resultTotalMoles;
  private final double resultTotalMass;
  private final double resultAverageMW;

  private final double massDifferencePercent;
  private final double molesDifferencePercent;
  private final double mwDifferencePercent;

  private final Map<String, Double> sourcePseudoComponentMoles;
  private final Map<String, Double> resultPseudoComponentMoles;

  private final List<String> warnings;
  private final boolean isValid;

  private CharacterizationValidationReport(Builder builder) {
    this.sourceFluidName = builder.sourceFluidName;
    this.referenceFluidName = builder.referenceFluidName;
    this.sourcePseudoComponentCount = builder.sourcePseudoComponentCount;
    this.referencePseudoComponentCount = builder.referencePseudoComponentCount;
    this.resultPseudoComponentCount = builder.resultPseudoComponentCount;
    this.sourceTotalMoles = builder.sourceTotalMoles;
    this.sourceTotalMass = builder.sourceTotalMass;
    this.sourceAverageMW = builder.sourceAverageMW;
    this.resultTotalMoles = builder.resultTotalMoles;
    this.resultTotalMass = builder.resultTotalMass;
    this.resultAverageMW = builder.resultAverageMW;
    this.massDifferencePercent = builder.massDifferencePercent;
    this.molesDifferencePercent = builder.molesDifferencePercent;
    this.mwDifferencePercent = builder.mwDifferencePercent;
    this.sourcePseudoComponentMoles = builder.sourcePseudoComponentMoles;
    this.resultPseudoComponentMoles = builder.resultPseudoComponentMoles;
    this.warnings = builder.warnings;
    this.isValid = builder.isValid;
  }

  /**
   * Generate a validation report comparing source and characterized fluids.
   *
   * @param source the original source fluid
   * @param reference the reference fluid used for characterization
   * @param result the characterized result fluid
   * @return validation report
   */
  public static CharacterizationValidationReport generate(SystemInterface source,
      SystemInterface reference, SystemInterface result) {
    Builder builder = new Builder();

    builder.sourceFluidName = "Source";
    builder.referenceFluidName = "Reference";

    // Count pseudo-components
    builder.sourcePseudoComponentCount = countPseudoComponents(source);
    builder.referencePseudoComponentCount = countPseudoComponents(reference);
    builder.resultPseudoComponentCount = countPseudoComponents(result);

    // Calculate source properties
    builder.sourceTotalMoles = source.getTotalNumberOfMoles();
    builder.sourceTotalMass = calculateTotalMass(source);
    builder.sourceAverageMW =
        builder.sourceTotalMoles > 0 ? builder.sourceTotalMass / builder.sourceTotalMoles : 0;

    // Calculate result properties
    builder.resultTotalMoles = result.getTotalNumberOfMoles();
    builder.resultTotalMass = calculateTotalMass(result);
    builder.resultAverageMW =
        builder.resultTotalMoles > 0 ? builder.resultTotalMass / builder.resultTotalMoles : 0;

    // Calculate differences
    builder.massDifferencePercent =
        builder.sourceTotalMass > 0
            ? 100.0 * Math.abs(builder.resultTotalMass - builder.sourceTotalMass)
                / builder.sourceTotalMass
            : 0;
    builder.molesDifferencePercent =
        builder.sourceTotalMoles > 0
            ? 100.0 * Math.abs(builder.resultTotalMoles - builder.sourceTotalMoles)
                / builder.sourceTotalMoles
            : 0;
    builder.mwDifferencePercent =
        builder.sourceAverageMW > 0
            ? 100.0 * Math.abs(builder.resultAverageMW - builder.sourceAverageMW)
                / builder.sourceAverageMW
            : 0;

    // Collect pseudo-component details
    builder.sourcePseudoComponentMoles = getPseudoComponentMoles(source);
    builder.resultPseudoComponentMoles = getPseudoComponentMoles(result);

    // Generate warnings
    builder.warnings = new ArrayList<>();
    if (builder.massDifferencePercent > 0.1) {
      builder.warnings.add(String.format("Mass difference %.4f%% exceeds 0.1%% threshold",
          builder.massDifferencePercent));
    }
    if (builder.molesDifferencePercent > 0.1) {
      builder.warnings.add(String.format("Moles difference %.4f%% exceeds 0.1%% threshold",
          builder.molesDifferencePercent));
    }
    if (builder.resultPseudoComponentCount != builder.referencePseudoComponentCount) {
      builder.warnings.add(String.format("Result PC count (%d) differs from reference (%d)",
          builder.resultPseudoComponentCount, builder.referencePseudoComponentCount));
    }

    builder.isValid = builder.warnings.isEmpty();

    return builder.build();
  }

  private static int countPseudoComponents(SystemInterface fluid) {
    int count = 0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface comp = fluid.getComponent(i);
      if (comp.isIsTBPfraction() || comp.isIsPlusFraction()) {
        count++;
      }
    }
    return count;
  }

  private static double calculateTotalMass(SystemInterface fluid) {
    double mass = 0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface comp = fluid.getComponent(i);
      mass += comp.getNumberOfmoles() * comp.getMolarMass();
    }
    return mass;
  }

  private static Map<String, Double> getPseudoComponentMoles(SystemInterface fluid) {
    Map<String, Double> result = new LinkedHashMap<>();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface comp = fluid.getComponent(i);
      if (comp.isIsTBPfraction() || comp.isIsPlusFraction()) {
        result.put(comp.getComponentName(), comp.getNumberOfmoles());
      }
    }
    return result;
  }

  /**
   * Check if the characterization is valid (no warnings).
   *
   * @return true if valid
   */
  public boolean isValid() {
    return isValid;
  }

  /**
   * Get any warnings generated during validation.
   *
   * @return list of warnings
   */
  public List<String> getWarnings() {
    return new ArrayList<>(warnings);
  }

  /**
   * Get the mass conservation error as a percentage.
   *
   * @return mass difference percentage
   */
  public double getMassDifferencePercent() {
    return massDifferencePercent;
  }

  /**
   * Get the moles conservation error as a percentage.
   *
   * @return moles difference percentage
   */
  public double getMolesDifferencePercent() {
    return molesDifferencePercent;
  }

  /**
   * Get the number of pseudo-components in the source fluid.
   *
   * @return source PC count
   */
  public int getSourcePseudoComponentCount() {
    return sourcePseudoComponentCount;
  }

  /**
   * Get the number of pseudo-components in the result fluid.
   *
   * @return result PC count
   */
  public int getResultPseudoComponentCount() {
    return resultPseudoComponentCount;
  }

  /**
   * Generate a formatted report string.
   *
   * @return formatted report
   */
  public String toReportString() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Characterization Validation Report ===\n\n");

    sb.append("Pseudo-Component Counts:\n");
    sb.append(String.format("  Source:    %d PCs\n", sourcePseudoComponentCount));
    sb.append(String.format("  Reference: %d PCs\n", referencePseudoComponentCount));
    sb.append(String.format("  Result:    %d PCs\n\n", resultPseudoComponentCount));

    sb.append("Mass/Moles Conservation:\n");
    sb.append(String.format("  Source total moles:  %.6f\n", sourceTotalMoles));
    sb.append(String.format("  Result total moles:  %.6f\n", resultTotalMoles));
    sb.append(String.format("  Moles difference:    %.6f%%\n\n", molesDifferencePercent));

    sb.append(String.format("  Source total mass:   %.6f kg\n", sourceTotalMass));
    sb.append(String.format("  Result total mass:   %.6f kg\n", resultTotalMass));
    sb.append(String.format("  Mass difference:     %.6f%%\n\n", massDifferencePercent));

    sb.append(String.format("  Source avg MW:       %.4f g/mol\n", sourceAverageMW * 1000));
    sb.append(String.format("  Result avg MW:       %.4f g/mol\n", resultAverageMW * 1000));
    sb.append(String.format("  MW difference:       %.6f%%\n\n", mwDifferencePercent));

    if (!warnings.isEmpty()) {
      sb.append("Warnings:\n");
      for (String warning : warnings) {
        sb.append("  ⚠ ").append(warning).append("\n");
      }
    } else {
      sb.append("Status: ✓ Valid (no warnings)\n");
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return toReportString();
  }

  private static class Builder {
    String sourceFluidName;
    String referenceFluidName;
    int sourcePseudoComponentCount;
    int referencePseudoComponentCount;
    int resultPseudoComponentCount;
    double sourceTotalMoles;
    double sourceTotalMass;
    double sourceAverageMW;
    double resultTotalMoles;
    double resultTotalMass;
    double resultAverageMW;
    double massDifferencePercent;
    double molesDifferencePercent;
    double mwDifferencePercent;
    Map<String, Double> sourcePseudoComponentMoles;
    Map<String, Double> resultPseudoComponentMoles;
    List<String> warnings;
    boolean isValid;

    CharacterizationValidationReport build() {
      return new CharacterizationValidationReport(this);
    }
  }
}
