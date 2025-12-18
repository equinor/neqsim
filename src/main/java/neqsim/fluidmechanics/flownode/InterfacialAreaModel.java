package neqsim.fluidmechanics.flownode;

/**
 * Enumeration of interfacial area calculation models for two-phase flow.
 *
 * <p>
 * The interfacial area per unit volume (a) is critical for mass transfer calculations:
 * </p>
 *
 * <pre>
 * ṅ_i = k_L · a · ΔC_i
 * </pre>
 *
 * <p>
 * Different flow patterns require different interfacial area models based on the geometry of the
 * gas-liquid interface.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public enum InterfacialAreaModel {
  /**
   * Geometric model based on flow pattern geometry. Calculates interfacial area from physical
   * dimensions.
   */
  GEOMETRIC("Geometric"),

  /**
   * Empirical correlation model from literature.
   */
  EMPIRICAL_CORRELATION("Empirical Correlation"),

  /**
   * User-defined interfacial area.
   */
  USER_DEFINED("User Defined");

  private final String name;

  InterfacialAreaModel(String name) {
    this.name = name;
  }

  /**
   * Gets the display name of this model.
   *
   * @return the model name
   */
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
