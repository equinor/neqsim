package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import neqsim.process.fielddevelopment.lifecycle.FacilityLifecycleStrategy.DevelopmentMode;

/** One executable routing option for a discovery in an area-development study. */
public final class AreaDevelopmentOption implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Physical development route. */
  public enum RouteType {
    /** Standalone new processing and export facility. */
    GREENFIELD,
    /** Connection to a producing host asset. */
    HOST_TIEBACK
  }

  private final String name;
  private final RouteType routeType;
  private final String receivingAssetName;
  private final FieldLifecycleConcept lifecycleConcept;

  private AreaDevelopmentOption(String name, RouteType routeType, String receivingAssetName,
      FieldLifecycleConcept lifecycleConcept) {
    if (name == null || name.trim().isEmpty() || receivingAssetName == null || receivingAssetName.trim().isEmpty()
        || lifecycleConcept == null) {
      throw new IllegalArgumentException("option name, receiving asset, and lifecycle concept are required");
    }
    FacilityLifecycleStrategy facility = lifecycleConcept.getConfiguration().getFacilityLifecycleStrategy();
    if (facility == null) {
      throw new IllegalArgumentException("area-development options require a facility lifecycle strategy");
    }
    DevelopmentMode requiredMode = routeType == RouteType.GREENFIELD ? DevelopmentMode.GREENFIELD
        : DevelopmentMode.BROWNFIELD_TIEBACK;
    if (facility.getDevelopmentMode() != requiredMode) {
      throw new IllegalArgumentException("route type does not match the lifecycle facility strategy");
    }
    this.name = name;
    this.routeType = routeType;
    this.receivingAssetName = receivingAssetName;
    this.lifecycleConcept = lifecycleConcept;
  }

  /** Creates a standalone greenfield option. */
  public static AreaDevelopmentOption greenfield(String name, String newFacilityName,
      FieldLifecycleConcept lifecycleConcept) {
    return new AreaDevelopmentOption(name, RouteType.GREENFIELD, newFacilityName, lifecycleConcept);
  }

  /** Creates a tieback option to a named producing asset. */
  public static AreaDevelopmentOption tieback(String name, String hostAssetName,
      FieldLifecycleConcept lifecycleConcept) {
    return new AreaDevelopmentOption(name, RouteType.HOST_TIEBACK, hostAssetName, lifecycleConcept);
  }

  /** Returns option display name. */
  public String getName() {
    return name;
  }

  /** Returns greenfield or host-tieback route. */
  public RouteType getRouteType() {
    return routeType;
  }

  /** Returns the new facility or existing receiving asset name. */
  public String getReceivingAssetName() {
    return receivingAssetName;
  }

  /** Returns the independent executable lifecycle concept. */
  public FieldLifecycleConcept getLifecycleConcept() {
    return lifecycleConcept;
  }
}
