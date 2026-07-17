package neqsim.process.engineering.dexpi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.SafetyFunctionDesign;

/** Materializes governed engineering requirements as DEXPI P&amp;ID objects. */
public final class DexpiEngineeringMaterializer {
  private DexpiEngineeringMaterializer() {
  }

  /** Result of materializing the engineering functions in one DEXPI document. */
  public static final class MaterializationResult {
    private final List<CauseAndEffectEntry> causeAndEffectEntries;
    private final int instrumentFunctionCount;
    private final int protectiveDeviceCount;

    MaterializationResult(List<CauseAndEffectEntry> entries, int instrumentFunctionCount, int protectiveDeviceCount) {
      this.causeAndEffectEntries = new ArrayList<CauseAndEffectEntry>(entries);
      this.instrumentFunctionCount = instrumentFunctionCount;
      this.protectiveDeviceCount = protectiveDeviceCount;
    }

    /** @return number of generated instrument and logic functions */
    public int getInstrumentFunctionCount() {
      return instrumentFunctionCount;
    }

    /** @return number of generated control and protective piping components */
    public int getProtectiveDeviceCount() {
      return protectiveDeviceCount;
    }

    /** @return immutable cause-and-effect entries */
    public List<CauseAndEffectEntry> getCauseAndEffectEntries() {
      return Collections.unmodifiableList(causeAndEffectEntries);
    }

    /** Serializes the proposed cause-and-effect matrix with its engineering governance. */
    public String toCauseAndEffectJson(EngineeringProject project) {
      Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
      JsonObject root = new JsonObject();
      root.addProperty("projectId", project.getProjectId());
      root.addProperty("projectName", project.getName());
      root.addProperty("documentStatus", "PROPOSED_FOR_HAZOP_LOPA_AND_DISCIPLINE_REVIEW");
      root.addProperty("setPoints", "NOT_ASSIGNED");
      root.addProperty("votingArchitectures",
          project.getSafetyFunctionDesigns().isEmpty() ? "NOT_ASSIGNED" : "PROJECT_DEFINED_REVIEW_REQUIRED");
      root.addProperty("note",
          "Generated actions are design proposals, not an approved safety requirements specification.");
      JsonArray entries = new JsonArray();
      for (CauseAndEffectEntry entry : causeAndEffectEntries) {
        entries.add(entry.toJson(project));
      }
      root.add("entries", entries);
      JsonArray safetyFunctions = new JsonArray();
      for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
        safetyFunctions.add(gson.toJsonTree(design.toMap()));
      }
      root.add("safetyFunctionDesigns", safetyFunctions);
      JsonArray shutdownSequences = new JsonArray();
      for (neqsim.process.engineering.ShutdownSequence sequence : project.getShutdownSequences()) {
        shutdownSequences.add(gson.toJsonTree(sequence.toMap()));
      }
      root.add("shutdownSequences", shutdownSequences);
      root.add("shutdownDynamicVerification", gson.toJsonTree(project.getShutdownVerificationResults()));
      root.add("engineeringEvidence", gson.toJsonTree(project.getEvidenceRecords()));
      root.add("installedReliefDeviceDesigns", gson.toJsonTree(project.getReliefDeviceDesignInputs()));
      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
    }
  }

  /** One traceable row in the generated cause-and-effect proposal. */
  public static final class CauseAndEffectEntry {
    private final String requirementId;
    private final String equipmentTag;
    private final String initiatingDeviceTag;
    private final String cause;
    private final List<String> proposedEffects;
    private final String silTarget;
    private final List<String> standardReferences;

    CauseAndEffectEntry(EngineeringRequirement requirement, String initiatingDeviceTag, String cause,
        List<String> proposedEffects) {
      this.requirementId = requirement.getId();
      this.equipmentTag = requirement.getEquipmentTag();
      this.initiatingDeviceTag = initiatingDeviceTag;
      this.cause = cause;
      this.proposedEffects = new ArrayList<String>(proposedEffects);
      this.silTarget = requirement.getSilTarget();
      this.standardReferences = new ArrayList<String>(requirement.getStandardReferences());
    }

    /** @return source engineering requirement identifier */
    public String getRequirementId() {
      return requirementId;
    }

    /** @return protected equipment tag */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /** @return initiating instrument or unresolved initiating-condition marker */
    public String getInitiatingDeviceTag() {
      return initiatingDeviceTag;
    }

    /** @return proposed initiating cause */
    public String getCause() {
      return cause;
    }

    /** @return immutable proposed-effect list */
    public List<String> getProposedEffects() {
      return Collections.unmodifiableList(proposedEffects);
    }

    /** @return assigned SIL target or SIL_UNASSIGNED */
    public String getSilTarget() {
      return silTarget;
    }

    /** @return immutable source-standard references */
    public List<String> getStandardReferences() {
      return Collections.unmodifiableList(standardReferences);
    }

    JsonObject toJson(EngineeringProject project) {
      JsonObject object = new JsonObject();
      object.addProperty("requirementId", requirementId);
      object.addProperty("equipmentTag", equipmentTag);
      object.addProperty("initiatingDeviceTag", initiatingDeviceTag);
      object.addProperty("cause", cause);
      JsonArray effects = new JsonArray();
      for (String effect : proposedEffects) {
        effects.add(effect);
      }
      object.add("proposedEffects", effects);
      object.addProperty("silTarget", silTarget);
      object.addProperty("approvalStatus", "REVIEW_REQUIRED");
      object.addProperty("setPoint", "NOT_ASSIGNED");
      object.addProperty("votingArchitecture", votingFor(project, requirementId));
      JsonArray standards = new JsonArray();
      for (String reference : standardReferences) {
        standards.add(reference);
      }
      object.add("standardReferences", standards);
      return object;
    }
  }

  private static String votingFor(EngineeringProject project, String requirementId) {
    for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
      if (requirementId.equals(design.getRequirementId())) {
        List<String> architectures = new ArrayList<String>();
        for (SafetyFunctionDesign.Subsystem subsystem : design.getSubsystems()) {
          architectures.add(subsystem.getType().name() + ":" + subsystem.getVotingArchitecture());
        }
        return architectures.isEmpty() ? "NOT_ASSIGNED" : join(architectures);
      }
    }
    return "NOT_ASSIGNED";
  }

  private static final class EquipmentReference {
    private final String id;
    private final double x;
    private final double y;

    EquipmentReference(String id, double x, double y) {
      this.id = id;
      this.x = x;
      this.y = y;
    }
  }

  private static final class FunctionDefinition {
    private final List<String> sensors = new ArrayList<String>();
    private final List<DeviceDefinition> finalElements = new ArrayList<DeviceDefinition>();
    private String controller;
    private String controlSystem = "DCS";
    private String cause;
    private final List<String> effects = new ArrayList<String>();

    FunctionDefinition sensor(String value) {
      sensors.add(value);
      return this;
    }

    FunctionDefinition controller(String value) {
      controller = value;
      return this;
    }

    FunctionDefinition finalElement(String tagPrefix, String componentClass) {
      finalElements.add(new DeviceDefinition(tagPrefix, componentClass));
      return this;
    }

    FunctionDefinition safety(String causeText, String... effectTexts) {
      controlSystem = "SIS";
      cause = causeText;
      for (String effect : effectTexts) {
        effects.add(effect);
      }
      return this;
    }

    FunctionDefinition machinery(String causeText, String... effectTexts) {
      controlSystem = "MPS";
      cause = causeText;
      for (String effect : effectTexts) {
        effects.add(effect);
      }
      return this;
    }
  }

  private static final class DeviceDefinition {
    private final String tagPrefix;
    private final String componentClass;

    DeviceDefinition(String tagPrefix, String componentClass) {
      this.tagPrefix = tagPrefix;
      this.componentClass = componentClass;
    }
  }

  /**
   * Adds explicit DEXPI instrumentation, logic, signal, control-valve and protective-device objects.
   *
   * @param project governed engineering project
   * @param document DEXPI document produced from the associated process
   * @return materialization summary and cause-and-effect proposal
   */
  public static MaterializationResult materialize(EngineeringProject project, Document document) {
    if (project == null || document == null) {
      throw new IllegalArgumentException("project and document must not be null");
    }
    Element root = document.getDocumentElement();
    Set<String> usedIds = collectIds(document);
    Map<String, EquipmentReference> equipment = collectEquipmentReferences(document);
    Element protectionSystem = createProtectionSystem(document, usedIds);
    int functionCount = 0;
    int deviceCount = 0;
    int offset = 0;
    List<CauseAndEffectEntry> matrix = new ArrayList<CauseAndEffectEntry>();

    for (EngineeringRequirement requirement : project.getRequirements()) {
      EquipmentReference protectedEquipment = equipment.get(requirement.getEquipmentTag());
      if (protectedEquipment == null) {
        continue;
      }
      FunctionDefinition definition = definitionFor(requirement);
      if (definition == null) {
        continue;
      }
      String number = tagNumber(requirement.getEquipmentTag());
      List<String> loopMembers = new ArrayList<String>();
      String initiatingTag = "";
      double baseX = protectedEquipment.x - 6.0 + (offset % 4) * 4.0;
      double baseY = protectedEquipment.y + 16.0 + (offset / 4) * 8.0;

      for (int i = 0; i < definition.sensors.size(); i++) {
        String tag = definition.sensors.get(i) + "-" + number;
        if (i > 0) {
          tag += "-" + (i + 1);
        }
        String id = appendInstrumentFunction(document, root, usedIds, project, requirement, protectedEquipment, tag,
            definition.controlSystem, "SENSOR_OR_SWITCH", baseX + i * 4.0, baseY);
        loopMembers.add(id);
        functionCount++;
        if (initiatingTag.isEmpty()) {
          initiatingTag = tag;
        }
      }

      if (definition.controller != null) {
        String tag = definition.controller + "-" + number;
        String id = appendInstrumentFunction(document, root, usedIds, project, requirement, protectedEquipment, tag,
            definition.controlSystem, "CONTROLLER_OR_LOGIC_SOLVER", baseX + 8.0, baseY);
        for (String sensorId : loopMembers) {
          appendInformationFlow(document, root, usedIds, project, requirement, sensorId, id, definition.controlSystem,
              "SignalConveyingFunction");
        }
        loopMembers.add(id);
        functionCount++;
      }

      String actuatingSourceId = last(loopMembers);
      for (DeviceDefinition device : definition.finalElements) {
        String tag = device.tagPrefix + "-" + number;
        String id = appendPipingComponent(document, protectionSystem, usedIds, project, requirement, protectedEquipment,
            tag, device.componentClass, definition.controlSystem, baseX + 12.0 + deviceCount % 4 * 3.0,
            protectedEquipment.y);
        if (actuatingSourceId != null && !"SwingCheckValve".equals(device.componentClass)
            && !"SpringLoadedGlobeSafetyValve".equals(device.componentClass)) {
          appendInformationFlow(document, root, usedIds, project, requirement, actuatingSourceId, id,
              definition.controlSystem, "ActuatingSystemFunction");
        }
        loopMembers.add(id);
        deviceCount++;
      }

      appendInstrumentationLoop(document, root, usedIds, project, requirement, loopMembers);
      if (definition.cause != null) {
        if (initiatingTag.isEmpty()) {
          initiatingTag = "INITIATING_CONDITION_TO_BE_CONFIRMED";
        }
        matrix.add(new CauseAndEffectEntry(requirement, initiatingTag, definition.cause, definition.effects));
      }
      offset++;
    }

    if (protectionSystem.hasChildNodes()) {
      insertBeforeCatalogue(root, protectionSystem);
    }
    return new MaterializationResult(matrix, functionCount, deviceCount);
  }

  private static FunctionDefinition definitionFor(EngineeringRequirement requirement) {
    String id = requirement.getId();
    if (id.endsWith("PRESSURE-CONTROL")) {
      return new FunctionDefinition().sensor("PT").controller("PIC").finalElement("PCV", "GlobeValve");
    }
    if (id.endsWith("LEVEL-CONTROL")) {
      return new FunctionDefinition().sensor("LT").controller("LIC").finalElement("LCV", "GlobeValve");
    }
    if (id.endsWith("ANTI-SURGE")) {
      return new FunctionDefinition().sensor("FT").sensor("PDT").controller("UIC-AS").finalElement("ASCV",
          "GlobeValve");
    }
    if (id.endsWith("LEVEL-HH-TRIP")) {
      return new FunctionDefinition().sensor("LSHH").controller("UY-ESD").safety("High-high liquid level",
          "Isolate incoming feed or pressure source after approved shutdown-sequence review",
          "Trip protected downstream gas equipment where confirmed by HAZOP/LOPA");
    }
    if (id.endsWith("LEVEL-LL-TRIP")) {
      return new FunctionDefinition().sensor("LSLL").controller("UY-ESD").safety("Low-low liquid level",
          "Close liquid outlet isolation or control final element to prevent gas blow-by");
    }
    if (id.endsWith("PRESSURE-HH-TRIP") || id.endsWith("DISCHARGE-P-HH")) {
      return new FunctionDefinition().sensor("PSHH").controller("UY-ESD").safety("High-high pressure",
          "Isolate pressure source", "Trip associated rotating equipment");
    }
    if (id.endsWith("PRESSURE-LL-TRIP") || id.endsWith("SUCTION-P-LL") || id.endsWith("LOW-SUCTION")) {
      return new FunctionDefinition().sensor("PSLL").controller("UY-ESD").safety("Low-low pressure",
          "Trip associated rotating equipment");
    }
    if (id.endsWith("DISCHARGE-T-HH") || id.endsWith("OUTLET-T-HH")) {
      return new FunctionDefinition().sensor("TSHH").controller("UY-ESD").safety("High-high temperature",
          "Remove heat or compression source", "Trip associated equipment");
    }
    if (id.endsWith("LOW-FLOW")) {
      return new FunctionDefinition().sensor("FSLL").controller("UY-ESD").safety("Low-low process flow",
          "Remove heat source");
    }
    if (id.endsWith("MACHINERY-PROTECTION")) {
      return new FunctionDefinition().sensor("VSHH").sensor("ZSHH").sensor("TSHH").sensor("SSH").controller("UY-MPS")
          .machinery("Machinery protection limit exceeded", "Trip compressor driver",
              "Latch machinery shutdown for investigation");
    }
    if (id.endsWith("RELIEF")) {
      return new FunctionDefinition().finalElement("PSV", "SpringLoadedGlobeSafetyValve")
          .finalElement("BDV", "BallValve").safety("Pressure exceeds approved relief set pressure",
              "Open pressure safety valve to the approved disposal system",
              "Initiate depressurization only where confirmed by the approved blowdown philosophy");
    }
    if (id.endsWith("ISOLATION-BLOWDOWN")) {
      return new FunctionDefinition().controller("UY-ESD").finalElement("ESDV-SUC", "BallValve")
          .finalElement("ESDV-DIS", "BallValve").finalElement("NRV", "SwingCheckValve").finalElement("BDV", "BallValve")
          .safety("Confirmed emergency shutdown or depressurization demand", "Close suction and discharge isolation",
              "Prevent reverse flow", "Depressurize to the approved flare or disposal system");
    }
    if (id.endsWith("OUTLET-T-HIGH")) {
      return new FunctionDefinition().sensor("TAH").controller("TI");
    }
    return null;
  }

  private static Element createProtectionSystem(Document document, Set<String> usedIds) {
    Element system = document.createElement("PipingNetworkSystem");
    system.setAttribute("ID", uniqueId("PipingNetworkSystem-EngineeringSafeguards", usedIds));
    system.setAttribute("ComponentClass", "PipingNetworkSystem");
    system.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/PipingNetworkSystem");
    Element attributes = document.createElement("GenericAttributes");
    attributes.setAttribute("Set", "EngineeringSafeguardingSystem");
    appendAttribute(document, attributes, "DocumentStatus", "REVIEW_REQUIRED");
    appendAttribute(document, attributes, "ConnectivityStatus", "LOGICAL_ASSOCIATION_PENDING_PIPING_DESIGN");
    system.appendChild(attributes);
    return system;
  }

  private static String appendInstrumentFunction(Document document, Element root, Set<String> usedIds,
      EngineeringProject project, EngineeringRequirement requirement, EquipmentReference equipment, String tag,
      String controlSystem, String role, double x, double y) {
    String id = uniqueId("ProcessInstrumentationFunction-" + safe(tag), usedIds);
    Element function = document.createElement("ProcessInstrumentationFunction");
    function.setAttribute("ID", id);
    function.setAttribute("ComponentClass", "ProcessInstrumentationFunction");
    function.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunction");
    function.setAttribute("ComponentName", "INSTRUMENTATION_BUBBLE_SHAPE_FIELD");
    appendPosition(document, function, x, y);
    appendLabel(document, function, id, tag, x, y);

    String[] parts = tag.split("-", 2);
    Element dexpi = document.createElement("GenericAttributes");
    dexpi.setAttribute("Set", "DexpiAttributes");
    appendAttribute(document, dexpi, "ProcessInstrumentationFunctionsAssignmentClass", parts[0]);
    appendAttribute(document, dexpi, "ProcessInstrumentationFunctionNumberAssignmentClass",
        parts.length > 1 ? parts[1] : tag);
    appendAttribute(document, dexpi, "TagNameAssignmentClass", tag);
    function.appendChild(dexpi);
    appendGovernance(document, function, project, requirement, controlSystem, role);
    if ("CONTROLLER_OR_LOGIC_SOLVER".equals(role)) {
      Element controllerDesign = document.createElement("GenericAttributes");
      controllerDesign.setAttribute("Set", "ControllerDesign");
      appendAttribute(document, controllerDesign, "ControllerSetPoint", "NOT_ASSIGNED");
      appendAttribute(document, controllerDesign, "ProportionalGain", "NOT_ASSIGNED");
      appendAttribute(document, controllerDesign, "IntegralTime", "NOT_ASSIGNED");
      appendAttribute(document, controllerDesign, "DerivativeTime", "NOT_ASSIGNED");
      appendAttribute(document, controllerDesign, "ControlAction", "NOT_ASSIGNED");
      function.appendChild(controllerDesign);
    }

    Element association = document.createElement("Association");
    association.setAttribute("Type", "is associated with");
    association.setAttribute("ItemID", equipment.id);
    function.appendChild(association);

    if ("SENSOR_OR_SWITCH".equals(role)) {
      Element sensor = document.createElement("ProcessSignalGeneratingFunction");
      sensor.setAttribute("ID", uniqueId("ProcessSignalGeneratingFunction-" + safe(tag), usedIds));
      sensor.setAttribute("ComponentClass", "ProcessSignalGeneratingFunction");
      sensor.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/ProcessSignalGeneratingFunction");
      Element sensorAttributes = document.createElement("GenericAttributes");
      sensorAttributes.setAttribute("Set", "DexpiAttributes");
      appendAttribute(document, sensorAttributes, "ProcessSignalGeneratingFunctionNumberAssignmentClass", tag);
      sensor.appendChild(sensorAttributes);
      function.appendChild(sensor);
    }
    insertBeforeCatalogue(root, function);
    return id;
  }

  private static String appendPipingComponent(Document document, Element protectionSystem, Set<String> usedIds,
      EngineeringProject project, EngineeringRequirement requirement, EquipmentReference equipment, String tag,
      String componentClass, String controlSystem, double x, double y) {
    Element segment = document.createElement("PipingNetworkSegment");
    segment.setAttribute("ID", uniqueId("PipingNetworkSegment-" + safe(tag), usedIds));
    segment.setAttribute("ComponentClass", "PipingNetworkSegment");
    segment.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/PipingNetworkSegment");
    String id = uniqueId("PipingComponent-" + safe(tag), usedIds);
    Element component = document.createElement("PipingComponent");
    component.setAttribute("ID", id);
    component.setAttribute("ComponentClass", componentClass);
    component.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/" + componentClass);
    if ("GlobeValve".equals(componentClass)) {
      component.setAttribute("ComponentName", "GLOBE_VALVE_SHAPE");
    } else if ("BallValve".equals(componentClass)) {
      component.setAttribute("ComponentName", "BALL_VALVE_SHAPE");
    } else if ("SwingCheckValve".equals(componentClass)) {
      component.setAttribute("ComponentName", "CHECK_VALVE_SHAPE");
    }
    appendPosition(document, component, x, y);
    appendLabel(document, component, id, tag, x, y);
    appendNozzle(document, component, uniqueId(id + "-INLET", usedIds));
    appendNozzle(document, component, uniqueId(id + "-OUTLET", usedIds));
    Element dexpi = document.createElement("GenericAttributes");
    dexpi.setAttribute("Set", "DexpiAttributes");
    appendAttribute(document, dexpi, "TagNameAssignmentClass", tag);
    component.appendChild(dexpi);
    appendGovernance(document, component, project, requirement, controlSystem, "FINAL_ELEMENT_OR_PROTECTIVE_DEVICE");
    Element association = document.createElement("Association");
    association.setAttribute("Type", "protects");
    association.setAttribute("ItemID", equipment.id);
    component.appendChild(association);
    segment.appendChild(component);
    protectionSystem.appendChild(segment);
    return id;
  }

  private static void appendNozzle(Document document, Element component, String id) {
    Element nozzle = document.createElement("Nozzle");
    nozzle.setAttribute("ID", id);
    nozzle.setAttribute("ComponentClass", "Nozzle");
    component.appendChild(nozzle);
  }

  private static void appendInformationFlow(Document document, Element root, Set<String> usedIds,
      EngineeringProject project, EngineeringRequirement requirement, String startId, String endId,
      String controlSystem, String componentClass) {
    if (startId == null || endId == null) {
      return;
    }
    Element flow = document.createElement("InformationFlow");
    flow.setAttribute("ID", uniqueId("InformationFlow-" + safe(requirement.getId()) + "-" + componentClass, usedIds));
    flow.setAttribute("ComponentClass", componentClass);
    Element start = document.createElement("Association");
    start.setAttribute("Type", "has logical start");
    start.setAttribute("ItemID", startId);
    flow.appendChild(start);
    Element end = document.createElement("Association");
    end.setAttribute("Type", "has logical end");
    end.setAttribute("ItemID", endId);
    flow.appendChild(end);
    appendGovernance(document, flow, project, requirement, controlSystem, "INFORMATION_FLOW");
    insertBeforeCatalogue(root, flow);
  }

  private static void appendInstrumentationLoop(Document document, Element root, Set<String> usedIds,
      EngineeringProject project, EngineeringRequirement requirement, List<String> members) {
    if (members.isEmpty()) {
      return;
    }
    Element loop = document.createElement("InstrumentationLoopFunction");
    loop.setAttribute("ID", uniqueId("InstrumentationLoopFunction-" + safe(requirement.getId()), usedIds));
    loop.setAttribute("ComponentClass", "InstrumentationLoopFunction");
    for (String member : members) {
      Element association = document.createElement("Association");
      association.setAttribute("Type", "is a collection including");
      association.setAttribute("ItemID", member);
      loop.appendChild(association);
    }
    appendGovernance(document, loop, project, requirement, systemFor(requirement), "ENGINEERING_FUNCTION_LOOP");
    insertBeforeCatalogue(root, loop);
  }

  private static void appendGovernance(Document document, Element parent, EngineeringProject project,
      EngineeringRequirement requirement, String controlSystem, String role) {
    Element attributes = document.createElement("GenericAttributes");
    attributes.setAttribute("Set", "EngineeringGovernance");
    appendAttribute(document, attributes, "EngineeringRequirementId", requirement.getId());
    appendAttribute(document, attributes, "ProtectedEquipmentTag", requirement.getEquipmentTag());
    appendAttribute(document, attributes, "EngineeringRole", role);
    appendAttribute(document, attributes, "ControlSystem", controlSystem);
    appendAttribute(document, attributes, "InformationOrigin", requirement.getOrigin().name());
    appendAttribute(document, attributes, "ApprovalStatus", requirement.getApprovalStatus().name());
    appendAttribute(document, attributes, "SILTarget", requirement.getSilTarget());
    appendAttribute(document, attributes, "SetPoint", "NOT_ASSIGNED");
    appendAttribute(document, attributes, "VotingArchitecture", votingFor(project, requirement));
    appendAttribute(document, attributes, "TagStatus", "PROVISIONAL_PROJECT_TAG");
    appendAttribute(document, attributes, "StandardReferences", join(requirement.getStandardReferences()));
    parent.appendChild(attributes);
  }

  private static String votingFor(EngineeringProject project, EngineeringRequirement requirement) {
    return votingFor(project, requirement.getId());
  }

  private static String systemFor(EngineeringRequirement requirement) {
    if (requirement.getType() == EngineeringRequirement.Type.TRIP
        || requirement.getType() == EngineeringRequirement.Type.RELIEF) {
      return "SIS";
    }
    if (requirement.getType() == EngineeringRequirement.Type.MECHANICAL_PROTECTION) {
      return "MPS";
    }
    return "DCS";
  }

  private static Map<String, EquipmentReference> collectEquipmentReferences(Document document) {
    Map<String, EquipmentReference> result = new LinkedHashMap<String, EquipmentReference>();
    NodeList equipmentNodes = document.getElementsByTagName("Equipment");
    for (int i = 0; i < equipmentNodes.getLength(); i++) {
      Element equipment = (Element) equipmentNodes.item(i);
      String tag = findGenericAttribute(equipment, "TagNameAssignmentClass");
      if (tag == null) {
        continue;
      }
      double[] position = findPosition(equipment);
      result.put(tag, new EquipmentReference(equipment.getAttribute("ID"), position[0], position[1]));
    }
    return result;
  }

  private static double[] findPosition(Element element) {
    NodeList positions = element.getElementsByTagName("Position");
    if (positions.getLength() > 0) {
      Element position = (Element) positions.item(0);
      NodeList locations = position.getElementsByTagName("Location");
      if (locations.getLength() > 0) {
        Element location = (Element) locations.item(0);
        try {
          return new double[] { Double.parseDouble(location.getAttribute("X")),
              Double.parseDouble(location.getAttribute("Y")) };
        } catch (NumberFormatException ignored) {
          // Fall through to a deterministic default for non-graphical DEXPI input.
        }
      }
    }
    return new double[] { 30.0, 30.0 };
  }

  private static String findGenericAttribute(Element parent, String name) {
    NodeList nodes = parent.getElementsByTagName("GenericAttribute");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element attribute = (Element) nodes.item(i);
      if (name.equals(attribute.getAttribute("Name"))) {
        return attribute.getAttribute("Value");
      }
    }
    return null;
  }

  private static Set<String> collectIds(Document document) {
    Set<String> result = new LinkedHashSet<String>();
    NodeList all = document.getElementsByTagName("*");
    for (int i = 0; i < all.getLength(); i++) {
      Element element = (Element) all.item(i);
      if (element.hasAttribute("ID")) {
        result.add(element.getAttribute("ID"));
      }
    }
    return result;
  }

  private static void insertBeforeCatalogue(Element root, Element element) {
    NodeList catalogues = root.getElementsByTagName("ShapeCatalogue");
    Node catalogue = catalogues.getLength() == 0 ? null : catalogues.item(0);
    if (catalogue != null && catalogue.getParentNode() == root) {
      root.insertBefore(element, catalogue);
    } else {
      root.appendChild(element);
    }
  }

  private static void appendPosition(Document document, Element parent, double x, double y) {
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element reference = document.createElement("Reference");
    reference.setAttribute("X", "1");
    reference.setAttribute("Y", "0");
    reference.setAttribute("Z", "0");
    position.appendChild(reference);
    parent.appendChild(position);
    Element scale = document.createElement("Scale");
    scale.setAttribute("X", "0.8");
    scale.setAttribute("Y", "0.8");
    parent.appendChild(scale);
  }

  private static void appendLabel(Document document, Element parent, String parentId, String tag, double x, double y) {
    Element label = document.createElement("Label");
    label.setAttribute("ID", parentId + "-Label");
    label.setAttribute("ComponentClass", "TagNameLabel");
    Element text = document.createElement("Text");
    text.setAttribute("String", tag);
    text.setAttribute("Font", "Arial");
    text.setAttribute("Height", "2.5");
    text.setAttribute("Justification", "CenterCenter");
    appendTextPosition(document, text, x, y + 3.5);
    label.appendChild(text);
    parent.appendChild(label);
  }

  private static void appendTextPosition(Document document, Element parent, double x, double y) {
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element reference = document.createElement("Reference");
    reference.setAttribute("X", "1");
    reference.setAttribute("Y", "0");
    reference.setAttribute("Z", "0");
    position.appendChild(reference);
    parent.appendChild(position);
  }

  private static void appendAttribute(Document document, Element parent, String name, String value) {
    if (value == null || value.trim().isEmpty()) {
      return;
    }
    Element attribute = document.createElement("GenericAttribute");
    attribute.setAttribute("Name", name);
    attribute.setAttribute("Value", value);
    parent.appendChild(attribute);
  }

  private static String tagNumber(String equipmentTag) {
    String result = safe(equipmentTag).replace("-", "");
    return result.isEmpty() ? "UNASSIGNED" : result;
  }

  private static String uniqueId(String candidate, Set<String> usedIds) {
    String base = safe(candidate);
    String result = base;
    int sequence = 2;
    while (!usedIds.add(result)) {
      result = base + "-" + sequence++;
    }
    return result;
  }

  private static String safe(String value) {
    return value == null ? "UNASSIGNED" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "-");
  }

  private static String last(List<String> values) {
    return values.isEmpty() ? null : values.get(values.size() - 1);
  }

  private static String join(List<String> values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (result.length() > 0) {
        result.append(';');
      }
      result.append(value);
    }
    return result.toString();
  }
}
