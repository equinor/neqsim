package neqsim.thermo.util.gerg;

/**
 * Enumeration of GERG-2008 model variants.
 *
 * <p>
 * This enum defines the available variants of the GERG-2008 equation of state.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public enum GERG2008Type {
  /**
   * Standard GERG-2008 equation of state.
   *
   * <p>
   * Reference: Kunz, O. and Wagner, W. (2012). "The GERG-2008 Wide-Range Equation of State for
   * Natural Gases and Other Mixtures: An Expansion of GERG-2004". J. Chem. Eng. Data, 57,
   * 3032-3091.
   * </p>
   */
  STANDARD("GERG-2008", "Standard GERG-2008 equation of state"),

  /**
   * GERG-2008-H2 equation of state with improved hydrogen parameters.
   *
   * <p>
   * Reference: Beckmüller, R., Thol, M., Sampson, I., Lemmon, E.W., Span, R. (2022). "Extension of
   * the equation of state for natural gases GERG-2008 with improved hydrogen parameters". Fluid
   * Phase Equilibria, 557, 113411.
   * </p>
   */
  HYDROGEN_ENHANCED("GERG-2008-H2", "GERG-2008 with improved hydrogen parameters"),

  /**
   * GERG-2008-NH3 equation of state extended with ammonia as the 22nd component.
   *
   * <p>
   * Reference: Neumann, T., Herrig, S., Bell, I.H., Beckmüller, R., Span, R., Thol, M. (2020).
   * "Ammonia as a constituent in natural gases - Tables for the GERG-2008 mixture model". Fluid
   * Phase Equilibria, 511, 112496.
   * </p>
   *
   * <p>
   * Pure ammonia EOS: Tillner-Roth, R., Harms-Watzenberg, F., Baehr, H.D. (1993). "A Fundamental
   * Equation of State for Ammonia". DKV-Tagungsbericht, 20, 167-181.
   * </p>
   */
  AMMONIA_EXTENDED("GERG-2008-NH3", "GERG-2008 extended with ammonia (22nd component)");

  private final String name;
  private final String description;

  GERG2008Type(String name, String description) {
    this.name = name;
    this.description = description;
  }

  /**
   * Get the display name of this model type.
   *
   * @return the display name
   */
  public String getName() {
    return name;
  }

  /**
   * Get the description of this model type.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return name;
  }
}
