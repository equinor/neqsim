package neqsim.process.equipment.filter;

/**
 * Common filter constructions used in oil and gas process systems.
 *
 * <p>
 * The values are engineering starting points for simulation and preliminary sizing, not vendor guarantees. A project
 * should replace them with element test data and the selected supplier's allowable velocities.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public enum FilterType {
  /** Replaceable depth or pleated cartridge element. */
  CARTRIDGE("Cartridge", FilterPressureDropModel.FLOW_SCALED, 1.5, 0.15, 1.0, 0.15, 1.0, false),

  /** Replaceable bag element, commonly used for produced water and liquid service. */
  BAG("Bag", FilterPressureDropModel.FLOW_SCALED, 1.5, 0.10, 0.5, 0.18, 0.8, false),

  /** Simple Y-pattern screen or strainer. */
  Y_STRAINER("Y-strainer", FilterPressureDropModel.FLOW_SCALED, 2.0, 1.5, 0.25, 0.20, 0.5, false),

  /** Basket screen with a larger dirt-holding area than a Y-strainer. */
  BASKET_STRAINER("Basket strainer", FilterPressureDropModel.FLOW_SCALED, 2.0, 1.0, 0.5, 0.30, 0.8, false),

  /** Gas or liquid coalescing element for dispersed droplets and aerosols. */
  COALESCER("Coalescer", FilterPressureDropModel.FLOW_SCALED, 2.0, 0.08, 1.5, 0.15, 1.0, false),

  /** Fixed granular-media bed such as sand, nutshell, or guard media. */
  GRANULAR_MEDIA("Granular media", FilterPressureDropModel.ERGUN, 1.0, 0.02, 1.0, 0.0, 1.0, false),

  /** Granular-media filter designed for periodic backwash. */
  BACKWASHABLE_MEDIA("Backwashable media", FilterPressureDropModel.ERGUN, 1.0, 0.02, 1.0, 0.0, 1.0, true),

  /** Activated-carbon or other replaceable sorbent guard medium. */
  ACTIVATED_CARBON("Activated carbon", FilterPressureDropModel.ERGUN, 1.0, 0.02, 1.0, 0.0, 1.0, true);

  private final String displayName;
  private final FilterPressureDropModel defaultPressureDropModel;
  private final double defaultFlowExponent;
  private final double defaultMaximumFaceVelocity;
  private final double defaultElementArea;
  private final double defaultElementDiameter;
  private final double defaultElementLength;
  private final boolean backwashSupported;

  FilterType(String displayName, FilterPressureDropModel defaultPressureDropModel, double defaultFlowExponent,
      double defaultMaximumFaceVelocity, double defaultElementArea, double defaultElementDiameter,
      double defaultElementLength, boolean backwashSupported) {
    this.displayName = displayName;
    this.defaultPressureDropModel = defaultPressureDropModel;
    this.defaultFlowExponent = defaultFlowExponent;
    this.defaultMaximumFaceVelocity = defaultMaximumFaceVelocity;
    this.defaultElementArea = defaultElementArea;
    this.defaultElementDiameter = defaultElementDiameter;
    this.defaultElementLength = defaultElementLength;
    this.backwashSupported = backwashSupported;
  }

  /** @return user-facing filter type name */
  public String getDisplayName() {
    return displayName;
  }

  /** @return recommended default hydraulic model */
  public FilterPressureDropModel getDefaultPressureDropModel() {
    return defaultPressureDropModel;
  }

  /** @return recommended flow exponent for reference-flow scaling */
  public double getDefaultFlowExponent() {
    return defaultFlowExponent;
  }

  /** @return preliminary maximum element face velocity in m/s */
  public double getDefaultMaximumFaceVelocity() {
    return defaultMaximumFaceVelocity;
  }

  /** @return preliminary effective filtration area per element in m2 */
  public double getDefaultElementArea() {
    return defaultElementArea;
  }

  /** @return preliminary element envelope diameter in m */
  public double getDefaultElementDiameter() {
    return defaultElementDiameter;
  }

  /** @return preliminary element length in m */
  public double getDefaultElementLength() {
    return defaultElementLength;
  }

  /** @return true when periodic backwash is normally applicable */
  public boolean isBackwashSupported() {
    return backwashSupported;
  }
}
