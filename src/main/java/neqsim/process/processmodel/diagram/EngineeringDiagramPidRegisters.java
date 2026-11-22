package neqsim.process.processmodel.diagram;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.pid.PidCompletenessFinding;
import neqsim.process.engineering.pid.PidCompletenessValidator;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidPhysicalProjection;
import neqsim.process.engineering.pid.PidPhysicalProjection.Attachment;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

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
    return build(documentSet, model, null, null);
  }

  /**
   * Snapshots canonical connections, configured actual-stream boundaries and resolved auxiliary routes with supplied
   * project line-design evidence. Supplied values remain review-required and do not grant approval.
   *
   * @param documentSet canonical source document set
   * @param model proposed P&amp;ID elements for the same plant
   * @param project source project containing the actual process and line-design inputs
   * @param physicalProjection shared resolved physical attachments for this model and process
   * @return immutable registers including supplied design inputs and unresolved engineering findings
   * @throws IllegalArgumentException for missing inputs, mismatched plants or ambiguous line-design tags
   */
  public static EngineeringDiagramPidRegisters fromDocumentSet(EngineeringDiagramDocumentSet documentSet,
      PidDesignModel model, EngineeringProject project, PidPhysicalProjection physicalProjection) {
    if (project == null || physicalProjection == null || documentSet == null
        || !documentSet.getPlantId().equals(project.getProjectId())) {
      throw new IllegalArgumentException(
          "document set, project and physical projection for the same plant are required");
    }
    return build(documentSet, model, project, physicalProjection);
  }

  /**
   * Builds legacy canonical registers or the coordinated project-aware register snapshot.
   *
   * @param documentSet canonical source document set
   * @param model proposed P&amp;ID model
   * @param project optional source project; null preserves the legacy overload
   * @param physicalProjection optional shared physical projection
   * @return immutable register snapshot
   * @throws IllegalArgumentException for inconsistent source identities or ambiguous input tags
   */
  private static EngineeringDiagramPidRegisters build(EngineeringDiagramDocumentSet documentSet, PidDesignModel model,
      EngineeringProject project, PidPhysicalProjection physicalProjection) {
    if (documentSet == null || model == null || !documentSet.getPlantId().equals(model.getProjectId())) {
      throw new IllegalArgumentException("document set and P&ID model must identify the same plant");
    }
    Map<String, LineDesignInput> designInputs = new LinkedHashMap<String, LineDesignInput>();
    if (project != null) {
      for (LineDesignInput input : project.getLineDesignInputs()) {
        if (designInputs.put(input.getLineTag(), input) != null) {
          throw new IllegalArgumentException("ambiguous project line-design tag: " + input.getLineTag());
        }
      }
    }
    Set<String> registeredLineTags = new LinkedHashSet<String>();
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
        if (project == null) {
          legacyLineInputs(row, object, gaps);
        } else {
          String lineTag = text(object.getProperties().get("carriedObjectName"));
          if (lineTag.isEmpty()) {
            lineTag = object.getLabel();
          }
          row.put("lineTag", lineTag);
          row.put("lineOrigin", "CANONICAL_MATERIAL_CONNECTION");
          row.put("sourceEquipmentTag", text(object.getProperties().get("sourceEquipment")));
          row.put("targetEquipmentTag", text(object.getProperties().get("targetEquipment")));
          row.put("sourceStreamTag", lineTag);
          row.put("targetStreamTag", lineTag);
          applyLineDesignInput(row, lineTag, designInputs.get(lineTag), gaps);
          registeredLineTags.add(lineTag);
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
    if (project != null) {
      addAuxiliaryLines(lines, gaps, registeredLineTags, designInputs, physicalProjection);
      for (LineDesignInput input : designInputs.values()) {
        if (registeredLineTags.contains(input.getLineTag())) {
          continue;
        }
        Map<String, Object> row = actualBoundaryLine(project.getEngineeringProcessSystem(), input.getLineTag());
        if (row == null) {
          gaps.add(gap("PID-LINE-INPUT-UNMATCHED", input.getLineTag(),
              "Line-design input has no canonical connection, actual source stream boundary or resolved auxiliary route"));
        } else {
          applyLineDesignInput(row, input.getLineTag(), input, gaps);
          row.put("reducerDisposition", "NO_GOVERNED_REDUCER_DECLARATION");
          lines.add(row);
          registeredLineTags.add(input.getLineTag());
        }
      }
    }
    Map<String, List<Map<String, Object>>> registers = new LinkedHashMap<String, List<Map<String, Object>>>();
    for (String name : new String[] {"nozzles", "valves", "instruments", "controlSignals", "interfaces"}) {
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

  /** @return number of canonical, configured boundary and resolved auxiliary material lines */
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
    return immutableMap(data);
  }

  /** @return deterministic register JSON */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(data);
  }

  /**
   * Preserves the canonical-only input policy of the original overload.
   *
   * @param row line row being assembled
   * @param object original canonical pipe object
   * @param gaps unresolved findings
   */
  private static void legacyLineInputs(Map<String, Object> row, SemanticObject object, List<Map<String, Object>> gaps) {
    for (String field : new String[] {"nominalPipeSize", "pipingClass", "schedule", "materialGrade"}) {
      Object value = object.getProperties().get(field);
      if (text(value).isEmpty()) {
        row.put(field, REQUIRED);
        gaps.add(gap("PID-LINE-INPUT-REQUIRED", object.getId(), field + " requires governed project input"));
      } else {
        row.put(field, value);
      }
    }
  }

  /**
   * Retains supplied design values and their evidence without changing their review-required status.
   *
   * @param row line row being assembled
   * @param lineTag exact material line designation
   * @param input supplied project input, or null
   * @param gaps unresolved findings
   */
  private static void applyLineDesignInput(Map<String, Object> row, String lineTag, LineDesignInput input,
      List<Map<String, Object>> gaps) {
    List<String> missing = new ArrayList<String>();
    if (input == null) {
      Collections.addAll(missing, "nominalPipeSize", "pipingClass", "schedule", "materialGrade", "outerDiameter",
          "nominalWallThickness", "corrosionAllowance", "designPressure", "designTemperature",
          "lineListEvidenceReference");
      row.put("evidenceReferences", Collections.emptyList());
    } else {
      row.putAll(input.toMap());
      row.put("lineDesignInput", input.toMap());
      missing.addAll(input.getMissingFields());
    }
    for (String field : new String[] {"nominalPipeSize", "pipingClass", "schedule", "materialGrade"}) {
      if (text(row.get(field)).isEmpty()) {
        row.put(field, REQUIRED);
      }
    }
    row.put("missingFields", missing);
    row.put("lineDesignInputStatus", missing.isEmpty() ? "SUPPLIED_REVIEW_REQUIRED" : "HOLD");
    row.put("sizingStatus", missing.contains("nominalPipeSize") || missing.contains("outerDiameter")
        || missing.contains("nominalWallThickness") ? "HOLD" : "REVIEW_REQUIRED");
    row.put("pipingClassStatus", missing.contains("pipingClass") ? "HOLD" : "REVIEW_REQUIRED");
    row.put("qualificationStatus", "REVIEW_REQUIRED");
    for (String field : missing) {
      gaps.add(gap("PID-LINE-INPUT-REQUIRED", lineTag, field + " requires governed project input"));
    }
  }

  /**
   * Adds one line per resolved auxiliary route, retaining the exact proposal endpoints and series members.
   *
   * @param lines material-line register rows
   * @param gaps unresolved findings
   * @param registeredLineTags existing canonical and auxiliary line designations
   * @param designInputs supplied inputs by exact line designation
   * @param projection actual resolved attachments
   * @throws IllegalArgumentException if an auxiliary route reuses a canonical line designation
   */
  private static void addAuxiliaryLines(List<Map<String, Object>> lines, List<Map<String, Object>> gaps,
      Set<String> registeredLineTags, Map<String, LineDesignInput> designInputs, PidPhysicalProjection projection) {
    Map<String, List<Attachment>> groups = new LinkedHashMap<String, List<Attachment>>();
    for (Attachment attachment : projection.getAttachments().values()) {
      if (attachment.getKind() != PidPhysicalProjection.Kind.AUXILIARY_ROUTE) {
        continue;
      }
      String lineTag = attachment.getMaterialLineTag();
      if (!groups.containsKey(lineTag)) {
        groups.put(lineTag, new ArrayList<Attachment>());
      }
      groups.get(lineTag).add(attachment);
    }
    for (Map.Entry<String, List<Attachment>> group : groups.entrySet()) {
      String lineTag = group.getKey();
      if (!registeredLineTags.add(lineTag)) {
        throw new IllegalArgumentException("auxiliary route reuses canonical material line tag: " + lineTag);
      }
      Collections.sort(group.getValue(), new Comparator<Attachment>() {
        /** {@inheritDoc} */
        @Override
        public int compare(Attachment left, Attachment right) {
          return Integer.compare(left.getPhysicalRouteOrder(), right.getPhysicalRouteOrder());
        }
      });
      Attachment first = group.getValue().get(0);
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("lineTag", lineTag);
      row.put("sourceLabel", lineTag);
      row.put("lineOrigin", "DECLARED_AUXILIARY_ROUTE");
      row.put("physicalRouteId", first.getPhysicalRouteId());
      row.put("sourceEquipmentTag", first.getSourceEquipmentTag());
      row.put("sourceStreamTag", first.getSourceStreamTag());
      row.put("targetEquipmentTag", first.getTargetEquipmentTag());
      row.put("targetStreamTag", first.getTargetStreamTag());
      row.put("destinationTag", first.getDestinationTag());
      for (String field : auxiliaryEndpointFields()) {
        row.put(field, first.toMap().get(field));
      }
      List<String> tags = new ArrayList<String>();
      List<Map<String, Object>> attachments = new ArrayList<Map<String, Object>>();
      boolean matchingEndpoints = true;
      for (Attachment attachment : group.getValue()) {
        tags.add(attachment.getPidTag());
        attachments.add(attachment.toMap());
        for (String field : auxiliaryEndpointFields()) {
          if (!text(first.toMap().get(field)).equals(text(attachment.toMap().get(field)))) {
            matchingEndpoints = false;
          }
        }
        if (!first.getDestinationTag().equals(attachment.getDestinationTag())) {
          matchingEndpoints = false;
        }
      }
      row.put("sourceProposalTags", tags);
      row.put("sourceAttachments", attachments);
      row.put("endpointStatus", matchingEndpoints ? "DECLARED_REVIEW_REQUIRED" : "HOLD");
      if (!matchingEndpoints) {
        gaps.add(gap("PID-AUXILIARY-ENDPOINT-CONFLICT", lineTag,
            "Series proposals declare inconsistent route endpoints; retained source attachments require review"));
      }
      applyLineDesignInput(row, lineTag, designInputs.get(lineTag), gaps);
      row.put("reducerDisposition", "NO_GOVERNED_REDUCER_DECLARATION");
      lines.add(row);
    }
  }

  /** @return exact endpoint fields carried by physical attachments */
  private static String[] auxiliaryEndpointFields() {
    return new String[] {"physicalSourceEquipmentTag", "physicalSourceStreamTag", "physicalSourceNozzleTag",
        "physicalSourcePortRole", "physicalTargetEquipmentTag", "physicalTargetStreamTag", "physicalTargetNozzleTag",
        "physicalTargetPortRole"};
  }

  /**
   * Finds an actual noncanonical stream boundary by source identity without inventing a canonical connection ID.
   *
   * @param process actual source process
   * @param lineTag configured actual stream name
   * @return boundary evidence, or null if no unambiguous actual boundary matches
   */
  private static Map<String, Object> actualBoundaryLine(ProcessSystem process, String lineTag) {
    Map<StreamInterface, Boolean> matches = new IdentityHashMap<StreamInterface, Boolean>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface && lineTag.equals(unit.getName())) {
        matches.put((StreamInterface) unit, Boolean.TRUE);
      }
      for (StreamInterface stream : unit.getInletStreams()) {
        if (lineTag.equals(stream.getName())) {
          matches.put(stream, Boolean.TRUE);
        }
      }
      for (StreamInterface stream : unit.getOutletStreams()) {
        if (lineTag.equals(stream.getName())) {
          matches.put(stream, Boolean.TRUE);
        }
      }
    }
    if (matches.size() != 1) {
      return null;
    }
    StreamInterface actual = matches.keySet().iterator().next();
    List<String> producers = new ArrayList<String>();
    List<String> consumers = new ArrayList<String>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface) {
        continue;
      }
      for (StreamInterface stream : unit.getOutletStreams()) {
        if (stream == actual) {
          producers.add(unit.getName());
        }
      }
      for (StreamInterface stream : unit.getInletStreams()) {
        if (stream == actual) {
          consumers.add(unit.getName());
        }
      }
    }
    if (producers.size() > 1 || consumers.size() > 1 || producers.isEmpty() && consumers.isEmpty()
        || !producers.isEmpty() && !consumers.isEmpty()) {
      return null;
    }
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("lineTag", lineTag);
    row.put("sourceLabel", actual.getName());
    row.put("lineOrigin", "ACTUAL_STREAM_BOUNDARY");
    row.put("boundaryDirection", producers.isEmpty() ? "INLET" : "OUTLET");
    row.put("sourceEquipmentTag", producers.isEmpty() ? "" : producers.get(0));
    row.put("sourceStreamTag", producers.isEmpty() ? "" : actual.getName());
    row.put("targetEquipmentTag", consumers.isEmpty() ? "" : consumers.get(0));
    row.put("targetStreamTag", consumers.isEmpty() ? "" : actual.getName());
    return row;
  }

  /**
   * Reads a non-null trimmed textual property.
   *
   * @param value source property
   * @return trimmed text or empty
   */
  private static String text(Object value) {
    return value == null ? "" : value.toString().trim();
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
