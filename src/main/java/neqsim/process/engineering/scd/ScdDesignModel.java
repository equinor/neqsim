package neqsim.process.engineering.scd;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringStandard;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidPhysicalProjection;
import neqsim.process.engineering.pid.PidSimulationBindings;

/**
 * Revision-bound System Control Diagram projection of the shared P&amp;ID and simulation bindings.
 *
 * <p>
 * Template and terminal assignments are explicit project inputs. The selected legacy standard is a drawing basis, not a
 * claim that the complete template behaviour or safety lifecycle has been implemented. This class never installs
 * controllers, changes a process or turns a proposed signal into an executed runtime connection.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class ScdDesignModel implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String pidJson;
  private final String bindingJson;
  private final String projectionJson;
  private final String revision;
  private final String drawingNumber;
  private final EngineeringStandard standard;

  /**
   * Captures a source-linked drawing model after checking the project-controlled standard edition.
   *
   * @param project source engineering project
   * @param pid shared P&amp;ID model
   * @param bindings shared explicit runtime bindings
   * @param drawingNumber controlled SCD drawing number
   * @param standard selected NORSOK I-005:2013 or legacy IEC PAS 63131:2017 reference
   */
  public ScdDesignModel(EngineeringProject project, PidDesignModel pid, PidSimulationBindings bindings,
      String drawingNumber, EngineeringStandard standard) {
    if (project == null || pid == null || bindings == null || standard == null || drawingNumber == null
        || drawingNumber.trim().isEmpty()) {
      throw new IllegalArgumentException("project, P&ID, bindings, drawing number and standard are required");
    }
    if (!("NORSOK I-005".equals(standard.getCode()) && "2013".equals(standard.getEdition()))
        && !("IEC PAS 63131".equals(standard.getCode()) && "2017".equals(standard.getEdition()))) {
      throw new IllegalArgumentException("Unsupported SCD edition; select a supported controlled legacy basis");
    }
    boolean declared = false;
    for (EngineeringStandard basis : project.getDesignBasis().getStandards()) {
      declared |= basis.getReference().equals(standard.getReference());
    }
    if (!declared) {
      throw new IllegalArgumentException("Declare the selected SCD standard in the project design basis first");
    }
    bindings.assertCurrent(project, pid);
    this.pidJson = json(pid.toMap());
    this.bindingJson = bindings.toJson();
    this.projectionJson = PidPhysicalProjection.resolve(project.getEngineeringProcessSystem(), pid, bindings).toJson();
    this.revision = project.getRevision();
    this.drawingNumber = drawingNumber.trim();
    this.standard = standard;
    // Validate template assignments and every functional endpoint before any drawing is exported.
    getSignals();
    for (PidElement element : getBlocks()) {
      template(element);
    }
  }

  /**
   * Checks that a retained SCD still represents the exact P&amp;ID and runtime identities.
   *
   * @param project current project
   * @param pid current P&amp;ID
   * @param bindings current runtime binding register
   */
  public void assertCurrent(EngineeringProject project, PidDesignModel pid, PidSimulationBindings bindings) {
    bindings.assertCurrent(project, pid);
    if (!revision.equals(project.getRevision()) || !pidJson.equals(json(pid.toMap()))
        || !bindingJson.equals(bindings.toJson())) {
      throw new IllegalStateException("SCD source changed; regenerate from the current P&ID and bindings");
    }
  }

  /**
   * Returns detached source elements; mutating a returned object cannot change this drawing model.
   *
   * @return functional blocks and actuated final elements, in source order
   */
  public List<PidElement> getBlocks() {
    List<PidElement> blocks = new ArrayList<PidElement>();
    for (PidElement element : PidDesignModel.fromJson(pidJson).getElements()) {
      PidElementType type = element.getType();
      if (type == PidElementType.MEASUREMENT || type == PidElementType.INDICATOR || type == PidElementType.CONTROLLER
          || type == PidElementType.ALARM || type == PidElementType.TRIP || type == PidElementType.SAFETY_FUNCTION
          || type == PidElementType.SIGNAL || type == PidElementType.CONTROL_VALVE
          || type == PidElementType.SHUTDOWN_VALVE || type == PidElementType.BLOWDOWN_VALVE) {
        blocks.add(element);
      }
    }
    return blocks;
  }

  /**
   * Uses the same directed functional links as the physical P&amp;ID/DEXPI projection.
   *
   * @return detached signal rows with source IDs, terminals, type and explicit unresolved-input status
   */
  public List<Map<String, String>> getSignals() {
    Map<String, PidElement> byTag = new LinkedHashMap<String, PidElement>();
    for (PidElement element : getBlocks()) {
      byTag.put(element.getTag(), element);
    }
    List<Map<String, String>> result = new ArrayList<Map<String, String>>();
    com.google.gson.JsonArray links = com.google.gson.JsonParser.parseString(projectionJson).getAsJsonObject()
        .getAsJsonArray("functionalLinks");
    int index = 0;
    for (com.google.gson.JsonElement value : links) {
      com.google.gson.JsonObject link = value.getAsJsonObject();
      if (!"DECLARED_FUNCTIONAL_LINK".equals(link.get("status").getAsString())) {
        throw new IllegalArgumentException("Unresolved P&ID signal reference: " + link);
      }
      PidElement source = byTag.get(link.get("sourceTag").getAsString());
      PidElement target = byTag.get(link.get("targetTag").getAsString());
      if (source == null || target == null) {
        throw new IllegalArgumentException("Unresolved or unsupported SCD functional endpoint: " + link);
      }
      Map<String, String> row = new LinkedHashMap<String, String>();
      row.put("id", String.format(java.util.Locale.ROOT, "S%03d", Integer.valueOf(++index)));
      row.put("sourcePidId", source.getId());
      row.put("targetPidId", target.getId());
      row.put("sourceTag", source.getTag());
      row.put("targetTag", target.getTag());
      row.put("sourceTerminal", terminal(source, target.getTag(), "scdOutputTerminals", "scdOutputTerminal"));
      row.put("targetTerminal", terminal(target, source.getTag(), "scdInputTerminals", "scdInputTerminal"));
      String signalType = terminal(source, target.getTag(), "scdSignalTypes", "scdSignalType");
      if (!signalType.isEmpty() && !"ANALOGUE".equals(signalType) && !"BINARY".equals(signalType)) {
        throw new IllegalArgumentException("SCD signal type must be ANALOGUE or BINARY: " + source.getTag());
      }
      row.put("signalType", signalType.isEmpty() ? "HOLD" : signalType);
      row.put("status",
          row.get("sourceTerminal").isEmpty() || row.get("targetTerminal").isEmpty() || signalType.isEmpty()
              ? "TERMINALS_OR_TYPE_REQUIRED"
              : "DECLARED_PROJECT_CONNECTION");
      result.add(row);
    }
    return result;
  }

  /**
   * Selects an explicitly assigned standard template or an explicitly marked project function.
   *
   * @param element source element
   * @return MA, MB, CA, OA, SBV, SBE, PROJECT or HOLD
   */
  public static String template(PidElement element) {
    String code = attribute(element, "scdTemplate");
    if (code.isEmpty()) {
      return "HOLD";
    }
    if (!Arrays.asList("MA", "MB", "CA", "OA", "SBV", "SBE", "PROJECT").contains(code)
        || attribute(element, "scdTemplateReference").isEmpty()) {
      throw new IllegalArgumentException(
          "SCD template needs a supported code and project reference: " + element.getTag());
    }
    PidElementType type = element.getType();
    boolean matches = "PROJECT".equals(code)
        || (("MA".equals(code) || "MB".equals(code)) && type == PidElementType.MEASUREMENT)
        || ("CA".equals(code) && type == PidElementType.CONTROLLER)
        || ("OA".equals(code) && type == PidElementType.CONTROL_VALVE)
        || ("SBV".equals(code) && (type == PidElementType.SHUTDOWN_VALVE || type == PidElementType.BLOWDOWN_VALVE))
        || ("SBE".equals(code) && type == PidElementType.SIGNAL);
    if (!matches) {
      throw new IllegalArgumentException("SCD template does not match P&ID element type: " + element.getTag());
    }
    return code;
  }

  /**
   * Reads an optional project attribute without substituting engineering defaults.
   *
   * @param element source element
   * @param name attribute name
   * @return text or empty string
   */
  public static String attribute(PidElement element, String name) {
    Object value = element.getAttributes().get(name);
    return value == null ? "" : String.valueOf(value).trim();
  }

  /**
   * Reads an individual connection override before a common terminal assignment.
   *
   * @param element source element
   * @param peerTag connected tag
   * @param mapName per-connection terminal map
   * @param commonName common terminal
   * @return terminal or empty string
   */
  private static String terminal(PidElement element, String peerTag, String mapName, String commonName) {
    Object overrides = element.getAttributes().get(mapName);
    if (overrides instanceof Map<?, ?> && ((Map<?, ?>) overrides).containsKey(peerTag)) {
      Object value = ((Map<?, ?>) overrides).get(peerTag);
      return value == null ? "" : String.valueOf(value).trim();
    }
    return attribute(element, commonName);
  }

  /** @return controlled drawing number */
  public String getDrawingNumber() {
    return drawingNumber;
  }

  /** @return captured project revision */
  public String getRevision() {
    return revision;
  }

  /** @return selected controlled standard reference */
  public EngineeringStandard getStandard() {
    return standard;
  }

  /** @return detached audit model linking drawings, P&amp;ID IDs, runtime names and unresolved engineering inputs */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_scd_design_model.v1");
    result.put("drawingNumber", drawingNumber);
    result.put("revision", revision);
    result.put("standardReference", standard.getReference());
    result.put("standardApplication", standard.getApplication());
    result.put("publicationStatus", "IEC PAS 63131".equals(standard.getCode()) ? "WITHDRAWN_2024-09-27"
        : "LEGACY_PROJECT_SELECTED_EDITION_STATUS_REVIEW_REQUIRED");
    result.put("standardsConformance", "NOT_ASSESSED_CONTROLLED_TEXT_AND_TEMPLATE_REVIEW_REQUIRED");
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("logicExecution", "EXISTING_NATIVE_BINDINGS_ONLY_NO_LOGIC_GENERATED");
    result.put("sourceBindings", com.google.gson.JsonParser.parseString(bindingJson));
    List<Map<String, Object>> blocks = new ArrayList<Map<String, Object>>();
    for (PidElement element : getBlocks()) {
      Map<String, Object> row = element.toMap();
      row.put("scdTemplate", template(element));
      row.put("system", attribute(element, "system").isEmpty() ? "HOLD" : attribute(element, "system"));
      blocks.add(row);
    }
    result.put("blocks", blocks);
    result.put("signals", getSignals());
    return result;
  }

  /** @return source-linked SCD JSON */
  public String toJson() {
    return json(toMap());
  }

  /**
   * Serializes source evidence deterministically.
   *
   * @param value JSON-compatible source value
   * @return formatted JSON
   */
  private static String json(Object value) {
    return new GsonBuilder().setPrettyPrinting().create().toJson(value);
  }
}
