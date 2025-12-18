package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc;

/**
 * Enum for selecting wall heat transfer models in two-phase pipe flow simulations.
 *
 * <p>
 * Different thermal boundary conditions are available for different use cases:
 * </p>
 * <ul>
 * <li>{@link #CONSTANT_WALL_TEMPERATURE} - Isothermal pipes with fixed wall temperature</li>
 * <li>{@link #CONSTANT_HEAT_FLUX} - Electric heating or constant heating/cooling rate</li>
 * <li>{@link #CONVECTIVE_BOUNDARY} - Subsea pipelines with heat transfer to surroundings</li>
 * <li>{@link #ADIABATIC} - Perfectly insulated pipes with no heat transfer</li>
 * </ul>
 *
 * @author NeqSim development team
 */
public enum WallHeatTransferModel {
  /**
   * Constant wall temperature boundary condition. The wall temperature is fixed at a specified
   * value: T_wall = T_const. Suitable for isothermal pipes or pipes with high thermal mass.
   */
  CONSTANT_WALL_TEMPERATURE("Constant Wall Temperature", "Fixed wall temperature (isothermal)"),

  /**
   * Constant heat flux boundary condition. A fixed heat flux is applied at the wall: q'' =
   * q''_const. Suitable for electric heating or controlled heating/cooling.
   */
  CONSTANT_HEAT_FLUX("Constant Heat Flux", "Fixed heat flux at wall"),

  /**
   * Convective boundary condition with heat transfer to surroundings. Heat transfer is calculated
   * using an overall heat transfer coefficient: q'' = U_overall * (T_ambient - T_fluid). Suitable
   * for subsea pipelines and buried pipes.
   */
  CONVECTIVE_BOUNDARY("Convective Boundary", "Heat transfer to surroundings"),

  /**
   * Adiabatic boundary condition with no heat transfer through the wall. The wall is perfectly
   * insulated: q'' = 0. Suitable for well-insulated pipes or short pipe sections.
   */
  ADIABATIC("Adiabatic", "No heat transfer (perfectly insulated)");

  private final String displayName;
  private final String description;

  /**
   * Constructor for WallHeatTransferModel enum.
   *
   * @param displayName the human-readable name of the model
   * @param description a brief description of the model
   */
  WallHeatTransferModel(String displayName, String description) {
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
