package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Builder class for configuring lumping models with a fluent API.
 *
 * <p>
 * This builder provides a clear and intuitive way to configure lumping settings, avoiding the
 * confusion between {@code setNumberOfLumpedComponents} and {@code setNumberOfPseudoComponents}.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * // For PVTlumpingModel: keep C6-C9 separate, lump C10+ into 5 groups
 * fluid.getCharacterization().configureLumping().model("PVTlumpingModel").plusFractionGroups(5)
 *     .build();
 *
 * // For standard model: create exactly 6 total pseudo-components from C6+
 * fluid.getCharacterization().configureLumping().model("standard").totalPseudoComponents(6)
 *     .build();
 *
 * // No lumping: keep all individual SCN components
 * fluid.getCharacterization().configureLumping().noLumping().build();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class LumpingConfigBuilder {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(LumpingConfigBuilder.class);

  private final Characterise characterise;
  private String modelName = "PVTlumpingModel";
  private int plusFractionGroups = -1;
  private int totalPseudoComponents = -1;

  /**
   * Constructor for LumpingConfigBuilder.
   *
   * @param characterise the Characterise instance to configure
   */
  public LumpingConfigBuilder(Characterise characterise) {
    this.characterise = characterise;
  }

  /**
   * Set the lumping model to use.
   *
   * <p>
   * Available models:
   * </p>
   * <ul>
   * <li>{@code "PVTlumpingModel"} - Default. Keeps TBP fractions (C6-C9) separate, only lumps C10+
   * </li>
   * <li>{@code "standard"} - Lumps all fractions from C6 into equal-weight groups</li>
   * <li>{@code "no lumping"} - Keeps all individual SCN components (C6-C80)</li>
   * </ul>
   *
   * @param modelName the name of the lumping model
   * @return this builder for method chaining
   */
  public LumpingConfigBuilder model(String modelName) {
    this.modelName = modelName;
    return this;
  }

  /**
   * Configure for no lumping - keeps all individual SCN components.
   *
   * <p>
   * This is equivalent to calling {@code model("no lumping")}.
   * </p>
   *
   * @return this builder for method chaining
   */
  public LumpingConfigBuilder noLumping() {
    this.modelName = "no lumping";
    return this;
  }

  /**
   * Set the number of groups to create from the plus fraction only (C10+).
   *
   * <p>
   * <strong>Use with:</strong> {@code "PVTlumpingModel"}
   * </p>
   *
   * <p>
   * This method directly controls how many lumped groups are created from the plus fraction (C10
   * through C80). The TBP fractions (C6-C9) are kept as separate pseudo-components.
   * </p>
   *
   * <p>
   * Total pseudo-components = number of TBP fractions + plusFractionGroups
   * </p>
   *
   * @param n number of groups to create from C10+ fraction
   * @return this builder for method chaining
   */
  public LumpingConfigBuilder plusFractionGroups(int n) {
    this.plusFractionGroups = n;
    return this;
  }

  /**
   * Set the total number of pseudo-components to create.
   *
   * <p>
   * <strong>Use with:</strong> {@code "standard"} model
   * </p>
   *
   * <p>
   * This method controls the total number of pseudo-components created by lumping all heavy
   * fractions (C6 through C80) into equal-weight groups.
   * </p>
   *
   * <p>
   * <strong>Warning:</strong> When used with {@code "PVTlumpingModel"}, the actual number of lumped
   * groups is calculated as: totalPseudoComponents - numberOfTBPfractions. If this results in fewer
   * groups than the default minimum, the setting may be overridden.
   * </p>
   *
   * @param n total number of pseudo-components
   * @return this builder for method chaining
   */
  public LumpingConfigBuilder totalPseudoComponents(int n) {
    this.totalPseudoComponents = n;
    return this;
  }

  /**
   * Set custom carbon number boundaries for lumping groups.
   *
   * <p>
   * This allows matching specific PVT lab report groupings. For example:
   * </p>
   *
   * <pre>
   * // Creates groups: C6, C7-C9, C10-C14, C15-C19, C20+
   * .customBoundaries(6, 7, 10, 15, 20)
   * </pre>
   *
   * <p>
   * Each value represents the starting carbon number for a group. The final group extends to the
   * heaviest component (C80 typically).
   * </p>
   *
   * @param boundaries starting carbon numbers for each group
   * @return this builder for method chaining
   */
  public LumpingConfigBuilder customBoundaries(int... boundaries) {
    this.customBoundaries = boundaries;
    return this;
  }

  /** Custom carbon number boundaries. */
  private int[] customBoundaries = null;

  /**
   * Build and apply the lumping configuration.
   *
   * <p>
   * This method validates the configuration and applies it to the characterization model.
   * </p>
   *
   * @return the configured Characterise instance for further operations
   * @throws IllegalArgumentException if configuration is invalid
   */
  public Characterise build() {
    // Validate configuration
    if (plusFractionGroups > 0 && totalPseudoComponents > 0) {
      logger.warn(
          "Both plusFractionGroups and totalPseudoComponents are set. "
              + "plusFractionGroups ({}) will take precedence for PVTlumpingModel, "
              + "totalPseudoComponents ({}) for standard model.",
          plusFractionGroups, totalPseudoComponents);
    }

    // Set the model
    characterise.setLumpingModel(modelName);

    // Apply custom boundaries if set
    if (customBoundaries != null && customBoundaries.length > 0) {
      characterise.getLumpingModel().setCustomBoundaries(customBoundaries);
      logger.debug("Configured custom carbon number boundaries with {} groups",
          customBoundaries.length);
      return characterise;
    }

    // Apply the appropriate setting based on model type
    if ("no lumping".equalsIgnoreCase(modelName)) {
      // No configuration needed for no lumping
      logger.debug("Configured 'no lumping' model - all SCN components will be preserved");
    } else if ("PVTlumpingModel".equalsIgnoreCase(modelName)) {
      if (plusFractionGroups > 0) {
        characterise.getLumpingModel().setNumberOfLumpedComponents(plusFractionGroups);
        logger.debug("Configured PVTlumpingModel with {} plus fraction groups", plusFractionGroups);
      } else if (totalPseudoComponents > 0) {
        logger.warn("Using totalPseudoComponents with PVTlumpingModel may have unexpected results. "
            + "Consider using plusFractionGroups() instead.");
        characterise.getLumpingModel().setNumberOfPseudoComponents(totalPseudoComponents);
      }
    } else if ("standard".equalsIgnoreCase(modelName)) {
      if (totalPseudoComponents > 0) {
        characterise.getLumpingModel().setNumberOfPseudoComponents(totalPseudoComponents);
        logger.debug("Configured standard lumping model with {} total pseudo-components",
            totalPseudoComponents);
      } else if (plusFractionGroups > 0) {
        logger.warn(
            "Using plusFractionGroups with standard model. This sets numberOfLumpedComponents "
                + "which equals totalPseudoComponents for standard model.");
        characterise.getLumpingModel().setNumberOfLumpedComponents(plusFractionGroups);
      }
    }

    return characterise;
  }
}
