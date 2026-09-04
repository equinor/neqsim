package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable source evidence for one DEXPI actuating-function occurrence.
 *
 * <p>
 * This record preserves explicit XML identity and references without inferring control intent, final-element type,
 * safeguard classification, or live simulation topology.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiActuatingFunctionInfo implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Explicit DEXPI actuating-function kind. */
  public enum Kind {
    /** Mechanical or otherwise non-electrical actuating function. */
    ACTUATING_FUNCTION,
    /** Electrical actuating function. */
    ACTUATING_ELECTRICAL_FUNCTION
  }

  private final String id;
  private final Kind kind;
  private final String componentClass;
  private final String functionNumber;
  private final String instrumentationFunctionId;
  private final boolean instrumentationFunctionResolved;
  private final String instrumentationFunctionElementName;
  private final String finalControlElementId;
  private final boolean finalControlElementResolved;
  private final String finalControlElementName;
  private final String finalControlElementComponentClass;
  private final String finalControlElementComponentName;
  private final String finalControlElementTagName;
  private final String locationId;
  private final boolean locationResolved;
  private final String locationElementName;
  private final String locationComponentClass;
  private final String locationComponentName;
  private final String locationTagName;

  /**
   * Creates immutable evidence for one actuating-function occurrence.
   *
   * @param id source XML identity, or an empty string when absent
   * @param kind explicit source kind
   * @param componentClass source component class
   * @param functionNumber explicit source function-number metadata, or an empty string
   * @param instrumentationFunctionId enclosing instrumentation-function identity, or an empty string
   * @param instrumentationFunctionResolved whether the enclosing identity resolves to the enclosing source element
   * @param instrumentationFunctionElementName enclosing XML element name, or an empty string
   * @param finalControlElementId explicit final-control-element reference, or an empty string
   * @param finalControlElementResolved whether the final-control-element reference resolves
   * @param finalControlElementName resolved final-control-element XML name, or an empty string
   * @param locationId explicit {@code is located in} reference, or an empty string
   * @param locationResolved whether the location reference resolves
   * @param locationElementName resolved location XML name, or an empty string
   */
  public DexpiActuatingFunctionInfo(String id, Kind kind, String componentClass, String functionNumber,
      String instrumentationFunctionId, boolean instrumentationFunctionResolved,
      String instrumentationFunctionElementName, String finalControlElementId, boolean finalControlElementResolved,
      String finalControlElementName, String locationId, boolean locationResolved, String locationElementName) {
    this(id, kind, componentClass, functionNumber, instrumentationFunctionId, instrumentationFunctionResolved,
        instrumentationFunctionElementName, finalControlElementId, finalControlElementResolved, finalControlElementName,
        "", "", "", locationId, locationResolved, locationElementName, "", "", "");
  }

  /**
   * Creates immutable evidence for one actuating-function occurrence with explicit resolved-target metadata.
   *
   * @param id source XML identity, or an empty string when absent
   * @param kind explicit source kind
   * @param componentClass source component class
   * @param functionNumber explicit source function-number metadata, or an empty string
   * @param instrumentationFunctionId enclosing instrumentation-function identity, or an empty string
   * @param instrumentationFunctionResolved whether the enclosing identity resolves to the enclosing source element
   * @param instrumentationFunctionElementName enclosing XML element name, or an empty string
   * @param finalControlElementId explicit final-control-element reference, or an empty string
   * @param finalControlElementResolved whether the final-control-element reference resolves
   * @param finalControlElementName resolved final-control-element XML name, or an empty string
   * @param finalControlElementComponentClass explicit resolved target component class, or an empty string
   * @param finalControlElementComponentName explicit resolved target component name, or an empty string
   * @param finalControlElementTagName explicit resolved target tag name, or an empty string
   * @param locationId explicit {@code is located in} reference, or an empty string
   * @param locationResolved whether the location reference resolves
   * @param locationElementName resolved location XML name, or an empty string
   * @param locationComponentClass explicit resolved location component class, or an empty string
   * @param locationComponentName explicit resolved location component name, or an empty string
   * @param locationTagName explicit resolved location tag name, or an empty string
   */
  public DexpiActuatingFunctionInfo(String id, Kind kind, String componentClass, String functionNumber,
      String instrumentationFunctionId, boolean instrumentationFunctionResolved,
      String instrumentationFunctionElementName, String finalControlElementId, boolean finalControlElementResolved,
      String finalControlElementName, String finalControlElementComponentClass, String finalControlElementComponentName,
      String finalControlElementTagName, String locationId, boolean locationResolved, String locationElementName,
      String locationComponentClass, String locationComponentName, String locationTagName) {
    this.id = normalize(id);
    this.kind = kind;
    this.componentClass = normalize(componentClass);
    this.functionNumber = normalize(functionNumber);
    this.instrumentationFunctionId = normalize(instrumentationFunctionId);
    this.instrumentationFunctionResolved = instrumentationFunctionResolved;
    this.instrumentationFunctionElementName = normalize(instrumentationFunctionElementName);
    this.finalControlElementId = normalize(finalControlElementId);
    this.finalControlElementResolved = finalControlElementResolved;
    this.finalControlElementName = normalize(finalControlElementName);
    this.finalControlElementComponentClass = normalize(finalControlElementComponentClass);
    this.finalControlElementComponentName = normalize(finalControlElementComponentName);
    this.finalControlElementTagName = normalize(finalControlElementTagName);
    this.locationId = normalize(locationId);
    this.locationResolved = locationResolved;
    this.locationElementName = normalize(locationElementName);
    this.locationComponentClass = normalize(locationComponentClass);
    this.locationComponentName = normalize(locationComponentName);
    this.locationTagName = normalize(locationTagName);
  }

  /** @return source XML identity, or an empty string when absent */
  public String getId() {
    return id;
  }

  /** @return explicit source actuating-function kind */
  public Kind getKind() {
    return kind;
  }

  /** @return source component class */
  public String getComponentClass() {
    return componentClass;
  }

  /** @return explicit source function-number metadata, or an empty string */
  public String getFunctionNumber() {
    return functionNumber;
  }

  /** @return enclosing instrumentation-function identity, or an empty string */
  public String getInstrumentationFunctionId() {
    return instrumentationFunctionId;
  }

  /** @return whether the enclosing identity resolves to the enclosing source element */
  public boolean isInstrumentationFunctionResolved() {
    return instrumentationFunctionResolved;
  }

  /** @return enclosing XML element name, or an empty string */
  public String getInstrumentationFunctionElementName() {
    return instrumentationFunctionElementName;
  }

  /** @return explicit final-control-element reference, or an empty string */
  public String getFinalControlElementId() {
    return finalControlElementId;
  }

  /** @return whether the final-control-element reference resolves */
  public boolean isFinalControlElementResolved() {
    return finalControlElementResolved;
  }

  /** @return resolved final-control-element XML name, or an empty string */
  public String getFinalControlElementName() {
    return finalControlElementName;
  }

  /** @return explicit component class of the resolved final-control-element target, or an empty string */
  public String getFinalControlElementComponentClass() {
    return finalControlElementComponentClass;
  }

  /** @return explicit component name of the resolved final-control-element target, or an empty string */
  public String getFinalControlElementComponentName() {
    return finalControlElementComponentName;
  }

  /** @return explicit tag name of the resolved final-control-element target, or an empty string */
  public String getFinalControlElementTagName() {
    return finalControlElementTagName;
  }

  /** @return explicit {@code is located in} reference, or an empty string */
  public String getLocationId() {
    return locationId;
  }

  /** @return whether the location reference resolves */
  public boolean isLocationResolved() {
    return locationResolved;
  }

  /** @return resolved location XML name, or an empty string */
  public String getLocationElementName() {
    return locationElementName;
  }

  /** @return explicit component class of the resolved actuation-location target, or an empty string */
  public String getLocationComponentClass() {
    return locationComponentClass;
  }

  /** @return explicit component name of the resolved actuation-location target, or an empty string */
  public String getLocationComponentName() {
    return locationComponentName;
  }

  /** @return explicit tag name of the resolved actuation-location target, or an empty string */
  public String getLocationTagName() {
    return locationTagName;
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("kind", kind.name());
    result.put("componentClass", componentClass);
    result.put("functionNumber", functionNumber);
    result.put("instrumentationFunctionId", instrumentationFunctionId);
    result.put("instrumentationFunctionResolved", Boolean.valueOf(instrumentationFunctionResolved));
    result.put("instrumentationFunctionElementName", instrumentationFunctionElementName);
    result.put("finalControlElementId", finalControlElementId);
    result.put("finalControlElementResolved", Boolean.valueOf(finalControlElementResolved));
    result.put("finalControlElementName", finalControlElementName);
    result.put("finalControlElementComponentClass", finalControlElementComponentClass);
    result.put("finalControlElementComponentName", finalControlElementComponentName);
    result.put("finalControlElementTagName", finalControlElementTagName);
    result.put("locationId", locationId);
    result.put("locationResolved", Boolean.valueOf(locationResolved));
    result.put("locationElementName", locationElementName);
    result.put("locationComponentClass", locationComponentClass);
    result.put("locationComponentName", locationComponentName);
    result.put("locationTagName", locationTagName);
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
