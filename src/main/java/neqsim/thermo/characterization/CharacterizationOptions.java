package neqsim.thermo.characterization;

/**
 * Configuration options for fluid characterization operations.
 *
 * <p>
 * This class provides a fluent API for configuring how pseudo-component characterization is
 * performed, including options for:
 * <ul>
 * <li>Binary interaction parameter (BIP) transfer from reference fluids</li>
 * <li>Component naming conventions</li>
 * <li>Composition normalization</li>
 * <li>Validation report generation</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * CharacterizationOptions options =
 *     CharacterizationOptions.builder().transferBinaryInteractionParameters(true)
 *         .normalizeComposition(true).namingScheme(NamingScheme.REFERENCE).build();
 *
 * SystemInterface characterized =
 *     PseudoComponentCombiner.characterizeToReference(source, reference, options);
 * </pre>
 *
 * @author ESOL
 */
public class CharacterizationOptions {

  /**
   * Naming scheme for pseudo-components in the characterized fluid.
   */
  public enum NamingScheme {
    /** Use names from the reference fluid (e.g., C7_PC, C10_PC). */
    REFERENCE,
    /** Use sequential naming (PC1, PC2, PC3, ...). */
    SEQUENTIAL,
    /** Use carbon number based naming (C7, C10, C15, ...). */
    CARBON_NUMBER
  }

  private final boolean transferBinaryInteractionParameters;
  private final boolean normalizeComposition;
  private final NamingScheme namingScheme;
  private final boolean generateValidationReport;
  private final double compositionTolerance;

  private CharacterizationOptions(Builder builder) {
    this.transferBinaryInteractionParameters = builder.transferBinaryInteractionParameters;
    this.normalizeComposition = builder.normalizeComposition;
    this.namingScheme = builder.namingScheme;
    this.generateValidationReport = builder.generateValidationReport;
    this.compositionTolerance = builder.compositionTolerance;
  }

  /**
   * Whether to transfer binary interaction parameters from the reference fluid.
   *
   * @return true if BIPs should be transferred
   */
  public boolean isTransferBinaryInteractionParameters() {
    return transferBinaryInteractionParameters;
  }

  /**
   * Whether to normalize composition after characterization.
   *
   * @return true if composition should be normalized to sum to 1.0
   */
  public boolean isNormalizeComposition() {
    return normalizeComposition;
  }

  /**
   * The naming scheme to use for pseudo-components.
   *
   * @return the naming scheme
   */
  public NamingScheme getNamingScheme() {
    return namingScheme;
  }

  /**
   * Whether to generate a validation report comparing before/after properties.
   *
   * @return true if validation report should be generated
   */
  public boolean isGenerateValidationReport() {
    return generateValidationReport;
  }

  /**
   * Tolerance for composition normalization and validation.
   *
   * @return the composition tolerance
   */
  public double getCompositionTolerance() {
    return compositionTolerance;
  }

  /**
   * Creates a new builder with default options.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates default options with BIP transfer enabled.
   *
   * @return default options instance
   */
  public static CharacterizationOptions defaults() {
    return new Builder().build();
  }

  /**
   * Creates options with BIP transfer enabled.
   *
   * @return options with BIP transfer
   */
  public static CharacterizationOptions withBipTransfer() {
    return new Builder().transferBinaryInteractionParameters(true).build();
  }

  /**
   * Builder for CharacterizationOptions.
   */
  public static class Builder {
    private boolean transferBinaryInteractionParameters = false;
    private boolean normalizeComposition = false;
    private NamingScheme namingScheme = NamingScheme.REFERENCE;
    private boolean generateValidationReport = false;
    private double compositionTolerance = 1e-10;

    /**
     * Set whether to transfer binary interaction parameters from the reference fluid.
     *
     * <p>
     * When enabled, BIPs between pseudo-components and other components in the reference fluid will
     * be applied to the corresponding components in the characterized fluid. This is essential for
     * maintaining consistent phase behavior across multiple fluids in compositional simulation.
     *
     * @param transfer true to enable BIP transfer
     * @return this builder
     */
    public Builder transferBinaryInteractionParameters(boolean transfer) {
      this.transferBinaryInteractionParameters = transfer;
      return this;
    }

    /**
     * Set whether to normalize composition after characterization.
     *
     * <p>
     * When enabled, mole fractions will be normalized to sum to exactly 1.0 after characterization.
     *
     * @param normalize true to enable normalization
     * @return this builder
     */
    public Builder normalizeComposition(boolean normalize) {
      this.normalizeComposition = normalize;
      return this;
    }

    /**
     * Set the naming scheme for pseudo-components.
     *
     * @param scheme the naming scheme to use
     * @return this builder
     */
    public Builder namingScheme(NamingScheme scheme) {
      this.namingScheme = scheme;
      return this;
    }

    /**
     * Set whether to generate a validation report.
     *
     * <p>
     * When enabled, a validation report comparing key properties before and after characterization
     * will be generated and logged.
     *
     * @param generate true to generate validation report
     * @return this builder
     */
    public Builder generateValidationReport(boolean generate) {
      this.generateValidationReport = generate;
      return this;
    }

    /**
     * Set the tolerance for composition operations.
     *
     * @param tolerance the tolerance value
     * @return this builder
     */
    public Builder compositionTolerance(double tolerance) {
      this.compositionTolerance = tolerance;
      return this;
    }

    /**
     * Build the CharacterizationOptions instance.
     *
     * @return the configured options
     */
    public CharacterizationOptions build() {
      return new CharacterizationOptions(this);
    }
  }
}
