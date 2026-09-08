package neqsim.process.processmodel.diagram;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.pid.PidCompletenessFinding;
import neqsim.process.engineering.pid.PidCompletenessValidator;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;

/**
 * Immutable, source-linked registers for a review-required P&amp;ID proposal.
 *
 * <p>
 * Lines are canonical material connections, not assumed project pipe specifications. Nozzles, valves, instrumentation
 * and interfaces retain their synthesis-rule provenance and proposal identities. Control signals exclude material-flow
 * links between nozzles and relief destinations. These registers do not add equipment to the simulation or claim that
 * the proposals have been drawn or approved.
 * </p>
 */
public final class EngineeringDiagramPidRegisters {
  private static final String REQUIRED = "PROJECT_INPUT_REQUIRED";
  private final Map<String, Object> data;

  private EngineeringDiagramPidRegisters(Map<String, Object> data) {
    this.data = immutableMap(data);
  }

  /**
   * Snapshots a proposal against the canonical equipment and material topology.
   *
   * @param documentSet canonical source document set
   * @param model proposed P&amp;ID elements for the same plant
   * @return immutable registers and unresolved engineering findings
   * @throws IllegalArgumentException for missing inputs, mismatched plants or unknown proposal equipment
   */
  public static EngineeringDiagramPidRegisters fromDocumentSet(EngineeringDiagramDocumentSet documentSet,
      PidDesignModel model) {
    if (documentSet == null || model == null || !documentSet.getPlantId().equals(model.getProjectId())) {
      throw new IllegalArgumentException("document set and P&ID model must identify the same plant");
    }
    Map<String, String> equipmentIds = new LinkedHashMap<String, String>();
    Map<String, List<SemanticObject>> lineObjects = new LinkedHashMap<String, List<SemanticObject>>();
    List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> gaps = new ArrayList<Map<String, Object>>();
    for (SemanticObject object : documentSet.getSemanticObjects()) {
      if (object.getKind() == EngineeringNode.Kind.EQUIPMENT) {
        if (equipmentIds.put(object.getLabel(), object.getId()) != null) {
          throw new IllegalArgumentException("ambiguous canonical equipment label: " + object.getLabel());
        }
      } else if (object.getKind() == EngineeringNode.Kind.PIPE_SEGMENT) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("semanticConnectionId", object.getId());
        row.put("sourceLabel", object.getLabel());
        row.put("sourceProperties", object.getProperties());
        row.put("qualificationStatus", "REVIEW_REQUIRED");
        for (String field : new String[] { "nominalPipeSize", "pipingClass", "schedule", "materialGrade" }) {
          Object value = object.getProperties().get(field);
          if (value == null || value.toString().trim().isEmpty()) {
            row.put(field, REQUIRED);
            gaps.add(gap("PID-LINE-INPUT-REQUIRED", object.getId(), field + " requires governed project input"));
          } else {
            row.put(field, value);
          }
        }
        row.put("reducerDisposition", "NO_GOVERNED_REDUCER_DECLARATION");
        lines.add(row);
        Object stream = object.getProperties().get("carriedObjectName");
        if (stream != null) {
          String name = stream.toString();
          if (!lineObjects.containsKey(name)) {
            lineObjects.put(name, new ArrayList<SemanticObject>());
          }
          lineObjects.get(name).add(object);
        }
      }
    }
    Map<String, List<Map<String, Object>>> registers = new LinkedHashMap<String, List<Map<String, Object>>>();
    for (String name : new String[] { "nozzles", "valves", "instruments", "controlSignals", "interfaces" }) {
      registers.put(name, new ArrayList<Map<String, Object>>());
    }
    Map<String, PidElement> proposals = new LinkedHashMap<String, PidElement>();
    for (PidElement element : model.getElements()) {
      proposals.put(element.getId(), element);
    }
    for (PidElement element : model.getElements()) {
      String equipmentId = equipmentIds.get(element.getEquipmentTag());
      if (equipmentId == null) {
        throw new IllegalArgumentException("P&ID proposal references unknown equipment: " + element.getEquipmentTag());
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>(element.toMap());
      row.put("semanticEquipmentId", equipmentId);
      List<String> connections = new ArrayList<String>();
      List<SemanticObject> candidates = lineObjects.get(element.getLineTag());
      if (candidates != null) {
        for (SemanticObject candidate : candidates) {
          if (element.getEquipmentTag().equals(candidate.getProperties().get("sourceEquipment"))
              || element.getEquipmentTag().equals(candidate.getProperties().get("targetEquipment"))) {
            connections.add(candidate.getId());
          }
        }
      }
      row.put("candidateSemanticConnectionIds", connections);
      row.put("connectionBindingStatus", "PROJECT_REVIEW_REQUIRED");
      row.put("qualificationStatus", "REVIEW_REQUIRED");
      switch (element.getType()) {
      case NOZZLE:
        row.put("nozzleSize", REQUIRED);
        registers.get("nozzles").add(row);
        break;
      case CONTROL_VALVE:
      case ISOLATION_VALVE:
      case SHUTDOWN_VALVE:
      case BLOWDOWN_VALVE:
      case CHECK_VALVE:
      case SAFETY_RELIEF_VALVE:
      case RUPTURE_DISK:
        registers.get("valves").add(row);
        break;
      case MEASUREMENT:
      case INDICATOR:
      case CONTROLLER:
      case ALARM:
      case TRIP:
      case SAFETY_FUNCTION:
        registers.get("instruments").add(row);
        break;
      case DRAIN:
      case VENT:
      case SAMPLE_POINT:
      case OFF_PAGE_CONNECTOR:
        registers.get("interfaces").add(row);
        break;
      default:
        break;
      }
      if (isSignalSource(element.getType())) {
        for (String targetId : element.getConnectedElementIds()) {
          PidElement target = proposals.get(targetId);
          if (target == null) {
            continue; // The completeness findings retain the unresolved reference.
          }
          Map<String, Object> signal = new LinkedHashMap<String, Object>();
          signal.put("sourcePidElementId", element.getId());
          signal.put("targetPidElementId", targetId);
          signal.put("sourceTag", element.getTag());
          signal.put("targetTag", target.getTag());
          signal.put("sourceRuleId", element.getRuleId());
          signal.put("qualificationStatus", "REVIEW_REQUIRED");
          registers.get("controlSignals").add(signal);
        }
      }
    }
    for (PidCompletenessFinding finding : PidCompletenessValidator.validate(model).getFindings()) {
      gaps.add(finding.toMap());
    }
    gaps.add(gap("PID-PROJECT-DESIGN-REQUIRED", model.getProjectId(),
        "Line classes, sizes, nozzle specifications, reducer declarations, instrument ranges, control narratives, "
            + "isolation, drain/vent and relief destinations require project evidence and discipline review"));
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("schemaVersion", "neqsim_engineering_diagram_pid_registers.v1");
    data.put("plantId", documentSet.getPlantId());
    data.put("revision", documentSet.getRevision());
    data.put("sourceGraphFingerprint", documentSet.getSourceGraphFingerprint());
    data.put("proposalProfileId", model.getProfileId());
    data.put("qualificationStatus", "REVIEW_REQUIRED");
    data.put("fitnessForConstruction", Boolean.FALSE);
    data.put("lines", lines);
    data.putAll(registers);
    data.put("gaps", gaps);
    return new EngineeringDiagramPidRegisters(data);
  }

  /** @return number of canonical material connections */
  public int getLineCount() {
    return count("lines");
  }

  /** @return number of proposed process nozzles */
  public int getNozzleCount() {
    return count("nozzles");
  }

  /** @return number of proposed valves and rupture devices */
  public int getValveCount() {
    return count("valves");
  }

  /** @return number of proposed measurement, control and safeguarding functions */
  public int getInstrumentCount() {
    return count("instruments");
  }

  /** @return number of proposed control and safeguarding signal connections */
  public int getControlSignalCount() {
    return count("controlSignals");
  }

  /** @return number of proposed drain, vent, sample and off-page interfaces */
  public int getInterfaceCount() {
    return count("interfaces");
  }

  /** @return number of unresolved engineering and completeness findings */
  public int getGapCount() {
    return count("gaps");
  }

  /** @return canonical source graph fingerprint */
  public String getSourceGraphFingerprint() {
    return (String) data.get("sourceGraphFingerprint");
  }

  /** @return recursively immutable register evidence */
  public Map<String, Object> toMap() {
    return data;
  }

  /** @return deterministic register JSON */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(data);
  }

  private int count(String register) {
    return ((List<?>) data.get(register)).size();
  }

  private static boolean isSignalSource(PidElementType type) {
    return type == PidElementType.MEASUREMENT || type == PidElementType.CONTROLLER || type == PidElementType.ALARM
        || type == PidElementType.TRIP || type == PidElementType.SAFETY_FUNCTION || type == PidElementType.SIGNAL;
  }

  private static Map<String, Object> gap(String code, String subject, String message) {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("code", code);
    value.put("subject", subject);
    value.put("message", message);
    value.put("qualificationStatus", "REVIEW_REQUIRED");
    return value;
  }

  private static Map<String, Object> immutableMap(Map<?, ?> source) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      result.put(String.valueOf(entry.getKey()), immutableValue(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  private static Object immutableValue(Object value) {
    if (value instanceof Map<?, ?>) {
      return immutableMap((Map<?, ?>) value);
    }
    if (value instanceof List<?>) {
      List<Object> result = new ArrayList<Object>();
      for (Object item : (List<?>) value) {
        result.add(immutableValue(item));
      }
      return Collections.unmodifiableList(result);
    }
    return value;
  }
}
