package neqsim.process.engineering.pid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.PushButton;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Resolves P&amp;ID proposal attachments against the actual source process without inventing auxiliary paths.
 *
 * <p>
 * Both native drawings and DEXPI projection consume this register. A resolved proposal is a design attachment, not an
 * executable device or an engineering approval.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class PidPhysicalProjection {
  /** Supported attachment families. */
  public enum Kind {
    EXISTING_EQUIPMENT, INLINE_VALVE, SENSING, MANUAL_INPUT, FUNCTION, PROCESS_NOZZLE, AUXILIARY_ROUTE, UNRESOLVED
  }

  /** Immutable source-backed location of one P&amp;ID element. */
  public static final class Attachment {
    private final Map<String, Object> values;

    /**
     * Creates a source-backed attachment.
     *
     * @param source resolved values
     */
    private Attachment(Map<String, Object> source) {
      values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(source));
    }

    /** @return stable P&amp;ID tag */
    public String getPidTag() {
      return string("pidTag");
    }

    /** @return attachment family */
    public Kind getKind() {
      return Kind.valueOf(string("kind"));
    }

    /** @return actual owner equipment tag */
    public String getEquipmentTag() {
      return string("equipmentTag");
    }

    /** @return declared or bound actual stream name */
    public String getStreamTag() {
      return string("streamTag");
    }

    /** @return INLET, OUTLET, VESSEL or empty when not applicable */
    public String getDirection() {
      return string("direction");
    }

    /** @return zero-based source stream index, or -1 */
    public int getStreamIndex() {
      return ((Number) values.get("streamIndex")).intValue();
    }

    /** @return actual bound runtime object name, or empty */
    public String getBoundSimulationName() {
      return string("boundSimulationName");
    }

    /** @return ordering among proposals on the same declared physical line */
    public int getInlineOrder() {
      return ((Number) values.get("inlineOrder")).intValue();
    }

    /** @return explicitly provided auxiliary source equipment */
    public String getSourceEquipmentTag() {
      return string("physicalSourceEquipmentTag");
    }

    /** @return explicitly provided auxiliary source stream */
    public String getSourceStreamTag() {
      return string("physicalSourceStreamTag");
    }

    /** @return explicitly provided auxiliary target equipment */
    public String getTargetEquipmentTag() {
      return string("physicalTargetEquipmentTag");
    }

    /** @return explicitly provided auxiliary target stream */
    public String getTargetStreamTag() {
      return string("physicalTargetStreamTag");
    }

    /** @return controlled proposed source nozzle name, or empty for an actual stream endpoint */
    public String getSourceNozzleTag() {
      return string("physicalSourceNozzleTag");
    }

    /** @return explicit UTILITY or PROCESS role for a proposed source nozzle */
    public String getSourcePortRole() {
      return string("physicalSourcePortRole");
    }

    /** @return controlled proposed target nozzle name, or empty for an actual stream endpoint */
    public String getTargetNozzleTag() {
      return string("physicalTargetNozzleTag");
    }

    /** @return explicit UTILITY or PROCESS role for a proposed target nozzle */
    public String getTargetPortRole() {
      return string("physicalTargetPortRole");
    }

    /** @return controlled design evidence authorizing the proposed nozzle, not engineering approval */
    public String getDesignEvidenceReference() {
      return string("designEvidenceReference");
    }

    /** @return explicit common auxiliary route ID, or empty for one independent route */
    public String getPhysicalRouteId() {
      return string("physicalRouteId");
    }

    /** @return coordinated material line tag, or empty when this attachment is not an auxiliary material route */
    public String getMaterialLineTag() {
      if (getKind() != Kind.AUXILIARY_ROUTE) {
        return "";
      }
      return getPhysicalRouteId().isEmpty() ? getPidTag() + "-ROUTE" : getPhysicalRouteId();
    }

    /** @return explicit nonnegative series order, or -1 when absent/invalid */
    public int getPhysicalRouteOrder() {
      return ((Number) values.get("physicalRouteOrder")).intValue();
    }

    /** @return explicitly provided external destination */
    public String getDestinationTag() {
      return string("destinationTag");
    }

    /** @return unresolved reason, or empty */
    public String getReason() {
      return string("reason");
    }

    /** @return immutable machine-readable values */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>(values);
      result.put("materialLineTag", getMaterialLineTag());
      return Collections.unmodifiableMap(result);
    }

    /**
     * Retrieves a textual value.
     *
     * @param key value name
     * @return text or empty
     */
    private String string(String key) {
      Object value = values.get(key);
      return value == null ? "" : String.valueOf(value);
    }
  }

  private final Map<String, Attachment> attachments;
  private final List<Map<String, String>> signals;

  /**
   * Creates an immutable projection.
   *
   * @param values attachments in proposal order
   * @param links directed declared functional connections
   */
  private PidPhysicalProjection(Map<String, Attachment> values, List<Map<String, String>> links) {
    attachments = Collections.unmodifiableMap(new LinkedHashMap<String, Attachment>(values));
    signals = Collections.unmodifiableList(new ArrayList<Map<String, String>>(links));
  }

  /**
   * Resolves source locations and declared functional links without changing the process.
   *
   * @param process actual source process
   * @param model P&amp;ID proposal model
   * @param bindings explicit runtime bindings, or null
   * @return shared native drawing/exchange projection
   */
  public static PidPhysicalProjection resolve(ProcessSystem process, PidDesignModel model,
      PidSimulationBindings bindings) {
    if (process == null || model == null) {
      throw new IllegalArgumentException("process and model are required");
    }
    Map<String, Map<String, Object>> bound = boundRows(bindings);
    Map<String, Attachment> result = new LinkedHashMap<String, Attachment>();
    Map<String, Integer> inlineOrders = new LinkedHashMap<String, Integer>();
    Map<String, PidElement> byId = new LinkedHashMap<String, PidElement>();
    for (PidElement element : model.getElements()) {
      byId.put(element.getId(), element);
      Map<String, Object> row = initial(element, bound.get(element.getTag()));
      resolveOne(process, element, row, bound.get(element.getTag()));
      if (Kind.INLINE_VALVE.name().equals(row.get("kind"))) {
        String key = row.get("equipmentTag") + ":" + row.get("direction") + ":" + row.get("streamTag");
        Integer count = inlineOrders.get(key);
        int order = count == null ? 0 : count.intValue();
        row.put("inlineOrder", Integer.valueOf(order));
        inlineOrders.put(key, Integer.valueOf(order + 1));
      }
      result.put(element.getTag(), new Attachment(row));
    }
    List<Map<String, String>> links = new ArrayList<Map<String, String>>();
    for (PidElement source : model.getElements()) {
      if (!isSignalSource(source.getType())) {
        continue;
      }
      for (String targetId : source.getConnectedElementIds()) {
        PidElement target = byId.get(targetId);
        Map<String, String> link = new LinkedHashMap<String, String>();
        link.put("sourceTag", source.getTag());
        link.put("targetTag", target == null ? targetId : target.getTag());
        link.put("status", target == null ? "UNRESOLVED_TARGET" : "DECLARED_FUNCTIONAL_LINK");
        links.add(Collections.unmodifiableMap(link));
      }
    }
    return new PidPhysicalProjection(result, links);
  }

  /** @return immutable tag to attachment map */
  public Map<String, Attachment> getAttachments() {
    return attachments;
  }

  /** @return immutable directed proposal links; physical disposal links remain distinguishable by element type */
  public List<Map<String, String>> getSignals() {
    return signals;
  }

  /** @return auditable source-attachment register */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_pid_physical_projection.v1");
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (Attachment attachment : attachments.values()) {
      rows.add(attachment.toMap());
    }
    result.put("attachments", rows);
    result.put("functionalLinks", signals);
    result.put("approvalStatus", "REVIEW_REQUIRED");
    result.put("fitnessForConstruction", Boolean.FALSE);
    return result;
  }

  /** @return indented JSON evidence */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /**
   * Resolves one semantic proposal.
   *
   * @param process source process
   * @param element proposal
   * @param row mutable resolution
   * @param bound explicit binding row or null
   */
  private static void resolveOne(ProcessSystem process, PidElement element, Map<String, Object> row,
      Map<String, Object> bound) {
    ProcessEquipmentInterface owner = process.getUnit(element.getEquipmentTag());
    String role = bound == null ? "" : text(bound.get("role"));
    if ("EQUIPMENT".equals(role)) {
      ProcessEquipmentInterface actual = process.getUnit(text(bound.get("simulationName")));
      if (actual != null) {
        if (!element.getLineTag().isEmpty()
            && !locate(actual, element.getLineTag(), "", new LinkedHashMap<String, Object>())) {
          hold(row, "BOUND_DEVICE_NOT_ON_DECLARED_LINE");
          return;
        }
        // Keep the declared primary owner's port context as well as the exact
        // runtime valve identity. The renderer needs the relative inlet side
        // to place a bound upstream valve instead of treating it as an orphan.
        if (owner != null && owner != actual && !element.getLineTag().isEmpty()) {
          locate(owner, element.getLineTag(), "", row);
        }
        row.put("equipmentTag", actual.getName());
        resolved(row, Kind.EXISTING_EQUIPMENT);
        return;
      }
    }
    if (isFunction(element.getType())) {
      if (owner == null) {
        hold(row, "UNKNOWN_FUNCTION_EQUIPMENT");
      } else {
        resolved(row, Kind.FUNCTION);
      }
      return;
    }
    if (owner == null) {
      hold(row, "UNKNOWN_SOURCE_EQUIPMENT");
      return;
    }
    if (element.getType() == PidElementType.MEASUREMENT || element.getType() == PidElementType.INDICATOR) {
      boolean manual = "MANUAL_PUSH_BUTTON".equals(element.getAttributes().get("inputKind"));
      boolean actualButton = bound != null && "MEASUREMENT".equals(role)
          && PushButton.class.getName().equals(text(bound.get("simulationClass")));
      if (manual || actualButton) {
        if (manual && actualButton && element.getLineTag().isEmpty()) {
          row.put("streamTag", "");
          row.put("direction", "MANUAL");
          resolved(row, Kind.MANUAL_INPUT);
        } else {
          hold(row, "MANUAL_INPUT_REQUIRES_BOUND_NATIVE_PUSH_BUTTON_WITHOUT_PROCESS_LINE");
        }
        return;
      }
      boolean designedNozzle = !text(row.get("physicalSourceNozzleTag")).isEmpty();
      if (designedNozzle) {
        if (bound != null) {
          hold(row, "PROPOSED_NOZZLE_IS_NOT_A_BOUND_RUNTIME_MEASUREMENT_LOCATION");
          return;
        }
        if (validEndpoint(process, row, "physicalSource")) {
          row.put("equipmentTag", row.get("physicalSourceEquipmentTag"));
          row.put("direction", "DESIGNED_NOZZLE");
          row.put("streamTag", "");
          resolved(row, Kind.SENSING);
        } else {
          hold(row, "CONTROLLED_PROPOSED_SENSING_NOZZLE_INPUT_REQUIRED");
        }
        return;
      }
      if ("UTILITY_SUPPLY".equals(element.getAttributes().get("sensingLocation"))) {
        hold(row, "EXPLICIT_UTILITY_SENSING_LOCATION_REQUIRED");
        return;
      }
      Map<?, ?> location = bound != null && bound.get("measurementLocation") instanceof Map
          ? (Map<?, ?>) bound.get("measurementLocation")
          : Collections.emptyMap();
      String actualLocation = text(location.get("name"));
      if (!actualLocation.isEmpty()) {
        if (actualLocation.equals(owner.getName())) {
          row.put("direction", "VESSEL");
          resolved(row, Kind.SENSING);
          return;
        }
        row.put("streamTag", actualLocation);
      }
      if ("VESSEL".equals(element.getAttributes().get("sensingLocation"))) {
        row.put("direction", "VESSEL");
        resolved(row, Kind.SENSING);
        return;
      }
      if (locate(owner, text(row.get("streamTag")), "", row)) {
        resolved(row, Kind.SENSING);
        return;
      }
      String variable = text(element.getAttributes().get("measuredVariable"));
      if (text(row.get("streamTag")).isEmpty() && !"FLOW".equals(variable) && !"VIBRATION".equals(variable)) {
        row.put("direction", "VESSEL");
        resolved(row, Kind.SENSING);
        return;
      }
      hold(row, "NO_UNAMBIGUOUS_SENSING_LOCATION");
      return;
    }
    if (element.getType() == PidElementType.NOZZLE) {
      String direction = text(element.getAttributes().get("boundaryDirection"));
      if (locate(owner, element.getLineTag(), direction, row)) {
        resolved(row, Kind.PROCESS_NOZZLE);
      } else {
        hold(row, "NO_MATCHING_SOURCE_PROCESS_PORT");
      }
      return;
    }
    if (isAuxiliary(element)) {
      if (!text(row.get("physicalRouteId")).isEmpty() && (!controlled(text(row.get("physicalRouteId")))
          || ((Number) row.get("physicalRouteOrder")).intValue() < 0)) {
        hold(row, "EXPLICIT_CONTROLLED_ROUTE_ID_AND_INTEGER_ORDER_REQUIRED");
        return;
      }
      boolean source = validEndpoint(process, row, "physicalSource");
      boolean target = validEndpoint(process, row, "physicalTarget");
      String sourceRole = text(row.get("physicalSourceNozzleTag")).isEmpty() ? "PROCESS"
          : text(row.get("physicalSourcePortRole"));
      String targetRole = text(row.get("physicalTargetNozzleTag")).isEmpty() ? "PROCESS"
          : text(row.get("physicalTargetPortRole"));
      if (source && target && !sourceRole.equals(targetRole)) {
        hold(row, "INCOMPATIBLE_AUXILIARY_PROCESS_AND_UTILITY_PORT_ROLES");
      } else if (source && (target || controlled(text(row.get("destinationTag"))))) {
        resolved(row, Kind.AUXILIARY_ROUTE);
      } else {
        hold(row, "EXPLICIT_AUXILIARY_SOURCE_AND_TARGET_REQUIRED");
      }
      return;
    }
    if (isValve(element.getType()) && locate(owner, element.getLineTag(), "", row)) {
      resolved(row, Kind.INLINE_VALVE);
      return;
    }
    hold(row, "NO_REVIEWED_PHYSICAL_ATTACHMENT");
  }

  /**
   * Matches a declared stream against one owner's actual source ports.
   *
   * @param owner actual source unit
   * @param stream declared stream name
   * @param requested optional INLET/OUTLET restriction
   * @param row mutable location output
   * @return true only for one unambiguous source port
   */
  private static boolean locate(ProcessEquipmentInterface owner, String stream, String requested,
      Map<String, Object> row) {
    if (stream.isEmpty()) {
      return false;
    }
    int matches = 0;
    String direction = "";
    int port = -1;
    for (String side : new String[] {"INLET", "OUTLET"}) {
      if (!requested.isEmpty() && !requested.equals(side)) {
        continue;
      }
      List<StreamInterface> list = "INLET".equals(side) ? owner.getInletStreams() : owner.getOutletStreams();
      if (list == null) {
        continue;
      }
      for (int index = 0; index < list.size(); index++) {
        if (list.get(index) != null && stream.equals(list.get(index).getName())) {
          matches++;
          direction = side;
          port = index;
        }
      }
    }
    if (matches != 1) {
      return false;
    }
    row.put("direction", direction);
    row.put("streamIndex", Integer.valueOf(port));
    row.put("streamTag", stream);
    return true;
  }

  /**
   * Checks an actual stream endpoint or a separately evidenced, proposed owned equipment nozzle.
   *
   * @param process source process
   * @param row controlled values
   * @param prefix physicalSource or physicalTarget
   * @return true for exactly one supported endpoint mode
   */
  private static boolean validEndpoint(ProcessSystem process, Map<String, Object> row, String prefix) {
    ProcessEquipmentInterface owner = process.getUnit(text(row.get(prefix + "EquipmentTag")));
    if (owner == null) {
      return false;
    }
    String stream = text(row.get(prefix + "StreamTag"));
    String nozzle = text(row.get(prefix + "NozzleTag"));
    if (!stream.isEmpty() && !nozzle.isEmpty()) {
      return false;
    }
    if (!nozzle.isEmpty()) {
      String role = text(row.get(prefix + "PortRole"));
      return ("UTILITY".equals(role) || "PROCESS".equals(role)) && controlled(text(row.get("designEvidenceReference")))
          && !(owner instanceof StreamInterface) && !(owner instanceof ThrottlingValve) && !(owner instanceof Splitter)
          && !(owner instanceof Recycle) && !(owner instanceof Calculator) && !(owner instanceof Adjuster);
    }
    return locate(owner, stream, "", new LinkedHashMap<String, Object>());
  }

  /**
   * Defines the attachment fields with no numerical or engineering defaults.
   *
   * @param element proposal
   * @param binding optional explicit runtime mapping
   * @return mutable attachment values
   */
  private static Map<String, Object> initial(PidElement element, Map<String, Object> binding) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("pidTag", element.getTag());
    row.put("elementId", element.getId());
    row.put("elementType", element.getType().name());
    row.put("kind", Kind.UNRESOLVED.name());
    row.put("equipmentTag", element.getEquipmentTag());
    row.put("streamTag", element.getLineTag());
    row.put("direction", "");
    row.put("streamIndex", Integer.valueOf(-1));
    row.put("inlineOrder", Integer.valueOf(-1));
    row.put("reason", "");
    row.put("boundSimulationName", binding == null ? "" : text(binding.get("simulationName")));
    for (String attribute : new String[] {"physicalSourceEquipmentTag", "physicalSourceStreamTag",
        "physicalTargetEquipmentTag", "physicalTargetStreamTag", "physicalSourceNozzleTag", "physicalSourcePortRole",
        "physicalTargetNozzleTag", "physicalTargetPortRole", "designEvidenceReference", "physicalRouteId",
        "destinationTag"}) {
      row.put(attribute, text(element.getAttributes().get(attribute)));
    }
    Object rawOrder = element.getAttributes().get("physicalRouteOrder");
    int order = -1;
    if (rawOrder != null && String.valueOf(rawOrder).matches("[0-9]+")) {
      try {
        order = Integer.parseInt(String.valueOf(rawOrder));
      } catch (NumberFormatException ex) {
        order = -1;
      }
    }
    row.put("physicalRouteOrder", Integer.valueOf(order));
    return row;
  }

  /**
   * Reads only explicit persisted binding rows.
   *
   * @param bindings optional binding register
   * @return binding rows keyed by P&amp;ID tag
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> boundRows(PidSimulationBindings bindings) {
    Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
    if (bindings != null) {
      for (Object item : (List<?>) bindings.toMap().get("bindings")) {
        Map<String, Object> row = (Map<String, Object>) item;
        result.put(text(row.get("pidTag")), row);
      }
    }
    return result;
  }

  /**
   * Keeps physical disposal/nozzle routes out of the functional signal graph.
   *
   * @param type semantic source type
   * @return whether this element can originate a declared measurement/control signal
   */
  private static boolean isSignalSource(PidElementType type) {
    return type == PidElementType.MEASUREMENT || type == PidElementType.INDICATOR || type == PidElementType.CONTROLLER
        || type == PidElementType.ALARM || type == PidElementType.TRIP || type == PidElementType.SAFETY_FUNCTION
        || type == PidElementType.SIGNAL;
  }

  /**
   * Recognizes function semantics, which are not physical valves or pipe routes.
   *
   * @param type proposed type
   * @return whether a native functional object is appropriate
   */
  private static boolean isFunction(PidElementType type) {
    return type == PidElementType.CONTROLLER || type == PidElementType.ALARM || type == PidElementType.TRIP
        || type == PidElementType.SAFETY_FUNCTION || type == PidElementType.SIGNAL;
  }

  /**
   * Recognizes auxiliary paths requiring explicit route inputs.
   *
   * @param element proposed element
   * @return whether source and destination must be explicitly supplied
   */
  private static boolean isAuxiliary(PidElement element) {
    PidElementType type = element.getType();
    return type == PidElementType.BLOWDOWN_VALVE || type == PidElementType.SAFETY_RELIEF_VALVE
        || type == PidElementType.RUPTURE_DISK || type == PidElementType.DRAIN || type == PidElementType.VENT
        || type == PidElementType.SAMPLE_POINT || type == PidElementType.OFF_PAGE_CONNECTOR
        || type == PidElementType.CONTROL_VALVE
            && (text(element.getAttributes().get("proposalPurpose")).contains("ANTI-SURGE")
                || text(element.getAttributes().get("proposalPurpose")).contains("ANTISURGE")
                || text(element.getAttributes().get("proposalPurpose")).contains("UTILITY-CONTROL"))
        || !text(element.getAttributes().get("physicalSourceEquipmentTag")).isEmpty();
  }

  /**
   * Recognizes inline physical valve proposals.
   *
   * @param type proposal type
   * @return whether an actual process line can host the component
   */
  private static boolean isValve(PidElementType type) {
    return type == PidElementType.CONTROL_VALVE || type == PidElementType.ISOLATION_VALVE
        || type == PidElementType.SHUTDOWN_VALVE || type == PidElementType.CHECK_VALVE;
  }

  /**
   * Rejects unresolved placeholder destination text.
   *
   * @param value destination input
   * @return whether the input is explicit rather than a HOLD placeholder
   */
  private static boolean controlled(String value) {
    String normalized = value.toUpperCase(Locale.ROOT);
    return !value.isEmpty() && !normalized.contains("REQUIRED") && !normalized.contains("HOLD")
        && !normalized.contains("UNRESOLVED");
  }

  /**
   * Records a resolved attachment family.
   *
   * @param row mutable output
   * @param kind attachment family
   */
  private static void resolved(Map<String, Object> row, Kind kind) {
    row.put("kind", kind.name());
    row.put("reason", "");
  }

  /**
   * Records a visible unresolved engineering input.
   *
   * @param row mutable output
   * @param reason stable reason code
   */
  private static void hold(Map<String, Object> row, String reason) {
    row.put("kind", Kind.UNRESOLVED.name());
    row.put("reason", reason);
  }

  /**
   * Converts optional input to text.
   *
   * @param value optional value
   * @return trimmed text or empty
   */
  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
