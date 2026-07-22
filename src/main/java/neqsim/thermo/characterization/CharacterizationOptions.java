package neqsim.thermo.characterization;

/**
 * Configuration options for fluid characterization operations.
 *
 * <p>
 * This class provides a fluent API for configuring how pseudo-component characterization is performed, including
 * options for:
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
 * CharacterizationOptions options = CharacterizationOptions.builder().transferBinaryInteractionParameters(true)
 *     .normalizeComposition(true).namingScheme(NamingScheme.REFERENCE).build();
 *
 * SystemInterface characterized = PseudoComponentCombiner.characterizeToReference(source, reference, options);
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
  private final boolean inheritReferenceProperties;
  private final boolean delumpBeforeRecharacterization;
  private final int delumpResolution;
  private final boolean sharedImaginaryBoundaries;

  private CharacterizationOptions(Builder builder) {
    this.transferBinaryInteractionParameters = builder.transferBinaryInteractionParameters;
    this.normalizeComposition = builder.normalizeComposition;
    this.namingScheme = builder.namingScheme;
    this.generateValidationReport = builder.generateValidationReport;
    this.compositionTolerance = builder.compositionTolerance;
    this.inheritReferenceProperties = builder.inheritReferenceProperties;
    this.delumpBeforeRecharacterization = builder.delumpBeforeRecharacterization;
    this.delumpResolution = builder.delumpResolution;
    this.sharedImaginaryBoundaries = builder.sharedImaginaryBoundaries;
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
   * Whether the characterized fluid should inherit the reference fluid's pseudo-component properties (molar mass,
   * density, critical constants, etc.).
   *
   * <p>
   * When {@code true} (the default), the characterized fluid reproduces the Pedersen et al. (Chapter 5.6) "Common EoS"
   * slate: every fluid characterized to the same reference shares an identical set of pseudo-component properties and
   * differs only in the mole fractions. This is required when several fluids must be mixed or compared on a common
   * equation-of-state basis.
   *
   * <p>
   * When {@code false}, the characterized fluid keeps the grid-only behaviour of the bare
   * {@link PseudoComponentCombiner#characterizeToReference(neqsim.thermo.system.SystemInterface, neqsim.thermo.system.SystemInterface)}
   * method: only the reference cut boundaries are reused and the lump properties are recomputed from the source fluid's
   * mass.
   *
   * @return true if reference pseudo-component properties should be inherited
   */
  public boolean isInheritReferenceProperties() {
    return inheritReferenceProperties;
  }

  /**
   * Whether each source pseudo-component should be delumped into a finer grid of single-carbon-number (SCN)
   * sub-fractions before it is re-distributed onto the reference cuts.
   *
   * <p>
   * Disabled by default. When {@code true}, every coarse source lump is split into {@link #getDelumpResolution()}
   * sub-fractions whose moles and mass exactly reproduce the parent (Pedersen et al., Chapter 5, lumping/delumping,
   * Eqs. 5.35-5.37). The sub-fractions are then re-lumped onto the reference boundaries, so the per-cut molar mass and
   * density are recomputed from a properly conserved mass distribution instead of being frozen by an effectively
   * identity source-to-reference mapping. This removes the per-field molar-mass and density drift that occurs when the
   * native source lumps already sit close to the reference grid.
   *
   * @return true if source lumps should be delumped before re-characterization
   */
  public boolean isDelumpBeforeRecharacterization() {
    return delumpBeforeRecharacterization;
  }

  /**
   * Number of single-carbon-number sub-fractions each source lump is split into when
   * {@link #isDelumpBeforeRecharacterization()} is enabled.
   *
   * @return the delump resolution (number of sub-fractions per source lump)
   */
  public int getDelumpResolution() {
    return delumpResolution;
  }

  /**
   * Whether the reference cut boundaries should be placed as carbon-number-based equal-mass cuts on the reference
   * fluid's imaginary (fine-resolution) composition, instead of the simple boiling-point midpoints between adjacent
   * reference pseudo-components.
   *
   * <p>
   * Disabled by default (boiling-point midpoints are used). When {@code true}, the reference is the single
   * representative composition (NFLUID = 1) of the Pedersen et al. (Chapter 5.6) common-slate scheme: each reference
   * pseudo-component is delumped into {@link #getDelumpResolution()} single-carbon-number sub-fractions to rebuild the
   * imaginary molar composition (Eqs. 5.58-5.59), and the cut points are then placed so each cut carries an equal mass
   * fraction (Section 5.3). Each boundary is clamped to lie strictly between the two adjacent reference
   * pseudo-component sorting keys, so the strict one-to-one property-inheritance mapping is preserved even when the
   * reference lumps are not equal in mass.
   *
   * @return true if equal-mass cut points on the reference imaginary composition should be used
   */
  public boolean isSharedImaginaryBoundaries() {
    return sharedImaginaryBoundaries;
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
    private boolean inheritReferenceProperties = true;
    private boolean delumpBeforeRecharacterization = false;
    private int delumpResolution = 12;
    private boolean sharedImaginaryBoundaries = false;

    /**
     * Set whether to transfer binary interaction parameters from the reference fluid.
     *
     * <p>
     * When enabled, BIPs between pseudo-components and other components in the reference fluid will be applied to the
     * corresponding components in the characterized fluid. This is essential for maintaining consistent phase behavior
     * across multiple fluids in compositional simulation.
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
     * When enabled, a validation report comparing key properties before and after characterization will be generated
     * and logged.
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
     * Set whether the characterized fluid should inherit the reference fluid's pseudo-component properties (molar mass,
     * density, critical constants, etc.).
     *
     * <p>
     * Enabled by default to reproduce the Pedersen et al. (Chapter 5.6) "Common EoS" slate, in which every fluid
     * characterized to the same reference shares an identical set of pseudo-component properties and differs only in
     * the mole fractions. Set to {@code false} to keep the grid-only behaviour where lump properties are recomputed
     * from the source fluid.
     *
     * @param inherit true to inherit reference pseudo-component properties
     * @return this builder
     */
    public Builder inheritReferenceProperties(boolean inherit) {
      this.inheritReferenceProperties = inherit;
      return this;
    }

    /**
     * Set whether each source pseudo-component should be delumped into a finer grid of single-carbon-number
     * sub-fractions before being re-distributed onto the reference cuts.
     *
     * <p>
     * Disabled by default. Enable to conserve per-cut mass and recompute lump molar mass and density from a properly
     * redistributed source slate (Pedersen et al., Chapter 5, lumping/delumping). Most effective together with
     * {@link #inheritReferenceProperties(boolean) inheritReferenceProperties(false)}, because inheriting the reference
     * molar mass and density would otherwise overwrite the recomputed lump properties.
     *
     * @param delump true to delump source lumps before re-characterization
     * @return this builder
     */
    public Builder delumpBeforeRecharacterization(boolean delump) {
      this.delumpBeforeRecharacterization = delump;
      return this;
    }

    /**
     * Set the number of single-carbon-number sub-fractions each source lump is split into when delumping is enabled.
     *
     * @param resolution the delump resolution; values of 1 or less disable splitting
     * @return this builder
     */
    public Builder delumpResolution(int resolution) {
      this.delumpResolution = resolution;
      return this;
    }

    /**
     * Set whether the reference cut boundaries should be placed as carbon-number-based equal-mass cuts on the reference
     * fluid's imaginary (fine-resolution) composition, instead of the simple boiling-point midpoints between adjacent
     * reference pseudo-components.
     *
     * <p>
     * Disabled by default. Enable to follow the Pedersen et al. (Chapter 5.6) common-slate cut-point rule (Eqs.
     * 5.58-5.59 with NFLUID = 1, Section 5.3 equal-mass lumping) for the reference-only path. The
     * {@link #delumpResolution(int) delump resolution} controls how finely each reference lump is split when rebuilding
     * the imaginary composition. Boundaries are clamped between adjacent reference pseudo-component sorting keys so the
     * property-inheritance mapping stays one-to-one.
     *
     * @param shared true to use equal-mass cut points on the reference imaginary composition
     * @return this builder
     */
    public Builder sharedImaginaryBoundaries(boolean shared) {
      this.sharedImaginaryBoundaries = shared;
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
