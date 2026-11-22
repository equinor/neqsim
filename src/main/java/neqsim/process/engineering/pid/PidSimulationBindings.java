package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.ProcessElementInterface;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.measurementdevice.CompressorSurgeMarginMeasurement;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.CheckValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.HIPPSValve;
import neqsim.process.equipment.valve.PSDValve;
import neqsim.process.equipment.valve.RuptureDisk;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.StreamMeasurementDeviceBaseClass;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.ProcessDiagramGraphAdapter;

/**
 * Revision-bound P&amp;ID identities mapped explicitly to executable simulation elements.
 *
 * <p>
 * Only names, types and fingerprints are retained. Resolution always uses the supplied process copy, so a scenario
 * cannot obtain a reference to the original equipment through this register. Unbound proposals remain visible; a
 * drawing does not provide the missing physical models, fluid, controller tuning or safety logic.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PidSimulationBindings implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String projectId;
  private final String revision;
  private final String pidFingerprint;
  private final String sourceFingerprint;
  private final Map<String, Map<String, Object>> proposals = new LinkedHashMap<String, Map<String, Object>>();
  private final Map<String, Map<String, Object>> sourceElements;
  private final Map<String, Map<String, Object>> bindings = new LinkedHashMap<String, Map<String, Object>>();

  /**
   * Captures the source identities without retaining mutable process objects.
   *
   * @param project governed process project
   * @param model final or proposed P&amp;ID model
   */
  private PidSimulationBindings(EngineeringProject project, PidDesignModel model) {
    if (project == null || model == null || !project.getProjectId().equals(model.getProjectId())) {
      throw new IllegalArgumentException("project and P&ID must identify the same project");
    }
    projectId = project.getProjectId();
    revision = project.getRevision();
    pidFingerprint = fingerprint(model.toMap());
    sourceElements = catalog(project.getEngineeringProcessSystem());
    sourceFingerprint = sourceFingerprint(project.getEngineeringProcessSystem(), projectId, revision);
    for (PidElement element : model.getElements()) {
      proposals.put(element.getTag(), element.toMap());
    }
  }

  /**
   * Creates an empty explicit binding register for the current engineering revision.
   *
   * @param project governed project owning the executable model
   * @param model P&amp;ID owning stable element IDs and tags
   * @return empty binding register
   */
  public static PidSimulationBindings from(EngineeringProject project, PidDesignModel model) {
    return new PidSimulationBindings(project, model);
  }

  /**
   * Restores bindings only after independently rebuilding and matching their source identities.
   *
   * @param project restored governed project and executable source
   * @param model restored P&amp;ID
   * @param json previously exported binding JSON
   * @return verified explicit bindings
   */
  public static PidSimulationBindings fromJson(EngineeringProject project, PidDesignModel model, String json) {
    if (json == null) {
      throw new IllegalArgumentException("binding JSON must not be null");
    }
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    PidSimulationBindings restored = from(project, model);
    Map<String, Object> expected = restored.toMap();
    for (String field : new String[] {"schemaVersion", "projectId", "revision", "pidFingerprint",
        "sourceFingerprint"}) {
      if (!root.has(field) || !String.valueOf(expected.get(field)).equals(root.get(field).getAsString())) {
        throw new IllegalArgumentException("Binding artifact does not match current " + field);
      }
    }
    JsonArray rows = root.getAsJsonArray("bindings");
    if (rows == null) {
      throw new IllegalArgumentException("Binding artifact requires bindings");
    }
    for (JsonElement value : rows) {
      JsonObject row = value.getAsJsonObject();
      String tag = row.get("pidTag").getAsString();
      if (restored.bindings.containsKey(tag)) {
        throw new IllegalArgumentException("Duplicate P&ID binding tag: " + tag);
      }
      String role = row.get("role").getAsString();
      if (!"EQUIPMENT".equals(role) && !"MEASUREMENT".equals(role) && !"CONTROLLER".equals(role)) {
        throw new IllegalArgumentException("Unknown simulation binding role: " + role);
      }
      restored.bind(tag, row.get("simulationName").getAsString(), role);
      Map<String, Object> actual = restored.bindings.get(tag);
      for (String field : new String[] {"pidElementId", "pidElementType", "simulationClass"}) {
        if (!row.has(field) || !String.valueOf(actual.get(field)).equals(row.get(field).getAsString())) {
          throw new IllegalArgumentException("Binding artifact has changed " + field + " for " + tag);
        }
      }
    }
    return restored;
  }

  /**
   * Binds a physical P&amp;ID component to actual equipment; valve proposals require valve models.
   *
   * @param pidTag P&amp;ID tag
   * @param simulationName registered equipment name
   * @return this register
   */
  public PidSimulationBindings bindEquipment(String pidTag, String simulationName) {
    return bind(pidTag, simulationName, "EQUIPMENT");
  }

  /**
   * Binds a measurement or indication to an actual measurement device.
   *
   * @param pidTag P&amp;ID tag
   * @param simulationName registered measurement name
   * @return this register
   */
  public PidSimulationBindings bindMeasurement(String pidTag, String simulationName) {
    return bind(pidTag, simulationName, "MEASUREMENT");
  }

  /**
   * Binds a controller function to an actual registered or equipment-attached controller.
   *
   * @param pidTag P&amp;ID tag
   * @param simulationName controller name
   * @return this register
   */
  public PidSimulationBindings bindController(String pidTag, String simulationName) {
    return bind(pidTag, simulationName, "CONTROLLER");
  }

  /**
   * Validates the role and captures one deterministic binding.
   *
   * @param pidTag P&amp;ID tag
   * @param simulationName simulation name
   * @param role executable element category
   * @return this register
   */
  private PidSimulationBindings bind(String pidTag, String simulationName, String role) {
    Map<String, Object> proposal = proposals.get(pidTag);
    if (proposal == null) {
      throw new IllegalArgumentException("Unknown P&ID tag: " + pidTag);
    }
    Map<String, Object> source = sourceElements.get(key(role, simulationName));
    if (source == null) {
      throw new IllegalArgumentException("Missing " + role + " simulation element: " + simulationName);
    }
    PidElementType type = PidElementType.valueOf(String.valueOf(proposal.get("type")));
    boolean allowed = "MEASUREMENT".equals(role)
        ? type == PidElementType.MEASUREMENT || type == PidElementType.INDICATOR
        : "CONTROLLER".equals(role) ? type == PidElementType.CONTROLLER : isPhysical(type);
    if (!allowed
        || ("EQUIPMENT".equals(role) && !((List<?>) source.get("supportedPhysicalTypes")).contains(type.name()))) {
      throw new IllegalArgumentException("Wrong simulation role/type for P&ID tag " + pidTag + " (" + type + ")");
    }
    Map<String, Object> binding = new LinkedHashMap<String, Object>(source);
    binding.put("pidElementId", proposal.get("id"));
    binding.put("pidTag", pidTag);
    binding.put("pidElementType", type.name());
    bindings.put(pidTag, binding);
    return this;
  }

  /**
   * Fails when a P&amp;ID, project revision or executable topology has changed since binding.
   *
   * @param project current governed project
   * @param model current P&amp;ID model
   */
  public void assertCurrent(EngineeringProject project, PidDesignModel model) {
    if (project == null || model == null || !projectId.equals(project.getProjectId())
        || !projectId.equals(model.getProjectId()) || !revision.equals(project.getRevision())
        || !pidFingerprint.equals(fingerprint(model.toMap()))) {
      throw new IllegalStateException("P&ID simulation bindings are stale or belong to another project/revision");
    }
    try {
      if (!sourceFingerprint.equals(sourceFingerprint(project.getEngineeringProcessSystem(), projectId, revision))) {
        throw new IllegalStateException("P&ID simulation bindings are stale for the current executable topology");
      }
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("P&ID simulation bindings have ambiguous or invalid source identities",
          exception);
    }
  }

  /**
   * Requires explicit executable bindings for all tags used by a scoped study.
   *
   * @param pidTags P&amp;ID tags actually exercised by the study
   */
  public void requireBindings(Collection<String> pidTags) {
    if (pidTags == null || pidTags.isEmpty()) {
      throw new IllegalArgumentException("A simulation study requires a non-empty explicit P&ID tag scope");
    }
    for (String tag : pidTags) {
      binding(tag);
    }
  }

  /**
   * Returns the actual model name retained in scenario equipment-tag evidence.
   *
   * @param pidTag explicitly bound P&amp;ID tag, which may be an alias
   * @return exact simulation name
   */
  public String getSimulationName(String pidTag) {
    return String.valueOf(binding(pidTag).get("simulationName"));
  }

  /**
   * Resolves actual equipment from the caller's isolated simulation copy.
   *
   * @param pidTag bound P&amp;ID tag
   * @param process isolated process copy
   * @return equipment in that copy
   */
  public ProcessEquipmentInterface resolveEquipment(String pidTag, ProcessSystem process) {
    return (ProcessEquipmentInterface) resolve(pidTag, process, "EQUIPMENT");
  }

  /**
   * Resolves a measurement from the caller's isolated simulation copy.
   *
   * @param pidTag bound P&amp;ID tag
   * @param process isolated process copy
   * @return measurement in that copy
   */
  public MeasurementDeviceInterface resolveMeasurement(String pidTag, ProcessSystem process) {
    return (MeasurementDeviceInterface) resolve(pidTag, process, "MEASUREMENT");
  }

  /**
   * Resolves a controller from the caller's isolated simulation copy.
   *
   * @param pidTag bound P&amp;ID tag
   * @param process isolated process copy
   * @return controller in that copy
   */
  public ControllerDeviceInterface resolveController(String pidTag, ProcessSystem process) {
    return (ControllerDeviceInterface) resolve(pidTag, process, "CONTROLLER");
  }

  /**
   * Resolves one binding without falling back to a parent equipment or a similarly named object.
   *
   * @param pidTag P&amp;ID tag
   * @param process supplied process copy
   * @param role expected category
   * @return exact matching element
   */
  private ProcessElementInterface resolve(String pidTag, ProcessSystem process, String role) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    Map<String, Object> value = binding(pidTag);
    if (!role.equals(value.get("role"))) {
      throw new IllegalArgumentException("Wrong resolution role for P&ID tag: " + pidTag);
    }
    String name = String.valueOf(value.get("simulationName"));
    ProcessElementInterface match = null;
    for (ProcessElementInterface candidate : elements(process, role)) {
      if (name.equals(candidate.getName())) {
        if (match != null && candidate != match) {
          throw new IllegalArgumentException("Ambiguous simulation element: " + name);
        }
        match = candidate;
      }
    }
    if (match == null || !match.getClass().getName().equals(value.get("simulationClass"))) {
      throw new IllegalArgumentException("Missing or changed simulation element for P&ID tag: " + pidTag);
    }
    return match;
  }

  /**
   * Returns the configured binding or fails closed.
   *
   * @param tag P&amp;ID tag
   * @return binding descriptor
   */
  private Map<String, Object> binding(String tag) {
    Map<String, Object> result = bindings.get(tag);
    if (result == null) {
      throw new IllegalArgumentException("Unbound P&ID simulation tag: " + tag);
    }
    return result;
  }

  /** @return immutable currently bound P&amp;ID tags */
  public List<String> getBoundPidTags() {
    return Collections.unmodifiableList(new ArrayList<String>(bindings.keySet()));
  }

  /** @return immutable unbound proposals, including drawing-only relationships */
  public List<String> getUnboundPidTags() {
    List<String> result = new ArrayList<String>(proposals.keySet());
    result.removeAll(bindings.keySet());
    return Collections.unmodifiableList(result);
  }

  /** @return revision and source-linked binding evidence, without engineering approval */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_pid_simulation_bindings.v1");
    result.put("projectId", projectId);
    result.put("revision", revision);
    result.put("pidFingerprint", pidFingerprint);
    result.put("sourceFingerprint", sourceFingerprint);
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> value : bindings.values()) {
      rows.add(new LinkedHashMap<String, Object>(value));
    }
    result.put("bindings", rows);
    result.put("unboundPidTags", getUnboundPidTags());
    result.put("allProposalsBound", Boolean.valueOf(!proposals.isEmpty() && getUnboundPidTags().isEmpty()));
    result.put("scope", "EXPLICITLY_BOUND_ELEMENTS_ONLY");
    result.put("dynamicReadiness", "SCENARIO_SPECIFIC_PREFLIGHT_REQUIRED");
    result.put("approvalStatus", "REVIEW_REQUIRED");
    result.put("fitnessForConstruction", Boolean.FALSE);
    return result;
  }

  /** @return deterministic JSON suitable for retaining beside DEXPI and drawings */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /**
   * Creates a duplicate-checked catalog with no live object references.
   *
   * @param process executable process
   * @return descriptors keyed by role and exact name
   */
  private static Map<String, Map<String, Object>> catalog(ProcessSystem process) {
    Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
    for (String role : new String[] {"EQUIPMENT", "MEASUREMENT", "CONTROLLER"}) {
      for (ProcessElementInterface element : elements(process, role)) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("role", role);
        row.put("simulationName", element.getName());
        row.put("simulationClass", element.getClass().getName());
        row.put("dynamicCapability", element.getDynamicCapability().name());
        row.put("valve", Boolean.valueOf(element instanceof ValveInterface));
        row.put("supportedPhysicalTypes", Collections.unmodifiableList(physicalTypes(element)));
        if (element instanceof ProcessEquipmentInterface) {
          ProcessEquipmentInterface equipment = (ProcessEquipmentInterface) element;
          List<Map<String, Object>> controllers = new ArrayList<Map<String, Object>>();
          for (ControllerDeviceInterface controller : equipment.getControllers()) {
            controllers.add(identity(controller));
          }
          row.put("primaryController", identity(equipment.getController()));
          row.put("attachedControllers", Collections.unmodifiableList(controllers));
        }
        if (element instanceof ControllerDeviceInterface) {
          MeasurementDeviceInterface transmitter = ((ControllerDeviceInterface) element).getTransmitter();
          row.put("transmitter", identity(transmitter));
          row.put("transmitterIdentityStatus", transmitter == null ? "NOT_EXPOSED_OR_UNCONFIGURED" : "AVAILABLE");
          if (element instanceof AntiSurgeController) {
            AntiSurgeController antiSurge = (AntiSurgeController) element;
            row.put("calculatedMarginInput", identity(antiSurge.getSurgeMarginMeasurement()));
            row.put("protectedCompressor", identity(antiSurge.getCompressor()));
            row.put("recycleValve", identity(antiSurge.getRecycleValve()));
          }
        }
        if (element instanceof CompressorSurgeMarginMeasurement) {
          row.put("calculatedInputEquipment", identity(((CompressorSurgeMarginMeasurement) element).getCompressor()));
          row.put("inputKind", "CALCULATED_COMPRESSOR_MAP_MARGIN");
          row.put("causalFlowInput", identity(((CompressorSurgeMarginMeasurement) element).getFlowTransmitter()));
        }
        if (element instanceof StreamMeasurementDeviceBaseClass) {
          row.put("measurementLocation", identity(((StreamMeasurementDeviceBaseClass) element).getStream()));
        } else if (element instanceof LevelTransmitter) {
          row.put("measurementLocation", identity(((LevelTransmitter) element).getVessel()));
        }
        if (result.put(key(role, element.getName()), row) != null) {
          throw new IllegalArgumentException("Ambiguous " + role + " simulation name: " + element.getName());
        }
      }
    }
    return result;
  }

  /**
   * Enumerates distinct registered and attached executable elements.
   *
   * @param process executable process
   * @param role element category
   * @return identity-deduplicated elements in deterministic order
   */
  private static List<ProcessElementInterface> elements(ProcessSystem process, String role) {
    List<ProcessElementInterface> result = new ArrayList<ProcessElementInterface>();
    if ("EQUIPMENT".equals(role)) {
      result.addAll(process.getUnitOperations());
    } else if ("MEASUREMENT".equals(role)) {
      Set<ProcessElementInterface> seen = Collections
          .newSetFromMap(new IdentityHashMap<ProcessElementInterface, Boolean>());
      for (MeasurementDeviceInterface measurement : process.getMeasurementDevices()) {
        if (measurement != null && seen.add(measurement)) {
          result.add(measurement);
        }
      }
      for (ProcessElementInterface element : elements(process, "CONTROLLER")) {
        MeasurementDeviceInterface measurement = ((ControllerDeviceInterface) element).getTransmitter();
        if (measurement != null && seen.add(measurement)) {
          result.add(measurement);
        }
      }
    } else {
      Set<ProcessElementInterface> seen = Collections
          .newSetFromMap(new IdentityHashMap<ProcessElementInterface, Boolean>());
      for (ControllerDeviceInterface controller : process.getControllerDevices()) {
        if (controller != null && seen.add(controller)) {
          result.add(controller);
        }
      }
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        for (ControllerDeviceInterface controller : unit.getControllers()) {
          if (controller != null && seen.add(controller)) {
            result.add(controller);
          }
        }
      }
    }
    return result;
  }

  /**
   * Captures canonical topology plus executable instrumentation/controller identities.
   *
   * @param process source process
   * @param projectId project identity
   * @param revision controlled revision
   * @return source fingerprint
   */
  private static String sourceFingerprint(ProcessSystem process, String projectId, String revision) {
    Map<String, Object> content = new LinkedHashMap<String, Object>();
    content.put("topology",
        ProcessDiagramGraphAdapter.fromProcessSystem(process, projectId, revision).getFingerprint());
    content.put("elements", catalog(process));
    return fingerprint(content);
  }

  /**
   * Describes implicit sensor/controller/actuator wiring without retaining a mutable runtime reference.
   *
   * @param element associated executable element, or null
   * @return immutable identity descriptor or null
   */
  private static Map<String, Object> identity(ProcessElementInterface element) {
    if (element == null) {
      return null;
    }
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("name", element.getName());
    value.put("class", element.getClass().getName());
    return Collections.unmodifiableMap(value);
  }

  /**
   * Creates an unambiguous catalog key.
   *
   * @param role element category
   * @param name exact name
   * @return catalog key
   */
  private static String key(String role, String name) {
    return role + ":" + name;
  }

  /**
   * Whether a proposal represents an executable physical component.
   *
   * @param type P&amp;ID category
   * @return true for supported physical bindings
   */
  private static boolean isPhysical(PidElementType type) {
    return isValve(type) || type == PidElementType.DRAIN || type == PidElementType.VENT
        || type == PidElementType.SAMPLE_POINT;
  }

  /**
   * Whether a proposal must resolve to a valve model.
   *
   * @param type P&amp;ID category
   * @return true for valve/device categories
   */
  private static boolean isValve(PidElementType type) {
    return type == PidElementType.CONTROL_VALVE || type == PidElementType.ISOLATION_VALVE
        || type == PidElementType.SHUTDOWN_VALVE || type == PidElementType.BLOWDOWN_VALVE
        || type == PidElementType.CHECK_VALVE || type == PidElementType.SAFETY_RELIEF_VALVE
        || type == PidElementType.RUPTURE_DISK;
  }

  /**
   * Retains functional valve classifications instead of treating every valve as interchangeable.
   *
   * @param element actual simulation element
   * @return supported physical P&amp;ID categories
   */
  private static List<String> physicalTypes(ProcessElementInterface element) {
    List<String> result = new ArrayList<String>();
    if (!(element instanceof ValveInterface)) {
      return result;
    }
    result.add(PidElementType.ISOLATION_VALVE.name());
    result.add(PidElementType.DRAIN.name());
    result.add(PidElementType.VENT.name());
    result.add(PidElementType.SAMPLE_POINT.name());
    if (element instanceof ESDValve || element instanceof PSDValve || element instanceof HIPPSValve) {
      result.add(PidElementType.SHUTDOWN_VALVE.name());
    } else if (element instanceof BlowdownValve) {
      result.add(PidElementType.BLOWDOWN_VALVE.name());
    } else if (element instanceof CheckValve) {
      result.add(PidElementType.CHECK_VALVE.name());
    } else if (element instanceof RuptureDisk) {
      result.add(PidElementType.RUPTURE_DISK.name());
    } else if (element instanceof SafetyValve || element instanceof SafetyReliefValve) {
      result.add(PidElementType.SAFETY_RELIEF_VALVE.name());
    } else if (element instanceof ThrottlingValve) {
      result.add(PidElementType.CONTROL_VALVE.name());
    }
    return result;
  }

  /**
   * Hashes a deterministic structured value.
   *
   * @param value structured value
   * @return SHA-256 in lowercase hexadecimal
   */
  private static String fingerprint(Object value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(new GsonBuilder().create().toJson(value).getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : digest) {
        result.append(String.format(java.util.Locale.ROOT, "%02x", Integer.valueOf(item & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
