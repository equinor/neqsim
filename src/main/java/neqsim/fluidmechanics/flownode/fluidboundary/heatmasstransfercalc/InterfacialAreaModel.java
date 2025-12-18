package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc;

/**
 * Enum for selecting interfacial area models in two-phase pipe flow simulations.
 *
 * <p>
 * The interfacial area per unit volume (a) is critical for mass transfer calculations as it
 * directly affects the mass transfer rate: N_i = k_L * a * ΔC_i
 * </p>
 *
 * <p>
 * Different models are available:
 * </p>
 * <ul>
 * <li>{@link #GEOMETRIC} - Based on flow geometry (liquid holdup, pipe diameter)</li>
 * <li>{@link #EMPIRICAL_CORRELATION} - Literature correlations for specific flow patterns</li>
 * <li>{@link #USER_DEFINED} - Custom model with user-specified interfacial area</li>
 * </ul>
 *
 * @author NeqSim development team
 */
public enum InterfacialAreaModel {
  /**
   * Geometric model based on flow geometry. Calculates interfacial area from liquid holdup, pipe
   * diameter, and flow pattern geometry.
   *
   * <p>
   * For stratified flow: a = S_i / A (interface chord length / cross-sectional area)
   * </p>
   * <p>
   * For annular flow: a = 4/D * 1/(1-sqrt(1-α_L)) (based on film thickness)
   * </p>
   * <p>
   * For bubble/droplet flow: a = 6α/d_32 (Sauter mean diameter)
   * </p>
   */
  GEOMETRIC("Geometric Model", "Based on flow geometry"),

  /**
   * Empirical correlation model using literature correlations. Uses flow pattern-specific
   * correlations from published sources.
   */
  EMPIRICAL_CORRELATION("Empirical Correlation", "Literature correlations"),

  /**
   * User-defined model allowing custom interfacial area specification. The user provides the
   * interfacial area value directly.
   */
  USER_DEFINED("User Defined", "Custom model");

  private final String displayName;
  private final String description;

  /**
   * Constructor for InterfacialAreaModel enum.
   *
   * @param displayName the human-readable name of the model
   * @param description a brief description of the model
   */
  InterfacialAreaModel(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  /**
   * Gets the human-readable display name of the model.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Gets a brief description of the model.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
