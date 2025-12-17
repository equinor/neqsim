package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc;

/**
 * Enum for selecting mass transfer models in two-phase pipe flow simulations.
 *
 * <p>
 * Different mass transfer models are suitable for different physical situations:
 * </p>
 * <ul>
 * <li>{@link #KRISHNA_STANDART_FILM} - Multi-component diffusion with thermodynamic correction,
 * general purpose</li>
 * <li>{@link #PENETRATION_THEORY} - Time-dependent diffusion into semi-infinite medium, best for
 * short contact times</li>
 * <li>{@link #SURFACE_RENEWAL} - Statistical distribution of surface ages, best for turbulent
 * interfaces</li>
 * </ul>
 *
 * @author NeqSim development team
 * @see <a href="https://doi.org/10.1002/aic.690220215">Krishna & Standart (1976)</a>
 */
public enum MassTransferModel {
  /**
   * Krishna-Standart film model for multi-component diffusion. This model incorporates a general
   * matrix method of solution to the Maxwell-Stefan equations with thermodynamic correction
   * factors. It is the default and most general-purpose model.
   */
  KRISHNA_STANDART_FILM("Krishna-Standart Film Model",
      "Multi-component diffusion with thermodynamic correction"),

  /**
   * Higbie's penetration theory model. Assumes time-dependent diffusion into a semi-infinite
   * medium. Best suited for situations with short contact times between phases.
   */
  PENETRATION_THEORY("Penetration Theory", "Time-dependent diffusion into semi-infinite medium"),

  /**
   * Danckwerts' surface renewal theory model. Assumes a statistical distribution of surface ages
   * due to turbulent eddies bringing fresh fluid to the interface. Best suited for turbulent
   * interfaces.
   */
  SURFACE_RENEWAL("Surface Renewal Theory", "Statistical distribution of surface ages");

  private final String displayName;
  private final String description;

  /**
   * Constructor for MassTransferModel enum.
   *
   * @param displayName the human-readable name of the model
   * @param description a brief description of the model
   */
  MassTransferModel(String displayName, String description) {
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
